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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceDetailResponse
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
        mappingApiMock.stubGetVisitBalanceByNomisIdOrNull(
          nomisVisitBalanceId = 10000,
          mapping = VisitBalanceMappingDto(
            dpsId = "A0001BC",
            nomisVisitBalanceId = 10000,
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetVisitBalanceByNomisIdOrNull(
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
        nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 2, pageSize = 10, firstVisitBalanceId = 10000L)
        mappingApiMock.stubGetVisitBalanceByNomisIdOrNull(nomisVisitBalanceId = 10000, mapping = null)
        mappingApiMock.stubGetVisitBalanceByNomisIdOrNull(nomisVisitBalanceId = 20000, mapping = null)

        nomisVisitBalanceApiMock.stubGetVisitBalanceDetail(
          nomisVisitBalanceId = 10000,
          visitBalance = visitBalanceDetail(prisonNumber = "A0001BC").copy(remainingPrivilegedVisitOrders = 3),
        )
        nomisVisitBalanceApiMock.stubGetVisitBalanceDetail(
          nomisVisitBalanceId = 10001,
          visitBalance = visitBalanceDetail(prisonNumber = "A0002BC").copy(remainingPrivilegedVisitOrders = 4),
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
      private lateinit var dpsRequests: List<VisitAllocationPrisonerMigrationDto>
      private lateinit var mappingRequests: List<VisitBalanceMappingDto>
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        stubMigrateVisitBalances(
          listOf(10000, 10001),
          VisitBalanceDetailResponse(
            prisonNumber = "A0001BC",
            remainingVisitOrders = 1,
            remainingPrivilegedVisitOrders = 4,
            lastIEPAllocationDate = LocalDate.parse("2025-02-01"),
          ),

          VisitBalanceDetailResponse(
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
          assertThat(it.lastVoAllocationDate).isEqualTo(LocalDate.now().minusDays(14))
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
        mappingApiMock.stubGetVisitBalanceByNomisIdOrNull(nomisVisitBalanceId = 10000, mapping = null)
        nomisVisitBalanceApiMock.stubGetVisitBalanceDetail(
          nomisVisitBalanceId = 10000,
          prisonNumber = "A0001BC",
          visitBalanceDetail(prisonNumber = "A0001BC").copy(remainingPrivilegedVisitOrders = 3),
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
        nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 1, pageSize = 10, firstVisitBalanceId = 10000)
        mappingApiMock.stubGetVisitBalanceByNomisIdOrNull(nomisVisitBalanceId = 10000, mapping = null)
        nomisVisitBalanceApiMock.stubGetVisitBalanceDetail(
          nomisVisitBalanceId = 10000,
          prisonNumber = "A0001BC",
          visitBalanceDetail(prisonNumber = "A0001BC").copy(remainingPrivilegedVisitOrders = 3),
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
              migrationType = MigrationType.VISIT_BALANCE,
            ),
          )
        }
        webTestClient.post().uri("/migrate/visit-balance")
          .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(VisitBalanceMigrationFilter())
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
              migrationType = MigrationType.VISIT_BALANCE,
            ),
          )
        }
        webTestClient.post().uri("/migrate/visit-balance")
          .headers(setAuthorisation(roles = listOf("MIGRATE_VISIT_BALANCE")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(VisitBalanceMigrationFilter())
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
              migrationType = MigrationType.VISIT_BALANCE,
            ),
          )
        }
        nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 1, pageSize = 10, firstVisitBalanceId = 10000)
        mappingApiMock.stubGetVisitBalanceByNomisIdOrNull(
          nomisVisitBalanceId = 10000,
          mapping = VisitBalanceMappingDto(
            dpsId = "A0001BC",
            nomisVisitBalanceId = 10000,
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
              migrationType = MigrationType.VISIT_BALANCE,
            ),
          )
        }
        nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 1, pageSize = 10, firstVisitBalanceId = 10000)
        mappingApiMock.stubGetVisitBalanceByNomisIdOrNull(
          nomisVisitBalanceId = 10000,
          mapping = VisitBalanceMappingDto(
            dpsId = "A0001BC",
            nomisVisitBalanceId = 10000,
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
        nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 1, pageSize = 10, firstVisitBalanceId = 10000)
        mappingApiMock.stubGetVisitBalanceByNomisIdOrNull(
          nomisVisitBalanceId = 10000,
          mapping = VisitBalanceMappingDto(
            dpsId = "A0001BC",
            nomisVisitBalanceId = 10000,
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        performMigration()
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

  private fun stubMigrateVisitBalances(visitBalanceIds: List<Long>, vararg visitBalances: VisitBalanceDetailResponse) {
    nomisApi.resetAll()
    dpsApiMock.resetAll()
    mappingApiMock.resetAll()
    nomisVisitBalanceApiMock.stubGetVisitBalanceIds(totalElements = 2, pageSize = 10, firstVisitBalanceId = 10000)
    visitBalances.forEachIndexed { index, nomisVisitBalance ->
      nomisVisitBalanceApiMock.stubGetVisitBalanceDetail(nomisVisitBalanceId = visitBalanceIds[index], visitBalance = nomisVisitBalance)
      mappingApiMock.stubGetVisitBalanceByNomisIdOrNull(nomisVisitBalanceId = visitBalanceIds[index], mapping = null)
      dpsApiMock.stubMigrateVisitBalance()
    }
    mappingApiMock.stubCreateMappingsForMigration()
    mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = visitBalances.size)
  }
}
