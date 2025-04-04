package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingApiMockServer.Companion.CSIP_CREATE_CHILD_MAPPINGS_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingApiMockServer.Companion.CSIP_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPChildMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFullMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFullMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPReportMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

private const val DPS_CSIP_ID = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5"
private const val NOMIS_CSIP_ID = 1234L

@SpringAPIServiceTest
@Import(CSIPMappingService::class, CSIPConfiguration::class, CSIPMappingApiMockServer::class)
internal class CSIPMappingServiceTest {
  @Autowired
  private lateinit var csipMappingService: CSIPMappingService

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  @DisplayName("CSIPReport")
  inner class CSIPReport {
    @Nested
    @DisplayName("FindCSIPReportMapping")
    inner class FindCSIPReportMapping {

      @Test
      internal fun `will return null when not found`() = runTest {
        csipMappingApi.stubGetByNomisId(HttpStatus.NOT_FOUND)

        assertThat(csipMappingService.getCSIPReportByNomisId(nomisCSIPReportId = NOMIS_CSIP_ID)).isNull()
      }

      @Test
      internal fun `will return the mapping when found`(): Unit = runTest {
        csipMappingApi.stubGetByNomisId()

        val mapping = csipMappingService.getCSIPReportByNomisId(
          nomisCSIPReportId = NOMIS_CSIP_ID,
        )
        assertThat(mapping).isNotNull
        assertThat(mapping!!.dpsCSIPReportId).isEqualTo(DPS_CSIP_ID)
        assertThat(mapping.nomisCSIPReportId).isEqualTo(NOMIS_CSIP_ID)
        assertThat(mapping.label).isEqualTo("2022-02-14T09:58:45")
        assertThat(mapping.mappingType).isEqualTo(CSIPReportMappingDto.MappingType.NOMIS_CREATED)
        assertThat(mapping.whenCreated).isEqualTo("2020-01-01T11:10:00")
      }

      @Test
      internal fun `will throw exception for any other error`() = runTest {
        csipMappingApi.stubGetByNomisId(HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.getCSIPReportByNomisId(nomisCSIPReportId = NOMIS_CSIP_ID)
        }
      }
    }

    @Nested
    @DisplayName("FindCSIPFullReportMapping")
    inner class FindCSIPFullReportMapping {
      private val dpsCsipReportId = UUID.randomUUID().toString()

      @Test
      internal fun `will return null when not found`() = runTest {
        csipMappingApi.stubGetFullMappingByDpsReportId(HttpStatus.NOT_FOUND)

        assertThat(csipMappingService.getFullMappingByDPSReportId(dpsCSIPReportId = dpsCsipReportId)).isNull()
      }

      @Test
      internal fun `will return the mapping when found`(): Unit = runTest {
        csipMappingApi.stubGetFullMappingByDpsReportId(dpsCSIPId = dpsCsipReportId)

        val mapping = csipMappingService.getFullMappingByDPSReportId(dpsCSIPReportId = dpsCsipReportId)
        assertThat(mapping).isNotNull
        assertThat(mapping!!.dpsCSIPReportId).isEqualTo(dpsCsipReportId)
        assertThat(mapping.nomisCSIPReportId).isEqualTo(NOMIS_CSIP_ID)
        assertThat(mapping.label).isEqualTo("2022-02-14T09:58:45")
        assertThat(mapping.mappingType).isEqualTo(CSIPFullMappingDto.MappingType.NOMIS_CREATED)
        assertThat(mapping.whenCreated).isEqualTo("2020-01-01T11:10:00")
      }

      @Test
      internal fun `will throw exception for any other error`() = runTest {
        csipMappingApi.stubGetFullMappingByDpsReportId(HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.getFullMappingByDPSReportId(dpsCSIPReportId = dpsCsipReportId)
        }
      }
    }

    @Nested
    @DisplayName("createCSIPChildMappings")
    inner class CreateCSIPChildMappings {
      @BeforeEach
      internal fun setUp() {
        mappingApi.stubFor(
          post(urlEqualTo(CSIP_CREATE_CHILD_MAPPINGS_URL)).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.CREATED.value()),
          ),
        )
      }

