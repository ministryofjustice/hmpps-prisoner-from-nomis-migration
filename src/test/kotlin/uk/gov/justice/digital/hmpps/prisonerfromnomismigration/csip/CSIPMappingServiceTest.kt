package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.CSIP_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

private const val DPS_CSIP_ID = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5"
private const val NOMIS_CSIP_ID = 1234L

@SpringAPIServiceTest
@Import(CSIPMappingService::class, CSIPConfiguration::class)
internal class CSIPMappingServiceTest {
  @Autowired
  private lateinit var csipMappingService: CSIPMappingService

  @Nested
  @DisplayName("findCSIPMapping")
  inner class FindCSIPMapping {

    @Test
    internal fun `will return null when not found`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/csip/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(
        runBlocking {
          csipMappingService.findNomisCSIPMapping(
            nomisCSIPId = NOMIS_CSIP_ID,
          )
        },
      ).isNull()
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/csip/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
              {
                  "dpsCSIPId": "$DPS_CSIP_ID",
                  "nomisCSIPId": $NOMIS_CSIP_ID,                                       
                  "label": "5678",
                  "mappingType": "MIGRATED",
                  "whenCreated": "2020-01-01T00:00:00"
              }
              """.trimIndent(),
            ),
        ),
      )

      val mapping = csipMappingService.findNomisCSIPMapping(
        nomisCSIPId = NOMIS_CSIP_ID,
      )
      assertThat(mapping).isNotNull
      assertThat(mapping!!.dpsCSIPId).isEqualTo(DPS_CSIP_ID)
      assertThat(mapping.nomisCSIPId).isEqualTo(NOMIS_CSIP_ID)
      assertThat(mapping.label).isEqualTo("5678")
      assertThat(mapping.mappingType).isEqualTo(MIGRATED)
      assertThat(mapping.whenCreated).isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/csip/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          csipMappingService.findNomisCSIPMapping(
            nomisCSIPId = NOMIS_CSIP_ID,
          )
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("createCSIPMapping")
  inner class CreateCSIPMapping {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubFor(
        post(urlEqualTo(CSIP_CREATE_MAPPING_URL)).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value()),
        ),
      )
    }

    @Test
    fun `should provide oath2 token`() {
      mappingApi.stubMappingCreate(CSIP_CREATE_MAPPING_URL)

      runBlocking {
        csipMappingService.createMapping(
          CSIPMappingDto(
            dpsCSIPId = DPS_CSIP_ID,
            nomisCSIPId = NOMIS_CSIP_ID,
            label = "some-migration-id",
            mappingType = MIGRATED,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPMappingDto>>() {},
        )
      }

      mappingApi.verify(
        postRequestedFor(
          urlPathEqualTo("/mapping/csip"),
        ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass all parameters dps csip id, nomis csip id, migration Id and MIGRATED indicator to mapping service`(): Unit =
      runBlocking {
        csipMappingService.createMapping(
          CSIPMappingDto(
            dpsCSIPId = DPS_CSIP_ID,
            nomisCSIPId = NOMIS_CSIP_ID,
            mappingType = MIGRATED,
            label = "5678",
            whenCreated = "2020-01-01T00:00:00",
          ),
          errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPMappingDto>>() {},
        )

        mappingApi.verify(
          postRequestedFor(urlEqualTo(CSIP_CREATE_MAPPING_URL))
            .withRequestBody(
              equalToJson(
                """
                  {
                  "dpsCSIPId": "$DPS_CSIP_ID",
                  "nomisCSIPId": $NOMIS_CSIP_ID,                                       
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
        post(urlPathMatching(CSIP_CREATE_MAPPING_URL)).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          csipMappingService.createMapping(
            CSIPMappingDto(
              dpsCSIPId = DPS_CSIP_ID,
              nomisCSIPId = NOMIS_CSIP_ID,
              mappingType = MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
            object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPMappingDto>>() {},
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
      mappingApi.stubCSIPLatestMigration("2020-01-01T10:00:00")
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      csipMappingService.findLatestMigration()

      mappingApi.verify(
        getRequestedFor(
          urlPathEqualTo("/mapping/csip/migrated/latest"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return null when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathEqualTo("/mapping/csip/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(csipMappingService.findLatestMigration()).isNull()
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/csip/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
                {
                  "dpsCSIPId": "$DPS_CSIP_ID",
                  "nomisCSIPId": $NOMIS_CSIP_ID,                                                         
                  "label": "2022-02-16T14:20:15",
                  "mappingType": "MIGRATED",
                  "whenCreated": "2022-02-16T16:21:15.589091"
                }              
              """,
            ),
        ),
      )

      val mapping = csipMappingService.findLatestMigration()
      assertThat(mapping).isNotNull
      assertThat(mapping?.migrationId).isEqualTo("2022-02-16T14:20:15")
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/csip/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          csipMappingService.findLatestMigration()
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("getMigrationDetails")
  inner class GetMigrationDetails {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubCSIPMappingByMigrationId("2020-01-01T11:10:00")
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      csipMappingService.getMigrationDetails("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/csip/migration-id/.*"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will throw error when not found`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/csip/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          csipMappingService.getMigrationDetails("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.NotFound::class.java)
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubCSIPMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 56_766,
      )

      val mapping = csipMappingService.getMigrationDetails("2020-01-01T10:00:00")
      assertThat(mapping).isNotNull
      assertThat(mapping.startedDateTime).isEqualTo("2020-01-01T11:10:00")
      assertThat(mapping.count).isEqualTo(56766)
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/csip/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          csipMappingService.getMigrationDetails("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("getMigrationCount")
  inner class GetMigrationCount {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubCSIPMappingByMigrationId(count = 56_766)
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      csipMappingService.getMigrationCount("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/csip/migration-id/.*"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return zero when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/csip/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(csipMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(0)
    }

    @Test
    internal fun `will return the mapping count when found`(): Unit = runBlocking {
      mappingApi.stubCSIPMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 54_766,
      )

      assertThat(csipMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(54_766)
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/csip/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          csipMappingService.getMigrationCount("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }
}
