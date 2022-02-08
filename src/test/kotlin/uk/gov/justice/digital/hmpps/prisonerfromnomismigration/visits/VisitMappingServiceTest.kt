package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitMappingApiExtension.Companion.visitMappingApi

@SpringAPIServiceTest
@Import(VisitMappingService::class, VisitsConfiguration::class)
internal class VisitMappingServiceTest {
  @Autowired
  private lateinit var visitMappingService: VisitMappingService

  @Nested
  @DisplayName("findNomisVisitMapping")
  inner class FindNomisVisitMapping {

    @Test
    internal fun `will return null when not found`() {
      visitMappingApi.stubFor(
        get(urlPathMatching("/mapping/nomisId/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}""")
        )
      )

      assertThat(visitMappingService.findNomisVisitMapping(1234)).isNull()
    }

    @Test
    internal fun `will return the mapping when found`() {
      visitMappingApi.stubFor(
        get(urlPathMatching("/mapping/nomisId/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
              {
                "nomisId": 1234,
                "vsipId": "5678",
                "label": "2020-01-01T00:00:00",
                "mappingType": "MIGRATED"
              }
              """.trimIndent()
            )
        )
      )

      val mapping = visitMappingService.findNomisVisitMapping(1234)
      assertThat(mapping).isNotNull
      assertThat(mapping!!.nomisId).isEqualTo(1234)
      assertThat(mapping.vsipId).isEqualTo("5678")
      assertThat(mapping.label).isEqualTo("2020-01-01T00:00:00")
      assertThat(mapping.mappingType).isEqualTo("MIGRATED")
    }

    @Test
    internal fun `will throw exception for any other error`() {
      visitMappingApi.stubFor(
        get(urlPathMatching("/mapping/nomisId/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}""")
        )
      )

      assertThatThrownBy {
        visitMappingService.findNomisVisitMapping(1234)
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("createNomisVisitMapping")
  inner class CreateNomisVisitMapping {
    @BeforeEach
    internal fun setUp() {
      visitMappingApi.stubFor(
        post(urlEqualTo("/mapping")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
        )
      )
    }

    @Test
    internal fun `will pass VSIP visit id, migration Id and MIGRATED indicator to mapping service`() {
      visitMappingService.createNomisVisitMapping(1234, "5678", "2020-01-01T00:00:00")

      visitMappingApi.verify(
        postRequestedFor(urlEqualTo("/mapping"))
          .withRequestBody(
            equalToJson(
              """
            {
              "nomisId": 1234,
              "vsipId": "5678",
              "label": "2020-01-01T00:00:00",
              "mappingType": "MIGRATED"
            }
              """.trimIndent()
            )
          )
      )
    }
  }

  @Nested
  @DisplayName("findRoomMapping")
  inner class FindRoomMapping {

    @Test
    internal fun `will return null when not found`() {
      visitMappingApi.stubFor(
        get(urlPathMatching("/prison/.+?/room/nomisRoomId/.+?")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}""")
        )
      )

      assertThat(visitMappingService.findRoomMapping("HB7SOC", "HEI")).isNull()
    }

    @Test
    internal fun `will return the mapping when found`() {
      visitMappingApi.stubFor(
        get(urlPathMatching("/prison/.+?/room/nomisRoomId/.+?")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
              {
                "vsipRoomId": "1234",
                "isOpen": true
              }
              """.trimIndent()
            )
        )
      )

      val mapping = visitMappingService.findRoomMapping("HB7SOC", "HEI")
      assertThat(mapping).isNotNull
      assertThat(mapping!!.vsipRoomId).isEqualTo("1234")
      assertThat(mapping.isOpen).isEqualTo(true)
    }

    @Test
    internal fun `will throw exception for any other error`() {
      visitMappingApi.stubFor(
        get(urlPathMatching("/prison/.+?/room/nomisRoomId/.+?")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}""")
        )
      )

      assertThatThrownBy {
        visitMappingService.findRoomMapping("HB7SOC", "HEI")
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }
}