      @Test
      fun `should provide oath2 token`() = runTest {
        csipMappingApi.stubCreateChildMappings()

        csipMappingService.createChildMappings(
          CSIPFullMappingDto(
            dpsCSIPReportId = DPS_CSIP_ID,
            nomisCSIPReportId = NOMIS_CSIP_ID,
            label = "some-migration-id",
            mappingType = MIGRATED,
            attendeeMappings = listOf(),
            factorMappings = listOf(),
            interviewMappings = listOf(),
            planMappings = listOf(),
            reviewMappings = listOf(),
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFullMappingDto>>() {},
        )

        mappingApi.verify(
          postRequestedFor(
            urlPathEqualTo(CSIP_CREATE_CHILD_MAPPINGS_URL),
          ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass all parameters dps csip id, nomis csip id, migration Id, factors and MIGRATED indicator to mapping service`(): Unit = runTest {
        val dpsCSIPFactorId = "081b743a-1b23-4621-90d5-111f55003c11"
        csipMappingService.createChildMappings(
          CSIPFullMappingDto(
            dpsCSIPReportId = DPS_CSIP_ID,
            nomisCSIPReportId = NOMIS_CSIP_ID,
            mappingType = MIGRATED,
            label = "5678",
            whenCreated = "2020-01-01T00:00:00",
            attendeeMappings = listOf(),
            factorMappings = listOf(
              CSIPChildMappingDto(
                nomisId = 123,
                dpsId = dpsCSIPFactorId,
                dpsCSIPReportId = DPS_CSIP_ID,
                mappingType = CSIPChildMappingDto.MappingType.MIGRATED,
              ),
            ),
            interviewMappings = listOf(),
            planMappings = listOf(),
            reviewMappings = listOf(),
          ),
          errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFullMappingDto>>() {},
        )

        mappingApi.verify(
          postRequestedFor(urlEqualTo(CSIP_CREATE_CHILD_MAPPINGS_URL))
            .withRequestBody(
              equalToJson(
                """
                  {
                    "dpsCSIPReportId": "$DPS_CSIP_ID",
                    "nomisCSIPReportId": $NOMIS_CSIP_ID,                                                                            
                    "label": "5678",
                    "mappingType": "MIGRATED",
                    "whenCreated": "2020-01-01T00:00:00",
                    "attendeeMappings": [],
                    "factorMappings": [
                      {
                        "nomisId" : 123,
                        "dpsId" : "081b743a-1b23-4621-90d5-111f55003c11",
                        "dpsCSIPReportId" : "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5",
                        "mappingType" : "MIGRATED",
                        "label" : null,
                        "whenCreated" : null
                      }
                    ],
                    "interviewMappings": [],
                    "planMappings": [],
                    "reviewMappings": []
                  }
                """.trimIndent(),
              ),
            ),
        )
      }

      @Test
      fun `should throw exception for any error`() = runTest {
        mappingApi.stubFor(
          post(urlPathMatching(CSIP_CREATE_CHILD_MAPPINGS_URL)).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
              .withBody("""{"message":"Tea"}"""),
          ),
        )

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.createChildMappings(
            CSIPFullMappingDto(
              dpsCSIPReportId = DPS_CSIP_ID,
              nomisCSIPReportId = NOMIS_CSIP_ID,
              mappingType = MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
              attendeeMappings = listOf(),
              factorMappings = listOf(),
              interviewMappings = listOf(),
              planMappings = listOf(),
              reviewMappings = listOf(),
            ),
            object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFullMappingDto>>() {},
          )
        }
      }
    }

    @Nested
    @DisplayName("createFullCSIPMapping")
    inner class CreateFullCSIPMapping {
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
      fun `should provide oath2 token`() = runTest {
        mappingApi.stubMappingCreate(CSIP_CREATE_MAPPING_URL)

        csipMappingService.createMapping(
          CSIPFullMappingDto(
            dpsCSIPReportId = DPS_CSIP_ID,
            nomisCSIPReportId = NOMIS_CSIP_ID,
            label = "some-migration-id",
            mappingType = MIGRATED,
            attendeeMappings = listOf(),
            factorMappings = listOf(),
            interviewMappings = listOf(),
            planMappings = listOf(),
            reviewMappings = listOf(),
          ),

          object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFullMappingDto>>() {},
        )

        mappingApi.verify(
          postRequestedFor(
            urlPathEqualTo("/mapping/csip/all"),
          ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass all parameters dps csip id, nomis csip id, migration Id, factors and MIGRATED indicator to mapping service`(): Unit = runTest {
        val dpsCSIPFactorId = "081b743a-1b23-4621-90d5-111f55003c11"
        csipMappingService.createMapping(
          CSIPFullMappingDto(
            dpsCSIPReportId = DPS_CSIP_ID,
            nomisCSIPReportId = NOMIS_CSIP_ID,
            mappingType = MIGRATED,
            label = "5678",
            whenCreated = "2020-01-01T00:00:00",
            attendeeMappings = listOf(),
            factorMappings = listOf(
              CSIPChildMappingDto(
                nomisId = 123,
                dpsId = dpsCSIPFactorId,
                dpsCSIPReportId = DPS_CSIP_ID,
                mappingType = CSIPChildMappingDto.MappingType.MIGRATED,
              ),
            ),
            interviewMappings = listOf(),
            planMappings = listOf(),
            reviewMappings = listOf(),
          ),
          errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFullMappingDto>>() {},
        )

        mappingApi.verify(
          postRequestedFor(urlEqualTo(CSIP_CREATE_MAPPING_URL))
            .withRequestBody(
              equalToJson(
                """
                  {
                    "dpsCSIPReportId": "$DPS_CSIP_ID",
                    "nomisCSIPReportId": $NOMIS_CSIP_ID,                                                                            
                    "label": "5678",
                    "mappingType": "MIGRATED",
                    "whenCreated": "2020-01-01T00:00:00",
                    "attendeeMappings": [],
                    "factorMappings": [
                      {
                        "nomisId" : 123,
                        "dpsId" : "081b743a-1b23-4621-90d5-111f55003c11",
                        "dpsCSIPReportId" : "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5",
                        "mappingType" : "MIGRATED",
                        "label" : null,
                        "whenCreated" : null
                      }
                    ],
                    "interviewMappings": [],
                    "planMappings": [],
                    "reviewMappings": []
                  }
                """.trimIndent(),
              ),
            ),
        )
      }

      @Test
      fun `should throw exception for any error`() = runTest {
        mappingApi.stubFor(
          post(urlPathMatching(CSIP_CREATE_MAPPING_URL)).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
              .withBody("""{"message":"Tea"}"""),
          ),
        )

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.createMapping(
            CSIPFullMappingDto(
              dpsCSIPReportId = DPS_CSIP_ID,
              nomisCSIPReportId = NOMIS_CSIP_ID,
              mappingType = MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
              attendeeMappings = listOf(),
              factorMappings = listOf(),
              interviewMappings = listOf(),
              planMappings = listOf(),
              reviewMappings = listOf(),
            ),
            object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFullMappingDto>>() {},
          )
        }
      }
    }

    @Nested
    inner class DeleteCSIPReportMapping {
      private val dpsCsipId = UUID.randomUUID().toString()

      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        csipMappingApi.stubDeleteCSIPReportMapping(dpsCsipId)

        csipMappingService.deleteCSIPReportMappingByDPSId(dpsCsipId)

        csipMappingApi.verify(
          deleteRequestedFor(WireMock.anyUrl()).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass id to service`() = runTest {
        val dpsCsipId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
        csipMappingApi.stubDeleteCSIPReportMapping(dpsCsipId)

        csipMappingService.deleteCSIPReportMappingByDPSId(dpsCsipId)

        csipMappingApi.verify(
          deleteRequestedFor(urlPathEqualTo("/mapping/csip/dps-csip-id/$dpsCsipId/all")),
        )
      }
    }
  }

  @Nested
  @DisplayName("CSIPFactor")
  inner class CSIPFactor {
    @Nested
    @DisplayName("GetCSIPFactorMapping")
    inner class GetCSIPFactorMapping {

      @Test
      internal fun `will return null when not found`() = runTest {
        csipMappingApi.stubGetFactorByNomisId(HttpStatus.NOT_FOUND)

        assertThat(csipMappingService.getCSIPFactorByNomisId(nomisCSIPFactorId = NOMIS_CSIP_ID)).isNull()
      }

      @Test
      internal fun `will return the factor mapping when found`(): Unit = runTest {
        val nomisCsipFactorId = 6543L
        val dpsCsipFactorId = UUID.randomUUID().toString()
        val dpsCSIPReportId = UUID.randomUUID().toString()
        csipMappingApi.stubGetFactorByNomisId(nomisCsipFactorId, dpsCsipFactorId, dpsCSIPReportId)

        val mapping = csipMappingService.getCSIPFactorByNomisId(nomisCSIPFactorId = nomisCsipFactorId)

        assertThat(mapping).isNotNull
        assertThat(mapping!!.dpsId).isEqualTo(dpsCsipFactorId)
        assertThat(mapping.nomisId).isEqualTo(nomisCsipFactorId)
        assertThat(mapping.dpsCSIPReportId).isEqualTo(dpsCSIPReportId)
        assertThat(mapping.label).isEqualTo("2022-02-14T09:58:45")
        assertThat(mapping.mappingType).isEqualTo(CSIPChildMappingDto.MappingType.NOMIS_CREATED)
        assertThat(mapping.whenCreated).isEqualTo("2020-01-01T11:10:00")
      }

      @Test
      internal fun `will throw exception for any other error`() = runTest {
        val nomisCsipFactorId = 6543L
        csipMappingApi.stubGetFactorByNomisId(HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.getCSIPFactorByNomisId(nomisCSIPFactorId = nomisCsipFactorId)
        }
      }
    }

    @Nested
    @DisplayName("findLatestMigration")
    inner class FindLatestMigration {
      @BeforeEach
      internal fun setUp() {
        csipMappingApi.stubCSIPLatestMigration("2020-01-01T10:00:00")
      }

      @Test
      internal fun `will supply authentication token`(): Unit = runTest {
        csipMappingService.findLatestMigration()

        mappingApi.verify(
          getRequestedFor(
            urlPathEqualTo("/mapping/csip/migrated/latest"),
          )
            .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will return null when not found`(): Unit = runTest {
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
      internal fun `will return the mapping when found`(): Unit = runTest {
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
      internal fun `will throw exception for any other error`() = runTest {
        mappingApi.stubFor(
          get(urlPathMatching("/mapping/csip/migrated/latest")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
              .withBody("""{"message":"Tea"}"""),
          ),
        )

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.findLatestMigration()
        }
      }
    }
  }

  @Nested
  @DisplayName("getMigrationDetails")
  inner class GetMigrationDetails {
    @BeforeEach
    internal fun setUp() {
      csipMappingApi.stubCSIPMappingByMigrationId("2020-01-01T11:10:00")
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runTest {
      csipMappingService.getMigrationDetails("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/csip/migration-id/.*"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will throw error when not found`() = runTest {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/csip/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThrows<WebClientResponseException.NotFound> {
        csipMappingService.getMigrationDetails("2020-01-01T10:00:00")
      }
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runTest {
      csipMappingApi.stubCSIPMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 56_766,
      )

      val mapping = csipMappingService.getMigrationDetails("2020-01-01T10:00:00")
      assertThat(mapping).isNotNull
      assertThat(mapping.startedDateTime).isEqualTo("2020-01-01T11:10:00")
      assertThat(mapping.count).isEqualTo(56766)
    }

    @Test
    internal fun `will throw exception for any other error`() = runTest {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/csip/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThrows<WebClientResponseException.InternalServerError> {
        csipMappingService.getMigrationDetails("2020-01-01T10:00:00")
      }
    }
  }

  @Nested
  @DisplayName("getMigrationCount")
  inner class GetMigrationCount {
    @BeforeEach
    internal fun setUp() {
      csipMappingApi.stubCSIPMappingByMigrationId(count = 56_766)
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runTest {
      csipMappingService.getMigrationCount("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/csip/migration-id/.*"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return zero when not found`(): Unit = runTest {
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
    internal fun `will return the mapping count when found`(): Unit = runTest {
      csipMappingApi.stubCSIPMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 54_766,
      )

      assertThat(csipMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(54_766)
    }

    @Test
    internal fun `will throw exception for any other error`() = runTest {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/csip/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThrows<WebClientResponseException.InternalServerError> {
        csipMappingService.getMigrationCount("2020-01-01T10:00:00")
      }
    }
  }
}
