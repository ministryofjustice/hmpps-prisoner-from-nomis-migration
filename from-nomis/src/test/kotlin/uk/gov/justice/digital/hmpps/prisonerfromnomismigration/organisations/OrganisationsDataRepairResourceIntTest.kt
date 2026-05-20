package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporateAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiMockServer.Companion.migrateOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigratedOrganisationAddress
import java.time.LocalDate
import java.time.LocalDateTime

class OrganisationsDataRepairResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: OrganisationsNomisApiMockServer

  private val dpsApiMock = dpsOrganisationsServer

  @Autowired
  private lateinit var mappingApiMock: OrganisationsMappingApiMockServer

  @DisplayName("POST /organisation/{organisationId}/resynchronise")
  @Nested
  inner class RepairOrganisation {
    val organisationId = 12345

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/organisation/$organisationId/resynchronise")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/organisation/$organisationId/resynchronise")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/organisation/$organisationId/resynchronise")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      val organisationId = 12345L

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetCorporateOrganisation(
          corporateId = organisationId,
          corporate = corporateOrganisation().withAddress(
            CorporateAddress(
              id = 1,
              phoneNumbers = emptyList(),
              validatedPAF = false,
              primaryAddress = true,
              mailAddress = true,
              noFixedAddress = false,
              type = CodeDescription("HOME", "Home Address"),
              startDate = LocalDate.parse("2021-01-01"),
              audit = NomisAudit(
                createUsername = "J.SPEAK",
                createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
              ),
              isServices = true,
            ),
          ),
        )
        dpsApiMock.stubMigrateOrganisation(
          response = migrateOrganisationResponse().copy(
            addresses = listOf(
              MigratedOrganisationAddress(
                address = IdPair(elementType = IdPair.ElementType.ADDRESS, nomisId = 1, dpsId = 1),
                phoneNumbers = emptyList(),
              ),
            ),
          ),
        )
        mappingApiMock.stubCreateCorporateMapping()
        mappingApiMock.stubCreateAddressMapping()

        webTestClient.post().uri("/organisation/$organisationId/resynchronise")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will retrieve organisation from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/corporates/$organisationId")))
      }

      @Test
      fun `will migration organisation into DPS`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/migrate/organisation")))
      }

      @Test
      fun `will add mapping for organisation and address`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/organisation")),
        )
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/address")),
        )
      }

      @Test
      fun `will track telemetry for the resynchronise`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-organisation-resynchronisation-repair"),
          check {
            assertThat(it["organisationId"]).isEqualTo(organisationId.toString())
          },
          isNull(),
        )
      }
    }
  }
}
