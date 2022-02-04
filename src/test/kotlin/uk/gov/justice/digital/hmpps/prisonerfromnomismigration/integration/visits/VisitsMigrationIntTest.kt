package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.visits

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.check
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.http.ReactiveHttpOutputMessage
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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
      nomisApi.stubGetVisitsInitialCount(23_045)

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

      await untilCallTo { Mockito.mockingDetails(visitsMigrationService).invocations.size } matches { it == (2 + 24) }
      verify(visitsMigrationService).migrateVisits(
        check {
          assertThat(it.prisonIds).containsExactly("MDI", "BXI")
          assertThat(it.visitTypes).containsExactly("SCON", "OFFI")
          assertThat(it.fromDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T01:30:00"))
          assertThat(it.toDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T23:30:00"))
        }
      )
      verify(visitsMigrationService).migrateVisitsByPage(
        check {
          assertThat(it.body.prisonIds).containsExactly("MDI", "BXI")
          assertThat(it.body.visitTypes).containsExactly("SCON", "OFFI")
          assertThat(it.body.fromDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T01:30:00"))
          assertThat(it.body.toDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T23:30:00"))
          assertThat(LocalDateTime.parse(it.migrationId)).isCloseTo(LocalDateTime.now(), within(10, ChronoUnit.SECONDS))
          assertThat(it.estimatedCount).isEqualTo(23_045)
        }
      )
      verify(visitsMigrationService, times(24)).migrateVisitsForPage(
        check {
          assertThat(it.body.filter.prisonIds).containsExactly("MDI", "BXI")
          assertThat(it.body.filter.visitTypes).containsExactly("SCON", "OFFI")
          assertThat(it.body.filter.fromDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T01:30:00"))
          assertThat(it.body.filter.toDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T23:30:00"))
          assertThat(LocalDateTime.parse(it.migrationId)).isCloseTo(LocalDateTime.now(), within(10, ChronoUnit.SECONDS))
          assertThat(it.estimatedCount).isEqualTo(23_045)
          assertThat(it.body.pageSize).isEqualTo(1_000)
          assertThat(it.body.pageNumber).isBetween(0, 23)
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
