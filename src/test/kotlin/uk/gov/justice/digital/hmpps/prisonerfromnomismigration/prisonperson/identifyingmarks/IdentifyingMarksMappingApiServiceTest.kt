package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.identifyingmarks

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
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IdentifyingMarkMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IdentifyingMarkMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonConfiguration
import java.time.LocalDateTime
import java.util.UUID

@SpringAPIServiceTest
@Import(
  IdentifyingMarksMappingApiService::class,
  IdentifyingMarksMappingApiMockServer::class,
  PrisonPersonConfiguration::class,
)
class IdentifyingMarksMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: IdentifyingMarksMappingApiService

  @Autowired
  private lateinit var mappingApi: IdentifyingMarksMappingApiMockServer

  @Nested
  inner class GetByNomisId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mappingApi.stubGetMappingByNomisId(bookingId = 12345L, idMarksSeq = 1L)

      apiService.getByNomisId(bookingId = 1234567, idMarksSeq = 1L)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      mappingApi.stubGetMappingByNomisId(bookingId = 12345L, idMarksSeq = 1L)

      val mapping = apiService.getByNomisId(bookingId = 12345L, idMarksSeq = 1L)

      with(mapping!!) {
        assertThat(nomisBookingId).isEqualTo(12345L)
        assertThat(nomisMarksSequence).isEqualTo(1L)
        assertThat(dpsId).isNotNull()
        assertThat(offenderNo).isEqualTo("A1234AA")
        assertThat(mappingType).isEqualTo(NOMIS_CREATED)
        assertThat(whenCreated).isNotNull()
        assertThat(label).isEqualTo("some_label")
      }
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mappingApi.stubGetMappingByNomisId(NOT_FOUND)

      assertThat(apiService.getByNomisId(bookingId = 12345L, idMarksSeq = 1L)).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      mappingApi.stubGetMappingByNomisId(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getByNomisId(bookingId = 12345L, idMarksSeq = 1L)
      }
    }
  }

  @Nested
  inner class GetByDpsId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      val dpsId = UUID.randomUUID()
      mappingApi.stubGetMappingByDpsId(dpsId = dpsId)

      apiService.getByDpsId(dpsId = dpsId)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass DPS id to service`() = runTest {
      val dpsId = UUID.randomUUID()
      mappingApi.stubGetMappingByDpsId(dpsId = dpsId)

      val mapping = apiService.getByDpsId(dpsId = dpsId)

      with(mapping!!) {
        assertThat(nomisBookingId).isEqualTo(12345L)
        assertThat(nomisMarksSequence).isEqualTo(1L)
        assertThat(dpsId).isEqualTo(dpsId)
        assertThat(offenderNo).isEqualTo("A1234AA")
        assertThat(mappingType).isEqualTo(NOMIS_CREATED)
        assertThat(whenCreated).isNotNull()
        assertThat(label).isEqualTo("some_label")
      }
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mappingApi.stubGetMappingByDpsId(NOT_FOUND)

      assertThat(apiService.getByDpsId(dpsId = UUID.randomUUID())).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      mappingApi.stubGetMappingByDpsId(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getByDpsId(dpsId = UUID.randomUUID())
      }
    }
  }

  @Nested
  inner class CreateMapping {
    private fun aMapping() = IdentifyingMarkMappingDto(
      nomisBookingId = 12345L,
      nomisMarksSequence = 1L,
      dpsId = UUID.randomUUID(),
      offenderNo = "A1234AA",
      mappingType = NOMIS_CREATED,
      whenCreated = "${LocalDateTime.now()}",
      label = "some_label",
    )

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mappingApi.stubCreateMapping()

      apiService.createMapping(aMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass DPS id to service`() = runTest {
      val mapping = aMapping()
      mappingApi.stubCreateMapping()

      apiService.createMapping(mapping)

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("nomisBookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisMarksSequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsId", equalTo(mapping.dpsId.toString())))
          .withRequestBody(matchingJsonPath("offenderNo", equalTo("A1234AA")))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED")))
          .withRequestBody(matchingJsonPath("label", equalTo("some_label"))),
      )
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      mappingApi.stubCreateMapping(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createMapping(aMapping())
      }
    }

    @Test
    fun `will throw error when 409 conflict`() = runTest {
      mappingApi.stubCreateMapping(
        HttpStatus.CONFLICT,
        DuplicateMappingErrorResponse(
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          errorCode = 1409,
          userMessage = "Duplicate mapping",
          moreInfo = DuplicateErrorContentObject(
            existing = aMapping().copy(dpsId = UUID.randomUUID()),
            duplicate = aMapping(),
          ),
        ),
      )

      assertThrows<WebClientResponseException.Conflict> {
        apiService.createMapping(aMapping())
      }
    }
  }
}
