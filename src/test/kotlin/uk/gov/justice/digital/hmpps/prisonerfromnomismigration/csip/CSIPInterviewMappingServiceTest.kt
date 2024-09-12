package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPInterviewMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

@SpringAPIServiceTest
@Import(CSIPMappingService::class, CSIPConfiguration::class, CSIPMappingApiMockServer::class)
internal class CSIPInterviewMappingServiceTest {
  @Autowired
  private lateinit var csipMappingService: CSIPMappingService

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  @DisplayName("CSIPInterview")
  inner class CSIPInterview {
    @Nested
    @DisplayName("GetCSIPInterviewMapping")
    inner class GetCSIPInterviewMapping {
      private val nomisCSIPId = 1234L

      @Test
      internal fun `will return null when not found`() = runTest {
        csipMappingApi.stubGetInterviewByNomisId(HttpStatus.NOT_FOUND)

        Assertions.assertThat(csipMappingService.getCSIPInterviewByNomisId(nomisCSIPInterviewId = nomisCSIPId)).isNull()
      }

      @Test
      internal fun `will return the interview mapping when found`(): Unit = runTest {
        val nomisCsipInterviewId = 6543L
        val dpsCsipInterviewId = UUID.randomUUID().toString()
        val dpsCsipReportId = UUID.randomUUID().toString()
        csipMappingApi.stubGetInterviewByNomisId(nomisCsipInterviewId, dpsCsipInterviewId, dpsCsipReportId)

        val mapping = csipMappingService.getCSIPInterviewByNomisId(nomisCSIPInterviewId = nomisCsipInterviewId)

        Assertions.assertThat(mapping).isNotNull
        Assertions.assertThat(mapping!!.dpsCSIPInterviewId).isEqualTo(dpsCsipInterviewId)
        Assertions.assertThat(mapping.nomisCSIPInterviewId).isEqualTo(nomisCsipInterviewId)
        Assertions.assertThat(mapping.dpsCSIPReportId).isEqualTo(dpsCsipReportId)
        Assertions.assertThat(mapping.label).isEqualTo("2022-02-14T09:58:45")
        Assertions.assertThat(mapping.mappingType).isEqualTo(CSIPInterviewMappingDto.MappingType.NOMIS_CREATED)
        Assertions.assertThat(mapping.whenCreated).isEqualTo("2020-01-01T11:10:00")
      }

      @Test
      internal fun `will throw exception for any other error`() = runTest {
        val nomisCsipInterviewId = 6543L
        csipMappingApi.stubGetInterviewByNomisId(HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.getCSIPInterviewByNomisId(nomisCSIPInterviewId = nomisCsipInterviewId)
        }
      }
    }

    @Nested
    @DisplayName("createCSIPInterviewMapping")
    inner class CreateCSIPInterviewMapping {

      private val nomisCsipInterviewId = 7654L
      private val dpsCsipInterviewId = UUID.randomUUID().toString()
      private val dpsCsipReportId = UUID.randomUUID().toString()

      @BeforeEach
      internal fun setUp() {
        mappingApi.stubFor(
          post(urlEqualTo("/mapping/csip/interviews")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.CREATED.value()),
          ),
        )
      }

      @Test
      fun `should provide oath2 token`() = runTest {
        csipMappingService.createCSIPInterviewMapping(
          CSIPInterviewMappingDto(
            dpsCSIPInterviewId = dpsCsipInterviewId,
            nomisCSIPInterviewId = nomisCsipInterviewId,
            dpsCSIPReportId = dpsCsipReportId,
            label = "some-migration-id",
            mappingType = CSIPInterviewMappingDto.MappingType.MIGRATED,
          ),
        )

        mappingApi.verify(
          postRequestedFor(
            urlPathEqualTo("/mapping/csip/interviews"),
          ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass all parameters dps csip interview id, nomis csip interview id, migration Id and MIGRATED indicator to mapping service`(): Unit =
        runTest {
          csipMappingService.createCSIPInterviewMapping(
            CSIPInterviewMappingDto(
              dpsCSIPInterviewId = dpsCsipInterviewId,
              nomisCSIPInterviewId = nomisCsipInterviewId,
              dpsCSIPReportId = dpsCsipReportId,
              mappingType = CSIPInterviewMappingDto.MappingType.MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
          )

          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/csip/interviews"))
              .withRequestBody(
                equalToJson(
                  """
                  {
                    "dpsCSIPInterviewId": "$dpsCsipInterviewId",
                    "nomisCSIPInterviewId": $nomisCsipInterviewId,                                       
                    "dpsCSIPReportId": "$dpsCsipReportId",                                       
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
      fun `should throw exception for any error`() = runTest {
        mappingApi.stubFor(
          post(urlPathMatching("/mapping/csip/interviews")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
              .withBody("""{"message":"Tea"}"""),
          ),
        )

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.createCSIPInterviewMapping(
            CSIPInterviewMappingDto(
              dpsCSIPInterviewId = dpsCsipInterviewId,
              nomisCSIPInterviewId = nomisCsipInterviewId,
              dpsCSIPReportId = dpsCsipReportId,
              mappingType = CSIPInterviewMappingDto.MappingType.MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
          )
        }
      }
    }
  }
}
