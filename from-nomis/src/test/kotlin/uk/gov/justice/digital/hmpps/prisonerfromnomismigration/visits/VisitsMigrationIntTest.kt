package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

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
import org.springframework.http.HttpStatus
import org.springframework.http.ReactiveHttpOutputMessage
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.VISITS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.VISITS_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension.Companion.visitsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.visitPagedResponse
import java.time.Duration

class VisitsMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {
  @Nested
  @DisplayName("POST /migrate/visits")
  inner class MigrationVisits {
    @BeforeEach
    internal fun setUp() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    private fun WebTestClient.performMigration(body: String = "{ }") = post().uri("/migrate/visits")
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
        eq("visits-migration-completed"),
        any(),
        isNull(),
      )
    }

    @Test
    internal fun `must have valid token to start migration`() {
      webTestClient.post().uri("/migrate/visits")
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will start processing pages of visits`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.VISITS_ID_URL, 86) { visitPagedResponse(it) }
      nomisApi.stubMultipleGetVisitsCounts(totalElements = 86, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = 86)
      mappingApi.stubAllMappingsNotFound(VISITS_GET_MAPPING_URL)
      mappingApi.stubRoomMapping()
      mappingApi.stubMappingCreate(VISITS_CREATE_MAPPING_URL)
      visitsApi.stubCreateVisit()
      mappingApi.stubVisitMappingByMigrationId(count = 86)

      webTestClient.performMigration(
        """
          {
            "prisonIds": [
              "MDI",
              "BXI"
            ],
            "visitTypes": [
              "SCON",
              "OFFI"
            ],
            "fromDateTime": "2020-01-01T01:30:00",
            "toDateTime": "2020-01-02T23:30:00"
          }
        """.trimIndent(),
      )

      // check filter matches what is passed in
      nomisApi.verifyGetVisitsFilter(
        prisonIds = listOf("MDI", "BXI"),
        visitTypes = listOf("SCON", "OFFI"),
        fromDateTime = "2020-01-01T01:30",
        toDateTime = "2020-01-02T23:30",
      )

      // check that each visit is created in VSIP
      assertThat(visitsApi.createVisitCount()).isEqualTo(86)

      val visitIdsUpTo86 = (1L..86L).map { it }.toTypedArray()

      // Check each visit has a mapping (each visit will be a unique number starting from 1)
      mappingApi.verifyCreateMappingVisitIds(visitIdsUpTo86)
    }

    @Test
    internal fun `will add analytical events for starting, ending and each migrated record`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.VISITS_ID_URL, 26) { visitPagedResponse(it) }
      nomisApi.stubMultipleGetVisitsCounts(totalElements = 26, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = 26)
      mappingApi.stubAllMappingsNotFound(VISITS_GET_MAPPING_URL)
      mappingApi.stubRoomMapping()
      mappingApi.stubMappingCreate(VISITS_CREATE_MAPPING_URL)
      visitsApi.stubCreateVisit()

      // stub 25 migrated records and 1 fake a failure
      mappingApi.stubVisitMappingByMigrationId(count = 25)
      awsSqsVisitsMigrationDlqClient!!.sendMessage(visitsMigrationDlqUrl!!, """{ "message": "some error" }""")

      webTestClient.performMigration(
        """
          {
            "prisonIds": [
              "HEI"
            ],
            "visitTypes": [
              "SCON"
            ]
          }
        """.trimIndent(),
      )

      await untilAsserted {
        verify(telemetryClient).trackEvent(eq("visits-migration-started"), any(), isNull())
      }

      await untilAsserted {
        verify(telemetryClient, times(26)).trackEvent(eq("visits-migration-entity-migrated"), any(), isNull())
      }

      await untilAsserted {
        webTestClient.get().uri("/migrate/history/all/{migrationType}", VISITS)
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
          .jsonPath("$[0].migrationType").isEqualTo("VISITS")
          .jsonPath("$[0].status").isEqualTo("COMPLETED")
          .jsonPath("$[0].filter").value(StringContains("SCON"))
          .jsonPath("$[0].filter").value(StringContains("HEI"))
          .jsonPath("$[0].recordsMigrated").isEqualTo(25)
          .jsonPath("$[0].recordsFailed").isEqualTo(1)
      }
    }

    @Test
    internal fun `will retry to create a mapping, and only the mapping, if it fails first time`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.VISITS_ID_URL, 1) { visitPagedResponse(it) }
      nomisApi.stubMultipleGetVisitsCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = 1)
      mappingApi.stubAllMappingsNotFound(VISITS_GET_MAPPING_URL)
      mappingApi.stubVisitMappingByMigrationId()
      mappingApi.stubRoomMapping()
      visitsApi.stubCreateVisit()
      mappingApi.stubMappingCreateFailureFollowedBySuccess(VISITS_CREATE_MAPPING_URL)

      webTestClient.performMigration()

      // check that each visit is created in VSIP
      assertThat(visitsApi.createVisitCount()).isEqualTo(1)

      // should retry to create mapping twice
      mappingApi.verifyCreateMappingVisitIds(arrayOf(1L), times = 2)
    }

    @Test
    internal fun `it will not retry after a 409 (duplicate visit written to Visits API)`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.VISITS_ID_URL, 1) { visitPagedResponse(it) }
      nomisApi.stubMultipleGetVisitsCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = 1)
      mappingApi.stubVisitsCreateConflict(existingVsipId = 12, duplicateVsipId = 654321, nomisVisitId = 1)
      mappingApi.stubVisitMappingByMigrationId()
      visitsApi.stubCreateVisit()

      webTestClient.performMigration()

      // check that one visit is created
      assertThat(visitsApi.createVisitCount()).isEqualTo(1)

      // doesn't retry
      mappingApi.verifyCreateMappingVisitIds(arrayOf(1), times = 1)

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-visit-duplicate"),
        org.mockito.kotlin.check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["existingVsipId"]).isEqualTo("12")
          assertThat(it["duplicateVsipId"]).isEqualTo("654321")
          assertThat(it["existingNomisId"]).isEqualTo("1")
          assertThat(it["duplicateNomisId"]).isEqualTo("1")
        },
        isNull(),
      )
    }

    @Test
    internal fun `it will not retry after a 422 (DPS refuses to migrate visit due, eg too far in the future)`() {
      nomisApi.stubGetInitialCount(NomisApiExtension.VISITS_ID_URL, 1) { visitPagedResponse(it) }
      nomisApi.stubMultipleGetVisitsCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = 1)
      visitsApi.stubCreateVisit(httpResponse = HttpStatus.UNPROCESSABLE_ENTITY)

      webTestClient.performMigration()

      // check that one attempt at creating a visit is made
      assertThat(visitsApi.createVisitCount()).isEqualTo(1)

      // doesn't try to create mapping
      mappingApi.verifyCreateMappingVisitIds(arrayOf(1), times = 0)

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-visit-rejected"),
        org.mockito.kotlin.check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["nomisId"]).isEqualTo("1")
        },
        isNull(),
      )
    }
  }

  @DisplayName("filter Visit room usage count")
  @Nested
  inner class GetVisitRoomCountByFilterRequest {

    @Test
    fun `get room usage all visit rooms - no filter specified`() {
      nomisApi.stubGetVisitsRoomUsage()
      mappingApi.stubRoomMapping("AGI")
      mappingApi.stubMissingRoomMapping("BXI")
      mappingApi.stubRoomMapping("AKI")
      webTestClient.get().uri("migrate/visits/rooms/usage")
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(3)
        .jsonPath("$[0].agencyInternalLocationDescription").isEqualTo("AGI-VISITS-OFF_VIS")
        .jsonPath("$[0].count").isEqualTo(95)
        .jsonPath("$[0].prisonId").isEqualTo("AGI")
        .jsonPath("$[0].vsipRoom").isEqualTo("1234")
        .jsonPath("$[1].agencyInternalLocationDescription").isEqualTo("AKI-VISITS-3RD SECTOR")
        .jsonPath("$[1].count").isEqualTo(390)
        .jsonPath("$[1].prisonId").isEqualTo("AKI")
        .jsonPath("$[1].vsipRoom").isEqualTo("1234")
        .jsonPath("$[2].agencyInternalLocationDescription").isEqualTo("BXI-VISITS-SOC_VIS")
        .jsonPath("$[2].count").isEqualTo(14314)
        .jsonPath("$[2].prisonId").isEqualTo("BXI")
        .jsonPath("$[2].vsipRoom").doesNotExist()
    }

    @Test
    fun `malformed date returns bad request`() {
      webTestClient.get().uri {
        it.path("migrate/visits/rooms/usage")
          .queryParam("fromDateTime", "202-10-01T09:00:00")
          .build()
      }
        .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `get visit rooms usage prevents access without appropriate role`() {
      assertThat(
        webTestClient.get().uri("migrate/visits/rooms/usage")
          .headers(setAuthorisation(roles = listOf("ROLE_BLA")))
          .exchange()
          .expectStatus().isForbidden,
      )
    }

    @Test
    fun `get visit rooms usage prevents access without authorization`() {
      assertThat(
        webTestClient.get().uri("/visits/rooms/usage")
          .exchange()
          .expectStatus().isUnauthorized,
      )
    }
  }
}

fun someMigrationFilter(): BodyInserter<String, ReactiveHttpOutputMessage> = BodyInserters.fromValue(
  """
  {
    "prisonIds": [
      "MDI",
      "BXI"
    ]
  }
  """.trimIndent(),
)
