@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.hamcrest.core.StringContains
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
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.ALLOCATIONS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.allocationsIdsPagedResponse
import java.time.Duration

class AllocationMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {
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
    mappingApi.stubAllocationsMappingByMigrationId()
    nomisApi.stubMultipleGetAllocations(entities)
    stubGetActivityMappings()
    activitiesApi.stubCreateAllocationForMigration(entities)
    stubCreateMapping()
  }

  @Nested
  @DisplayName("POST /migrate/allocations")
  inner class MigrateAllocations {
    @BeforeEach
    fun setUp() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    private fun WebTestClient.performMigration(body: String = """{ "prisonId": "BXI" }""") = post().uri("/migrate/allocations")
      .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
      .header("Content-Type", "application/json")
      .body(BodyInserters.fromValue(body))
      .exchange()
      .expectStatus().isAccepted
      .also {
        waitUntilCompleted()
      }

    private fun waitUntilCompleted() = await atMost Duration.ofSeconds(31) untilAsserted {
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
      nomisApi.verifyActivitiesGetIds("/allocations/ids", "BXI")

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
      webTestClient.get().uri("/migrate/history/all/{migrationType}", ALLOCATIONS)
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
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
}
