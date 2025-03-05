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
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerDomesticStatusResponse
import java.time.LocalDateTime
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

class ContactPersonMoveBookingIntTest(
  @Autowired private val nomisApi: ContactPersonProfileDetailsNomisApiMockServer,
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiMockServer,
  @Autowired private val nomisSyncApi: ContactPersonNomisSyncApiMockServer,
) : SqsIntegrationTestBase() {

  private val movedBookingId = 12345L
  private val toOffenderNo = "A1234AA"
  private val fromOffenderNo = "B1234BB"

  @Nested
  @DisplayName("prison-offender-events.prisoner.booking.moved")
  inner class BookingMoved {
    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetProfileDetails(
          offenderNo = fromOffenderNo,
          bookingId = null,
          profileTypes = listOf("MARITAL"),
          response = PrisonerProfileDetailsResponse(
            offenderNo = fromOffenderNo,
            bookings = listOf(
              BookingProfileDetailsResponse(
                bookingId = 1,
                startDateTime = "2024-01-03T12:34:56",
                latestBooking = true,
                profileDetails = listOf(
                  ProfileDetailsResponse(
                    type = "MARITAL",
                    code = "M",
                    createDateTime = "${LocalDateTime.now()}",
                    createdBy = "A_USER",
                    modifiedDateTime = "${LocalDateTime.now()}",
                    modifiedBy = "ANOTHER_USER",
                    auditModuleName = "NOMIS",
                  ),
                ),
              ),
            ),
          ),
        )

        nomisApi.stubGetProfileDetails(
          offenderNo = toOffenderNo,
          bookingId = null,
          profileTypes = listOf("MARITAL"),
          response = PrisonerProfileDetailsResponse(
            offenderNo = toOffenderNo,
            bookings = listOf(
              BookingProfileDetailsResponse(
                bookingId = movedBookingId,
                startDateTime = "2024-02-03T12:34:56",
                latestBooking = true,
                profileDetails = listOf(
                  ProfileDetailsResponse(
                    type = "MARITAL",
                    code = "D",
                    createDateTime = "${LocalDateTime.now()}",
                    createdBy = "A_USER",
                    modifiedDateTime = "${LocalDateTime.now()}",
                    modifiedBy = "ANOTHER_USER",
                    auditModuleName = "NOMIS",
                  ),
                ),
              ),
              BookingProfileDetailsResponse(
                bookingId = 2,
                startDateTime = "2024-01-03T12:34:56",
                latestBooking = false,
                profileDetails = listOf(
                  ProfileDetailsResponse(
                    type = "MARITAL",
                    code = "C",
                    createDateTime = "${LocalDateTime.now()}",
                    createdBy = "A_USER",
                    modifiedDateTime = "${LocalDateTime.now()}",
                    modifiedBy = "ANOTHER_USER",
                    auditModuleName = "NOMIS",
                  ),
                ),
              ),
            ),
          ),
        )

        dpsApi.stubSyncDomesticStatus(fromOffenderNo, SyncPrisonerDomesticStatusResponse(123, true))
        dpsApi.stubSyncDomesticStatus(toOffenderNo, SyncPrisonerDomesticStatusResponse(345, true))
        nomisSyncApi.stubSyncProfileDetails(toOffenderNo, "MARITAL")
      }

      @Test
      fun `domestic status changed in NOMIS`() {
        sendBookingMovedEvent().also {
          waitForAnyProcessingToComplete("contact-person-booking-moved")
        }

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
        nomisSyncApi.verify(
          putRequestedFor(urlPathEqualTo("/contactperson/sync/profile-details/$toOffenderNo/MARITAL")),
        )

        // telemetry
        org.mockito.kotlin.verify(telemetryClient).trackEvent(
          eq("contact-person-booking-moved"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "bookingId" to movedBookingId.toString(),
                "toOffenderNo" to toOffenderNo,
                "fromOffenderNo" to fromOffenderNo,
                "syncToDps" to "B1234BB-MARITAL,A1234AA-MARITAL",
                "syncToNomis" to "A1234AA-MARITAL",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      @Disabled("TODO")
      fun `domestic status not changed in NOMIS`() {
      }

      @Test
      @Disabled("TODO")
      fun `number of children changed in NOMIS`() {
      }

      @Test
      @Disabled("TODO")
      fun `number of children not changed in NOMIS`() {
      }

      @Test
      @Disabled("TODO")
      fun `Both profile details changed in NOMIS`() {
      }
    }

    @Nested
    inner class Errors {
      @Test
      @Disabled("TODO")
      fun `Call to get NOMIS data fails`() {}

      @Test
      @Disabled("TODO")
      fun `Call to put DPS data fails`() {}

      @Test
      @Disabled("TODO")
      fun `Call to sync back to NOMIS fails`() {}
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
}
