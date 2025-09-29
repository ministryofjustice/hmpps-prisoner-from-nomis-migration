package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.InitialGeneralLedgerBalancesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonBalanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonAccountBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrisonBalanceMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var nomisPrisonBalanceApiMock: FinanceNomisApiMockServer

  private val dpsApiMock = FinanceApiExtension.Companion.financeApi

  @Autowired
  private lateinit var mappingApiMock: PrisonBalanceMappingApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/prison-balance")
  inner class MigratePrisonBalance {
    @BeforeEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/prison-balance")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonBalanceMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/prison-balance")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonBalanceMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/prison-balance")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonBalanceMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisPrisonBalanceApiMock.stubGetPrisonBalanceIds(totalElements = 2, pageSize = 10)
        mappingApiMock.stubGetPrisonBalanceByNomisIdOrNull(
          nomisId = "MDI1",
          mapping = PrisonBalanceMappingDto(
            dpsId = "MDI",
            nomisId = "MDI",
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetPrisonBalanceByNomisIdOrNull(
          nomisId = "MDI2",
          mapping = PrisonBalanceMappingDto(
            dpsId = "LEI",
            nomisId = "LEI",
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will not bother retrieving any prison balance details`() {
        nomisPrisonBalanceApiMock.verify(0, getRequestedFor(urlPathEqualTo("/prison-balance/MDI1")))
        nomisPrisonBalanceApiMock.verify(0, getRequestedFor(urlPathEqualTo("/prison-balance/MDI2")))
      }

      @Test
      fun `will mark migration as complete`() {
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
      }
    }

    @Nested
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisPrisonBalanceApiMock.stubGetPrisonBalanceIds(totalElements = 2, pageSize = 10)
        mappingApiMock.stubGetPrisonBalanceByNomisIdOrNull(nomisId = "MDI1", mapping = null)
        mappingApiMock.stubGetPrisonBalanceByNomisIdOrNull(nomisId = "MDI2", mapping = null)

        nomisPrisonBalanceApiMock.stubGetPrisonBalance(
          prisonId = "MDI1",
          prisonBalance = prisonBalance(prisonId = "MDI").copy(
            accountBalances = listOf(
              PrisonAccountBalanceDto(
                accountCode = 2102,
                balance = BigDecimal.valueOf(24.50),
                transactionDate = LocalDateTime.parse("2025-06-01T01:02:03"),
              ),
            ),
          ),
        )
        nomisPrisonBalanceApiMock.stubGetPrisonBalance(
          prisonId = "MDI2",
          prisonBalance = prisonBalance(prisonId = "LEI").copy(
            accountBalances = listOf(
              PrisonAccountBalanceDto(
                accountCode = 2103,
                balance = BigDecimal.valueOf(25.50),
                transactionDate = LocalDateTime.parse("2025-06-02T02:03:04"),
              ),
            ),
          ),
        )
        dpsApiMock.stubMigratePrisonBalance(prisonId = "MDI")
        dpsApiMock.stubMigratePrisonBalance(prisonId = "LEI")
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 2)
        migrationResult = performMigration()
      }

      @Test
      fun `will get the prisons to migrate`() {
        nomisPrisonBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/finance/prison/ids")))
      }

      @Test
      fun `will get prison balance details for each prison`() {
        nomisPrisonBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/finance/prison/MDI1/balance")))
        nomisPrisonBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/finance/prison/MDI2/balance")))
      }

      @Test
      fun `will create mapping for each prison`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/prison-balance"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "MDI")
            .withRequestBodyJsonPath("nomisId", "MDI1"),
        )
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/prison-balance"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "LEI")
            .withRequestBodyJsonPath("nomisId", "MDI2"),
        )
      }

      @Test
      fun `will track telemetry for each prison migrated`() {
        verify(telemetryClient).trackEvent(
          eq("prisonbalance-migration-entity-migrated"),
          check {
            assertThat(it["nomisId"]).isEqualTo("MDI1")
            assertThat(it["dpsId"]).isEqualTo("MDI")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("prisonbalance-migration-entity-migrated"),
          check {
            assertThat(it["nomisId"]).isEqualTo("MDI2")
            assertThat(it["dpsId"]).isEqualTo("LEI")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of prisons migrated`() {
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("2")
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathNomisToDPSMapping {
      private lateinit var dpsRequests: List<InitialGeneralLedgerBalancesRequest>
      private lateinit var dpsRequests2: List<InitialGeneralLedgerBalancesRequest>
      private lateinit var mappingRequests: List<PrisonBalanceMappingDto>
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        stubMigratePrisonBalances(
          listOf("MDI1", "MDI2"),
          PrisonBalanceDto(
            prisonId = "MDI",
            accountBalances = listOf(
              PrisonAccountBalanceDto(
                accountCode = 2102,
                balance = BigDecimal.valueOf(20.50),
                transactionDate = LocalDateTime.parse("2025-06-01T01:02:03"),
              ),
            ),
          ),
          PrisonBalanceDto(
            prisonId = "LEI",
            accountBalances = listOf(
              PrisonAccountBalanceDto(
                accountCode = 2103,
                balance = BigDecimal.valueOf(25.50),
                transactionDate = LocalDateTime.parse("2025-06-02T02:02:03"),
              ),
            ),
          ),
        )

        migrationResult = performMigration()
        dpsRequests =
          FinanceApiExtension.getRequestBodies(postRequestedFor(urlPathMatching("/migrate/general-ledger-balances/MDI")))
        dpsRequests2 =
          FinanceApiExtension.getRequestBodies(postRequestedFor(urlPathMatching("/migrate/general-ledger-balances/LEI")))
        mappingRequests =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/prison-balance")))
      }

      // TODO the structure of the DPS object is not quite right - this will need to change once updated
      @Test
      fun `will send prison balance data to Dps`() {
        dpsRequests.find { it.initialBalances[0].accountCode == 2101 }?.let {
          assertThat(it.initialBalances[0].balance).isEqualTo(BigDecimal(20.50))
          // TODO assertThat(it.initialBalances[0].transactionDate).isEqualTo(1)
        }
        dpsRequests2.find { it.initialBalances[0].accountCode == 2102 }?.let {
          assertThat(it.initialBalances[0].balance).isEqualTo(BigDecimal(25.50))
          // TODO assertThat(it.initialBalances[1].transactionDate).isEqualTo(180)
        }
      }

      @Test
      fun `will create mappings for nomis id to dps prison balance`() {
        with(mappingRequests.find { it.nomisId == "MDI1" } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisId).isEqualTo("MDI1")
          assertThat(dpsId).isEqualTo("MDI")
        }
        with(mappingRequests.find { it.nomisId == "MDI2" } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisId).isEqualTo("MDI2")
          assertThat(dpsId).isEqualTo("LEI")
        }
      }
    }

    @Nested
    inner class MappingErrorRecovery {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisPrisonBalanceApiMock.stubGetPrisonBalanceIds(totalElements = 1, pageSize = 10)
        mappingApiMock.stubGetPrisonBalanceByNomisIdOrNull(nomisId = "MDI1", mapping = null)
        nomisPrisonBalanceApiMock.stubGetPrisonBalance(
          prisonId = "MDI1",
          prisonBalance(prisonId = "MDI").copy(
            accountBalances = listOf(
              PrisonAccountBalanceDto(
                accountCode = 2102,
                balance = BigDecimal.valueOf(25.10),
                transactionDate = LocalDateTime.parse("2025-06-01T01:02:03"),
              ),
            ),
          ),
        )
        dpsApiMock.stubMigratePrisonBalance(prisonId = "MDI")
        mappingApiMock.stubCreateMappingsForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will get details only once`() {
        nomisPrisonBalanceApiMock.verify(1, getRequestedFor(urlPathEqualTo("/finance/prison/MDI1/balance")))
      }

      @Test
      fun `will attempt create mapping twice before succeeding`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/prison-balance"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "MDI")
            .withRequestBodyJsonPath("nomisId", "MDI1"),
        )
      }

      @Test
      fun `will track telemetry for each offender migrated`() {
        verify(telemetryClient).trackEvent(
          eq("prisonbalance-migration-entity-migrated"),
          check {
            assertThat(it["nomisId"]).isEqualTo("MDI1")
            assertThat(it["dpsId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of prison balance migrated`() {
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("1")
      }
    }

    @Nested
    inner class DuplicateMappingErrorHandling {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisPrisonBalanceApiMock.stubGetPrisonBalanceIds(totalElements = 1, pageSize = 10)
        mappingApiMock.stubGetPrisonBalanceByNomisIdOrNull(nomisId = "MDI1", mapping = null)
        nomisPrisonBalanceApiMock.stubGetPrisonBalance(
          prisonId = "MDI1",
          prisonBalance(prisonId = "MDI").copy(
            accountBalances = listOf(
              PrisonAccountBalanceDto(
                accountCode = 2102,
                balance = BigDecimal.valueOf(25.70),
                transactionDate = LocalDateTime.parse("2025-06-01T01:02:03"),
              ),
            ),
          ),
        )

        dpsApiMock.stubMigratePrisonBalance(prisonId = "MDI")
        mappingApiMock.stubCreateMappingsForMigration(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PrisonBalanceMappingDto(
                dpsId = "MDI",
                nomisId = "MDI1",
                mappingType = MIGRATED,
              ),
              existing = PrisonBalanceMappingDto(
                dpsId = "LEI",
                nomisId = "MDI2",
                mappingType = MIGRATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will get details for offender only once`() {
        nomisPrisonBalanceApiMock.verify(1, getRequestedFor(urlPathEqualTo("/finance/prison/MDI1/balance")))
      }

      @Test
      fun `will attempt create mapping once before failing`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/prison-balance"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "MDI")
            .withRequestBodyJsonPath("nomisId", "MDI1"),
        )
      }

      @Test
      fun `will track telemetry for each offender migrated`() {
        verify(telemetryClient).trackEvent(
          eq("prisonbalance-migration-duplicate"),
          check {
            assertThat(it["duplicateNomisId"]).isEqualTo("MDI1")
            assertThat(it["duplicateDpsId"]).isEqualTo("MDI")
            assertThat(it["existingNomisId"]).isEqualTo("MDI2")
            assertThat(it["existingDpsId"]).isEqualTo("LEI")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of prison balances (offenders) migrated`() {
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("0")
      }
    }

    @Nested
    inner class PreventMultipleMigrations {
      @Test
      fun `will not run a new migration if existing is in progress`() {
        runBlocking {
          migrationHistoryRepository.save(
            MigrationHistory(
              migrationId = "2020-01-01T00:00:00",
              whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
              whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
              status = MigrationStatus.STARTED,
              estimatedRecordCount = 123_567,
              filter = "",
              recordsMigrated = 123_560,
              recordsFailed = 7,
              migrationType = MigrationType.PRISON_BALANCE,
            ),
          )
        }
        webTestClient.post().uri("/migrate/prison-balance")
          .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonBalanceMigrationFilter())
          .exchange()
          .expectStatus().isEqualTo(HttpStatus.CONFLICT)
      }

      @Test
      fun `will not run a new migration if existing is being cancelled`() {
        runBlocking {
          migrationHistoryRepository.save(
            MigrationHistory(
              migrationId = "2020-01-01T00:00:00",
              whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
              whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
              status = MigrationStatus.CANCELLED_REQUESTED,
              estimatedRecordCount = 123_567,
              filter = "",
              recordsMigrated = 123_560,
              recordsFailed = 7,
              migrationType = MigrationType.PRISON_BALANCE,
            ),
          )
        }
        webTestClient.post().uri("/migrate/prison-balance")
          .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonBalanceMigrationFilter())
          .exchange()
          .expectStatus().isEqualTo(HttpStatus.CONFLICT)
      }

      @Test
      fun `will run a new migration if existing is completed`() {
        runBlocking {
          migrationHistoryRepository.save(
            MigrationHistory(
              migrationId = "2020-01-01T00:00:00",
              whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
              whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
              status = MigrationStatus.COMPLETED,
              estimatedRecordCount = 123_567,
              filter = "",
              recordsMigrated = 123_560,
              recordsFailed = 7,
              migrationType = MigrationType.PRISON_BALANCE,
            ),
          )
        }
        nomisPrisonBalanceApiMock.stubGetPrisonBalanceIds(totalElements = 1, pageSize = 10)
        mappingApiMock.stubGetPrisonBalanceByNomisIdOrNull(
          nomisId = "MDI1",
          mapping = PrisonBalanceMappingDto(
            dpsId = "MDI",
            nomisId = "MDI",
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        performMigration()
      }

      @Test
      fun `will run a new migration if existing is cancelled`() {
        runBlocking {
          migrationHistoryRepository.save(
            MigrationHistory(
              migrationId = "2020-01-01T00:00:00",
              whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
              whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
              status = MigrationStatus.CANCELLED,
              estimatedRecordCount = 123_567,
              filter = "",
              recordsMigrated = 123_560,
              recordsFailed = 7,
              migrationType = MigrationType.PRISON_BALANCE,
            ),
          )
        }
        nomisPrisonBalanceApiMock.stubGetPrisonBalanceIds(totalElements = 1, pageSize = 10)
        mappingApiMock.stubGetPrisonBalanceByNomisIdOrNull(
          nomisId = "MDI1",
          mapping = PrisonBalanceMappingDto(
            dpsId = "MDI",
            nomisId = "MDI",
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        performMigration()
      }

      @Test
      fun `will run a new migration if a different migration type has started`() {
        runBlocking {
          migrationHistoryRepository.save(
            MigrationHistory(
              migrationId = "2020-01-01T00:00:00",
              whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
              whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
              status = MigrationStatus.STARTED,
              estimatedRecordCount = 123_567,
              filter = "",
              recordsMigrated = 123_560,
              recordsFailed = 7,
              migrationType = MigrationType.ACTIVITIES,
            ),
          )
        }
        nomisPrisonBalanceApiMock.stubGetPrisonBalanceIds(totalElements = 1, pageSize = 10)
        mappingApiMock.stubGetPrisonBalanceByNomisIdOrNull(
          nomisId = "MDI1",
          mapping = PrisonBalanceMappingDto(
            dpsId = "MDI",
            nomisId = "MDI",
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        performMigration()
      }
    }
  }

  private fun performMigration(body: PrisonBalanceMigrationFilter = PrisonBalanceMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/prison-balance")
    .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(body)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("prisonbalance-migration-completed"),
      any(),
      isNull(),
    )
  }

  private fun stubMigratePrisonBalances(nomisIds: List<String>, vararg prisonAccounts: PrisonBalanceDto) {
    nomisApi.resetAll()
    dpsApiMock.resetAll()
    mappingApiMock.resetAll()
    nomisPrisonBalanceApiMock.stubGetPrisonBalanceIds(totalElements = 2, pageSize = 10)
    prisonAccounts.forEachIndexed { index, nomisPrisonBalance ->
      nomisPrisonBalanceApiMock.stubGetPrisonBalance(prisonId = nomisIds[index], prisonBalance = nomisPrisonBalance)
      mappingApiMock.stubGetPrisonBalanceByNomisIdOrNull(nomisId = nomisIds[index], mapping = null)
      mappingApiMock.stubGetPrisonBalanceByNomisIdOrNull(nomisId = nomisIds[index], mapping = null)
      dpsApiMock.stubMigratePrisonBalance("MDI")
      dpsApiMock.stubMigratePrisonBalance("LEI")
    }
    mappingApiMock.stubCreateMappingsForMigration()
    mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = prisonAccounts.size)
  }
}
