package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.LocationsConfiguration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.LocationsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.util.UUID

private const val BED_ASSIGNMENT_LOCATION_ID = "abcdef12-1234-5678-9012-abcdef123456"

@ExtendWith(NomisApiExtension::class, MappingApiExtension::class)
@SpringAPIServiceTest
@Import(
  TranslatorService::class,
  BedAssignmentNomisApiService::class,
  BedAssignmentConfiguration::class,
  BedAssignmentNomisApiMockServer::class,
  LocationsMappingService::class,
  LocationsConfiguration::class,
)
class TranslatorServiceTest {
  @Autowired
  private lateinit var historyService: TranslatorService

  @Autowired
  private lateinit var nomisMockServer: BedAssignmentNomisApiMockServer

  @Nested
  inner class GetHistory {
    @Test
    fun `converts every assignment and resolves a distinct location only once`() = runTest {
      nomisMockServer.stubGetBedAssignmentsForPrisoner(
        "A1234KT",
        bedAssignmentHistoryWithRowsJson(
          bedAssignmentJson(bookingId = 123456, sequence = 1, livingUnitId = 987654),
          bedAssignmentJson(bookingId = 234567, sequence = 2, livingUnitId = 987654),
        ),
      )
      stubLocationMapping(987654, BED_ASSIGNMENT_LOCATION_ID)

      val history = historyService.getHistory("A1234KT")

      assertThat(history).hasSize(2)
      assertThat(history.map { it.bookingId }).containsExactly(123456L, 234567L)
      assertThat(history.map { it.sequence }).containsExactly(1, 2)
      assertThat(history.map { it.livingUnitId }).containsOnly(987654L)
      assertThat(history.map { it.locationId }).containsOnly(UUID.fromString(BED_ASSIGNMENT_LOCATION_ID))
      assertThat(history.map { it.createdBy }).containsOnly("NOMIS.USER")
      mappingApi.verify(
        1,
        com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
          com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/mapping/locations/nomis/987654"),
        ),
      )
    }

    @Test
    fun `preserves null historical fields and leaves deleted or unmapped locations without a DPS id`() = runTest {
      nomisMockServer.stubGetBedAssignmentsForPrisoner(
        "A1234KT",
        bedAssignmentHistoryWithRowsJson(
          bedAssignmentJson(
            bookingId = 123456,
            sequence = 1,
            livingUnitId = -7,
            livingUnitDescription = null,
            reasonCode = null,
            endDateTime = "2024-02-03T04:05:06",
            createUsername = null,
          ),
        ),
      )
      stubLocationNotFound()

      val history = historyService.getHistory("A1234KT")

      assertThat(history).hasSize(1)
      assertThat(history.single().livingUnitId).isEqualTo(-7)
      assertThat(history.single().livingUnitDescription).isNull()
      assertThat(history.single().assignmentReasonCode).isNull()
      assertThat(history.single().assignmentEndDateTime).isNotNull
      assertThat(history.single().createdBy).isEqualTo("NOMIS.USER")
      assertThat(history.single().locationId).isNull()
    }

    @Test
    fun `returns an empty history without resolving locations`() = runTest {
      nomisMockServer.stubGetBedAssignmentsForPrisoner("A1234KT", """{"bedAssignments":[]}""")

      assertThat(historyService.getHistory("A1234KT")).isEmpty()
      mappingApi.verify(
        0,
        com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
          urlPathMatching("/mapping/locations/nomis/.*"),
        ),
      )
    }

    @Test
    fun `does not suppress location mapping service errors`() {
      nomisMockServer.stubGetBedAssignmentsForPrisoner(
        "A1234KT",
        bedAssignmentHistoryJson(livingUnitId = 987654),
      )
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/locations/nomis/.*"))
          .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()).withBody("""{"message":"down"}""")),
      )

      assertThatThrownBy {
        runTest { historyService.getHistory("A1234KT") }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }

    @Test
    fun `fails visibly when a location mapping contains an invalid UUID`() {
      nomisMockServer.stubGetBedAssignmentsForPrisoner(
        "A1234KT",
        bedAssignmentHistoryJson(livingUnitId = 987654),
      )
      stubLocationMapping(987654, "not-a-uuid")

      assertThatThrownBy {
        runTest { historyService.getHistory("A1234KT") }
      }.isInstanceOf(IllegalArgumentException::class.java)
    }
  }

  private fun stubLocationMapping(nomisLocationId: Long, dpsLocationId: String) {
    mappingApi.stubFor(
      get("/mapping/locations/nomis/$nomisLocationId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(
            """{"dpsLocationId":"$dpsLocationId","nomisLocationId":$nomisLocationId,"mappingType":"MIGRATED"}""",
          ),
      ),
    )
  }

  private fun stubLocationNotFound() {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/locations/nomis/.*"))
        .willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value()).withBody("""{"message":"not found"}""")),
    )
  }
}
