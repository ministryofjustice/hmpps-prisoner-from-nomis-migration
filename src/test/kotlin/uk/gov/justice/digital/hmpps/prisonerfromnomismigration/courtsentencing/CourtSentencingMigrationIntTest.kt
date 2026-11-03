package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ReactiveHttpOutputMessage
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension.Companion.dpsCourtSentencingServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreatePeriodLengthResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateSentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationSentenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.NomisPeriodLengthId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtOrderResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenceResultCodeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RecallCustodyDate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceTermResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.COURT_SENTENCING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val OFFENDER_NO = "AN1"
private const val NOMIS_CASE_ID = 1L
private const val NOMIS_BOOKING_ID = 2L
private const val NOMIS_APPEARANCE_1_ID = 11L
private const val NOMIS_APPEARANCE_2_ID = 22L
private const val DPS_APPEARANCE_1_ID = "a04f7a8d-61aa-111a-9395-f4dc62f36ab0"
private const val DPS_APPEARANCE_2_ID = "a04f7a8d-61aa-222a-9395-f4dc62f36ab0"
private const val NOMIS_CHARGE_1_ID = 111L
private const val NOMIS_CHARGE_2_ID = 222L
private const val DPS_CHARGE_1_ID = "a04f7a8d-61aa-111c-9395-f4dc62f36ab0"
private const val DPS_CHARGE_2_ID = "a04f7a8d-61aa-222c-9395-f4dc62f36ab0"
private const val DPS_COURT_CASE_ID = "99C"
private const val DPS_SENTENCE_ID = "a14f7a8d-61aa-111c-9395-f4dc62f36ab0"
private const val DPS_TERM_ID = "b14f7a8d-61aa-111c-9395-f4dc62f36ab0"
private const val NOMIS_SENTENCE_SEQUENCE_ID = 112L
private const val NOMIS_TERM_SEQUENCE_ID = 111L
private const val NOMIS_TERM_SEQUENCE_2_ID = 222L

data class MigrationResult(val migrationId: String)

private const val PAGE_SIZE = 10

