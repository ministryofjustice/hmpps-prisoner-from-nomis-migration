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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapConfiguration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapApplicationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceMoveBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceMovementIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingDto
import java.util.*

@SpringAPIServiceTest
@Import(ExternalMovementsMappingApiService::class, ExternalMovementsMappingApiMockServer::class, TapConfiguration::class)
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
      mappingApi.stubCreateTapApplicationMapping()

      apiService.createTapApplicationMapping(
        tapApplicationMapping(),
      )

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateTapApplicationMapping()

      apiService.createTapApplicationMapping(
        tapApplicationMapping(),
      )

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisApplicationId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsAuthorisationId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsAuthorisationId = UUID.randomUUID()
      mappingApi.stubCreateTapApplicationMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = TapApplicationMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisApplicationId = 1L,
              dpsAuthorisationId = dpsAuthorisationId,
              mappingType = TapApplicationMappingDto.MappingType.NOMIS_CREATED,
            ),
            duplicate = TapApplicationMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisApplicationId = 2L,
              dpsAuthorisationId = dpsAuthorisationId,
              mappingType = TapApplicationMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createTapApplicationMapping(
        tapApplicationMapping(),
      )
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing!!.nomisApplicationId).isEqualTo(1L)
          assertThat(errorResponse.moreInfo.duplicate.nomisApplicationId).isEqualTo(2L)
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTapApplicationMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createTapApplicationMapping(
          tapApplicationMapping(),
        )
      }
    }
  }

  @Nested
  inner class GetApplicationMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTapApplicationMapping()

      apiService.getTapApplicationMappingOrNull(1L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTapApplicationMapping(status = NOT_FOUND)

      apiService.getTapApplicationMappingOrNull(1L)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTapApplicationMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapApplicationMappingOrNull(1L)
      }
    }
  }

  @Nested
  inner class DeleteApplicationMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubDeleteTapApplicationMapping()

      apiService.deleteTapApplicationMapping(1L)

      mappingApi.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubDeleteTapApplicationMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteTapApplicationMapping(1L)
      }
    }
  }

  @Nested
  inner class CreateScheduledMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTapScheduleMapping()

      apiService.createTapScheduleMapping(
        tapScheduleMapping(),
      )

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateTapScheduleMapping()

      apiService.createTapScheduleMapping(
        tapScheduleMapping(),
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
      mappingApi.stubCreateTapScheduleMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = TapScheduleMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 1L,
              dpsOccurrenceId = dpsOccurrenceId,
              mappingType = TapScheduleMappingDto.MappingType.NOMIS_CREATED,
              nomisAddressId = 0,
              nomisAddressOwnerClass = "",
              dpsAddressText = "",
              eventTime = "",
            ),
            duplicate = TapScheduleMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 2L,
              dpsOccurrenceId = dpsOccurrenceId,
              mappingType = TapScheduleMappingDto.MappingType.NOMIS_CREATED,
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

      apiService.createTapScheduleMapping(
        tapScheduleMapping(),
      )
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing!!.nomisEventId).isEqualTo(1L)
          assertThat(errorResponse.moreInfo.duplicate.nomisEventId).isEqualTo(2L)
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTapScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createTapScheduleMapping(
          tapScheduleMapping(),
        )
      }
    }
  }

  @Nested
  inner class UpdateScheduledMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubUpdateTapScheduleMapping()

      apiService.updateTapScheduleMapping(
        tapScheduleMapping(),
      )

      mappingApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubUpdateTapScheduleMapping()

      apiService.updateTapScheduleMapping(
        tapScheduleMapping(),
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
      mappingApi.stubUpdateTapScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.updateTapScheduleMapping(
          tapScheduleMapping(),
        )
      }
    }
  }

  @Nested
  inner class GetScheduledMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTapScheduleMapping()

      apiService.getTapScheduleMappingOrNull(1L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTapScheduleMapping(status = NOT_FOUND)

      apiService.getTapScheduleMappingOrNull(1L)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTapScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapScheduleMappingOrNull(1L)
      }
    }
  }

  @Nested
  inner class DeleteScheduledMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubDeleteTapScheduleMapping()

      apiService.deleteTapScheduleMapping(1L)

      mappingApi.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubDeleteTapScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteTapScheduleMapping(1L)
      }
    }
  }

  @Nested
  inner class CreateExternalMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTapMovementMapping()

      apiService.createTapMovementMapping(tapMovementMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateTapMovementMapping()

      apiService.createTapMovementMapping(tapMovementMapping())

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
      mappingApi.stubCreateTapMovementMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = TapMovementMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementSeq = 1,
              dpsMovementId = dpsMovementId,
              mappingType = TapMovementMappingDto.MappingType.NOMIS_CREATED,
              "",
              0,
              "",
            ),
            duplicate = TapMovementMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementSeq = 2,
              dpsMovementId = dpsMovementId,
              mappingType = TapMovementMappingDto.MappingType.NOMIS_CREATED,
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

      apiService.createTapMovementMapping(tapMovementMapping())
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing!!.nomisMovementSeq).isEqualTo(1)
          assertThat(errorResponse.moreInfo.duplicate.nomisMovementSeq).isEqualTo(2)
        }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTapMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createTapMovementMapping(tapMovementMapping())
      }
    }
  }

  @Nested
  inner class UpdateExternalMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubUpdateTapMovementMapping()

      apiService.updateTapMovementMapping(tapMovementMapping())

      mappingApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubUpdateTapMovementMapping()

      apiService.updateTapMovementMapping(tapMovementMapping())

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
      mappingApi.stubUpdateTapMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.updateTapMovementMapping(tapMovementMapping())
      }
    }
  }

  @Nested
  inner class GetExternalMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTapMovementMapping()

      apiService.getTapMovementMappingOrNull(12345L, 1)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTapMovementMapping(status = NOT_FOUND)

      apiService.getTapMovementMappingOrNull(12345L, 1)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTapMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapMovementMappingOrNull(12345L, 1)
      }
    }
  }

  @Nested
  inner class DeleteExternalMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubDeleteTapMovementMapping()

      apiService.deleteTapMovementMapping(12345L, 1)

      mappingApi.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubDeleteTapMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteTapMovementMapping(12345L, 1)
      }
    }
  }

  @Nested
  inner class FindScheduledMovementsForAddresses {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubFindScheduledMovementsForAddress()

      apiService.findTapScheduleMappingsForAddress(123L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return list of schedule mappings`() = runTest {
      mappingApi.stubFindScheduledMovementsForAddress()

      with(apiService.findTapScheduleMappingsForAddress(123L)) {
        assertThat(scheduleMappings).extracting("prisonerNumber").containsExactlyInAnyOrder("A1234AA", "B1234BB")
      }
    }

    @Test
    fun `should handle empty list if none found`() = runTest {
      mappingApi.stubFindScheduledMovementsForAddress(prisoners = listOf())

      with(apiService.findTapScheduleMappingsForAddress(123L)) {
        assertThat(scheduleMappings.size).isEqualTo(0)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubFindScheduledMovementsForAddressError(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.findTapScheduleMappingsForAddress(123L)
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
