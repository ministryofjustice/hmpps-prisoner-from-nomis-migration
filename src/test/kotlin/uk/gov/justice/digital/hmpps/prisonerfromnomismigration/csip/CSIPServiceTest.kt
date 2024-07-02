package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.notContaining
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension.Companion.csipApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsCreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsCreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsMigrateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(CSIPService::class, CSIPConfiguration::class)
internal class CSIPServiceTest {

  @Autowired
  private lateinit var csipService: CSIPService

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
          .withRequestBody(matchingJsonPath("referral.incidentTime", equalTo("10:00")))
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
          .withRequestBody(matchingJsonPath("referral.incidentTypeCode", equalTo("INT")))
          .withRequestBody(matchingJsonPath("referral.incidentLocationCode", equalTo("LIB")))
          .withRequestBody(matchingJsonPath("referral.referredBy", equalTo("JIM_ADM")))
          .withRequestBody(matchingJsonPath("referral.refererAreaCode", equalTo("EDU")))
          .withRequestBody(notContaining("referral.contributoryFactors"))
          .withRequestBody(matchingJsonPath("referral.incidentTime", equalTo("10:00")))
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
  @DisplayName("DELETE /csip-records/{cspReportId}")
  inner class DeleteCSIP {
    private val dpsCSIPId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5"

    @Nested
    inner class CSIPExists {
      @BeforeEach
      internal fun setUp() {
        csipApi.stubCSIPDelete()
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
      fun `should ignore 404 error`() {
        runBlocking {
          csipService.deleteCSIP(csipReportId = dpsCSIPId)
        }
      }
    }
  }

  @Nested
  @DisplayName("POST /csip-records/{cspReportId}/referral/safer-custody-screening")
  inner class CreateCSIPSCS {
    private val dpsCSIPId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e6"

    @BeforeEach
    internal fun setUp() {
      csipApi.stubCSIPInsertSCS(dpsCSIPId = dpsCSIPId)

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
