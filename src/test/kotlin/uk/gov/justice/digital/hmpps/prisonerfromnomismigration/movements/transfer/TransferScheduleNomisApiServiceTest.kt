package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

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
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleNomisApiMockServer.Companion.transferScheduleWaitlistResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(TransferScheduleNomisApiService::class, TransferScheduleNomisApiMockServer::class)
class TransferScheduleNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: TransferScheduleNomisApiService

  @Autowired
  private lateinit var transferScheduleNomisApiMockServer: TransferScheduleNomisApiMockServer

  private val now = LocalDateTime.now()
  private val yesterday = now.minusDays(1)

  @Nested
  inner class GetTransferScheduleOut {
    @Test
    internal fun `will pass oauth2 token to service`() = runTest {
      transferScheduleNomisApiMockServer.stubGetTransferScheduleOut(offenderNo = "A1234BC", eventId = 1)

      apiService.getTransferScheduleOut(offenderNo = "A1234BC", eventId = 1)

      transferScheduleNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number and event ID to service`() = runTest {
      transferScheduleNomisApiMockServer.stubGetTransferScheduleOut(offenderNo = "A1234BC", eventId = 1)

      apiService.getTransferScheduleOut(offenderNo = "A1234BC", eventId = 1)

      transferScheduleNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/transfers/schedule/out/1")),
      )
    }

    @Test
    fun `will return transfer schedule out`() = runTest {
      transferScheduleNomisApiMockServer.stubGetTransferScheduleOut(offenderNo = "A1234BC", eventId = 1, waitlist = null)

      apiService.getTransferScheduleOut(offenderNo = "A1234BC", eventId = 1)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(eventId).isEqualTo(1)
          assertThat(eventStatus).isEqualTo("SCH")
          assertThat(eventSubType).isEqualTo("TRN")
          assertThat(fromPrison).isEqualTo("BXI")
          assertThat(toPrison).isEqualTo("LEI")
          assertThat(startTime).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
        }
    }

    @Test
    fun `will return transfer schedule out with a waitlist`() = runTest {
      transferScheduleNomisApiMockServer.stubGetTransferScheduleOut(
        offenderNo = "A1234BC",
        eventId = 1,
        response = TransferScheduleNomisApiMockServer.transferScheduleOutResponse(
          eventId = 1,
          waitlist = transferScheduleWaitlistResponse(),
        ),
      )

      apiService.getTransferScheduleOut(offenderNo = "A1234BC", eventId = 1)
        .apply {
          assertThat(waitlist).isNotNull
          assertThat(waitlist?.status).isEqualTo("PEND")
          assertThat(waitlist?.priority).isEqualTo("3")
          assertThat(waitlist?.approved).isTrue
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      transferScheduleNomisApiMockServer.stubGetTransferScheduleOut(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTransferScheduleOut(offenderNo = "A1234BC", eventId = 1)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      transferScheduleNomisApiMockServer.stubGetTransferScheduleOut(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTransferScheduleOut(offenderNo = "A1234BC", eventId = 1)
      }
    }
  }

  @Nested
  inner class GetTransferMovementOut {
    @Test
    internal fun `will pass oauth2 token to service`() = runTest {
      transferScheduleNomisApiMockServer.stubGetTransferMovementOut(offenderNo = "A1234BC")

      apiService.getTransferMovementOut(offenderNo = "A1234BC", bookingId = 12345L, sequence = 3)

      transferScheduleNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number and movement ID to service`() = runTest {
      transferScheduleNomisApiMockServer.stubGetTransferMovementOut(offenderNo = "A1234BC")

      apiService.getTransferMovementOut(offenderNo = "A1234BC", bookingId = 12345L, sequence = 3)

      transferScheduleNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/transfer/movement/out/12345/3")),
      )
    }

    @Test
    fun `will return transfer movement out`() = runTest {
      transferScheduleNomisApiMockServer.stubGetTransferMovementOut(offenderNo = "A1234BC")

      apiService.getTransferMovementOut(offenderNo = "A1234BC", bookingId = 12345L, sequence = 3)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(eventId).isEqualTo(123)
          assertThat(fromPrison).isEqualTo("BXI")
          assertThat(toPrison).isEqualTo("LEI")
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      transferScheduleNomisApiMockServer.stubGetTransferMovementOut(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTransferMovementOut(offenderNo = "A1234BC", bookingId = 12345L, sequence = 3)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      transferScheduleNomisApiMockServer.stubGetTransferMovementOut(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTransferMovementOut(offenderNo = "A1234BC", bookingId = 12345L, sequence = 3)
      }
    }
  }
}
