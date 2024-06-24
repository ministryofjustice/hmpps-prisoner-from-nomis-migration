package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerAlertMappingsDto
import java.util.UUID

@SpringAPIServiceTest
@Import(AlertsMappingApiService::class, AlertsConfiguration::class, AlertsMappingApiMockServer::class)
class AlertsMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: AlertsMappingApiService

  @Autowired
  private lateinit var alertsMappingApiMockServer: AlertsMappingApiMockServer

  @Nested
  inner class GetByNomisId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsMappingApiMockServer.stubGetByNomisId(bookingId = 1234567, alertSequence = 3)

      apiService.getOrNullByNomisId(bookingId = 1234567, alertSequence = 3)

      alertsMappingApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      alertsMappingApiMockServer.stubGetByNomisId(bookingId = 1234567, alertSequence = 3)

      apiService.getOrNullByNomisId(bookingId = 1234567, alertSequence = 3)

      alertsMappingApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/alerts/nomis-booking-id/1234567/nomis-alert-sequence/3")),
      )
    }

    @Test
    fun `will return dpsAlertId when mapping exists`() = runTest {
      alertsMappingApiMockServer.stubGetByNomisId(
        bookingId = 1234567,
        alertSequence = 3,
        mapping = AlertMappingDto(
          dpsAlertId = "d94c3c31-ecdc-4a0e-b8bb-94e8cb8262bc",
          nomisBookingId = 123456,
          nomisAlertSequence = 1,
          offenderNo = "A1234KT",
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getOrNullByNomisId(bookingId = 1234567, alertSequence = 3)

      assertThat(mapping?.dpsAlertId).isEqualTo("d94c3c31-ecdc-4a0e-b8bb-94e8cb8262bc")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      alertsMappingApiMockServer.stubGetByNomisId(NOT_FOUND)

      assertThat(apiService.getOrNullByNomisId(bookingId = 1234567, alertSequence = 3)).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      alertsMappingApiMockServer.stubGetByNomisId(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getOrNullByNomisId(bookingId = 1234567, alertSequence = 4)
      }
    }
  }

  @Nested
  inner class DeleteMapping {
    private val dpsAlertId = UUID.randomUUID().toString()

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsMappingApiMockServer.stubDeleteMapping()

      apiService.deleteMappingByDpsId(dpsAlertId)

      alertsMappingApiMockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to service`() = runTest {
      val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      alertsMappingApiMockServer.stubDeleteMapping()

      apiService.deleteMappingByDpsId(dpsAlertId)

      alertsMappingApiMockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/alerts/dps-alert-id/$dpsAlertId")),
      )
    }
  }

  @Nested
  inner class PostMapping {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsMappingApiMockServer.stubPostMapping()

      apiService.createMapping(
        AlertMappingDto(
          nomisBookingId = 123456,
          nomisAlertSequence = 1,
          dpsAlertId = UUID.randomUUID().toString(),
          offenderNo = "A1234KT",
          mappingType = NOMIS_CREATED,
        ),
      )

      alertsMappingApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to service`() = runTest {
      val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      alertsMappingApiMockServer.stubPostMapping()

      apiService.createMapping(
        AlertMappingDto(
          nomisBookingId = 123456,
          nomisAlertSequence = 1,
          dpsAlertId = dpsAlertId,
          offenderNo = "A1234KT",
          mappingType = NOMIS_CREATED,
        ),
      )

      alertsMappingApiMockServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("nomisBookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("nomisAlertSequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsAlertId", equalTo(dpsAlertId)))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
      )
    }
  }

  @Nested
  inner class PostMappingsBatch {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsMappingApiMockServer.stubPostBatchMappings()

      apiService.createMappingsBatch(
        listOf(
          AlertMappingDto(
            nomisBookingId = 123456,
            nomisAlertSequence = 1,
            dpsAlertId = UUID.randomUUID().toString(),
            offenderNo = "A1234KT",
            mappingType = NOMIS_CREATED,
          ),
        ),
      )

      alertsMappingApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to service`() = runTest {
      val dpsAlertId1 = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      val dpsAlertId2 = "cb3bf3b9-c23c-4787-9450-59259ab62b06"
      alertsMappingApiMockServer.stubPostBatchMappings()

      apiService.createMappingsBatch(
        listOf(
          AlertMappingDto(
            nomisBookingId = 123456,
            nomisAlertSequence = 1,
            dpsAlertId = dpsAlertId1,
            offenderNo = "A1234KT",
            mappingType = NOMIS_CREATED,
          ),
          AlertMappingDto(
            nomisBookingId = 123456,
            nomisAlertSequence = 2,
            dpsAlertId = dpsAlertId2,
            offenderNo = "A1234KT",
            mappingType = NOMIS_CREATED,
          ),
        ),
      )

      alertsMappingApiMockServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("$[0].nomisBookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("$[0].nomisAlertSequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("$[0].dpsAlertId", equalTo(dpsAlertId1)))
          .withRequestBody(matchingJsonPath("$[0].mappingType", equalTo("NOMIS_CREATED")))
          .withRequestBody(matchingJsonPath("$[1].nomisBookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("$[1].nomisAlertSequence", equalTo("2")))
          .withRequestBody(matchingJsonPath("$[1].dpsAlertId", equalTo(dpsAlertId2)))
          .withRequestBody(matchingJsonPath("$[1].mappingType", equalTo("NOMIS_CREATED"))),
      )
    }

    @Test
    fun `will return success when no errors`() = runTest {
      alertsMappingApiMockServer.stubPostBatchMappings()

      val result = apiService.createMappingsBatch(
        listOf(
          AlertMappingDto(
            nomisBookingId = 123456,
            nomisAlertSequence = 1,
            dpsAlertId = UUID.randomUUID().toString(),
            offenderNo = "A1234KT",
            mappingType = NOMIS_CREATED,
          ),
        ),
      )
      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val bookingId = 1234567890L
      val dpsAlertId = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      val existingAlertId = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      alertsMappingApiMockServer.stubPostBatchMappings(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = AlertMappingDto(
              dpsAlertId = dpsAlertId.toString(),
              nomisBookingId = bookingId,
              nomisAlertSequence = 1,
              offenderNo = "A1234KT",
              mappingType = NOMIS_CREATED,
            ),
            existing = AlertMappingDto(
              dpsAlertId = existingAlertId.toString(),
              nomisBookingId = bookingId,
              nomisAlertSequence = 1,
              offenderNo = "A1234KT",
              mappingType = NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMappingsBatch(
        listOf(
          AlertMappingDto(
            nomisBookingId = 123456,
            nomisAlertSequence = 1,
            dpsAlertId = UUID.randomUUID().toString(),
            offenderNo = "A1234KT",
            mappingType = NOMIS_CREATED,
          ),
        ),
      )
      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsAlertId).isEqualTo(dpsAlertId.toString())
      assertThat(result.errorResponse!!.moreInfo.existing.dpsAlertId).isEqualTo(existingAlertId.toString())
    }
  }

  @Nested
  inner class UpdateNomisMappingId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsMappingApiMockServer.stubUpdateByNomisId(previousBookingId = 5000, alertSequence = 3)

      apiService.updateNomisMappingId(previousBookingId = 5000, alertSequence = 3, newBookingId = 123456)

      alertsMappingApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to service`() = runTest {
      alertsMappingApiMockServer.stubUpdateByNomisId(previousBookingId = 5000, alertSequence = 3)

      apiService.updateNomisMappingId(previousBookingId = 5000, alertSequence = 3, newBookingId = 123456)

      alertsMappingApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/mapping/alerts/nomis-booking-id/5000/nomis-alert-sequence/3"))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("123456"))),
      )
    }
  }

  @Nested
  inner class ReplaceMappings {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsMappingApiMockServer.stubReplaceMappings("A1234KT")

      apiService.replaceMappings(
        offenderNo = "A1234KT",
        PrisonerAlertMappingsDto(
          mappingType = PrisonerAlertMappingsDto.MappingType.NOMIS_CREATED,
          mappings = listOf(
            AlertMappingIdDto(
              nomisBookingId = 123456,
              nomisAlertSequence = 1,
              dpsAlertId = UUID.randomUUID().toString(),
            ),
          ),
        ),
      )

      alertsMappingApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to service`() = runTest {
      val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      alertsMappingApiMockServer.stubReplaceMappings("A1234KT")

      apiService.replaceMappings(
        offenderNo = "A1234KT",
        PrisonerAlertMappingsDto(
          mappingType = PrisonerAlertMappingsDto.MappingType.NOMIS_CREATED,
          mappings = listOf(
            AlertMappingIdDto(
              nomisBookingId = 123456,
              nomisAlertSequence = 1,
              dpsAlertId = dpsAlertId,
            ),
          ),
        ),
      )

      alertsMappingApiMockServer.verify(
        putRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("mappings[0].nomisBookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("mappings[0].nomisAlertSequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("mappings[0].dpsAlertId", equalTo(dpsAlertId)))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
      )
    }
  }
}
