package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.prisonerlocation

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerLocationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import java.util.*

@ExtendWith(MappingApiExtension::class)
@SpringAPIServiceTest
@Import(PrisonerLocationMappingApiService::class, PrisonerLocationMappingApiMockServer::class, PrisonerLocationConfiguration::class)
class PrisonerLocationMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: PrisonerLocationMappingApiService

  @Autowired
  private lateinit var mappingApi: PrisonerLocationMappingApiMockServer

  private val errorType = object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonerLocationsMappingDto>>() {}

  @Nested
  inner class CreateMigrationMappings {
    @Test
    internal fun `should pass oauth2 token to service`() = runTest {
      mappingApi.stubCreatePrisonerLocationMappings()

      apiService.createMapping(prisonerLocationsMapping(), errorType)

      mappingApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      val dpsCustodialSeriesId = UUID.randomUUID()
      val dpsExternalMovementId = UUID.randomUUID()
      mappingApi.stubCreatePrisonerLocationMappings()

      apiService.createMapping(
        prisonerLocationsMapping(
          offenderNo = "A1234BC",
          bookingId = 12345,
          dpsCustodialSeriesId = dpsCustodialSeriesId,
          nomisMovementSeq = 3,
          dpsExternalMovementId = dpsExternalMovementId,
        ),
        errorType,
      ).also { assertThat(it.isError).isFalse }

      mappingApi.verify(
        putRequestedFor(urlPathEqualTo("/mapping/prisoner-location/migrate"))
          .withRequestBody(matchingJsonPath("offenderNo", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("migrationId", equalTo("2020-01-01T11:10:00")))
          .withRequestBody(matchingJsonPath("bookings[0].bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("bookings[0].dpsCustodialSeriesId", equalTo(dpsCustodialSeriesId.toString())))
          .withRequestBody(matchingJsonPath("bookings[0].movements[0].nomisMovementSeq", equalTo("3")))
          .withRequestBody(matchingJsonPath("bookings[0].movements[0].dpsExternalMovementId", equalTo(dpsExternalMovementId.toString()))),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreatePrisonerLocationMappings(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createMapping(prisonerLocationsMapping(), errorType)
      }
    }
  }
}
