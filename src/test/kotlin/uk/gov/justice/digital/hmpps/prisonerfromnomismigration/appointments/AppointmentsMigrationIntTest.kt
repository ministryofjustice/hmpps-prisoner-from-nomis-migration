package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
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
import org.springframework.http.ReactiveHttpOutputMessage
import org.springframework.test.web.reactive.server.returnResult
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.COMPLETED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.STARTED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.APPOINTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.APPOINTMENTS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.APPOINTMENTS_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.APPOINTMENTS_ID_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.appointmentIdsPagedResponse
import java.time.Duration
import java.time.LocalDateTime

class AppointmentsMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/appointments")
  inner class MigrationAppointments {
    @BeforeEach
    fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    @Test
    fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/appointments")
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/appointments")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will start processing pages of appointments`() {
      nomisApi.stubGetInitialCount(APPOINTMENTS_ID_URL, 14) { appointmentIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAppointmentIdCounts(totalElements = 14, pageSize = 10)
      nomisApi.stubMultipleGetAppointments(1..14)
      mappingApi.stubAllMappingsNotFound(APPOINTMENTS_GET_MAPPING_URL)
      mappingApi.stubMappingCreate(APPOINTMENTS_CREATE_MAPPING_URL)

      activitiesApi.stubCreateAppointmentForMigration(12345)
      mappingApi.stubAppointmentMappingByMigrationId(count = 14)

      webTestClient.post().uri("/migrate/appointments")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
              "fromDate": "2020-01-01",
              "toDate": "2020-01-02",
              "prisonIds": ["MDI"]
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted

      await atMost Duration.ofSeconds(60) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("appointments-migration-completed"),
          any(),
          isNull(),
        )
      }

      // check filter matches what is passed in
      nomisApi.verifyGetIdsCount(
        url = APPOINTMENTS_ID_URL,
        fromDate = "2020-01-01",
        toDate = "2020-01-02",
        prisonId = "MDI",
      )

      activitiesApi.verifyCreatedDate("2023-01-01T11:00:01", "2023-02-02T12:00:03")

      await untilAsserted {
        assertThat(activitiesApi.createAppointmentCount()).isEqualTo(14)
      }
    }

    @Test
    fun `will add analytical events for starting, ending and each migrated record`() {
      nomisApi.stubGetInitialCount(APPOINTMENTS_ID_URL, 3) { appointmentIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAppointmentIdCounts(totalElements = 3, pageSize = 10)
      nomisApi.stubMultipleGetAppointments(1..3)
      activitiesApi.stubCreateAppointmentForMigration(12345)
      mappingApi.stubAllMappingsNotFound(APPOINTMENTS_GET_MAPPING_URL)
      mappingApi.stubMappingCreate(APPOINTMENTS_CREATE_MAPPING_URL)

      // stub 10 migrated records and 1 fake a failure
      mappingApi.stubAppointmentMappingByMigrationId(count = 2)
      awsSqsAppointmentsMigrationDlqClient!!.sendMessage(
        SendMessageRequest.builder().queueUrl(appointmentsMigrationDlqUrl)
          .messageBody("""{ "message": "some error" }""").build(),
      ).get()

      webTestClient.post().uri("/migrate/appointments")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
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

      await atMost Duration.ofSeconds(60) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("appointments-migration-completed"),
          any(),
          isNull(),
        )
      }

      verify(telemetryClient).trackEvent(eq("appointments-migration-started"), any(), isNull())
      verify(telemetryClient, times(3)).trackEvent(eq("appointments-migration-entity-migrated"), any(), isNull())

      await atMost Duration.ofSeconds(20) untilAsserted {
        webTestClient.get().uri("/migrate/appointments/history")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.size()").isEqualTo(1)
          .jsonPath("$[0].migrationId").isNotEmpty
          .jsonPath("$[0].whenStarted").isNotEmpty
          .jsonPath("$[0].estimatedRecordCount").isEqualTo(3)
          .jsonPath("$[0].migrationType").isEqualTo("APPOINTMENTS")
          .jsonPath("$[0].recordsMigrated").isEqualTo(2)
          .jsonPath("$[0].recordsFailed").isEqualTo(1)
          .jsonPath("$[0].whenEnded").isNotEmpty
          .jsonPath("$[0].status").isEqualTo("COMPLETED")
      }
    }

    @Test
    fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      nomisApi.stubGetInitialCount(APPOINTMENTS_ID_URL, 1) { appointmentIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAppointmentIdCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetAppointments(1..1)
      mappingApi.stubAllMappingsNotFound(APPOINTMENTS_GET_MAPPING_URL)
      activitiesApi.stubCreateAppointmentForMigration(654321L)
      mappingApi.stubMappingCreateFailureFollowedBySuccess(APPOINTMENTS_CREATE_MAPPING_URL)

      webTestClient.post().uri("/migrate/appointments")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isAccepted

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createMappingCount(APPOINTMENTS_CREATE_MAPPING_URL) } matches { it == 2 }

      // check that one appointment is created
      assertThat(activitiesApi.createAppointmentCount()).isEqualTo(1)

      // should retry to create mapping twice
      mappingApi.verifyCreateMappingAppointmentIds(arrayOf("654321"), times = 2)
    }

    @Test
    fun `it will not retry after a 409 (duplicate appointment written to Activities API) or mapping already exists`() {
      nomisApi.stubGetInitialCount(APPOINTMENTS_ID_URL, 1) { appointmentIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAppointmentIdCounts(totalElements = 2, pageSize = 10)
      nomisApi.stubMultipleGetAppointments(1..2)
      mappingApi.stubAllMappingsNotFound(APPOINTMENTS_GET_MAPPING_URL)
      activitiesApi.stubCreateAppointmentForMigration(123)
      mappingApi.stubAppointmentMappingCreateConflict(10, 11, 1)
      mappingApi.stubNomisAppointmentsMappingFound(2)

      webTestClient.post().uri("/migrate/appointments")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isAccepted

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createMappingCount(APPOINTMENTS_CREATE_MAPPING_URL) } matches { it == 1 }

      // check that one appointment is created
      assertThat(activitiesApi.createAppointmentCount()).isEqualTo(1)

      // doesn't retry
      mappingApi.verifyCreateMappingAppointmentIds(arrayOf("123"), times = 1)

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-appointment-duplicate"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["existingAppointmentInstanceId"]).isEqualTo("10")
          assertThat(it["duplicateAppointmentInstanceId"]).isEqualTo("11")
          assertThat(it["existingNomisEventId"]).isEqualTo("1")
          assertThat(it["duplicateNomisEventId"]).isEqualTo("1")
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("GET /migrate/appointments/history")
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
            migrationType = APPOINTMENTS,
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
            migrationType = APPOINTMENTS,
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
            migrationType = APPOINTMENTS,
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
            migrationType = APPOINTMENTS,
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
      webTestClient.get().uri("/migrate/appointments/history")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/appointments/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can read all records with no filter`() {
      webTestClient.get().uri("/migrate/appointments/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
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
        it.path("/migrate/appointments/history")
          .queryParam("fromDateTime", "2020-01-02T02:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
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
        it.path("/migrate/appointments/history")
          .queryParam("toDateTime", "2020-01-02T00:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
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
        it.path("/migrate/appointments/history")
          .queryParam("fromDateTime", "2020-01-03T01:59:59")
          .queryParam("toDateTime", "2020-01-03T02:00:01")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
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
        it.path("/migrate/appointments/history")
          .queryParam("includeOnlyFailures", "true")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
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
  @DisplayName("GET /migrate/appointments/history/{migrationId}")
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
            migrationType = APPOINTMENTS,
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
      webTestClient.get().uri("/migrate/appointments/history/2020-01-01T00:00:00")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to get history`() {
      webTestClient.get().uri("/migrate/appointments/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `can read record`() {
      webTestClient.get().uri("/migrate/appointments/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.status").isEqualTo("COMPLETED")
    }
  }

  @Nested
  @DisplayName("POST /migrate/appointments/{migrationId}/terminate/")
  inner class TerminateMigrationAppointments {
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
      webTestClient.post().uri("/migrate/appointments/{migrationId}/cqncel/", "some id")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to terminate a migration`() {
      webTestClient.post().uri("/migrate/appointments/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/appointments/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `will terminate a running migration`() {
      val count = 30L
      nomisApi.stubGetInitialCount(APPOINTMENTS_ID_URL, count) { appointmentIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAppointmentIdCounts(totalElements = count, pageSize = 10)
      mappingApi.stubAppointmentMappingByMigrationId(count = count.toInt())

      val migrationId = webTestClient.post().uri("/migrate/appointments")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
              "fromDate": "2020-01-01",
              "toDate": "2020-01-02",
              "prisonIds": ["MDI"]
            }
            """.trimIndent(),
          ),
        )
        .exchange()
        .expectStatus().isAccepted
        .returnResult<MigrationContext<AppointmentsMigrationFilter>>()
        .responseBody.blockFirst()!!.migrationId

      webTestClient.post().uri("/migrate/appointments/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/appointments/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/appointments/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
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
  @DisplayName("GET /migrate/appointments/active-migration")
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
            status = STARTED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = APPOINTMENTS,
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
            migrationType = APPOINTMENTS,
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
      webTestClient.get().uri("/migrate/appointments/active-migration")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to get action migration data`() {
      webTestClient.get().uri("/migrate/appointments/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/appointments/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
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
      mappingApi.stubAppointmentMappingByMigrationId(count = 123456)
      webTestClient.get().uri("/migrate/appointments/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
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
        .jsonPath("$.migrationType").isEqualTo("APPOINTMENTS")
    }
  }
}

fun someMigrationFilter(): BodyInserter<String, ReactiveHttpOutputMessage> =
  BodyInserters.fromValue("""{ "prisonIds": ["MDI"] }""")
