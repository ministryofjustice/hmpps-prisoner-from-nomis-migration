package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances.VisitBalanceDpsApiExtension.Companion.dpsVisitBalanceServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances.VisitBalanceDpsApiMockServer.Companion.visitBalanceAdjustmentSyncDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances.VisitBalanceDpsApiMockServer.Companion.visitBalanceMigrationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

@SpringAPIServiceTest
@Import(VisitBalanceDpsApiService::class, VisitBalanceConfiguration::class)
class VisitBalanceDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitBalanceDpsApiService

  @Nested
  inner class MigrateVisitBalance {
    @Test
    internal fun `will pass oath2 token to migrate endpoint`() = runTest {
      dpsVisitBalanceServer.stubMigrateVisitBalance()

      apiService.migrateVisitBalance(visitBalanceMigrationDto())

      dpsVisitBalanceServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will migrate request data  to migrate endpoint`() = runTest {
      dpsVisitBalanceServer.stubMigrateVisitBalance()

      apiService.migrateVisitBalance(visitBalanceMigrationDto())

      dpsVisitBalanceServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("prisonerId", equalTo("A1234BC"))
          .withRequestBodyJsonPath("voBalance", equalTo("2"))
          .withRequestBodyJsonPath("pvoBalance", equalTo("3"))
          .withRequestBodyJsonPath("lastVoAllocationDate", equalTo("2024-03-04")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      dpsVisitBalanceServer.stubMigrateVisitBalance()

      apiService.migrateVisitBalance(visitBalanceMigrationDto())

      dpsVisitBalanceServer.verify(
        postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/migrate")),
      )
    }
  }

  @Nested
  inner class SyncVisitBalanceAdjustment {
    @Test
    internal fun `will pass oath2 token to sync adjustment endpoint`() = runTest {
      dpsVisitBalanceServer.stubSyncVisitBalanceAdjustment()

      apiService.syncVisitBalanceAdjustment(visitBalanceAdjustmentSyncDto())

      dpsVisitBalanceServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will post request data  to sync adjustment endpoint`() = runTest {
      dpsVisitBalanceServer.stubSyncVisitBalanceAdjustment()

      apiService.syncVisitBalanceAdjustment(visitBalanceAdjustmentSyncDto())

      dpsVisitBalanceServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("prisonerId", equalTo("A1234BC"))
          .withRequestBodyJsonPath("oldVoBalance", equalTo("12"))
          .withRequestBodyJsonPath("changeToVoBalance", equalTo("2"))
          .withRequestBodyJsonPath("oldPvoBalance", equalTo("4"))
          .withRequestBodyJsonPath("changeToPvoBalance", equalTo("1"))
          .withRequestBodyJsonPath("createdDate", equalTo("2025-01-01"))
          .withRequestBodyJsonPath("adjustmentReasonCode", equalTo("IEP"))
          .withRequestBodyJsonPath("changeLogSource", equalTo("STAFF"))
          .withRequestBodyJsonPath("comment", equalTo("Some comment")),
      )
    }

    @Test
    fun `will call the sync adjustment endpoint`() = runTest {
      dpsVisitBalanceServer.stubSyncVisitBalanceAdjustment()

      apiService.syncVisitBalanceAdjustment(visitBalanceAdjustmentSyncDto())

      dpsVisitBalanceServer.verify(
        postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/sync")),
      )
    }
  }
}
