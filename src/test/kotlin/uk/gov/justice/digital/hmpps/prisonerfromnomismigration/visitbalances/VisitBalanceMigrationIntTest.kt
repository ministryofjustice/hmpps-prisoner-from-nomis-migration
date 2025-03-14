package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
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
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerVisitBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VisitBalanceMigrationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisVisitBalanceApiMock: VisitBalanceNomisApiMockServer

  private val dpsApiMock = VisitBalanceDpsApiExtension.dpsVisitBalanceServer

  @Autowired
  private lateinit var mappingApiMock: VisitBalanceMappingApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/visit-balance")
  inner class MigrateVisitBalance {
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
        webTestClient.post().uri("/migrate/visit-balance")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(VisitBalanceMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/visit-balance")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(VisitBalanceMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/visit-balance")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(VisitBalanceMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 2, pageSize = 10, firstVisitBalanceId = 10000)
        mappingApiMock.stubGetByNomisIdOrNull(
          nomisVisitBalanceId = 10000,
          mapping = VisitBalanceMappingDto(
            dpsId = "A0001BC",
            nomisVisitBalanceId = 10000,
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetByNomisIdOrNull(
          nomisVisitBalanceId = 20000,
          mapping = VisitBalanceMappingDto(
            dpsId = "A0002BC",
            nomisVisitBalanceId = 20000,
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will not bother retrieving any visit balance details`() {
        nomisVisitBalanceApiMock.verify(0, getRequestedFor(urlPathEqualTo("/visit-balance/A0001BC")))
        nomisVisitBalanceApiMock.verify(0, getRequestedFor(urlPathEqualTo("/visit-balance/B0002BC")))
      }

      @Test
      fun `will mark migration as complete`() {
        webTestClient.get().uri("/migrate/visit-balance/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
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
        nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 2, pageSize = 10, firstVisitBalanceId = 10000L)
        mappingApiMock.stubGetByNomisIdOrNull(nomisVisitBalanceId = 10000, mapping = null)
        mappingApiMock.stubGetByNomisIdOrNull(nomisVisitBalanceId = 20000, mapping = null)

        nomisVisitBalanceApiMock.stubGetVisitBalance(
          nomisVisitBalanceId = 10000,
          visitBalance = visitBalance(prisonNumber = "A0001BC").copy(remainingPrivilegedVisitOrders = 3),
        )
        nomisVisitBalanceApiMock.stubGetVisitBalance(
          nomisVisitBalanceId = 10001,
          visitBalance = visitBalance(prisonNumber = "A0002BC").copy(remainingPrivilegedVisitOrders = 4),
        )
        dpsApiMock.stubMigrateVisitBalance()
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 2)
        migrationResult = performMigration()
      }

      @Test
      fun `will get the count of the number offenders to migrate`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/visit-balances/ids")))
      }

      @Test
      fun `will get visit balance details for each offender`() {
        nomisVisitBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/visit-balances/10000")))
        nomisVisitBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/visit-balances/10001")))
      }

      @Test
      fun `will create mapping for each person and children`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/visit-balance"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "A0001BC")
            .withRequestBodyJsonPath("nomisVisitBalanceId", 10000),
        )
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/visit-balance"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "A0002BC")
            .withRequestBodyJsonPath("nomisVisitBalanceId", 10001),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-migration-entity-migrated"),
          check {
            assertThat(it["nomisVisitBalanceId"]).isEqualTo("10000")
            assertThat(it["dpsPrisonerId"]).isEqualTo("A0001BC")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("visitbalance-migration-entity-migrated"),
          check {
            assertThat(it["nomisVisitBalanceId"]).isEqualTo("10001")
            assertThat(it["dpsPrisonerId"]).isEqualTo("A0002BC")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of prisoners migrated`() {
        webTestClient.get().uri("/migrate/visit-balance/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
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
      private lateinit var dpsRequests: List<VisitAllocationPrisonerMigrationDto>
      private lateinit var mappingRequests: List<VisitBalanceMappingDto>
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        stubMigrateVisitBalances(
          listOf(10000, 10001),
          PrisonerVisitBalanceResponse(
            prisonNumber = "A0001BC",
            remainingVisitOrders = 1,
            remainingPrivilegedVisitOrders = 4,
            lastIEPAllocationDate = LocalDate.parse("2025-02-01"),
          ),

          PrisonerVisitBalanceResponse(
            prisonNumber = "A0002BC",
            remainingVisitOrders = 2,
            remainingPrivilegedVisitOrders = 3,
            lastIEPAllocationDate = null,
          ),
        )

        migrationResult = performMigration()
        dpsRequests =
          VisitBalanceDpsApiExtension.getRequestBodies(postRequestedFor(urlPathMatching("/visits/allocation/prisoner/migrate")))
        mappingRequests =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/visit-balance")))
      }

      @Test
      fun `will send visit balance data to Dps`() {
        dpsRequests.find { it.prisonerId == "A0001BC" }?.let {
          assertThat(it.voBalance).isEqualTo(1)
          assertThat(it.pvoBalance).isEqualTo(4)
          assertThat(it.lastVoAllocationDate).isEqualTo(LocalDate.parse("2025-02-01"))
        }
        dpsRequests.find { it.prisonerId == "A0002BC" }?.let {
          assertThat(it.voBalance).isEqualTo(2)
          assertThat(it.pvoBalance).isEqualTo(3)
          assertThat(it.lastVoAllocationDate).isEqualTo(LocalDate.parse("2002-02-01"))
        }
      }

      @Test
      fun `will create mappings for nomis person to dps visit balance`() {
        with(mappingRequests.find { it.nomisVisitBalanceId == 10000L } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisVisitBalanceId).isEqualTo(10000)
          assertThat(dpsId).isEqualTo("A0001BC")
        }
        with(mappingRequests.find { it.nomisVisitBalanceId == 10001L } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisVisitBalanceId).isEqualTo(10001)
          assertThat(dpsId).isEqualTo("A0002BC")
        }
      }
    }

    @Nested
    inner class MappingErrorRecovery {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 1, pageSize = 10, firstVisitBalanceId = 10000)
        mappingApiMock.stubGetByNomisIdOrNull(nomisVisitBalanceId = 10000, mapping = null)
        nomisVisitBalanceApiMock.stubGetVisitBalance(
          nomisVisitBalanceId = 10000,
          prisonNumber = "A0001BC",
          visitBalance(prisonNumber = "A0001BC").copy(remainingPrivilegedVisitOrders = 3),
        )
        dpsApiMock.stubMigrateVisitBalance()
        mappingApiMock.stubCreateMappingsForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will get details for person only once`() {
        nomisVisitBalanceApiMock.verify(1, getRequestedFor(urlPathEqualTo("/visit-balances/10000")))
      }

      @Test
      fun `will attempt create mapping twice before succeeding`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/visit-balance"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "A0001BC")
            .withRequestBodyJsonPath("nomisVisitBalanceId", 10000),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-migration-entity-migrated"),
          check {
            assertThat(it["nomisVisitBalanceId"]).isEqualTo("10000")
            assertThat(it["dpsPrisonerId"]).isEqualTo("A0001BC")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of visit balance migrated`() {
        webTestClient.get().uri("/migrate/visit-balance/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
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
        nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 1, pageSize = 10, firstVisitBalanceId = 10000)
        mappingApiMock.stubGetByNomisIdOrNull(nomisVisitBalanceId = 10000, mapping = null)
        nomisVisitBalanceApiMock.stubGetVisitBalance(
          nomisVisitBalanceId = 10000,
          prisonNumber = "A0001BC",
          visitBalance(prisonNumber = "A0001BC").copy(remainingPrivilegedVisitOrders = 3),
        )
        dpsApiMock.stubMigrateVisitBalance()
        mappingApiMock.stubCreateMappingsForMigration(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = VisitBalanceMappingDto(
                dpsId = "A0001BC",
                nomisVisitBalanceId = 10000,
                mappingType = MIGRATED,
              ),
              existing = VisitBalanceMappingDto(
                dpsId = "A0001XX",
                nomisVisitBalanceId = 10001,
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
      fun `will get details for person only once`() {
        nomisVisitBalanceApiMock.verify(1, getRequestedFor(urlPathEqualTo("/visit-balances/10000")))
      }

      @Test
      fun `will attempt create mapping once before failing`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/visit-balance"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "A0001BC")
            .withRequestBodyJsonPath("nomisVisitBalanceId", 10000),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("nomis-migration-visitbalance-duplicate"),
          check {
            assertThat(it["duplicateNomisVisitBalanceId"]).isEqualTo("10000")
            assertThat(it["duplicateDpsPrisonerId"]).isEqualTo("A0001BC")
            assertThat(it["existingNomisVisitBalanceId"]).isEqualTo("10001")
            assertThat(it["existingDpsPrisonerId"]).isEqualTo("A0001XX")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of visit balances (offenders) migrated`() {
        webTestClient.get().uri("/migrate/visit-balance/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("0")
      }
    }
  }

  @Nested
  @DisplayName("GET /migrate/visit-balance/history")
  inner class GetAll {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
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
            migrationType = MigrationType.VISIT_BALANCE,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.VISIT_BALANCE,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T03:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.VISIT_BALANCE,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-03T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-03T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-03T03:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.VISIT_BALANCE,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/visit-balance/history")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/visit-balance/history")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/visit-balance/history")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `can read all records`() {
      webTestClient.get().uri("/migrate/visit-balance/history")
        .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(4)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-02T02:00:00")
        .jsonPath("$[2].migrationId").isEqualTo("2020-01-02T00:00:00")
        .jsonPath("$[3].migrationId").isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    fun `can also use the syscon generic role`() {
      webTestClient.get().uri("/migrate/visit-balance/history")
        .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  @DisplayName("GET /migrate/visit-balance/history/{migrationId}")
  inner class Get {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
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
            migrationType = MigrationType.VISIT_BALANCE,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/visit-balance/history/2020-01-01T00:00:00")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/visit-balance/history/2020-01-01T00:00:00")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/visit-balance/history/2020-01-01T00:00:00")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `can read record`() {
      webTestClient.get().uri("/migrate/visit-balance/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.status").isEqualTo("COMPLETED")
    }

    @Test
    fun `can also use the syscon generic role`() {
      webTestClient.get().uri("/migrate/visit-balance/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  @DisplayName("GET /migrate/visit-balance/active-migration")
  inner class GetActiveMigration {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
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
            migrationType = MigrationType.VISIT_BALANCE,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2019-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2019-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2019-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.VISIT_BALANCE,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/visit-balance/active-migration")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/visit-balance/active-migration")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/visit-balance/active-migration")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/visit-balance/active-migration")
        .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").doesNotExist()
        .jsonPath("$.whenStarted").doesNotExist()
        .jsonPath("$.recordsMigrated").doesNotExist()
        .jsonPath("$.estimatedRecordCount").doesNotExist()
        .jsonPath("$.status").doesNotExist()
        .jsonPath("$.migrationType").doesNotExist()
    }

    @Test
    internal fun `can read active migration data`() {
      mappingApiMock.stubGetMigrationDetails(migrationId = "2020-01-01T00%3A00%3A00", count = 123456)
      webTestClient.get().uri("/migrate/visit-balance/active-migration")
        .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.whenStarted").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.recordsMigrated").isEqualTo(123456)
        .jsonPath("$.toBeProcessedCount").isEqualTo(0)
        .jsonPath("$.beingProcessedCount").isEqualTo(0)
        .jsonPath("$.recordsFailed").isEqualTo(0)
        .jsonPath("$.estimatedRecordCount").isEqualTo(123567)
        .jsonPath("$.status").isEqualTo("STARTED")
        .jsonPath("$.migrationType").isEqualTo("VISIT_BALANCE")
    }

    @Test
    fun `can also use the syscon generic role`() {
      mappingApiMock.stubGetMigrationDetails(migrationId = "2020-01-01T00%3A00%3A00", count = 123456)
      webTestClient.get().uri("/migrate/visit-balance/active-migration")
        .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  @DisplayName("POST /migrate/visit-balance/{migrationId}/cancel")
  inner class TerminateMigrationVisitBalance {
    @BeforeEach
    internal fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/visit-balance/{migrationId}/cancel", "some id")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/visit-balance/{migrationId}/cancel", "some id")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/visit-balance/{migrationId}/cancel", "some id")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/visit-balance/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    internal fun `will terminate a running migration`() {
      // slow the API calls so there is time to cancel before it completes
      nomisApi.setGlobalFixedDelay(1000)
      nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 2, pageSize = 10, firstVisitBalanceId = 10000)
      mappingApiMock.stubGetByNomisIdOrNull(nomisVisitBalanceId = 10000, mapping = null)
      mappingApiMock.stubGetByNomisIdOrNull(nomisVisitBalanceId = 20000, mapping = null)
      nomisVisitBalanceApiMock.stubGetVisitBalance(
        nomisVisitBalanceId = 10000,
        prisonNumber = "A0001BC",
        visitBalance(prisonNumber = "A0001BC").copy(remainingPrivilegedVisitOrders = 3),
      )
      nomisVisitBalanceApiMock.stubGetVisitBalance(
        nomisVisitBalanceId = 20000,
        prisonNumber = "A0002BC",
        visitBalance(prisonNumber = "A0002BC").copy(remainingPrivilegedVisitOrders = 4),
      )

      dpsApiMock.stubMigrateVisitBalance()
      mappingApiMock.stubCreateMappingsForMigration()
      mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 2)

      val migrationId = performMigration().migrationId

      webTestClient.post().uri("/migrate/visit-balance/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/visit-balance/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/visit-balance/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationId)
          .jsonPath("$.status").isEqualTo("CANCELLED")
      }
    }
  }

  private fun performMigration(body: VisitBalanceMigrationFilter = VisitBalanceMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/visit-balance")
    .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
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

  private fun stubMigrateVisitBalances(visitBalanceIds: List<Long>, vararg visitBalances: PrisonerVisitBalanceResponse) {
    nomisApi.resetAll()
    dpsApiMock.resetAll()
    mappingApiMock.resetAll()
    nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 2, pageSize = 10, firstVisitBalanceId = 10000)
    visitBalances.forEachIndexed { index, nomisVisitBalance ->
      nomisVisitBalanceApiMock.stubGetVisitBalance(nomisVisitBalanceId = visitBalanceIds[index], visitBalance = nomisVisitBalance)
      mappingApiMock.stubGetByNomisIdOrNull(nomisVisitBalanceId = visitBalanceIds[index], mapping = null)
      dpsApiMock.stubMigrateVisitBalance()
    }
    mappingApiMock.stubCreateMappingsForMigration()
    mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = visitBalances.size)
  }
}
