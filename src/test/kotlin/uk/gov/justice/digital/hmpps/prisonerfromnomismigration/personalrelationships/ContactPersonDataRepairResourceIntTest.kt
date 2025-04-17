package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase

class ContactPersonDataRepairResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: ContactPersonNomisApiMockServer

  private val dpsApiMock = ContactPersonDpsApiExtension.dpsContactPersonServer

  @Autowired
  private lateinit var mappingApiMock: ContactPersonMappingApiMockServer

  @DisplayName("POST /person/{personId}/resynchronise")
  @Nested
  inner class RepairPerson {
    val personId = 123456L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/persons/$personId/resynchronise")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/persons/$personId/resynchronise")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/persons/$personId/resynchronise")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      val personId = 123456L

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetPerson(personId)
        dpsApiMock.stubMigrateContact(personId)
        mappingApiMock.stubReplaceMappingsForPerson(personId)

        webTestClient.post().uri("/persons/$personId/resynchronise")
          .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will retrieve person`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$personId")))
      }

      @Test
      fun `will send person contact to DPS`() {
        dpsApiMock.verify(
          postRequestedFor(urlPathEqualTo("/migrate/contact")),
        )
      }

      @Test
      fun `will replaces mapping between the DPS and NOMIS alerts`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/person/$personId")),
        )
      }

      @Test
      fun `will track telemetry for the resynchronise`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-contactperson-resynchronisation-repair"),
          check {
            assertThat(it["personId"]).isEqualTo("$personId")
          },
          isNull(),
        )
      }
    }
  }
}
