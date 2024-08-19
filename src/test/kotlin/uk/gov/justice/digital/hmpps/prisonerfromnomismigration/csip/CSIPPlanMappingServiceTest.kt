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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPPlanMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

@SpringAPIServiceTest
@Import(CSIPMappingService::class, CSIPConfiguration::class, CSIPMappingApiMockServer::class)
internal class CSIPPlanMappingServiceTest {
  @Autowired
  private lateinit var csipMappingService: CSIPMappingService

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  @DisplayName("CSIPPlan")
  inner class CSIPPlan {
    @Nested
    @DisplayName("GetCSIPPlanMapping")
    inner class GetCSIPPlanMapping {
      private val nomisCSIPId = 1234L

      @Test
      internal fun `will return null when not found`() = runTest {
        csipMappingApi.stubGetPlanByNomisId(HttpStatus.NOT_FOUND)

        Assertions.assertThat(csipMappingService.getCSIPPlanByNomisId(nomisCSIPPlanId = nomisCSIPId)).isNull()
      }

      @Test
      internal fun `will return the plan mapping when found`(): Unit = runTest {
        val nomisCsipPlanId = 6543L
        val dpsCsipPlanId = UUID.randomUUID().toString()
        csipMappingApi.stubGetPlanByNomisId(nomisCsipPlanId, dpsCsipPlanId)

        val mapping = csipMappingService.getCSIPPlanByNomisId(nomisCSIPPlanId = nomisCsipPlanId)

        Assertions.assertThat(mapping).isNotNull
        Assertions.assertThat(mapping!!.dpsCSIPPlanId).isEqualTo(dpsCsipPlanId)
        Assertions.assertThat(mapping.nomisCSIPPlanId).isEqualTo(nomisCsipPlanId)
        Assertions.assertThat(mapping.label).isEqualTo("2022-02-14T09:58:45")
        Assertions.assertThat(mapping.mappingType).isEqualTo(CSIPPlanMappingDto.MappingType.NOMIS_CREATED)
        Assertions.assertThat(mapping.whenCreated).isEqualTo("2020-01-01T11:10:00")
      }

      @Test
      internal fun `will throw exception for any other error`() = runTest {
        val nomisCsipPlanId = 6543L
        csipMappingApi.stubGetPlanByNomisId(HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.getCSIPPlanByNomisId(nomisCSIPPlanId = nomisCsipPlanId)
        }
      }
    }

    @Nested
    @DisplayName("createCSIPPlanMapping")
    inner class CreateCSIPPlanMapping {

      private val nomisCsipPlanId = 7654L
      private val dpsCsipPlanId = UUID.randomUUID().toString()

      @BeforeEach
      internal fun setUp() {
        mappingApi.stubFor(
          post(urlEqualTo("/mapping/csip/plans")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.CREATED.value()),
          ),
        )
      }

      @Test
      fun `should provide oath2 token`() = runTest {
        csipMappingService.createCSIPPlanMapping(
          CSIPPlanMappingDto(
            dpsCSIPPlanId = dpsCsipPlanId,
            nomisCSIPPlanId = nomisCsipPlanId,
            label = "some-migration-id",
            mappingType = CSIPPlanMappingDto.MappingType.MIGRATED,
          ),
        )

        mappingApi.verify(
          postRequestedFor(
            urlPathEqualTo("/mapping/csip/plans"),
          ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass all parameters dps csip plan id, nomis csip plan id, migration Id and MIGRATED indicator to mapping service`(): Unit =
        runTest {
          csipMappingService.createCSIPPlanMapping(
            CSIPPlanMappingDto(
              dpsCSIPPlanId = dpsCsipPlanId,
              nomisCSIPPlanId = nomisCsipPlanId,
              mappingType = CSIPPlanMappingDto.MappingType.MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
          )

          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/csip/plans"))
              .withRequestBody(
                equalToJson(
                  """
                  {
                  "dpsCSIPPlanId": "$dpsCsipPlanId",
                  "nomisCSIPPlanId": $nomisCsipPlanId,                                       
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
          post(urlPathMatching("/mapping/csip/plans")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
              .withBody("""{"message":"Tea"}"""),
          ),
        )

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.createCSIPPlanMapping(
            CSIPPlanMappingDto(
              dpsCSIPPlanId = dpsCsipPlanId,
              nomisCSIPPlanId = nomisCsipPlanId,
              mappingType = CSIPPlanMappingDto.MappingType.MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
          )
        }
      }
    }

    @Nested
    inner class DeleteCSIPPlanMapping {
      private val dpsCsipPlanId = UUID.randomUUID().toString()

      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        csipMappingApi.stubDeletePlanMapping(dpsCsipPlanId)

        csipMappingService.deleteCSIPPlanMappingByDPSId(dpsCsipPlanId)

        csipMappingApi.verify(
          WireMock.deleteRequestedFor(WireMock.anyUrl())
            .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass id to service`() = runTest {
        val dpsCsipPlanId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
        csipMappingApi.stubDeletePlanMapping(dpsCsipPlanId)

        csipMappingService.deleteCSIPPlanMappingByDPSId(dpsCsipPlanId)

        csipMappingApi.verify(
          WireMock.deleteRequestedFor(WireMock.urlPathEqualTo("/mapping/csip/plans/dps-csip-plan-id/$dpsCsipPlanId")),
        )
      }
    }
  }
}
