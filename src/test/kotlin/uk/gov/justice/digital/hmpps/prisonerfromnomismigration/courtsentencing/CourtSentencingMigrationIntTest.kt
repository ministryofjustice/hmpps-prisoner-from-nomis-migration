package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterEach
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenceResultCodeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.COMPLETED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val OFFENDER_NO = "AN1"
private const val NOMIS_CASE_ID = 1L
private const val NOMIS_APPEARANCE_1_ID = 11L
private const val NOMIS_APPEARANCE_2_ID = 22L
private const val DPS_APPEARANCE_1_ID = "a04f7a8d-61aa-111a-9395-f4dc62f36ab0"
private const val DPS_APPEARANCE_2_ID = "a04f7a8d-61aa-222a-9395-f4dc62f36ab0"
private const val NOMIS_CHARGE_1_ID = 111L
private const val NOMIS_CHARGE_2_ID = 222L
private const val DPS_CHARGE_1_ID = "a04f7a8d-61aa-111c-9395-f4dc62f36ab0"
private const val DPS_CHARGE_2_ID = "a04f7a8d-61aa-222c-9395-f4dc62f36ab0"
private const val DPS_COURT_CASE_ID = "99C"

data class MigrationResult(val migrationId: String)

class CourtSentencingMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Autowired
  private lateinit var courtSentencingMappingApiMockServer: CourtSentencingMappingApiMockServer

  @Autowired
  private lateinit var courtSentencingNomisApiMockServer: CourtSentencingNomisApiMockServer

  @Nested
  @DisplayName("POST /migrate/court-sentencing")
  inner class MigrationCourtCases {
    @BeforeEach
    internal fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    private fun WebTestClient.performMigration(body: String = "{ }"): MigrationResult = post().uri("/migrate/court-sentencing")
      .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
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

      webTestClient.performMigration()

      await untilAsserted {
        assertThat(dpsCourtSentencingServer.createCourtCaseByOffenderMigrationCount()).isEqualTo(14)
      }

      // verify 2 charges are mapped for each case
      courtSentencingMappingApiMockServer.verify(
        14,
        WireMock.postRequestedFor(urlPathMatching("/mapping/court-sentencing/prisoner/\\S+/court-cases"))
          .withRequestBody(
            WireMock.matchingJsonPath(
              "courtCharges.size()",
              WireMock.equalTo("2"),
            ),
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
        bookingId = 3,
        caseId = 1,
        caseIndentifiers = listOf(
          buildCaseIdentifierResponse(reference = "YY12345678"),
          buildCaseIdentifierResponse(reference = "XX12345678"),
        ),
        courtEvents = listOf(buildCourtEventResponse()),
      )
      courtSentencingMappingApiMockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubPostMigrationMapping()

      dpsCourtSentencingServer.stubPostCourtCasesForCreateMigration(response = dpsMigrationCreateResponseWithTwoAppearancesAndTwoCharges())
      courtSentencingMappingApiMockServer.stubCourtSentencingSummaryByMigrationId(count = 1)

      webTestClient.performMigration()

      await untilAsserted {
        dpsCourtSentencingServer.verify(
          1,
          WireMock.postRequestedFor(WireMock.urlPathEqualTo("/legacy/court-case/migration"))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].courtCaseLegacyData.caseReferences[0].offenderCaseReference",
                WireMock.equalTo("YY12345678"),
              ),
            )
            .withRequestBody(WireMock.matchingJsonPath("courtCases[0].caseId", WireMock.equalTo("1")))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].courtCaseLegacyData.caseReferences[1].offenderCaseReference",
                WireMock.equalTo("XX12345678"),
              ),
            )
            .withRequestBody(WireMock.matchingJsonPath("courtCases[0].appearances.size()", WireMock.equalTo("1")))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].eventId",
                WireMock.equalTo(NOMIS_APPEARANCE_1_ID.toString()),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].legacyData.nomisOutcomeCode",
                WireMock.equalTo("4506"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].legacyData.outcomeConvictionFlag",
                WireMock.equalTo("true"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].legacyData.outcomeDispositionCode",
                WireMock.equalTo("I"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].legacyData.outcomeDescription",
                WireMock.equalTo("Adjournment"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].legacyData.nextEventDateTime",
                WireMock.equalTo("2020-02-01T00:00:00"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].legacyData.appearanceTime",
                WireMock.equalTo("12:00"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].legacyData.postedDate",
                WireMock.not(WireMock.absent()),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].charges[0].offenceCode",
                WireMock.equalTo("RR84027"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].charges[0].legacyData.nomisOutcomeCode",
                WireMock.equalTo("1081"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].charges[0].legacyData.outcomeDescription",
                WireMock.equalTo("Detention and Training Order"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].charges[0].legacyData.outcomeDispositionCode",
                WireMock.equalTo("F"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].charges[0].chargeNOMISId",
                WireMock.equalTo("3934645"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].charges[0].legacyData.postedDate",
                WireMock.not(WireMock.absent()),
              ),
            ).withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].appearances[0].appearanceTypeUuid",
                WireMock.equalTo(COURT_APPEARANCE_DPS_APPEARANCE_TYPE_UUID),
              ),
            ),
        )
      }
    }

    @Test
    internal fun `will map result IDs from a migrated record`() {
      nomisApi.stubGetInitialCount(
        NomisApiExtension.COURT_SENTENCING_PRISONER_IDS,
        1,
      ) { courtSentencingNomisApiMockServer.prisonerIdsPagedResponse(it) }
      courtSentencingNomisApiMockServer.stubMultipleGetPrisonerIdCounts(totalElements = 1, pageSize = 10)
      courtSentencingNomisApiMockServer.stubGetCourtCasesByOffenderForMigration(bookingId = 3, caseId = NOMIS_CASE_ID, offenderNo = "AN1")
      courtSentencingMappingApiMockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubPostMigrationMapping()

      dpsCourtSentencingServer.stubPostCourtCasesForCreateMigration(response = dpsMigrationCreateResponseWithTwoAppearancesAndTwoCharges())
      courtSentencingMappingApiMockServer.stubCourtSentencingSummaryByMigrationId(count = 1)

      webTestClient.performMigration()

      await untilAsserted {
        MappingApiExtension.mappingApi.stubFor(
          WireMock.post("/mapping/court-sentencing/prisoner/AN1/court-cases").willReturn(
            WireMock.aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(201),
          ),
        )
        courtSentencingMappingApiMockServer.verify(
          1,
          WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/court-sentencing/prisoner/AN1/court-cases"))
            .withRequestBody(WireMock.matchingJsonPath("courtAppearances.size()", WireMock.equalTo("2")))
            .withRequestBody(WireMock.matchingJsonPath("courtCharges.size()", WireMock.equalTo("2")))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCases[0].nomisCourtCaseId",
                WireMock.equalTo(NOMIS_CASE_ID.toString()),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearances[0].nomisCourtAppearanceId",
                WireMock.equalTo(NOMIS_APPEARANCE_2_ID.toString()),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearances[0].dpsCourtAppearanceId",
                WireMock.equalTo(DPS_APPEARANCE_2_ID),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearances[1].nomisCourtAppearanceId",
                WireMock.equalTo(NOMIS_APPEARANCE_1_ID.toString()),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearances[1].dpsCourtAppearanceId",
                WireMock.equalTo(DPS_APPEARANCE_1_ID),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCharges[0].nomisCourtChargeId",
                WireMock.equalTo(NOMIS_CHARGE_2_ID.toString()),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCharges[0].dpsCourtChargeId",
                WireMock.equalTo(DPS_CHARGE_2_ID),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCharges[1].nomisCourtChargeId",
                WireMock.equalTo(NOMIS_CHARGE_1_ID.toString()),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtCharges[1].dpsCourtChargeId",
                WireMock.equalTo(DPS_CHARGE_1_ID),
              ),
            ),
        )
      }
    }

    @Test
    internal fun `will skip locked down linked cases`() {
      nomisApi.stubGetInitialCount(
        NomisApiExtension.COURT_SENTENCING_PRISONER_IDS,
        1,
      ) { courtSentencingNomisApiMockServer.prisonerIdsPagedResponse(it) }
      courtSentencingNomisApiMockServer.stubMultipleGetPrisonerIdCounts(totalElements = 1, pageSize = 10)
      courtSentencingNomisApiMockServer.stubGetCourtCasesByOffenderForMigration(
        bookingId = 3,
        caseId = NOMIS_CASE_ID,
        combinedCaseId = 2,
        offenderNo = OFFENDER_NO,
      )
      courtSentencingMappingApiMockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)

      // will be calling dps with no cases but still need to record the migration offender level mapping
      dpsCourtSentencingServer.stubPostCourtCasesForCreateMigration()
      courtSentencingMappingApiMockServer.stubPostMigrationMapping()

      courtSentencingMappingApiMockServer.stubCourtSentencingSummaryByMigrationId(count = 1)

      webTestClient.performMigration()

      verify(telemetryClient).trackEvent(
        eq("court-sentencing-migration-entity-skipped"),
        check {
          assertThat(it["reason"]).isEqualTo("skipped linked(s) cases for this offender")
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["migrationId"]).isNotNull
        },
        isNull(),
      )

      // must still completes the migration at an offender level
      verify(telemetryClient).trackEvent(
        eq("court-sentencing-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          assertThat(it["migrationId"]).isNotNull
        },
        isNull(),
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
        webTestClient.get().uri("/migrate/court-sentencing/history")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.size()").isEqualTo(1)
          .jsonPath("$[0].migrationId").isNotEmpty
          .jsonPath("$[0].whenStarted").isNotEmpty
          .jsonPath("$[0].whenEnded").isNotEmpty
          .jsonPath("$[0].estimatedRecordCount").isEqualTo(26)
          .jsonPath("$[0].migrationType").isEqualTo(MigrationType.COURT_SENTENCING.name)
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

      val (migrationId) = webTestClient.performMigration()

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
  @DisplayName("GET /migrate/court-sentencing/history")
  inner class GetAll {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.COURT_SENTENCING,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T01:00:00"),
            status = COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.COURT_SENTENCING,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T03:00:00"),
            status = COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.COURT_SENTENCING,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-03T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-03T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-03T03:00:00"),
            status = COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.COURT_SENTENCING,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Test
    internal fun `must have valid token to get history`() {
      webTestClient.get().uri("/migrate/court-sentencing/history")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/court-sentencing/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `can read all records with no filter`() {
      webTestClient.get().uri("/migrate/court-sentencing/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(4)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-02T02:00:00")
        .jsonPath("$[2].migrationId").isEqualTo("2020-01-02T00:00:00")
        .jsonPath("$[3].migrationId").isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    internal fun `can filter so only records after a date are returned`() {
      webTestClient.get().uri {
        it.path("/migrate/court-sentencing/history")
          .queryParam("fromDateTime", "2020-01-02T02:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(2)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-02T02:00:00")
    }

    @Test
    internal fun `can filter so only records before a date are returned`() {
      webTestClient.get().uri {
        it.path("/migrate/court-sentencing/history")
          .queryParam("toDateTime", "2020-01-02T00:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(2)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-02T00:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    internal fun `can filter so only records between dates are returned`() {
      webTestClient.get().uri {
        it.path("/migrate/court-sentencing/history")
          .queryParam("fromDateTime", "2020-01-03T01:59:59")
          .queryParam("toDateTime", "2020-01-03T02:00:01")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(1)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
    }

    @Test
    internal fun `can filter so only records with failed records are returned`() {
      webTestClient.get().uri {
        it.path("/migrate/court-sentencing/history")
          .queryParam("includeOnlyFailures", "true")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(2)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-01T00:00:00")
    }
  }

  @Nested
  @DisplayName("GET /migrate/court-sentencing/history/{migrationId}")
  inner class Get {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.COURT_SENTENCING,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Test
    internal fun `must have valid token to get history`() {
      webTestClient.get().uri("/migrate/court-sentencing/history/2020-01-01T00:00:00")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/court-sentencing/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `can read record`() {
      webTestClient.get().uri("/migrate/court-sentencing/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.status").isEqualTo("COMPLETED")
    }
  }

  @Nested
  @DisplayName("GET /migrate/court-sentencing/active-migration")
  inner class GetActiveMigration {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.STARTED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.COURT_SENTENCING,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2019-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2019-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2019-01-01T01:00:00"),
            status = COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.COURT_SENTENCING,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Test
    internal fun `must have valid token to get active migration data`() {
      webTestClient.get().uri("/migrate/court-sentencing/active-migration")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get action migration data`() {
      webTestClient.get().uri("/migrate/court-sentencing/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/court-sentencing/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").doesNotExist()
        .jsonPath("$.whenStarted").doesNotExist()
        .jsonPath("$.recordsMigrated").doesNotExist()
        .jsonPath("$.estimatedRecordCount").doesNotExist()
        .jsonPath("$.status").doesNotExist()
        .jsonPath("$.migrationType").doesNotExist()
    }

    @Test
    internal fun `can read active migration data`() {
      courtSentencingMappingApiMockServer.stubCourtSentencingSummaryByMigrationId(count = 123456)
      webTestClient.get().uri("/migrate/court-sentencing/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.whenStarted").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.recordsMigrated").isEqualTo(123456)
        .jsonPath("$.toBeProcessedCount").isEqualTo(0)
        .jsonPath("$.beingProcessedCount").isEqualTo(0)
        .jsonPath("$.recordsFailed").isEqualTo(0)
        .jsonPath("$.estimatedRecordCount").isEqualTo(123567)
        .jsonPath("$.status").isEqualTo("STARTED")
        .jsonPath("$.migrationType").isEqualTo(MigrationType.COURT_SENTENCING.name)
    }
  }

  @Nested
  @DisplayName("POST /migrate/court-sentencing/{migrationId}/terminate/")
  inner class TerminateMigrationSentencing {
    @BeforeEach
    internal fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    @Test
    internal fun `must have valid token to terminate a migration`() {
      webTestClient.post().uri("/migrate/court-sentencing/{migrationId}/cqncel/", "some id")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to terminate a migration`() {
      webTestClient.post().uri("/migrate/court-sentencing/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/court-sentencing/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    internal fun `will terminate a running migration`() {
      val count = 30L
      nomisApi.stubGetInitialCount(
        NomisApiExtension.COURT_SENTENCING_PRISONER_IDS,
        count,
      ) { courtSentencingNomisApiMockServer.prisonerIdsPagedResponse(it) }
      courtSentencingNomisApiMockServer.stubMultipleGetPrisonerIdCounts(totalElements = count, pageSize = 10)
      courtSentencingMappingApiMockServer.stubCourtSentencingSummaryByMigrationId(count = count.toInt())
      courtSentencingMappingApiMockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)

      val migrationId = webTestClient.post().uri("/migrate/court-sentencing")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
              "fromDate": "2020-01-01",
              "toDate": "2020-01-02"
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted
        .returnResult<MigrationContext<CourtSentencingMigrationFilter>>()
        .responseBody.blockFirst()!!.migrationId

      webTestClient.post().uri("/migrate/court-sentencing/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/court-sentencing/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/court-sentencing/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationId)
          .jsonPath("$.status").isEqualTo("CANCELLED")
      }
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
  return MigrationCreateCourtCasesResponse(
    courtCases = courtCaseIds,
    appearances = courtAppearancesIds,
    charges = courtChargesIds,
    sentences = emptyList(),
    sentenceTerms = emptyList(),
  )
}

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
      ),
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
