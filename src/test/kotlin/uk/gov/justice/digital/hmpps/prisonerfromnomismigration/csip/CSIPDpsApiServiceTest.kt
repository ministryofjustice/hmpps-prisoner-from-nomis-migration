package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPDpsApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPDpsApiMockServer.Companion.dpsSyncCsipRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.DefaultLegacyActioned
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.MoveCsipRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime
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
              .withRequestBodyJsonPath("legacyId", "1234")
              .withRequestBodyJsonPath("logCode", "ASI-001")
              .withRequestBodyJsonPath("referral.incidentDate", "2024-06-12")
              .withRequestBodyJsonPath("referral.incidentTypeCode", "INT")
              .withRequestBodyJsonPath("referral.incidentLocationCode", "LIB")
              .withRequestBodyJsonPath("referral.referredBy", "JIM_ADM")
              .withRequestBodyJsonPath("referral.refererAreaCode", "EDU")
              .withRequestBodyJsonPath("referral.incidentInvolvementCode", "PER")
              .withRequestBodyJsonPath("referral.descriptionOfConcern", "There was a worry about the offender")
              .withRequestBodyJsonPath("referral.knownReasons", "known reasons details go in here")
              .withRequestBodyJsonPath("referral.incidentTime", "10:32:12")
              .withRequestBodyJsonPath("referral.isProactiveReferral", "true")
              .withRequestBodyJsonPath("referral.isStaffAssaulted", "true")
              .withRequestBodyJsonPath("referral.assaultedStaffName", "Fred Jones")
              .withRequestBodyJsonPath("referral.otherInformation", "other information goes in here")
              .withRequestBodyJsonPath("referral.isSaferCustodyTeamInformed", "NO"),
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
    @DisplayName("DELETE /sync/csip-records/{dpsReportId}")
    inner class DeleteCSIPReport {
      private val dpsCSIPId = UUID.randomUUID().toString()
      private val dateTime = LocalDateTime.parse("2024-07-15T11:15:12")

      @Nested
      inner class CSIPExists {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubCSIPDelete(dpsCSIPId = dpsCSIPId)
          runBlocking {
            csipService.deleteCSIP(csipReportId = dpsCSIPId, DefaultLegacyActioned(actionedAt = dateTime))
          }
        }

        @Test
        fun `should call api with OAuth2 token`() {
          csipDpsApi.verify(
            deleteRequestedFor(urlEqualTo("/sync/csip-records/$dpsCSIPId"))
              .withHeader("Authorization", equalTo("Bearer ABCDE"))
              .withRequestBodyJsonPath("actionedAt", dateTime),
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
            csipService.deleteCSIP(csipReportId = dpsCSIPId, DefaultLegacyActioned(actionedAt = LocalDateTime.now()))
          }
        }
      }
    }

    @Nested
    @DisplayName("PUT /sync/csip-records/move")
    inner class MoveOffender {
      private val uuids = setOf(
        UUID.fromString("17a5aa28-dec1-460d-8260-ac5c8dd7be76"),
        UUID.fromString("2a19146d-77c6-42e6-918a-89821d3f54eb"),
      )

      @Nested
      inner class CSIPExists {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubMoveOffenderForCSIP()
          runBlocking {
            csipService.moveCSIPs(
              MoveCsipRequest(
                fromPrisonNumber = "A1234BC",
                toPrisonNumber = "D5678EF",
                recordUuids = uuids,
              ),
            )
          }
        }

        @Test
        fun `should call api with OAuth2 token`() {
          csipDpsApi.verify(
            putRequestedFor(urlEqualTo("/sync/csip-records/move"))
              .withHeader("Authorization", equalTo("Bearer ABCDE"))
              .withRequestBodyJsonPath("fromPrisonNumber", "A1234BC")
              .withRequestBodyJsonPath("toPrisonNumber", "D5678EF")
              .withRequestBodyJsonPath("recordUuids[0]", "17a5aa28-dec1-460d-8260-ac5c8dd7be76")
              .withRequestBodyJsonPath("recordUuids[1]", "2a19146d-77c6-42e6-918a-89821d3f54eb"),
          )
        }
      }

      @Nested
      inner class CSIPMoveFailed {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubMoveOffenderForCSIP(HttpStatus.INTERNAL_SERVER_ERROR)
        }

        @Test
        fun `should throw error when not found`() = runTest {
          assertThrows<WebClientResponseException.InternalServerError> {
            csipService.moveCSIPs(
              MoveCsipRequest(
                fromPrisonNumber = "A1234BC",
                toPrisonNumber = "D5678EF",
                recordUuids = uuids,
              ),
            )
          }
        }
      }
    }
  }
}
