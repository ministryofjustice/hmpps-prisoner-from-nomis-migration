@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.ACTIVITIES
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

  @Autowired
  private lateinit var objectMapper: ObjectMapper

  private val today = LocalDate.now()
  private val tomorrow = today.plusDays(1)

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
    fun setUp() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    private fun WebTestClient.performMigration(body: String = """{ "prisonId": "BXI", "activityStartDate": "$tomorrow" }""") = post().uri("/migrate/activities")
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
        .body(BodyInserters.fromValue("""{ "prisonId": "BXI", "activityStartDate": "$tomorrow" }"""))
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
      webTestClient.performMigration("""{ "prisonId": "BXI", "courseActivityId": 1, "activityStartDate": "$tomorrow" }""")

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

      webTestClient.get().uri("/migrate/history/all/{migrationType}", ACTIVITIES)
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
              migrationType = ACTIVITIES,
            ),
          )
        }
        webTestClient.post().uri("/migrate/activities")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
          .header("Content-Type", "application/json")
          .body(BodyInserters.fromValue("""{ "prisonId": "BXI", "activityStartDate": "$tomorrow" }"""))
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
              migrationType = ACTIVITIES,
            ),
          )
        }
        webTestClient.post().uri("/migrate/activities")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
          .header("Content-Type", "application/json")
          .body(BodyInserters.fromValue("""{ "prisonId": "BXI", "activityStartDate": "$tomorrow" }"""))
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
              migrationType = ACTIVITIES,
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
              migrationType = ACTIVITIES,
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
  @DisplayName("PUT /migrate/activities/end")
  inner class EndMigratedActivities {

    private val migrationId = "2023-10-05T09:58:45"
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
          migrationType = ACTIVITIES,
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
    internal fun `will end activities`() = runTest {
      webTestClient.put().uri("/migrate/activities/$migrationId/end")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ACTIVITIES")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk

      with(migrationHistoryRepository.findById(migrationId)!!) {
        val filter = objectMapper.readValue<ActivitiesMigrationFilter>(filter!!)
        assertThat(filter.activityStartDate).isEqualTo(activityStartDate)
        assertThat(filter.nomisActivityEndDate).isEqualTo(activityStartDate.minusDays(1))
      }

      mappingApi.verifyActivitiesMappingByMigrationId(migrationId, count)
      nomisApi.verifyEndActivities("[1,2,3]", "${activityStartDate.minusDays(1)}")
    }
  }
}
