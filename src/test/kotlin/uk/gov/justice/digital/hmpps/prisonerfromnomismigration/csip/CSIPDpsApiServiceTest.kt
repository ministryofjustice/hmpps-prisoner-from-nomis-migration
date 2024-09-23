package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsSyncCsipRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import java.util.UUID

@SpringAPIServiceTest
@Import(CSIPDpsApiService::class, CSIPConfiguration::class)
internal class CSIPDpsApiServiceTest {

  @Autowired
  private lateinit var csipService: CSIPDpsApiService

  @Nested
  @DisplayName("CSIPReport")
  inner class CSIPReport {

    @Nested
    @DisplayName("PUT /sync/csip-records")
    inner class SyncCSIPReport {
      @Nested
      inner class CSIPSyncSuccess {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubSyncCSIPReport()

          runBlocking {
            csipService.migrateCSIP(dpsSyncCsipRequest())
          }
        }

        @Test
        fun `should call api with OAuth2 token`() {
          csipDpsApi.verify(
            putRequestedFor(urlEqualTo("/sync/csip-records"))
              .withHeader("Authorization", equalTo("Bearer ABCDE")),
          )
        }

        @Test
        fun `will pass data to the api (this is a subset of the full data passed)`() {
          csipDpsApi.verify(
            putRequestedFor(urlEqualTo("/sync/csip-records"))
              .withRequestBody(matchingJsonPath("legacyId", equalTo("1234")))
              .withRequestBody(matchingJsonPath("logCode", equalTo("ASI-001")))
              .withRequestBody(matchingJsonPath("referral.incidentDate", equalTo("2024-06-12")))
              .withRequestBody(matchingJsonPath("referral.incidentTypeCode", equalTo("INT")))
              .withRequestBody(matchingJsonPath("referral.incidentLocationCode", equalTo("LIB")))
              .withRequestBody(matchingJsonPath("referral.referredBy", equalTo("JIM_ADM")))
              .withRequestBody(matchingJsonPath("referral.refererAreaCode", equalTo("EDU")))
              .withRequestBody(matchingJsonPath("referral.incidentInvolvementCode", equalTo("PER")))
              .withRequestBody(
                matchingJsonPath(
                  "referral.descriptionOfConcern",
                  equalTo("There was a worry about the offender"),
                ),
              )
              .withRequestBody(matchingJsonPath("referral.knownReasons", equalTo("known reasons details go in here")))
              .withRequestBody(matchingJsonPath("referral.incidentTime", equalTo("10:32:12")))
              .withRequestBody(matchingJsonPath("referral.isProactiveReferral", equalTo("true")))
              .withRequestBody(matchingJsonPath("referral.isStaffAssaulted", equalTo("true")))
              .withRequestBody(matchingJsonPath("referral.assaultedStaffName", equalTo("Fred Jones")))
              .withRequestBody(matchingJsonPath("referral.otherInformation", equalTo("other information goes in here")))
              .withRequestBody(matchingJsonPath("referral.isSaferCustodyTeamInformed", equalTo("NO"))),
          )
        }
      }

      @Nested
      inner class CSIPRethrowsLoggedBadRequest {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubSyncCSIPReport(status = HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `should still throw caught error when bad request thrown`() = runTest {
          assertThrows<WebClientResponseException.BadRequest> {
            csipService.migrateCSIP(dpsSyncCsipRequest())
          }
        }
      }
    }

    @Nested
    @DisplayName("DELETE /csip-records/{dpsReportId}")
    inner class DeleteCSIPReport {
      private val dpsCSIPId = UUID.randomUUID().toString()

      @Nested
      inner class CSIPExists {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubCSIPDelete(dpsCSIPId = dpsCSIPId)
          runBlocking {
            csipService.deleteCSIP(csipReportId = dpsCSIPId)
          }
        }

        @Test
        fun `should call api with OAuth2 token`() {
          csipDpsApi.verify(
            deleteRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId"))
              .withHeader("Authorization", equalTo("Bearer ABCDE")),
          )
        }
      }

      @Nested
      inner class CSIPAlreadyDeleted {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubCSIPDeleteNotFound()
        }

        @Test
        fun `should throw error when not found`() = runTest {
          assertThrows<WebClientResponseException.NotFound> {
            csipService.deleteCSIP(csipReportId = dpsCSIPId)
          }
        }
      }
    }
  }
}
