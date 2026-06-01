package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

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
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapDpsApiExtension.Companion.dpsTapsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapDpsApiMockServer.Companion.moveBookingRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapDpsApiMockServer.Companion.syncTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapDpsApiMockServer.Companion.syncTapMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapDpsApiMockServer.Companion.syncTapOccurrence
import java.time.LocalDate
import java.util.*

@ExtendWith(TapDpsApiExtension::class)
@SpringAPIServiceTest
@Import(TapDpsApiService::class, TapConfiguration::class, TapDpsApiMockServer::class)
class TapDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: TapDpsApiService

  @Nested
  inner class SyncTapApplication {
    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsTapsServer.stubSyncTapAuthorisation()

      apiService.syncTapAuthorisation("A1234BC", syncTapAuthorisation())

      dpsTapsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsTapsServer.stubSyncTapAuthorisation()

      apiService.syncTapAuthorisation("A1234BC", syncTapAuthorisation())

      dpsTapsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/A1234BC"))
          .withRequestBody(matchingJsonPath("legacyId", equalTo("1234")))
          .withRequestBody(matchingJsonPath("start", equalTo("${LocalDate.now()}")))
          .withRequestBody(matchingJsonPath("created.by", equalTo("AAA11A"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val dpsId = UUID.randomUUID()
      dpsTapsServer.stubSyncTapAuthorisation(
        response = SyncResponse(
          dpsId,
        ),
      )

      assertThat(
        apiService.syncTapAuthorisation(
          "A1234BC",
          syncTapAuthorisation(),
        ).id,
      )
        .isEqualTo(dpsId)
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsTapsServer.stubSyncTapAuthorisationError()

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
      dpsTapsServer.stubDeleteTapAuthorisation(authorisationId)

      apiService.deleteTapAuthorisation(authorisationId)

      dpsTapsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should call the endpoint`() = runTest {
      val authorisationId = UUID.randomUUID()
      dpsTapsServer.stubDeleteTapAuthorisation(authorisationId)

      apiService.deleteTapAuthorisation(authorisationId)

      dpsTapsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$authorisationId")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      val authorisationId = UUID.randomUUID()
      dpsTapsServer.stubDeleteTapAuthorisationError(authorisationId)

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
      dpsTapsServer.stubSyncTapOccurrence(parentId)

      apiService.syncTapOccurrence(parentId, syncTapOccurrence())

      dpsTapsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsTapsServer.stubSyncTapOccurrence(parentId)

      apiService.syncTapOccurrence(parentId, syncTapOccurrence())

      dpsTapsServer.verify(
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
      dpsTapsServer.stubSyncTapOccurrence(
        parentId,
        response = SyncResponse(dpsId),
      )

      assertThat(
        apiService.syncTapOccurrence(
          parentId,
          syncTapOccurrence(),
        ).id,
      )
        .isEqualTo(dpsId)
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsTapsServer.stubSyncTapOccurrenceError(parentId)

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
      dpsTapsServer.stubDeleteTapOccurrence(occurrenceId)

      apiService.deleteTapOccurrence(occurrenceId)

      dpsTapsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should call the endpoint`() = runTest {
      val occurrenceId = UUID.randomUUID()
      dpsTapsServer.stubDeleteTapOccurrence(occurrenceId)

      apiService.deleteTapOccurrence(occurrenceId)

      dpsTapsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/temporary-absence-occurrences/$occurrenceId")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      val occurrenceId = UUID.randomUUID()
      dpsTapsServer.stubDeleteTapOccurrenceError(occurrenceId)

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
      dpsTapsServer.stubSyncTapMovement()

      apiService.syncTapMovement(
        prisonerNumber,
        syncTapMovement(occurrenceId),
      )

      dpsTapsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsTapsServer.stubSyncTapMovement()

      apiService.syncTapMovement(
        prisonerNumber,
        syncTapMovement(occurrenceId),
      )

      dpsTapsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/$prisonerNumber"))
          .withRequestBody(matchingJsonPath("occurrenceId", equalTo("$occurrenceId")))
          .withRequestBody(matchingJsonPath("legacyId", equalTo("12345_6")))
          .withRequestBody(matchingJsonPath("location.postcode", equalTo("S1 1AA"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val dpsId = UUID.randomUUID()
      dpsTapsServer.stubSyncTapMovement(response = SyncResponse(dpsId))

      assertThat(
        apiService.syncTapMovement(
          prisonerNumber,
          syncTapMovement(occurrenceId),
        ).id,
      )
        .isEqualTo(dpsId)
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsTapsServer.stubSyncTapMovementError()

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncTapMovement(
          prisonerNumber,
          syncTapMovement(occurrenceId),
        )
      }
    }
  }

  @Nested
  inner class DeleteTapMovement {

    @Test
    internal fun `should pass oath2 token`() = runTest {
      val movementId = UUID.randomUUID()
      dpsTapsServer.stubDeleteTapMovement(movementId)

      apiService.deleteTapMovement(movementId)

      dpsTapsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should call the endpoint`() = runTest {
      val movementId = UUID.randomUUID()
      dpsTapsServer.stubDeleteTapMovement(movementId)

      apiService.deleteTapMovement(movementId)

      dpsTapsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/temporary-absence-movements/$movementId")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      val movementId = UUID.randomUUID()
      dpsTapsServer.stubDeleteTapMovementError(movementId)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteTapMovement(movementId)
      }
    }
  }

  @Nested
  inner class MoveBooking {
    val request = moveBookingRequest()

    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsTapsServer.stubMoveBooking()

      apiService.moveBooking(request)

      dpsTapsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the move endpoint`() = runTest {
      dpsTapsServer.stubMoveBooking()

      apiService.moveBooking(request)

      dpsTapsServer.verify(
        putRequestedFor(urlPathEqualTo("/move/temporary-absences"))
          .withRequestBody(
            matchingJsonPath(
              "fromPersonIdentifier",
              equalTo(request.fromPersonIdentifier),
            ),
          )
          .withRequestBody(
            matchingJsonPath(
              "toPersonIdentifier",
              equalTo(request.toPersonIdentifier),
            ),
          )
          .withRequestBody(matchingJsonPath("authorisationIds.size()", equalTo("1")))
          .withRequestBody(
            matchingJsonPath(
              "authorisationIds[0]",
              equalTo("${request.authorisationIds.first()}"),
            ),
          )
          .withRequestBody(matchingJsonPath("unscheduledMovementIds.size()", equalTo("1")))
          .withRequestBody(
            matchingJsonPath(
              "unscheduledMovementIds[0]",
              equalTo("${request.unscheduledMovementIds.first()}"),
            ),
          ),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsTapsServer.stubMoveBookingError()

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.moveBooking(request)
      }
    }
  }

  @Nested
  inner class Resync {
    val request = MigrateTapRequest(listOf(), listOf())

    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsTapsServer.stubResyncPrisonerTaps()

      apiService.resyncPrisonerTaps("A1234BC", request)

      dpsTapsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the move endpoint`() = runTest {
      dpsTapsServer.stubResyncPrisonerTaps()

      apiService.resyncPrisonerTaps("A1234BC", request)

      dpsTapsServer.verify(
        putRequestedFor(urlPathEqualTo("/resync/temporary-absences/A1234BC")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsTapsServer.stubResyncPrisonerTapsError()

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.resyncPrisonerTaps("A1234BC", request)
      }
    }

    @Test
    fun `should return null if not found`() = runTest {
      dpsTapsServer.stubResyncPrisonerTapsError(status = 404)

      assertThat(apiService.resyncPrisonerTaps("A1234BC", request)).isNull()
    }
  }
}
