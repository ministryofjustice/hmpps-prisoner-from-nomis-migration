package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerDomesticStatusResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerNumberOfChildrenResponse
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

class ContactPersonBookingMovedIntTest(
  @Autowired private val nomisApi: ContactPersonProfileDetailsNomisApiMockServer,
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiMockServer,
  @Autowired private val nomisSyncApi: ContactPersonNomisSyncApiMockServer,
) : SqsIntegrationTestBase() {

  private val movedBookingId = 12345L
  private val toOffenderNo = "A1234AA"
  private val fromOffenderNo = "B1234BB"
  private val now = LocalDateTime.now()
  private val today = LocalDate.now().atStartOfDay()
  private val yesterday = today.minusDays(1)

  @Nested
  @DisplayName("prison-offender-events.prisoner.booking.moved")
  inner class BookingMoved {
    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        dpsApi.stubSyncDomesticStatus(fromOffenderNo, SyncPrisonerDomesticStatusResponse(123, true))
        dpsApi.stubSyncDomesticStatus(toOffenderNo, SyncPrisonerDomesticStatusResponse(345, true))
        dpsApi.stubSyncNumberOfChildren(fromOffenderNo, SyncPrisonerNumberOfChildrenResponse(567, true))
        dpsApi.stubSyncNumberOfChildren(toOffenderNo, SyncPrisonerNumberOfChildrenResponse(789, true))

        nomisSyncApi.stubSyncProfileDetails(toOffenderNo, "MARITAL")
        nomisSyncApi.stubSyncProfileDetails(toOffenderNo, "CHILD")
      }

      @Test
      fun `domestic status changed in NOMIS`() {
        // stub profile details returned from NOMIS
        stubGetProfileDetails(
          fromOffenderNo,
          bookingResponse(
            bookingId = 1,
            latestBooking = true,
            startDateTime = "$yesterday",
            profileDetailsResponse("MARITAL", "M"),
          ),
        )
        stubGetProfileDetails(
          toOffenderNo,
          bookingResponse(
            bookingId = 2,
            latestBooking = false,
            startDateTime = "$yesterday",
            profileDetailsResponse("MARITAL", "C"),
          ),
          bookingResponse(
            bookingId = movedBookingId,
            latestBooking = true,
            startDateTime = "$today",
            // The domestic status was updated after the booking started so should be sync'd to DPS
            profileDetailsResponse("MARITAL", "D", modifiedTime = "$now"),
          ),
        )

        // Send the event
        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("contact-person-booking-moved")
        }

        // check DPS updates
        dpsApi.verify(
          prisonerNumber = fromOffenderNo,
          type = "updated",
          profileType = "domestic-status",
          dpsUpdates = mapOf(
            "domesticStatusCode" to equalTo("M"),
          ),
        )
        dpsApi.verify(
          prisonerNumber = toOffenderNo,
          type = "updated",
          profileType = "domestic-status",
          dpsUpdates = mapOf(
            "domesticStatusCode" to equalTo("D"),
          ),
        )

        // check NOMIS updates
        nomisSyncApi.verify(
          putRequestedFor(urlPathEqualTo("/contactperson/sync/profile-details/$toOffenderNo/MARITAL")),
        )

        // check telemetry
        verify(telemetryClient).trackEvent(
          eq("contact-person-booking-moved"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "syncToDps" to "$fromOffenderNo-MARITAL,$toOffenderNo-MARITAL",
                "syncToNomis" to "$toOffenderNo-MARITAL,$toOffenderNo-CHILD",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `domestic status not changed in NOMIS`() {
        // stub profile details returned from NOMIS
        stubGetProfileDetails(
          fromOffenderNo,
          bookingResponse(
            bookingId = 1,
            latestBooking = true,
            startDateTime = "$yesterday",
            profileDetailsResponse("MARITAL", "M"),
          ),
        )
        stubGetProfileDetails(
          toOffenderNo,
          bookingResponse(
            bookingId = 2,
            latestBooking = false,
            startDateTime = "$yesterday",
            profileDetailsResponse("MARITAL", "C"),
          ),
          bookingResponse(
            bookingId = movedBookingId,
            latestBooking = true,
            startDateTime = "$today",
            // The modified time is before the booking start time so was not new
            profileDetailsResponse("MARITAL", "D", modifiedTime = "$yesterday"),
          ),
        )

        // Send the event
        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("contact-person-booking-moved")
        }

        // check DPS updates
        dpsApi.verify(
          prisonerNumber = fromOffenderNo,
          type = "updated",
          dpsUpdates = mapOf(
            "domesticStatusCode" to equalTo("M"),
          ),
        )
        // The to offender was not sync'd to DPS because it has not changed
        dpsApi.verify(
          prisonerNumber = toOffenderNo,
          type = "ignored",
        )

        // check NOMIS updates
        nomisSyncApi.verify(
          putRequestedFor(urlPathEqualTo("/contactperson/sync/profile-details/$toOffenderNo/MARITAL")),
        )

        // check telemetry
        verify(telemetryClient).trackEvent(
          eq("contact-person-booking-moved"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "syncToDps" to "$fromOffenderNo-MARITAL",
                "syncToNomis" to "$toOffenderNo-MARITAL,$toOffenderNo-CHILD",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `Number of children only changed in NOMIS`() {
        // stub profile details returned from NOMIS
        stubGetProfileDetails(
          fromOffenderNo,
          bookingResponse(
            bookingId = 1,
            latestBooking = true,
            startDateTime = "$yesterday",
            profileDetailsResponse("MARITAL", "M"),
            profileDetailsResponse("CHILD", "3"),
          ),
        )
        stubGetProfileDetails(
          toOffenderNo,
          bookingResponse(
            bookingId = 2,
            latestBooking = false,
            startDateTime = "$yesterday",
            profileDetailsResponse("MARITAL", "C"),
            profileDetailsResponse("CHILD", null),
          ),
          bookingResponse(
            bookingId = movedBookingId,
            latestBooking = true,
            startDateTime = "$today",
            // The domestic status was not updated so won't be sync'd to DPS
            profileDetailsResponse("MARITAL", "C", modifiedTime = "$yesterday"),
            // The number of children was updated after the booking started so should be sync'd to DPS
            profileDetailsResponse("CHILD", "4", modifiedTime = "$now"),
          ),
        )

        // Send the event
        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("contact-person-booking-moved")
        }

        // check DPS updates
        dpsApi.verify(
          prisonerNumber = fromOffenderNo,
          profileType = "domestic-status",
          type = "updated",
          dpsUpdates = mapOf(
            "domesticStatusCode" to equalTo("M"),
          ),
        )
        dpsApi.verify(
          prisonerNumber = fromOffenderNo,
          profileType = "number-of-children",
          type = "updated",
          dpsUpdates = mapOf(
            "numberOfChildren" to equalTo("3"),
          ),
        )
        dpsApi.verify(
          prisonerNumber = toOffenderNo,
          type = "ignored",
          profileType = "domestic-status",
        )
        dpsApi.verify(
          prisonerNumber = toOffenderNo,
          type = "updated",
          profileType = "number-of-children",
          dpsUpdates = mapOf(
            "numberOfChildren" to equalTo("4"),
          ),
        )

        // check NOMIS updates
        nomisSyncApi.verify(
          putRequestedFor(urlPathEqualTo("/contactperson/sync/profile-details/$toOffenderNo/MARITAL")),
        )
        nomisSyncApi.verify(
          putRequestedFor(urlPathEqualTo("/contactperson/sync/profile-details/$toOffenderNo/CHILD")),
        )

        // check telemetry
        verify(telemetryClient).trackEvent(
          eq("contact-person-booking-moved"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "syncToDps" to "$fromOffenderNo-MARITAL,$fromOffenderNo-CHILD,$toOffenderNo-CHILD",
                "syncToNomis" to "$toOffenderNo-MARITAL,$toOffenderNo-CHILD",
              ),
            )
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class Errors {
      @Test
      @Disabled("TODO")
      fun `Call to get NOMIS data fails`() {
      }

      @Test
      @Disabled("TODO")
      fun `Call to put DPS data fails`() {
      }

      @Test
      @Disabled("TODO")
      fun `Call to sync back to NOMIS fails`() {
      }
    }
  }

  private fun sendBookingMovedEvent(): SendMessageResponse? = awsSqsSentencingOffenderEventsClient.sendMessage(
    personalRelationshipsQueueOffenderEventsUrl,
    bookingMovedDomainEvent(
      eventType = "prison-offender-events.prisoner.booking.moved",
      bookingId = movedBookingId,
      movedFromNomsNumber = fromOffenderNo,
      movedToNomsNumber = toOffenderNo,
    ),
  )

  private fun ContactPersonProfileDetailsDpsApiMockServer.verify(
    prisonerNumber: String = "A1234AA",
    type: String = "updated",
    profileType: String = "domestic-status",
    dpsUpdates: Map<String, StringValuePattern> = mapOf(),
  ) {
    // For updates verify we sent the correct details to the DPS API
    if (type == "updated") {
      verify(
        putRequestedFor(urlPathEqualTo("/sync/$prisonerNumber/$profileType"))
          .apply {
            dpsUpdates.forEach { (jsonPath, pattern) ->
              withRequestBody(matchingJsonPath(jsonPath, pattern))
            }
          },
      )
    } else {
      // If not updated we shouldn't call the DPS API
      verify(0, putRequestedFor(urlPathEqualTo("/sync/A1234AA/$profileType")))
    }
  }

  fun stubGetProfileDetails(
    offenderNo: String,
    vararg bookings: BookingProfileDetailsResponse = arrayOf(bookingResponse()),
  ) {
    nomisApi.stubGetProfileDetails(
      offenderNo = offenderNo,
      bookingId = null,
      profileTypes = ContactPersonProfileType.all(),
      response = PrisonerProfileDetailsResponse(
        offenderNo = offenderNo,
        bookings = bookings.asList(),
      ),
    )
  }

  fun bookingResponse(
    bookingId: Long = 1,
    latestBooking: Boolean = true,
    startDateTime: String = "2024-01-03T12:34:56",
    vararg profileDetails: ProfileDetailsResponse = arrayOf(profileDetailsResponse("MARITAL", "M")),
  ) = BookingProfileDetailsResponse(
    bookingId = bookingId,
    startDateTime = startDateTime,
    latestBooking = latestBooking,
    profileDetails = profileDetails.asList(),
  )

  fun profileDetailsResponse(profileType: String, code: String?, modifiedTime: String = "2024-01-03T12:34:56") = ProfileDetailsResponse(
    type = profileType,
    code = code,
    createDateTime = "2024-01-03T12:34:56",
    createdBy = "A_USER",
    modifiedDateTime = modifiedTime,
    modifiedBy = "ANOTHER_USER",
  )
}
