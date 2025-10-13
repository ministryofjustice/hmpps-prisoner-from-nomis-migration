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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances.VisitBalanceDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances.VisitBalanceMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances.VisitBalanceMigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances.VisitBalanceNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances.visitBalanceDetail
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.Duration
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MigrateIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisVisitBalanceApiMock: VisitBalanceNomisApiMockServer

  private val dpsApiMock = VisitBalanceDpsApiExtension.Companion.dpsVisitBalanceServer

  @Autowired
  private lateinit var mappingApiMock: VisitBalanceMappingApiMockServer

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

    @Test
    internal fun `will terminate a running migration`() {
      // slow the API calls so there is time to cancel before it completes
      nomisApi.setGlobalFixedDelay(1000)
      nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 2, pageSize = 10, firstVisitBalanceId = 10000)
      mappingApiMock.stubGetVisitBalanceByNomisIdOrNull(nomisVisitBalanceId = 10000, mapping = null)
      mappingApiMock.stubGetVisitBalanceByNomisIdOrNull(nomisVisitBalanceId = 20000, mapping = null)
      nomisVisitBalanceApiMock.stubGetVisitBalanceDetail(
        nomisVisitBalanceId = 10000,
        prisonNumber = "A0001BC",
        visitBalanceDetail(prisonNumber = "A0001BC").copy(remainingPrivilegedVisitOrders = 3),
      )
      nomisVisitBalanceApiMock.stubGetVisitBalanceDetail(
        nomisVisitBalanceId = 20000,
        prisonNumber = "A0002BC",
        visitBalanceDetail(prisonNumber = "A0002BC").copy(remainingPrivilegedVisitOrders = 4),
      )

      dpsApiMock.stubMigrateVisitBalance()
      mappingApiMock.stubCreateMappingsForMigration()
      mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 2)

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
  inner class RefreshMigrationVisitBalance {
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
            migrationType = MigrationType.VISIT_BALANCE,
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
            migrationType = MigrationType.VISIT_BALANCE,
          ),
        )
      }
      mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 4)

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

  private fun performMigration(body: VisitBalanceMigrationFilter = VisitBalanceMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/visit-balance")
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
      eq("visitbalance-migration-completed"),
      any(),
      isNull(),
    )
  }
}
