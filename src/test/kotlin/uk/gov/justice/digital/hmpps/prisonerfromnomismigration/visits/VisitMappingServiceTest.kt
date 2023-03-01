package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

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
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

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
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/visits/nomisId/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(
        runBlocking { visitMappingService.findNomisVisitMapping(1234) },
      ).isNull()
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/visits/nomisId/.*")).willReturn(
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
              """.trimIndent(),
            ),
        ),
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
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/visits/nomisId/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          visitMappingService.findNomisVisitMapping(1234)
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("createNomisVisitMapping")
  inner class CreateNomisVisitMapping {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubFor(
        post(urlEqualTo("/mapping/visits")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value()),
        ),
      )
    }

    @Test
    internal fun `will pass VSIP visit id, migration Id and MIGRATED indicator to mapping service`(): Unit = runBlocking {
      visitMappingService.createNomisVisitMapping(1234, "5678", "2020-01-01T00:00:00")

      mappingApi.verify(
        postRequestedFor(urlEqualTo("/mapping/visits"))
          .withRequestBody(
            equalToJson(
              """
            {
              "nomisId": 1234,
              "vsipId": "5678",
              "label": "2020-01-01T00:00:00",
              "mappingType": "MIGRATED"
            }
              """.trimIndent(),
            ),
          ),
      )
    }
  }

  @Nested
  @DisplayName("findRoomMapping")
  inner class FindRoomMapping {

    @Test
    internal fun `will return null when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/prison/.+?/room/nomis-room-id/.+?")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(visitMappingService.findRoomMapping("HB7SOC", "HEI")).isNull()
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/prison/.+?/room/nomis-room-id/.+?")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
              {
                "vsipId": "1234",
                "isOpen": true
              }
              """.trimIndent(),
            ),
        ),
      )

      val mapping = visitMappingService.findRoomMapping("HB7SOC", "HEI")
      assertThat(mapping).isNotNull
      assertThat(mapping!!.vsipId).isEqualTo("1234")
      assertThat(mapping.isOpen).isEqualTo(true)
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/prison/.+?/room/nomis-room-id/.+?")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          visitMappingService.findRoomMapping("HB7SOC", "HEI")
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("findLatestMigration")
  inner class FindLatestMigration {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubLatestMigration("2020-01-01T10:00:00")
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      visitMappingService.findLatestMigration()

      mappingApi.verify(
        getRequestedFor(
          urlPathEqualTo("/mapping/visits/migrated/latest"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return null when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathEqualTo("/mapping/visits/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(visitMappingService.findLatestMigration()).isNull()
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/visits/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
{
    "nomisId": 14478380,
    "vsipId": "7fd62066-9aff-4f77-bdee-9d92aafc5555",
    "label": "2022-02-16T14:20:15",
    "mappingType": "MIGRATED",
    "whenCreated": "2022-02-16T16:21:15.589091"
}              
              """,
            ),
        ),
      )

      val mapping = visitMappingService.findLatestMigration()
      assertThat(mapping).isNotNull
      assertThat(mapping?.migrationId).isEqualTo("2022-02-16T14:20:15")
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/visits/migrated/latest")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          visitMappingService.findLatestMigration()
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("getMigrationDetails")
  inner class GetMigrationDetails {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubVisitMappingByMigrationId("2020-01-01T11:10:00")
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      visitMappingService.getMigrationDetails("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/visits/migration-id/.*"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will throw error when not found`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/visits/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          visitMappingService.getMigrationDetails("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.NotFound::class.java)
    }

    @Test
    internal fun `will return the mapping when found`(): Unit = runBlocking {
      mappingApi.stubVisitMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 56_766,
      )

      val mapping = visitMappingService.getMigrationDetails("2020-01-01T10:00:00")
      assertThat(mapping).isNotNull
      assertThat(mapping.startedDateTime).isEqualTo("2020-01-01T11:10:00")
      assertThat(mapping.count).isEqualTo(56766)
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/visits/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          visitMappingService.getMigrationDetails("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  @DisplayName("getMigrationCount")
  inner class GetMigrationCount {
    @BeforeEach
    internal fun setUp() {
      mappingApi.stubVisitMappingByMigrationId(count = 56_766)
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      visitMappingService.getMigrationCount("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/visits/migration-id/.*"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return zero when not found`(): Unit = runBlocking {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/visits/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(visitMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(0)
    }

    @Test
    internal fun `will return the mapping count when found`(): Unit = runBlocking {
      mappingApi.stubVisitMappingByMigrationId(
        whenCreated = "2020-01-01T11:10:00",
        count = 56_766,
      )

      assertThat(visitMappingService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(56_766)
    }

    @Test
    internal fun `will throw exception for any other error`() {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/visits/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          visitMappingService.getMigrationCount("2020-01-01T10:00:00")
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }
}
