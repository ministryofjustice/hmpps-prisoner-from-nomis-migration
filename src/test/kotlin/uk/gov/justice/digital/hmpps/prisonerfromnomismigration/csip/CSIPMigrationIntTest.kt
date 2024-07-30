package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

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
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.web.reactive.function.BodyInserters
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension.Companion.csipApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingApiMockServer.Companion.CSIP_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPNomisApiMockServer.Companion.CSIP_ID_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.COMPLETED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.CSIP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.time.Duration
import java.time.LocalDateTime

class CSIPMigrationIntTest : SqsIntegrationTestBase() {
  companion object {
    private const val DPS_CSIP_ID = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5"
    private const val NOMIS_CSIP_ID = 1234L
  }

  @Autowired
  private lateinit var csipNomisApi: CSIPNomisApiMockServer

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/csip")
  inner class MigrationCSIP {
    @BeforeEach
    internal fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    private fun WebTestClient.performMigration(body: String = "{ }") =
      post().uri("/migrate/csip")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
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
          eq("csip-migration-completed"),
          any(),
          isNull(),
        )
      }

    @Test
    internal fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/csip")
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("{ }"))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/csip")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("{ }"))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will start processing pages of CSIP`() {
      csipNomisApi.stubGetInitialCount(CSIP_ID_URL, 86) { csipIdsPagedResponse(it) }
      csipNomisApi.stubGetPagedCSIPIds(totalElements = 86, pageSize = 10)
      csipNomisApi.stubMultipleGetCSIP(1..86)

      csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)
      mappingApi.stubMappingCreate(CSIP_CREATE_MAPPING_URL)

      csipApi.stubCSIPMigrate()
      csipMappingApi.stubCSIPMappingByMigrationId(count = 86)

      webTestClient.performMigration(
        """
          {
            "fromDate": "2020-01-01",
            "toDate": "2020-01-02"
          }
        """.trimIndent(),
      )

      // check filter matches what is passed in
      csipNomisApi.verifyGetIdsCount(
        url = "/csip/ids",
        fromDate = "2020-01-01",
        toDate = "2020-01-02",
      )

      await untilAsserted {
        assertThat(csipApi.createCSIPMigrationCount()).isEqualTo(86)
      }
    }

    // / TODO Add test for minimal nomis prisoner api data
    @Test
    internal fun `will add analytical events for starting, ending and each migrated record`() {
      csipNomisApi.stubGetInitialCount(CSIP_ID_URL, 26) { csipIdsPagedResponse(it) }
      csipNomisApi.stubGetPagedCSIPIds(totalElements = 26, pageSize = 10)
      csipNomisApi.stubMultipleGetCSIP(1..26)
      csipApi.stubCSIPMigrate()
      csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)
      mappingApi.stubMappingCreate(CSIP_CREATE_MAPPING_URL)

      // stub 25 migrated records and 1 fake a failure
      csipMappingApi.stubCSIPMappingByMigrationId(count = 25)
      awsSqsCSIPMigrationDlqClient!!.sendMessage(
        SendMessageRequest.builder().queueUrl(csipMigrationDlqUrl).messageBody("""{ "message": "some error" }""").build(),
      ).get()

      webTestClient.performMigration()

      verify(telemetryClient).trackEvent(eq("csip-migration-started"), any(), isNull())
      verify(telemetryClient, times(26)).trackEvent(eq("csip-migration-entity-migrated"), any(), isNull())

      await untilAsserted {
        webTestClient.get().uri("/migrate/csip/history")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.size()").isEqualTo(1)
          .jsonPath("$[0].migrationId").isNotEmpty
          .jsonPath("$[0].whenStarted").isNotEmpty
          .jsonPath("$[0].whenEnded").isNotEmpty
          .jsonPath("$[0].estimatedRecordCount").isEqualTo(26)
          .jsonPath("$[0].migrationType").isEqualTo("CSIP")
          .jsonPath("$[0].status").isEqualTo("COMPLETED")
          .jsonPath("$[0].recordsMigrated").isEqualTo(25)
          .jsonPath("$[0].recordsFailed").isEqualTo(1)
      }
    }

    @Test
    internal fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      csipNomisApi.stubGetInitialCount(CSIP_ID_URL, 1) { csipIdsPagedResponse(it) }
      csipNomisApi.stubGetPagedCSIPIds(totalElements = 1, pageSize = 10)
      csipNomisApi.stubMultipleGetCSIP(1..1)
      csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)
      csipMappingApi.stubCSIPMappingByMigrationId()
      csipApi.stubCSIPMigrate()
      mappingApi.stubMappingCreateFailureFollowedBySuccess(CSIP_CREATE_MAPPING_URL)

      webTestClient.performMigration()

      // check that one csip is created
      assertThat(csipApi.createCSIPMigrationCount()).isEqualTo(1)

      // should retry to create mapping twice
      csipMappingApi.verifyCreateCSIPReportMapping(DPS_CSIP_ID, times = 2)
    }

    @Test
    internal fun `it will not retry after a 409 (duplicate csip written to CSIP API)`() {
      val duplicateDPSCSIPId = "ddd596da-8eab-4d2a-a026-bc5afb8acda0"

      csipNomisApi.stubGetInitialCount(CSIP_ID_URL, 1) { csipIdsPagedResponse(it) }
      csipNomisApi.stubGetPagedCSIPIds(totalElements = 1, pageSize = 10)
      csipNomisApi.stubMultipleGetCSIP(1..1)
      csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)
      csipMappingApi.stubCSIPMappingByMigrationId()
      csipApi.stubCSIPMigrate(duplicateDPSCSIPId)
      csipMappingApi.stubCSIPMappingCreateConflict()
      webTestClient.performMigration()

      // check that one csip is created
      assertThat(csipApi.createCSIPMigrationCount()).isEqualTo(1)

      // doesn't retry
      csipMappingApi.verifyCreateCSIPReportMapping(duplicateDPSCSIPId)

      verify(telemetryClient).trackEvent(
        eq("csip-nomis-migration-duplicate"),
        check {
          assertThat(it["existingNomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
          assertThat(it["duplicateNomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
          assertThat(it["existingDPSCSIPId"]).isEqualTo(DPS_CSIP_ID)
          assertThat(it["duplicateDPSCSIPId"]).isEqualTo(duplicateDPSCSIPId)
          assertThat(it["migrationId"]).isNotNull()
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("GET /migrate/csip/history")
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
            migrationType = CSIP,
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
            migrationType = CSIP,
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
            migrationType = CSIP,
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
            migrationType = CSIP,
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
      webTestClient.get().uri("/migrate/csip/history")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/csip/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `can read all records with no filter`() {
      webTestClient.get().uri("/migrate/csip/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
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
        it.path("/migrate/csip/history")
          .queryParam("fromDateTime", "2020-01-02T02:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
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
        it.path("/migrate/csip/history")
          .queryParam("toDateTime", "2020-01-02T00:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
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
        it.path("/migrate/csip/history")
          .queryParam("fromDateTime", "2020-01-03T01:59:59")
          .queryParam("toDateTime", "2020-01-03T02:00:01")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
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
        it.path("/migrate/csip/history")
          .queryParam("includeOnlyFailures", "true")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
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
  @DisplayName("GET /migrate/csip/history/{migrationId}")
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
            migrationType = CSIP,
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
      webTestClient.get().uri("/migrate/csip/history/2020-01-01T00:00:00")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/csip/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `can read record`() {
      webTestClient.get().uri("/migrate/csip/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.status").isEqualTo("COMPLETED")
    }
  }

  @Nested
  @DisplayName("GET /migrate/csip/active-migration")
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
            migrationType = CSIP,
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
            migrationType = CSIP,
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
      webTestClient.get().uri("/migrate/csip/active-migration")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get action migration data`() {
      webTestClient.get().uri("/migrate/csip/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/csip/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
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
      csipMappingApi.stubCSIPMappingByMigrationId(count = 123456)
      webTestClient.get().uri("/migrate/csip/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
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
        .jsonPath("$.migrationType").isEqualTo("CSIP")
    }
  }

  @Nested
  @DisplayName("POST /migrate/csip/{migrationId}/terminate/")
  inner class TerminateMigrationCSIP {
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
      webTestClient.post().uri("/migrate/csip/{migrationId}/cancel/", "some id")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to terminate a migration`() {
      webTestClient.post().uri("/migrate/csip/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/csip/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    internal fun `will terminate a running migration`() {
      val count = 30L
      csipNomisApi.stubGetInitialCount(CSIP_ID_URL, count) { csipIdsPagedResponse(it) }
      csipNomisApi.stubGetPagedCSIPIds(totalElements = count, pageSize = 10)
      csipMappingApi.stubCSIPMappingByMigrationId(count = count.toInt())

      val migrationId = webTestClient.post().uri("/migrate/csip")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
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
        .returnResult<MigrationContext<CSIPMigrationFilter>>()
        .responseBody.blockFirst()!!.migrationId

      webTestClient.post().uri("/migrate/csip/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/csip/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/csip/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_CSIP")))
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
