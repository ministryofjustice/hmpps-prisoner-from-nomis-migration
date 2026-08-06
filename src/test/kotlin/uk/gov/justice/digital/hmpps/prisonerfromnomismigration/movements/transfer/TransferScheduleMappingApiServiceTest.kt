package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.not
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import java.util.UUID

@ExtendWith(MappingApiExtension::class)
@SpringAPIServiceTest
@Import(TransferScheduleMappingApiService::class, TransferScheduleMappingApiMockServer::class, TransferScheduleConfiguration::class)
class TransferScheduleMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: TransferScheduleMappingApiService

  @Autowired
  private lateinit var mappingApi: TransferScheduleMappingApiMockServer

  @Nested
  inner class CreateTransferScheduleMapping {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTransferScheduleMapping()

      apiService.createTransferScheduleMapping(transferScheduleMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateTransferScheduleMapping()

      apiService.createTransferScheduleMapping(transferScheduleMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisEventId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsTransferScheduleId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsTransferScheduleId = UUID.randomUUID()
      mappingApi.stubCreateTransferScheduleMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = transferScheduleMapping(nomisEventId = 1L, dpsTransferScheduleId = dpsTransferScheduleId),
            duplicate = transferScheduleMapping(nomisEventId = 2L, dpsTransferScheduleId = dpsTransferScheduleId),
          ),
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          errorCode = 1409,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createTransferScheduleMapping(transferScheduleMapping())
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing!!).isNotNull
          assertThat(errorResponse.moreInfo.duplicate).isNotNull
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTransferScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createTransferScheduleMapping(transferScheduleMapping())
      }
    }
  }

  @Nested
  inner class GetTransferScheduleMapping {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTransferScheduleMapping()

      apiService.getTransferScheduleMappingOrNull(1L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return mapping`() = runTest {
      mappingApi.stubGetTransferScheduleMapping(nomisEventId = 1L, prisonerNumber = "A1234BC")

      apiService.getTransferScheduleMappingOrNull(1L)
        .also {
          assertThat(it?.nomisEventId).isEqualTo(1L)
          assertThat(it?.prisonerNumber).isEqualTo("A1234BC")
        }
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTransferScheduleMapping(status = NOT_FOUND)

      apiService.getTransferScheduleMappingOrNull(1L)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTransferScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTransferScheduleMappingOrNull(1L)
      }
    }
  }
}
