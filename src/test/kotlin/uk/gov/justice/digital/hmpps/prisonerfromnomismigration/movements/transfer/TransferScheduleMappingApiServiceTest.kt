package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.not
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferSchedulerPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import java.util.*

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

  @Nested
  inner class DeleteTransferScheduleMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubDeleteTransferScheduleMapping()

      apiService.deleteTransferScheduleMapping(1L)

      mappingApi.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubDeleteTransferScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteTransferScheduleMapping(1L)
      }
    }
  }

  @Nested
  inner class CreateTransferMovementMapping {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTransferMovementMapping()

      apiService.createTransferMovementMapping(transferMovementMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateTransferMovementMapping()

      apiService.createTransferMovementMapping(transferMovementMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("nomisBookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisMovementSeq", equalTo("3")))
          .withRequestBody(matchingJsonPath("dpsTransferMovementId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsTransferMovementId = UUID.randomUUID()
      mappingApi.stubCreateTransferMovementMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = transferMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, dpsTransferMovementId = dpsTransferMovementId),
            duplicate = transferMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 4, dpsTransferMovementId = dpsTransferMovementId),
          ),
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          errorCode = 1409,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createTransferMovementMapping(transferMovementMapping())
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing!!).isNotNull
          assertThat(errorResponse.moreInfo.duplicate).isNotNull
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTransferMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createTransferMovementMapping(transferMovementMapping())
      }
    }
  }

  @Nested
  inner class GetTransferMovementMapping {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTransferMovementMapping()

      apiService.getTransferMovementMappingOrNull(nomisBookingId = 12345L, nomisMovementSeq = 3)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return mapping`() = runTest {
      mappingApi.stubGetTransferMovementMapping()

      apiService.getTransferMovementMappingOrNull(nomisBookingId = 12345L, nomisMovementSeq = 3)
        .also {
          assertThat(it?.nomisBookingId).isEqualTo(12345L)
          assertThat(it?.nomisMovementSeq).isEqualTo(3)
          assertThat(it?.prisonerNumber).isEqualTo("A1234BC")
        }
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTransferMovementMapping(status = NOT_FOUND)

      apiService.getTransferMovementMappingOrNull(nomisBookingId = 12345L, nomisMovementSeq = 3)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTransferMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTransferMovementMappingOrNull(nomisBookingId = 12345L, nomisMovementSeq = 3)
      }
    }
  }

  @Nested
  inner class DeleteTransferMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubDeleteTransferMovementMapping()

      apiService.deleteTransferMovementMapping(12345L, 1)

      mappingApi.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass ids to URL`() = runTest {
      mappingApi.stubDeleteTransferMovementMapping(12345, 1)

      apiService.deleteTransferMovementMapping(12345L, 1)

      mappingApi.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement/nomis-id/12345/1")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubDeleteTransferMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteTransferMovementMapping(12345L, 1)
      }
    }
  }

  @Nested
  inner class CreateMigrationMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTransferSchedulerPrisonerMappings()

      apiService.createMapping(
        transferSchedulerPrisonerMappings(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<TransferSchedulerPrisonerMappingsDto>>() {},
      )

      mappingApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTransferSchedulerPrisonerMappings(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createMapping(
          transferSchedulerPrisonerMappings(),
          object : ParameterizedTypeReference<DuplicateErrorResponse<TransferSchedulerPrisonerMappingsDto>>() {},
        )
      }
    }
  }

  @Nested
  inner class GetPrisonerMappingIds {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTransferSchedulerPrisonerMappingIds()

      apiService.getMappings("A1234BC")

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return mappings`() = runTest {
      mappingApi.stubGetTransferSchedulerPrisonerMappingIds()

      with(apiService.getMappings("A1234BC")) {
        assertThat(schedules[0].nomisEventId).isEqualTo(1)
        assertThat(movements[0].nomisMovementSeq).isEqualTo(3)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTransferSchedulerPrisonerMappingIds(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getMappings("A1234BC")
      }
    }
  }
}
