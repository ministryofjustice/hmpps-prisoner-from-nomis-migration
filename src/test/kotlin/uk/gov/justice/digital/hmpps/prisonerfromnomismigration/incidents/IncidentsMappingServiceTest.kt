package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsMappingApiMockServer.Companion.INCIDENTS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IncidentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IncidentMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

private const val DPS_INCIDENT_ID = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42"
private const val NOMIS_INCIDENT_ID = 1234L

@SpringAPIServiceTest
@Import(IncidentsMappingService::class, IncidentsConfiguration::class, IncidentsMappingApiMockServer::class)
internal class IncidentsMappingServiceTest {
  @Autowired
  private lateinit var incidentsMappingService: IncidentsMappingService

  @Autowired
  private lateinit var incidentsMappingApi: IncidentsMappingApiMockServer

  @Nested
  @DisplayName("findIncidentMapping")
  inner class FindIncidentMapping {

    @Test
    internal fun `will return null when not found`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/incidents/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(
        runBlocking {
          incidentsMappingService.findByNomisId(
            nomisIncidentId = NOMIS_INCIDENT_ID,
          )
        },
      ).isNull()
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/incidents/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
              {
                  "dpsIncidentId": "$DPS_INCIDENT_ID",
                  "nomisIncidentId": $NOMIS_INCIDENT_ID,                                       
                  "label": "5678",
                  "mappingType": "MIGRATED",
                  "whenCreated": "2020-01-01T00:00:00"
              }
              """.trimIndent(),
            ),
        ),
      )

      val mapping = incidentsMappingService.findByNomisId(
        nomisIncidentId = NOMIS_INCIDENT_ID,
      )
      assertThat(mapping).isNotNull
      assertThat(mapping!!.dpsIncidentId).isEqualTo(DPS_INCIDENT_ID)
      assertThat(mapping.nomisIncidentId).isEqualTo(NOMIS_INCIDENT_ID)
      assertThat(mapping.label).isEqualTo("5678")
      assertThat(mapping.mappingType).isEqualTo(MIGRATED)
      assertThat(mapping.whenCreated).isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/incidents/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          incidentsMappingService.findByNomisId(
            nomisIncidentId = NOMIS_INCIDENT_ID,
          )
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("createIncidentMapping")
  inner class CreateIncidentMapping {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubFor(
        post(urlEqualTo(INCIDENTS_CREATE_MAPPING_URL)).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value()),
        ),
      )
    }

    @Test
    fun `should provide oath2 token`() {
      mappingApi.stubMappingCreate(INCIDENTS_CREATE_MAPPING_URL)

      runBlocking {
        incidentsMappingService.createMapping(
          IncidentMappingDto(
            dpsIncidentId = DPS_INCIDENT_ID,
            nomisIncidentId = NOMIS_INCIDENT_ID,
            label = "some-migration-id",
            mappingType = MIGRATED,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<IncidentMappingDto>>() {},
        )
      }

      mappingApi.verify(
        postRequestedFor(
          urlPathEqualTo("/mapping/incidents"),
        ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass all parameters incident id, migration Id and MIGRATED indicator to mapping service`(): Unit = runBlocking {
      incidentsMappingService.createMapping(
        IncidentMappingDto(
          dpsIncidentId = DPS_INCIDENT_ID,
          nomisIncidentId = NOMIS_INCIDENT_ID,
          mappingType = MIGRATED,
          label = "5678",
          whenCreated = "2020-01-01T00:00:00",
        ),
        errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<IncidentMappingDto>>() {},
      )

      mappingApi.verify(
        postRequestedFor(urlEqualTo(INCIDENTS_CREATE_MAPPING_URL))
          .withRequestBody(
            equalToJson(
              """
                  {
                  "dpsIncidentId": "$DPS_INCIDENT_ID",
                  "nomisIncidentId": $NOMIS_INCIDENT_ID,                                       
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
        post(urlPathMatching(INCIDENTS_CREATE_MAPPING_URL)).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          incidentsMappingService.createMapping(
            IncidentMappingDto(
              dpsIncidentId = DPS_INCIDENT_ID,
              nomisIncidentId = NOMIS_INCIDENT_ID,
              mappingType = MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
            object : ParameterizedTypeReference<DuplicateErrorResponse<IncidentMappingDto>>() {},
          )
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  inner class DeleteIncidentMapping {
    @Test
    internal fun `will pass oath2 token to service`() {
      incidentsMappingApi.stubIncidentMappingDelete(DPS_INCIDENT_ID)

      runBlocking {
        incidentsMappingService.deleteIncidentMapping(
          dpsIncidentId = DPS_INCIDENT_ID,
        )
      }

      mappingApi.verify(
        WireMock.deleteRequestedFor(
          urlPathEqualTo("/mapping/incidents/dps-incident-id/$DPS_INCIDENT_ID"),
        ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }
  }

  @Nested
  @DisplayName("getMigrationCount")
  inner class GetMigrationCount {
    @BeforeEach
    internal fun setUp() {
      incidentsMappingApi.stubIncidentsMappingByMigrationId(count = 56_766)
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      incidentsMappingService.getMigrationCount("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/incidents/migration-id/.*"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return zero when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/incidents/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(incidentsMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(0)
    }

    @Test
    internal fun `will return the mapping count when found`(): Unit = runBlocking {
      incidentsMappingApi.stubIncidentsMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 54_766,
      )

      assertThat(incidentsMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(54_766)
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/incidents/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          incidentsMappingService.getMigrationCount("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }
}
