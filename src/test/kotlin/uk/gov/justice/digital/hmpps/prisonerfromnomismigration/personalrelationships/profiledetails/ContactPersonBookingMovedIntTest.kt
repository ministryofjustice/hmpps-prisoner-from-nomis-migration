package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.countAllMessagesOnDLQQueue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ProfileDetailsResponse
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
  @DisplayName("prison-offender-events.prisoner.booking.moved event")
  inner class BookingMoved {

    @BeforeEach
    fun setUp() {
      dpsApi.stubSyncDomesticStatus(fromOffenderNo, SyncPrisonerDomesticStatusResponse(123, true))
      dpsApi.stubSyncDomesticStatus(toOffenderNo, SyncPrisonerDomesticStatusResponse(345, true))
      dpsApi.stubSyncNumberOfChildren(fromOffenderNo, SyncPrisonerNumberOfChildrenResponse(567, true))
      dpsApi.stubSyncNumberOfChildren(toOffenderNo, SyncPrisonerNumberOfChildrenResponse(789, true))

      nomisSyncApi.stubSyncProfileDetails(toOffenderNo, "MARITAL")
      nomisSyncApi.stubSyncProfileDetails(toOffenderNo, "CHILD")
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `domestic status changed in NOMIS`() {
        // stub profile details returned from NOMIS
        stubGetProfileDetails(
          fromOffenderNo,
          bookingResponse(
            bookingId = 1,
            latestBooking = true,
            startDateTime = yesterday,
            profileDetailsResponse("MARITAL", "M"),
          ),
        )
        stubGetProfileDetails(
          toOffenderNo,
          bookingResponse(
            bookingId = 2,
            latestBooking = false,
            startDateTime = yesterday,
            profileDetailsResponse("MARITAL", "C"),
          ),
          bookingResponse(
            bookingId = movedBookingId,
            latestBooking = true,
            startDateTime = today,
            // The domestic status was updated after the booking started so should be sync'd to DPS
            profileDetailsResponse("MARITAL", "D", modifiedTime = now),
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
            startDateTime = yesterday,
            profileDetailsResponse("MARITAL", "M"),
          ),
        )
        stubGetProfileDetails(
          toOffenderNo,
          bookingResponse(
            bookingId = 2,
            latestBooking = false,
            startDateTime = yesterday,
            profileDetailsResponse("MARITAL", "C"),
          ),
          bookingResponse(
            bookingId = movedBookingId,
            latestBooking = true,
            startDateTime = today,
            // The modified time is before the booking start time so was not new
            profileDetailsResponse("MARITAL", "D", modifiedTime = yesterday),
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
            startDateTime = yesterday,
            profileDetailsResponse("MARITAL", "M"),
            profileDetailsResponse("CHILD", "3"),
          ),
        )
        stubGetProfileDetails(
          toOffenderNo,
          bookingResponse(
            bookingId = 2,
            latestBooking = false,
            startDateTime = yesterday,
            profileDetailsResponse("MARITAL", "C"),
            profileDetailsResponse("CHILD", null),
          ),
          bookingResponse(
            bookingId = movedBookingId,
            latestBooking = true,
            startDateTime = today,
            // The domestic status was not updated so won't be sync'd to DPS
            profileDetailsResponse("MARITAL", "C", modifiedTime = yesterday),
            // The number of children was updated after the booking started so should be sync'd to DPS
            profileDetailsResponse("CHILD", "4", modifiedTime = now),
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

      @Test
      fun `domestic status changed in DPS`() {
        // stub profile details returned from NOMIS
        stubGetProfileDetails(
          fromOffenderNo,
          bookingResponse(
            bookingId = 1,
            latestBooking = true,
            startDateTime = yesterday,
            // The domestic status was last updated in DPS - but should still be sync'd to DPS in this scenario
            profileDetailsResponse("MARITAL", "M", auditModuleName = synchronisationUser),
          ),
        )
        stubGetProfileDetails(
          toOffenderNo,
          bookingResponse(
            bookingId = 2,
            latestBooking = false,
            startDateTime = yesterday,
            profileDetailsResponse("MARITAL", "C"),
          ),
          bookingResponse(
            bookingId = movedBookingId,
            latestBooking = true,
            startDateTime = today,
            // The domestic status was last updated in DPS - but should still be sync'd to DPS in this scenario
            profileDetailsResponse("MARITAL", "D", modifiedTime = now, auditModuleName = synchronisationUser),
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
    }

    @Nested
    inner class Errors {
      @Test
      fun `Call to get NOMIS data fails`() {
        // error when getting NOMIS details
        nomisApi.stubGetProfileDetails(fromOffenderNo, HttpStatus.INTERNAL_SERVER_ERROR)

        // Send the event
        sendBookingMovedEvent().also {
          waitForDlqMessage()
        }

        // there should be no DPS updates
        dpsApi.verify(
          prisonerNumber = fromOffenderNo,
          type = "ignored",
          profileType = "domestic-status",
        )
        dpsApi.verify(
          prisonerNumber = toOffenderNo,
          type = "ignored",
          profileType = "domestic-status",
        )

        // there should be no NOMIS updates
        nomisSyncApi.verify(
          count = 0,
          putRequestedFor(urlPathEqualTo("/contactperson/sync/profile-details/$toOffenderNo/MARITAL")),
        )

        // check telemetry
        verify(telemetryClient).trackEvent(
          eq("contact-person-booking-moved-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "error" to "500 Internal Server Error from GET http://localhost:8081/prisoners/$fromOffenderNo/profile-details",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `Call to put DPS data fails`() {
        // stub profile details returned from NOMIS
        stubGetProfileDetails(fromOffenderNo, bookingResponse())
        stubGetProfileDetails(toOffenderNo, bookingResponse())

        // stub error from DPS update
        dpsApi.stubSyncDomesticStatus(fromOffenderNo, BAD_REQUEST)

        // there should be no DPS updates
        dpsApi.verify(
          prisonerNumber = fromOffenderNo,
          type = "ignored",
          profileType = "domestic-status",
        )
        dpsApi.verify(
          prisonerNumber = toOffenderNo,
          type = "ignored",
          profileType = "domestic-status",
        )

        // Send the event
        sendBookingMovedEvent().also {
          waitForDlqMessage()
        }

        // check telemetry
        verify(telemetryClient).trackEvent(
          eq("contact-person-booking-moved-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "error" to "400 Bad Request from PUT http://localhost:8097/sync/$fromOffenderNo/domestic-status",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `Call to sync back to NOMIS fails`() {
        // stub profile details returned from NOMIS
        stubGetProfileDetails(fromOffenderNo, bookingResponse())
        stubGetProfileDetails(toOffenderNo, bookingResponse())

        // stub error when calling sync service to sync back to NOMIS
        nomisSyncApi.stubSyncProfileDetails(toOffenderNo, "MARITAL", BAD_REQUEST)

        // Send the event
        sendBookingMovedEvent().also {
          waitForDlqMessage()
        }

        // DPS should have been updated
        dpsApi.verify(
          prisonerNumber = fromOffenderNo,
          type = "updated",
          profileType = "domestic-status",
        )
        dpsApi.verify(
          prisonerNumber = toOffenderNo,
          type = "updated",
          profileType = "domestic-status",
        )

        // check telemetry
        verify(telemetryClient).trackEvent(
          eq("contact-person-booking-moved-error"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "syncToDps" to "B1234BB-MARITAL,A1234AA-MARITAL",
                "error" to "400 Bad Request from PUT http://localhost:8098/contactperson/sync/profile-details/$toOffenderNo/MARITAL",
              ),
            )
          },
          isNull(),
        )
      }
    }
  }

  private fun sendBookingMovedEvent(): SendMessageResponse? = personalRelationshipsDomainEventsQueue.sendMessage(
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
    startDateTime: LocalDateTime = LocalDateTime.parse("2024-01-03T12:34:56"),
    vararg profileDetails: ProfileDetailsResponse = arrayOf(profileDetailsResponse("MARITAL", "M")),
  ) = BookingProfileDetailsResponse(
    bookingId = bookingId,
    startDateTime = startDateTime,
    latestBooking = latestBooking,
    profileDetails = profileDetails.asList(),
  )

  fun profileDetailsResponse(
    profileType: String,
    code: String?,
    modifiedTime: LocalDateTime = LocalDateTime.parse("2024-02-03T12:34:56"),
    auditModuleName: String = "NOMIS_MODULE",
  ) = ProfileDetailsResponse(
    type = profileType,
    code = code,
    createDateTime = LocalDateTime.parse("2024-01-03T12:34:56"),
    createdBy = "A_USER",
    modifiedDateTime = modifiedTime,
    modifiedBy = "ANOTHER_USER",
    auditModuleName = auditModuleName,
  )

  private fun waitForDlqMessage() = await untilAsserted {
    assertThat(
      personalRelationshipsDomainEventsQueue.countAllMessagesOnDLQQueue(),
    ).isEqualTo(1)
  }
}
