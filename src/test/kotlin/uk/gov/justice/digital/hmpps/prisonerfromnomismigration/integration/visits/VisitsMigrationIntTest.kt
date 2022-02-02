package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.visits

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.check
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.ReactiveHttpOutputMessage
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsMigrationService

class VisitsMigrationIntTest : IntegrationTestBase() {
  @SpyBean
  private lateinit var visitsMigrationService: VisitsMigrationService

  @Nested
  @DisplayName("POST /migrate/visits")
  inner class MigrationVisits {
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
              ]
            }
            """.trimIndent()
          )
        )
        .exchange()
        .expectStatus().isAccepted

      await untilCallTo { Mockito.mockingDetails(visitsMigrationService).invocations.size } matches { it == 2 }
      verify(visitsMigrationService).migrateVisits(
        check {
          assertThat(it.prisonIds).containsExactly("MDI", "BXI")
        }
      )
      verify(visitsMigrationService).migrateVisitsByPage(
        check {
          assertThat(it.filter.prisonIds).containsExactly("MDI", "BXI")
          assertThat(it.migrationId).isNotNull()
        }
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
  """.trimIndent()
)
