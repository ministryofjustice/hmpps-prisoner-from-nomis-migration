package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncCourtEvents
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.dpsCourtSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiMockServer.Companion.referenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiMockServer.Companion.syncCourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiMockServer.Companion.syncCourtMovement
import java.time.LocalDate
import java.util.*

@SpringAPIServiceTest
@Import(CourtSchedulerDpsApiService::class, CourtSchedulerConfiguration::class, CourtSchedulerDpsApiMockServer::class)
class CourtSchedulerDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: CourtSchedulerDpsApiService

  @Nested
  inner class SyncCourtEvent {

    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsCourtSchedulerServer.stubSyncCourtEvent("A1234BC")

      apiService.syncCourtEvent("A1234BC", syncCourtEvent())

      dpsCourtSchedulerServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsCourtSchedulerServer.stubSyncCourtEvent("A1234BC")

      apiService.syncCourtEvent("A1234BC", syncCourtEvent())

      dpsCourtSchedulerServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC"))
          .withRequestBody(matchingJsonPath("user.username", equalTo("USER")))
          .withRequestBody(matchingJsonPath("courtEvent.prisonCodeAtTimeOfScheduling", equalTo("MDI")))
          .withRequestBody(matchingJsonPath("courtEvent.eventDate", equalTo("${LocalDate.now()}"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val dpsId = UUID.randomUUID()
      dpsCourtSchedulerServer.stubSyncCourtEvent("A1234BC", referenceId(dpsId))

      assertThat(
        apiService.syncCourtEvent("A1234BC", syncCourtEvent()).id,
      )
        .isEqualTo(dpsId)
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsCourtSchedulerServer.stubSyncCourtEventError("A1234BC")

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncCourtEvent("A1234BC", syncCourtEvent())
      }
    }
  }

  @Nested
  inner class DeleteCourtEvent {

    @Test
    internal fun `should pass oath2 token`() = runTest {
      val courtAppearanceId = UUID.randomUUID()
      dpsCourtSchedulerServer.stubDeleteCourtEvent(courtAppearanceId)

      apiService.deleteCourtEvent(courtAppearanceId)

      dpsCourtSchedulerServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should call the endpoint`() = runTest {
      val courtAppearanceId = UUID.randomUUID()
      dpsCourtSchedulerServer.stubDeleteCourtEvent(courtAppearanceId)

      apiService.deleteCourtEvent(courtAppearanceId)

      dpsCourtSchedulerServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/court-appearances/$courtAppearanceId")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      val courtAppearanceId = UUID.randomUUID()
      dpsCourtSchedulerServer.stubDeleteCourtEventError(courtAppearanceId)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteCourtEvent(courtAppearanceId)
      }
    }
  }

  @Nested
  inner class SyncCourtMovement {

    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsCourtSchedulerServer.stubSyncCourtMovement("A1234BC")

      apiService.syncCourtMovement("A1234BC", syncCourtMovement())

      dpsCourtSchedulerServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsCourtSchedulerServer.stubSyncCourtMovement("A1234BC")

      apiService.syncCourtMovement("A1234BC", syncCourtMovement())

      dpsCourtSchedulerServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC"))
          .withRequestBody(matchingJsonPath("user.username", equalTo("USER")))
          .withRequestBody(matchingJsonPath("movement.fromAgencyId", equalTo("BXI")))
          .withRequestBody(matchingJsonPath("movement.toAgencyId", equalTo("LEEDMC"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val dpsId = UUID.randomUUID()
      dpsCourtSchedulerServer.stubSyncCourtMovement("A1234BC", referenceId(dpsId))

      assertThat(
        apiService.syncCourtMovement("A1234BC", syncCourtMovement()).id,
      )
        .isEqualTo(dpsId)
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsCourtSchedulerServer.stubSyncCourtMovementError("A1234BC")

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncCourtMovement("A1234BC", syncCourtMovement())
      }
    }
  }

  @Nested
  inner class DeleteCourtMovement {

    @Test
    internal fun `should pass oath2 token`() = runTest {
      val courtMovementId = UUID.randomUUID()
      dpsCourtSchedulerServer.stubDeleteCourtMovement(courtMovementId)

      apiService.deleteCourtMovement(courtMovementId)

      dpsCourtSchedulerServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should call the endpoint`() = runTest {
      val courtMovementId = UUID.randomUUID()
      dpsCourtSchedulerServer.stubDeleteCourtMovement(courtMovementId)

      apiService.deleteCourtMovement(courtMovementId)

      dpsCourtSchedulerServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/$courtMovementId")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      val courtMovementId = UUID.randomUUID()
      dpsCourtSchedulerServer.stubDeleteCourtMovementError(courtMovementId)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteCourtMovement(courtMovementId)
      }
    }
  }

  @Nested
  inner class Resync {
    val request = ResyncCourtEvents(listOf(), listOf())

    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsCourtSchedulerServer.stubResyncPrisonerCourtAppearances()

      apiService.resyncPrisoner("A1234BC", request)

      dpsCourtSchedulerServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the move endpoint`() = runTest {
      dpsCourtSchedulerServer.stubResyncPrisonerCourtAppearances()

      apiService.resyncPrisoner("A1234BC", request)

      dpsCourtSchedulerServer.verify(
        putRequestedFor(urlPathEqualTo("/resync/court-appearances/A1234BC")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsCourtSchedulerServer.stubResyncPrisonerCourtAppearances(status = 500)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.resyncPrisoner("A1234BC", request)
      }
    }

    @Test
    fun `should return null if not found`() = runTest {
      dpsCourtSchedulerServer.stubResyncPrisonerCourtAppearances(status = 404)

      assertThat(apiService.resyncPrisoner("A1234BC", request)).isNull()
    }
  }
}
