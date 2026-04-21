package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

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
@Import(TapsNomisApiService::class, TapNomisApiMockServer::class)
class TapNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: TapsNomisApiService

  @Autowired
  private lateinit var tapNomisApiMockServer: TapNomisApiMockServer

  private val now = LocalDateTime.now()
  private val today = now.toLocalDate()
  private val yesterday = now.minusDays(1)

  @Nested
  inner class GetOffenderAllTapsTest {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      tapNomisApiMockServer.stubGetAllOffenderTaps(offenderNo = "A1234BC")

      apiService.getAllOffenderTapsOrNull(offenderNo = "A1234BC")

      tapNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      tapNomisApiMockServer.stubGetAllOffenderTaps(offenderNo = "A1234BC")

      apiService.getAllOffenderTapsOrNull(offenderNo = "A1234BC")

      tapNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps")),
      )
    }

    @Test
    fun `will return temporary absences`() = runTest {
      tapNomisApiMockServer.stubGetAllOffenderTaps(offenderNo = "A1234BC")

      val result = apiService.getAllOffenderTapsOrNull(offenderNo = "A1234BC")!!

      assertThat(result.bookings).hasSize(1)
      assertThat(result.bookings[0].bookingId).isEqualTo(12345)
      assertThat(result.bookings[0].tapApplications).hasSize(1)
      assertThat(result.bookings[0].tapApplications[0].taps[0].tapScheduleOut?.eventId).isEqualTo(1)
      assertThat(result.bookings[0].tapApplications[0].taps[0].tapScheduleIn?.eventId).isEqualTo(2)
      assertThat(result.bookings[0].tapApplications[0].taps[0].tapMovementOut?.sequence).isEqualTo(3)
      assertThat(result.bookings[0].tapApplications[0].taps[0].tapMovementIn?.sequence).isEqualTo(4)
      assertThat(result.bookings[0].unscheduledTapMovementOuts[0].sequence).isEqualTo(1)
      assertThat(result.bookings[0].unscheduledTapMovementIns[0].sequence).isEqualTo(2)
    }

    @Test
    fun `will return null when offender does not exist`() = runTest {
      tapNomisApiMockServer.stubGetAllOffenderTaps(NOT_FOUND)

      assertThat(apiService.getAllOffenderTapsOrNull(offenderNo = "A1234BC")).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      tapNomisApiMockServer.stubGetAllOffenderTaps(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getAllOffenderTapsOrNull(offenderNo = "A1234BC")
      }
    }
  }

  @Nested
  inner class GetTemporaryAbsenceApplication {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      tapNomisApiMockServer.stubGetTapApplication(offenderNo = "A1234BC", applicationId = 111)

      apiService.getTapApplication(offenderNo = "A1234BC", applicationId = 111)

      tapNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number and application ID to service`() = runTest {
      tapNomisApiMockServer.stubGetTapApplication(offenderNo = "A1234BC", applicationId = 111)

      apiService.getTapApplication(offenderNo = "A1234BC", applicationId = 111)

      tapNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/application/111")),
      )
    }

    @Test
    fun `will return temporary absence application`() = runTest {
      tapNomisApiMockServer.stubGetTapApplication(offenderNo = "A1234BC", applicationId = 111)

      apiService.getTapApplication(offenderNo = "A1234BC", applicationId = 111)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(tapApplicationId).isEqualTo(111)
          assertThat(eventSubType).isEqualTo("C5")
          assertThat(fromDate).isEqualTo(today)
          assertThat(releaseTime).isCloseTo(now, within(5, ChronoUnit.MINUTES))
          assertThat(toAddressId).isEqualTo(321)
          assertThat(audit.createUsername).isEqualTo("USER")
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      tapNomisApiMockServer.stubGetTapApplication(status = NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTapApplication(offenderNo = "A1234BC", applicationId = 111)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      tapNomisApiMockServer.stubGetTapApplication(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapApplication(offenderNo = "A1234BC", applicationId = 111)
      }
    }
  }

  @Nested
  inner class GetTemporaryAbsenceScheduledMovement {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      tapNomisApiMockServer.stubGetTapScheduleOut(offenderNo = "A1234BC", eventId = 1)

      apiService.getTapScheduleOut(offenderNo = "A1234BC", eventId = 1)

      tapNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number and event ID to service`() = runTest {
      tapNomisApiMockServer.stubGetTapScheduleOut(offenderNo = "A1234BC", eventId = 1)

      apiService.getTapScheduleOut(offenderNo = "A1234BC", eventId = 1)

      tapNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/schedule/out/1")),
      )
    }

    @Test
    fun `will return scheduled temporary absence`() = runTest {
      tapNomisApiMockServer.stubGetTapScheduleOut(offenderNo = "A1234BC", eventId = 1)

      apiService.getTapScheduleOut(offenderNo = "A1234BC", eventId = 1)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(tapApplicationId).isEqualTo(111)
          assertThat(eventId).isEqualTo(1)
          assertThat(eventSubType).isEqualTo("C5")
          assertThat(eventStatus).isEqualTo("COMP")
          assertThat(eventDate).isEqualTo(yesterday.toLocalDate())
          assertThat(startTime).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
          assertThat(toAddressId).isEqualTo(321)
          assertThat(audit.createUsername).isEqualTo("USER")
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      tapNomisApiMockServer.stubGetTapScheduleOut(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTapScheduleOut(offenderNo = "A1234BC", eventId = 1)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      tapNomisApiMockServer.stubGetTapScheduleOut(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapScheduleOut(offenderNo = "A1234BC", eventId = 1)
      }
    }
  }

  @Nested
  inner class GetTemporaryAbsenceMovement {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      tapNomisApiMockServer.stubGetTapMovementOut(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      apiService.getTapMovementOut(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      tapNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number, booking ID and movement sequence to service`() = runTest {
      tapNomisApiMockServer.stubGetTapMovementOut(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      apiService.getTapMovementOut(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      tapNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/movement/out/12345/1")),
      )
    }

    @Test
    fun `will return temporary absence`() = runTest {
      tapNomisApiMockServer.stubGetTapMovementOut(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      apiService.getTapMovementOut(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(sequence).isEqualTo(1)
          assertThat(movementDate).isEqualTo(today)
          assertThat(movementTime).isCloseTo(now, within(5, ChronoUnit.MINUTES))
          assertThat(movementReason).isEqualTo("C6")
          assertThat(tapApplicationId).isEqualTo(111)
          assertThat(tapScheduleOutId).isEqualTo(1)
          assertThat(arrestAgency).isEqualTo("POL")
          assertThat(escort).isEqualTo("P")
          assertThat(escortText).isEqualTo("Absence escort text")
          assertThat(fromPrison).isEqualTo("LEI")
          assertThat(toAgency).isEqualTo("COURT1")
          assertThat(commentText).isEqualTo("Absence comment text")
          assertThat(toAddressId).isEqualTo(321)
          assertThat(toAddressOwnerClass).isEqualTo("OFF")
          assertThat(toAddressDescription).isEqualTo("Some description")
          assertThat(toFullAddress).isEqualTo("full address")
          assertThat(audit.createUsername).isEqualTo("USER")
        }
    }

    @Test
    fun `will return unscheduled temporary absence`() = runTest {
      tapNomisApiMockServer.stubGetTapMovementOut(
        offenderNo = "A1234BC",
        bookingId = 12345,
        movementSeq = 1,
        tapApplicationId = null,
        tapScheduleOutId = null,
      )

      apiService.getTapMovementOut(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(sequence).isEqualTo(1)
          assertThat(tapApplicationId).isNull()
          assertThat(tapScheduleOutId).isNull()
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      tapNomisApiMockServer.stubGetTapMovementOut(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTapMovementOut(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      tapNomisApiMockServer.stubGetTapMovementOut(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapMovementOut(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
      }
    }
  }

  @Nested
  inner class TemporaryAbsenceReturnMovementTest {
    @Test
    fun `will call the correct endpoint`() = runTest {
      tapNomisApiMockServer.stubGetTapMovementIn(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      apiService.getTapMovementIn(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      tapNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/movement/in/12345/1")),
      )
    }

    @Test
    fun `will return temporary absence return`() = runTest {
      tapNomisApiMockServer.stubGetTapMovementIn(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      apiService.getTapMovementIn(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(sequence).isEqualTo(1)
          assertThat(movementDate).isEqualTo(today)
          assertThat(movementTime).isCloseTo(now, within(5, ChronoUnit.MINUTES))
          assertThat(movementReason).isEqualTo("C5")
          assertThat(tapApplicationId).isEqualTo(111)
          assertThat(tapScheduleInId).isEqualTo(2)
          assertThat(escort).isEqualTo("PECS")
          assertThat(escortText).isEqualTo("Return escort text")
          assertThat(fromAgency).isEqualTo("COURT1")
          assertThat(toPrison).isEqualTo("LEI")
          assertThat(commentText).isEqualTo("Return comment text")
          assertThat(fromAddressId).isEqualTo(321)
          assertThat(fromAddressOwnerClass).isEqualTo("OFF")
          assertThat(audit.createUsername).isEqualTo("USER")
        }
    }

    @Test
    fun `will return unscheduled temporary absence return`() = runTest {
      tapNomisApiMockServer.stubGetTapMovementIn(
        offenderNo = "A1234BC",
        bookingId = 12345,
        movementSeq = 1,
        tapApplicationId = null,
        tapScheduleMovementInId = null,
      )

      apiService.getTapMovementIn(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(sequence).isEqualTo(1)
          assertThat(tapApplicationId).isNull()
          assertThat(tapScheduleInId).isNull()
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      tapNomisApiMockServer.stubGetTapMovementIn(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTapMovementIn(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      tapNomisApiMockServer.stubGetTapMovementIn(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapMovementIn(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
      }
    }
  }
}
