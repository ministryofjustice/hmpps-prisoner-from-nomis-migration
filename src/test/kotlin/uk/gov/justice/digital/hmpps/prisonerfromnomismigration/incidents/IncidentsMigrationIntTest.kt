package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
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
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsApiExtension.Companion.incidentsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsMappingApiMockServer.Companion.INCIDENTS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsNomisApiMockServer.Companion.INCIDENTS_ID_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.INCIDENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.Duration

private const val DPS_INCIDENT_ID = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42"
private const val NOMIS_INCIDENT_ID = 1234L

class IncidentsMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var incidentsNomisApi: IncidentsNomisApiMockServer

  @Autowired
  private lateinit var incidentsMappingApi: IncidentsMappingApiMockServer

  @Nested
  @DisplayName("POST /migrate/incidents")
  inner class MigrationIncidents {
    @BeforeEach
    internal fun setUp() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    private fun WebTestClient.performMigration(body: String = "{ }") = post().uri("/migrate/incidents")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
      .header("Content-Type", "application/json")
      .body(BodyInserters.fromValue(body))
      .exchange()
      .expectStatus().isAccepted
      .also {
        waitUntilCompleted()
      }

    private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
      verify(telemetryClient).trackEvent(
        eq("incidents-migration-completed"),
        any(),
        isNull(),
      )
    }

    @Test
    internal fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/incidents")
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("{ }"))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/incidents")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(BodyInserters.fromValue("{ }"))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will start processing pages of Incidents`() {
      nomisApi.stubGetInitialCount(INCIDENTS_ID_URL, 86) { incidentIdsPagedResponse(it) }
      incidentsNomisApi.stubMultipleGetIncidentIdCounts(totalElements = 86, pageSize = 10)
      incidentsNomisApi.stubMultipleGetIncidents(1..86)

      incidentsMappingApi.stubGetAnyIncidentNotFound()
      incidentsMappingApi.stubMappingCreate()

      incidentsApi.stubIncidentUpsert()
      incidentsMappingApi.stubIncidentsMappingByMigrationId(count = 86)

      webTestClient.performMigration(
        """
          {
            "fromDate": "2020-01-01",
            "toDate": "2020-01-02"
          }
        """.trimIndent(),
      )

      // check filter matches what is passed in
      nomisApi.verifyGetIdsCount(
        url = "/incidents/ids",
        fromDate = "2020-01-01",
        toDate = "2020-01-02",
      )

      await untilAsserted {
        assertThat(incidentsApi.createIncidentUpsertCount()).isEqualTo(86)
      }
    }

    @Test
    internal fun `will add analytical events for starting, ending and each migrated record`() {
      nomisApi.stubGetInitialCount(INCIDENTS_ID_URL, 26) { incidentIdsPagedResponse(it) }
      incidentsNomisApi.stubMultipleGetIncidentIdCounts(totalElements = 26, pageSize = 10)
      incidentsNomisApi.stubMultipleGetIncidents(1..26)
      incidentsApi.stubIncidentUpsert()
      incidentsMappingApi.stubGetAnyIncidentNotFound()
      incidentsMappingApi.stubMappingCreate()

      // stub 25 migrated records and 1 fake a failure
      incidentsMappingApi.stubIncidentsMappingByMigrationId(count = 25)
      awsSqsIncidentsMigrationDlqClient!!.sendMessage(
        SendMessageRequest.builder().queueUrl(incidentsMigrationDlqUrl).messageBody("""{ "message": "some error" }""").build(),
      ).get()

      webTestClient.performMigration()

      verify(telemetryClient).trackEvent(eq("incidents-migration-started"), any(), isNull())
      verify(telemetryClient, times(26)).trackEvent(eq("incidents-migration-entity-migrated"), any(), isNull())

      await untilAsserted {
        webTestClient.get().uri("/migrate/history/all/{migrationType}", INCIDENTS)
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.size()").isEqualTo(1)
          .jsonPath("$[0].migrationId").isNotEmpty
          .jsonPath("$[0].whenStarted").isNotEmpty
          .jsonPath("$[0].whenEnded").isNotEmpty
          .jsonPath("$[0].estimatedRecordCount").isEqualTo(26)
          .jsonPath("$[0].migrationType").isEqualTo("INCIDENTS")
          .jsonPath("$[0].status").isEqualTo("COMPLETED")
          .jsonPath("$[0].recordsMigrated").isEqualTo(25)
          .jsonPath("$[0].recordsFailed").isEqualTo(1)
      }
    }

    @Test
    internal fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      nomisApi.stubGetInitialCount(INCIDENTS_ID_URL, 1) { incidentIdsPagedResponse(it) }
      incidentsNomisApi.stubMultipleGetIncidentIdCounts(totalElements = 1, pageSize = 10)
      incidentsNomisApi.stubMultipleGetIncidents(1..1)
      incidentsMappingApi.stubGetAnyIncidentNotFound()
      incidentsMappingApi.stubIncidentsMappingByMigrationId(count = 1)
      incidentsApi.stubIncidentUpsert()
      mappingApi.stubMappingCreateFailureFollowedBySuccess(INCIDENTS_CREATE_MAPPING_URL)

      webTestClient.performMigration()

      // check that one incident is created
      assertThat(incidentsApi.createIncidentUpsertCount()).isEqualTo(1)

      // should retry to create mapping twice
      incidentsMappingApi.verifyCreateMappingIncidentId(DPS_INCIDENT_ID, times = 2)
    }

    @Test
    internal fun `it will not retry after a 409 (duplicate incident written to Mapping API)`() {
      val duplicateDPSIncidentId = "ddd596da-8eab-4d2a-a026-bc5afb8acda0"

      nomisApi.stubGetInitialCount(INCIDENTS_ID_URL, 1) { incidentIdsPagedResponse(it) }
      incidentsNomisApi.stubMultipleGetIncidentIdCounts(totalElements = 1, pageSize = 10)
      incidentsNomisApi.stubMultipleGetIncidents(1..1)
      incidentsMappingApi.stubGetAnyIncidentNotFound()
      incidentsMappingApi.stubIncidentsMappingByMigrationId()
      incidentsApi.stubIncidentUpsert(duplicateDPSIncidentId)
      incidentsMappingApi.stubIncidentMappingCreateConflict()
      webTestClient.performMigration()

      // check that one incident is created
      assertThat(incidentsApi.createIncidentUpsertCount()).isEqualTo(1)

      // doesn't retry
      incidentsMappingApi.verifyCreateMappingIncidentId(duplicateDPSIncidentId)

      verify(telemetryClient).trackEvent(
        eq("incidents-migration-nomis-duplicate"),
        check {
          assertThat(it["existingNomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
          assertThat(it["duplicateNomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
          assertThat(it["existingDPSIncidentId"]).isEqualTo(DPS_INCIDENT_ID)
          assertThat(it["duplicateDPSIncidentId"]).isEqualTo(duplicateDPSIncidentId)
          assertThat(it["migrationId"]).isNotNull()
        },
        isNull(),
      )
    }

    @Test
    internal fun `it will attempt to determine dpsIncident Id if get a 409 (duplicate incident written to Incidents API)`() {
      nomisApi.stubGetInitialCount(INCIDENTS_ID_URL, 1) { incidentIdsPagedResponse(it) }
      incidentsNomisApi.stubMultipleGetIncidentIdCounts(totalElements = 1, pageSize = 10)
      incidentsNomisApi.stubMultipleGetIncidents(1..1)
      incidentsMappingApi.stubGetAnyIncidentNotFound()
      incidentsApi.stubIncidentUpsert(status = HttpStatus.CONFLICT)
      incidentsApi.stubGetBasicIncident()
      incidentsMappingApi.stubMappingCreate()

      webTestClient.performMigration()

      assertThat(incidentsApi.createIncidentUpsertCount()).isEqualTo(1)

      verify(telemetryClient).trackEvent(
        eq("incidents-migration-entity-migration-conflict"),
        check {
          assertThat(it["nomisIncidentId"]).isEqualTo("1")
          assertThat(it["reason"]).contains("Conflict: 409 Conflict from POST")
          assertThat(it["migrationId"]).isNotNull()
        },
        isNull(),
      )

      incidentsApi.verifyMigrationGetBasicIncident()
      incidentsMappingApi.verifyCreateMappingIncidentId(dpsIncidentId = DPS_INCIDENT_ID)
    }

    @Test
    internal fun `it will retry to write mapping after recovering from incidents api conflict 409 (from Incidents API)`() {
      nomisApi.stubGetInitialCount(INCIDENTS_ID_URL, 1) { incidentIdsPagedResponse(it) }
      incidentsNomisApi.stubMultipleGetIncidentIdCounts(totalElements = 1, pageSize = 10)
      incidentsNomisApi.stubMultipleGetIncidents(1..1)
      incidentsMappingApi.stubGetAnyIncidentNotFound()
      incidentsApi.stubIncidentUpsert(status = HttpStatus.CONFLICT)
      incidentsApi.stubGetBasicIncident()
      incidentsMappingApi.stubMappingCreate()
      mappingApi.stubMappingCreateFailureFollowedBySuccess(INCIDENTS_CREATE_MAPPING_URL)

      webTestClient.performMigration()

      assertThat(incidentsApi.createIncidentUpsertCount()).isEqualTo(1)

      verify(telemetryClient).trackEvent(
        eq("incidents-migration-entity-migration-conflict"),
        check {
          assertThat(it["nomisIncidentId"]).isEqualTo("1")
          assertThat(it["reason"]).contains("Conflict: 409 Conflict from POST")
          assertThat(it["migrationId"]).isNotNull()
        },
        isNull(),
      )

      incidentsApi.verifyMigrationGetBasicIncident()

      // should retry to create mapping twice
      incidentsMappingApi.verifyCreateMappingIncidentId(DPS_INCIDENT_ID, times = 2)

      verify(telemetryClient).trackEvent(
        eq("incidents-migration-entity-migrated"),
        check {
          assertThat(it["nomisIncidentId"]).isEqualTo("1")
          assertThat(it["dpsIncidentId"]).isEqualTo(DPS_INCIDENT_ID)
          assertThat(it["migrationId"]).isNotNull()
        },
        isNull(),
      )
    }
  }
}
