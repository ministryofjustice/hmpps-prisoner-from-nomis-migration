package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.visits

import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.mock.mockito.SpyBean
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
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `must have correct role to start migration`() {
      webTestClient.post().uri("/migrate/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    internal fun `will start processing pages of visits`() {
      webTestClient.post().uri("/migrate/visits")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISITS")))
        .exchange()
        .expectStatus().isAccepted

      // TODO: this is a poor check - but for now good enough. This will be replaced with some
      // Wiremock asserts on the visits service - but for now shows messaging is working
      await untilCallTo { Mockito.mockingDetails(visitsMigrationService).invocations.size } matches { it == 2 }
    }
  }
}
