package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
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
import org.springframework.web.reactive.function.BodyInserters
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.APPOINTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.APPOINTMENTS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.APPOINTMENTS_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.APPOINTMENTS_ID_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.appointmentIdsPagedResponse
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.Duration
import java.util.UUID

class AppointmentsMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {
  @Nested
  @DisplayName("POST /migrate/appointments")
  inner class MigrationAppointments {
    @BeforeEach
    fun setUp() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    private fun WebTestClient.performMigration(body: String = "{ }") = post().uri("/migrate/appointments")
      .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_APPOINTMENTS")))
      .header("Content-Type", "application/json")
      .body(BodyInserters.fromValue(body))
      .exchange()
      .expectStatus().isAccepted
      .also {
        waitUntilCompleted()
      }

    private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
      verify(telemetryClient).trackEvent(
        eq("appointments-migration-completed"),
        any(),
        isNull(),
      )
    }

    @Test
    fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/appointments")
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("{ }"))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/appointments")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("{ }"))
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
      mappingApi.stubGetApiLocationNomis(23456, UUID.randomUUID().toString())

      activitiesApi.stubCreateAppointmentForMigration(12345)
      mappingApi.stubAppointmentMappingByMigrationId(count = 14)

      webTestClient.performMigration(
        """
          {
            "fromDate": "2020-01-01",
            "toDate": "2020-01-02",
            "prisonIds": ["MDI"]
          }
        """.trimIndent(),
      )

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
      mappingApi.stubGetApiLocationNomis(23456, UUID.randomUUID().toString())

      // stub 10 migrated records and 1 fake a failure
      mappingApi.stubAppointmentMappingByMigrationId(count = 2)
      awsSqsAppointmentsMigrationDlqClient!!.sendMessage(
        SendMessageRequest.builder().queueUrl(appointmentsMigrationDlqUrl)
          .messageBody("""{ "message": "some error" }""").build(),
      ).get()

      webTestClient.performMigration()

      verify(telemetryClient).trackEvent(eq("appointments-migration-started"), any(), isNull())
      verify(telemetryClient, times(3)).trackEvent(eq("appointments-migration-entity-migrated"), any(), isNull())

      await atMost Duration.ofSeconds(20) untilAsserted {
        webTestClient.get().uri("/migrate/history/all/{migrationType}", APPOINTMENTS)
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
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
      mappingApi.stubAppointmentMappingByMigrationId()
      activitiesApi.stubCreateAppointmentForMigration(654321L)
      mappingApi.stubMappingCreateFailureFollowedBySuccess(APPOINTMENTS_CREATE_MAPPING_URL)
      mappingApi.stubGetApiLocationNomis(23456, UUID.randomUUID().toString())

      webTestClient.performMigration("""{ "prisonIds": ["MDI"] }""")

      // wait for all mappings to be created before verifying
      await untilCallTo { mappingApi.createMappingCount(APPOINTMENTS_CREATE_MAPPING_URL) } matches { it == 2 }

      // check that one appointment is created
      assertThat(activitiesApi.createAppointmentCount()).isEqualTo(1)

      // should retry to create mapping twice
      mappingApi.verifyCreateMappingAppointmentIds(arrayOf("654321"), times = 2)
    }

    @Test
    fun `will end up on the DLQ if the location mapping transformation fails`() {
      nomisApi.stubGetInitialCount(APPOINTMENTS_ID_URL, 1) { appointmentIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAppointmentIdCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetAppointments(1..1)
      activitiesApi.stubCreateAppointmentForMigration(12345)
      mappingApi.stubAllMappingsNotFound(APPOINTMENTS_GET_MAPPING_URL)
      mappingApi.stubMappingCreate(APPOINTMENTS_CREATE_MAPPING_URL)
      // Not found from location mapping request
      mappingApi.stubGetAnyLocationNotFound()

      webTestClient.performMigration("""{ "prisonIds": ["MDI"] }""")

      // should end up on the DLQ
      await untilCallTo {
        awsSqsAppointmentsMigrationDlqClient!!.countMessagesOnQueue(appointmentsMigrationDlqUrl!!).get()
      } matches { it == 1 }

      // check that appointment is not created
      assertThat(activitiesApi.createAppointmentCount()).isEqualTo(0)
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
      mappingApi.stubAppointmentMappingByMigrationId()
      mappingApi.stubGetApiLocationNomis(23456, UUID.randomUUID().toString())

      webTestClient.performMigration("""{ "prisonIds": ["MDI"] }""")

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

    @Test
    fun `will add tracking event if DPS ignore the migration`() {
      nomisApi.stubGetInitialCount(APPOINTMENTS_ID_URL, 1) { appointmentIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAppointmentIdCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetAppointments(1..1)
      activitiesApi.stubCreateAppointmentForMigration(null)
      mappingApi.stubAllMappingsNotFound(APPOINTMENTS_GET_MAPPING_URL)
      mappingApi.stubMappingCreate(APPOINTMENTS_CREATE_MAPPING_URL)
      mappingApi.stubGetApiLocationNomis(23456, UUID.randomUUID().toString())

      // stub 1 migrated records
      mappingApi.stubAppointmentMappingByMigrationId(count = 1)

      webTestClient.performMigration()

      verify(telemetryClient).trackEvent(eq("appointments-migration-started"), any(), isNull())
      verify(telemetryClient).trackEvent(eq("appointments-migration-entity-ignored"), any(), isNull())
    }
  }
}
