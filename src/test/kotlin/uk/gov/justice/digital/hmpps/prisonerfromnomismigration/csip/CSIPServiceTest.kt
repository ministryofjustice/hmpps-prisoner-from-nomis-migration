package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.notContaining
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension.Companion.csipApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsCreateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsCreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsCreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsMigrateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsUpdateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import java.util.UUID

@SpringAPIServiceTest
@Import(CSIPService::class, CSIPConfiguration::class)
internal class CSIPServiceTest {

  @Autowired
  private lateinit var csipService: CSIPService

  @Nested
  @DisplayName("CSIPReport")
  inner class CSIPReport {

    @Nested
    @DisplayName("POST /migrate/prisoners/{offenderNo}/csip-records")
    inner class CreateCSIPForMigration {

      @BeforeEach
      internal fun setUp() {
        csipApi.stubCSIPMigrate()

        runBlocking {
          csipService.migrateCSIP("A1234BC", dpsMigrateCsipRecordRequest())
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        csipApi.verify(
          postRequestedFor(urlEqualTo("/migrate/prisoners/A1234BC/csip-records"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will pass data to the api`() {
        csipApi.verify(
          postRequestedFor(urlEqualTo("/migrate/prisoners/A1234BC/csip-records"))
            .withRequestBody(matchingJsonPath("logNumber", equalTo("ASI-001")))
            .withRequestBody(matchingJsonPath("referral.incidentDate", equalTo("2024-06-12")))
            .withRequestBody(matchingJsonPath("referral.incidentTypeCode", equalTo("INT")))
            .withRequestBody(matchingJsonPath("referral.incidentLocationCode", equalTo("LIB")))
            .withRequestBody(matchingJsonPath("referral.referredBy", equalTo("JIM_ADM")))
            .withRequestBody(matchingJsonPath("referral.refererAreaCode", equalTo("EDU")))
            .withRequestBody(matchingJsonPath("referral.incidentInvolvementCode", equalTo("PER")))
            .withRequestBody(matchingJsonPath("referral.descriptionOfConcern", equalTo("There was a worry about the offender")))
            .withRequestBody(matchingJsonPath("referral.knownReasons", equalTo("known reasons details go in here")))
            .withRequestBody(notContaining("referral.contributoryFactors"))
            .withRequestBody(matchingJsonPath("referral.incidentTime", equalTo("10:32:12")))
            .withRequestBody(notContaining("referral.referralSummary"))
            .withRequestBody(matchingJsonPath("referral.isProactiveReferral", equalTo("true")))
            .withRequestBody(matchingJsonPath("referral.isStaffAssaulted", equalTo("true")))
            .withRequestBody(matchingJsonPath("referral.assaultedStaffName", equalTo("Fred Jones")))
            .withRequestBody(notContaining("referral.otherInformation"))
            .withRequestBody(notContaining("referral.isSaferCustodyTeamInformed")),
        )
      }
    }

    @Nested
    @DisplayName("POST /prisoners/{offenderNo}/csip-records")
    inner class CreateCSIP {
      @BeforeEach
      internal fun setUp() {
        csipApi.stubCSIPInsert()

        runBlocking {
          csipService.createCSIPReport("A1234BC", dpsCreateCsipRecordRequest(), "JIM_ADM")
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        csipApi.verify(
          postRequestedFor(urlEqualTo("/prisoners/A1234BC/csip-records"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will pass data to the api`() {
        csipApi.verify(
          postRequestedFor(urlEqualTo("/prisoners/A1234BC/csip-records"))
            .withRequestBody(matchingJsonPath("logNumber", equalTo("ASI-001")))
            .withRequestBody(matchingJsonPath("referral.incidentDate", equalTo("2024-06-12")))
            .withRequestBody(matchingJsonPath("referral.incidentTime", equalTo("10:32:12")))
            .withRequestBody(matchingJsonPath("referral.incidentTypeCode", equalTo("INT")))
            .withRequestBody(matchingJsonPath("referral.incidentLocationCode", equalTo("LIB")))
            .withRequestBody(matchingJsonPath("referral.referredBy", equalTo("JIM_ADM")))
            .withRequestBody(matchingJsonPath("referral.refererAreaCode", equalTo("EDU")))
            .withRequestBody(notContaining("referral.contributoryFactors"))
            .withRequestBody(notContaining("referral.referralSummary"))
            .withRequestBody(matchingJsonPath("referral.isProactiveReferral", equalTo("true")))
            .withRequestBody(matchingJsonPath("referral.isStaffAssaulted", equalTo("true")))
            .withRequestBody(matchingJsonPath("referral.assaultedStaffName", equalTo("Fred Jones")))
            .withRequestBody(notContaining("referral.otherInformation"))
            .withRequestBody(notContaining("referral.isSaferCustodyTeamInformed")),
        )
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
          csipApi.stubCSIPDelete(dpsCSIPId = dpsCSIPId)
          runBlocking {
            csipService.deleteCSIP(csipReportId = dpsCSIPId)
          }
        }

        @Test
        fun `should call api with OAuth2 token`() {
          csipApi.verify(
            deleteRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId"))
              .withHeader("Authorization", equalTo("Bearer ABCDE")),
          )
        }
      }

      @Nested
      inner class CSIPAlreadyDeleted {
        @BeforeEach
        internal fun setUp() {
          csipApi.stubCSIPDeleteNotFound()
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

  @Nested
  inner class SafetyCustodyScreening {
    @Nested
    @DisplayName("POST /csip-records/{dpsReportId}/referral/safer-custody-screening")
    inner class CreateCSIPSCS {
      private val dpsCSIPId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e6"

      @BeforeEach
      internal fun setUp() {
        csipApi.stubCSIPSCSInsert(dpsCSIPId = dpsCSIPId)

        runBlocking {
          csipService.createCSIPSaferCustodyScreening(
            csipReportId = dpsCSIPId,
            csipSCS = dpsCreateSaferCustodyScreeningOutcomeRequest(),
            "JIM_ADM",
          )
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        csipApi.verify(
          postRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral/safer-custody-screening"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will pass data to the api`() {
        csipApi.verify(
          postRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral/safer-custody-screening"))
            .withRequestBody(matchingJsonPath("outcomeTypeCode", equalTo("CUR")))
            .withRequestBody(matchingJsonPath("date", equalTo("2024-04-08")))
            .withRequestBody(matchingJsonPath("reasonForDecision", equalTo("There is a reason for the decision - it goes here"))),

        )
      }
    }
  }

  @Nested
  @DisplayName("CSIPFactor")
  inner class CSIPFactor {
    @Nested
    @DisplayName("POST /csip-records/{dpsReportId}/referral/contributory-factors")
    inner class CreateCSIPFactor {
      private val dpsCSIPReportId = UUID.randomUUID().toString()
      private val dpsCSIPFactorId = UUID.randomUUID().toString()

      @BeforeEach
      internal fun setUp() {
        csipApi.stubCSIPFactorInsert(dpsCSIPReportId = dpsCSIPReportId, dpsCSIPFactorId = dpsCSIPFactorId)

        runBlocking {
          csipService.createCSIPFactor(
            csipReportId = dpsCSIPReportId,
            csipFactor = dpsCreateContributoryFactorRequest(),
            "JIM_ADM",
          )
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        csipApi.verify(
          postRequestedFor(urlEqualTo("/csip-records/$dpsCSIPReportId/referral/contributory-factors"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will pass data to the api`() {
        csipApi.verify(
          postRequestedFor(urlEqualTo("/csip-records/$dpsCSIPReportId/referral/contributory-factors"))
            .withRequestBody(matchingJsonPath("factorTypeCode", equalTo("BUL")))
            .withRequestBody(matchingJsonPath("comment", equalTo("Offender causes trouble"))),
        )
      }
    }

    @Nested
    @DisplayName("PATCH /csip-records/referral/contributory-factors/{contributorFactorUuid}")
    inner class UpdateCSIPFactor {
      private val dpsCSIPFactorId = UUID.randomUUID().toString()

      @BeforeEach
      internal fun setUp() {
        csipApi.stubCSIPFactorUpdate(dpsCSIPFactorId = dpsCSIPFactorId)

        runBlocking {
          csipService.updateCSIPFactor(
            csipFactorId = dpsCSIPFactorId,
            csipFactor = dpsUpdateContributoryFactorRequest(),
            "JIM_ADM",
          )
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        csipApi.verify(
          patchRequestedFor(urlEqualTo("/csip-records/referral/contributory-factors/$dpsCSIPFactorId"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will pass data to the api`() {
        csipApi.verify(
          patchRequestedFor(urlEqualTo("/csip-records/referral/contributory-factors/$dpsCSIPFactorId"))
            .withRequestBody(matchingJsonPath("factorTypeCode", equalTo("BUL")))
            .withRequestBody(matchingJsonPath("comment", equalTo("Offender causes trouble"))),
        )
      }
    }

    @Nested
    @DisplayName("DELETE /csip-records/referral/contributory-factors/{contributorFactorUuid}")
    inner class DeleteCSIPFactor {
      private val dpsCSIPFactorId = UUID.randomUUID().toString()

      @Nested
      inner class CSIPFactorExists {
        @BeforeEach
        internal fun setUp() {
          csipApi.stubCSIPFactorDelete(dpsCSIPFactorId)
          runBlocking {
            csipService.deleteCSIPFactor(csipFactorId = dpsCSIPFactorId)
          }
        }

        @Test
        fun `should call api with OAuth2 token`() {
          csipApi.verify(
            deleteRequestedFor(urlEqualTo("/csip-records/referral/contributory-factors/$dpsCSIPFactorId"))
              .withHeader("Authorization", equalTo("Bearer ABCDE")),
          )
        }
      }

      @Nested
      inner class CSIPFactorAlreadyDeleted {
        @BeforeEach
        internal fun setUp() {
          csipApi.stubCSIPFactorDeleteNotFound()
        }

        @Test
        fun `should throw error when not found`() = runTest {
          assertThrows<WebClientResponseException.NotFound> {
            csipService.deleteCSIPFactor(csipFactorId = dpsCSIPFactorId)
          }
        }
      }
    }
  }
}
