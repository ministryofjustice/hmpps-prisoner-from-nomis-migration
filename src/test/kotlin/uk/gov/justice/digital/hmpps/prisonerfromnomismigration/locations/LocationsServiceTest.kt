package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.ChangeHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.MigrateHistoryRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.UpsertLocationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension.Companion.locationsApi
import java.util.*

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
          UpsertLocationRequest(
            code = "C",
            localName = "Wing C",
            locationType = UpsertLocationRequest.LocationType.WING,
            comments = "Test comment",
            prisonId = "LEI",
            lastUpdatedBy = "TJONES_ADM",
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

  @Nested
  @DisplayName("POST /migrate/location")
  inner class CreateLocationForMigration {
    @BeforeEach
    internal fun setUp() {
      locationsApi.stubUpsertLocationForMigration(locationId = LOCATION_ID)
      runBlocking {
        locationsService.migrateLocation(
          UpsertLocationRequest(
            id = UUID.fromString(LOCATION_ID),
            code = "C",
            localName = "Wing C",
            locationType = UpsertLocationRequest.LocationType.WING,
            comments = "Test comment",
            prisonId = "LEI",
            lastUpdatedBy = "TJONES_ADM",
            parentId = UUID.fromString("12345678-1234-1234-1234-1234567890ab"),
          ),
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      locationsApi.verify(
        postRequestedFor(urlEqualTo("/migrate/location"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass data to the api`() {
      locationsApi.verify(
        postRequestedFor(urlEqualTo("/migrate/location"))
          .withRequestBody(matchingJsonPath("id", equalTo(LOCATION_ID)))
          .withRequestBody(matchingJsonPath("locationType", equalTo("WING")))
          .withRequestBody(matchingJsonPath("code", equalTo("C")))
          .withRequestBody(matchingJsonPath("localName", equalTo("Wing C")))
          .withRequestBody(matchingJsonPath("prisonId", equalTo("LEI")))
          .withRequestBody(matchingJsonPath("comments", equalTo("Test comment")))
          .withRequestBody(matchingJsonPath("parentId", equalTo("12345678-1234-1234-1234-1234567890ab"))),
      )
    }
  }

  @Nested
  @DisplayName("POST /migrate/location/{locationId}/history")
  inner class CreateLocationHistoryForMigration {
    @Test
    fun `should call api with OAuth2 token`() {
      locationsApi.stubUpsertLocationHistoryForMigration(LOCATION_ID, aLocationHistory())
      runBlocking {
        locationsService.migrateLocationHistory(
          UUID.fromString(LOCATION_ID),
          MigrateHistoryRequest(
            attribute = MigrateHistoryRequest.Attribute.ORDER_WITHIN_PARENT_LOCATION,
            amendedBy = "TJONES_ADM",
            amendedDate = "2022-12-02T10:00:00",
            oldValue = "1",
            newValue = "2",
          ),
        )
      }

      locationsApi.verify(
        postRequestedFor(urlEqualTo("/migrate/location/$LOCATION_ID/history"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass data to the api`() {
      locationsApi.stubUpsertLocationHistoryForMigration(LOCATION_ID, aLocationHistory())
      runBlocking {
        locationsService.migrateLocationHistory(
          UUID.fromString(LOCATION_ID),
          MigrateHistoryRequest(
            attribute = MigrateHistoryRequest.Attribute.ORDER_WITHIN_PARENT_LOCATION,
            amendedBy = "TJONES_ADM",
            amendedDate = "2022-12-02T10:00:00",
            oldValue = "1",
            newValue = "2",
          ),
        )
      }

      locationsApi.verify(
        postRequestedFor(urlEqualTo("/migrate/location/$LOCATION_ID/history"))
          .withRequestBody(matchingJsonPath("attribute", equalTo("ORDER_WITHIN_PARENT_LOCATION")))
          .withRequestBody(matchingJsonPath("amendedBy", equalTo("TJONES_ADM")))
          .withRequestBody(matchingJsonPath("amendedDate", equalTo("2022-12-02T10:00:00")))
          .withRequestBody(matchingJsonPath("oldValue", equalTo("1")))
          .withRequestBody(matchingJsonPath("newValue", equalTo("2"))),
      )
    }

    @Test
    fun `no data returned`() {
      locationsApi.stubUpsertLocationHistoryForMigration(LOCATION_ID, "")
      runBlocking {
        locationsService.migrateLocationHistory(
          UUID.fromString(LOCATION_ID),
          MigrateHistoryRequest(
            attribute = MigrateHistoryRequest.Attribute.ORDER_WITHIN_PARENT_LOCATION,
            amendedBy = "TJONES_ADM",
            amendedDate = "2022-12-02T10:00:00",
            oldValue = "1",
            newValue = "2",
          ),
        )
      }

      locationsApi.verify(
        postRequestedFor(urlEqualTo("/migrate/location/$LOCATION_ID/history"))
          .withRequestBody(matchingJsonPath("attribute", equalTo("ORDER_WITHIN_PARENT_LOCATION")))
          .withRequestBody(matchingJsonPath("amendedBy", equalTo("TJONES_ADM")))
          .withRequestBody(matchingJsonPath("amendedDate", equalTo("2022-12-02T10:00:00")))
          .withRequestBody(matchingJsonPath("oldValue", equalTo("1")))
          .withRequestBody(matchingJsonPath("newValue", equalTo("2"))),
      )
    }
  }

  private fun aLocationHistory() = ChangeHistory(
    attribute = "Location Type",
    oldValue = "CELL",
    newValue = "WING",
    amendedBy = "user",
    amendedDate = "2021-07-05T10:35:17",
  ).toJson()

  private fun Any.toJson(): String = ObjectMapper().writeValueAsString(this)
}
