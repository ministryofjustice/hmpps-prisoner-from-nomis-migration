package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse

@SpringAPIServiceTest
@Import(ExternalMovementsMappingApiService::class, ExternalMovementsMappingApiMockServer::class)
class ExternalMovementsMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: ExternalMovementsMappingApiService

  @Autowired
  private lateinit var mappingApi: ExternalMovementsMappingApiMockServer

  @Nested
  inner class CreateMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTemporaryAbsenceMapping()

      apiService.createMapping(
        temporaryAbsencePrisonerMappings(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsencesPrisonerMappingDto>>() {},
      )

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateTemporaryAbsenceMapping()

      apiService.createMapping(
        temporaryAbsencePrisonerMappings(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsencesPrisonerMappingDto>>() {},
      )

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookings[0].bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("bookings[0].applications[0].nomisMovementApplicationId", equalTo("1")))
          .withRequestBody(matchingJsonPath("bookings[0].applications[0].dpsMovementApplicationId", equalTo("1001")))
          .withRequestBody(matchingJsonPath("migrationId", equalTo("2020-01-01T11:10:00"))),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTemporaryAbsenceMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createMapping(
          temporaryAbsencePrisonerMappings(),
          object : ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsencesPrisonerMappingDto>>() {},
        )
      }
    }
  }

  @Nested
  inner class GetMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTemporaryAbsenceMappings()

      apiService.getPrisonerTemporaryAbsenceMappings("A1234BC")

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTemporaryAbsenceMappings(status = NOT_FOUND)

      apiService.getPrisonerTemporaryAbsenceMappings("A1234BC")
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTemporaryAbsenceMappings(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getPrisonerTemporaryAbsenceMappings("A1234BC")
      }
    }
  }
}
