package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiExtension.Companion.dpsExtMovementsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiMockServer.Companion.migratePrisonerTaps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiMockServer.Companion.moveBookingRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiMockServer.Companion.syncTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiMockServer.Companion.syncTapMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiMockServer.Companion.syncTapOccurrence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import java.time.LocalDate
import java.util.*

@SpringAPIServiceTest
@Import(ExternalMovementsDpsApiService::class, ExternalMovementsConfiguration::class, ExternalMovementsDpsApiMockServer::class)
class ExternalMovementsDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: ExternalMovementsDpsApiService

  @Nested
  inner class SyncTapApplication {
    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsExtMovementsServer.stubSyncTapAuthorisation()

      apiService.syncTapAuthorisation("A1234BC", syncTapAuthorisation())

      dpsExtMovementsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsExtMovementsServer.stubSyncTapAuthorisation()

      apiService.syncTapAuthorisation("A1234BC", syncTapAuthorisation())

      dpsExtMovementsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/A1234BC"))
          .withRequestBody(matchingJsonPath("legacyId", equalTo("1234")))
          .withRequestBody(matchingJsonPath("start", equalTo("${LocalDate.now()}")))
          .withRequestBody(matchingJsonPath("created.by", equalTo("AAA11A"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val dpsId = UUID.randomUUID()
      dpsExtMovementsServer.stubSyncTapAuthorisation(response = SyncResponse(dpsId))

      assertThat(apiService.syncTapAuthorisation("A1234BC", syncTapAuthorisation()).id)
        .isEqualTo(dpsId)
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsExtMovementsServer.stubSyncTapAuthorisationError()

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncTapAuthorisation("A1234BC", syncTapAuthorisation())
      }
    }
  }

  @Nested
  inner class DeleteTapAuthorisation {

    @Test
    internal fun `should pass oath2 token`() = runTest {
      val authorisationId = UUID.randomUUID()
      dpsExtMovementsServer.stubDeleteTapAuthorisation(authorisationId)

      apiService.deleteTapAuthorisation(authorisationId)

      dpsExtMovementsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should call the endpoint`() = runTest {
      val authorisationId = UUID.randomUUID()
      dpsExtMovementsServer.stubDeleteTapAuthorisation(authorisationId)

      apiService.deleteTapAuthorisation(authorisationId)

      dpsExtMovementsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$authorisationId")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      val authorisationId = UUID.randomUUID()
      dpsExtMovementsServer.stubDeleteTapAuthorisationError(authorisationId)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteTapAuthorisation(authorisationId)
      }
    }
  }

  @Nested
  inner class SyncScheduledTemporaryAbsence {
    val parentId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsExtMovementsServer.stubSyncTapOccurrence(parentId)

      apiService.syncTapOccurrence(parentId, syncTapOccurrence())

      dpsExtMovementsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsExtMovementsServer.stubSyncTapOccurrence(parentId)

      apiService.syncTapOccurrence(parentId, syncTapOccurrence())

      dpsExtMovementsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$parentId/occurrences"))
          .withRequestBody(matchingJsonPath("legacyId", equalTo("1234")))
          .withRequestBody(matchingJsonPath("isCancelled", equalTo("false")))
          .withRequestBody(matchingJsonPath("location.description", equalTo("Boots")))
          .withRequestBody(matchingJsonPath("created.by", equalTo("AAA11A"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val dpsId = UUID.randomUUID()
      dpsExtMovementsServer.stubSyncTapOccurrence(parentId, response = SyncResponse(dpsId))

      assertThat(apiService.syncTapOccurrence(parentId, syncTapOccurrence()).id)
        .isEqualTo(dpsId)
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsExtMovementsServer.stubSyncTapOccurrenceError(parentId)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncTapOccurrence(parentId, syncTapOccurrence())
      }
    }
  }

  @Nested
  inner class DeleteTapOccurrence {

    @Test
    internal fun `should pass oath2 token`() = runTest {
      val occurrenceId = UUID.randomUUID()
      dpsExtMovementsServer.stubDeleteTapOccurrence(occurrenceId)

      apiService.deleteTapOccurrence(occurrenceId)

      dpsExtMovementsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should call the endpoint`() = runTest {
      val occurrenceId = UUID.randomUUID()
      dpsExtMovementsServer.stubDeleteTapOccurrence(occurrenceId)

      apiService.deleteTapOccurrence(occurrenceId)

      dpsExtMovementsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/temporary-absence-occurrences/$occurrenceId")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      val occurrenceId = UUID.randomUUID()
      dpsExtMovementsServer.stubDeleteTapOccurrenceError(occurrenceId)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteTapOccurrence(occurrenceId)
      }
    }
  }

  @Nested
  inner class SyncTemporaryAbsenceMovement {
    val prisonerNumber = "A1234BC"
    val occurrenceId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsExtMovementsServer.stubSyncTapMovement()

      apiService.syncTapMovement(prisonerNumber, syncTapMovement(occurrenceId))

      dpsExtMovementsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsExtMovementsServer.stubSyncTapMovement()

      apiService.syncTapMovement(prisonerNumber, syncTapMovement(occurrenceId))

      dpsExtMovementsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/$prisonerNumber"))
          .withRequestBody(matchingJsonPath("occurrenceId", equalTo("$occurrenceId")))
          .withRequestBody(matchingJsonPath("legacyId", equalTo("12345_6")))
          .withRequestBody(matchingJsonPath("location.postcode", equalTo("S1 1AA"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val dpsId = UUID.randomUUID()
      dpsExtMovementsServer.stubSyncTapMovement(response = SyncResponse(dpsId))

      assertThat(apiService.syncTapMovement(prisonerNumber, syncTapMovement(occurrenceId)).id)
        .isEqualTo(dpsId)
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsExtMovementsServer.stubSyncTapMovementError()

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncTapMovement(prisonerNumber, syncTapMovement(occurrenceId))
      }
    }
  }

  @Nested
  inner class DeleteTapMovement {

    @Test
    internal fun `should pass oath2 token`() = runTest {
      val movementId = UUID.randomUUID()
      dpsExtMovementsServer.stubDeleteTapMovement(movementId)

      apiService.deleteTapMovement(movementId)

      dpsExtMovementsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should call the endpoint`() = runTest {
      val movementId = UUID.randomUUID()
      dpsExtMovementsServer.stubDeleteTapMovement(movementId)

      apiService.deleteTapMovement(movementId)

      dpsExtMovementsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/$movementId")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      val movementId = UUID.randomUUID()
      dpsExtMovementsServer.stubDeleteTapMovementError(movementId)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteTapMovement(movementId)
      }
    }
  }

  @Nested
  inner class MigratePrisonerTaps {
    val prisonerNumber = "A1234BC"

    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsExtMovementsServer.stubMigratePrisonerTaps()

      apiService.migratePrisonerTaps(prisonerNumber, migratePrisonerTaps())

      dpsExtMovementsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsExtMovementsServer.stubMigratePrisonerTaps()

      apiService.migratePrisonerTaps(prisonerNumber, migratePrisonerTaps())

      dpsExtMovementsServer.verify(
        putRequestedFor(urlPathEqualTo("/migrate/temporary-absences/$prisonerNumber"))
          .withRequestBody(matchingJsonPath("temporaryAbsences[0].prisonCode", equalTo("LEI")))
          .withRequestBody(matchingJsonPath("temporaryAbsences[0].occurrences[0].location.description", equalTo("Boots")))
          .withRequestBody(matchingJsonPath("temporaryAbsences[0].occurrences[0].movements[0].direction", equalTo("OUT")))
          .withRequestBody(matchingJsonPath("unscheduledMovements[0].direction", equalTo("OUT"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsExtMovementsServer.stubMigratePrisonerTaps()

      apiService.migratePrisonerTaps(prisonerNumber, migratePrisonerTaps())
        .apply {
          assertThat(temporaryAbsences.size).isEqualTo(1)
          assertThat(temporaryAbsences[0].legacyId).isEqualTo(1)
          assertThat(temporaryAbsences[0].occurrences.size).isEqualTo(1)
          assertThat(temporaryAbsences[0].occurrences[0].movements.size).isEqualTo(2)
          assertThat(temporaryAbsences[0].occurrences[0].movements[0].legacyId).isEqualTo("12345_3")
        }
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsExtMovementsServer.stubMigratePrisonerTapsError()

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.migratePrisonerTaps(prisonerNumber, migratePrisonerTaps())
      }
    }
  }

  @Nested
  inner class MoveBooking {
    val request = moveBookingRequest()

    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsExtMovementsServer.stubMoveBooking()

      apiService.moveBooking(request)

      dpsExtMovementsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the move endpoint`() = runTest {
      dpsExtMovementsServer.stubMoveBooking()

      apiService.moveBooking(request)

      dpsExtMovementsServer.verify(
        putRequestedFor(urlPathEqualTo("/move/temporary-absences"))
          .withRequestBody(matchingJsonPath("fromPersonIdentifier", equalTo(request.fromPersonIdentifier)))
          .withRequestBody(matchingJsonPath("toPersonIdentifier", equalTo(request.toPersonIdentifier)))
          .withRequestBody(matchingJsonPath("authorisationIds.size()", equalTo("1")))
          .withRequestBody(matchingJsonPath("authorisationIds[0]", equalTo("${request.authorisationIds.first()}")))
          .withRequestBody(matchingJsonPath("unscheduledMovementIds.size()", equalTo("1")))
          .withRequestBody(matchingJsonPath("unscheduledMovementIds[0]", equalTo("${request.unscheduledMovementIds.first()}"))),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsExtMovementsServer.stubMoveBookingError()

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.moveBooking(request)
      }
    }
  }
}
