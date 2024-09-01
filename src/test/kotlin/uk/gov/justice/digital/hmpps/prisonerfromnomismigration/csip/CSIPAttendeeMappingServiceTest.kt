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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPAttendeeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

@SpringAPIServiceTest
@Import(CSIPMappingService::class, CSIPConfiguration::class, CSIPMappingApiMockServer::class)
internal class CSIPAttendeeMappingServiceTest {
  @Autowired
  private lateinit var csipMappingService: CSIPMappingService

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  @DisplayName("CSIPAttendee")
  inner class CSIPAttendee {
    @Nested
    @DisplayName("GetCSIPAttendeeMapping")
    inner class GetCSIPAttendeeMapping {
      private val nomisCSIPId = 1234L

      @Test
      internal fun `will return null when not found`() = runTest {
        csipMappingApi.stubGetAttendeeByNomisId(HttpStatus.NOT_FOUND)

        Assertions.assertThat(csipMappingService.getCSIPAttendeeByNomisId(nomisCSIPAttendeeId = nomisCSIPId)).isNull()
      }

      @Test
      internal fun `will return the attendee mapping when found`(): Unit = runTest {
        val nomisCsipAttendeeId = 6543L
        val dpsCsipAttendeeId = UUID.randomUUID().toString()
        val dpsCsipReportId = UUID.randomUUID().toString()
        csipMappingApi.stubGetAttendeeByNomisId(nomisCsipAttendeeId, dpsCsipAttendeeId, dpsCsipReportId)

        val mapping = csipMappingService.getCSIPAttendeeByNomisId(nomisCSIPAttendeeId = nomisCsipAttendeeId)

        Assertions.assertThat(mapping).isNotNull
        Assertions.assertThat(mapping!!.dpsCSIPAttendeeId).isEqualTo(dpsCsipAttendeeId)
        Assertions.assertThat(mapping.nomisCSIPAttendeeId).isEqualTo(nomisCsipAttendeeId)
        Assertions.assertThat(mapping.dpsCSIPReportId).isEqualTo(dpsCsipReportId)
        Assertions.assertThat(mapping.label).isEqualTo("2022-02-14T09:58:45")
        Assertions.assertThat(mapping.mappingType).isEqualTo(CSIPAttendeeMappingDto.MappingType.NOMIS_CREATED)
        Assertions.assertThat(mapping.whenCreated).isEqualTo("2020-01-01T11:10:00")
      }

      @Test
      internal fun `will throw exception for any other error`() = runTest {
        val nomisCsipAttendeeId = 6543L
        csipMappingApi.stubGetAttendeeByNomisId(HttpStatus.INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.getCSIPAttendeeByNomisId(nomisCSIPAttendeeId = nomisCsipAttendeeId)
        }
      }
    }

    @Nested
    @DisplayName("createCSIPAttendeeMapping")
    inner class CreateCSIPAttendeeMapping {

      private val nomisCsipAttendeeId = 7654L
      private val dpsCsipAttendeeId = UUID.randomUUID().toString()
      private val dpsCsipReportId = UUID.randomUUID().toString()

      @BeforeEach
      internal fun setUp() {
        mappingApi.stubFor(
          post(urlEqualTo("/mapping/csip/attendees")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.CREATED.value()),
          ),
        )
      }

      @Test
      fun `should provide oath2 token`() = runTest {
        csipMappingService.createCSIPAttendeeMapping(
          CSIPAttendeeMappingDto(
            dpsCSIPAttendeeId = dpsCsipAttendeeId,
            nomisCSIPAttendeeId = nomisCsipAttendeeId,
            dpsCSIPReportId = dpsCsipReportId,
            label = "some-migration-id",
            mappingType = CSIPAttendeeMappingDto.MappingType.MIGRATED,
          ),
        )

        mappingApi.verify(
          postRequestedFor(
            urlPathEqualTo("/mapping/csip/attendees"),
          ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass all parameters dps csip attendee id, nomis csip attendee id, migration Id and MIGRATED indicator to mapping service`(): Unit =
        runTest {
          csipMappingService.createCSIPAttendeeMapping(
            CSIPAttendeeMappingDto(
              dpsCSIPAttendeeId = dpsCsipAttendeeId,
              nomisCSIPAttendeeId = nomisCsipAttendeeId,
              dpsCSIPReportId = dpsCsipReportId,
              mappingType = CSIPAttendeeMappingDto.MappingType.MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
          )

          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/csip/attendees"))
              .withRequestBody(
                equalToJson(
                  """
                  {
                    "dpsCSIPAttendeeId": "$dpsCsipAttendeeId",
                    "nomisCSIPAttendeeId": $nomisCsipAttendeeId,                                       
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
          post(urlPathMatching("/mapping/csip/attendees")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
              .withBody("""{"message":"Tea"}"""),
          ),
        )

        assertThrows<WebClientResponseException.InternalServerError> {
          csipMappingService.createCSIPAttendeeMapping(
            CSIPAttendeeMappingDto(
              dpsCSIPAttendeeId = dpsCsipAttendeeId,
              nomisCSIPAttendeeId = nomisCsipAttendeeId,
              dpsCSIPReportId = dpsCsipReportId,
              mappingType = CSIPAttendeeMappingDto.MappingType.MIGRATED,
              label = "5678",
              whenCreated = "2020-01-01T00:00:00",
            ),
          )
        }
      }
    }

    @Nested
    inner class DeleteCSIPAttendeeMapping {
      private val dpsCsipAttendeeId = UUID.randomUUID().toString()

      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        csipMappingApi.stubDeleteAttendeeMapping(dpsCsipAttendeeId)

        csipMappingService.deleteCSIPAttendeeMappingByDPSId(dpsCsipAttendeeId)

        csipMappingApi.verify(
          WireMock.deleteRequestedFor(WireMock.anyUrl())
            .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass id to service`() = runTest {
        val dpsCsipAttendeeId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
        csipMappingApi.stubDeleteAttendeeMapping(dpsCsipAttendeeId)

        csipMappingService.deleteCSIPAttendeeMappingByDPSId(dpsCsipAttendeeId)

        csipMappingApi.verify(
          WireMock.deleteRequestedFor(WireMock.urlPathEqualTo("/mapping/csip/attendees/dps-csip-attendee-id/$dpsCsipAttendeeId")),
        )
      }
    }
  }
}
