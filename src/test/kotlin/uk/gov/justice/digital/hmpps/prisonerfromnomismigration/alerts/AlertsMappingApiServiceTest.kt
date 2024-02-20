package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.DPS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.MIGRATED
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
  inner class PostMapping {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsMappingApiMockServer.stubPostMapping()

      apiService.createMapping(
        AlertMappingDto(
          nomisBookingId = 123456,
          nomisAlertSequence = 1,
          dpsAlertId = UUID.randomUUID().toString(),
          mappingType = DPS_CREATED,
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
          mappingType = DPS_CREATED,
        ),
      )

      alertsMappingApiMockServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("nomisBookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("nomisAlertSequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsAlertId", equalTo(dpsAlertId)))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("DPS_CREATED"))),
      )
    }
  }
}
