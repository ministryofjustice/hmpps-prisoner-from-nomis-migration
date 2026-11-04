package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyNomisApiMockServer.Companion.agencyResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyRegistersDpsApiExtension.Companion.agencyRegistersApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyDto

class AgencyRegistersRepairResourceIntTest(
  @Autowired private val nomisApiMock: AgencyNomisApiMockServer,
) : AgencyRegistersIntegrationTestBase() {

  private val dpsApiMock = agencyRegistersApi

  @DisplayName("PUT /agency/{agencyId}/repair")
  @Nested
  inner class ResynchroniseAgency {
    val agencyId = "SHEFCC"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/agency/$agencyId/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/agency/$agencyId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/agency/$agencyId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetAgency(
          agencyId = agencyId,
          response = agencyResponse().copy(agencyId = agencyId),
        )
        dpsApiMock.stubSyncAgency(agencyId = agencyId)
        webTestClient.put().uri("/agency/$agencyId/repair")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("agency-synchronisation-updated-success"),
          check {
            assertThat(it["agencyId"]).isEqualTo(agencyId)
          },
          isNull(),
        )
      }

      @Test
      fun `will retrieve agency details from nomis`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/agency/$agencyId")))
      }

      @Test
      fun `will sync the agency into DPS`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/legacy/sync/agency/id/$agencyId")))
      }

      @Test
      fun `will transform the agency data correctly`() {
        val request: LegacyAgencyDto =
          AgencyRegistersDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/legacy/sync/agency/id/$agencyId")))
        assertThat(request.name).isEqualTo("Sheffield Crown Court")
      }
    }
  }
}
