package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences")),
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

  @Nested
  inner class GetTemporaryAbsenceApplication {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceApplication(offenderNo = "A1234BC", applicationId = 111)

      apiService.getTemporaryAbsenceApplication(offenderNo = "A1234BC", applicationId = 111)

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number and application ID to service`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceApplication(offenderNo = "A1234BC", applicationId = 111)

      apiService.getTemporaryAbsenceApplication(offenderNo = "A1234BC", applicationId = 111)

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application/111")),
      )
    }

    @Test
    fun `will return temporary absence application`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceApplication(offenderNo = "A1234BC", applicationId = 111)

      apiService.getTemporaryAbsenceApplication(offenderNo = "A1234BC", applicationId = 111)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(movementApplicationId).isEqualTo(111)
          assertThat(eventSubType).isEqualTo("C5")
          assertThat(fromDate).isEqualTo(LocalDate.now())
          assertThat(releaseTime).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES))
          assertThat(toAddressId).isEqualTo(321)
          assertThat(audit.createUsername).isEqualTo("USER")
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceApplication(status = NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTemporaryAbsenceApplication(offenderNo = "A1234BC", applicationId = 111)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceApplication(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTemporaryAbsenceApplication(offenderNo = "A1234BC", applicationId = 111)
      }
    }
  }

  @Nested
  inner class GetTemporaryAbsenceApplicationOutsideMovement {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceApplicationOutsideMovement(offenderNo = "A1234BC", appMultiId = 222)

      apiService.getTemporaryAbsenceApplicationOutsideMovement(offenderNo = "A1234BC", appMultiId = 222)

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number and appMultiId to service`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceApplicationOutsideMovement(offenderNo = "A1234BC", appMultiId = 222)

      apiService.getTemporaryAbsenceApplicationOutsideMovement(offenderNo = "A1234BC", appMultiId = 222)

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/outside-movement/222")),
      )
    }

    @Test
    fun `will return temporary absence application outside movement`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceApplicationOutsideMovement(offenderNo = "A1234BC", appMultiId = 222)

      apiService.getTemporaryAbsenceApplicationOutsideMovement(offenderNo = "A1234BC", appMultiId = 222)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(movementApplicationId).isEqualTo(111)
          assertThat(outsideMovementId).isEqualTo(222)
          assertThat(eventSubType).isEqualTo("C5")
          assertThat(fromDate).isEqualTo(LocalDate.now())
          assertThat(releaseTime).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES))
          assertThat(toAddressId).isEqualTo(321)
          assertThat(audit.createUsername).isEqualTo("USER")
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceApplicationOutsideMovement(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTemporaryAbsenceApplicationOutsideMovement(offenderNo = "A1234BC", appMultiId = 222)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceApplicationOutsideMovement(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTemporaryAbsenceApplicationOutsideMovement(offenderNo = "A1234BC", appMultiId = 222)
      }
    }
  }

  @Nested
  inner class GetTemporaryAbsenceScheduledMovement {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceScheduledMovement(offenderNo = "A1234BC", eventId = 1)

      apiService.getTemporaryAbsenceScheduledMovement(offenderNo = "A1234BC", eventId = 1)

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number and event ID to service`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceScheduledMovement(offenderNo = "A1234BC", eventId = 1)

      apiService.getTemporaryAbsenceScheduledMovement(offenderNo = "A1234BC", eventId = 1)

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence/1")),
      )
    }

    @Test
    fun `will return scheduled temporary absence`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceScheduledMovement(offenderNo = "A1234BC", eventId = 1)

      apiService.getTemporaryAbsenceScheduledMovement(offenderNo = "A1234BC", eventId = 1)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(movementApplicationId).isEqualTo(111)
          assertThat(eventId).isEqualTo(1)
          assertThat(eventSubType).isEqualTo("C5")
          assertThat(eventStatus).isEqualTo("SCH")
          assertThat(eventDate).isEqualTo(LocalDate.now())
          assertThat(startTime).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES))
          assertThat(toAddressId).isEqualTo(321)
          assertThat(audit.createUsername).isEqualTo("USER")
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceScheduledMovement(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTemporaryAbsenceScheduledMovement(offenderNo = "A1234BC", eventId = 1)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceScheduledMovement(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTemporaryAbsenceScheduledMovement(offenderNo = "A1234BC", eventId = 1)
      }
    }
  }

  @Nested
  inner class GetTemporaryAbsenceScheduledReturnMovement {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceScheduledReturnMovement(offenderNo = "A1234BC", eventId = 2)

      apiService.getTemporaryAbsenceScheduledReturnMovement(offenderNo = "A1234BC", eventId = 2)

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number and event ID to service`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceScheduledReturnMovement(offenderNo = "A1234BC", eventId = 2)

      apiService.getTemporaryAbsenceScheduledReturnMovement(offenderNo = "A1234BC", eventId = 2)

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence-return/2")),
      )
    }

    @Test
    fun `will return scheduled temporary absence return`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceScheduledReturnMovement(offenderNo = "A1234BC", eventId = 2)

      apiService.getTemporaryAbsenceScheduledReturnMovement(offenderNo = "A1234BC", eventId = 2)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(movementApplicationId).isEqualTo(111)
          assertThat(eventId).isEqualTo(2)
          assertThat(eventSubType).isEqualTo("C5")
          assertThat(eventStatus).isEqualTo("SCH")
          assertThat(eventDate).isEqualTo(LocalDate.now().plusDays(1))
          assertThat(startTime).isCloseTo(LocalDateTime.now().plusDays(1), within(1, ChronoUnit.MINUTES))
          assertThat(fromAgency).isEqualTo("COURT1")
          assertThat(toPrison).isEqualTo("LEI")
          assertThat(audit.createUsername).isEqualTo("USER")
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceScheduledReturnMovement(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTemporaryAbsenceScheduledReturnMovement(offenderNo = "A1234BC", eventId = 2)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceScheduledReturnMovement(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTemporaryAbsenceScheduledReturnMovement(offenderNo = "A1234BC", eventId = 2)
      }
    }
  }

  @Nested
  inner class GetTemporaryAbsenceMovement {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      apiService.getTemporaryAbsenceMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number, booking ID and movement sequence to service`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      apiService.getTemporaryAbsenceMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence/12345/1")),
      )
    }

    @Test
    fun `will return temporary absence`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      apiService.getTemporaryAbsenceMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(sequence).isEqualTo(1)
          assertThat(movementDate).isEqualTo(LocalDate.now())
          assertThat(movementTime).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES))
          assertThat(movementReason).isEqualTo("C6")
          assertThat(movementApplicationId).isEqualTo(111)
          assertThat(scheduledTemporaryAbsenceId).isEqualTo(1)
          assertThat(arrestAgency).isEqualTo("POL")
          assertThat(escort).isEqualTo("U")
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
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceMovement(
        offenderNo = "A1234BC",
        bookingId = 12345,
        movementSeq = 1,
        movementApplicationId = null,
        scheduledTemporaryAbsenceId = null,
      )

      apiService.getTemporaryAbsenceMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(sequence).isEqualTo(1)
          assertThat(movementApplicationId).isNull()
          assertThat(scheduledTemporaryAbsenceId).isNull()
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceMovement(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTemporaryAbsenceMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceMovement(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTemporaryAbsenceMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
      }
    }
  }

  @Nested
  inner class TemporaryAbsenceReturnMovementTest {
    @Test
    fun `will call the correct endpoint`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceReturnMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      apiService.getTemporaryAbsenceReturnMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      externalMovementsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence-return/12345/1")),
      )
    }

    @Test
    fun `will return temporary absence return`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceReturnMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)

      apiService.getTemporaryAbsenceReturnMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(sequence).isEqualTo(1)
          assertThat(movementDate).isEqualTo(LocalDate.now())
          assertThat(movementTime).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES))
          assertThat(movementReason).isEqualTo("C5")
          assertThat(movementApplicationId).isEqualTo(111)
          assertThat(scheduledTemporaryAbsenceReturnId).isEqualTo(2)
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
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceReturnMovement(
        offenderNo = "A1234BC",
        bookingId = 12345,
        movementSeq = 1,
        movementApplicationId = null,
        scheduledTemporaryAbsenceReturnId = null,
      )

      apiService.getTemporaryAbsenceReturnMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
        .apply {
          assertThat(bookingId).isEqualTo(12345)
          assertThat(sequence).isEqualTo(1)
          assertThat(movementApplicationId).isNull()
          assertThat(scheduledTemporaryAbsenceReturnId).isNull()
        }
    }

    @Test
    fun `will throw error when offender does not exist`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceReturnMovement(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getTemporaryAbsenceReturnMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      externalMovementsNomisApiMockServer.stubGetTemporaryAbsenceReturnMovement(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTemporaryAbsenceReturnMovement(offenderNo = "A1234BC", bookingId = 12345, movementSeq = 1)
      }
    }
  }
}
