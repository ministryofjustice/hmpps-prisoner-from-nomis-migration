package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

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
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleDpsApiExtension.Companion.dpsTransferSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleDpsApiMockServer.Companion.referenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleDpsApiMockServer.Companion.syncTransferRequest
import java.util.*

@ExtendWith(TransferScheduleDpsApiExtension::class)
@SpringAPIServiceTest
@Import(TransferScheduleDpsApiService::class, TransferScheduleConfiguration::class, TransferScheduleDpsApiMockServer::class)
class TransferScheduleDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: TransferScheduleDpsApiService

  @Nested
  inner class SyncTransferSchedule {

    @Test
    internal fun `should pass oath2 token`() = runTest {
      dpsTransferSchedulerServer.stubSyncTransferSchedule("A1234BC")

      apiService.syncTransferSchedule("A1234BC", syncTransferRequest())

      dpsTransferSchedulerServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the sync endpoint`() = runTest {
      dpsTransferSchedulerServer.stubSyncTransferSchedule("A1234BC")

      apiService.syncTransferSchedule("A1234BC", syncTransferRequest())

      dpsTransferSchedulerServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/transfers/A1234BC"))
          .withRequestBody(matchingJsonPath("syncUser.username", equalTo("USER")))
          .withRequestBody(matchingJsonPath("transfer.schedule.agyLocId", equalTo("BXI")))
          .withRequestBody(matchingJsonPath("transfer.schedule.toAgyLocId", equalTo("LEI"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val dpsId = UUID.randomUUID()
      dpsTransferSchedulerServer.stubSyncTransferSchedule("A1234BC", referenceId(dpsId))

      assertThat(
        apiService.syncTransferSchedule("A1234BC", syncTransferRequest()).dpsId,
      )
        .isEqualTo(dpsId)
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsTransferSchedulerServer.stubSyncTransferScheduleError("A1234BC")

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncTransferSchedule("A1234BC", syncTransferRequest())
      }
    }
  }
}
