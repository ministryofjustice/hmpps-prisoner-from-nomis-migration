package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
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
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import java.util.*

@ExtendWith(MappingApiExtension::class)
@SpringAPIServiceTest
@Import(PersonLocationMappingApiService::class, PersonLocationMappingApiMockServer::class, PersonLocationConfiguration::class)
class PersonLocationMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: PersonLocationMappingApiService

  @Autowired
  private lateinit var mappingApi: PersonLocationMappingApiMockServer

  private val errorType = object : ParameterizedTypeReference<DuplicateErrorResponse<PersonLocationsMappingDto>>() {}

  @Nested
  inner class CreateMigrationMappings {
    @Test
    internal fun `should pass oauth2 token to service`() = runTest {
      mappingApi.stubCreatePersonLocationMappings()

      apiService.createMapping(personLocationsMapping(), errorType)

      mappingApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      val dpsCustodialSeriesId = UUID.randomUUID()
      val dpsExternalMovementId = UUID.randomUUID()
      mappingApi.stubCreatePersonLocationMappings()

      apiService.createMapping(
        personLocationsMapping(
          offenderNo = "A1234BC",
          bookingId = 12345,
          dpsCustodialSeriesId = dpsCustodialSeriesId,
          nomisMovementSeq = 3,
          dpsExternalMovementId = dpsExternalMovementId,
        ),
        errorType,
      ).also { assertThat(it.isError).isFalse }

      mappingApi.verify(
        putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate"))
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
      mappingApi.stubCreatePersonLocationMappings(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createMapping(personLocationsMapping(), errorType)
      }
    }
  }

  @Nested
  inner class GetMappings {
    @Test
    internal fun `should pass oauth2 token to service`() = runTest {
      mappingApi.stubGetPersonLocationMappings()

      apiService.getMappings(listOf(12345L))

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass booking ids to service`() = runTest {
      mappingApi.stubGetPersonLocationMappings()

      apiService.getMappings(listOf(12345L, 12346L))

      mappingApi.verify(
        postRequestedFor(urlPathEqualTo("/mapping/person-location/nomis-booking-ids/get-list"))
          .withRequestBody(equalToJson("[12345, 12346]")),
      )
    }

    @Test
    internal fun `should parse response`() = runTest {
      val dpsCustodialSeriesId = UUID.randomUUID()
      val dpsExternalMovementId = UUID.randomUUID()
      val mappings = listOf(
        PersonLocationBookingMappingDto(
          bookingId = 12345L,
          dpsCustodialSeriesId = dpsCustodialSeriesId,
          movements = listOf(PersonLocationMovementMappingDto(nomisMovementSeq = 1, dpsExternalMovementId = dpsExternalMovementId)),
        ),
      )
      mappingApi.stubGetPersonLocationMappings(response = mappings)

      assertThat(apiService.getMappings(listOf(12345L))).isEqualTo(mappings)
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetPersonLocationMappings(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getMappings(listOf(12345L))
      }
    }
  }

  @Nested
  inner class CreateBookingMapping {
    @Test
    internal fun `should pass data to service`() = runTest {
      val dpsId = UUID.randomUUID()
      mappingApi.stubCreatePersonLocationBookingMapping()

      apiService.createBookingMapping(personLocationBookingMapping(nomisBookingId = 12345L, dpsCustodialSeriesId = dpsId)).also { assertThat(it.isError).isFalse }

      mappingApi.verify(
        postRequestedFor(urlPathEqualTo("/mapping/person-location/booking"))
          .withRequestBody(matchingJsonPath("nomisBookingId", equalTo("12345"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsId = UUID.randomUUID()
      mappingApi.stubCreatePersonLocationBookingMapping(
        status = CONFLICT,
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = personLocationBookingMapping(nomisBookingId = 12345L, dpsCustodialSeriesId = dpsId),
            duplicate = personLocationBookingMapping(nomisBookingId = 12346L, dpsCustodialSeriesId = dpsId),
          ),
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          errorCode = 1409,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createBookingMapping(personLocationBookingMapping())
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing).isNotNull
          assertThat(errorResponse.moreInfo.duplicate).isNotNull
        }
    }
  }

  @Nested
  inner class CreateMovementMapping {
    @Test
    internal fun `should pass data to service`() = runTest {
      val dpsId = UUID.randomUUID()
      mappingApi.stubCreatePersonLocationMovementMapping()

      apiService.createMovementMapping(personLocationMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, dpsExternalMovementId = dpsId)).also { assertThat(it.isError).isFalse }

      mappingApi.verify(
        postRequestedFor(urlPathEqualTo("/mapping/person-location/movement"))
          .withRequestBody(matchingJsonPath("nomisBookingId", equalTo("12345"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsId = UUID.randomUUID()
      mappingApi.stubCreatePersonLocationMovementMapping(
        status = CONFLICT,
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = personLocationMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 1, dpsExternalMovementId = dpsId),
            duplicate = personLocationMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 2, dpsExternalMovementId = dpsId),
          ),
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          errorCode = 1409,
          userMessage = "Duplicate mapping",
        ),
      )

      apiService.createMovementMapping(personLocationMovementMapping())
        .apply {
          assertThat(isError).isTrue
          assertThat(errorResponse!!.moreInfo.existing).isNotNull
          assertThat(errorResponse.moreInfo.duplicate).isNotNull
        }
    }
  }

  @Nested
  inner class GetBookingMapping {
    @Test
    internal fun `should return mapping`() = runTest {
      val expectedDpsId = UUID.randomUUID()
      mappingApi.stubGetPersonLocationBookingMapping(
        nomisBookingId = 12345L,
        response = personLocationBookingMapping(nomisBookingId = 12345L, dpsCustodialSeriesId = expectedDpsId),
      )

      assertThat(apiService.getBookingMappingOrNull(12345L)!!.dpsCustodialSeriesId).isEqualTo(expectedDpsId)
    }

    @Test
    internal fun `should return null if not found`() = runTest {
      mappingApi.stubGetPersonLocationBookingMapping(status = NOT_FOUND)

      assertThat(apiService.getBookingMappingOrNull(12345L)).isNull()
    }
  }

  @Nested
  inner class GetMovementMapping {
    @Test
    internal fun `should return mapping`() = runTest {
      val expectedDpsId = UUID.randomUUID()
      mappingApi.stubGetPersonLocationMovementMapping(
        nomisBookingId = 12345L,
        nomisMovementSeq = 3,
        response = personLocationMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, dpsExternalMovementId = expectedDpsId),
      )

      assertThat(apiService.getMovementMappingOrNull(12345L, 3)!!.dpsExternalMovementId).isEqualTo(expectedDpsId)
    }

    @Test
    internal fun `should return null if not found`() = runTest {
      mappingApi.stubGetPersonLocationMovementMapping(nomisMovementSeq = 3, status = NOT_FOUND)

      assertThat(apiService.getMovementMappingOrNull(12345L, 3)).isNull()
    }
  }

  @Nested
  inner class DeleteBookingMapping {
    @Test
    internal fun `should pass ids to URL`() = runTest {
      mappingApi.stubDeletePersonLocationBookingMapping(nomisBookingId = 23456L)

      apiService.deleteBookingMapping(23456L)

      mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/person-location/booking/nomis-booking-id/23456")))
    }
  }

  @Nested
  inner class DeleteMovementMapping {
    @Test
    internal fun `should pass ids to URL`() = runTest {
      mappingApi.stubDeletePersonLocationMovementMapping(nomisBookingId = 23456L, nomisMovementSeq = 3)

      apiService.deleteMovementMapping(23456L, 3)

      mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/person-location/movement/nomis-booking-id/23456/nomis-movement-seq/3")))
    }
  }
}
