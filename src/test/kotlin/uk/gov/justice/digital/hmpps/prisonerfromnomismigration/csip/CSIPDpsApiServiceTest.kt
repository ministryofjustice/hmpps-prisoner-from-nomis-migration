package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsCreateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsCreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsSyncCsipRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsUpdateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsUpdateCsipReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsUpdateDecisionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsUpdateInvestigationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsUpdatePlanRequest
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
            .withRequestBody(matchingJsonPath("referral.descriptionOfConcern", equalTo("There was a worry about the offender")))
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

    @Nested
    @DisplayName("PATCH /csip-records/{recordUuid}/referral")
    inner class UpdateCSIPReport {
      private val dpsCSIPId = UUID.randomUUID().toString()

      @BeforeEach
      internal fun setUp() {
        csipDpsApi.stubUpdateCSIPReport(dpsCSIPId)

        runBlocking {
          csipService.updateCSIPReferral(dpsCSIPId, dpsUpdateCsipReferralRequest(), "JIM_ADM")
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        csipDpsApi.verify(
          patchRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will pass data to the api`() {
        csipDpsApi.verify(
          patchRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral"))
            .withRequestBody(matchingJsonPath("logCode", equalTo("ASI-001")))
            .withRequestBody(matchingJsonPath("referral.incidentDate", equalTo("2024-06-12")))
            .withRequestBody(matchingJsonPath("referral.incidentTime", equalTo("10:32:12")))
            .withRequestBody(matchingJsonPath("referral.incidentTypeCode", equalTo("INT")))
            .withRequestBody(matchingJsonPath("referral.incidentLocationCode", equalTo("LIB")))
            .withRequestBody(matchingJsonPath("referral.referredBy", equalTo("JIM_ADM")))
            .withRequestBody(matchingJsonPath("referral.refererAreaCode", equalTo("EDU")))
            .withRequestBody(matchingJsonPath("referral.isProactiveReferral", equalTo("true")))
            .withRequestBody(matchingJsonPath("referral.isStaffAssaulted", equalTo("true")))
            .withRequestBody(matchingJsonPath("referral.assaultedStaffName", equalTo("Fred Jones"))),
        )
      }
    }

    @Nested
    @DisplayName("PUT /csip-records/{recordUuid}/referral/investigation")
    inner class UpdateCSIPInvestigation {
      private val dpsCSIPId = UUID.randomUUID().toString()

      @BeforeEach
      internal fun setUp() {
        csipDpsApi.stubUpdateCSIPInvestigation(dpsCSIPId)

        runBlocking {
          csipService.updateCSIPInvestigation(dpsCSIPId, dpsUpdateInvestigationRequest(), "JIM_ADM")
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        csipDpsApi.verify(
          putRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral/investigation"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will pass data to the api`() {
        csipDpsApi.verify(
          putRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral/investigation"))
            .withRequestBody(matchingJsonPath("staffInvolved", equalTo("some people")))
            .withRequestBody(matchingJsonPath("evidenceSecured", equalTo("A piece of pipe")))
            .withRequestBody(matchingJsonPath("occurrenceReason", equalTo("bad behaviour")))
            .withRequestBody(matchingJsonPath("personsUsualBehaviour", equalTo("Good person")))
            .withRequestBody(matchingJsonPath("personsTrigger", equalTo("missed meal")))
            .withRequestBody(matchingJsonPath("protectiveFactors", equalTo("ensure taken to canteen"))),
        )
      }
    }

    @Nested
    @DisplayName("PUT /csip-records/{recordUuid}/referral/decision-and-actions")
    inner class UpdateCSIPDecision {
      private val dpsCSIPId = UUID.randomUUID().toString()

      @BeforeEach
      internal fun setUp() {
        csipDpsApi.stubUpdateCSIPDecision(dpsCSIPId)

        runBlocking {
          csipService.updateCSIPDecision(dpsCSIPId, dpsUpdateDecisionRequest(), "JIM_ADM")
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        csipDpsApi.verify(
          putRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral/decision-and-actions"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will pass data to the api`() {
        csipDpsApi.verify(
          putRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral/decision-and-actions"))
            .withRequestBody(matchingJsonPath("outcomeTypeCode", equalTo("CUR")))
            .withRequestBody(matchingJsonPath("signedOffByRoleCode", equalTo("CUSTMAN")))
            .withRequestBody(matchingJsonPath("recordedBy", equalTo("FRED_ADM")))
            .withRequestBody(matchingJsonPath("recordedByDisplayName", equalTo("Fred Admin")))
            .withRequestBody(matchingJsonPath("date", equalTo("2024-04-08")))
            .withRequestBody(matchingJsonPath("actions[0]", equalTo("NON_ASSOCIATIONS_UPDATED")))
            .withRequestBody(matchingJsonPath("actions[1]", equalTo("OBSERVATION_BOOK")))
            .withRequestBody(matchingJsonPath("actions[2]", equalTo("SERVICE_REFERRAL")))
            .withRequestBody(matchingJsonPath("actionOther", equalTo("Some other info here"))),
        )
      }
    }

    @Nested
    @DisplayName("PUT /csip-records/{recordUuid}/plan")
    inner class UpdateCSIPPlan {
      private val dpsCSIPId = UUID.randomUUID().toString()

      @BeforeEach
      internal fun setUp() {
        csipDpsApi.stubUpdateCSIPPlan(dpsCSIPId)

        runBlocking {
          csipService.updateCSIPPlan(dpsCSIPId, dpsUpdatePlanRequest(), "JIM_ADM")
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        csipDpsApi.verify(
          putRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/plan"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will pass data to the api`() {
        csipDpsApi.verify(
          putRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/plan"))
            .withRequestBody(matchingJsonPath("caseManager", equalTo("C Jones")))
            .withRequestBody(matchingJsonPath("reasonForPlan", equalTo("helper")))
            .withRequestBody(matchingJsonPath("firstCaseReviewDate", equalTo("2024-04-15"))),
        )
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
        csipDpsApi.stubInsertCSIPSCS(dpsCSIPId = dpsCSIPId)

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
        csipDpsApi.verify(
          postRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral/safer-custody-screening"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will pass data to the api`() {
        csipDpsApi.verify(
          postRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral/safer-custody-screening"))
            .withRequestBody(matchingJsonPath("outcomeTypeCode", equalTo("CUR")))
            .withRequestBody(matchingJsonPath("date", equalTo("2024-04-08")))
            .withRequestBody(matchingJsonPath("reasonForDecision", equalTo("There is a reason for the decision - it goes here")))
            .withRequestBody(matchingJsonPath("recordedBy", equalTo("BOB_ADM")))
            .withRequestBody(matchingJsonPath("recordedByDisplayName", equalTo("Bob Admin"))),

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
        csipDpsApi.stubInsertCSIPFactor(dpsCSIPReportId = dpsCSIPReportId, dpsCSIPFactorId = dpsCSIPFactorId)

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
        csipDpsApi.verify(
          postRequestedFor(urlEqualTo("/csip-records/$dpsCSIPReportId/referral/contributory-factors"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will pass data to the api`() {
        csipDpsApi.verify(
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
        csipDpsApi.stubUpdateCSIPFactor(dpsCSIPFactorId = dpsCSIPFactorId)

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
        csipDpsApi.verify(
          patchRequestedFor(urlEqualTo("/csip-records/referral/contributory-factors/$dpsCSIPFactorId"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will pass data to the api`() {
        csipDpsApi.verify(
          patchRequestedFor(urlEqualTo("/csip-records/referral/contributory-factors/$dpsCSIPFactorId"))
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
          csipDpsApi.stubDeleteCSIPFactor(dpsCSIPFactorId)
          runBlocking {
            csipService.deleteCSIPFactor(csipFactorId = dpsCSIPFactorId)
          }
        }

        @Test
        fun `should call api with OAuth2 token`() {
          csipDpsApi.verify(
            deleteRequestedFor(urlEqualTo("/csip-records/referral/contributory-factors/$dpsCSIPFactorId"))
              .withHeader("Authorization", equalTo("Bearer ABCDE")),
          )
        }
      }

      @Nested
      inner class CSIPFactorAlreadyDeleted {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubDeleteCSIPFactorNotFound()
        }

        @Test
        fun `should throw error when not found`() = runTest {
          assertThrows<WebClientResponseException.NotFound> {
            csipService.deleteCSIPFactor(csipFactorId = dpsCSIPFactorId)
          }
        }
      }
    }

    @Nested
    @DisplayName("DELETE /csip-records/plan/identified-needs/{dpsCSIPPlanId}")
    inner class DeleteCSIPPlan {
      private val dpsCSIPPlanId = UUID.randomUUID().toString()

      @Nested
      inner class CSIPPlanExists {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubDeleteCSIPPlan(dpsCSIPPlanId)
          runBlocking {
            csipService.deleteCSIPPlan(csipPlanId = dpsCSIPPlanId)
          }
        }

        @Test
        fun `should call api with OAuth2 token`() {
          csipDpsApi.verify(
            deleteRequestedFor(urlEqualTo("/csip-records/plan/identified-needs/$dpsCSIPPlanId"))
              .withHeader("Authorization", equalTo("Bearer ABCDE")),
          )
        }
      }

      @Nested
      inner class CSIPPlanAlreadyDeleted {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubDeleteCSIPPlanNotFound()
        }

        @Test
        fun `should throw error when not found`() = runTest {
          assertThrows<WebClientResponseException.NotFound> {
            csipService.deleteCSIPPlan(csipPlanId = dpsCSIPPlanId)
          }
        }
      }
    }

    @Nested
    @DisplayName("DELETE /csip-records/referral/investigation/interviews/{dpsCSIPInterviewId}")
    inner class DeleteCSIPInterview {
      private val dpsCSIPInterviewId = UUID.randomUUID().toString()

      @Nested
      inner class CSIPInterviewExists {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubDeleteCSIPInterview(dpsCSIPInterviewId)
          runBlocking {
            csipService.deleteCSIPInterview(csipInterviewId = dpsCSIPInterviewId)
          }
        }

        @Test
        fun `should call api with OAuth2 token`() {
          csipDpsApi.verify(
            deleteRequestedFor(urlEqualTo("/csip-records/referral/investigation/interviews/$dpsCSIPInterviewId"))
              .withHeader("Authorization", equalTo("Bearer ABCDE")),
          )
        }
      }

      @Nested
      inner class CSIPInterviewAlreadyDeleted {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubDeleteCSIPInterviewNotFound()
        }

        @Test
        fun `should throw error when not found`() = runTest {
          assertThrows<WebClientResponseException.NotFound> {
            csipService.deleteCSIPInterview(csipInterviewId = dpsCSIPInterviewId)
          }
        }
      }
    }

    @Nested
    @DisplayName("DELETE /csip-records/plan/reviews/attendees/{dpsCSIPAttendeeId}")
    inner class DeleteCSIPAttendee {
      private val dpsCSIPAttendeeId = UUID.randomUUID().toString()

      @Nested
      inner class CSIPAttendeeExists {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubDeleteCSIPAttendee(dpsCSIPAttendeeId)
          runBlocking {
            csipService.deleteCSIPAttendee(csipAttendeeId = dpsCSIPAttendeeId)
          }
        }

        @Test
        fun `should call api with OAuth2 token`() {
          csipDpsApi.verify(
            deleteRequestedFor(urlEqualTo("/csip-records/plan/reviews/attendees/$dpsCSIPAttendeeId"))
              .withHeader("Authorization", equalTo("Bearer ABCDE")),
          )
        }
      }

      @Nested
      inner class CSIPAttendeeAlreadyDeleted {
        @BeforeEach
        internal fun setUp() {
          csipDpsApi.stubDeleteCSIPAttendeeNotFound()
        }

        @Test
        fun `should throw error when not found`() = runTest {
          assertThrows<WebClientResponseException.NotFound> {
            csipService.deleteCSIPAttendee(csipAttendeeId = dpsCSIPAttendeeId)
          }
        }
      }
    }
  }
}
