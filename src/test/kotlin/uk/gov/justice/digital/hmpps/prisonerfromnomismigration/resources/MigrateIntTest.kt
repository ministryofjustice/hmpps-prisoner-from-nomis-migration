package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.resources

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.PrisonerBalanceMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.PrisonerBalanceMigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.PrisonerBalanceNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.prisonerBalance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAccountDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MigrateIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: PrisonerBalanceNomisApiMockServer

  @Autowired
  private lateinit var mappingApiMock: PrisonerBalanceMappingApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/cancel/{migrationId}")
  inner class CancelMigrationVisitBalance {
    @BeforeEach
    internal fun setUp() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/cancel/{migrationId}", "some id")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/cancel/{migrationId}", "some id")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/cancel/{migrationId}", "some id")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/cancel/{migrationId}", "some id")
        .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    internal fun setUpMigrationStubs() {
      nomisApiMock.stubGetRootOffenderIdsToMigrate(
        totalElements = 2,
        pageSize = 10,
        firstRootOffenderId = 10000L,
      )
      mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(
        nomisRootOffenderId = 10000,
        dpsId = "A0001BC",
        mapping = null,
      )
      mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(
        nomisRootOffenderId = 10001,
        dpsId = "A0002BC",
        mapping = null,
      )

      nomisApiMock.stubGetPrisonerBalance(
        rootOffenderId = 10000,
        prisonerBalance = prisonerBalance(prisonNumber = "A0001BC").copy(
          accounts = listOf(
            PrisonerAccountDto(
              prisonId = "ASI",
              lastTransactionId = 175,
              accountCode = 2102,
              balance = BigDecimal.valueOf(24.50),
              holdBalance = BigDecimal.valueOf(2.25),
              transactionDate = LocalDateTime.parse("2025-06-02T02:02:03"),
            ),
          ),
        ),
      )
      nomisApiMock.stubGetPrisonerBalance(
        rootOffenderId = 10001,
        prisonerBalance = prisonerBalance(prisonNumber = "A0002BC").copy(
          accounts = listOf(
            PrisonerAccountDto(
              prisonId = "ASI",
              lastTransactionId = 176,
              accountCode = 2103,
              balance = BigDecimal.valueOf(25.50),
              holdBalance = BigDecimal.valueOf(2.15),
              transactionDate = LocalDateTime.parse("2025-07-02T01:02:05"),
            ),
          ),
        ),
      )
      financeApi.stubMigratePrisonerBalance(prisonNumber = "A0001BC")
      financeApi.stubMigratePrisonerBalance(prisonNumber = "A0002BC")
      mappingApiMock.stubCreateMappingsForMigration()
      mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 2)
    }

    @Test
    internal fun `will terminate a running migration`() {
      // slow the API calls so there is time to cancel before it completes
      nomisApi.setGlobalFixedDelay(1000)
      setUpMigrationStubs()

      val migrationId = performMigration().migrationId

      webTestClient.post().uri("/migrate/cancel/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationId)
          .jsonPath("$.status").isEqualTo("CANCELLED")
      }
    }
  }

  @Nested
  @DisplayName("POST /migrate/refresh/{migrationId}")
  inner class RefreshMigration {
    @BeforeEach
    internal fun setUp() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/refresh/{migrationId}", "some id")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/refresh/{migrationId}", "some id")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/refresh/{migrationId}", "some id")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `will return a not found if no migration found`() {
      webTestClient.post().uri("/migrate/refresh/{migrationId}", "some id")
        .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    internal fun `will return bad request if no completed migration found`() {
      runBlocking {
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2019-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2019-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2019-01-01T01:00:00"),
            status = MigrationStatus.STARTED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.PRISONER_BALANCE,
          ),
        )
      }

      webTestClient.post().uri("/migrate/refresh/{migrationId}", "2019-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    internal fun `will refresh a completed migration`() {
      val migrationId = "2019-01-01T02:01:03"
      runBlocking {
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = migrationId,
            whenStarted = LocalDateTime.parse("2019-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2019-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 123,
            migrationType = MigrationType.PRISONER_BALANCE,
          ),
        )
      }
      mappingApiMock.stubGetMigrationCount(count = 4)

      webTestClient.post().uri("/migrate/refresh/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNoContent

      webTestClient.get().uri("/migrate/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("COMPLETED")
        .jsonPath("$.recordsMigrated").isEqualTo(4)
        .jsonPath("$.recordsFailed").isEqualTo(0)
        .jsonPath("$.estimatedRecordCount").isEqualTo(123_567)
    }
  }

  private fun performMigration(body: PrisonerBalanceMigrationFilter = PrisonerBalanceMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/prisoner-balance")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(body)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("prisonerbalance-migration-completed"),
      any(),
      isNull(),
    )
  }
}
