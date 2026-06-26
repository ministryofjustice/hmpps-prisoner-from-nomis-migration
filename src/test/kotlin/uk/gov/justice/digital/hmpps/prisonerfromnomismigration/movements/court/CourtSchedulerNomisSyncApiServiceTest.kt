package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisSyncApiExtension
import java.util.*

@ExtendWith(NomisSyncApiExtension::class)
@SpringAPIServiceTest
@Import(CourtSchedulerNomisSyncApiService::class, CourtSchedulerNomisSyncApiMockServer::class)
class CourtSchedulerNomisSyncApiServiceTest(
  @Autowired private val apiService: CourtSchedulerNomisSyncApiService,
  @Autowired private val mockServer: CourtSchedulerNomisSyncApiMockServer,
) {

  @Nested
  inner class RecreateCourtSchedule {
    private val dpsId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token`() = runTest {
      mockServer.stubRecreateCourtScheduleInNomis("A1234BC", dpsId)

      apiService.recreateCourtScheduleInNomis("A1234BC", dpsId)

      mockServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      mockServer.stubRecreateCourtScheduleInNomis("A1234BC", dpsId)

      apiService.recreateCourtScheduleInNomis("A1234BC", dpsId)

      mockServer.verify(
        putRequestedFor(urlEqualTo("/court-scheduler/court/schedule/out/A1234BC/$dpsId?recreate=true")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      mockServer.stubRecreateCourtScheduleInNomis(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.recreateCourtScheduleInNomis("A1234BC", dpsId)
      }
    }
  }
}
