package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
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
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension.Companion.dpsCourtSentencingServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreateChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreatePeriodLengthResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreateSentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingSentenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCases
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreatePeriodLengthResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateSentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationSentenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.NomisPeriodLengthId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtOrderResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.LinkedCaseChargeDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenceResultCodeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RecallCustodyDate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceTermResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.COURT_SENTENCING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
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

class CourtSentencingMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var courtSentencingMappingApiMockServer: CourtSentencingMappingApiMockServer

  @Autowired
  private lateinit var courtSentencingNomisApiMockServer: CourtSentencingNomisApiMockServer

  @Nested
  @DisplayName("POST /migrate/court-sentencing")
  inner class MigrationCourtCases {
    @BeforeEach
    internal fun setUp() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    private fun WebTestClient.performMigration(body: String = "{ }"): MigrationResult = post().uri("/migrate/court-sentencing")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
      .header("Content-Type", "application/json")
      .body(BodyInserters.fromValue(body))
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

    @Test
    internal fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/court-sentencing")
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/court-sentencing")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will start processing pages of court cases`() {
      nomisApi.stubGetInitialCount(
        NomisApiExtension.COURT_SENTENCING_PRISONER_IDS,
        14,
      ) { courtSentencingNomisApiMockServer.prisonerIdsPagedResponse(it) }
      courtSentencingNomisApiMockServer.stubMultipleGetPrisonerIdCounts(totalElements = 14, pageSize = 10)
      courtSentencingNomisApiMockServer.stubMultipleGetCourtCasesByOffender(1..14)
      courtSentencingMappingApiMockServer.stubGetMigrationSummaryByOffenderNo(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubPostMigrationMapping()

      dpsCourtSentencingServer.stubPostCourtCasesForCreateMigration(response = dpsMigrationCreateResponseWithTwoAppearancesAndTwoCharges())
      courtSentencingMappingApiMockServer.stubCourtSentencingSummaryByMigrationId(count = 14)

      webTestClient.performMigration("{\"deleteExisting\": true}")

      await untilAsserted {
        assertThat(dpsCourtSentencingServer.createCourtCaseByOffenderMigrationCount()).isEqualTo(14)
      }

      // verify 2 charges are mapped for each case
      courtSentencingMappingApiMockServer.verify(
        14,
        postRequestedFor(urlPathMatching("/mapping/court-sentencing/prisoner/\\S+/court-cases"))
          .withRequestBody(
            WireMock.matchingJsonPath(
              "courtCharges.size()",
              equalTo("2"),
            ),
          ),
      )

      dpsCourtSentencingServer.verify(
        postRequestedFor(urlPathEqualTo("/legacy/court-case/migration")).withQueryParam(
          "deleteExisting",
          equalTo("true"),
        ),
      )
    }

    @Test
    internal fun `will migrate case hierarchy by offender`() {
      nomisApi.stubGetInitialCount(
        NomisApiExtension.COURT_SENTENCING_PRISONER_IDS,
        1,
      ) { courtSentencingNomisApiMockServer.prisonerIdsPagedResponse(it) }
      courtSentencingNomisApiMockServer.stubMultipleGetPrisonerIdCounts(totalElements = 1, pageSize = 10)
      courtSentencingNomisApiMockServer.stubGetCourtCasesByOffenderForMigration(
        offenderNo = "AN1",
        bookingId = NOMIS_BOOKING_ID,
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
      courtSentencingMappingApiMockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubPostMigrationMapping()

      dpsCourtSentencingServer.stubPostCourtCasesForCreateMigration(response = dpsMigrationCreateResponseWithTwoAppearancesAndTwoCharges())
      courtSentencingMappingApiMockServer.stubCourtSentencingSummaryByMigrationId(count = 1)

      webTestClient.performMigration()

      val migrationRequest: MigrationCreateCourtCases =
        CourtSentencingDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/legacy/court-case/migration")))
      assertThat(migrationRequest.prisonerId).isEqualTo("AN1")
      assertThat(migrationRequest.courtCases).hasSize(1)
      with(migrationRequest.courtCases.first()) {
        assertThat(caseId).isEqualTo(1L)
        assertThat(courtCaseLegacyData.caseReferences).hasSize(2)
        assertThat(courtCaseLegacyData.caseReferences[0].offenderCaseReference).isEqualTo("YY12345678")
        assertThat(courtCaseLegacyData.caseReferences[1].offenderCaseReference).isEqualTo("XX12345678")
        assertThat(courtCaseLegacyData.bookingId).isEqualTo(NOMIS_BOOKING_ID)
        assertThat(appearances).hasSize(1)
        with(appearances.first()) {
          assertThat(eventId).isEqualTo(NOMIS_APPEARANCE_1_ID)
          assertThat(legacyData.nomisOutcomeCode).isEqualTo("4506")
          assertThat(legacyData.outcomeConvictionFlag).isTrue
          assertThat(legacyData.outcomeDispositionCode).isEqualTo("I")
          assertThat(legacyData.outcomeDescription).isEqualTo("Adjournment")
          assertThat(legacyData.nextEventDateTime).isEqualTo("2020-02-01T00:00:00")
          assertThat(legacyData.appearanceTime).isEqualTo("12:00")
          assertThat(legacyData.postedDate).isNotNull
          assertThat(appearanceTypeUuid.toString()).isEqualTo(COURT_APPEARANCE_DPS_APPEARANCE_TYPE_UUID)
          assertThat(charges).hasSize(1)
          with(charges.first()) {
            assertThat(offenceCode).isEqualTo("RR84027")
            assertThat(legacyData.nomisOutcomeCode).isEqualTo("1081")
            assertThat(legacyData.outcomeDescription).isEqualTo("Detention and Training Order")
            assertThat(legacyData.outcomeDispositionCode).isEqualTo("F")
            assertThat(legacyData.offenceDescription).isEqualTo("Failing to stop at school crossing (horsedrawn vehicle)")
            assertThat(legacyData.outcomeConvictionFlag).isEqualTo(true)
            assertThat(chargeNOMISId).isEqualTo(3934645)
            assertThat(legacyData.postedDate).isNotNull
            assertThat(sentence?.sentenceId?.sequence).isEqualTo(NOMIS_SENTENCE_SEQUENCE_ID)
            assertThat(sentence?.returnToCustodyDate).isEqualTo("2024-01-01")
            assertThat(sentence?.periodLengths).hasSize(2)
            assertThat(sentence?.legacyData?.nomisLineReference).isEqualTo("5")
            assertThat(sentence?.legacyData?.bookingId).isEqualTo(NOMIS_BOOKING_ID)
          }
        }
      }

      dpsCourtSentencingServer.verify(
        postRequestedFor(urlPathEqualTo("/legacy/court-case/migration")).withQueryParam(
          "deleteExisting",
          equalTo("false"),
        ),
      )
    }

    @Test
    internal fun `will map result IDs from a migrated record`() {
      nomisApi.stubGetInitialCount(
        NomisApiExtension.COURT_SENTENCING_PRISONER_IDS,
        1,
      ) { courtSentencingNomisApiMockServer.prisonerIdsPagedResponse(it) }
      courtSentencingNomisApiMockServer.stubMultipleGetPrisonerIdCounts(totalElements = 1, pageSize = 10)
      courtSentencingNomisApiMockServer.stubGetCourtCasesByOffenderForMigration(
        bookingId = 3,
        caseId = NOMIS_CASE_ID,
        offenderNo = "AN1",
      )
      courtSentencingMappingApiMockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubPostMigrationMapping()

      dpsCourtSentencingServer.stubPostCourtCasesForCreateMigration(response = dpsMigrationCreateResponseWithTwoAppearancesAndTwoCharges())
      courtSentencingMappingApiMockServer.stubCourtSentencingSummaryByMigrationId(count = 1)

      webTestClient.performMigration()

      val mappingRequest: CourtCaseBatchMappingDto =
        CourtSentencingMappingApiMockServer.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/prisoner/AN1/court-cases")))
      with(mappingRequest) {
        assertThat(courtCases).hasSize(1)
        with(courtCases.first()) {
          assertThat(nomisCourtCaseId).isEqualTo(NOMIS_CASE_ID)
        }
        assertThat(courtAppearances).hasSize(2)
        with(courtAppearances[0]) {
          assertThat(nomisCourtAppearanceId).isEqualTo(NOMIS_APPEARANCE_2_ID)
          assertThat(dpsCourtAppearanceId).isEqualTo(DPS_APPEARANCE_2_ID)
        }
        with(courtAppearances[1]) {
          assertThat(nomisCourtAppearanceId).isEqualTo(NOMIS_APPEARANCE_1_ID)
          assertThat(dpsCourtAppearanceId).isEqualTo(DPS_APPEARANCE_1_ID)
        }
        assertThat(courtCharges).hasSize(2)
        with(courtCharges[0]) {
          assertThat(nomisCourtChargeId).isEqualTo(NOMIS_CHARGE_2_ID)
          assertThat(dpsCourtChargeId).isEqualTo(DPS_CHARGE_2_ID)
        }
        with(courtCharges[1]) {
          assertThat(nomisCourtChargeId).isEqualTo(NOMIS_CHARGE_1_ID)
          assertThat(dpsCourtChargeId).isEqualTo(DPS_CHARGE_1_ID)
        }
        with(sentences[0]) {
          assertThat(nomisSentenceSequence).isEqualTo(NOMIS_SENTENCE_SEQUENCE_ID)
        }
        with(sentenceTerms[0]) {
          assertThat(nomisTermSequence).isEqualTo(NOMIS_TERM_SEQUENCE_ID)
          assertThat(nomisSentenceSequence).isEqualTo(NOMIS_SENTENCE_SEQUENCE_ID)
        }
        with(sentenceTerms[1]) {
          assertThat(nomisTermSequence).isEqualTo(NOMIS_TERM_SEQUENCE_2_ID)
          assertThat(nomisSentenceSequence).isEqualTo(NOMIS_SENTENCE_SEQUENCE_ID)
        }
      }
    }

    @Test
    internal fun `will link charges between linked cases`() {
      nomisApi.stubGetInitialCount(
        NomisApiExtension.COURT_SENTENCING_PRISONER_IDS,
        1,
      ) { courtSentencingNomisApiMockServer.prisonerIdsPagedResponse(it) }
      courtSentencingNomisApiMockServer.stubMultipleGetPrisonerIdCounts(totalElements = 1, pageSize = 10)
      courtSentencingNomisApiMockServer.stubGetCourtCasesByOffenderForMigration(
        offenderNo = OFFENDER_NO,
        response = listOf(
          // original source case that is now inactive
          courtCaseResponse().copy(
            id = 1,
            combinedCaseId = 2,
            courtEvents = listOf(
              courtEventResponse(eventId = 101).copy(
                courtEventCharges = listOf(
                  courtEventChargeResponse(eventId = 101, offenderChargeId = 1001),
                ),
              ),
            ),
            caseStatus = CodeDescription("I", "Inactive"),
          ),
          // source case was linked to this case
          courtCaseResponse().copy(
            id = 2,
            combinedCaseId = null,
            sourceCombinedCaseIds = listOf(1),
            courtEvents = listOf(
              courtEventResponse(eventId = 201).copy(
                courtEventCharges = listOf(
                  courtEventChargeResponse(eventId = 201, offenderChargeId = 1001).copy(
                    linkedCaseDetails = LinkedCaseChargeDetails(
                      caseId = 1,
                      eventId = 201,
                      dateLinked = LocalDate.parse("2024-02-02"),
                    ),
                  ),
                  courtEventChargeResponse(eventId = 201, offenderChargeId = 2001),
                ),
              ),
            ),
            caseStatus = CodeDescription("A", "Active"),
          ),
          // some other normal case
          courtCaseResponse().copy(
            id = 3,
            combinedCaseId = null,
            courtEvents = listOf(
              courtEventResponse(eventId = 302).copy(
                courtEventCharges = listOf(
                  courtEventChargeResponse(eventId = 302, offenderChargeId = 3001),
                ),
              ),
            ),
            caseStatus = CodeDescription("A", "Active"),
          ),
        ),
      )
      courtSentencingMappingApiMockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)
      dpsCourtSentencingServer.stubPostCourtCasesForCreateMigration()
      courtSentencingMappingApiMockServer.stubPostMigrationMapping()

      courtSentencingMappingApiMockServer.stubCourtSentencingSummaryByMigrationId(count = 1)

      val (migrationId) = webTestClient.performMigration()

      val migrationRequest: MigrationCreateCourtCases =
        CourtSentencingDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/legacy/court-case/migration")))
      assertThat(migrationRequest.prisonerId).isEqualTo(OFFENDER_NO)
      assertThat(migrationRequest.courtCases).hasSize(3)
      val sourceLinkedCase = migrationRequest.courtCases.find { it.caseId == 1L }!!
      with(sourceLinkedCase) {
        assertThat(merged).isTrue
        assertThat(appearances).hasSize(1)
        assertThat(appearances.first().charges).hasSize(1)
        with(appearances.first().charges.first()) {
          assertThat(chargeNOMISId).isEqualTo(1001)
          assertThat(mergedFromCaseId).isNull()
          assertThat(mergedFromDate).isNull()
        }
      }
      val targetLinkedCase = migrationRequest.courtCases.find { it.caseId == 2L }!!
      with(targetLinkedCase) {
        assertThat(merged).isFalse
        assertThat(appearances).hasSize(1)
        assertThat(appearances.first().charges).hasSize(2)
        val chargeFromLinkedSource = appearances.first().charges.find { it.chargeNOMISId == 1001L }!!
        with(chargeFromLinkedSource) {
          assertThat(chargeNOMISId).isEqualTo(1001L)
          assertThat(mergedFromCaseId).isEqualTo(1L)
          assertThat(mergedFromDate).isEqualTo(LocalDate.parse("2024-02-02"))
        }
        val chargeAlreadyOnTarget = appearances.first().charges.find { it.chargeNOMISId == 2001L }!!
        with(chargeAlreadyOnTarget) {
          assertThat(chargeNOMISId).isEqualTo(2001)
          assertThat(mergedFromCaseId).isNull()
          assertThat(mergedFromDate).isNull()
        }
      }
      val normalCase = migrationRequest.courtCases.find { it.caseId == 3L }!!
      with(normalCase) {
        assertThat(merged).isNull()
        assertThat(appearances).hasSize(1)
        assertThat(appearances.first().charges).hasSize(1)
        with(appearances.first().charges.first()) {
          assertThat(chargeNOMISId).isEqualTo(3001)
          assertThat(mergedFromCaseId).isNull()
          assertThat(mergedFromDate).isNull()
        }
      }
      verify(telemetryClient).trackEvent(
        eq("court-sentencing-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )

      dpsCourtSentencingServer.verify(
        postRequestedFor(urlPathEqualTo("/legacy/court-case/migration")).withQueryParam(
          "deleteExisting",
          equalTo("false"),
        ),
      )
    }

    @Test
    internal fun `will add analytical events for starting, ending and each migrated record`() {
      nomisApi.stubGetInitialCount(
        NomisApiExtension.COURT_SENTENCING_PRISONER_IDS,
        26,
      ) { courtSentencingNomisApiMockServer.prisonerIdsPagedResponse(it) }
      courtSentencingNomisApiMockServer.stubMultipleGetPrisonerIdCounts(totalElements = 26, pageSize = 10)
      courtSentencingNomisApiMockServer.stubMultipleGetCourtCasesByOffender(1..26)
      dpsCourtSentencingServer.stubPostCourtCasesForCreateMigration()
      courtSentencingMappingApiMockServer.stubGetMigrationSummaryByOffenderNo(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubPostMigrationMapping()

      // stub 25 migrated records and 1 fake a failure
      courtSentencingMappingApiMockServer.stubCourtSentencingSummaryByMigrationId(count = 25)
      awsSqsCourtSentencingMigrationDlqClient!!.sendMessage(
        SendMessageRequest.builder().queueUrl(courtSentencingMigrationDlqUrl)
          .messageBody("""{ "message": "some error" }""").build(),
      ).get()

      webTestClient.performMigration()

      verify(telemetryClient).trackEvent(eq("court-sentencing-migration-started"), any(), isNull())
      verify(telemetryClient, times(26)).trackEvent(eq("court-sentencing-migration-entity-migrated"), any(), isNull())

      await untilAsserted {
        webTestClient.get().uri("/migrate/history/all/{migrationType}", COURT_SENTENCING)
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.size()").isEqualTo(1)
          .jsonPath("$[0].migrationId").isNotEmpty
          .jsonPath("$[0].whenStarted").isNotEmpty
          .jsonPath("$[0].whenEnded").isNotEmpty
          .jsonPath("$[0].estimatedRecordCount").isEqualTo(26)
          .jsonPath("$[0].migrationType").isEqualTo(COURT_SENTENCING.name)
          .jsonPath("$[0].status").isEqualTo("COMPLETED")
          .jsonPath("$[0].recordsMigrated").isEqualTo(25)
          .jsonPath("$[0].recordsFailed").isEqualTo(1)
      }
    }

    @Test
    internal fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      nomisApi.stubGetInitialCount(
        NomisApiExtension.COURT_SENTENCING_PRISONER_IDS,
        1,
      ) { courtSentencingNomisApiMockServer.prisonerIdsPagedResponse(it) }
      courtSentencingNomisApiMockServer.stubMultipleGetPrisonerIdCounts(totalElements = 1, pageSize = 10)
      courtSentencingNomisApiMockServer.stubGetCourtCasesByOffenderForMigration(caseId = 1)
      courtSentencingMappingApiMockServer.stubGetMigrationSummaryByOffenderNo(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubCourtSentencingSummaryByMigrationId()
      dpsCourtSentencingServer.stubPostCourtCasesForCreateMigration("05b332ad-58eb-4ec2-963c-c9c927856788")
      courtSentencingMappingApiMockServer.stubPostMigrationMappingFailureFollowedBySuccess()

      webTestClient.performMigration()

      // wait for all mappings to be created before verifying
      await untilCallTo { courtSentencingMappingApiMockServer.createMigrationMappingCount() } matches { it == 2 }

      // check that one court case is created
      assertThat(dpsCourtSentencingServer.createCourtCaseByOffenderMigrationCount()).isEqualTo(1)

      // should retry to create mapping twice
      courtSentencingMappingApiMockServer.verifyCreateMappingMigrationOffenderIds(arrayOf("1"), times = 2)
    }

    @Test
    internal fun `it will not retry after a 409 (duplicate court case written to Sentencing API)`() {
      nomisApi.stubGetInitialCount(
        NomisApiExtension.COURT_SENTENCING_PRISONER_IDS,
        1,
      ) { courtSentencingNomisApiMockServer.prisonerIdsPagedResponse(it) }
      courtSentencingNomisApiMockServer.stubMultipleGetPrisonerIdCounts(totalElements = 1, pageSize = 10)
      courtSentencingNomisApiMockServer.stubMultipleGetCourtCasesByOffender(1..1)
      courtSentencingMappingApiMockServer.stubGetMigrationSummaryByOffenderNo(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubCourtSentencingSummaryByMigrationId()
      dpsCourtSentencingServer.stubPostCourtCasesForCreateMigration("05b332ad-58eb-4ec2-963c-c9c927856788")
      courtSentencingMappingApiMockServer.stubMigrationMappingCreateConflict()

      webTestClient.performMigration()

      // wait for all mappings to be created before verifying
      await untilCallTo { courtSentencingMappingApiMockServer.createMigrationMappingCount() } matches { it == 1 }

      // check that one court case is created
      assertThat(dpsCourtSentencingServer.createCourtCaseByOffenderMigrationCount()).isEqualTo(1)

      // doesn't retry
      courtSentencingMappingApiMockServer.verifyCreateMappingMigrationOffenderIds(arrayOf("1"), times = 1)

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-court-sentencing-duplicate"),
        check {
          assertThat(it["migrationId"]).isNotNull
        },
        isNull(),
      )
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
fun dpsBookingCloneCreateResponseWithTwoAppearancesAndTwoCharges(): BookingCreateCourtCasesResponse {
  val courtCaseIds: List<BookingCreateCourtCaseResponse> = listOf(
    BookingCreateCourtCaseResponse(courtCaseUuid = DPS_COURT_CASE_ID, caseId = NOMIS_CASE_ID),
  )
  val courtChargesIds: List<BookingCreateChargeResponse> =
    listOf(
      BookingCreateChargeResponse(
        chargeUuid = UUID.fromString(DPS_CHARGE_2_ID),
        chargeNOMISId = NOMIS_CHARGE_2_ID,
      ),
      BookingCreateChargeResponse(
        chargeUuid = UUID.fromString(DPS_CHARGE_1_ID),
        chargeNOMISId = NOMIS_CHARGE_1_ID,
      ),
    )
  val courtAppearancesIds: List<BookingCreateCourtAppearanceResponse> = listOf(
    BookingCreateCourtAppearanceResponse(
      appearanceUuid = UUID.fromString(DPS_APPEARANCE_2_ID),
      eventId = NOMIS_APPEARANCE_2_ID,
    ),
    BookingCreateCourtAppearanceResponse(
      appearanceUuid = UUID.fromString(DPS_APPEARANCE_1_ID),
      eventId = NOMIS_APPEARANCE_1_ID,
    ),
  )
  val sentenceIds: List<BookingCreateSentenceResponse> = listOf(
    BookingCreateSentenceResponse(
      sentenceUuid = UUID.fromString(DPS_SENTENCE_ID),
      sentenceNOMISId = BookingSentenceId(
        offenderBookingId = NOMIS_BOOKING_ID,
        sequence = NOMIS_SENTENCE_SEQUENCE_ID.toInt(),
      ),
    ),
  )
  val sentenceTermIds: List<BookingCreatePeriodLengthResponse> = listOf(
    BookingCreatePeriodLengthResponse(
      periodLengthUuid = UUID.fromString(DPS_TERM_ID),
      sentenceTermNOMISId = NomisPeriodLengthId(
        offenderBookingId = NOMIS_BOOKING_ID,
        sentenceSequence = NOMIS_SENTENCE_SEQUENCE_ID.toInt(),
        termSequence = NOMIS_TERM_SEQUENCE_ID.toInt(),
      ),
    ),
    BookingCreatePeriodLengthResponse(
      periodLengthUuid = UUID.fromString(DPS_TERM_ID),
      sentenceTermNOMISId = NomisPeriodLengthId(
        offenderBookingId = NOMIS_BOOKING_ID,
        sentenceSequence = NOMIS_SENTENCE_SEQUENCE_ID.toInt(),
        termSequence = NOMIS_TERM_SEQUENCE_2_ID.toInt(),
      ),
    ),
  )
  return BookingCreateCourtCasesResponse(
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
)
