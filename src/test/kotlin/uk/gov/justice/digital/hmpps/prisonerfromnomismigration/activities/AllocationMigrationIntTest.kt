@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
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
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.allocationsIdsPagedResponse
import java.time.Duration
import java.time.LocalDateTime

class AllocationMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  private fun stubMigrationDependencies(
    entities: Int = 1,
    stubGetActivityMappings: () -> Unit = { mappingApi.stubMultipleGetActivityMappings(entities) },
    stubCreateMapping: () -> Unit = { mappingApi.stubMappingCreate(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL) },
  ) {
    activitiesApi.stubGetActivityCategories()
    nomisApi.stubGetInitialCount(NomisApiExtension.ALLOCATIONS_ID_URL, entities.toLong()) {
      allocationsIdsPagedResponse(
        it,
      )
    }
    nomisApi.stubMultipleGetAllocationsIdCounts(totalElements = entities.toLong(), pageSize = 3)
    mappingApi.stubAllMappingsNotFound(MappingApiExtension.ALLOCATIONS_GET_MAPPING_URL)
    nomisApi.stubMultipleGetAllocations(entities)
    stubGetActivityMappings()
    activitiesApi.stubCreateAllocationForMigration(entities)
    stubCreateMapping()
  }

  @Nested
  @DisplayName("POST /migrate/allocations")
  inner class MigrateAllocations {
    @BeforeEach
    fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    private fun WebTestClient.performMigration(body: String = """{ "prisonId": "BXI" }""") =
      post().uri("/migrate/allocations")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue(body))
        .exchange()
        .expectStatus().isAccepted
        .also {
          waitUntilCompleted()
        }

    private fun waitUntilCompleted() =
      await.atMost(Duration.ofSeconds(31)) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("activity-allocation-migration-completed"),
          any(),
          isNull(),
        )
      }

    @Test
    fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/allocations")
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/allocations")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will migrate several pages of allocations`() {
      stubMigrationDependencies(7)

      webTestClient.performMigration()

      // check filter values passed to get allocation ids call
      nomisApi.verifyActivitiesGetIds("/allocations/ids", "BXI", listOf("SAA_EDUCATION", "SAA_INDUCTION"))

      // all mappings should be created
      assertThat(mappingApi.createMappingCount(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL)).isEqualTo(7)
      mappingApi.verifyCreateAllocationMappings(7)

      // all allocations should be created in DPS
      assertThat(activitiesApi.createAllocationsCount()).isEqualTo(7)
      activitiesApi.verifyCreateAllocationsForMigration(7)
    }

    @Test
    fun `will migrate allocations for a single course activity`() {
      stubMigrationDependencies(3)

      // Pass a course activity id into the migrate request
      webTestClient.performMigration("""{ "prisonId": "BXI", "courseActivityId": 1 }""")

      // check course activity is included when retrieving ids
      nomisApi.verifyActivitiesGetIds(
        "/allocations/ids",
        "BXI",
        listOf("SAA_EDUCATION", "SAA_INDUCTION"),
        courseActivityId = 1,
      )

      // mappings and allocations should be created
      mappingApi.verifyCreateAllocationMappings(3)
      activitiesApi.verifyCreateAllocationsForMigration(3)
    }

    @Test
    fun `will migrate allocation with a null split activity mapping`() {
      stubMigrationDependencies(
        // The 2nd activity schedule id returned after creating the DPS allocation is null
        stubGetActivityMappings = { mappingApi.stubMultipleGetActivityMappings(1, activityScheduleId2 = null) },
      )

      webTestClient.performMigration()

      // the null 2nd activity id is included when creating the DPS allocation
      activitiesApi.verifyCreateAllocationsForMigration(1, activityScheduleId2 = null)
    }

    @Test
    fun `will add analytical events and history`() {
      stubMigrationDependencies(3)

      // stub 2 migrated records and 1 failure for the history
      mappingApi.stubAllocationsMappingByMigrationId(count = 2)
      awsSqsAllocationsMigrationDlqClient!!.sendMessage(allocationsMigrationDlqUrl!!, """{ "message": "some error" }""")

      webTestClient.performMigration()

      // check telemetry published
      verify(telemetryClient).trackEvent(eq("activity-allocation-migration-started"), any(), isNull())
      verify(telemetryClient, times(3)).trackEvent(eq("activity-allocation-migration-entity-migrated"), any(), isNull())

      // check history correct
      webTestClient.get().uri("/migrate/allocations/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(1)
        .jsonPath("$[0].migrationId").isNotEmpty
        .jsonPath("$[0].whenStarted").isNotEmpty
        .jsonPath("$[0].whenEnded").isNotEmpty
        .jsonPath("$[0].estimatedRecordCount").isEqualTo(3)
        .jsonPath("$[0].migrationType").isEqualTo("ALLOCATIONS")
        .jsonPath("$[0].status").isEqualTo("COMPLETED")
        .jsonPath("$[0].filter").value(StringContains("BXI"))
        .jsonPath("$[0].recordsMigrated").isEqualTo(2)
        .jsonPath("$[0].recordsFailed").isEqualTo(1)
    }

    @Test
    fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      stubMigrationDependencies(
        // Force a retry of the mapping creation
        stubCreateMapping = { mappingApi.stubMappingCreateFailureFollowedBySuccess(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL) },
      )

      webTestClient.performMigration()

      // should have retried the create mapping
      assertThat(mappingApi.createMappingCount(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL)).isEqualTo(2)
      mappingApi.verifyCreateAllocationMappings(1, times = 2)

      // should have created the activity
      assertThat(activitiesApi.createAllocationsCount()).isEqualTo(1)
    }

    @Test
    fun `will not retry after a 409 (duplicate mapping written to mapping service)`() {
      stubMigrationDependencies(
        stubCreateMapping = {
          // Emulate mapping already exists when trying to create
          mappingApi.stubAllocationMappingCreateConflict(
            existingAllocationId = 4444,
            duplicateAllocationId = 5555,
            nomisAllocationId = 123,
          )
        },
      )

      webTestClient.performMigration()

      verify(telemetryClient).trackEvent(
        eq("activity-allocation-nomis-migration-duplicate"),
        org.mockito.kotlin.check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["existingDpsAllocationId"]).isEqualTo("4444")
          assertThat(it["duplicateDpsAllocationId"]).isEqualTo("5555")
          assertThat(it["existingNomisAllocationId"]).isEqualTo("123")
          assertThat(it["duplicateNomisAllocationId"]).isEqualTo("123")
        },
        isNull(),
      )

      // Check we tried to create a mapping
      assertThat(mappingApi.createMappingCount(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL)).isEqualTo(1)

      // check that the activity is created
      assertThat(activitiesApi.createAllocationsCount()).isEqualTo(1)

      // doesn't retry
      mappingApi.verifyCreateAllocationMappings(1, times = 1)
    }
  }

  @Nested
  @DisplayName("GET /migrate/allocations/history")
  inner class GetHistory {
    @BeforeEach
    fun createHistoryRecords() = runTest {
      migrationHistoryRepository.deleteAll()
      migrationHistoryRepository.save(
        MigrationHistory(
          migrationId = "2020-01-01T00:00:00",
          whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
          whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
          status = MigrationStatus.COMPLETED,
          estimatedRecordCount = 7,
          filter = "",
          recordsMigrated = 5,
          recordsFailed = 2,
          migrationType = MigrationType.ALLOCATIONS,
        ),
      )
      migrationHistoryRepository.save(
        MigrationHistory(
          migrationId = "2020-01-02T00:00:00",
          whenStarted = LocalDateTime.parse("2020-01-02T00:00:00"),
          whenEnded = LocalDateTime.parse("2020-01-02T01:00:00"),
          status = MigrationStatus.COMPLETED,
          estimatedRecordCount = 8,
          filter = "",
          recordsMigrated = 8,
          recordsFailed = 0,
          migrationType = MigrationType.ALLOCATIONS,
        ),
      )
      migrationHistoryRepository.save(
        MigrationHistory(
          migrationId = "2020-01-02T02:00:00",
          whenStarted = LocalDateTime.parse("2020-01-02T02:00:00"),
          whenEnded = LocalDateTime.parse("2020-01-02T03:00:00"),
          status = MigrationStatus.COMPLETED,
          estimatedRecordCount = 9,
          filter = "",
          recordsMigrated = 9,
          recordsFailed = 0,
          migrationType = MigrationType.ALLOCATIONS,
        ),
      )
      migrationHistoryRepository.save(
        MigrationHistory(
          migrationId = "2020-01-03T02:00:00",
          whenStarted = LocalDateTime.parse("2020-01-03T02:00:00"),
          whenEnded = LocalDateTime.parse("2020-01-03T03:00:00"),
          status = MigrationStatus.COMPLETED,
          estimatedRecordCount = 10,
          filter = "",
          recordsMigrated = 6,
          recordsFailed = 4,
          migrationType = MigrationType.ALLOCATIONS,
        ),
      )
    }

    @AfterEach
    fun deleteHistoryRecords() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    @Test
    fun `must have valid token to get history`() {
      webTestClient.get().uri("/migrate/allocations/history")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/allocations/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can read all records with no filter`() {
      webTestClient.get().uri("/migrate/allocations/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
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
    fun `can filter so only records after a date are returned`() {
      webTestClient.get().uri {
        it.path("/migrate/allocations/history")
          .queryParam("fromDateTime", "2020-01-02T02:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(2)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-02T02:00:00")
    }

    @Test
    fun `can filter so only records before a date are returned`() {
      webTestClient.get().uri {
        it.path("/migrate/allocations/history")
          .queryParam("toDateTime", "2020-01-02T00:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(2)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-02T00:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    fun `can filter so only records between dates are returned`() {
      webTestClient.get().uri {
        it.path("/migrate/allocations/history")
          .queryParam("fromDateTime", "2020-01-03T01:59:59")
          .queryParam("toDateTime", "2020-01-03T02:00:01")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(1)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
    }

    @Test
    fun `can filter so only records with failed records are returned`() {
      webTestClient.get().uri {
        it.path("/migrate/allocations/history")
          .queryParam("includeOnlyFailures", "true")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
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
  @DisplayName("GET /migrate/allocations/history/{migrationId}")
  inner class Get {
    @BeforeEach
    fun createHistoryRecords() = runTest {
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
          migrationType = MigrationType.ALLOCATIONS,
        ),
      )
    }

    @AfterEach
    fun deleteHistoryRecords() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    @Test
    fun `must have valid token to get history`() {
      webTestClient.get().uri("/migrate/allocations/history/2020-01-01T00:00:00")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/allocations/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can read record`() {
      webTestClient.get().uri("/migrate/allocations/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.status").isEqualTo("COMPLETED")
    }
  }

  @Nested
  @DisplayName("POST /migrate/allocations/{migrationId}/cancel/")
  inner class CancelMigrationAllocations {
    @BeforeEach
    fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    @Test
    fun `must have valid token to cancel a migration`() {
      webTestClient.post().uri("/migrate/allocations/{migrationId}/cancel/", "some id")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to cancel a migration`() {
      webTestClient.post().uri("/migrate/allocations/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/allocations/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `will cancel a running migration`() {
      stubMigrationDependencies(entities = 10)
      mappingApi.stubAllocationsMappingByMigrationId(count = 10)

      val migrationId = webTestClient.post().uri("/migrate/allocations")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
              "prisonId": "MDI"
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted
        .returnResult<MigrationContext<ActivitiesMigrationFilter>>()
        .responseBody.blockFirst()!!.migrationId

      webTestClient.post().uri("/migrate/allocations/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/allocations/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/allocations/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
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
  @DisplayName("GET /migrate/allocations/active-migration")
  inner class GetActiveMigration {
    @BeforeEach
    internal fun createHistoryRecords() = runTest {
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
          migrationType = MigrationType.ALLOCATIONS,
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
          migrationType = MigrationType.ALLOCATIONS,
        ),
      )
    }

    @AfterEach
    internal fun deleteHistoryRecords() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    @Test
    internal fun `must have valid token to get active migration data`() {
      webTestClient.get().uri("/migrate/allocations/active-migration")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get action migration data`() {
      webTestClient.get().uri("/migrate/allocations/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/allocations/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
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
      mappingApi.stubAllocationsMappingByMigrationId(count = 123456)
      webTestClient.get().uri("/migrate/allocations/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
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
        .jsonPath("$.migrationType").isEqualTo("ALLOCATIONS")
    }
  }

  @Nested
  @DisplayName("GET /migrate/allocations/ids")
  inner class FindActivitiesToMigrate {
    @BeforeEach
    internal fun stubNomisApi() = runTest {
      activitiesApi.stubGetActivityCategories()
      nomisApi.stubMultipleGetAllocationsIdCounts(2, 3)
    }

    @Test
    internal fun `must have valid token to get active migration data`() {
      webTestClient.get().uri("/migrate/allocations/ids?prisonId=MDI&pageSize=3&page=0")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get action migration data`() {
      webTestClient.get().uri("/migrate/allocations/ids?prisonId=MDI&pageSize=3&page=0")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will call nomis prisoner api with excluded program services`() {
      webTestClient.get().uri("/migrate/allocations/ids?prisonId=MDI&pageSize=3&page=0")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk

      nomisApi.verifyActivitiesGetIds("/allocations/ids", "MDI", listOf("SAA_EDUCATION", "SAA_INDUCTION"))
    }

    @Test
    internal fun `will return allocations and paging details`() {
      webTestClient.get().uri("/migrate/allocations/ids?prisonId=MDI&pageSize=3&page=0")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.content.size()").isEqualTo(2)
        .jsonPath("$.content[0].allocationId").isEqualTo(1)
        .jsonPath("$.content[1].allocationId").isEqualTo(2)
        .jsonPath("$.totalElements").isEqualTo(2)
        .jsonPath("$.pageable.pageNumber").isEqualTo(0)
        .jsonPath("$.pageable.pageSize").isEqualTo(3)
    }
  }
}
