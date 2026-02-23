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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceMoveBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceMovementIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingDto
import java.util.*

@SpringAPIServiceTest
@Import(ExternalMovementsMappingApiService::class, ExternalMovementsMappingApiMockServer::class, ExternalMovementsConfiguration::class)
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
  inner class CreateApplicationMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTemporaryAbsenceApplicationMapping()

      apiService.createApplicationMapping(
        temporaryAbsenceApplicationMapping(),
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
      )
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing!!.nomisMovementApplicationId).isEqualTo(1L)
          assertThat(errorResponse.moreInfo.duplicate.nomisMovementApplicationId).isEqualTo(2L)
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTemporaryAbsenceApplicationMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createApplicationMapping(
          temporaryAbsenceApplicationMapping(),
        )
      }
    }
  }

  @Nested
  inner class GetApplicationMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTemporaryAbsenceApplicationMapping()

      apiService.getApplicationMappingOrNull(1L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTemporaryAbsenceApplicationMapping(status = NOT_FOUND)

      apiService.getApplicationMappingOrNull(1L)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTemporaryAbsenceApplicationMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getApplicationMappingOrNull(1L)
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
  inner class CreateScheduledMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateScheduledMovementMapping()

      apiService.createScheduledMovementMapping(
        temporaryAbsenceScheduledMovementMapping(),
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
      )

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisEventId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsOccurrenceId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED")))
          .withRequestBody(matchingJsonPath("nomisAddressId", equalTo("321")))
          .withRequestBody(matchingJsonPath("nomisAddressOwnerClass", equalTo("OFF")))
          .withRequestBody(matchingJsonPath("dpsAddressText", equalTo("to full address"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsOccurrenceId = UUID.randomUUID()
      mappingApi.stubCreateScheduledMovementMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = ScheduledMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 1L,
              dpsOccurrenceId = dpsOccurrenceId,
              mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
              nomisAddressId = 0,
              nomisAddressOwnerClass = "",
              dpsAddressText = "",
              eventTime = "",
            ),
            duplicate = ScheduledMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 2L,
              dpsOccurrenceId = dpsOccurrenceId,
              mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
              nomisAddressId = 0,
              nomisAddressOwnerClass = "",
              dpsAddressText = "",
              eventTime = "",
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createScheduledMovementMapping(
        temporaryAbsenceScheduledMovementMapping(),
      )
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing!!.nomisEventId).isEqualTo(1L)
          assertThat(errorResponse.moreInfo.duplicate.nomisEventId).isEqualTo(2L)
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateScheduledMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createScheduledMovementMapping(
          temporaryAbsenceScheduledMovementMapping(),
        )
      }
    }
  }

  @Nested
  inner class UpdateScheduledMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubUpdateScheduledMovementMapping()

      apiService.updateScheduledMovementMapping(
        temporaryAbsenceScheduledMovementMapping(),
      )

      mappingApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubUpdateScheduledMovementMapping()

      apiService.updateScheduledMovementMapping(
        temporaryAbsenceScheduledMovementMapping(),
      )

      mappingApi.verify(
        putRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisEventId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsOccurrenceId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED")))
          .withRequestBody(matchingJsonPath("nomisAddressId", equalTo("321")))
          .withRequestBody(matchingJsonPath("nomisAddressOwnerClass", equalTo("OFF")))
          .withRequestBody(matchingJsonPath("dpsAddressText", equalTo("to full address"))),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubUpdateScheduledMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.updateScheduledMovementMapping(
          temporaryAbsenceScheduledMovementMapping(),
        )
      }
    }
  }

  @Nested
  inner class GetScheduledMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetScheduledMovementMapping()

      apiService.getScheduledMovementMappingOrNull(1L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetScheduledMovementMapping(status = NOT_FOUND)

      apiService.getScheduledMovementMappingOrNull(1L)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetScheduledMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getScheduledMovementMappingOrNull(1L)
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

      apiService.createExternalMovementMapping(temporaryAbsenceExternalMovementMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateExternalMovementMapping()

      apiService.createExternalMovementMapping(temporaryAbsenceExternalMovementMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisMovementSeq", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsMovementId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsMovementId = UUID.randomUUID()
      mappingApi.stubCreateExternalMovementMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = ExternalMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementSeq = 1,
              dpsMovementId = dpsMovementId,
              mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
              "",
              0,
              "",
            ),
            duplicate = ExternalMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementSeq = 2,
              dpsMovementId = dpsMovementId,
              mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
              "",
              0,
              "",
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createExternalMovementMapping(temporaryAbsenceExternalMovementMapping())
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing!!.nomisMovementSeq).isEqualTo(1)
          assertThat(errorResponse.moreInfo.duplicate.nomisMovementSeq).isEqualTo(2)
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateExternalMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createExternalMovementMapping(temporaryAbsenceExternalMovementMapping())
      }
    }
  }

  @Nested
  inner class UpdateExternalMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubUpdateExternalMovementMapping()

      apiService.updateExternalMovementMapping(temporaryAbsenceExternalMovementMapping())

      mappingApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubUpdateExternalMovementMapping()

      apiService.updateExternalMovementMapping(temporaryAbsenceExternalMovementMapping())

      mappingApi.verify(
        putRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisMovementSeq", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsMovementId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED")))
          .withRequestBody(matchingJsonPath("nomisAddressId", equalTo("321")))
          .withRequestBody(matchingJsonPath("nomisAddressOwnerClass", equalTo("OFF")))
          .withRequestBody(matchingJsonPath("dpsAddressText", equalTo("full address"))),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubUpdateExternalMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.updateExternalMovementMapping(temporaryAbsenceExternalMovementMapping())
      }
    }
  }

  @Nested
  inner class GetExternalMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetExternalMovementMapping()

      apiService.getExternalMovementMappingOrNull(12345L, 1)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetExternalMovementMapping(status = NOT_FOUND)

      apiService.getExternalMovementMappingOrNull(12345L, 1)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetExternalMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getExternalMovementMappingOrNull(12345L, 1)
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

  @Nested
  inner class FindScheduledMovementsForAddresses {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubFindScheduledMovementsForAddress()

      apiService.findScheduledMovementMappingsForAddress(123L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return list of schedule mappings`() = runTest {
      mappingApi.stubFindScheduledMovementsForAddress()

      with(apiService.findScheduledMovementMappingsForAddress(123L)) {
        assertThat(scheduleMappings).extracting("prisonerNumber").containsExactlyInAnyOrder("A1234AA", "B1234BB")
      }
    }

    @Test
    fun `should handle empty list if none found`() = runTest {
      mappingApi.stubFindScheduledMovementsForAddress(prisoners = listOf())

      with(apiService.findScheduledMovementMappingsForAddress(123L)) {
        assertThat(scheduleMappings.size).isEqualTo(0)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubFindScheduledMovementsForAddressError(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.findScheduledMovementMappingsForAddress(123L)
      }
    }
  }

  @Nested
  inner class GetMoveBookingMappings {
    val response = TemporaryAbsenceMoveBookingMappingDto(
      applicationIds = listOf(TemporaryAbsenceApplicationIdMapping(777L, UUID.randomUUID())),
      movementIds = listOf(TemporaryAbsenceMovementIdMapping(77, UUID.randomUUID())),
    )

    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetMoveBookingMappings(12345L, response)

      apiService.getMoveBookingMappings(12345L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return mappings`() = runTest {
      mappingApi.stubGetMoveBookingMappings(12345L, response)

      with(apiService.getMoveBookingMappings(12345L)) {
        assertThat(applicationIds).containsExactly(TemporaryAbsenceApplicationIdMapping(777, response.applicationIds.first().authorisationId))
        assertThat(movementIds).containsExactly(TemporaryAbsenceMovementIdMapping(77, response.movementIds.first().dpsMovementId))
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetMoveBookingMappingsError(12345L)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getMoveBookingMappings(12345L)
      }
    }
  }

  @Nested
  inner class MoveBookingMappings {
    @Test
    fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubMoveBookingMappings()

      apiService.moveBookingMappings(12345L, "A1234AA", "B1234BB")

      mappingApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubMoveBookingMappingsError(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.moveBookingMappings(12345L, "A1234AA", "B1234BB")
      }
    }
  }

  @Nested
  inner class GetPrisonerMappingIds {
    val response = TemporaryAbsenceMoveBookingMappingDto(
      applicationIds = listOf(TemporaryAbsenceApplicationIdMapping(777L, UUID.randomUUID())),
      movementIds = listOf(TemporaryAbsenceMovementIdMapping(77, UUID.randomUUID())),
    )

    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTemporaryAbsenceMappingIds()

      apiService.getPrisonerMappingIds("A1234BC")

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return mappings`() = runTest {
      mappingApi.stubGetTemporaryAbsenceMappingIds()

      with(apiService.getPrisonerMappingIds("A1234BC")) {
        assertThat(applications[0].nomisMovementApplicationId).isEqualTo(1)
        assertThat(schedules[0].nomisEventId).isEqualTo(2)
        assertThat(movements[0].nomisMovementSeq).isEqualTo(3)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTemporaryAbsenceMappingIds("A1234BC", status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getPrisonerMappingIds("A1234BC")
      }
    }
  }
}
