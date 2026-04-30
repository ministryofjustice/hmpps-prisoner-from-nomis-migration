package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.dpsCourtSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiMockServer.Companion.referenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiMockServer.Companion.syncCourtEvent
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
}
