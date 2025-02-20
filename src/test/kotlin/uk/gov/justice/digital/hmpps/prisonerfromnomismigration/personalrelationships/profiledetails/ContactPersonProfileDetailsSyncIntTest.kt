package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.StringValuePattern
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse

class ContactPersonProfileDetailsSyncIntTest(
  @Autowired private val nomisApi: ContactPersonProfileDetailsNomisApiMockServer,
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiMockServer,
  @Autowired private val syncService: ContactPersonProfileDetailsSyncService,
) : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("OFFENDER_PHYSICAL_DETAILS-CHANGED for contact person details")
  inner class DomesticStatusChanged {
    @Nested
    inner class HappyPath {
      @Test
      fun `should sync new profile details`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          nomisResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                bookingId = 12345,
                startDateTime = "2024-09-03T12:34:56",
                endDateTime = null,
                latestBooking = true,
                profileDetails = listOf(
                  profileDetails(
                    type = "MARITAL",
                    code = "M",
                    createDateTime = "2024-09-04T12:34:56",
                    createdBy = "A_USER",
                    modifiedDateTime = null,
                    modifiedBy = null,
                    auditModuleName = "NOMIS",
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncDomesticStatus(response = dpsResponse())

        // TODO replace this with a send event when listener implemented
        syncService.profileDetailsChanged(profileType = "MARITAL", offenderNo = "A1234AA", bookingId = 12345)

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify(
          dpsUpdates = mapOf(
            "domesticStatusCode" to equalTo("M"),
            "createdDateTime" to equalTo("2024-09-04T12:34:56"),
            "createdBy" to equalTo("A_USER"),
            "latestBooking" to equalTo("true"),
          ),
        )
        verifyTelemetry(
          telemetryType = "success",
          profileType = "domestic-status",
          offenderNo = "A1234AA",
          bookingId = 12345,
          latestBooking = true,
        )
      }

      @Test
      fun `should sync a null value`() = runTest {
        nomisApi.stubGetProfileDetails(
          "A1234AA",
          nomisResponse(
            bookings = listOf(
              booking(
                profileDetails = listOf(
                  profileDetails(
                    type = "MARITAL",
                    code = null,
                    modifiedDateTime = "2024-09-05T12:34:56",
                    modifiedBy = "ANOTHER_USER",
                  ),
                ),
              ),
            ),
          ),
        )
        dpsApi.stubSyncDomesticStatus(response = dpsResponse())

        // TODO replace this with a send event when listener implemented
        syncService.profileDetailsChanged(profileType = "MARITAL", offenderNo = "A1234AA", bookingId = 12345)

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify(
          dpsUpdates = mapOf(
            "domesticStatusCode" to absent(),
            "createdDateTime" to equalTo("2024-09-05T12:34:56"),
            "createdBy" to equalTo("ANOTHER_USER"),
            "latestBooking" to equalTo("true"),
          ),
        )
        verifyTelemetry(latestBooking = true)
      }
    }

    @Nested
    inner class Errors {
      @Test
      fun `should handle failed NOMIS API call`() = runTest {
        nomisApi.stubGetProfileDetails(status = NOT_FOUND)

        // TODO replace this with a send event when listener implemented and test msg on DLQ
        assertThrows<WebClientResponseException.NotFound> {
          syncService.profileDetailsChanged(profileType = "MARITAL", offenderNo = "A1234AA", bookingId = 12345)
        }

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify(type = "ignored")
        verifyTelemetry(telemetryType = "error", errorReason = "404 Not Found from GET http://localhost:8081/prisoners/A1234AA/profile-details")
      }

      @Test
      fun `should handle failed DPS API call`() = runTest {
        nomisApi.stubGetProfileDetails("A1234AA", nomisResponse(offenderNo = "A1234AA"))
        dpsApi.stubSyncDomesticStatus(status = INTERNAL_SERVER_ERROR)

        // TODO replace this with a send event when listener implemented and test msg on DLQ
        assertThrows<WebClientResponseException.InternalServerError> {
          syncService.profileDetailsChanged(profileType = "MARITAL", offenderNo = "A1234AA", bookingId = 12345)
        }

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))
        dpsApi.verify()
        verifyTelemetry(telemetryType = "error", errorReason = "500 Internal Server Error from PUT http://localhost:8097/sync/domestic-status/A1234AA")
      }
    }

    private fun dpsResponse(id: Long = 321) = DomesticStatusSyncResponse(id)

    private fun ContactPersonProfileDetailsDpsApiMockServer.verify(
      type: String = "updated",
      dpsUpdates: Map<String, StringValuePattern> = mapOf(),
    ) {
      // For updates verify we sent the correct details to the DPS API
      if (type == "updated") {
        verify(
          putRequestedFor(urlPathEqualTo("/sync/domestic-status/A1234AA"))
            .apply {
              dpsUpdates.forEach { (jsonPath, pattern) ->
                withRequestBody(matchingJsonPath(jsonPath, pattern))
              }
            },
        )
      } else {
        // If not updated we shouldn't call the DPS API
        verify(0, putRequestedFor(urlPathEqualTo("/sync/domestic-status/A1234AA")))
      }
    }
  }

  private fun nomisResponse(
    offenderNo: String = "A1234AA",
    bookings: List<BookingProfileDetailsResponse> = listOf(booking()),
  ) = PrisonerProfileDetailsResponse(
    offenderNo = offenderNo,
    bookings = bookings,
  )

  private fun booking(
    bookingId: Long = 12345,
    startDateTime: String = "2024-09-03T12:34:56",
    endDateTime: String? = null,
    latestBooking: Boolean = true,
    profileDetails: List<ProfileDetailsResponse> = listOf(profileDetails()),
  ) = BookingProfileDetailsResponse(
    bookingId = bookingId,
    startDateTime = startDateTime,
    endDateTime = endDateTime,
    latestBooking = latestBooking,
    profileDetails = profileDetails,
  )

  private fun profileDetails(
    type: String = "MARITAL",
    code: String? = "M",
    createDateTime: String = "2024-09-04T12:34:56",
    createdBy: String = "A_USER",
    modifiedDateTime: String? = null,
    modifiedBy: String? = null,
    auditModuleName: String = "NOMIS",
  ) = ProfileDetailsResponse(
    type = type,
    code = code,
    createDateTime = createDateTime,
    createdBy = createdBy,
    modifiedDateTime = modifiedDateTime,
    modifiedBy = modifiedBy,
    auditModuleName = auditModuleName,
  )

  private fun verifyTelemetry(
    profileType: String = "domestic-status",
    telemetryType: String = "success",
    offenderNo: String = "A1234AA",
    bookingId: Long = 12345,
    latestBooking: Boolean? = null,
    ignoreReason: String? = null,
    errorReason: String? = null,
  ) = verify(telemetryClient).trackEvent(
    eq("contact-person-$profileType-synchronisation-$telemetryType"),
    check {
      assertThat(it["offenderNo"]).isEqualTo(offenderNo)
      assertThat(it["bookingId"]).isEqualTo("$bookingId")
      latestBooking?.run { assertThat(it["latestBooking"]).isEqualTo("$latestBooking") }
      ignoreReason?.run { assertThat(it["reason"]).isEqualTo(this) }
      errorReason?.run { assertThat(it["error"]).isEqualTo(this) }
      if (ignoreReason == null && errorReason == null) {
        assertThat(it["dpsId"]).isEqualTo("321")
      }
    },
    isNull(),
  )
}
