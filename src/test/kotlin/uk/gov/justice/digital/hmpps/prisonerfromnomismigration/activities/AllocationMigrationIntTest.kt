package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.web.reactive.function.BodyInserters
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

    // Call this after each migration is started - it means we can make immediate assertions, and we know the next test has a clean slate
    private fun waitUntilCompleted() =
      await.atMost(Duration.ofSeconds(31)) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("allocations-migration-completed"),
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
      activitiesApi.stubGetActivityCategories()
      nomisApi.stubGetInitialCount(NomisApiExtension.ALLOCATIONS_ID_URL, 7) { allocationsIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAllocationsIdCounts(totalElements = 7, pageSize = 3)
      mappingApi.stubAllMappingsNotFound(MappingApiExtension.ALLOCATIONS_GET_MAPPING_URL)
      nomisApi.stubMultipleGetAllocations(7)
      mappingApi.stubMultipleGetActivityMappings(7)
      activitiesApi.stubCreateAllocationForMigration(7)
      mappingApi.stubMappingCreate(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL)

      webTestClient.post().uri("/migrate/allocations")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isAccepted

      waitUntilCompleted()

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
      activitiesApi.stubGetActivityCategories()
      nomisApi.stubGetInitialCount(NomisApiExtension.ALLOCATIONS_ID_URL, 3) { allocationsIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAllocationsIdCounts(totalElements = 3, pageSize = 3)
      mappingApi.stubAllMappingsNotFound(MappingApiExtension.ALLOCATIONS_GET_MAPPING_URL)
      nomisApi.stubMultipleGetAllocations(3)
      mappingApi.stubMultipleGetActivityMappings(3)
      activitiesApi.stubCreateAllocationForMigration(3)
      mappingApi.stubMappingCreate(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL)

      // Pass a course activity id into the migrate request
      webTestClient.post().uri("/migrate/allocations")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI", "courseActivityId": 1 }"""))
        .exchange()
        .expectStatus().isAccepted

      waitUntilCompleted()

      // check course activity is included when retrieving ids
      nomisApi.verifyActivitiesGetIds("/allocations/ids", "BXI", listOf("SAA_EDUCATION", "SAA_INDUCTION"), courseActivityId = 1)

      // mappings and allocations should be created
      mappingApi.verifyCreateAllocationMappings(3)
      activitiesApi.verifyCreateAllocationsForMigration(3)
    }

    @Test
    fun `will migrate allocation with a null split activity mapping`() {
      activitiesApi.stubGetActivityCategories()
      nomisApi.stubGetInitialCount(NomisApiExtension.ALLOCATIONS_ID_URL, 1) { allocationsIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAllocationsIdCounts(totalElements = 1, pageSize = 3)
      mappingApi.stubAllMappingsNotFound(MappingApiExtension.ALLOCATIONS_GET_MAPPING_URL)
      nomisApi.stubMultipleGetAllocations(1)
      mappingApi.stubMappingCreate(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL)
      activitiesApi.stubCreateAllocationForMigration(1)

      // The 2nd activity schedule id returned after creating the DPS allocation is null
      mappingApi.stubMultipleGetActivityMappings(1, activityScheduleId2 = null)

      // Pass a course activity id into the migrate request
      webTestClient.post().uri("/migrate/allocations")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isAccepted

      waitUntilCompleted()

      // the null 2nd activity id is included when creating the DPS allocation
      activitiesApi.verifyCreateAllocationsForMigration(1, activityScheduleId2 = null)
    }

    @Test
    fun `will add analytical events and history`() {
      activitiesApi.stubGetActivityCategories()
      nomisApi.stubGetInitialCount(NomisApiExtension.ALLOCATIONS_ID_URL, 3) { allocationsIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAllocationsIdCounts(totalElements = 3, pageSize = 3)
      mappingApi.stubAllMappingsNotFound(MappingApiExtension.ALLOCATIONS_GET_MAPPING_URL)
      nomisApi.stubMultipleGetAllocations(3)
      mappingApi.stubMultipleGetActivityMappings(3)
      activitiesApi.stubCreateAllocationForMigration(3)
      mappingApi.stubMappingCreate(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL)

      // stub 2 migrated records and 1 failure for the history
      mappingApi.stubAllocationsMappingByMigrationId(count = 2)
      awsSqsAllocationsMigrationDlqClient!!.sendMessage(allocationsMigrationDlqUrl!!, """{ "message": "some error" }""")

      webTestClient.post().uri("/migrate/allocations")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isAccepted

      waitUntilCompleted()

      verify(telemetryClient).trackEvent(eq("allocations-migration-started"), any(), isNull())

      verify(telemetryClient, times(3)).trackEvent(eq("allocation-migration-entity-migrated"), any(), isNull())

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
      activitiesApi.stubGetActivityCategories()
      nomisApi.stubGetInitialCount(NomisApiExtension.ALLOCATIONS_ID_URL, 1) { allocationsIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAllocationsIdCounts(totalElements = 1, pageSize = 3)
      mappingApi.stubAllMappingsNotFound(MappingApiExtension.ALLOCATIONS_GET_MAPPING_URL)
      nomisApi.stubMultipleGetAllocations(1)
      mappingApi.stubMultipleGetActivityMappings(1)
      activitiesApi.stubCreateAllocationForMigration(1)

      // Force a retry of the mapping creation
      mappingApi.stubMappingCreateFailureFollowedBySuccess(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL)

      webTestClient.post().uri("/migrate/allocations")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isAccepted

      waitUntilCompleted()

      // should have retried the create mapping
      assertThat(mappingApi.createMappingCount(MappingApiExtension.ALLOCATIONS_CREATE_MAPPING_URL)).isEqualTo(2)
      mappingApi.verifyCreateAllocationMappings(1, times = 2)

      // should have created the activity
      assertThat(activitiesApi.createAllocationsCount()).isEqualTo(1)
    }

    @Test
    fun `will not retry after a 409 (duplicate mapping written to mapping service)`() {
      activitiesApi.stubGetActivityCategories()
      nomisApi.stubGetInitialCount(NomisApiExtension.ALLOCATIONS_ID_URL, 1) { allocationsIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAllocationsIdCounts(totalElements = 1, pageSize = 3)
      mappingApi.stubAllMappingsNotFound(MappingApiExtension.ALLOCATIONS_GET_MAPPING_URL)
      nomisApi.stubMultipleGetAllocations(1)
      mappingApi.stubMultipleGetActivityMappings(1)
      activitiesApi.stubCreateAllocationForMigration(1)

      // Emulate mapping already exists when trying to create
      mappingApi.stubAllocationMappingCreateConflict(
        existingAllocationId = 4444,
        duplicateAllocationId = 5555,
        nomisAllocationId = 123,
      )

      webTestClient.post().uri("/migrate/allocations")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isAccepted

      waitUntilCompleted()

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-allocation-duplicate"),
        org.mockito.kotlin.check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["existingActivityAllocationId"]).isEqualTo("4444")
          assertThat(it["duplicateActivityAllocationId"]).isEqualTo("5555")
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
    fun createHistoryRecords() {
      runBlocking {
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
    }

    @AfterEach
    fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
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
}
