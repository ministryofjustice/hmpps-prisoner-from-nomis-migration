package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension.Companion.locationsApi

private const val LOCATION_ID = "abcde123-1234-1234-1234-1234567890ab"

@SpringAPIServiceTest
@Import(LocationsService::class, LocationsConfiguration::class)
internal class LocationsServiceTest {

  @Autowired
  private lateinit var locationsService: LocationsService

  @Nested
  @DisplayName("POST /sync/upsert")
  inner class CreateLocationForSynchronisation {
    @BeforeEach
    internal fun setUp() {
      locationsApi.stubUpsertLocationForSynchronisation(locationId = LOCATION_ID)
      runBlocking {
        locationsService.upsertLocation(
          NomisSyncLocationRequest(
            code = "C",
            localName = "Wing C",
            locationType = NomisSyncLocationRequest.LocationType.WING,
            comments = "Test comment",
            prisonId = "LEI",
            lastUpdatedBy = "TJONES_ADM",
            isDeactivated = false,
          ),
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      locationsApi.verify(
        postRequestedFor(urlEqualTo("/sync/upsert"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass data to the api`() {
      locationsApi.verify(
        postRequestedFor(urlEqualTo("/sync/upsert"))
          .withRequestBody(matchingJsonPath("locationType", equalTo("WING")))
          .withRequestBody(matchingJsonPath("code", equalTo("C")))
          .withRequestBody(matchingJsonPath("localName", equalTo("Wing C")))
          .withRequestBody(matchingJsonPath("prisonId", equalTo("LEI")))
          .withRequestBody(matchingJsonPath("comments", equalTo("Test comment")))
          .withRequestBody(matchingJsonPath("lastUpdatedBy", equalTo("TJONES_ADM"))),
      )
    }
  }
}
