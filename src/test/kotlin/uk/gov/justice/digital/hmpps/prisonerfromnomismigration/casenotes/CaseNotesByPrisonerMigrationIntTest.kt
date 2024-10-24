package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiExtension.Companion.caseNotesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.Duration
import java.time.LocalDateTime

private const val OFFENDER_NUMBER1 = "A0001KT"
private const val OFFENDER_NUMBER2 = "A0002KT"

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CaseNotesByPrisonerMigrationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var caseNotesNomisApiMockServer: CaseNotesNomisApiMockServer

  @Autowired
  private lateinit var caseNotesMappingApiMockServer: CaseNotesMappingApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/casenotes")
  inner class MigrateDpsCaseNotes {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/casenotes")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CaseNotesMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/casenotes")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CaseNotesMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/casenotes")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CaseNotesMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonIds(totalElements = 2, pageSize = 10, firstOffenderNo = OFFENDER_NUMBER1)
        caseNotesNomisApiMockServer.stubGetCaseNotesToMigrate(offenderNo = OFFENDER_NUMBER1, currentCaseNoteCount = 1)
        caseNotesNomisApiMockServer.stubGetCaseNotesToMigrate(offenderNo = OFFENDER_NUMBER2, currentCaseNoteCount = 1, type = "CNOTE")
        caseNotesApi.stubMigrateCaseNotes(OFFENDER_NUMBER1, listOf(1 to "00000000-0000-0000-0000-000000000001"))
        caseNotesApi.stubMigrateCaseNotes(OFFENDER_NUMBER2, listOf(1 to "00000000-0000-0000-0000-000000000002"))
        caseNotesMappingApiMockServer.stubGetMappings(listOf())
        caseNotesMappingApiMockServer.stubPostBatchMappings(OFFENDER_NUMBER1)
        caseNotesMappingApiMockServer.stubPostBatchMappings(OFFENDER_NUMBER2)
        caseNotesMappingApiMockServer.stubMigrationCount(recordsMigrated = 2)
        migrationResult = performMigration()
      }

      @Test
      fun `will migrate all prisoner's casenotes`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("casenotes-migration-entity-migrated"),
          any(),
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("casenotes-migration-entity-migrated"),
          check { assertThat(it).containsEntry("offenderNo", "A0001KT") },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("casenotes-migration-entity-migrated"),
          check { assertThat(it).containsEntry("offenderNo", "A0002KT") },
          isNull(),
        )
      }

      @Test
      fun `will POST all casenotes to DPS for each prisoner`() {
        caseNotesApi.verify(
          postRequestedFor(urlPathEqualTo("/migrate/case-notes/$OFFENDER_NUMBER1"))
            .withRequestBodyJsonPath("$[0].legacyId", "1")
            .withRequestBodyJsonPath("$[0].locationId", equalTo("SWI"))
            .withRequestBodyJsonPath("$[0].type", equalTo("GEN"))
            .withRequestBodyJsonPath("$[0].subType", equalTo("OUTCOME"))
            .withRequestBodyJsonPath("$[0].text", equalTo("text"))
            .withRequestBodyJsonPath("$[0].systemGenerated", equalTo("false"))
            .withRequestBodyJsonPath("$[0].createdDateTime", equalTo("2021-02-03T04:05:06"))
            .withRequestBodyJsonPath("$[0].createdByUsername", equalTo("John"))
            .withRequestBodyJsonPath("$[0].system", equalTo("NOMIS"))
            .withRequestBodyJsonPath("$[0].author.username", equalTo("me"))
            .withRequestBodyJsonPath("$[0].author.userId", equalTo("123456"))
            .withRequestBodyJsonPath("$[0].author.firstName", equalTo("First"))
            .withRequestBodyJsonPath("$[0].author.lastName", equalTo("Last"))
            .withRequestBodyJsonPath("$[0].occurrenceDateTime", equalTo("2021-02-03T04:05:06")),
        )
        caseNotesApi.verify(
          postRequestedFor(urlPathEqualTo("/migrate/case-notes/$OFFENDER_NUMBER2"))
            .withRequestBodyJsonPath("$[0].legacyId", equalTo("1"))
            .withRequestBodyJsonPath("$[0].locationId", equalTo("SWI"))
            .withRequestBodyJsonPath("$[0].type", equalTo("APP"))
            .withRequestBodyJsonPath("$[0].subType", equalTo("OUTCOME"))
            .withRequestBodyJsonPath("$[0].text", equalTo("text"))
            .withRequestBodyJsonPath("$[0].systemGenerated", equalTo("false"))
            .withRequestBodyJsonPath("$[0].createdDateTime", equalTo("2021-02-03T04:05:06"))
            .withRequestBodyJsonPath("$[0].createdByUsername", equalTo("John"))
            .withRequestBodyJsonPath("$[0].system", equalTo("NOMIS"))
            .withRequestBodyJsonPath("$[0].author.username", equalTo("me"))
            .withRequestBodyJsonPath("$[0].author.userId", equalTo("123456"))
            .withRequestBodyJsonPath("$[0].author.firstName", equalTo("First"))
            .withRequestBodyJsonPath("$[0].author.lastName", equalTo("Last"))
            .withRequestBodyJsonPath("$[0].occurrenceDateTime", equalTo("2021-02-03T04:05:06")),
        )
      }

      @Test
      fun `will POST mappings for casenotes created for each prisoner`() {
        caseNotesMappingApiMockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/casenotes/$OFFENDER_NUMBER1/all")))
        caseNotesMappingApiMockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/casenotes/$OFFENDER_NUMBER2/all")))
      }

      @Test
      @Disabled("Count not implemented yet")
      fun `will record the number of prisoners migrated`() {
        caseNotesMappingApiMockServer.stubGetCount(2)

        webTestClient.get().uri("/migrate/casenotes/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CASENOTES")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("2")
      }

      @Test
      fun `will transform NOMIS case note to DPS case note`() {
        caseNotesApi.verify(
          postRequestedFor(urlPathEqualTo("/migrate/case-notes/$OFFENDER_NUMBER1"))
            .withRequestBodyJsonPath("$[0].text", "text"),
        )
        caseNotesApi.verify(
          postRequestedFor(urlPathEqualTo("/migrate/case-notes/$OFFENDER_NUMBER2"))
            .withRequestBodyJsonPath("$[0].text", "text"),
        )
      }
    }

    @Nested
    inner class ErrorRecovery {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonIds(totalElements = 1, pageSize = 10, firstOffenderNo = OFFENDER_NUMBER1)
        caseNotesNomisApiMockServer.stubGetCaseNotesToMigrate(offenderNo = OFFENDER_NUMBER1, currentCaseNoteCount = 1)
        caseNotesApi.stubMigrateCaseNotes(OFFENDER_NUMBER1, listOf(1 to "00000000-0000-0000-0000-000000000001"))
        caseNotesMappingApiMockServer.stubPostBatchMappingsFailureFollowedBySuccess(OFFENDER_NUMBER1)
        performMigration()
      }

      @Test
      fun `will POST the casenotes to DPS only once`() {
        caseNotesApi.verify(1, postRequestedFor(urlPathEqualTo("/migrate/case-notes/$OFFENDER_NUMBER1")))
      }

      @Test
      fun `will POST mappings for casenotes twice due to the single error`() {
        caseNotesMappingApiMockServer.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/casenotes/$OFFENDER_NUMBER1/all")),
        )
      }
    }

    @Nested
    inner class ErrorDpsFailure {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonIds(totalElements = 1, pageSize = 10, firstOffenderNo = OFFENDER_NUMBER1)
        caseNotesNomisApiMockServer.stubGetCaseNotesToMigrate(offenderNo = OFFENDER_NUMBER1, currentCaseNoteCount = 1)
        caseNotesApi.stubMigrateCaseNotesFailure(OFFENDER_NUMBER1)
        performMigration()
      }

      @Test
      fun `will POST the casenotes to DPS twice as per dlqMaxReceiveCount, but not mappings`() {
        await untilAsserted {
          caseNotesApi.verify(2, postRequestedFor(urlPathEqualTo("/migrate/case-notes/$OFFENDER_NUMBER1")))
          caseNotesMappingApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/mapping/casenotes/$OFFENDER_NUMBER1/all")))
        }
      }

      @Test
      fun `failure telemetry is posted`() {
        verify(telemetryClient, atLeast(2)).trackEvent(
          eq("casenotes-migration-entity-failed"),
          check {
            assertThat(it).containsEntry("offenderNo", OFFENDER_NUMBER1)
            assertThat(it).containsEntry("error", "400 Bad Request from POST http://localhost:8096/migrate/case-notes/$OFFENDER_NUMBER1")
          },
          isNull(),
        )
      }

      @Test
      fun `message ends up on the dead letter queue`() {
        await untilCallTo {
          awsSqsCaseNotesOffenderMigrationDlqClient.countMessagesOnQueue(caseNotesQueueOffenderMigrationDlqUrl).get()
        } matches { it == 1 }
      }
    }
  }

  @Nested
  @DisplayName("GET /migrate/casenotes/history")
  inner class GetAll {
    @BeforeEach
    fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.CASENOTES,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.CASENOTES,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T03:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.CASENOTES,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-03T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-03T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-03T03:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.CASENOTES,
          ),
        )
      }
    }

    @AfterEach
    fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/casenotes/history")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/casenotes/history")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/casenotes/history")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    fun `can read all records`() {
      webTestClient.get().uri("/migrate/casenotes/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CASENOTES")))
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
  }

  @Nested
  @DisplayName("GET /migrate/casenotes/history/{migrationId}")
  inner class Get {
    @BeforeEach
    fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.CASENOTES,
          ),
        )
      }
    }

    @AfterEach
    fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/casenotes/history/2020-01-01T00:00:00")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/casenotes/history/2020-01-01T00:00:00")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/casenotes/history/2020-01-01T00:00:00")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    fun `can read record`() {
      webTestClient.get().uri("/migrate/casenotes/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CASENOTES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.status").isEqualTo("COMPLETED")
    }
  }

  @Nested
  @DisplayName("GET /migrate/casenotes/active-migration")
  inner class GetActiveMigration {
    @BeforeEach
    fun createHistoryRecords() {
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
            migrationType = MigrationType.CASENOTES,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2019-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2019-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2019-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.CASENOTES,
          ),
        )
      }
    }

    @AfterEach
    fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/casenotes/active-migration")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/casenotes/active-migration")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/casenotes/active-migration")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/casenotes/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CASENOTES")))
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
    fun `can read active migration data`() {
      caseNotesMappingApiMockServer.stubSingleItemByMigrationId(migrationId = "2020-01-01T00:00:00", count = 123456)
      webTestClient.get().uri("/migrate/casenotes/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CASENOTES")))
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
        .jsonPath("$.migrationType").isEqualTo("CASENOTES")
    }
  }

  @Nested
  @DisplayName("POST /migrate/casenotes/{migrationId}/cancel")
  inner class TerminateMigrationDpsCaseNotes {
    @BeforeEach
    fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/casenotes/{migrationId}/cancel", "some id")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/casenotes/{migrationId}/cancel", "some id")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/casenotes/{migrationId}/cancel", "some id")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/casenotes/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CASENOTES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `will terminate a running migration`() {
      nomisApi.stubGetPrisonIds(totalElements = 2, pageSize = 10, firstOffenderNo = OFFENDER_NUMBER1)
      caseNotesNomisApiMockServer.stubGetCaseNotesToMigrate(offenderNo = OFFENDER_NUMBER1, currentCaseNoteCount = 1)
      caseNotesNomisApiMockServer.stubGetCaseNotesToMigrate(offenderNo = OFFENDER_NUMBER1, currentCaseNoteCount = 1)

      val migrationId = performMigration().migrationId

      webTestClient.post().uri("/migrate/casenotes/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CASENOTES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/casenotes/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CASENOTES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/casenotes/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CASENOTES")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationId)
          .jsonPath("$.status").isEqualTo("CANCELLED")
      }
    }
  }

  private fun performMigration(body: CaseNotesMigrationFilter = CaseNotesMigrationFilter()): MigrationResult =
    webTestClient.post().uri("/migrate/casenotes")
      .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CASENOTES")))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(body)
      .exchange()
      .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
      .also {
        waitUntilCompleted()
      }

  private fun waitUntilCompleted() =
    await atMost Duration.ofSeconds(60) untilAsserted {
      verify(telemetryClient).trackEvent(
        eq("casenotes-migration-completed"),
        any(),
        isNull(),
      )
    }
}
