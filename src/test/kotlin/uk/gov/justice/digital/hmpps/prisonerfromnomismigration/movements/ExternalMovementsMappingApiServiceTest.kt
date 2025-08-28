package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.not
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingDto
import java.util.*

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
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
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
        putRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookings[0].bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("bookings[0].applications[0].nomisMovementApplicationId", equalTo("1")))
          .withRequestBody(matchingJsonPath("bookings[0].applications[0].dpsMovementApplicationId", not(absent())))
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

  @Nested
  inner class CreateApplicationMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTemporaryAbsenceApplicationMapping()

      apiService.createApplicationMapping(
        temporaryAbsenceApplicationMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsenceApplicationSyncMappingDto>>() {},
      )

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateTemporaryAbsenceApplicationMapping()

      apiService.createApplicationMapping(
        temporaryAbsenceApplicationMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsenceApplicationSyncMappingDto>>() {},
      )

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisMovementApplicationId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsMovementApplicationId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsMovementApplicationId = UUID.randomUUID()
      mappingApi.stubCreateTemporaryAbsenceApplicationMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = TemporaryAbsenceApplicationSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementApplicationId = 1L,
              dpsMovementApplicationId = dpsMovementApplicationId,
              mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED,
            ),
            duplicate = TemporaryAbsenceApplicationSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementApplicationId = 2L,
              dpsMovementApplicationId = dpsMovementApplicationId,
              mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createApplicationMapping(
        temporaryAbsenceApplicationMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsenceApplicationSyncMappingDto>>() {},
      )
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing.nomisMovementApplicationId).isEqualTo(1L)
          assertThat(errorResponse.moreInfo.duplicate.nomisMovementApplicationId).isEqualTo(2L)
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTemporaryAbsenceApplicationMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createApplicationMapping(
          temporaryAbsenceApplicationMapping(),
          object : ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsenceApplicationSyncMappingDto>>() {},
        )
      }
    }
  }

  @Nested
  inner class GetApplicationMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTemporaryAbsenceApplicationMapping()

      apiService.getApplicationMapping(1L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTemporaryAbsenceApplicationMapping(status = NOT_FOUND)

      apiService.getApplicationMapping(1L)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTemporaryAbsenceApplicationMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getApplicationMapping(1L)
      }
    }
  }

  @Nested
  inner class DeleteApplicationMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubDeleteTemporaryAbsenceApplicationMapping()

      apiService.deleteApplicationMapping(1L)

      mappingApi.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubDeleteTemporaryAbsenceApplicationMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteApplicationMapping(1L)
      }
    }
  }

  @Nested
  inner class CreateOutsideMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateOutsideMovementMapping()

      apiService.createOutsideMovementMapping(
        temporaryAbsenceOutsideMovementMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsenceOutsideMovementSyncMappingDto>>() {},
      )

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateOutsideMovementMapping()

      apiService.createOutsideMovementMapping(
        temporaryAbsenceOutsideMovementMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsenceOutsideMovementSyncMappingDto>>() {},
      )

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisMovementApplicationMultiId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsOutsideMovementId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsOutsideMovementId = UUID.randomUUID()
      mappingApi.stubCreateOutsideMovementMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = TemporaryAbsenceOutsideMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementApplicationMultiId = 1L,
              dpsOutsideMovementId = dpsOutsideMovementId,
              mappingType = TemporaryAbsenceOutsideMovementSyncMappingDto.MappingType.NOMIS_CREATED,
            ),
            duplicate = TemporaryAbsenceOutsideMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementApplicationMultiId = 2L,
              dpsOutsideMovementId = dpsOutsideMovementId,
              mappingType = TemporaryAbsenceOutsideMovementSyncMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createOutsideMovementMapping(
        temporaryAbsenceOutsideMovementMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsenceOutsideMovementSyncMappingDto>>() {},
      )
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing.nomisMovementApplicationMultiId).isEqualTo(1L)
          assertThat(errorResponse.moreInfo.duplicate.nomisMovementApplicationMultiId).isEqualTo(2L)
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateOutsideMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createOutsideMovementMapping(
          temporaryAbsenceOutsideMovementMapping(),
          object : ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsenceOutsideMovementSyncMappingDto>>() {},
        )
      }
    }
  }

  @Nested
  inner class GetOutsideMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetOutsideMovementMapping()

      apiService.getOutsideMovementMapping(1L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetOutsideMovementMapping(status = NOT_FOUND)

      apiService.getOutsideMovementMapping(1L)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetOutsideMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getOutsideMovementMapping(1L)
      }
    }
  }

  @Nested
  inner class DeleteOutsideMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubDeleteOutsideMovementMapping()

      apiService.deleteOutsideMovementMapping(1L)

      mappingApi.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubDeleteOutsideMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteOutsideMovementMapping(1L)
      }
    }
  }

  @Nested
  inner class CreateScheduledMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateScheduledMovementMapping()

      apiService.createScheduledMovementMapping(
        temporaryAbsenceScheduledMovementMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledMovementSyncMappingDto>>() {},
      )

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateScheduledMovementMapping()

      apiService.createScheduledMovementMapping(
        temporaryAbsenceScheduledMovementMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledMovementSyncMappingDto>>() {},
      )

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisEventId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsScheduledMovementId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsScheduledMovementId = UUID.randomUUID()
      mappingApi.stubCreateScheduledMovementMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = ScheduledMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 1L,
              dpsScheduledMovementId = dpsScheduledMovementId,
              mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
            ),
            duplicate = ScheduledMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 2L,
              dpsScheduledMovementId = dpsScheduledMovementId,
              mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createScheduledMovementMapping(
        temporaryAbsenceScheduledMovementMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledMovementSyncMappingDto>>() {},
      )
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing.nomisEventId).isEqualTo(1L)
          assertThat(errorResponse.moreInfo.duplicate.nomisEventId).isEqualTo(2L)
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateScheduledMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createScheduledMovementMapping(
          temporaryAbsenceScheduledMovementMapping(),
          object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledMovementSyncMappingDto>>() {},
        )
      }
    }
  }

  @Nested
  inner class GetScheduledMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetScheduledMovementMapping()

      apiService.getScheduledMovementMapping(1L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetScheduledMovementMapping(status = NOT_FOUND)

      apiService.getScheduledMovementMapping(1L)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetScheduledMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getScheduledMovementMapping(1L)
      }
    }
  }

  @Nested
  inner class DeleteScheduledMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubDeleteScheduledMovementMapping()

      apiService.deleteScheduledMovementMapping(1L)

      mappingApi.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubDeleteScheduledMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteScheduledMovementMapping(1L)
      }
    }
  }

  @Nested
  inner class CreateExternalMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateExternalMovementMapping()

      apiService.createExternalMovementMapping(
        temporaryAbsenceExternalMovementMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<ExternalMovementSyncMappingDto>>() {},
      )

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateExternalMovementMapping()

      apiService.createExternalMovementMapping(
        temporaryAbsenceExternalMovementMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<ExternalMovementSyncMappingDto>>() {},
      )

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisMovementSeq", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsExternalMovementId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsExternalMovementId = UUID.randomUUID()
      mappingApi.stubCreateExternalMovementMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = ExternalMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementSeq = 1,
              dpsExternalMovementId = dpsExternalMovementId,
              mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
            ),
            duplicate = ExternalMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementSeq = 2,
              dpsExternalMovementId = dpsExternalMovementId,
              mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createExternalMovementMapping(
        temporaryAbsenceExternalMovementMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<ExternalMovementSyncMappingDto>>() {},
      )
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing.nomisMovementSeq).isEqualTo(1)
          assertThat(errorResponse.moreInfo.duplicate.nomisMovementSeq).isEqualTo(2)
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateExternalMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createExternalMovementMapping(
          temporaryAbsenceExternalMovementMapping(),
          object : ParameterizedTypeReference<DuplicateErrorResponse<ExternalMovementSyncMappingDto>>() {},
        )
      }
    }
  }

  @Nested
  inner class GetExternalMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetExternalMovementMapping()

      apiService.getExternalMovementMapping(12345L, 1)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetExternalMovementMapping(status = NOT_FOUND)

      apiService.getExternalMovementMapping(12345L, 1)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetExternalMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getExternalMovementMapping(12345L, 1)
      }
    }
  }

  @Nested
  inner class DeleteExternalMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubDeleteExternalMovementMapping()

      apiService.deleteExternalMovementMapping(12345L, 1)

      mappingApi.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubDeleteExternalMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteExternalMovementMapping(12345L, 1)
      }
    }
  }
}
