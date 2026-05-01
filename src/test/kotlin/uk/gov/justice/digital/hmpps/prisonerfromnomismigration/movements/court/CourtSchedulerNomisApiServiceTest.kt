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
@Import(CourtSchedulerNomisApiService::class, CourtSchedulerNomisApiMockServer::class)
class CourtSchedulerNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CourtSchedulerNomisApiService

  @Autowired
  private lateinit var courtSchedulerNomisApiMockServer: CourtSchedulerNomisApiMockServer

  private val now = LocalDateTime.now()
  private val yesterday = now.minusDays(1)

  @Nested
  inner class GetCourtSchedule {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)

      apiService.getCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)

      courtSchedulerNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number and event ID to service`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)

      apiService.getCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)

      courtSchedulerNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/court/schedule/out/1")),
      )
    }

    @Test
    fun `will return tap schedule out`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)

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
      courtSchedulerNomisApiMockServer.stubGetCourtScheduleOut(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtScheduleOut(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getCourtScheduleOut(offenderNo = "A1234BC", eventId = 1)
      }
    }
  }

  @Nested
  inner class GetCourtMovementOut {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtMovementOut(offenderNo = "A1234BC", bookingId = 12345L, movementSeq = 3)

      apiService.getCourtMovementOut(offenderNo = "A1234BC", bookingId = 12345L, movementSequence = 3)

      courtSchedulerNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to the service`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtMovementOut(offenderNo = "A1234BC", bookingId = 12345L, movementSeq = 3)

      apiService.getCourtMovementOut(offenderNo = "A1234BC", bookingId = 12345L, movementSequence = 3)

      courtSchedulerNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/court/movement/out/12345/3")),
      )
    }

    @Test
    fun `will return court movement out`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtMovementOut(offenderNo = "A1234BC", bookingId = 12345L, movementSeq = 3)

      apiService.getCourtMovementOut(offenderNo = "A1234BC", bookingId = 12345L, movementSequence = 3)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(sequence).isEqualTo(3)
          assertThat(movementReason).isEqualTo("CRT")
          assertThat(movementDate).isEqualTo(yesterday.toLocalDate())
          assertThat(movementTime).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtMovementOut(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getCourtMovementOut(offenderNo = "A1234BC", bookingId = 12345L, movementSequence = 3)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtMovementOut(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getCourtMovementOut(offenderNo = "A1234BC", bookingId = 12345L, movementSequence = 3)
      }
    }
  }

  @Nested
  inner class GetCourtMovementIn {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtMovementIn(offenderNo = "A1234BC", bookingId = 12345L, movementSeq = 3)

      apiService.getCourtMovementIn(offenderNo = "A1234BC", bookingId = 12345L, movementSequence = 3)

      courtSchedulerNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to service`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtMovementIn(offenderNo = "A1234BC", bookingId = 12345L, movementSeq = 3)

      apiService.getCourtMovementIn(offenderNo = "A1234BC", bookingId = 12345L, movementSequence = 3)

      courtSchedulerNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/court/movement/in/12345/3")),
      )
    }

    @Test
    fun `will return court movement out`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtMovementIn(offenderNo = "A1234BC", bookingId = 12345L, movementSeq = 3)

      apiService.getCourtMovementIn(offenderNo = "A1234BC", bookingId = 12345L, movementSequence = 3)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(sequence).isEqualTo(3)
          assertThat(movementReason).isEqualTo("CRT")
          assertThat(movementDate).isEqualTo(yesterday.toLocalDate())
          assertThat(movementTime).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtMovementIn(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getCourtMovementIn(offenderNo = "A1234BC", bookingId = 12345L, movementSequence = 3)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      courtSchedulerNomisApiMockServer.stubGetCourtMovementIn(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getCourtMovementIn(offenderNo = "A1234BC", bookingId = 12345L, movementSequence = 3)
      }
    }
  }
}
