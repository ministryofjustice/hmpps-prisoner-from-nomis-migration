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
import org.mockito.kotlin.check
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.ACTIVITIES_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.ACTIVITIES_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.ACTIVITIES_ID_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.activitiesIdsPagedResponse
import java.time.Duration
import java.time.LocalDateTime

class ActivitiesMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/activities")
  inner class MigrateActivities {
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
          eq("activity-migration-completed"),
          any(),
          isNull(),
        )
      }

    @Test
    fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/activities")
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/activities")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will start processing pages of activities`() {
      nomisApi.stubGetInitialCount(ACTIVITIES_ID_URL, 7) { activitiesIdsPagedResponse(it) }
      nomisApi.stubMultipleGetActivitiesIdCounts(totalElements = 7, pageSize = 3)
      nomisApi.stubMultipleGetActivities(1..7)
      mappingApi.stubAllMappingsNotFound(ACTIVITIES_GET_MAPPING_URL)
      mappingApi.stubMappingCreate(ACTIVITIES_CREATE_MAPPING_URL)
      activitiesApi.stubCreateActivityForMigration()
      activitiesApi.stubGetActivityCategories()
      mappingApi.stubActivitiesMappingByMigrationId(count = 7)

      webTestClient.post().uri("/migrate/activities")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isAccepted

      waitUntilCompleted()

      // check filter values passed to get activity ids call
      nomisApi.verifyActivitiesGetIds("/activities/ids", "BXI")

      // all mappings should be created
      assertThat(mappingApi.createMappingCount(ACTIVITIES_CREATE_MAPPING_URL)).isEqualTo(7)

      // check that each activity is created in DPS
      assertThat(activitiesApi.createActivitiesCount()).isEqualTo(7)

      // Check each activity has a mapping (each activity will be a unique number starting from 1)
      mappingApi.verifyCreateMappingActivitiesIds(1L..7L)
    }

    @Test
    fun `will start processing a single course activity`() {
      nomisApi.stubGetInitialCount(ACTIVITIES_ID_URL, 1) { activitiesIdsPagedResponse(it) }
      nomisApi.stubMultipleGetActivitiesIdCounts(totalElements = 1, pageSize = 3)
      nomisApi.stubMultipleGetActivities(1..1)
      mappingApi.stubAllMappingsNotFound(ACTIVITIES_GET_MAPPING_URL)
      mappingApi.stubMappingCreate(ACTIVITIES_CREATE_MAPPING_URL)
      activitiesApi.stubCreateActivityForMigration()
      activitiesApi.stubGetActivityCategories()
      mappingApi.stubActivitiesMappingByMigrationId(count = 1)

      webTestClient.post().uri("/migrate/activities")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI", "courseActivityId": 1 }"""))
        .exchange()
        .expectStatus().isAccepted

      waitUntilCompleted()

      // check course activity id included when retrieving ids
      nomisApi.verifyActivitiesGetIds("/activities/ids", "BXI", 1)

      // single mapping created
      assertThat(mappingApi.createMappingCount(ACTIVITIES_CREATE_MAPPING_URL)).isEqualTo(1)

      // single activity created
      assertThat(activitiesApi.createActivitiesCount()).isEqualTo(1)

      // Created the correct mapping
      mappingApi.verifyCreateMappingActivitiesIds(1L..1L)
    }

    @Test
    fun `will add analytical events for starting, ending and each migrated record`() {
      nomisApi.stubGetInitialCount(ACTIVITIES_ID_URL, 7) { activitiesIdsPagedResponse(it) }
      nomisApi.stubMultipleGetActivitiesIdCounts(totalElements = 7, pageSize = 3)
      nomisApi.stubMultipleGetActivities(1..7)
      mappingApi.stubAllMappingsNotFound(ACTIVITIES_GET_MAPPING_URL)
      mappingApi.stubMappingCreate(ACTIVITIES_CREATE_MAPPING_URL)
      activitiesApi.stubCreateActivityForMigration()
      activitiesApi.stubGetActivityCategories()
      mappingApi.stubActivitiesMappingByMigrationId(count = 6)

      // stub 6 migrated records and 1 fake a failure
      mappingApi.stubActivitiesMappingByMigrationId(count = 6)
      awsSqsActivitiesMigrationDlqClient!!.sendMessage(activitiesMigrationDlqUrl!!, """{ "message": "some error" }""")

      webTestClient.post().uri("/migrate/activities")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isAccepted

      waitUntilCompleted()

      verify(telemetryClient).trackEvent(eq("activity-migration-started"), any(), isNull())

      verify(telemetryClient, times(7)).trackEvent(eq("activity-migration-entity-migrated"), any(), isNull())

      webTestClient.get().uri("/migrate/activities/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(1)
        .jsonPath("$[0].migrationId").isNotEmpty
        .jsonPath("$[0].whenStarted").isNotEmpty
        .jsonPath("$[0].whenEnded").isNotEmpty
        .jsonPath("$[0].estimatedRecordCount").isEqualTo(7)
        .jsonPath("$[0].migrationType").isEqualTo("ACTIVITIES")
        .jsonPath("$[0].status").isEqualTo("COMPLETED")
        .jsonPath("$[0].filter").value(StringContains("BXI"))
        .jsonPath("$[0].recordsMigrated").isEqualTo(6)
        .jsonPath("$[0].recordsFailed").isEqualTo(1)
    }

    @Test
    fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      nomisApi.stubGetInitialCount(ACTIVITIES_ID_URL, 1) { activitiesIdsPagedResponse(it) }
      nomisApi.stubMultipleGetActivitiesIdCounts(totalElements = 1, pageSize = 3)
      nomisApi.stubMultipleGetActivities(1..1)
      mappingApi.stubAllMappingsNotFound(ACTIVITIES_GET_MAPPING_URL)
      activitiesApi.stubCreateActivityForMigration()
      activitiesApi.stubGetActivityCategories()
      mappingApi.stubMappingCreateFailureFollowedBySuccess(ACTIVITIES_CREATE_MAPPING_URL)

      webTestClient.post().uri("/migrate/activities")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isAccepted

      waitUntilCompleted()

      // all mappings should be created
      assertThat(mappingApi.createMappingCount(ACTIVITIES_CREATE_MAPPING_URL)).isEqualTo(2)

      // check that each activity is created in DPS
      assertThat(activitiesApi.createActivitiesCount()).isEqualTo(1)

      // should retry to create mapping twice
      mappingApi.verifyCreateMappingActivitiesIds(1L..1L, times = 2)
    }

    @Test
    fun `it will not retry after a 409 (duplicate mapping written to mapping service)`() {
      nomisApi.stubGetInitialCount(ACTIVITIES_ID_URL, 1) { activitiesIdsPagedResponse(it) }
      nomisApi.stubMultipleGetActivitiesIdCounts(totalElements = 1, pageSize = 3)
      nomisApi.stubMultipleGetActivities(1..1)
      mappingApi.stubAllMappingsNotFound(ACTIVITIES_GET_MAPPING_URL)
      activitiesApi.stubCreateActivityForMigration()
      activitiesApi.stubGetActivityCategories()
      mappingApi.stubActivityMappingCreateConflict(4444, 4445, 123)

      webTestClient.post().uri("/migrate/activities")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
        .exchange()
        .expectStatus().isAccepted

      waitUntilCompleted()

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-activity-duplicate"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["existingActivityScheduleId"]).isEqualTo("4444")
          assertThat(it["duplicateActivityScheduleId"]).isEqualTo("4445")
          assertThat(it["existingNomisCourseActivityId"]).isEqualTo("123")
          assertThat(it["duplicateNomisCourseActivityId"]).isEqualTo("123")
        },
        isNull(),
      )

      // Check we tried to create a mapping
      assertThat(mappingApi.createMappingCount(ACTIVITIES_CREATE_MAPPING_URL)).isEqualTo(1)

      // check that the activity is created
      assertThat(activitiesApi.createActivitiesCount()).isEqualTo(1)

      // doesn't retry
      mappingApi.verifyCreateMappingActivitiesIds(1L..1L, times = 1)
    }
  }

  @Nested
  @DisplayName("GET /migrate/activities/history")
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
            migrationType = MigrationType.ACTIVITIES,
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
            migrationType = MigrationType.ACTIVITIES,
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
            migrationType = MigrationType.ACTIVITIES,
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
            migrationType = MigrationType.ACTIVITIES,
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
      webTestClient.get().uri("/migrate/activities/history")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/activities/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can read all records with no filter`() {
      webTestClient.get().uri("/migrate/activities/history")
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
        it.path("/migrate/activities/history")
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
        it.path("/migrate/activities/history")
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
        it.path("/migrate/activities/history")
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
        it.path("/migrate/activities/history")
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
