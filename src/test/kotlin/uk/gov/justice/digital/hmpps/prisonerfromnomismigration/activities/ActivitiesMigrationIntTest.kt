@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class ActivitiesMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  private fun stubMigrationDependencies(
    entities: Int = 1,
    stubCreateMapping: () -> Unit = { mappingApi.stubMappingCreate(ACTIVITIES_CREATE_MAPPING_URL) },
  ) {
    activitiesApi.stubGetActivityCategories()
    nomisApi.stubGetInitialCount(ACTIVITIES_ID_URL, entities.toLong()) { activitiesIdsPagedResponse(it) }
    nomisApi.stubMultipleGetActivitiesIdCounts(totalElements = entities.toLong(), pageSize = 3)
    mappingApi.stubAllMappingsNotFound(ACTIVITIES_GET_MAPPING_URL)
    mappingApi.stubActivitiesMappingByMigrationId()
    mappingApi.stubGetApiLocationNomis(1234, UUID.randomUUID().toString())
    nomisApi.stubMultipleGetActivities(entities)
    activitiesApi.stubCreateActivityForMigration()
    stubCreateMapping()
  }

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

    private fun WebTestClient.performMigration(body: String = """{ "prisonId": "BXI" }""") = post().uri("/migrate/activities")
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
    fun `will migrate several pages of activities`() {
      stubMigrationDependencies(7)

      webTestClient.performMigration()

      // check filter values passed to get activity ids
      nomisApi.verifyActivitiesGetIds("/activities/ids", "BXI")

      // all mappings should be created
      assertThat(mappingApi.createMappingCount(ACTIVITIES_CREATE_MAPPING_URL)).isEqualTo(7)
      mappingApi.verifyCreateActivityMappings(7)

      // all activities should be created in DPS
      assertThat(activitiesApi.createActivitiesCount()).isEqualTo(7)
    }

    @Test
    fun `will migrate a single course activity`() {
      stubMigrationDependencies()

      // Pass a course activity id into the migrate request
      webTestClient.performMigration("""{ "prisonId": "BXI", "courseActivityId": 1 }""")

      // check course activity is included when retrieving ids
      nomisApi.verifyActivitiesGetIds("/activities/ids", "BXI", courseActivityId = 1)

      // single mapping and activity are created
      mappingApi.verifyCreateActivityMappings(1)
      assertThat(activitiesApi.createActivitiesCount()).isEqualTo(1)
    }

    @Test
    fun `will migrate activities to start on requested date`() {
      stubMigrationDependencies()

      // Pass a course activity id into the migrate request
      webTestClient.performMigration("""{ "prisonId": "BXI", "activityStartDate": "${LocalDate.now().plusDays(1)}" }""")

      // check course activity is included when retrieving ids
      nomisApi.verifyActivitiesGetIds("/activities/ids", "BXI")

      // single mapping and activity are created
      mappingApi.verifyCreateActivityMappings(1)
      assertThat(activitiesApi.createActivitiesCount()).isEqualTo(1)
    }

    @Test
    fun `will add analytical events and history`() {
      stubMigrationDependencies(3)

      // stub 2 migrated records and 1 failure for the history
      mappingApi.stubActivitiesMappingByMigrationId(count = 2)
      awsSqsActivitiesMigrationDlqClient!!.sendMessage(activitiesMigrationDlqUrl!!, """{ "message": "some error" }""")

      webTestClient.performMigration()

      verify(telemetryClient).trackEvent(eq("activity-migration-started"), any(), isNull())

      verify(telemetryClient, times(3)).trackEvent(eq("activity-migration-entity-migrated"), any(), isNull())

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
        .jsonPath("$[0].estimatedRecordCount").isEqualTo(3)
        .jsonPath("$[0].migrationType").isEqualTo("ACTIVITIES")
        .jsonPath("$[0].status").isEqualTo("COMPLETED")
        .jsonPath("$[0].filter").value(StringContains("BXI"))
        .jsonPath("$[0].recordsMigrated").isEqualTo(2)
        .jsonPath("$[0].recordsFailed").isEqualTo(1)
    }

    @Test
    fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      stubMigrationDependencies {
        // Force a retry of the mapping creation
        mappingApi.stubMappingCreateFailureFollowedBySuccess(ACTIVITIES_CREATE_MAPPING_URL)
      }

      webTestClient.performMigration()

      // should have retried the create mapping
      assertThat(mappingApi.createMappingCount(ACTIVITIES_CREATE_MAPPING_URL)).isEqualTo(2)
      mappingApi.verifyCreateActivityMappings(1, times = 2)

      // should have created the activity
      assertThat(activitiesApi.createActivitiesCount()).isEqualTo(1)
    }

    @Test
    fun `it will not retry after a 409 (duplicate mapping written to mapping service)`() {
      stubMigrationDependencies {
        // Emulate mapping already exists when trying to create
        mappingApi.stubActivityMappingCreateConflict(
          existingActivityId = 4444,
          duplicateActivityId = 4445,
          nomisCourseActivityId = 123,
        )
      }

      webTestClient.performMigration()

      verify(telemetryClient).trackEvent(
        eq("activity-nomis-migration-duplicate"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["existingActivityId"]).isEqualTo("4444")
          assertThat(it["duplicateActivityId"]).isEqualTo("4445")
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
      mappingApi.verifyCreateActivityMappings(1, times = 1)
    }

    @Nested
    inner class PreventMultipleMigrations {
      @Test
      fun `will not run a new migration if existing is in progress`() {
        runBlocking {
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
              migrationType = MigrationType.ACTIVITIES,
            ),
          )
        }
        webTestClient.post().uri("/migrate/activities")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
          .header("Content-Type", "application/json")
          .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
          .exchange()
          .expectStatus().isEqualTo(HttpStatus.CONFLICT)
      }

      @Test
      fun `will not run a new migration if existing is being cancelled`() {
        runBlocking {
          migrationHistoryRepository.save(
            MigrationHistory(
              migrationId = "2020-01-01T00:00:00",
              whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
              whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
              status = MigrationStatus.CANCELLED_REQUESTED,
              estimatedRecordCount = 123_567,
              filter = "",
              recordsMigrated = 123_560,
              recordsFailed = 7,
              migrationType = MigrationType.ACTIVITIES,
            ),
          )
        }
        webTestClient.post().uri("/migrate/activities")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
          .header("Content-Type", "application/json")
          .body(BodyInserters.fromValue("""{ "prisonId": "BXI" }"""))
          .exchange()
          .expectStatus().isEqualTo(HttpStatus.CONFLICT)
      }

      @Test
      fun `will run a new migration if existing is completed`() {
        runBlocking {
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
              migrationType = MigrationType.ACTIVITIES,
            ),
          )
        }
        stubMigrationDependencies()
        webTestClient.performMigration()
      }

      @Test
      fun `will run a new migration if existing is cancelled`() {
        runBlocking {
          migrationHistoryRepository.save(
            MigrationHistory(
              migrationId = "2020-01-01T00:00:00",
              whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
              whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
              status = MigrationStatus.CANCELLED,
              estimatedRecordCount = 123_567,
              filter = "",
              recordsMigrated = 123_560,
              recordsFailed = 7,
              migrationType = MigrationType.ACTIVITIES,
            ),
          )
        }
        stubMigrationDependencies()
        webTestClient.performMigration()
      }

      @Test
      fun `will run a new migration if a different migration type has started`() {
        runBlocking {
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
              migrationType = MigrationType.VISIT_BALANCE,
            ),
          )
        }
        stubMigrationDependencies()
        webTestClient.performMigration()
      }
    }
  }

  @Nested
  @DisplayName("GET /migrate/activities/history")
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

    @AfterEach
    fun deleteHistoryRecords() = runTest {
      migrationHistoryRepository.deleteAll()
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

  @Nested
  @DisplayName("GET /migrate/activities/history/{migrationId}")
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
          migrationType = MigrationType.ACTIVITIES,
        ),
      )
    }

    @AfterEach
    fun deleteHistoryRecords() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    @Test
    fun `must have valid token to get history`() {
      webTestClient.get().uri("/migrate/activities/history/2020-01-01T00:00:00")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/activities/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can read record`() {
      webTestClient.get().uri("/migrate/activities/history/2020-01-01T00:00:00")
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
  @DisplayName("POST /migrate/activities/{migrationId}/cancel/")
  inner class CancelMigrationActivities {
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
      webTestClient.post().uri("/migrate/activities/{migrationId}/cancel/", "some id")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to cancel a migration`() {
      webTestClient.post().uri("/migrate/activities/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/activities/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `will cancel a running migration`() {
      // slow the API calls so there is time to cancel before it completes
      nomisApi.setGlobalFixedDelay(1000)
      stubMigrationDependencies(entities = 100)
      mappingApi.stubActivitiesMappingByMigrationId(count = 100)

      val migrationId = webTestClient.post().uri("/migrate/activities")
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

      webTestClient.post().uri("/migrate/activities/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/activities/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/activities/history/{migrationId}", migrationId)
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
  @DisplayName("GET /migrate/activities/active-migration")
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
          estimatedRecordCount = 8,
          filter = "",
          recordsMigrated = 7,
          recordsFailed = 1,
          migrationType = MigrationType.ACTIVITIES,
        ),
      )
      migrationHistoryRepository.save(
        MigrationHistory(
          migrationId = "2019-01-01T00:00:00",
          whenStarted = LocalDateTime.parse("2019-01-01T00:00:00"),
          whenEnded = LocalDateTime.parse("2019-01-01T01:00:00"),
          status = MigrationStatus.COMPLETED,
          estimatedRecordCount = 8,
          filter = "",
          recordsMigrated = 8,
          recordsFailed = 0,
          migrationType = MigrationType.ACTIVITIES,
        ),
      )
    }

    @AfterEach
    internal fun deleteHistoryRecords() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    @Test
    internal fun `must have valid token to get active migration data`() {
      webTestClient.get().uri("/migrate/activities/active-migration")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get action migration data`() {
      webTestClient.get().uri("/migrate/activities/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/activities/active-migration")
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
      mappingApi.stubActivitiesMappingByMigrationId(count = 7)
      webTestClient.get().uri("/migrate/activities/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.whenStarted").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.recordsMigrated").isEqualTo(7)
        .jsonPath("$.toBeProcessedCount").isEqualTo(0)
        .jsonPath("$.beingProcessedCount").isEqualTo(0)
        .jsonPath("$.recordsFailed").isEqualTo(0)
        .jsonPath("$.estimatedRecordCount").isEqualTo(8)
        .jsonPath("$.status").isEqualTo("STARTED")
        .jsonPath("$.migrationType").isEqualTo("ACTIVITIES")
    }
  }

  @Nested
  @DisplayName("GET /migrate/activities/end")
  inner class EndMigratedActivities {

    private val migrationId = "2023-10-05T09:58:45"
    private val migrationIdNoStarDate = "2024-11-05T09:58:45"
    private val count = 3
    private val activityStartDate = LocalDate.parse("2023-10-08")

    @BeforeEach
    internal fun stubApis() = runTest {
      mappingApi.stubActivitiesMappingByMigrationId(count = count, migrationId = migrationId)
      nomisApi.stubEndActivities()
    }

    @BeforeEach
    internal fun createHistoryRecords() = runTest {
      migrationHistoryRepository.deleteAll()
      migrationHistoryRepository.save(
        MigrationHistory(
          migrationId = migrationId,
          whenStarted = LocalDateTime.parse("2023-10-05T09:58:45"),
          whenEnded = LocalDateTime.parse("2023-10-05T10:04:45"),
          status = MigrationStatus.COMPLETED,
          estimatedRecordCount = 8,
          filter = """{"prisonId":"BLI","activityStartDate":"$activityStartDate"}""",
          recordsMigrated = 8,
          recordsFailed = 0,
          migrationType = MigrationType.ACTIVITIES,
        ),
      )
      migrationHistoryRepository.save(
        MigrationHistory(
          migrationId = migrationIdNoStarDate,
          whenStarted = LocalDateTime.parse("2024-12-05T09:58:45"),
          whenEnded = LocalDateTime.parse("2024-11-05T10:04:45"),
          status = MigrationStatus.COMPLETED,
          estimatedRecordCount = 8,
          filter = """{"prisonId":"BLI"}""",
          recordsMigrated = 8,
          recordsFailed = 0,
          migrationType = MigrationType.ACTIVITIES,
        ),
      )
    }

    @AfterEach
    internal fun deleteHistoryRecords() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    @Test
    internal fun `must have valid token`() {
      webTestClient.put().uri("/migrate/activities/$migrationId/end")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role`() {
      webTestClient.put().uri("/migrate/activities/$migrationId/end")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return not found for unknown migration`() {
      mappingApi.stubActivitiesMappingByMigrationIdFails(404)

      webTestClient.put().uri("/migrate/activities/$migrationId/end")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    internal fun `will pass on upstream errors`() {
      mappingApi.stubActivitiesMappingByMigrationIdFails(500)

      webTestClient.put().uri("/migrate/activities/$migrationId/end")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is5xxServerError
    }

    @Test
    internal fun `will end activities`() {
      webTestClient.put().uri("/migrate/activities/$migrationIdNoStarDate/end")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk

      mappingApi.verifyActivitiesMappingByMigrationId(migrationIdNoStarDate, count)
      nomisApi.verifyEndActivities("[1,2,3]", "${LocalDate.now()}")
    }

    @Test
    internal fun `will end activities with an activity start date filter`() {
      webTestClient.put().uri("/migrate/activities/$migrationId/end")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk

      mappingApi.verifyActivitiesMappingByMigrationId(migrationId, count)
      nomisApi.verifyEndActivities("[1,2,3]", "${activityStartDate.minusDays(1)}")
    }
  }
}
