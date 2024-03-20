package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.web.reactive.function.BodyInserters
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.COMPLETED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.LOCATIONS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension.Companion.locationsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.LOCATIONS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.locationIdsPagedResponse
import java.time.Duration
import java.time.LocalDateTime

private const val DPS_LOCATION_ID = "abcde123-1234-1234-1234-1234567890ab"
private const val DPS_PARENT_LOCATION_ID = "fedcba98-1234-1234-1234-1234567890ab"
private const val NOMIS_PARENT_LOCATION_ID = 45678L

class LocationsMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/locations")
  inner class MigrationLocations {
    @BeforeEach
    fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    private fun WebTestClient.performMigration(body: String = "{ }") =
      post().uri("/migrate/locations")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
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
          eq("locations-migration-completed"),
          any(),
          isNull(),
        )
      }

    @Test
    fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/locations")
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("{ }"))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/locations")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("{ }"))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will start processing pages of locations`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.LOCATIONS_ID_URL, 86) { locationIdsPagedResponse(it) }
      nomisApi.stubMultipleGetLocationIdCounts(totalElements = 86, pageSize = 10)
      nomisApi.stubMultipleGetLocations(1..86, NOMIS_PARENT_LOCATION_ID)

      mappingApi.stubGetAnyLocationNotFound()
      mappingApi.stubGetLocation(DPS_PARENT_LOCATION_ID, NOMIS_PARENT_LOCATION_ID)
      mappingApi.stubMappingCreate(LOCATIONS_CREATE_MAPPING_URL)

      locationsApi.stubUpsertLocationForMigration()
      mappingApi.stubLocationsMappingByMigrationId(count = 86)

      webTestClient.performMigration()

      // check filter matches what is passed in
      nomisApi.verify(getRequestedFor(urlPathEqualTo("/locations/ids")))

      await untilAsserted {
        assertThat(locationsApi.createLocationMigrationCount()).isEqualTo(86)
      }
    }

    @Test
    fun `will add analytical events for starting, ending and each migrated record`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.LOCATIONS_ID_URL, 26) { locationIdsPagedResponse(it) }
      nomisApi.stubMultipleGetLocationIdCounts(totalElements = 26, pageSize = 10)
      nomisApi.stubMultipleGetLocations(1..26, NOMIS_PARENT_LOCATION_ID)
      locationsApi.stubUpsertLocationForMigration()
      mappingApi.stubGetAnyLocationNotFound()
      mappingApi.stubGetLocation(DPS_PARENT_LOCATION_ID, NOMIS_PARENT_LOCATION_ID)
      mappingApi.stubMappingCreate(LOCATIONS_CREATE_MAPPING_URL)

      // stub 25 migrated records and 1 fake a failure
      mappingApi.stubLocationsMappingByMigrationId(count = 25)
      awsSqsLocationsMigrationDlqClient!!.sendMessage(
        SendMessageRequest.builder().queueUrl(locationsMigrationDlqUrl).messageBody("""{ "message": "some error" }""")
          .build(),
      ).get()

      webTestClient.performMigration()

      verify(telemetryClient).trackEvent(eq("locations-migration-started"), any(), isNull())
      verify(telemetryClient, times(26)).trackEvent(eq("locations-migration-entity-migrated"), any(), isNull())

      await untilAsserted {
        webTestClient.get().uri("/migrate/locations/history")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.size()").isEqualTo(1)
          .jsonPath("$[0].migrationId").isNotEmpty
          .jsonPath("$[0].whenStarted").isNotEmpty
          .jsonPath("$[0].whenEnded").isNotEmpty
          .jsonPath("$[0].estimatedRecordCount").isEqualTo(26)
          .jsonPath("$[0].migrationType").isEqualTo("LOCATIONS")
          .jsonPath("$[0].status").isEqualTo("COMPLETED")
          .jsonPath("$[0].recordsMigrated").isEqualTo(25)
          .jsonPath("$[0].recordsFailed").isEqualTo(1)
      }
    }

    @Test
    fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.LOCATIONS_ID_URL, 1) { locationIdsPagedResponse(it) }
      nomisApi.stubMultipleGetLocationIdCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetLocations(1..1, NOMIS_PARENT_LOCATION_ID)
      mappingApi.stubGetAnyLocationNotFound()
      mappingApi.stubGetLocation(DPS_PARENT_LOCATION_ID, NOMIS_PARENT_LOCATION_ID)
      mappingApi.stubLocationsMappingByMigrationId()
      locationsApi.stubUpsertLocationForMigration(DPS_LOCATION_ID)
      mappingApi.stubMappingCreateFailureFollowedBySuccess(LOCATIONS_CREATE_MAPPING_URL)

      webTestClient.performMigration()

      // check that one location is created
      assertThat(locationsApi.createLocationMigrationCount()).isEqualTo(1)

      // should retry to create mapping twice
      mappingApi.verifyCreateMappingLocationIds(arrayOf(DPS_LOCATION_ID), times = 2)
    }

    @Test
    fun `it will not retry after a 409 (duplicate location written to locations API)`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.LOCATIONS_ID_URL, 1) { locationIdsPagedResponse(it) }
      nomisApi.stubMultipleGetLocationIdCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetLocations(1..1, NOMIS_PARENT_LOCATION_ID)
      mappingApi.stubGetAnyLocationNotFound()
      mappingApi.stubGetLocation(DPS_PARENT_LOCATION_ID, NOMIS_PARENT_LOCATION_ID)
      mappingApi.stubLocationsMappingByMigrationId()
      locationsApi.stubUpsertLocationForMigration(DPS_LOCATION_ID)
      val duplicateLocationId = "abcde123-1234-1234-1234-deadbeefcafe"
      mappingApi.stubLocationMappingCreateConflict(4321, DPS_LOCATION_ID, duplicateLocationId)

      webTestClient.performMigration()

      // check that one location is created
      assertThat(locationsApi.createLocationMigrationCount()).isEqualTo(1)

      // doesn't retry
      mappingApi.verifyCreateMappingLocationIds(arrayOf(DPS_LOCATION_ID), times = 1)

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-location-duplicate"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["duplicateDpsLocationId"]).isEqualTo(duplicateLocationId)
          assertThat(it["duplicateNomisLocationId"]).isEqualTo("4321")
          assertThat(it["existingDpsLocationId"]).isEqualTo(DPS_LOCATION_ID)
          assertThat(it["existingNomisLocationId"]).isEqualTo("4321")
          assertThat(it["durationMinutes"]).isEqualTo("0")
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("GET /migrate/locations/history")
  inner class GetAll {
    @BeforeEach
    fun createHistoryRecords() {
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
            migrationType = LOCATIONS,
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
            migrationType = LOCATIONS,
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
            migrationType = LOCATIONS,
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
            migrationType = LOCATIONS,
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
      webTestClient.get().uri("/migrate/locations/history")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/locations/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can read all records with no filter`() {
      webTestClient.get().uri("/migrate/locations/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
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
        it.path("/migrate/locations/history")
          .queryParam("fromDateTime", "2020-01-02T02:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
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
        it.path("/migrate/locations/history")
          .queryParam("toDateTime", "2020-01-02T00:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
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
        it.path("/migrate/locations/history")
          .queryParam("fromDateTime", "2020-01-03T01:59:59")
          .queryParam("toDateTime", "2020-01-03T02:00:01")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
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
        it.path("/migrate/locations/history")
          .queryParam("includeOnlyFailures", "true")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
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
  @DisplayName("GET /migrate/locations/history/{migrationId}")
  inner class Get {
    @BeforeEach
    fun createHistoryRecords() {
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
            migrationType = LOCATIONS,
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
      webTestClient.get().uri("/migrate/locations/history/2020-01-01T00:00:00")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/locations/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can read record`() {
      webTestClient.get().uri("/migrate/locations/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.status").isEqualTo("COMPLETED")
    }
  }

  @Nested
  @DisplayName("GET /migrate/locations/active-migration")
  inner class GetActiveMigration {
    @BeforeEach
    fun createHistoryRecords() {
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
            migrationType = LOCATIONS,
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
            migrationType = LOCATIONS,
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
    fun `must have valid token to get active migration data`() {
      webTestClient.get().uri("/migrate/locations/active-migration")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to get action migration data`() {
      webTestClient.get().uri("/migrate/locations/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/locations/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
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
    fun `can read active migration data`() {
      mappingApi.stubLocationsMappingByMigrationId(count = 123456)
      webTestClient.get().uri("/migrate/locations/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
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
        .jsonPath("$.migrationType").isEqualTo("LOCATIONS")
    }
  }

  @Nested
  @DisplayName("POST /migrate/locations/{migrationId}/terminate/")
  inner class TerminateMigrationLocations {
    @BeforeEach
    fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    @Test
    fun `must have valid token to terminate a migration`() {
      webTestClient.post().uri("/migrate/locations/{migrationId}/cqncel/", "some id")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to terminate a migration`() {
      webTestClient.post().uri("/migrate/locations/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/locations/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `will terminate a running migration`() {
      val count = 30L
      nomisApi.stubGetInitialCount(NomisApiExtension.LOCATIONS_ID_URL, count) { locationIdsPagedResponse(it) }
      nomisApi.stubMultipleGetLocationIdCounts(totalElements = count, pageSize = 10)
      mappingApi.stubLocationsMappingByMigrationId(count = count.toInt())

      val migrationId = webTestClient.post().uri("/migrate/locations")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
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
        .returnResult<MigrationContext<LocationsMigrationFilter>>()
        .responseBody.blockFirst()!!.migrationId

      webTestClient.post().uri("/migrate/locations/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/locations/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/locations/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_LOCATIONS")))
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
