package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.hamcrest.core.StringContains
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
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.COMPLETED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension.Companion.visitsApi
import java.time.Duration
import java.time.LocalDateTime

class VisitsMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/visits")
  inner class MigrationVisits {
    @BeforeEach
    internal fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    @Test
    internal fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/visits")
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will start processing pages of visits`() {
      nomisApi.stubGetVisitsInitialCount(86)
      nomisApi.stubMultipleGetVisitsCounts(totalElements = 86, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = 86)
      mappingApi.stubNomisVisitNotFound()
      mappingApi.stubRoomMapping()
      mappingApi.stubVisitMappingCreate()
      visitsApi.stubCreateVisit()
      mappingApi.stubVisitMappingByMigrationId(count = 86)

      webTestClient.post().uri("/migrate/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
              "prisonIds": [
                "MDI",
                "BXI"
              ],
              "visitTypes": [
                "SCON",
                "OFFI"
              ],
              "fromDateTime": "2020-01-01T01:30:00",
              "toDateTime": "2020-01-02T23:30:00"
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createVisitMappingCount() } matches { it == 86 }

      // check filter matches what is passed in
      nomisApi.verifyGetVisitsFilter(
        prisonIds = listOf("MDI", "BXI"),
        visitTypes = listOf("SCON", "OFFI"),
        fromDateTime = "2020-01-01T01:30",
        toDateTime = "2020-01-02T23:30",
      )

      // check that each visit is created in VSIP
      assertThat(visitsApi.createVisitCount()).isEqualTo(86)

      val visitIdsUpTo86 = (1L..86L).map { it }.toTypedArray()

      // Check each visit has a mapping (each visit will be a unique number starting from 1)
      mappingApi.verifyCreateMappingVisitIds(visitIdsUpTo86)
    }

    @Test
    internal fun `will add analytical events for starting, ending and each migrated record`() {
      nomisApi.stubGetVisitsInitialCount(26)
      nomisApi.stubMultipleGetVisitsCounts(totalElements = 26, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = 26)
      mappingApi.stubNomisVisitNotFound()
      mappingApi.stubRoomMapping()
      mappingApi.stubVisitMappingCreate()
      visitsApi.stubCreateVisit()

      // stub 25 migrated records and 1 fake a failure
      mappingApi.stubVisitMappingByMigrationId(count = 25)
      awsSqsVisitsMigrationDlqClient!!.sendMessage(visitsMigrationDlqUrl!!, """{ "message": "some error" }""")

      webTestClient.post().uri("/migrate/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
              "prisonIds": [
                "HEI"
              ],
              "visitTypes": [
                "SCON"
              ]
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createVisitMappingCount() } matches { it == 26 }

      await untilAsserted {
        verify(telemetryClient).trackEvent(eq("visits-migration-started"), any(), isNull())
      }

      await untilAsserted {
        verify(telemetryClient, times(26)).trackEvent(eq("visits-migration-entity-migrated"), any(), isNull())
      }

      await.atMost(Duration.ofSeconds(31)) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("visits-migration-completed"),
          any(),
          isNull(),
        )
      }

      await untilAsserted {
        webTestClient.get().uri("/migrate/visits/history")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.size()").isEqualTo(1)
          .jsonPath("$[0].migrationId").isNotEmpty
          .jsonPath("$[0].whenStarted").isNotEmpty
          .jsonPath("$[0].whenEnded").isNotEmpty
          .jsonPath("$[0].estimatedRecordCount").isEqualTo(26)
          .jsonPath("$[0].migrationType").isEqualTo("VISITS")
          .jsonPath("$[0].status").isEqualTo("COMPLETED")
          .jsonPath("$[0].filter").value(StringContains("SCON"))
          .jsonPath("$[0].filter").value(StringContains("HEI"))
          .jsonPath("$[0].recordsMigrated").isEqualTo(25)
          .jsonPath("$[0].recordsFailed").isEqualTo(1)
      }
    }

    @Test
    internal fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      nomisApi.stubGetVisitsInitialCount(1)
      nomisApi.stubMultipleGetVisitsCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = 1)
      mappingApi.stubNomisVisitNotFound()
      mappingApi.stubRoomMapping()
      visitsApi.stubCreateVisit()
      mappingApi.stubVisitMappingCreateFailureFollowedBySuccess()

      webTestClient.post().uri("/migrate/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue("{}"),
        )
        .exchange()
        .expectStatus().isAccepted

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createVisitMappingCount() } matches { it == 2 }

      // check that each visit is created in VSIP
      assertThat(visitsApi.createVisitCount()).isEqualTo(1)

      // should retry to create mapping twice
      mappingApi.verifyCreateMappingVisitIds(arrayOf(1L), times = 2)
    }

    @Test
    internal fun `it will not retry after a 409 (duplicate visit written to Visits API)`() {
      nomisApi.stubGetVisitsInitialCount(1)
      nomisApi.stubMultipleGetVisitsCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = 1)
      mappingApi.stubVisitsCreateConflict(existingVsipId = 12, duplicateVsipId = 654321, nomisVisitId = 1)
      visitsApi.stubCreateVisit()

      webTestClient.post().uri("/migrate/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createVisitMappingCount() } matches { it == 1 }

      // check that one visit is created
      assertThat(visitsApi.createVisitCount())
        .isEqualTo(1)

      // doesn't retry
      mappingApi.verifyCreateMappingVisitIds(arrayOf(1), times = 1)

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-visit-duplicate"),
        org.mockito.kotlin.check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["existingVsipId"]).isEqualTo("12")
          assertThat(it["duplicateVsipId"]).isEqualTo("654321")
          assertThat(it["existingNomisId"]).isEqualTo("1")
          assertThat(it["duplicateNomisId"]).isEqualTo("1")
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("GET /migrate/visits/history")
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
            filter = """"prisonIds":["HEI"],"visitTypes":["SCON"],"ignoreMissingRoom":false""",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = VISITS,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T01:00:00"),
            status = COMPLETED,
            estimatedRecordCount = 123_567,
            filter = """"prisonIds":["WWI"],"visitTypes":["SCON"],"ignoreMissingRoom":false""",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = VISITS,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T03:00:00"),
            status = COMPLETED,
            estimatedRecordCount = 123_567,
            filter = """"prisonIds":["BXI"],"visitTypes":["SCON"],"ignoreMissingRoom":false""",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = VISITS,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-03T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-03T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-03T03:00:00"),
            status = COMPLETED,
            estimatedRecordCount = 123_567,
            filter = """"prisonIds":["BXI"],"visitTypes":["SCON"],"ignoreMissingRoom":false""",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = VISITS,
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
      webTestClient.get().uri("/migrate/visits/history")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/visits/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `can read all records with no filter`() {
      webTestClient.get().uri("/migrate/visits/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
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
        it.path("/migrate/visits/history")
          .queryParam("fromDateTime", "2020-01-02T02:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
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
        it.path("/migrate/visits/history")
          .queryParam("toDateTime", "2020-01-02T00:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
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
        it.path("/migrate/visits/history")
          .queryParam("fromDateTime", "2020-01-03T01:59:59")
          .queryParam("toDateTime", "2020-01-03T02:00:01")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
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
        it.path("/migrate/visits/history")
          .queryParam("includeOnlyFailures", "true")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(2)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    internal fun `can filter by prisonId`() {
      webTestClient.get().uri {
        it.path("/migrate/visits/history")
          .queryParam("prisonId", "WWI")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(1)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-02T00:00:00")
    }
  }

  @Nested
  @DisplayName("GET /migrate/visits/history/{migrationId}")
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
            filter = """"prisonIds":["HEI"],"visitTypes":["SCON"],"ignoreMissingRoom":false""",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = VISITS,
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
      webTestClient.get().uri("/migrate/visits/history/2020-01-01T00:00:00")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/visits/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `can read record`() {
      webTestClient.get().uri("/migrate/visits/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.status").isEqualTo("COMPLETED")
    }
  }

  @DisplayName("filter Visit room usage count")
  @Nested
  inner class GetVisitRoomCountByFilterRequest {

    @Test
    fun `get room usage all visit rooms - no filter specified`() {
      nomisApi.stubGetVisitsRoomUsage()
      mappingApi.stubRoomMapping("AGI")
      mappingApi.stubMissingRoomMapping("BXI")
      mappingApi.stubRoomMapping("AKI")
      webTestClient.get().uri("migrate/visits/rooms/usage")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(3)
        .jsonPath("$[0].agencyInternalLocationDescription").isEqualTo("AGI-VISITS-OFF_VIS")
        .jsonPath("$[0].count").isEqualTo(95)
        .jsonPath("$[0].prisonId").isEqualTo("AGI")
        .jsonPath("$[0].vsipRoom").isEqualTo("1234")
        .jsonPath("$[1].agencyInternalLocationDescription").isEqualTo("AKI-VISITS-3RD SECTOR")
        .jsonPath("$[1].count").isEqualTo(390)
        .jsonPath("$[1].prisonId").isEqualTo("AKI")
        .jsonPath("$[1].vsipRoom").isEqualTo("1234")
        .jsonPath("$[2].agencyInternalLocationDescription").isEqualTo("BXI-VISITS-SOC_VIS")
        .jsonPath("$[2].count").isEqualTo(14314)
        .jsonPath("$[2].prisonId").isEqualTo("BXI")
        .jsonPath("$[2].vsipRoom").doesNotExist()
    }

    @Test
    fun `malformed date returns bad request`() {
      webTestClient.get().uri {
        it.path("migrate/visits/rooms/usage")
          .queryParam("fromDateTime", "202-10-01T09:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `get visit rooms usage prevents access without appropriate role`() {
      assertThat(
        webTestClient.get().uri("migrate/visits/rooms/usage")
          .headers(setAuthorisation(roles = listOf("ROLE_BLA")))
          .exchange()
          .expectStatus().isForbidden,
      )
    }

    @Test
    fun `get visit rooms usage prevents access without authorization`() {
      assertThat(
        webTestClient.get().uri("/visits/rooms/usage")
          .exchange()
          .expectStatus().isUnauthorized,
      )
    }
  }

  @Nested
  @DisplayName("POST /migrate/visits/{migrationId}/terminate/")
  inner class TerminateMigrationVisits {
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
      webTestClient.post().uri("/migrate/visits/{migrationId}/cqncel/", "some id")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to terminate a migration`() {
      webTestClient.post().uri("/migrate/visits/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/visits/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    internal fun `will terminate a running migration`() {
      val count = 30L
      nomisApi.stubGetVisitsInitialCount(count)
      nomisApi.stubMultipleGetVisitsCounts(totalElements = count, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = count)
      mappingApi.stubNomisVisitNotFound()
      mappingApi.stubRoomMapping()
      mappingApi.stubVisitMappingCreate()
      visitsApi.stubCreateVisit()
      mappingApi.stubVisitMappingByMigrationId(count = count.toInt())

      val migrationId = webTestClient.post().uri("/migrate/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
              "prisonIds": [
                "MDI",
                "BXI"
              ],
              "visitTypes": [
                "SCON",
                "OFFI"
              ],
              "fromDateTime": "2020-01-01T01:30:00",
              "toDateTime": "2020-01-02T23:30:00"
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted
        .returnResult<MigrationContext<VisitsMigrationFilter>>()
        .responseBody.blockFirst()!!.migrationId

      webTestClient.post().uri("/migrate/visits/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/visits/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/visits/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationId)
          .jsonPath("$.status").isEqualTo("CANCELLED")
      }
    }
  }

  @Nested
  @DisplayName("GET /migrate/visits/active-migration")
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
            migrationType = MigrationType.VISITS,
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
            migrationType = MigrationType.VISITS,
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
      webTestClient.get().uri("/migrate/visits/active-migration")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get action migration data`() {
      webTestClient.get().uri("/migrate/visits/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/visits/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
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
      webTestClient.get().uri("/migrate/visits/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.whenStarted").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.recordsMigrated").isEqualTo(123560)
        .jsonPath("$.toBeProcessedCount").isEqualTo(0)
        .jsonPath("$.beingProcessedCount").isEqualTo(0)
        .jsonPath("$.recordsFailed").isEqualTo(0)
        .jsonPath("$.estimatedRecordCount").isEqualTo(123567)
        .jsonPath("$.status").isEqualTo("STARTED")
        .jsonPath("$.migrationType").isEqualTo("VISITS")
    }
  }
}

fun someMigrationFilter(): BodyInserter<String, ReactiveHttpOutputMessage> = BodyInserters.fromValue(
  """
  {
    "prisonIds": [
      "MDI",
      "BXI"
    ]
  }
  """.trimIndent(),
)
