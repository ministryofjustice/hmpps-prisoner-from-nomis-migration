package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

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
import org.springframework.http.ReactiveHttpOutputMessage
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.COMPLETED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.SENTENCING_ADJUSTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.ADJUSTMENTS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.KEYDATE_ADJUSTMENTS_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.SENTENCE_ADJUSTMENTS_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension.Companion.sentencingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.adjustmentIdsPagedResponse
import java.time.Duration
import java.time.LocalDateTime

class SentencingMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/sentencing")
  inner class MigrationSentenceAdjustments {
    @BeforeEach
    internal fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    private fun WebTestClient.performMigration(body: String = "{ }") =
      post().uri("/migrate/sentencing")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue(body))
        .exchange()
        .expectStatus().isAccepted
        .also {
          waitUntilCompleted()
        }

    private fun waitUntilCompleted() =
      await atMost Duration.ofSeconds(60) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("sentencing-adjustments-migration-completed"),
          any(),
          isNull(),
        )
      }

    @Test
    internal fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/sentencing")
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/sentencing")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will start processing pages of sentence adjustments`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.ADJUSTMENTS_ID_URL, 86) { adjustmentIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAdjustmentIdCounts(totalElements = 86, pageSize = 10)
      nomisApi.stubMultipleGetSentenceAdjustments(1..86 step 2)
      nomisApi.stubMultipleGetKeyDateAdjustments(2..86 step 2)
      mappingApi.stubAllMappingsNotFound(SENTENCE_ADJUSTMENTS_GET_MAPPING_URL)
      mappingApi.stubAllMappingsNotFound(KEYDATE_ADJUSTMENTS_GET_MAPPING_URL)
      mappingApi.stubMappingCreate(ADJUSTMENTS_CREATE_MAPPING_URL)

      sentencingApi.stubCreateSentencingAdjustmentForMigration()
      mappingApi.stubSentenceAdjustmentMappingByMigrationId(count = 86)

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
        url = "/adjustments/ids",
        fromDate = "2020-01-01",
        toDate = "2020-01-02",
      )

      await untilAsserted {
        assertThat(sentencingApi.createSentenceAdjustmentCount()).isEqualTo(86)
      }
    }

    @Test
    internal fun `will add analytical events for starting, ending and each migrated record`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.ADJUSTMENTS_ID_URL, 26) { adjustmentIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAdjustmentIdCounts(totalElements = 26, pageSize = 10)
      nomisApi.stubMultipleGetSentenceAdjustments(1..26 step 2)
      nomisApi.stubMultipleGetKeyDateAdjustments(2..26 step 2)
      sentencingApi.stubCreateSentencingAdjustmentForMigration()
      mappingApi.stubAllMappingsNotFound(SENTENCE_ADJUSTMENTS_GET_MAPPING_URL)
      mappingApi.stubAllMappingsNotFound(KEYDATE_ADJUSTMENTS_GET_MAPPING_URL)
      mappingApi.stubMappingCreate(ADJUSTMENTS_CREATE_MAPPING_URL)

      // stub 25 migrated records and 1 fake a failure
      mappingApi.stubSentenceAdjustmentMappingByMigrationId(count = 25)
      awsSqsSentencingMigrationDlqClient!!.sendMessage(
        SendMessageRequest.builder().queueUrl(sentencingMigrationDlqUrl).messageBody("""{ "message": "some error" }""").build(),
      ).get()

      webTestClient.performMigration()

      verify(telemetryClient).trackEvent(eq("sentencing-adjustments-migration-started"), any(), isNull())
      verify(telemetryClient, times(26)).trackEvent(eq("sentencing-adjustments-migration-entity-migrated"), any(), isNull())

      await untilAsserted {
        webTestClient.get().uri("/migrate/sentencing/history")
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
          .jsonPath("$[0].migrationType").isEqualTo("SENTENCING_ADJUSTMENTS")
          .jsonPath("$[0].status").isEqualTo("COMPLETED")
          .jsonPath("$[0].recordsMigrated").isEqualTo(25)
          .jsonPath("$[0].recordsFailed").isEqualTo(1)
      }
    }

    @Test
    internal fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.ADJUSTMENTS_ID_URL, 1) { adjustmentIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAdjustmentIdCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetSentenceAdjustments(1..1)
      mappingApi.stubAllMappingsNotFound(SENTENCE_ADJUSTMENTS_GET_MAPPING_URL)
      mappingApi.stubAllMappingsNotFound(KEYDATE_ADJUSTMENTS_GET_MAPPING_URL)
      sentencingApi.stubCreateSentencingAdjustmentForMigration("654321")
      mappingApi.stubMappingCreateFailureFollowedBySuccess(ADJUSTMENTS_CREATE_MAPPING_URL)

      webTestClient.performMigration()

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createMappingCount(ADJUSTMENTS_CREATE_MAPPING_URL) } matches { it == 2 }

      // check that one sentence-adjustment is created
      assertThat(sentencingApi.createSentenceAdjustmentCount()).isEqualTo(1)

      // should retry to create mapping twice
      mappingApi.verifyCreateMappingSentenceAdjustmentIds(arrayOf("654321"), times = 2)
    }

    @Test
    internal fun `it will not retry after a 409 (duplicate adjustment written to Sentencing API)`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.ADJUSTMENTS_ID_URL, 1) { adjustmentIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAdjustmentIdCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetSentenceAdjustments(1..1)
      mappingApi.stubAllMappingsNotFound(SENTENCE_ADJUSTMENTS_GET_MAPPING_URL)
      mappingApi.stubAllMappingsNotFound(KEYDATE_ADJUSTMENTS_GET_MAPPING_URL)
      sentencingApi.stubCreateSentencingAdjustmentForMigration("123")
      mappingApi.stubSentenceAdjustmentMappingCreateConflict()

      webTestClient.performMigration()

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createMappingCount(ADJUSTMENTS_CREATE_MAPPING_URL) } matches { it == 1 }

      // check that one sentence-adjustment is created
      assertThat(sentencingApi.createSentenceAdjustmentCount()).isEqualTo(1)

      // doesn't retry
      mappingApi.verifyCreateMappingSentenceAdjustmentIds(arrayOf("123"), times = 1)

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-adjustment-duplicate"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["existingAdjustmentId"]).isEqualTo("10")
          assertThat(it["duplicateAdjustmentId"]).isEqualTo("11")
          assertThat(it["existingNomisAdjustmentId"]).isEqualTo("123")
          assertThat(it["duplicateNomisAdjustmentId"]).isEqualTo("123")
          assertThat(it["existingNomisAdjustmentCategory"]).isEqualTo("SENTENCE")
          assertThat(it["duplicateNomisAdjustmentCategory"]).isEqualTo("SENTENCE")
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("GET /migrate/sentencing/history")
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
            migrationType = SENTENCING_ADJUSTMENTS,
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
            migrationType = SENTENCING_ADJUSTMENTS,
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
            migrationType = SENTENCING_ADJUSTMENTS,
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
            migrationType = SENTENCING_ADJUSTMENTS,
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
      webTestClient.get().uri("/migrate/sentencing/history")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/sentencing/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `can read all records with no filter`() {
      webTestClient.get().uri("/migrate/sentencing/history")
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
        it.path("/migrate/sentencing/history")
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
        it.path("/migrate/sentencing/history")
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
        it.path("/migrate/sentencing/history")
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
        it.path("/migrate/sentencing/history")
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
  @DisplayName("GET /migrate/sentencing/history/{migrationId}")
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
            migrationType = SENTENCING_ADJUSTMENTS,
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
      webTestClient.get().uri("/migrate/sentencing/history/2020-01-01T00:00:00")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/sentencing/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `can read record`() {
      webTestClient.get().uri("/migrate/sentencing/history/2020-01-01T00:00:00")
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
  @DisplayName("GET /migrate/sentencing/active-migration")
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
            migrationType = SENTENCING_ADJUSTMENTS,
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
            migrationType = SENTENCING_ADJUSTMENTS,
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
      webTestClient.get().uri("/migrate/sentencing/active-migration")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get action migration data`() {
      webTestClient.get().uri("/migrate/sentencing/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/sentencing/active-migration")
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
      mappingApi.stubSentenceAdjustmentMappingByMigrationId(count = 123456)
      webTestClient.get().uri("/migrate/sentencing/active-migration")
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
        .jsonPath("$.migrationType").isEqualTo("SENTENCING_ADJUSTMENTS")
    }
  }

  @Nested
  @DisplayName("POST /migrate/sentencing/{migrationId}/terminate/")
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
      webTestClient.post().uri("/migrate/sentencing/{migrationId}/cqncel/", "some id")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to terminate a migration`() {
      webTestClient.post().uri("/migrate/sentencing/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/sentencing/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    internal fun `will terminate a running migration`() {
      val count = 30L
      nomisApi.stubGetInitialCount(NomisApiExtension.ADJUSTMENTS_ID_URL, count) { adjustmentIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAdjustmentIdCounts(totalElements = count, pageSize = 10)
      mappingApi.stubSentenceAdjustmentMappingByMigrationId(count = count.toInt())

      val migrationId = webTestClient.post().uri("/migrate/sentencing")
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
        .returnResult<MigrationContext<SentencingMigrationFilter>>()
        .responseBody.blockFirst()!!.migrationId

      webTestClient.post().uri("/migrate/sentencing/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/sentencing/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/sentencing/history/{migrationId}", migrationId)
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
