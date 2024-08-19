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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPReviewMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

@SpringAPIServiceTest
@Import(CSIPMappingService::class, CSIPConfiguration::class, CSIPMappingApiMockServer::class)
internal class CSIPReviewMappingServiceTest {
  @Autowired
  private lateinit var csipMappingService: CSIPMappingService

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  @DisplayName("CSIPReview")
  inner class CSIPReview {
    @Nested
    @DisplayName("GetCSIPReviewMapping")
    inner class GetCSIPReviewMapping {
      private val nomisCSIPId = 1234L

      @Test
      internal fun `will return null when not found`() = runTest {
        csipMappingApi.stubGetReviewByNomisId(HttpStatus.NOT_FOUND)

        Assertions.assertThat(csipMappingService.getCSIPReviewByNomisId(nomisCSIPReviewId = nomisCSIPId)).isNull()
      }

      @Test
      internal fun `will return the review mapping when found`(): Unit = runTest {
        val nomisCsipReviewId = 6543L
        val dpsCsipReviewId = UUID.randomUUID().toString()
        csipMappingApi.stubGetReviewByNomisId(nomisCsipReviewId, dpsCsipReviewId)

        val mapping = csipMappingService.getCSIPReviewByNomisId(nomisCSIPReviewId = nomisCsipReviewId)

        Assertions.assertThat(mapping).isNotNull
        Assertions.assertThat(mapping!!.dpsCSIPReviewId).isEqualTo(dpsCsipReviewId)
        Assertions.assertThat(mapping.nomisCSIPReviewId).isEqualTo(nomisCsipReviewId)
        Assertions.assertThat(mapping.label).isEqualTo("2022-02-14T09:58:45")
        Assertions.assertThat(mapping.mappingType).isEqualTo(CSIPReviewMappingDto.MappingType.NOMIS_CREATED)
        Assertions.assertThat(mapping.whenCreated).isEqualTo("2020-01-01T11:10:00")
      }

      @Test
      internal fun `will throw exception for any other error`() = runTest {
        val nomisCsipReviewId = 6543L
        csipMappingApi.stubGetReviewByNomisId(HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.getCSIPReviewByNomisId(nomisCSIPReviewId = nomisCsipReviewId)
        }
      }
    }

    @Nested
    @DisplayName("createCSIPReviewMapping")
    inner class CreateCSIPReviewMapping {

      val nomisCsipReviewId = 7654L
      val dpsCsipReviewId = UUID.randomUUID().toString()

      @BeforeEach
      internal fun setUp() {
        mappingApi.stubFor(
          post(urlEqualTo("/mapping/csip/reviews")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.CREATED.value()),
          ),
        )
      }

      @Test
      fun `should provide oath2 token`() = runTest {
        csipMappingService.createCSIPReviewMapping(
          CSIPReviewMappingDto(
            dpsCSIPReviewId = dpsCsipReviewId,
            nomisCSIPReviewId = nomisCsipReviewId,
            label = "some-migration-id",
            mappingType = CSIPReviewMappingDto.MappingType.MIGRATED,
          ),
        )

        mappingApi.verify(
          postRequestedFor(
            urlPathEqualTo("/mapping/csip/reviews"),
          ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass all parameters dps csip review id, nomis csip review id, migration Id and MIGRATED indicator to mapping service`(): Unit =
        runTest {
          csipMappingService.createCSIPReviewMapping(
            CSIPReviewMappingDto(
              dpsCSIPReviewId = dpsCsipReviewId,
              nomisCSIPReviewId = nomisCsipReviewId,
              mappingType = CSIPReviewMappingDto.MappingType.MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
          )

          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/csip/reviews"))
              .withRequestBody(
                equalToJson(
                  """
                  {
                  "dpsCSIPReviewId": "$dpsCsipReviewId",
                  "nomisCSIPReviewId": $nomisCsipReviewId,                                       
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
          post(urlPathMatching("/mapping/csip/reviews")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
              .withBody("""{"message":"Tea"}"""),
          ),
        )

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.createCSIPReviewMapping(
            CSIPReviewMappingDto(
              dpsCSIPReviewId = dpsCsipReviewId,
              nomisCSIPReviewId = nomisCsipReviewId,
              mappingType = CSIPReviewMappingDto.MappingType.MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
          )
        }
      }
    }

    @Nested
    inner class DeleteCSIPReviewMapping {
      private val dpsCsipReviewId = UUID.randomUUID().toString()

      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        csipMappingApi.stubDeleteReviewMapping(dpsCsipReviewId)

        csipMappingService.deleteCSIPReviewMappingByDPSId(dpsCsipReviewId)

        csipMappingApi.verify(
          WireMock.deleteRequestedFor(WireMock.anyUrl())
            .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass id to service`() = runTest {
        val dpsCsipReviewId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
        csipMappingApi.stubDeleteReviewMapping(dpsCsipReviewId)

        csipMappingService.deleteCSIPReviewMappingByDPSId(dpsCsipReviewId)

        csipMappingApi.verify(
          WireMock.deleteRequestedFor(WireMock.urlPathEqualTo("/mapping/csip/reviews/dps-csip-review-id/$dpsCsipReviewId")),
        )
      }
    }
  }
}
