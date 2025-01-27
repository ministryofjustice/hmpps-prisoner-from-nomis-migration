package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.LOCATIONS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

private const val DPS_LOCATION_ID = "abcdef12-1234-5678-9012-abcdef1234567"
private const val NOMIS_LOCATION_ID = 1234L

@SpringAPIServiceTest
@Import(LocationsMappingService::class, LocationsConfiguration::class)
internal class LocationsMappingServiceTest {
  @Autowired
  private lateinit var locationsMappingService: LocationsMappingService

  @Nested
  @DisplayName("findLocationMapping")
  inner class FindLocationMapping {

    @Test
    internal fun `will return null when not found`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/locations/nomis/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(
        runBlocking {
          locationsMappingService.getMappingGivenNomisId(NOMIS_LOCATION_ID)
        },
      ).isNull()
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/locations/nomis/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
              {
                  "dpsLocationId": "$DPS_LOCATION_ID",
                  "nomisLocationId": $NOMIS_LOCATION_ID,
                  "label": "5678",
                  "mappingType": "MIGRATED",
                  "whenCreated": "2020-01-01T00:00:00"
              }
              """.trimIndent(),
            ),
        ),
      )

      val mapping = locationsMappingService.getMappingGivenNomisId(NOMIS_LOCATION_ID)
      assertThat(mapping).isNotNull
      assertThat(mapping!!.dpsLocationId).isEqualTo(DPS_LOCATION_ID)
      assertThat(mapping.nomisLocationId).isEqualTo(NOMIS_LOCATION_ID)
      assertThat(mapping.label).isEqualTo("5678")
      assertThat(mapping.mappingType).isEqualTo(MIGRATED)
      assertThat(mapping.whenCreated).isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/locations/nomis/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          locationsMappingService.getMappingGivenNomisId(NOMIS_LOCATION_ID)
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("createLocationMapping")
  inner class CreateLocationMapping {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubFor(
        post(urlEqualTo(LOCATIONS_CREATE_MAPPING_URL)).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value()),
        ),
      )
    }

    @Test
    fun `should provide oath2 token`() {
      mappingApi.stubMappingCreate(LOCATIONS_CREATE_MAPPING_URL)

      runBlocking {
        locationsMappingService.createMapping(
          LocationMappingDto(
            dpsLocationId = DPS_LOCATION_ID,
            nomisLocationId = NOMIS_LOCATION_ID,
            label = "some-migration-id",
            mappingType = MIGRATED,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<LocationMappingDto>>() {},
        )
      }

      mappingApi.verify(
        postRequestedFor(
          urlPathEqualTo("/mapping/locations"),
        ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass all parameters visit id, migration Id and MIGRATED indicator to mapping service`(): Unit = runBlocking {
      locationsMappingService.createMapping(
        LocationMappingDto(
          dpsLocationId = DPS_LOCATION_ID,
          nomisLocationId = NOMIS_LOCATION_ID,
          mappingType = MIGRATED,
          label = "5678",
          whenCreated = "2020-01-01T00:00:00",
        ),
        errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<LocationMappingDto>>() {},
      )

      mappingApi.verify(
        postRequestedFor(urlEqualTo(LOCATIONS_CREATE_MAPPING_URL))
          .withRequestBody(
            equalToJson(
              """
                  {
                  "dpsLocationId": "$DPS_LOCATION_ID",
                  "nomisLocationId": $NOMIS_LOCATION_ID,
                  "label": "5678",
                  "mappingType": "MIGRATED",
                  "whenCreated": "2020-01-01T00:00:00"
                  }
              """.trimIndent(),
            ),
          ),
      )
    }

    @Test
    fun `should throw exception for any error`() {
      mappingApi.stubFor(
        post(urlPathMatching(LOCATIONS_CREATE_MAPPING_URL)).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          locationsMappingService.createMapping(
            LocationMappingDto(
              dpsLocationId = DPS_LOCATION_ID,
              nomisLocationId = NOMIS_LOCATION_ID,
              mappingType = MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
            object : ParameterizedTypeReference<DuplicateErrorResponse<LocationMappingDto>>() {},
          )
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("findLatestMigration")
  inner class FindLatestMigration {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubLocationsLatestMigration("2020-01-01T10:00:00")
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      locationsMappingService.findLatestMigration()

      mappingApi.verify(
        getRequestedFor(
          urlPathEqualTo("/mapping/locations/migrated/latest"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return null when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathEqualTo("/mapping/locations/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(locationsMappingService.findLatestMigration()).isNull()
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/locations/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
                {
                  "dpsLocationId": "$DPS_LOCATION_ID",
                  "nomisLocationId": $NOMIS_LOCATION_ID,
                  "label": "2022-02-16T14:20:15",
                  "mappingType": "MIGRATED",
                  "whenCreated": "2022-02-16T16:21:15.589091"
                }              
              """,
            ),
        ),
      )

      val mapping = locationsMappingService.findLatestMigration()
      assertThat(mapping).isNotNull
      assertThat(mapping?.migrationId).isEqualTo("2022-02-16T14:20:15")
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/locations/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          locationsMappingService.findLatestMigration()
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("getMigrationDetails")
  inner class GetMigrationDetails {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubLocationsMappingByMigrationId("2024-01-01T11:10:00")
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      locationsMappingService.getMigrationDetails("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/locations/migration-id/.*"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will throw error when not found`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/locations/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          locationsMappingService.getMigrationDetails("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.NotFound::class.java)
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubLocationsMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 56_766,
      )

      val mapping = locationsMappingService.getMigrationDetails("2020-01-01T10:00:00")
      assertThat(mapping).isNotNull
      assertThat(mapping.startedDateTime).isEqualTo("2020-01-01T11:10:00")
      assertThat(mapping.count).isEqualTo(56766)
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/locations/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          locationsMappingService.getMigrationDetails("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("getMigrationCount")
  inner class GetMigrationCount {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubLocationsMappingByMigrationId(count = 56_766)
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      locationsMappingService.getMigrationCount("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/locations/migration-id/.*"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return zero when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/locations/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(locationsMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(0)
    }

    @Test
    internal fun `will return the mapping count when found`(): Unit = runBlocking {
      mappingApi.stubLocationsMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 54_766,
      )

      assertThat(locationsMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(54_766)
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/locations/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          locationsMappingService.getMigrationCount("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }
}
