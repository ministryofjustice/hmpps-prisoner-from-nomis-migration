package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@SpringAPIServiceTest
@Import(CourtNomisApiService::class, CourtNomisApiMockServer::class)
class CourtNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CourtNomisApiService

  @Autowired
  private lateinit var courtNomisApiMockServer: CourtNomisApiMockServer

  private val now = LocalDateTime.now()
  private val yesterday = now.minusDays(1)

  @Nested
  inner class GetCourtSchedule {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtNomisApiMockServer.stubGetCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)

      apiService.getCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)

      courtNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number and event ID to service`() = runTest {
      courtNomisApiMockServer.stubGetCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)

      apiService.getCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)

      courtNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/court/schedule/out/1")),
      )
    }

    @Test
    fun `will return tap schedule out`() = runTest {
      courtNomisApiMockServer.stubGetCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)

      apiService.getCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(eventId).isEqualTo(1)
          assertThat(eventStatus).isEqualTo("SCH")
          assertThat(eventDate).isEqualTo(yesterday.toLocalDate())
          assertThat(startTime).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      courtNomisApiMockServer.stubGetCourtScheduleOut(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      courtNomisApiMockServer.stubGetCourtScheduleOut(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)
      }
    }
  }
}
