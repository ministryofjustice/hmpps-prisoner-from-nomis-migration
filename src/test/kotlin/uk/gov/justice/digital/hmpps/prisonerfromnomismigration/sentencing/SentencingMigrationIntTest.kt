package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AdjustmentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.COMPLETED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.SENTENCING_ADJUSTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension.Companion.sentencingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDateTime

data class MigrationResult(val migrationId: String)

class SentencingMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Autowired
  private lateinit var nomisApi: SentencingAdjustmentsNomisApiMockServer

  @Nested
  @DisplayName("POST /migrate/sentencing")
  inner class MigrationSentenceAdjustments {

    @Nested
    inner class Security {
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
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      internal fun setUp() {
        webTestClient.delete().uri("/history")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().is2xxSuccessful

        nomisApi.stubGetSentencingAdjustmentIds(
          4,
          listOf(
            AdjustmentIdResponse(1, "SENTENCE"),
            AdjustmentIdResponse(2, "SENTENCE"),
            AdjustmentIdResponse(10, "KEY_DATE"),
            AdjustmentIdResponse(11, "KEY_DATE"),
          ),
        )
        nomisApi.stubGetSentenceAdjustment(1, bookingSequence = 1)
        nomisApi.stubGetSentenceAdjustment(2, bookingSequence = 3)
        nomisApi.stubGetKeyDateAdjustment(10, bookingSequence = 1)
        nomisApi.stubGetKeyDateAdjustment(11, bookingSequence = 4)
        mappingApi.stubGetNomisSentencingAdjustment(
          adjustmentCategory = "SENTENCE",
          nomisAdjustmentId = 1,
          adjustmentId = "8e46f2e0-60ec-4ce1-8706-c66bfa6343d8",
        )
        mappingApi.stubGetNomisSentencingAdjustment(
          adjustmentCategory = "SENTENCE",
          nomisAdjustmentId = 2,
          adjustmentId = "6bf29331-12a6-4089-bd56-fe8a643a57d3",
        )
        mappingApi.stubGetNomisSentencingAdjustment(
          adjustmentCategory = "KEY_DATE",
          nomisAdjustmentId = 10,
          adjustmentId = "ec9dcdb1-11e1-4943-aa4d-8b1baf800620",
        )
        mappingApi.stubGetNomisSentencingAdjustment(
          adjustmentCategory = "KEY_DATE",
          nomisAdjustmentId = 11,
          adjustmentId = "f3337ab5-cc68-4e2c-a9f1-23969c83e8a3",
        )
        sentencingApi.stubPatchSentencingAdjustmentCurrentTerm("8e46f2e0-60ec-4ce1-8706-c66bfa6343d8")
        sentencingApi.stubPatchSentencingAdjustmentCurrentTerm("6bf29331-12a6-4089-bd56-fe8a643a57d3")
        sentencingApi.stubPatchSentencingAdjustmentCurrentTerm("ec9dcdb1-11e1-4943-aa4d-8b1baf800620")
        sentencingApi.stubPatchSentencingAdjustmentCurrentTerm("f3337ab5-cc68-4e2c-a9f1-23969c83e8a3")
      }

      @Test
      internal fun `will pass filter through to service`() {
        webTestClient.performMigration(
          // language=JSON
          """
          {
            "fromDate": "2020-01-01",
            "toDate": "2020-01-02"
          }
          """.trimIndent(),
        )
        nomisApi.verify(
          getRequestedFor(urlPathEqualTo("/adjustments/ids"))
            .withQueryParam("fromDate", equalTo("2020-01-01"))
            .withQueryParam("toDate", equalTo("2020-01-02")),
        )
      }

      @Test
      internal fun `will patch all 4 adjustments found`() {
        webTestClient.performMigration()

        verify(telemetryClient, times(4)).trackEvent(eq("sentencing-adjustments-migration-entity-patched"), any(), isNull())
        verify(telemetryClient).trackEvent(
          eq("sentencing-adjustments-migration-entity-patched"),
          org.mockito.kotlin.check {
            assertThat(it["nomisAdjustmentId"]).isEqualTo("1")
            assertThat(it["nomisAdjustmentCategory"]).isEqualTo("SENTENCE")
            assertThat(it["adjustmentId"]).isEqualTo("8e46f2e0-60ec-4ce1-8706-c66bfa6343d8")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("sentencing-adjustments-migration-entity-patched"),
          org.mockito.kotlin.check {
            assertThat(it["nomisAdjustmentId"]).isEqualTo("2")
            assertThat(it["nomisAdjustmentCategory"]).isEqualTo("SENTENCE")
            assertThat(it["adjustmentId"]).isEqualTo("6bf29331-12a6-4089-bd56-fe8a643a57d3")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("sentencing-adjustments-migration-entity-patched"),
          org.mockito.kotlin.check {
            assertThat(it["nomisAdjustmentId"]).isEqualTo("10")
            assertThat(it["nomisAdjustmentCategory"]).isEqualTo("KEY_DATE")
            assertThat(it["adjustmentId"]).isEqualTo("ec9dcdb1-11e1-4943-aa4d-8b1baf800620")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("sentencing-adjustments-migration-entity-patched"),
          org.mockito.kotlin.check {
            assertThat(it["nomisAdjustmentId"]).isEqualTo("11")
            assertThat(it["nomisAdjustmentCategory"]).isEqualTo("KEY_DATE")
            assertThat(it["adjustmentId"]).isEqualTo("f3337ab5-cc68-4e2c-a9f1-23969c83e8a3")
          },
          isNull(),
        )
      }

      @Test
      internal fun `will patch each adjustment with the currentTerm`() {
        webTestClient.performMigration()

        sentencingApi.verify(
          patchRequestedFor(urlPathEqualTo("/legacy/adjustments/8e46f2e0-60ec-4ce1-8706-c66bfa6343d8/current-term"))
            .withRequestBodyJsonPath("currentTerm", "true"),
        )
        sentencingApi.verify(
          patchRequestedFor(urlPathEqualTo("/legacy/adjustments/6bf29331-12a6-4089-bd56-fe8a643a57d3/current-term"))
            .withRequestBodyJsonPath("currentTerm", "false"),
        )
        sentencingApi.verify(
          patchRequestedFor(urlPathEqualTo("/legacy/adjustments/ec9dcdb1-11e1-4943-aa4d-8b1baf800620/current-term"))
            .withRequestBodyJsonPath("currentTerm", "true"),
        )
        sentencingApi.verify(
          patchRequestedFor(urlPathEqualTo("/legacy/adjustments/f3337ab5-cc68-4e2c-a9f1-23969c83e8a3/current-term"))
            .withRequestBodyJsonPath("currentTerm", "false"),
        )
      }
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
    internal fun `will return dto with correct role`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/sentencing/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("sentencing-adjustments-migration-completed"),
      any(),
      isNull(),
    )
  }

  private fun WebTestClient.performMigration(body: String = "{ }"): MigrationResult = post().uri("/migrate/sentencing")
    .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_SENTENCING")))
    .header("Content-Type", "application/json")
    .body(BodyInserters.fromValue(body))
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted()
    }
}

fun someMigrationFilter(): BodyInserter<String, ReactiveHttpOutputMessage> = BodyInserters.fromValue(
  """
  {
  }
  """.trimIndent(),
)
