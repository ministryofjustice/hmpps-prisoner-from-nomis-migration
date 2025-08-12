package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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

@SpringAPIServiceTest
@Import(ExternalMovementsNomisApiService::class, ExternalMovementsNomisApiMockServer::class)
class ExternalMovementsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: ExternalMovementsNomisApiService

  @Autowired
  private lateinit var externalMovementsNomisApiMockServer: ExternalMovementsNomisApiMockServer

  @Nested
  inner class GetTemporaryAbsences {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsences(offenderNo = "A1234BC")

      apiService.getTemporaryAbsences(offenderNo = "A1234BC")

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsences(offenderNo = "A1234BC")

      apiService.getTemporaryAbsences(offenderNo = "A1234BC")

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234BC/temporary-absences")),
      )
    }

    @Test
    fun `will return temporary absences`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsences(offenderNo = "A1234BC")

      val result = apiService.getTemporaryAbsences(offenderNo = "A1234BC")

      assertThat(result.bookings).hasSize(1)
      assertThat(result.bookings[0].bookingId).isEqualTo(12345)
      assertThat(result.bookings[0].temporaryAbsenceApplications).hasSize(1)
      assertThat(result.bookings[0].temporaryAbsenceApplications[0].absences[0].scheduledTemporaryAbsence?.eventId).isEqualTo(1)
      assertThat(result.bookings[0].temporaryAbsenceApplications[0].absences[0].scheduledTemporaryAbsenceReturn?.eventId).isEqualTo(2)
      assertThat(result.bookings[0].temporaryAbsenceApplications[0].absences[0].temporaryAbsence?.sequence).isEqualTo(3)
      assertThat(result.bookings[0].temporaryAbsenceApplications[0].absences[0].temporaryAbsenceReturn?.sequence).isEqualTo(4)
      assertThat(result.bookings[0].unscheduledTemporaryAbsences[0].sequence).isEqualTo(1)
      assertThat(result.bookings[0].unscheduledTemporaryAbsenceReturns[0].sequence).isEqualTo(2)
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsences(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTemporaryAbsences(offenderNo = "A1234BC")
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsences(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTemporaryAbsences(offenderNo = "A1234BC")
      }
    }
  }
}
