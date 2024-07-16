package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsMigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension.Companion.dpsPrisonPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration

class PrisonPersonMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var prisonPersonNomisApi: PrisonPersonNomisApiMockServer

  @Nested
  inner class MigratePhysicalAttributes {
    private fun stubMigrationDependencies(entities: Int = 2) {
      nomisApi.stubGetPrisonIds(totalElements = entities.toLong(), pageSize = 10, offenderNo = "A0001KT")
      (1L..entities).forEach {
        prisonPersonNomisApi.stubGetPhysicalAttributes("A000${it}KT")
        dpsPrisonPersonServer.stubMigratePhysicalAttributes(PhysicalAttributesMigrationResponse(listOf(it)))
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/prisonperson/physical-attributes")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(AlertsMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/prisonperson/physical-attributes")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(AlertsMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/prisonperson/physical-attributes")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(AlertsMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        stubMigrationDependencies(entities = 2)
        webTestClient.performMigration()
      }

      @Test
      fun `will migrate physical attributes`() {
        dpsPrisonPersonServer.verify(
          putRequestedFor(urlMatching("/migration/prisoners/A0001KT/physical-attributes"))
            .withRequestBodyJsonPath("$[0].height", 180)
            .withRequestBodyJsonPath("$[0].weight", 80)
            .withRequestBodyJsonPath("$[0].appliesFrom", "2024-02-03T12:34:56Z[Europe/London]")
            .withRequestBodyJsonPath("$[0].appliesTo", "2024-10-21T12:34:56+01:00[Europe/London]"),
        )
        dpsPrisonPersonServer.verify(
          putRequestedFor(urlMatching("/migration/prisoners/A0002KT/physical-attributes")),
        )
      }
    }

    private fun WebTestClient.performMigration() =
      post().uri("/migrate/prisonperson/physical-attributes")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted
        .also {
          waitUntilCompleted()
        }

    private fun waitUntilCompleted() =
      await atMost Duration.ofSeconds(60) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-completed"),
          any(),
          isNull(),
        )
      }
  }
}
