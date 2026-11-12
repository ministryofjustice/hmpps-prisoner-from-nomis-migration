package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyPrisonPhoneDto

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

  @DisplayName("PUT /prison/{agencyId}/repair/phone-number")
  @Nested
  inner class ResynchronisePrisonPhoneNumber {
    val agencyId = "MDI"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/prison/$agencyId/repair/phone-number")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/prison/$agencyId/repair/phone-number")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/prison/$agencyId/repair/phone-number")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetAgencyPhoneNumber(agencyId = agencyId)
        dpsApiMock.stubUpdatePrisonPhoneNumber(agencyId = agencyId)
        webTestClient.put().uri("/prison/$agencyId/repair/phone-number")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("agency-synchronisation-phone-number-updated-success"),
          check {
            assertThat(it["agencyId"]).isEqualTo(agencyId)
            assertThat(it["reason"]).isEqualTo("repair")
          },
          isNull(),
        )
      }

      @Test
      fun `will retrieve phone number from nomis`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/agency/$agencyId/phone-number")))
      }

      @Test
      fun `will update the phone number in DPS`() {
        val pattern = putRequestedFor(urlPathEqualTo("/legacy/sync/prison/id/$agencyId/phone-number"))
        dpsApiMock.verify(pattern)
        val request: LegacyPrisonPhoneDto = AgencyRegistersDpsApiExtension.getRequestBody(pattern)
        assertThat(request.phoneNumber.number).isEqualTo("0114 555 9898")
      }
    }

    @Nested
    inner class NoPhoneNumberInNomis {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetAgencyPhoneNumberNoPhone(agencyId = agencyId)
        webTestClient.put().uri("/prison/$agencyId/repair/phone-number")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will not update DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathEqualTo("/legacy/sync/prison/id/$agencyId/phone-number")))
      }

      @Test
      fun `will track skipped telemetry`() {
        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("agency-synchronisation-phone-number-updated-skipped"),
          check {
            assertThat(it["agencyId"]).isEqualTo(agencyId)
          },
          isNull(),
        )
      }
    }
  }
}
