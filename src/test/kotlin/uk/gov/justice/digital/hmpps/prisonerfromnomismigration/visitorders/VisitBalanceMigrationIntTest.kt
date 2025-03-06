package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitorders

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerVisitOrderBalanceResponse
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
        nomisApi.stubGetPrisonerIds(totalElements = 2, pageSize = 10, firstOffenderNo = "A0001BC")
        mappingApiMock.stubGetByNomisPrisonNumberOrNull(
          nomisPrisonNumber = "A0001BC",
          mapping = VisitBalanceMappingDto(
            dpsId = "10000",
            nomisPrisonNumber = "A0001BC",
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetByNomisPrisonNumberOrNull(
          nomisPrisonNumber = "A0002BC",
          mapping = VisitBalanceMappingDto(
            dpsId = "20000",
            nomisPrisonNumber = "A0002BC",
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
        nomisApi.stubGetPrisonerIds(totalElements = 2, pageSize = 10, firstOffenderNo = "A0001BC")
        mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A0001BC", mapping = null)
        mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A0002BC", mapping = null)

        nomisVisitBalanceApiMock.stubGetVisitBalance(
          prisonNumber = "A0001BC",
          visitBalance(prisonNumber = "A0001BC").copy(remainingPrivilegedVisitOrders = 3),
        )
        nomisVisitBalanceApiMock.stubGetVisitBalance(
          prisonNumber = "A0002BC",
          visitBalance(prisonNumber = "A0002BC").copy(remainingPrivilegedVisitOrders = 4),
        )
        dpsApiMock.stubMigrateVisitBalance()
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 2)
        migrationResult = performMigration()
      }

      @Test
      fun `will get the count of the number offenders to migrate`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/ids/all")))
      }

      @Test
      fun `will get visit balance details for each offender`() {
        nomisVisitBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/A0001BC/visit-orders/balance")))
        nomisVisitBalanceApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/A0002BC/visit-orders/balance")))
      }

      @Test
      fun `will create mapping for each person and children`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/visit-balance/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "A0001BC")
            .withRequestBodyJsonPath("nomisPrisonNumber", "A0001BC"),
        )
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/visit-balance/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "A0002BC")
            .withRequestBodyJsonPath("nomisPrisonNumber", "A0002BC"),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo("A0002BC")
            assertThat(it["dpsPrisonerId"]).isEqualTo("A0002BC")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("visitbalance-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo("A0001BC")
            assertThat(it["dpsPrisonerId"]).isEqualTo("A0001BC")
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
          listOf("A0001BC", "A0002BC"),
          PrisonerVisitOrderBalanceResponse(
            remainingVisitOrders = 1,
            remainingPrivilegedVisitOrders = 4,
            visitOrderBalanceAdjustments = listOf(),
          ),

          PrisonerVisitOrderBalanceResponse(
            remainingVisitOrders = 2,
            remainingPrivilegedVisitOrders = 3,
            visitOrderBalanceAdjustments = listOf(),
          ),
        )

        migrationResult = performMigration()
        dpsRequests =
          VisitBalanceDpsApiExtension.getRequestBodies(postRequestedFor(urlPathMatching("/visits/allocation/prisoner/migrate")))
        mappingRequests =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/visit-balance/migrate")))
      }

      @Test
      fun `will send visit balance data to Dps`() {
        dpsRequests.find { it.prisonerId == "A0001BC" }?.let {
          assertThat(it.voBalance).isEqualTo(1)
          assertThat(it.pvoBalance).isEqualTo(4)
          assertThat(it.lastVoAllocationDate).isEqualTo(LocalDate.parse("2025-01-15"))
        }
        dpsRequests.find { it.prisonerId == "A0002BC" }?.let {
          assertThat(it.voBalance).isEqualTo(2)
          assertThat(it.pvoBalance).isEqualTo(3)
          assertThat(it.lastVoAllocationDate).isEqualTo(LocalDate.parse("2025-01-15"))
        }
      }

      @Test
      fun `will create mappings for nomis person to dps visit balance`() {
        with(mappingRequests.find { it.nomisPrisonNumber == "A0001BC" } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(VisitBalanceMappingDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisPrisonNumber).isEqualTo("A0001BC")
          assertThat(dpsId).isEqualTo("A0001BC")
        }
        with(mappingRequests.find { it.nomisPrisonNumber == "A0002BC" } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(VisitBalanceMappingDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisPrisonNumber).isEqualTo("A0002BC")
          assertThat(dpsId).isEqualTo("A0002BC")
        }
      }
    }

    @Nested
    inner class MappingErrorRecovery {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonerIds(totalElements = 1, pageSize = 10, firstOffenderNo = "A0001BC")
        mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A0001BC", mapping = null)
        nomisVisitBalanceApiMock.stubGetVisitBalance(
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
        nomisVisitBalanceApiMock.verify(1, getRequestedFor(urlPathEqualTo("/prisoners/A0001BC/visit-orders/balance")))
      }

      @Test
      fun `will attempt create mapping twice before succeeding`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/visit-balance/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "A0001BC")
            .withRequestBodyJsonPath("nomisPrisonNumber", "A0001BC"),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo("A0001BC")
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
        nomisApi.stubGetPrisonerIds(totalElements = 1, pageSize = 10, firstOffenderNo = "A0001BC")
        mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A0001BC", mapping = null)
        nomisVisitBalanceApiMock.stubGetVisitBalance(
          prisonNumber = "A0001BC",
          visitBalance(prisonNumber = "A0001BC").copy(remainingPrivilegedVisitOrders = 3),
        )
        dpsApiMock.stubMigrateVisitBalance()
        mappingApiMock.stubCreateMappingsForMigration(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = VisitBalanceMappingDto(
                dpsId = "DPS-A0001BC",
                nomisPrisonNumber = "A0001BC",
                mappingType = MIGRATED,
              ),
              existing = VisitBalanceMappingDto(
                dpsId = "DPS-A0001XX",
                nomisPrisonNumber = "A0001BC",
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
        nomisVisitBalanceApiMock.verify(1, getRequestedFor(urlPathEqualTo("/prisoners/A0001BC/visit-orders/balance")))
      }

      @Test
      fun `will attempt create mapping once before failing`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/visit-balance/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "A0001BC")
            .withRequestBodyJsonPath("nomisPrisonNumber", "A0001BC"),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("nomis-migration-visitbalance-duplicate"),
          check {
            assertThat(it["duplicateNomisPrisonNumber"]).isEqualTo("A0001BC")
            assertThat(it["duplicateDpsPrisonerId"]).isEqualTo("DPS-A0001BC")
            assertThat(it["existingNomisPrisonNumber"]).isEqualTo("A0001BC")
            assertThat(it["existingDpsPrisonerId"]).isEqualTo("DPS-A0001XX")
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
      nomisApi.stubGetPrisonerIds(totalElements = 2, pageSize = 10, firstOffenderNo = "A0001BC")
      mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A0001BC", mapping = null)
      mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A0002BC", mapping = null)
      nomisVisitBalanceApiMock.stubGetVisitBalance(
        prisonNumber = "A0001BC",
        visitBalance(prisonNumber = "A0001BC").copy(remainingPrivilegedVisitOrders = 3),
      )
      nomisVisitBalanceApiMock.stubGetVisitBalance(
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

  private fun stubMigrateVisitBalances(prisonNumbers: List<String>, vararg visitBalances: PrisonerVisitOrderBalanceResponse) {
    nomisApi.resetAll()
    dpsApiMock.resetAll()
    mappingApiMock.resetAll()
    nomisApi.stubGetPrisonerIds(totalElements = 2, pageSize = 10, firstOffenderNo = "A0001BC")
    visitBalances.forEachIndexed { index, nomisVisitBalance ->
      nomisVisitBalanceApiMock.stubGetVisitBalance(prisonNumbers[index], nomisVisitBalance)
      mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = prisonNumbers[index], mapping = null)
      dpsApiMock.stubMigrateVisitBalance()
    }
    mappingApiMock.stubCreateMappingsForMigration()
    mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = visitBalances.size)
  }
}
