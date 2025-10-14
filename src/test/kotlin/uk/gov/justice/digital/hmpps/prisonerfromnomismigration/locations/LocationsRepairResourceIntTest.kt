package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension.Companion.locationsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.LOCATIONS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi

private const val DPS_LOCATION_ID = "abcdef12-1234-1234-1234-1234567890ab"
private const val NOMIS_LOCATION_ID = 12345L
private const val DPS_PARENT_LOCATION_ID = "fedcba98-1234-1234-1234-1234567890ab"
private const val NOMIS_PARENT_LOCATION_ID = 45678L

class LocationsRepairResourceIntTest : SqsIntegrationTestBase() {

  @DisplayName("POST /locations/id/$NOMIS_LOCATION_ID/repair")
  @Nested
  inner class RepairLocations {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/locations/id/$NOMIS_LOCATION_ID/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/locations/id/$NOMIS_LOCATION_ID/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/locations/id/$NOMIS_LOCATION_ID/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetLocation(NOMIS_LOCATION_ID, NOMIS_PARENT_LOCATION_ID)
        mappingApi.stubGetLocation(DPS_LOCATION_ID, NOMIS_LOCATION_ID)
        mappingApi.stubGetLocation(DPS_PARENT_LOCATION_ID, NOMIS_PARENT_LOCATION_ID)
        locationsApi.stubUpsertLocationForSynchronisation(DPS_LOCATION_ID)
        mappingApi.stubMappingCreate(LOCATIONS_CREATE_MAPPING_URL)

        webTestClient.post().uri("/locations/id/$NOMIS_LOCATION_ID/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will callback back to Nomis service to get more details`() {
        nomisApi.verify(getRequestedFor(urlEqualTo("/locations/$NOMIS_LOCATION_ID")))
      }

      @Test
      fun `will create correct telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("locations-updated-synchronisation-success"),
          check {
            assertThat(it["dpsLocationId"]).isEqualTo(DPS_LOCATION_ID)
            assertThat(it["nomisLocationId"]).isEqualTo(NOMIS_LOCATION_ID.toString())
          },
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("locations-repair"),
          check {
            assertThat(it["nomisLocationId"]).isEqualTo(NOMIS_LOCATION_ID.toString())
            assertThat(it["recordDeleted"]).isEqualTo("false")
          },
          isNull(),
        )
      }

      @Test
      fun `will call DPS api to update the location`() {
        locationsApi.verify(postRequestedFor(urlEqualTo("/sync/upsert")))
      }
    }
  }
}
