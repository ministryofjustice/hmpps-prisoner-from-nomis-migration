package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ReactiveHttpOutputMessage
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.adjudicationsIdsPagedResponse
import java.time.Duration

class AdjudicationsMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/adjudications")
  inner class MigrationAdjudications {
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
      webTestClient.post().uri("/migrate/adjudications")
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/adjudications")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .header("Content-Type", "application/json")
        .body(someMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will start processing pages of adjudications`() {
      nomisApi.stubGetMigrationInitialCount("/adjudications/ids", 2) { adjudicationsIdsPagedResponse(it) }
      nomisApi.stubMultipleGetAdjudicationIdCounts(totalElements = 2, pageSize = 10)
      nomisApi.stubMultipleGetAdjudications(1..2)
      mappingApi.stubAllMappingsNotFound("/mapping/adjudications/adjudication-number/\\d*")
      mappingApi.stubMappingCreate("/mapping/adjudications")

      adjudicationsApi.stubCreateAdjudicationForMigration()
      mappingApi.stubAdjudicationMappingByMigrationId(count = 2)

      webTestClient.post().uri("/migrate/adjudications")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ADJUDICATIONS")))
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

      await atMost Duration.ofSeconds(60) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("adjudications-migration-completed"),
          any(),
          isNull(),
        )
      }

      // check filter matches what is passed in
      nomisApi.verifyGetIdsCount(
        url = "/adjudications/ids",
        fromDate = "2020-01-01",
        toDate = "2020-01-02",
      )

      await untilAsserted {
        assertThat(adjudicationsApi.createAdjudicationsCount()).isEqualTo(2)
      }
    }
  }
}

fun someMigrationFilter(): BodyInserter<String, ReactiveHttpOutputMessage> = BodyInserters.fromValue(
  """
  {
  }
  """.trimIndent(),
)
