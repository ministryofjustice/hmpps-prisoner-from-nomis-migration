package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import java.util.*

@SpringAPIServiceTest
@Import(CourtSchedulerMappingApiService::class, CourtSchedulerMappingApiMockServer::class, CourtSchedulerConfiguration::class)
class CourtSchedulerMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: CourtSchedulerMappingApiService

  @Autowired
  private lateinit var mappingApi: CourtSchedulerMappingApiMockServer

  @Nested
  inner class CreateCourtScheduleMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateCourtScheduleMapping()

      apiService.createCourtScheduleMapping(courtScheduleMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateCourtScheduleMapping()

      apiService.createCourtScheduleMapping(courtScheduleMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisEventId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsCourtAppearanceId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsCourtAppearanceId = UUID.randomUUID()
      mappingApi.stubCreateCourtScheduleMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = CourtScheduleMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 1L,
              dpsCourtAppearanceId = dpsCourtAppearanceId,
              mappingType = CourtScheduleMappingDto.MappingType.NOMIS_CREATED,
            ),
            duplicate = CourtScheduleMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 2L,
              dpsCourtAppearanceId = dpsCourtAppearanceId,
              mappingType = CourtScheduleMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createCourtScheduleMapping(courtScheduleMapping())
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing!!.nomisEventId).isEqualTo(1L)
          assertThat(errorResponse.moreInfo.duplicate.nomisEventId).isEqualTo(2L)
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateCourtScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createCourtScheduleMapping(courtScheduleMapping())
      }
    }
  }

  @Nested
  inner class GetCourtScheduleMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetCourtScheduleMapping()

      apiService.getCourtScheduleMappingOrNull(1L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetCourtScheduleMapping(status = NOT_FOUND)

      apiService.getCourtScheduleMappingOrNull(1L)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetCourtScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getCourtScheduleMappingOrNull(1L)
      }
    }
  }

  @Nested
  inner class CreateCourtMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateCourtMovementMapping()

      apiService.createCourtMovementMapping(courtMovementMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateCourtMovementMapping()

      apiService.createCourtMovementMapping(courtMovementMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("nomisBookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisMovementSeq", equalTo("3")))
          .withRequestBody(matchingJsonPath("dpsCourtMovementId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsCourtMovementId = UUID.randomUUID()
      mappingApi.stubCreateCourtMovementMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = CourtMovementMappingDto(
              prisonerNumber = "A1234BC",
              nomisBookingId = 12345L,
              nomisMovementSeq = 3,
              dpsCourtMovementId = dpsCourtMovementId,
              mappingType = CourtMovementMappingDto.MappingType.NOMIS_CREATED,
            ),
            duplicate = CourtMovementMappingDto(
              prisonerNumber = "A1234BC",
              nomisBookingId = 12345L,
              nomisMovementSeq = 3,
              dpsCourtMovementId = dpsCourtMovementId,
              mappingType = CourtMovementMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createCourtMovementMapping(courtMovementMapping())
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing!!.nomisBookingId).isEqualTo(12345L)
          assertThat(errorResponse.moreInfo.duplicate.nomisMovementSeq).isEqualTo(3)
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateCourtMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createCourtMovementMapping(courtMovementMapping())
      }
    }
  }

  @Nested
  inner class GetCourtMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetCourtMovementMapping()

      apiService.getCourtMovementMappingOrNull(12345L, 3)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetCourtMovementMapping(status = NOT_FOUND)

      apiService.getCourtMovementMappingOrNull(12345L, 3)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetCourtMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getCourtMovementMappingOrNull(12345L, 3)
      }
    }
  }
}
