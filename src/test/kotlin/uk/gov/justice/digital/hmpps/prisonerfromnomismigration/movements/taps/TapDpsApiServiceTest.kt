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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapDpsApiMockServer.Companion.syncTapMovement
import java.util.*

@ExtendWith(TapDpsApiExtension::class)
@SpringAPIServiceTest
@Import(TapDpsApiService::class, TapConfiguration::class, TapDpsApiMockServer::class)
class TapDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: TapDpsApiService

  @Nested
  inner class SyncTemporaryAbsenceMovement {
    val prisonerNumber = "A1234BC"
    val occurrenceId = UUID.randomUUID()

    @Test
    internal fun `should pass oauth2 token`() = runTest {
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
    internal fun `should pass oauth2 token`() = runTest {
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
    internal fun `should pass oauth2 token`() = runTest {
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
    internal fun `should pass oauth2 token`() = runTest {
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