@TestPropertySource(properties = ["courtsentencing.page.size=$PAGE_SIZE"])
class CourtSentencingMigrationIntTest(
  @Autowired private val courtSentencingMappingApiMockServer: CourtSentencingMappingApiMockServer,
  @Autowired private val courtSentencingNomisApiMockServer: CourtSentencingNomisApiMockServer,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : CourtSentencingIntegrationTestBase() {

  @Nested
  @DisplayName("POST /migrate/court-sentencing")
  inner class MigrationCourtCharges {
    @BeforeEach
    internal fun setUp() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    private fun WebTestClient.performMigration(): MigrationResult = post().uri("/migrate/court-sentencing")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
      .also {
        waitUntilCompleted()
      }

    private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
      verify(telemetryClient).trackEvent(
        eq("court-sentencing-migration-completed"),
        any(),
        isNull(),
      )
    }

    @Nested
    inner class Security {
      @Test
      internal fun `must have valid token to start migration`() {
        webTestClient.post().uri("/migrate/court-sentencing")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      internal fun `must have correct role to start migration`() {
        webTestClient.post().uri("/migrate/court-sentencing")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isForbidden
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      internal fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtEventChargeIds(
          listOf(CourtEventChargeIdResponse(eventId = NOMIS_APPEARANCE_1_ID, offenderChargeId = NOMIS_CHARGE_1_ID)),
        )
        courtSentencingNomisApiMockServer.stubGetCourtEventChargeForMigration(
          eventId = NOMIS_APPEARANCE_1_ID,
          offenderChargeId = NOMIS_CHARGE_1_ID,
          response = courtEventChargeResponse(NOMIS_APPEARANCE_1_ID, NOMIS_CHARGE_1_ID).copy(
            propertyValue = BigDecimal(
              "123.45",
            ),
          ),
        )
        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(NOMIS_APPEARANCE_1_ID, DPS_APPEARANCE_1_ID)
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(NOMIS_CHARGE_1_ID, DPS_CHARGE_1_ID)
      }

      @Test
      internal fun `will retrieve the court event charge ids and each charge from NOMIS`() {
        webTestClient.performMigration()

        nomisApi.verify(getRequestedFor(urlPathEqualTo("/court-event-charges/ids/all-from-id")))
        nomisApi.verify(getRequestedFor(urlEqualTo("/court-event-charges/$NOMIS_APPEARANCE_1_ID/charges/$NOMIS_CHARGE_1_ID")))
      }

      @Test
      internal fun `will not create any mappings`() {
        webTestClient.performMigration()

        courtSentencingMappingApiMockServer.verify(0, postRequestedFor(anyUrl()))
      }

      @Test
      internal fun `will not update DPS while the migration is a dry run`() {
        webTestClient.performMigration()

        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }

      @Test
      internal fun `will add analytical events for starting, ending and each migrated record`() {
        webTestClient.performMigration()

        verify(telemetryClient).trackEvent(eq("court-sentencing-migration-started"), any(), isNull())
        verify(telemetryClient).trackEvent(
          eq("court-event-charge-migration-entity-migrated"),
          check {
            assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_APPEARANCE_1_ID.toString())
            assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_CHARGE_1_ID.toString())
            assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_1_ID)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_APPEARANCE_1_ID)
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(eq("court-sentencing-migration-completed"), any(), isNull())
      }

      @Test
      internal fun `will mark the migration as completed`() {
        val result = webTestClient.performMigration()

        await untilAsserted {
          webTestClient.get().uri("/migrate/history/{migrationId}", result.migrationId)
            .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.migrationId").isEqualTo(result.migrationId)
            .jsonPath("$.migrationType").isEqualTo(COURT_SENTENCING.name)
            .jsonPath("$.estimatedRecordCount").isEqualTo(0)
            .jsonPath("$.status").isEqualTo("COMPLETED")
        }
      }
    }

    @Nested
    inner class MultipleCharges {
      private val charges = (1L..5L).map { CourtEventChargeIdResponse(eventId = it * 10, offenderChargeId = it * 100) }

      @BeforeEach
      internal fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtEventChargeIds(charges)
        charges.forEach {
          courtSentencingNomisApiMockServer.stubGetCourtEventChargeForMigration(
            eventId = it.eventId,
            offenderChargeId = it.offenderChargeId,
          )
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            it.eventId,
            "a04f7a8d-61aa-${it.eventId}a-9395-f4dc62f36ab0",
          )
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            it.offenderChargeId,
            "a04f7a8d-61aa-${it.offenderChargeId}c-9395-f4dc62f36ab0",
          )
        }
      }

      @Test
      internal fun `will migrate every charge without updating DPS`() {
        webTestClient.performMigration()

        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
        verify(telemetryClient, times(charges.size)).trackEvent(
          eq("court-event-charge-migration-entity-migrated"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    inner class MultiplePages {
      private val charges = courtSentencingNomisApiMockServer.courtEventChargeIds(14)

      @BeforeEach
      internal fun setUp() {
        courtSentencingNomisApiMockServer.stubMultipleGetCourtEventChargeIds(
          totalElements = charges.size,
          pageSize = PAGE_SIZE,
        )
        charges.forEach {
          courtSentencingNomisApiMockServer.stubGetCourtEventChargeForMigration(
            eventId = it.eventId,
            offenderChargeId = it.offenderChargeId,
          )
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            it.eventId,
            "a04f7a8d-61aa-${it.eventId}a-9395-f4dc62f36ab0",
          )
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            it.offenderChargeId,
            "a04f7a8d-61aa-${it.offenderChargeId}c-9395-f4dc62f36ab0",
          )
        }
      }

      @Test
      internal fun `will start processing pages of court event charges`() {
        webTestClient.performMigration()

        // 14 charges with a page size of 10: a full page, a partial page, then an empty page to finish
        fun idsFrom(offenderChargeId: Long, eventId: Long) = getRequestedFor(urlPathEqualTo("/court-event-charges/ids/all-from-id"))
          .withQueryParam("offenderChargeId", equalTo(offenderChargeId.toString()))
          .withQueryParam("eventId", equalTo(eventId.toString()))
          .withQueryParam("pageSize", equalTo("$PAGE_SIZE"))
        nomisApi.verify(1, idsFrom(0, 0))
        nomisApi.verify(1, idsFrom(charges[9].offenderChargeId, charges[9].eventId))
        nomisApi.verify(1, idsFrom(charges[13].offenderChargeId, charges[13].eventId))
        nomisApi.verify(3, getRequestedFor(urlPathEqualTo("/court-event-charges/ids/all-from-id")))

        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
        verify(telemetryClient, times(charges.size)).trackEvent(
          eq("court-event-charge-migration-entity-migrated"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    inner class NoChargesToMigrate {
      @BeforeEach
      internal fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtEventChargeIds(emptyList())
      }

      @Test
      internal fun `will complete without calling DPS`() {
        webTestClient.performMigration()

        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
        verify(telemetryClient, times(0)).trackEvent(
          eq("court-event-charge-migration-entity-migrated"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    inner class MissingMappings {
      @BeforeEach
      internal fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtEventChargeIds(
          listOf(CourtEventChargeIdResponse(eventId = NOMIS_APPEARANCE_1_ID, offenderChargeId = NOMIS_CHARGE_1_ID)),
        )
        courtSentencingNomisApiMockServer.stubGetCourtEventChargeForMigration(
          eventId = NOMIS_APPEARANCE_1_ID,
          offenderChargeId = NOMIS_CHARGE_1_ID,
        )
      }

      @Test
      internal fun `will track a failure and not update DPS when the court appearance mapping is missing`() {
        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(HttpStatus.NOT_FOUND)
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(NOMIS_CHARGE_1_ID, DPS_CHARGE_1_ID)

        webTestClient.performMigration()

        verify(telemetryClient).trackEvent(
          eq("court-event-charge-migration-entity-failed"),
          check {
            assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_APPEARANCE_1_ID.toString())
            assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_CHARGE_1_ID.toString())
            assertThat(it["reason"]).isEqualTo("court appearance mapping not found")
          },
          isNull(),
        )
        verify(telemetryClient, times(0)).trackEvent(
          eq("court-event-charge-migration-entity-migrated"),
          any(),
          isNull(),
        )
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }

      @Test
      internal fun `will track a failure and not update DPS when the court charge mapping is missing`() {
        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(NOMIS_APPEARANCE_1_ID, DPS_APPEARANCE_1_ID)
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisIdNotFound(NOMIS_CHARGE_1_ID)

        webTestClient.performMigration()

        verify(telemetryClient).trackEvent(
          eq("court-event-charge-migration-entity-failed"),
          check {
            assertThat(it["reason"]).isEqualTo("court charge mapping not found")
          },
          isNull(),
        )
        verify(telemetryClient, times(0)).trackEvent(
          eq("court-event-charge-migration-entity-migrated"),
          any(),
          isNull(),
        )
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }
  }

  @Nested
  @DisplayName("GET /migrate/court-sentencing/offender-payload/{offenderNo}")
  inner class MigrationDebug {

    @Test
    internal fun `must have valid token to retrieve migration payload`() {
      webTestClient.get().uri("/migrate/court-sentencing/offender-payload/AN1")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to retrieve migration payload`() {
      webTestClient.get().uri("/migrate/court-sentencing/offender-payload/AN1")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `migration payload can be accessed for debug purposes`() {
      courtSentencingNomisApiMockServer.stubGetCourtCasesByOffenderForMigration(
        offenderNo = "AN1",
        bookingId = 3,
        caseId = 1,
        caseIdentifiers = listOf(
          buildCaseIdentifierResponse(reference = "YY12345678"),
          buildCaseIdentifierResponse(reference = "XX12345678"),
        ),
        courtEvents = listOf(buildCourtEventResponse()),
        sentences = listOf(
          buildSentenceResponse(
            sentenceTerms = listOf(
              buildSentenceTermResponse(),
              buildSentenceTermResponse(termSequence = NOMIS_TERM_SEQUENCE_2_ID),
            ),
            recallCustodyDate = RecallCustodyDate(
              returnToCustodyDate = LocalDate.parse("2024-01-01"),
              recallLength = 14,
            ),
          ),
        ),
      )

      webTestClient.get().uri("/migrate/court-sentencing/offender-payload/AN1")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("prisonerId").isEqualTo("AN1")
        .jsonPath("courtCases.size()").isEqualTo(1)
    }
  }
}

fun someMigrationFilter(): BodyInserter<String, ReactiveHttpOutputMessage> = BodyInserters.fromValue(
  """
  {
  }
  """.trimIndent(),
)

// charges can be shared across appearances
fun dpsMigrationCreateResponseWithTwoAppearancesAndTwoCharges(): MigrationCreateCourtCasesResponse {
  val courtCaseIds: List<MigrationCreateCourtCaseResponse> = listOf(
    MigrationCreateCourtCaseResponse(courtCaseUuid = DPS_COURT_CASE_ID, caseId = NOMIS_CASE_ID),
  )
  val courtChargesIds: List<MigrationCreateChargeResponse> =
    listOf(
      MigrationCreateChargeResponse(
        chargeUuid = UUID.fromString(DPS_CHARGE_2_ID),
        chargeNOMISId = NOMIS_CHARGE_2_ID,
      ),
      MigrationCreateChargeResponse(
        chargeUuid = UUID.fromString(DPS_CHARGE_1_ID),
        chargeNOMISId = NOMIS_CHARGE_1_ID,
      ),
    )
  val courtAppearancesIds: List<MigrationCreateCourtAppearanceResponse> = listOf(
    MigrationCreateCourtAppearanceResponse(
      appearanceUuid = UUID.fromString(DPS_APPEARANCE_2_ID),
      eventId = NOMIS_APPEARANCE_2_ID,
    ),
    MigrationCreateCourtAppearanceResponse(
      appearanceUuid = UUID.fromString(DPS_APPEARANCE_1_ID),
      eventId = NOMIS_APPEARANCE_1_ID,
    ),
  )
  val sentenceIds: List<MigrationCreateSentenceResponse> = listOf(
    MigrationCreateSentenceResponse(
      sentenceUuid = UUID.fromString(DPS_SENTENCE_ID),
      sentenceNOMISId = MigrationSentenceId(
        offenderBookingId = NOMIS_BOOKING_ID,
        sequence = NOMIS_SENTENCE_SEQUENCE_ID.toInt(),
      ),
    ),
  )
  val sentenceTermIds: List<MigrationCreatePeriodLengthResponse> = listOf(
    MigrationCreatePeriodLengthResponse(
      periodLengthUuid = UUID.fromString(DPS_TERM_ID),
      sentenceTermNOMISId = NomisPeriodLengthId(
        offenderBookingId = NOMIS_BOOKING_ID,
        sentenceSequence = NOMIS_SENTENCE_SEQUENCE_ID.toInt(),
        termSequence = NOMIS_TERM_SEQUENCE_ID.toInt(),
      ),
    ),
    MigrationCreatePeriodLengthResponse(
      periodLengthUuid = UUID.fromString(DPS_TERM_ID),
      sentenceTermNOMISId = NomisPeriodLengthId(
        offenderBookingId = NOMIS_BOOKING_ID,
        sentenceSequence = NOMIS_SENTENCE_SEQUENCE_ID.toInt(),
        termSequence = NOMIS_TERM_SEQUENCE_2_ID.toInt(),
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

fun dpsMigrationCreateResponse(
  courtCases: List<Pair<String, Long>>,
  charges: List<Pair<String, Long>>,
  courtAppearances: List<Pair<String, Long>>,
  sentences: List<Pair<String, MigrationSentenceId>>,
  sentenceTerms: List<Pair<String, NomisPeriodLengthId>>,
): MigrationCreateCourtCasesResponse = MigrationCreateCourtCasesResponse(
  courtCases = courtCases.map { MigrationCreateCourtCaseResponse(courtCaseUuid = it.first, caseId = it.second) },
  appearances = courtAppearances.map {
    MigrationCreateCourtAppearanceResponse(
      appearanceUuid = UUID.fromString(it.first),
      eventId = it.second,
    )
  },
  charges = charges.map {
    MigrationCreateChargeResponse(
      chargeUuid = UUID.fromString(it.first),
      chargeNOMISId = it.second,
    )
  },
  sentences = sentences.map {
    MigrationCreateSentenceResponse(
      sentenceUuid = UUID.fromString(it.first),
      sentenceNOMISId = it.second,
    )
  },
  sentenceTerms = sentenceTerms.map {
    MigrationCreatePeriodLengthResponse(
      periodLengthUuid = UUID.fromString(it.first),
      sentenceTermNOMISId = it.second,
    )
  },
)

fun buildSentenceTermResponse(
  termSequence: Long = NOMIS_TERM_SEQUENCE_ID,
) = SentenceTermResponse(
  termSequence = termSequence,
  startDate = LocalDate.of(2020, 4, 4),
  lifeSentenceFlag = false,
  sentenceTermType = CodeDescription("IMP", "Imprisonment"),
  years = 6,
  months = 5,
  weeks = 2,
  days = 3,
  hours = 0,
  prisonId = "OUT",
  createdByUsername = "msmith",
)

fun buildSentenceResponse(
  bookingId: Long = NOMIS_BOOKING_ID,
  sentenceTerms: List<SentenceTermResponse>,
  sentenceSequence: Long = NOMIS_SENTENCE_SEQUENCE_ID,
  courtOrder: CourtOrderResponse = buildCourtOrderResponse(),
  recallCustodyDate: RecallCustodyDate? = null,
) = SentenceResponse(
  bookingId = bookingId,
  sentenceSeq = sentenceSequence,
  status = "Active",
  calculationType = CodeDescription("IND", "Desc"),
  startDate = LocalDate.of(2020, 4, 4),
  endDate = LocalDate.of(2020, 4, 4),
  category = CodeDescription("cat", "Category"),
  createdDateTime = LocalDateTime.now(),
  sentenceTerms = sentenceTerms,
  offenderCharges = listOf(
    OffenderChargeResponse(
      id = 3934645,
      offence = OffenceResponse(
        offenceCode = "RR84027",
        statuteCode = "RR84",
        description = "Failing to stop at school crossing (horsedrawn vehicle)",
      ),
      mostSeriousFlag = true,
      createdByUsername = "msmith",
    ),
  ),
  prisonId = "MDI",
  createdByUsername = "BNELL",
  courtOrder = courtOrder,
  recallCustodyDate = recallCustodyDate,
  missingCourtOffenderChargeIds = emptyList(),
  lineSequence = 5,
)

fun buildCourtOrderResponse(eventId: Long = NOMIS_APPEARANCE_1_ID) = CourtOrderResponse(
  eventId = eventId,
  id = 2,
  courtDate = LocalDate.of(2020, 4, 4),
  issuingCourt = "MDI",
  orderType = "IMP",
  orderStatus = "Active",
  sentencePurposes = emptyList(),
)

fun buildCaseIdentifierResponse(reference: String = "AB12345678"): CaseIdentifierResponse = CaseIdentifierResponse(
  type = "CASE/INFO#",
  reference = reference,
  createDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
)

fun buildCourtEventResponse(
  courtAppearanceId: Long = NOMIS_APPEARANCE_1_ID,
  offenderNo: String = OFFENDER_NO,
  courtCaseId: Long = NOMIS_CASE_ID,
  courtId: String = "DER",
  eventDateTime: LocalDateTime = LocalDateTime.parse("2020-01-01T12:00:00"),
  nextEventDateTime: LocalDateTime = LocalDateTime.parse("2020-02-01T00:00:00"),
  courtEventCharges: List<CourtEventChargeResponse> = listOf(
    CourtEventChargeResponse(
      eventId = NOMIS_APPEARANCE_1_ID,
      offenderCharge = OffenderChargeResponse(
        id = 3934645,
        offence = OffenceResponse(
          offenceCode = "RR84027",
          statuteCode = "RR84",
          description = "Failing to stop at school crossing (horsedrawn vehicle)",
        ),
        mostSeriousFlag = false,
        offenceDate = LocalDate.parse("2024-01-02"),
        resultCode1 = OffenceResultCodeResponse(
          chargeStatus = "A",
          code = "1081",
          description = "Detention and Training Order",
          dispositionCode = "F",
          conviction = true,
        ),
        createdByUsername = "msmith",
      ),
      createdByUsername = "msmith",
      mostSeriousFlag = false,
      resultCode1 = OffenceResultCodeResponse(
        chargeStatus = "A",
        code = "1081",
        description = "Detention and Training Order",
        dispositionCode = "F",
        conviction = true,
      ),
    ),
  ),
): CourtEventResponse = CourtEventResponse(
  id = courtAppearanceId,
  offenderNo = offenderNo,
  caseId = courtCaseId,
  courtId = courtId,
  courtEventCharges = courtEventCharges,
  createdDateTime = LocalDateTime.now(),
  createdByUsername = "Q1251T",
  courtEventType = CodeDescription("CRT", "Court Appearance"),
  outcomeReasonCode = OffenceResultCodeResponse(
    chargeStatus = "A",
    code = "4506",
    description = "Adjournment",
    dispositionCode = "I",
    conviction = true,
  ),
  eventStatus = CodeDescription("SCH", "Scheduled (Approved)"),
  eventDateTime = eventDateTime,
  courtOrders = emptyList(),
  nextEventDateTime = nextEventDateTime,
  isClone = false,
)
