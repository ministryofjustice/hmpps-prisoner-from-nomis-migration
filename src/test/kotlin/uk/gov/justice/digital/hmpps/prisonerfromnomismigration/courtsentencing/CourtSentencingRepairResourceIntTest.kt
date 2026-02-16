package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension.Companion.dpsCourtSentencingServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreatePeriodLengthResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateSentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationSentenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.NomisPeriodLengthId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseBatchMappingDto
import java.util.*

class CourtSentencingRepairResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var courtSentencingMappingApiMockServer: CourtSentencingMappingApiMockServer

  @Autowired
  private lateinit var courtSentencingNomisApiMockServer: CourtSentencingNomisApiMockServer

  @Nested
  @DisplayName("POST /prisoners/{offenderNo}/court-sentencing/court-cases/repair")
  inner class MigrationCourtCases {
    val offenderNo = "A1234KT"

    @Nested
    inner class Security {

      @Test
      internal fun `must have valid token`() {
        webTestClient.post().uri("/prisoners/{offenderNo}/court-sentencing/court-cases/repair", offenderNo)
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      internal fun `must have correct role`() {
        webTestClient.post().uri("/prisoners/{offenderNo}/court-sentencing/court-cases/repair", offenderNo)
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      internal fun setup() {
        courtSentencingNomisApiMockServer.stubGetCourtCasesByOffender(offenderNo)
        dpsCourtSentencingServer.stubPostCourtCasesForCreateMigration(response = dpsMigrationCreateResponse())
        courtSentencingMappingApiMockServer.stubPostMigrationMapping()

        webTestClient.post().uri("/prisoners/{offenderNo}/court-sentencing/court-cases/repair", offenderNo)
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will re-migrate case to DPS`() {
        dpsCourtSentencingServer.verify(
          postRequestedFor(urlPathEqualTo("/legacy/court-case/migration")).withQueryParam(
            "deleteExisting",
            equalTo("true"),
          ),
        )
      }

      @Test
      fun `will recreate mappings`() {
        val mapping: CourtCaseBatchMappingDto = CourtSentencingMappingApiMockServer.getRequestBody(postRequestedFor(urlPathMatching("/mapping/court-sentencing/prisoner/$offenderNo/court-cases")))
        assertThat(mapping.label).hasSize(19)
      }
    }

    @Nested
    inner class HappyPathWithTransientError {

      @BeforeEach
      internal fun setup() {
        courtSentencingNomisApiMockServer.stubGetCourtCasesByOffender(offenderNo)
        dpsCourtSentencingServer.stubPostCourtCasesForCreateMigration(response = dpsMigrationCreateResponse())
        courtSentencingMappingApiMockServer.stubPostMigrationMappingFailureFollowedBySuccess()

        webTestClient.post().uri("/prisoners/{offenderNo}/court-sentencing/court-cases/repair", offenderNo)
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk

        await untilAsserted {
          courtSentencingMappingApiMockServer.verify(
            2,
            postRequestedFor(urlPathMatching("/mapping/court-sentencing/prisoner/$offenderNo/court-cases")),

          )
        }
      }

      @Test
      fun `will re-migrate case to DPS`() {
        dpsCourtSentencingServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/legacy/court-case/migration")).withQueryParam(
            "deleteExisting",
            equalTo("true"),
          ),
        )
      }

      @Test
      fun `will recreate mappings retying twice`() {
        courtSentencingMappingApiMockServer.verify(
          2,
          postRequestedFor(urlPathMatching("/mapping/court-sentencing/prisoner/$offenderNo/court-cases")),

        )
      }
    }
  }

  @Nested
  @DisplayName("PUT /prisoners/{offenderNo}/booking-id/{bookingId}/court-sentencing/court-cases/{caseId}/status/repair")
  inner class RepairCourtCase {
    val offenderNo = "A1234KT"
    val bookingId = 12344321L
    val caseId = 1234L
    val dpsCaseId = UUID.randomUUID().toString()

    @Nested
    inner class Security {

      @Test
      internal fun `must have valid token`() {
        webTestClient.put().uri("/prisoners/{offenderNo}/booking-id/{bookingId}/court-sentencing/court-cases/{caseId}/status/repair", offenderNo, bookingId, caseId)
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      internal fun `must have correct role`() {
        webTestClient.put().uri("/prisoners/{offenderNo}/booking-id/{bookingId}/court-sentencing/court-cases/{caseId}/status/repair", offenderNo, bookingId, caseId)
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      internal fun setup() {
        courtSentencingNomisApiMockServer.stubGetCourtCase(
          courtCaseId = caseId,
          bookingId = bookingId,
          offenderNo = offenderNo,
        )

        courtSentencingMappingApiMockServer.stubGetByNomisId(
          nomisCourtCaseId = caseId,
          dpsCourtCaseId = dpsCaseId,
        )

        dpsCourtSentencingServer.stubPutCourtCaseForUpdate(courtCaseId = dpsCaseId)

        webTestClient.put().uri("/prisoners/{offenderNo}/booking-id/{bookingId}/court-sentencing/court-cases/{caseId}/status/repair", offenderNo, bookingId, caseId)
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will update DPS with the changes`() {
        dpsCourtSentencingServer.verify(
          1,
          putRequestedFor(urlPathEqualTo("/legacy/court-case/$dpsCaseId"))
            .withRequestBody(matchingJsonPath("bookingId", equalTo(bookingId.toString()))),
        )
      }

      @Test
      fun `will track a telemetry event for success`() {
        verify(telemetryClient).trackEvent(
          eq("court-case-synchronisation-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisBookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisCourtCaseId"]).isEqualTo(caseId.toString())
            assertThat(it["dpsCourtCaseId"]).isEqualTo(dpsCaseId)
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("court-sentencing-prisoner-case-status-repaired"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisBookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisCourtCaseId"]).isEqualTo(caseId.toString())
          },
          isNull(),
        )
      }
    }
  }
}

private fun dpsMigrationCreateResponse(): MigrationCreateCourtCasesResponse {
  val courtCaseIds: List<MigrationCreateCourtCaseResponse> = listOf(
    MigrationCreateCourtCaseResponse(courtCaseUuid = "99C", caseId = 1L),
  )
  val courtChargesIds: List<MigrationCreateChargeResponse> =
    listOf(
      MigrationCreateChargeResponse(
        chargeUuid = UUID.fromString("a04f7a8d-61aa-222c-9395-f4dc62f36ab0"),
        chargeNOMISId = 222L,
      ),
    )
  val courtAppearancesIds: List<MigrationCreateCourtAppearanceResponse> = listOf(
    MigrationCreateCourtAppearanceResponse(
      appearanceUuid = UUID.fromString("a04f7a8d-61aa-222a-9395-f4dc62f36ab0"),
      eventId = 22L,
    ),
  )
  val sentenceIds: List<MigrationCreateSentenceResponse> = listOf(
    MigrationCreateSentenceResponse(
      sentenceUuid = UUID.fromString("a14f7a8d-61aa-111c-9395-f4dc62f36ab0"),
      sentenceNOMISId = MigrationSentenceId(
        offenderBookingId = 2L,
        sequence = 112,
      ),
    ),
  )
  val sentenceTermIds: List<MigrationCreatePeriodLengthResponse> = listOf(
    MigrationCreatePeriodLengthResponse(
      periodLengthUuid = UUID.fromString("b14f7a8d-61aa-111c-9395-f4dc62f36ab0"),
      sentenceTermNOMISId = NomisPeriodLengthId(
        offenderBookingId = 2L,
        sentenceSequence = 112,
        termSequence = 111,
      ),
    ),
  )
  return MigrationCreateCourtCasesResponse(
    courtCases = courtCaseIds,
    appearances = courtAppearancesIds,
    charges = courtChargesIds,
    sentences = sentenceIds,
    sentenceTerms = sentenceTermIds,
  )
}
