package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.COMPLETED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.courtCaseIdsPagedResponse
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

data class MigrationResult(val migrationId: String)

class CourtSentencingMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Autowired
  private lateinit var courtSentencingMappingApiMockServer: CourtSentencingMappingApiMockServer

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

    private fun WebTestClient.performMigration(body: String = "{ }"): MigrationResult =
      post().uri("/migrate/court-sentencing")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue(body))
        .exchange()
        .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
        .also {
          waitUntilCompleted()
        }

    private fun waitUntilCompleted() =
      await atMost Duration.ofSeconds(60) untilAsserted {
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
      nomisApi.stubGetInitialCount(NomisApiExtension.COURT_CASES_ID_URL, 14) { courtCaseIdsPagedResponse(it) }
      nomisApi.stubMultipleGetCourtCaseIdCounts(totalElements = 14, pageSize = 10)
      nomisApi.stubMultipleGetCourtCases(1..14)
      courtSentencingMappingApiMockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubPostMapping()

      dpsCourtSentencingServer.stubPostCourtCaseForCreateMigration(response = dpsCourtCaseCreateResponseWithTwoAppearancesAndTwoCharges())
      courtSentencingMappingApiMockServer.stubCourtCaseMappingByMigrationId(count = 14)

      webTestClient.performMigration(
        """
          {
            "fromDate": "2020-01-01",
            "toDate": "2020-01-02"
          }
        """.trimIndent(),
      )

      // check filter matches what is passed in
      nomisApi.verifyGetIdsCount(
        url = "/court-cases/ids",
        fromDate = "2020-01-01",
        toDate = "2020-01-02",
      )

      await untilAsserted {
        assertThat(dpsCourtSentencingServer.createCourtCaseMigrationCount()).isEqualTo(14)
      }

      // verify 2 charges are mapped for each case
      courtSentencingMappingApiMockServer.verify(
        14,
        WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/court-sentencing/court-cases"))
          .withRequestBody(
            WireMock.matchingJsonPath(
              "courtCharges.size()",
              WireMock.equalTo("2"),
            ),
          ),
      )
    }

    @Test
    internal fun `will migrate case hierarchy`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.COURT_CASES_ID_URL, 1) { courtCaseIdsPagedResponse(it) }
      nomisApi.stubMultipleGetCourtCaseIdCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubGetCourtCase(caseId = 1)
      courtSentencingMappingApiMockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubPostMapping()

      dpsCourtSentencingServer.stubPostCourtCaseForCreateMigration(response = dpsCourtCaseCreateResponseWithTwoAppearancesAndTwoCharges())
      courtSentencingMappingApiMockServer.stubCourtCaseMappingByMigrationId(count = 1)

      webTestClient.performMigration(
        """
          {
            "fromDate": "2020-01-01",
            "toDate": "2020-01-02"
          }
        """.trimIndent(),
      )

      await untilAsserted {
        dpsCourtSentencingServer.verify(
          1,
          WireMock.postRequestedFor(WireMock.urlPathEqualTo("/court-case/migration"))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "appearances.size()",
                WireMock.equalTo("1"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "appearances[0].courtCaseReference",
                WireMock.equalTo("caseRef1"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "appearances[0].charges[0].offenceCode",
                WireMock.equalTo("RR84027"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "appearances[0].charges[0].outcome",
                WireMock.equalTo("1081"),
              ),
            ),
        )
      }
    }

    @Test
    internal fun `will add analytical events for starting, ending and each migrated record`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.COURT_CASES_ID_URL, 26) { courtCaseIdsPagedResponse(it) }
      nomisApi.stubMultipleGetCourtCaseIdCounts(totalElements = 26, pageSize = 10)
      nomisApi.stubMultipleGetCourtCases(1..26)
      dpsCourtSentencingServer.stubPostCourtCaseForCreateMigration()
      courtSentencingMappingApiMockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubPostMapping()

      // stub 25 migrated records and 1 fake a failure
      courtSentencingMappingApiMockServer.stubCourtCaseMappingByMigrationId(count = 25)
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
      nomisApi.stubGetInitialCount(NomisApiExtension.COURT_CASES_ID_URL, 1) { courtCaseIdsPagedResponse(it) }
      nomisApi.stubMultipleGetCourtCaseIdCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubGetCourtCase(caseId = 1)
      courtSentencingMappingApiMockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubCourtCaseMappingByMigrationId()
      dpsCourtSentencingServer.stubPostCourtCaseForCreateMigration("05b332ad-58eb-4ec2-963c-c9c927856788")
      courtSentencingMappingApiMockServer.stubPostMappingFailureFollowedBySuccess()

      val (migrationId) = webTestClient.performMigration()

      // wait for all mappings to be created before verifying
      await untilCallTo { courtSentencingMappingApiMockServer.createCourtCaseMappingCount() } matches { it == 2 }

      // check that one court case is created
      assertThat(dpsCourtSentencingServer.createCourtCaseMigrationCount()).isEqualTo(1)

      // should retry to create mapping twice
      courtSentencingMappingApiMockServer.verifyCreateMappingCourtCaseIds(migrationId, arrayOf("1"), times = 2)
    }

    @Test
    internal fun `it will not retry after a 409 (duplicate court case written to Sentencing API)`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.COURT_CASES_ID_URL, 1) { courtCaseIdsPagedResponse(it) }
      nomisApi.stubMultipleGetCourtCaseIdCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetCourtCases(1..1)
      courtSentencingMappingApiMockServer.stubGetByNomisId(HttpStatus.NOT_FOUND)
      courtSentencingMappingApiMockServer.stubCourtCaseMappingByMigrationId()
      dpsCourtSentencingServer.stubPostCourtCaseForCreateMigration("05b332ad-58eb-4ec2-963c-c9c927856788")
      courtSentencingMappingApiMockServer.stubCourtCaseMappingCreateConflict()

      webTestClient.performMigration()

      // wait for all mappings to be created before verifying
      await untilCallTo { courtSentencingMappingApiMockServer.createCourtCaseMappingCount() } matches { it == 1 }

      // check that one court case is created
      assertThat(dpsCourtSentencingServer.createCourtCaseMigrationCount()).isEqualTo(1)

      // doesn't retry
      courtSentencingMappingApiMockServer.verifyCreateMappingCourtCaseIds(arrayOf("1"), times = 1)

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-court-sentencing-duplicate"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["existingDpsCourtCaseId"]).isEqualTo("10")
          assertThat(it["duplicateDpsCourtCaseId"]).isEqualTo("11")
          assertThat(it["existingNomisCourtCaseId"]).isEqualTo("123")
          assertThat(it["duplicateNomisCourtCaseId"]).isEqualTo("123")
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
      courtSentencingMappingApiMockServer.stubCourtCaseMappingByMigrationId(count = 123456)
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
      nomisApi.stubGetInitialCount(NomisApiExtension.COURT_CASES_ID_URL, count) { courtCaseIdsPagedResponse(it) }
      nomisApi.stubMultipleGetCourtCaseIdCounts(totalElements = count, pageSize = 10)
      courtSentencingMappingApiMockServer.stubCourtCaseMappingByMigrationId(count = count.toInt())
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
fun dpsCourtCaseCreateResponseWithTwoAppearancesAndTwoCharges(): CreateCourtCaseMigrationResponse {
  val courtCaseUUID: UUID = UUID.randomUUID()
  val courtChargesIds: List<String> =
    listOf(UUID.randomUUID().toString(), UUID.randomUUID().toString())
  val courtAppearancesIds: List<String> = listOf(
    UUID.randomUUID().toString(),
    UUID.randomUUID().toString(),
  )
  return CreateCourtCaseMigrationResponse(
    courtCaseUuid = courtCaseUUID.toString(),
    courtAppearanceIds = courtAppearancesIds,
    courtChargeIds = courtChargesIds,
  )
}
