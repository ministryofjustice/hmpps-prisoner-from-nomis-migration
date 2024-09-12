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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFactorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

@SpringAPIServiceTest
@Import(CSIPMappingService::class, CSIPConfiguration::class, CSIPMappingApiMockServer::class)
internal class CSIPFactorMappingServiceTest {
  @Autowired
  private lateinit var csipMappingService: CSIPMappingService

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  @DisplayName("CSIPFactor")
  inner class CSIPFactor {
    @Nested
    @DisplayName("GetCSIPFactorMapping")
    inner class GetCSIPFactorMapping {
      private val nomisCSIPId = 1234L

      @Test
      internal fun `will return null when not found`() = runTest {
        csipMappingApi.stubGetFactorByNomisId(HttpStatus.NOT_FOUND)

        Assertions.assertThat(csipMappingService.getCSIPFactorByNomisId(nomisCSIPFactorId = nomisCSIPId)).isNull()
      }

      @Test
      internal fun `will return the factor mapping when found`(): Unit = runTest {
        val nomisCsipFactorId = 6543L
        val dpsCsipFactorId = UUID.randomUUID().toString()
        val dpsCsipReportId = UUID.randomUUID().toString()
        csipMappingApi.stubGetFactorByNomisId(nomisCsipFactorId, dpsCsipFactorId, dpsCsipReportId)

        val mapping = csipMappingService.getCSIPFactorByNomisId(nomisCSIPFactorId = nomisCsipFactorId)

        Assertions.assertThat(mapping).isNotNull
        Assertions.assertThat(mapping!!.dpsCSIPFactorId).isEqualTo(dpsCsipFactorId)
        Assertions.assertThat(mapping.nomisCSIPFactorId).isEqualTo(nomisCsipFactorId)
        Assertions.assertThat(mapping.label).isEqualTo("2022-02-14T09:58:45")
        Assertions.assertThat(mapping.mappingType).isEqualTo(CSIPFactorMappingDto.MappingType.NOMIS_CREATED)
        Assertions.assertThat(mapping.whenCreated).isEqualTo("2020-01-01T11:10:00")
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
    @DisplayName("createCSIPFactorMapping")
    inner class CreateCSIPFactorMapping {

      private val nomisCsipFactorId = 7654L
      private val dpsCsipFactorId = UUID.randomUUID().toString()
      private val dpsCsipReportId = UUID.randomUUID().toString()

      @BeforeEach
      internal fun setUp() {
        mappingApi.stubFor(
          post(urlEqualTo("/mapping/csip/factors")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.CREATED.value()),
          ),
        )
      }

      @Test
      fun `should provide oath2 token`() = runTest {
        csipMappingService.createCSIPFactorMapping(
          CSIPFactorMappingDto(
            dpsCSIPFactorId = dpsCsipFactorId,
            nomisCSIPFactorId = nomisCsipFactorId,
            dpsCSIPReportId = dpsCsipReportId,
            label = "some-migration-id",
            mappingType = CSIPFactorMappingDto.MappingType.MIGRATED,
          ),
          // TODO ADD IN  object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFactorMappingDto>>() {},
        )

        mappingApi.verify(
          postRequestedFor(
            urlPathEqualTo("/mapping/csip/factors"),
          ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass all parameters dps csip factor id, nomis csip factor id, migration Id and MIGRATED indicator to mapping service`(): Unit =
        runTest {
          csipMappingService.createCSIPFactorMapping(
            CSIPFactorMappingDto(
              dpsCSIPFactorId = dpsCsipFactorId,
              nomisCSIPFactorId = nomisCsipFactorId,
              mappingType = CSIPFactorMappingDto.MappingType.MIGRATED,
              dpsCSIPReportId = dpsCsipReportId,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
          )

          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/csip/factors"))
              .withRequestBody(
                equalToJson(
                  """
                  {
                  "dpsCSIPFactorId": "$dpsCsipFactorId",
                  "nomisCSIPFactorId": $nomisCsipFactorId,                                       
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
          post(urlPathMatching("/mapping/csip/factors")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
              .withBody("""{"message":"Tea"}"""),
          ),
        )

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.createCSIPFactorMapping(
            CSIPFactorMappingDto(
              dpsCSIPFactorId = dpsCsipFactorId,
              nomisCSIPFactorId = nomisCsipFactorId,
              mappingType = CSIPFactorMappingDto.MappingType.MIGRATED,
              dpsCSIPReportId = dpsCsipReportId,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
          )
        }
      }
    }
  }
}
