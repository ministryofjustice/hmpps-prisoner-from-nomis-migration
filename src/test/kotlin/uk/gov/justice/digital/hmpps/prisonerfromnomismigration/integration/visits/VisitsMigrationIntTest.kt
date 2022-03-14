package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.visits

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
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
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.ReactiveHttpOutputMessage
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitMappingApiExtension.Companion.visitMappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension.Companion.visitsApi
import java.time.Duration

class VisitsMigrationIntTest : SqsIntegrationTestBase() {

  @SpyBean
  private lateinit var telemetryClient: TelemetryClient

  @Nested
  @DisplayName("POST /migrate/visits")
  inner class MigrationVisits {
    @BeforeEach
    internal fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
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
      nomisApi.stubGetVisitsInitialCount(86)
      nomisApi.stubMultipleGetVisitsCounts(totalElements = 86, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = 86)
      visitMappingApi.stubNomisVisitNotFound()
      visitMappingApi.stubRoomMapping()
      visitMappingApi.stubVisitMappingCreate()
      visitsApi.stubCreateVisit()
      visitMappingApi.stubVisitMappingByMigrationId(count = 86)

      webTestClient.post().uri("/migrate/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
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
            """.trimIndent()
          )
        )
        .exchange()
        .expectStatus().isAccepted

      // wait for all mappings to be created before verifying
      await untilCallTo { visitMappingApi.createVisitMappingCount() } matches { it == 86 }

      // check filter matches what is passed in
      nomisApi.verifyGetVisitsFilter(
        prisonIds = listOf("MDI", "BXI"),
        visitTypes = listOf("SCON", "OFFI"),
        fromDateTime = "2020-01-01T01:30",
        toDateTime = "2020-01-02T23:30"
      )

      // check that each visit is created in VSIP
      assertThat(visitsApi.createVisitCount()).isEqualTo(86)

      val visitIdsUpTo86 = (1L..86L).map { it }.toTypedArray()

      // Check each visit has a mapping (each visit will be a unique number starting from 1)
      visitMappingApi.verifyCreateMappingVisitIds(visitIdsUpTo86)
    }

    @Test
    internal fun `will add analytical events for starting, ending and each migrated record`() {
      nomisApi.stubGetVisitsInitialCount(26)
      nomisApi.stubMultipleGetVisitsCounts(totalElements = 26, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = 26)
      visitMappingApi.stubNomisVisitNotFound()
      visitMappingApi.stubRoomMapping()
      visitMappingApi.stubVisitMappingCreate()
      visitsApi.stubCreateVisit()

      // stub 25 migrated records and 1 fake a failure
      visitMappingApi.stubVisitMappingByMigrationId(count = 25)
      awsSqsDlqClient!!.sendMessage(dlqUrl, """{ "message": "some error" }""")

      webTestClient.post().uri("/migrate/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue(
            """
            {
              "prisonIds": [
                "HEI"
              ],
              "visitTypes": [
                "SCON"
              ]
            }
            """.trimIndent()
          )
        )
        .exchange()
        .expectStatus().isAccepted

      // wait for all mappings to be created before verifying
      await untilCallTo { visitMappingApi.createVisitMappingCount() } matches { it == 26 }

      verify(telemetryClient).trackEvent(eq("nomis-migration-visits-started"), any(), isNull())
      verify(telemetryClient, times(26)).trackEvent(eq("nomis-migration-visit-migrated"), any(), isNull())

      await.atMost(Duration.ofSeconds(21)) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("nomis-migration-visits-completed"),
          any(),
          isNull()
        )
      }

      await untilAsserted {
        webTestClient.get().uri("/history")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
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
      nomisApi.stubGetVisitsInitialCount(1)
      nomisApi.stubMultipleGetVisitsCounts(totalElements = 1, pageSize = 10)
      nomisApi.stubMultipleGetVisits(totalElements = 1)
      visitMappingApi.stubNomisVisitNotFound()
      visitMappingApi.stubRoomMapping()
      visitsApi.stubCreateVisit()
      visitMappingApi.stubVisitMappingCreateFailureFollowedBySuccess()

      webTestClient.post().uri("/migrate/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .header("Content-Type", "application/json")
        .body(
          BodyInserters.fromValue("{}")
        )
        .exchange()
        .expectStatus().isAccepted

      // wait for all mappings to be created before verifying
      await untilCallTo { visitMappingApi.createVisitMappingCount() } matches { it == 2 }

      // check that each visit is created in VSIP
      assertThat(visitsApi.createVisitCount()).isEqualTo(1)

      // should retry to create mapping twice
      visitMappingApi.verifyCreateMappingVisitIds(arrayOf(1L), times = 2)
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
  """.trimIndent()
)
