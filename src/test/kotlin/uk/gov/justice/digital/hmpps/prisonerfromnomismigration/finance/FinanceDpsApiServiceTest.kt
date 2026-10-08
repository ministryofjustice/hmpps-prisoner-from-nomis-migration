package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiMockServer.Companion.addHoldDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiMockServer.Companion.createAdvanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiMockServer.Companion.prisonBalanceMigrationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiMockServer.Companion.prisonerBalanceMigrationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiMockServer.Companion.releaseHoldDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiMockServer.Companion.repayAdvanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiMockServer.Companion.writeOffAdvanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.util.UUID

@ExtendWith(FinanceApiExtension::class)
@SpringAPIServiceTest
@Import(FinanceDpsApiService::class, FinanceConfiguration::class)
class FinanceDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: FinanceDpsApiService

  @Nested
  inner class MigratePrisonerBalance {
    @Test
    internal fun `will pass oauth2 token to migrate endpoint`() = runTest {
      financeApi.stubMigratePrisonerBalance()

      apiService.migratePrisonerBalance("A1234BC", prisonerBalanceMigrationDto())

      financeApi.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will migrate request data to migrate endpoint`() = runTest {
      financeApi.stubMigratePrisonerBalance()

      apiService.migratePrisonerBalance("A1234BC", prisonerBalanceMigrationDto())

      financeApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("accountBalances[0].prisonId", equalTo("ASI"))
          .withRequestBodyJsonPath("accountBalances[0].accountCode", equalTo("2101"))
          .withRequestBodyJsonPath("accountBalances[0].balance", equalTo("23.5"))
          .withRequestBodyJsonPath("accountBalances[0].holdBalance", equalTo("1.25"))
          .withRequestBodyJsonPath("accountBalances[0].transactionId", equalTo("173"))
          .withRequestBodyJsonPath("accountBalances[0].asOfTimestamp", equalTo("2025-06-02T02:02:03"))
          .withRequestBodyJsonPath("accountBalances[1].prisonId", equalTo("ASI"))
          .withRequestBodyJsonPath("accountBalances[1].accountCode", equalTo("2102"))
          .withRequestBodyJsonPath("accountBalances[1].balance", equalTo("11.5"))
          .withRequestBodyJsonPath("accountBalances[1].holdBalance", equalTo("0"))
          .withRequestBodyJsonPath("accountBalances[1].transactionId", equalTo("174"))
          .withRequestBodyJsonPath("accountBalances[1].asOfTimestamp", equalTo("2025-06-01T01:02:03")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      financeApi.stubMigratePrisonerBalance()

      apiService.migratePrisonerBalance("A1234BC", prisonerBalanceMigrationDto())

      financeApi.verify(
        postRequestedFor(urlPathEqualTo("/migrate/prisoner-balances/A1234BC")),
      )
    }
  }

  @Nested
  inner class MigratePrisonBalance {
    @Test
    internal fun `will pass oauth2 token to migrate endpoint`() = runTest {
      financeApi.stubMigratePrisonBalance()

      apiService.migratePrisonBalance("MDI", prisonBalanceMigrationDto())

      financeApi.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will migrate request data  to migrate endpoint`() = runTest {
      financeApi.stubMigratePrisonBalance()

      apiService.migratePrisonBalance("MDI", prisonBalanceMigrationDto())

      financeApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("accountBalances[0].accountCode", equalTo("2101"))
          .withRequestBodyJsonPath("accountBalances[0].balance", equalTo("23.5"))
          .withRequestBodyJsonPath("accountBalances[1].accountCode", equalTo("2102"))
          .withRequestBodyJsonPath("accountBalances[1].balance", equalTo("11.5")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      financeApi.stubMigratePrisonBalance()

      apiService.migratePrisonBalance("MDI", prisonBalanceMigrationDto())

      financeApi.verify(
        postRequestedFor(urlPathEqualTo("/migrate/general-ledger-balances/MDI")),
      )
    }
  }

  @Nested
  inner class Holds {
    @Nested
    inner class MigrateHold {
      @Test
      internal fun `will pass oauth2 token to the sync endpoint`() = runTest {
        financeApi.stubMigrateHold()

        apiService.migrateHold(addHoldDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will send request data to migrate endpoint`() = runTest {
        financeApi.stubMigrateHold()

        apiService.migrateHold(addHoldDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withRequestBodyJsonPath("prisonNumber", equalTo("A1234BC"))
            .withRequestBodyJsonPath("subAccountCode", equalTo("2101"))
            .withRequestBodyJsonPath("holdNumber", equalTo("12345"))
            .withRequestBodyJsonPath("createdAt", equalTo("2025-06-01T01:02:03"))
            .withRequestBodyJsonPath("createdBy", equalTo("testUser"))
            .withRequestBodyJsonPath("holdFromDate", equalTo("2025-06-01T01:02:03"))
            .withRequestBodyJsonPath("isReleased", equalTo("false"))
            .withRequestBodyJsonPath("holdTransactionId", equalTo("12344"))
            .withRequestBodyJsonPath("holdType", equalTo("HOA"))
            .withRequestBodyJsonPath("holdLocation", equalTo("Some location"))
            .withRequestBodyJsonPath("amount", equalTo("10.0"))
            .withRequestBodyJsonPath("holdUntilDate", equalTo("2025-09-09T04:05:06"))
            .withRequestBodyJsonPath("description", equalTo("This is a hold")),
        )
      }

      @Test
      fun `will call the migrate endpoint`() = runTest {
        financeApi.stubMigrateHold()

        apiService.migrateHold(addHoldDto())

        financeApi.verify(
          postRequestedFor(urlPathEqualTo("/migrate/holds")),
        )
      }
    }

    @Nested
    inner class SyncAddHold {
      @Test
      internal fun `will pass oauth2 token to the sync endpoint`() = runTest {
        financeApi.stubAddHold()

        apiService.syncAddHoldTransaction(addHoldDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will send request data to sync endpoint`() = runTest {
        financeApi.stubAddHold()

        apiService.syncAddHoldTransaction(addHoldDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withRequestBodyJsonPath("prisonNumber", equalTo("A1234BC"))
            .withRequestBodyJsonPath("subAccountCode", equalTo("2101"))
            .withRequestBodyJsonPath("holdNumber", equalTo("12345"))
            .withRequestBodyJsonPath("createdAt", equalTo("2025-06-01T01:02:03"))
            .withRequestBodyJsonPath("createdBy", equalTo("testUser"))
            .withRequestBodyJsonPath("holdFromDate", equalTo("2025-06-01T01:02:03"))
            .withRequestBodyJsonPath("isReleased", equalTo("false"))
            .withRequestBodyJsonPath("holdTransactionId", equalTo("12344"))
            .withRequestBodyJsonPath("holdType", equalTo("HOA"))
            .withRequestBodyJsonPath("holdLocation", equalTo("Some location"))
            .withRequestBodyJsonPath("amount", equalTo("10.0"))
            .withRequestBodyJsonPath("holdUntilDate", equalTo("2025-09-09T04:05:06"))
            .withRequestBodyJsonPath("description", equalTo("This is a hold")),
        )
      }

      @Test
      fun `will call the sync endpoint`() = runTest {
        financeApi.stubAddHold()

        apiService.syncAddHoldTransaction(addHoldDto())

        financeApi.verify(
          postRequestedFor(urlPathEqualTo("/sync/holds")),
        )
      }
    }

    @Nested
    inner class SyncReleaseHold {
      @Test
      internal fun `will pass oauth2 token to the sync endpoint`() = runTest {
        financeApi.stubReleaseHold()

        apiService.syncReleaseHoldTransaction(12345, releaseHoldDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will send request data to sync endpoint`() = runTest {
        financeApi.stubReleaseHold()

        apiService.syncReleaseHoldTransaction(12345, releaseHoldDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withRequestBodyJsonPath("releaseDateTime", equalTo("2025-06-04T05:06:07")),
        )
      }

      @Test
      fun `will call the sync endpoint`() = runTest {
        financeApi.stubReleaseHold()

        apiService.syncReleaseHoldTransaction(12345, releaseHoldDto())

        financeApi.verify(
          postRequestedFor(urlPathEqualTo("/sync/holds/12345/release")),
        )
      }
    }
  }

  @Nested
  inner class Advances {
    @Nested
    inner class MigrateAdvance {
      @Test
      internal fun `will pass oauth2 token to migrate endpoint`() = runTest {
        financeApi.stubMigrateAdvance()

        apiService.migrateAdvance(createAdvanceDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will sync request data to migrate advance endpoint`() = runTest {
        financeApi.stubMigrateAdvance()

        apiService.migrateAdvance(createAdvanceDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withRequestBodyJsonPath("legacyPaymentProfileId", equalTo("12345"))
            .withRequestBodyJsonPath("legacyInformationNumber", equalTo("9876-1"))
            .withRequestBodyJsonPath("prisonNumber", equalTo("A0001BC"))
            .withRequestBodyJsonPath("prisonID", equalTo("LEI"))
            .withRequestBodyJsonPath("amount", equalTo("2.1"))
            .withRequestBodyJsonPath("repaymentAmount", equalTo("0.5"))
            .withRequestBodyJsonPath("repaymentStartDate", equalTo("2024-06-18T00:00:00"))
            .withRequestBodyJsonPath("comment", equalTo("This is a comment"))
            .withRequestBodyJsonPath("reference", equalTo("description of the advance"))
            .withRequestBodyJsonPath("status", equalTo("ACTIVE"))
            .withRequestBodyJsonPath("createdBy", equalTo("JD12345"))
            .withRequestBodyJsonPath("createdOn", equalTo("2024-06-18T12:10:00"))
            // TODO REMOVE - this will not be required once the migrate endpoint is added
            .withRequestBodyJsonPath("legacyTransactionId", equalTo("2345")),
        )
      }

      @Test
      fun `will call the migrate prisoner advance endpoint`() = runTest {
        financeApi.stubMigrateAdvance()

        apiService.migrateAdvance(createAdvanceDto())

        financeApi.verify(
          postRequestedFor(urlPathEqualTo("/migrate/advances")),
        )
      }

      @Test
      fun `will return dpsAdvanceId`() = runTest {
        val advanceUuid = UUID.randomUUID()
        financeApi.stubMigrateAdvance(advanceUuid = advanceUuid)

        val dpsAdvance = apiService.migrateAdvance(createAdvanceDto())
        assertThat(dpsAdvance.advanceId).isEqualTo(advanceUuid)
      }
    }

    @Nested
    inner class SyncCreateAdvance {
      @Test
      internal fun `will pass oauth2 token to migrate endpoint`() = runTest {
        financeApi.stubCreateAdvance()

        apiService.createAdvance(createAdvanceDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will sync request data to sync advance endpoint`() = runTest {
        financeApi.stubCreateAdvance()

        apiService.createAdvance(createAdvanceDto())
        financeApi.verify(
          postRequestedFor(anyUrl())
            .withRequestBodyJsonPath("legacyPaymentProfileId", equalTo("12345"))
            .withRequestBodyJsonPath("legacyTransactionId", equalTo("2345"))
            .withRequestBodyJsonPath("legacyInformationNumber", equalTo("9876-1"))
            .withRequestBodyJsonPath("prisonNumber", equalTo("A0001BC"))
            .withRequestBodyJsonPath("prisonID", equalTo("LEI"))
            .withRequestBodyJsonPath("amount", equalTo("2.1"))
            .withRequestBodyJsonPath("repaymentAmount", equalTo("0.5"))
            .withRequestBodyJsonPath("repaymentStartDate", equalTo("2024-06-18T00:00:00"))
            .withRequestBodyJsonPath("comment", equalTo("This is a comment"))
            .withRequestBodyJsonPath("reference", equalTo("description of the advance"))
            .withRequestBodyJsonPath("createdBy", equalTo("JD12345"))
            .withRequestBodyJsonPath("createdOn", equalTo("2024-06-18T12:10:00"))
            .withRequestBodyJsonPath("status", equalTo("ACTIVE")),
        )
      }

      @Test
      fun `will call the sync prisoner advance endpoint`() = runTest {
        financeApi.stubCreateAdvance()

        apiService.createAdvance(createAdvanceDto())

        financeApi.verify(
          postRequestedFor(urlPathEqualTo("/sync/advances")),
        )
      }
    }

    @Nested
    inner class SyncRepayAdvance {
      val advanceUuid: UUID = UUID.randomUUID()

      @Test
      internal fun `will pass oauth2 token to migrate endpoint`() = runTest {
        financeApi.stubRepayAdvance(advanceUuid)

        apiService.repayAdvance(advanceUuid, repayAdvanceDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will sync request data to sync repay advance endpoint`() = runTest {
        financeApi.stubRepayAdvance(advanceUuid)

        apiService.repayAdvance(advanceUuid, repayAdvanceDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withRequestBodyJsonPath("amount", equalTo("2"))
            .withRequestBodyJsonPath("transactionId", equalTo("3456"))
            .withRequestBodyJsonPath("createdBy", equalTo("KE12345"))
            .withRequestBodyJsonPath("createdAt", equalTo("2024-06-20T11:30:00")),
        )
      }

      @Test
      fun `will call the sync prisoner advance endpoint`() = runTest {
        financeApi.stubRepayAdvance(advanceUuid)

        apiService.repayAdvance(advanceUuid, repayAdvanceDto())

        financeApi.verify(
          postRequestedFor(urlPathEqualTo("/sync/advances/$advanceUuid/repay")),
        )
      }
    }

    @Nested
    inner class SyncWriteOffAdvance {
      val advanceUuid: UUID = UUID.randomUUID()

      @Test
      internal fun `will pass oauth2 token to migrate endpoint`() = runTest {
        financeApi.stubWriteOffAdvance(advanceUuid)

        apiService.writeOffAdvance(advanceUuid, writeOffAdvanceDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will sync request data to sync advance endpoint`() = runTest {
        financeApi.stubWriteOffAdvance(advanceUuid)

        apiService.writeOffAdvance(advanceUuid, writeOffAdvanceDto())

        financeApi.verify(
          postRequestedFor(anyUrl())
            .withRequestBodyJsonPath("amount", equalTo("2"))
            .withRequestBodyJsonPath("writeOffDateTime", equalTo("2024-06-29T09:15:00"))
            .withRequestBodyJsonPath("writtenOffBy", equalTo("LF12345")),
        )
      }

      @Test
      fun `will call the sync prisoner advance repayment endpoint`() = runTest {
        financeApi.stubWriteOffAdvance(advanceUuid)

        apiService.writeOffAdvance(advanceUuid, writeOffAdvanceDto())
        financeApi.verify(
          postRequestedFor(urlPathEqualTo("/sync/advances/$advanceUuid/write-off")),
        )
      }
    }
  }
}
