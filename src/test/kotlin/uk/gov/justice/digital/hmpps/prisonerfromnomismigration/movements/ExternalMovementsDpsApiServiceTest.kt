package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiMockServer.Companion.syncScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiMockServer.Companion.syncTapApplicationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiMockServer.Companion.syncTemporaryAbsenceMovementRequest
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
      dpsExtMovementsServer.stubSyncTapApplication()

      apiService.syncTemporaryAbsenceApplication("A1234BC", syncTapApplicationRequest())

      dpsExtMovementsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsExtMovementsServer.stubSyncTapApplication()

      apiService.syncTemporaryAbsenceApplication("A1234BC", syncTapApplicationRequest())

      dpsExtMovementsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/temporary-absence-application/A1234BC"))
          .withRequestBody(matchingJsonPath("movementApplicationId", equalTo("1234")))
          .withRequestBody(matchingJsonPath("applicationDate", equalTo("${LocalDate.now()}")))
          .withRequestBody(matchingJsonPath("audit.createUsername", equalTo("AAA11A"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val dpsId = UUID.randomUUID()
      dpsExtMovementsServer.stubSyncTapApplication(response = SyncResponse(dpsId))

      assertThat(apiService.syncTemporaryAbsenceApplication("A1234BC", syncTapApplicationRequest()).id)
        .isEqualTo(dpsId)
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsExtMovementsServer.stubSyncTapApplicationError()

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncTemporaryAbsenceApplication("A1234BC", syncTapApplicationRequest())
      }
    }
  }

  @Nested
  inner class SyncScheduledTemporaryAbsence {
    val parentId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsExtMovementsServer.stubSyncScheduledTemporaryAbsence(parentId)

      apiService.syncTemporaryAbsenceScheduledMovement(parentId, syncScheduledTemporaryAbsenceRequest())

      dpsExtMovementsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsExtMovementsServer.stubSyncScheduledTemporaryAbsence(parentId)

      apiService.syncTemporaryAbsenceScheduledMovement(parentId, syncScheduledTemporaryAbsenceRequest())

      dpsExtMovementsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/scheduled-temporary-absence/$parentId"))
          .withRequestBody(matchingJsonPath("eventId", equalTo("1234")))
          .withRequestBody(matchingJsonPath("eventStatus", equalTo("SCH")))
          .withRequestBody(matchingJsonPath("audit.createUsername", equalTo("AAA11A"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val dpsId = UUID.randomUUID()
      dpsExtMovementsServer.stubSyncScheduledTemporaryAbsence(parentId, response = SyncResponse(dpsId))

      assertThat(apiService.syncTemporaryAbsenceScheduledMovement(parentId, syncScheduledTemporaryAbsenceRequest()).id)
        .isEqualTo(dpsId)
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsExtMovementsServer.stubSyncScheduledTemporaryAbsenceError(parentId)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncTemporaryAbsenceScheduledMovement(parentId, syncScheduledTemporaryAbsenceRequest())
      }
    }
  }

  @Nested
  inner class SyncTemporaryAbsenceMovement {
    val prisonerNumber = "A1234BC"
    val occurrenceId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsExtMovementsServer.stubSyncTemporaryAbsenceMovement()

      apiService.syncTemporaryAbsenceMovement(prisonerNumber, syncTemporaryAbsenceMovementRequest(occurrenceId))

      dpsExtMovementsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsExtMovementsServer.stubSyncTemporaryAbsenceMovement(prisonerNumber)

      apiService.syncTemporaryAbsenceMovement(prisonerNumber, syncTemporaryAbsenceMovementRequest(occurrenceId))

      dpsExtMovementsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/temporary-absence-movement/$prisonerNumber"))
          .withRequestBody(matchingJsonPath("occurrenceId", equalTo("$occurrenceId")))
          .withRequestBody(matchingJsonPath("legacyId", equalTo("12345_154")))
          .withRequestBody(matchingJsonPath("location.id", equalTo("654")))
          .withRequestBody(matchingJsonPath("location.address.premise", equalTo("Some premise"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val dpsId = UUID.randomUUID()
      dpsExtMovementsServer.stubSyncTemporaryAbsenceMovement(prisonerNumber, response = SyncResponse(dpsId))

      assertThat(apiService.syncTemporaryAbsenceMovement(prisonerNumber, syncTemporaryAbsenceMovementRequest(occurrenceId)).id)
        .isEqualTo(dpsId)
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsExtMovementsServer.stubSyncTemporaryAbsenceMovementError(prisonerNumber)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncTemporaryAbsenceMovement(prisonerNumber, syncTemporaryAbsenceMovementRequest(occurrenceId))
      }
    }
  }
}
