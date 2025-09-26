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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.InitialPrisonerBalancesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAccountDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerBalanceDto
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
class PrisonerBalanceMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var nomisPrisonerBalanceApiMock: PrisonerBalanceNomisApiMockServer

  private val dpsApiMock = FinanceApiExtension.Companion.financeApi

  @Autowired
  private lateinit var mappingApiMock: PrisonerBalanceMappingApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/prisoner-balance")
  inner class MigratePrisonerBalance {
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
        webTestClient.post().uri("/migrate/prisoner-balance")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerBalanceMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/prisoner-balance")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerBalanceMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/prisoner-balance")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerBalanceMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisPrisonerBalanceApiMock.stubGetRootOffenderIdsToMigrate(totalElements = 2, pageSize = 10, firstRootOffenderId = 10000)
        mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(
          nomisRootOffenderId = 10000,
          mapping = PrisonerBalanceMappingDto(
            dpsId = "A0001BC",
            nomisRootOffenderId = 10000,
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(
          nomisRootOffenderId = 20000,
          mapping = PrisonerBalanceMappingDto(
            dpsId = "A0002BC",
            nomisRootOffenderId = 20000,
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will not bother retrieving any prisoner balance details`() {
        nomisPrisonerBalanceApiMock.verify(0, getRequestedFor(urlPathEqualTo("/prisoner-balance/A0001BC")))
        nomisPrisonerBalanceApiMock.verify(0, getRequestedFor(urlPathEqualTo("/prisoner-balance/B0002BC")))
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
        nomisPrisonerBalanceApiMock.stubGetRootOffenderIdsToMigrate(totalElements = 2, pageSize = 10, firstRootOffenderId = 10000L)
        mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(nomisRootOffenderId = 10000, dpsId = "A0001BC", mapping = null)
        mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(nomisRootOffenderId = 10001, dpsId = "A0002BC", mapping = null)

        nomisPrisonerBalanceApiMock.stubGetPrisonerBalance(
          rootOffenderId = 10000,
          prisonerBalance = prisonerBalance(prisonNumber = "A0001BC").copy(
            accounts = listOf(
              PrisonerAccountDto(
                prisonId = "ASI",
                lastTransactionId = 175,
                accountCode = 2102,
                balance = BigDecimal.valueOf(24.50),
                holdBalance = BigDecimal.valueOf(2.25),
              ),
            ),
          ),
        )
        nomisPrisonerBalanceApiMock.stubGetPrisonerBalance(
          rootOffenderId = 10001,
          prisonerBalance = prisonerBalance(prisonNumber = "A0002BC").copy(
            accounts = listOf(
              PrisonerAccountDto(
                prisonId = "ASI",
                lastTransactionId = 176,
                accountCode = 2103,
                balance = BigDecimal.valueOf(25.50),
                holdBalance = BigDecimal.valueOf(2.15),
              ),
            ),
          ),
        )
        dpsApiMock.stubMigratePrisonerBalance(prisonNumber = "A0001BC")
        dpsApiMock.stubMigratePrisonerBalance(prisonNumber = "A0002BC")
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 2)
        migrationResult = performMigration()
      }

      @Test
      fun `will get the offenders to migrate`() {
        nomisPrisonerBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/finance/prisoners/ids")))
      }

      @Test
      fun `will get prisoner balance details for each offender`() {
        nomisPrisonerBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/finance/prisoners/10000/balance")))
        nomisPrisonerBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/finance/prisoners/10001/balance")))
      }

      @Test
      fun `will create mapping for each prisoner`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/prisoner-balance"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "A0001BC")
            .withRequestBodyJsonPath("nomisRootOffenderId", 10000),
        )
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/prisoner-balance"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "A0002BC")
            .withRequestBodyJsonPath("nomisRootOffenderId", 10001),
        )
      }

      @Test
      fun `will track telemetry for each prisoner migrated`() {
        verify(telemetryClient).trackEvent(
          eq("prisonerbalance-migration-entity-migrated"),
          check {
            assertThat(it["nomisRootOffenderId"]).isEqualTo("10000")
            assertThat(it["dpsPrisonerId"]).isEqualTo("A0001BC")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("prisonerbalance-migration-entity-migrated"),
          check {
            assertThat(it["nomisRootOffenderId"]).isEqualTo("10001")
            assertThat(it["dpsPrisonerId"]).isEqualTo("A0002BC")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of prisoners migrated`() {
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
      private lateinit var dpsRequests: List<InitialPrisonerBalancesRequest>
      private lateinit var dpsRequests2: List<InitialPrisonerBalancesRequest>
      private lateinit var mappingRequests: List<PrisonerBalanceMappingDto>
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        stubMigratePrisonerBalances(
          listOf(10000, 10001),
          PrisonerBalanceDto(
            rootOffenderId = 10000,
            prisonNumber = "A0001BC",
            accounts = listOf(
              PrisonerAccountDto(
                prisonId = "ASI",
                lastTransactionId = 175,
                accountCode = 2102,
                balance = BigDecimal.valueOf(20.50),
                holdBalance = BigDecimal.valueOf(2.15),
              ),
            ),
          ),
          PrisonerBalanceDto(
            rootOffenderId = 10001,
            prisonNumber = "A0002BC",
            accounts = listOf(
              PrisonerAccountDto(
                prisonId = "ASI",
                lastTransactionId = 176,
                accountCode = 2103,
                balance = BigDecimal.valueOf(25.50),
                holdBalance = BigDecimal.valueOf(1.15),
              ),
            ),
          ),
        )

        migrationResult = performMigration()
        dpsRequests =
          FinanceApiExtension.Companion.getRequestBodies(postRequestedFor(urlPathMatching("/migrate/prisoner-balances/A0001BC")))
        dpsRequests2 =
          FinanceApiExtension.Companion.getRequestBodies(postRequestedFor(urlPathMatching("/migrate/prisoner-balances/A0002BC")))
        mappingRequests =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/prisoner-balance")))
      }

      // TODO the structure of the DPS object is not quite right - this will need to change once updated
      @Test
      fun `will send prisoner balance data to Dps`() {
        // dpsRequests.find { it.prisonerId == "A0001BC" }?.let {
        dpsRequests.find { it.initialBalances[0].accountCode == 2101 }?.let {
          assertThat(it.initialBalances[0].balance).isEqualTo(BigDecimal(20.50))
          assertThat(it.initialBalances[0].holdBalance).isEqualTo(BigDecimal(2.15))
          // TODO - this should be in the InitialPrisonerBalance class
          assertThat(it.prisonId).isEqualTo("ASI")
          // TODO assertThat(it.initialBalances[0].transactionId).isEqualTo(1)
        }
        dpsRequests2.find { it.initialBalances[0].accountCode == 2102 }?.let {
          assertThat(it.initialBalances[0].balance).isEqualTo(BigDecimal(25.50))
          assertThat(it.initialBalances[0].holdBalance).isEqualTo(BigDecimal(1.15))
          // TODO - this should be in the InitialPrisonerBalance class
          assertThat(it.prisonId).isEqualTo("ASI")
          // TODO assertThat(it.initialBalances[1].transactionId).isEqualTo(180)
        }
      }

      @Test
      fun `will create mappings for nomis rootOffender to dps prisoner balance`() {
        with(mappingRequests.find { it.nomisRootOffenderId == 10000L } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisRootOffenderId).isEqualTo(10000)
          assertThat(dpsId).isEqualTo("A0001BC")
        }
        with(mappingRequests.find { it.nomisRootOffenderId == 10001L } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisRootOffenderId).isEqualTo(10001)
          assertThat(dpsId).isEqualTo("A0002BC")
        }
      }
    }

    @Nested
    inner class MappingErrorRecovery {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisPrisonerBalanceApiMock.stubGetRootOffenderIdsToMigrate(totalElements = 1, pageSize = 10, firstRootOffenderId = 10000)
        mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(nomisRootOffenderId = 10000, mapping = null)
        nomisPrisonerBalanceApiMock.stubGetPrisonerBalance(
          rootOffenderId = 10000,
          prisonNumber = "A0001BC",
          prisonerBalance(prisonNumber = "A0001BC").copy(
            accounts = listOf(
              PrisonerAccountDto(
                prisonId = "ASI",
                lastTransactionId = 179,
                accountCode = 2102,
                balance = BigDecimal.valueOf(25.10),
                holdBalance = BigDecimal.valueOf(2.15),
              ),
            ),
          ),
        )
        dpsApiMock.stubMigratePrisonerBalance(prisonNumber = "A0001BC")
        mappingApiMock.stubCreateMappingsForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will get details only once`() {
        nomisPrisonerBalanceApiMock.verify(1, getRequestedFor(urlPathEqualTo("/finance/prisoners/10000/balance")))
      }

      @Test
      fun `will attempt create mapping twice before succeeding`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/prisoner-balance"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "A0001BC")
            .withRequestBodyJsonPath("nomisRootOffenderId", 10000),
        )
      }

      @Test
      fun `will track telemetry for each offender migrated`() {
        verify(telemetryClient).trackEvent(
          eq("prisonerbalance-migration-entity-migrated"),
          check {
            assertThat(it["nomisRootOffenderId"]).isEqualTo("10000")
            assertThat(it["dpsPrisonerId"]).isEqualTo("A0001BC")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of prisoner balance migrated`() {
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
        nomisPrisonerBalanceApiMock.stubGetRootOffenderIdsToMigrate(totalElements = 1, pageSize = 10, firstRootOffenderId = 10000)
        mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(nomisRootOffenderId = 10000, mapping = null)
        nomisPrisonerBalanceApiMock.stubGetPrisonerBalance(
          rootOffenderId = 10000,
          prisonNumber = "A0001BC",
          prisonerBalance(prisonNumber = "A0001BC").copy(
            accounts = listOf(
              PrisonerAccountDto(
                prisonId = "ASI",
                lastTransactionId = 180,
                accountCode = 2102,
                balance = BigDecimal.valueOf(25.70),
                holdBalance = BigDecimal.valueOf(2.75),
              ),
            ),
          ),
        )

        dpsApiMock.stubMigratePrisonerBalance(prisonNumber = "A0001BC")
        mappingApiMock.stubCreateMappingsForMigration(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PrisonerBalanceMappingDto(
                dpsId = "A0001BC",
                nomisRootOffenderId = 10000,
                mappingType = MIGRATED,
              ),
              existing = PrisonerBalanceMappingDto(
                dpsId = "A0001XX",
                nomisRootOffenderId = 10001,
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
        nomisPrisonerBalanceApiMock.verify(1, getRequestedFor(urlPathEqualTo("/finance/prisoners/10000/balance")))
      }

      @Test
      fun `will attempt create mapping once before failing`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/prisoner-balance"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "A0001BC")
            .withRequestBodyJsonPath("nomisRootOffenderId", 10000),
        )
      }

      @Test
      fun `will track telemetry for each offender migrated`() {
        verify(telemetryClient).trackEvent(
          eq("prisonerbalance-migration-duplicate"),
          check {
            assertThat(it["duplicateNomisRootOffenderId"]).isEqualTo("10000")
            assertThat(it["duplicateDpsPrisonerId"]).isEqualTo("A0001BC")
            assertThat(it["existingNomisRootOffenderId"]).isEqualTo("10001")
            assertThat(it["existingDpsPrisonerId"]).isEqualTo("A0001XX")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of prisoner balances (offenders) migrated`() {
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
              migrationType = MigrationType.PRISONER_BALANCE,
            ),
          )
        }
        webTestClient.post().uri("/migrate/prisoner-balance")
          .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerBalanceMigrationFilter())
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
              migrationType = MigrationType.PRISONER_BALANCE,
            ),
          )
        }
        webTestClient.post().uri("/migrate/prisoner-balance")
          .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerBalanceMigrationFilter())
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
              migrationType = MigrationType.PRISONER_BALANCE,
            ),
          )
        }
        nomisPrisonerBalanceApiMock.stubGetRootOffenderIdsToMigrate(totalElements = 1, pageSize = 10, firstRootOffenderId = 10000)
        mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(
          nomisRootOffenderId = 10000,
          mapping = PrisonerBalanceMappingDto(
            dpsId = "A0001BC",
            nomisRootOffenderId = 10000,
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
              migrationType = MigrationType.PRISONER_BALANCE,
            ),
          )
        }
        nomisPrisonerBalanceApiMock.stubGetRootOffenderIdsToMigrate(totalElements = 1, pageSize = 10, firstRootOffenderId = 10000)
        mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(
          nomisRootOffenderId = 10000,
          mapping = PrisonerBalanceMappingDto(
            dpsId = "A0001BC",
            nomisRootOffenderId = 10000,
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
        nomisPrisonerBalanceApiMock.stubGetRootOffenderIdsToMigrate(totalElements = 1, pageSize = 10, firstRootOffenderId = 10000)
        mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(
          nomisRootOffenderId = 10000,
          mapping = PrisonerBalanceMappingDto(
            dpsId = "A0001BC",
            nomisRootOffenderId = 10000,
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        performMigration()
      }
    }
  }

  private fun performMigration(body: PrisonerBalanceMigrationFilter = PrisonerBalanceMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/prisoner-balance")
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
      eq("prisonerbalance-migration-completed"),
      any(),
      isNull(),
    )
  }

  private fun stubMigratePrisonerBalances(nomisRootOffenderIds: List<Long>, vararg prisonerAccounts: PrisonerBalanceDto) {
    nomisApi.resetAll()
    dpsApiMock.resetAll()
    mappingApiMock.resetAll()
    nomisPrisonerBalanceApiMock.stubGetRootOffenderIdsToMigrate(totalElements = 2, pageSize = 10, firstRootOffenderId = 10000)
    prisonerAccounts.forEachIndexed { index, nomisPrisonerBalance ->
      nomisPrisonerBalanceApiMock.stubGetPrisonerBalance(rootOffenderId = nomisRootOffenderIds[index], prisonerBalance = nomisPrisonerBalance)
      mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(nomisRootOffenderId = nomisRootOffenderIds[index], mapping = null, dpsId = "A0001BC")
      mappingApiMock.stubGetPrisonerBalanceByNomisIdOrNull(nomisRootOffenderId = nomisRootOffenderIds[index], mapping = null, dpsId = "A0002BC")
      dpsApiMock.stubMigratePrisonerBalance("A0001BC")
      dpsApiMock.stubMigratePrisonerBalance("A0002BC")
    }
    mappingApiMock.stubCreateMappingsForMigration()
    mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = prisonerAccounts.size)
  }
}
