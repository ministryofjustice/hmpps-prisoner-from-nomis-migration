package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterAll
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
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.StaffMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.StaffMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.MigratedUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.MigratedUserAccount
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffMigrationIntTest(
  @Autowired private val nomisApiMock: StaffNomisApiMockServer,
  @Autowired private val mappingApiMock: StaffMappingApiMockServer,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,

) : StaffIntegrationTestBase() {
  private val dpsApiMock = StaffDpsApiExtension.dpsStaffServer

  override fun resetTelemetryClient() {}

  internal fun setupMigrationTest() = runBlocking {
    migrationHistoryRepository.deleteAll()

    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    StaffDpsApiExtension.resetAndDisableResetBeforeEach()

    tearDownTelemetryClient()
  }

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  @Nested
  @DisplayName("POST /migrate/staff")
  inner class MigrateStaff {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/staff")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/staff")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/staff")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult
      private val dpsStaffId = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetStaffIds(content = listOf(StaffIdResponse(staffId = 1234)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 0, content = listOf(StaffIdResponse(staffId = 1234)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 1234, content = emptyList())

        mappingApiMock.stubGetStaffByNomisIdOrNull(mapping = null)
        nomisApiMock.stubGetStaffDetailsById()
        dpsApiMock.stubMigrateStaff(dpsStaffId = dpsStaffId)

        mappingApiMock.stubCreateMapping()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will track telemetry migration started`() {
        verify(telemetryClient).trackEvent(eq("staff-migration-started"), any(), isNull())
      }

      @Test
      fun `will get the staff to migrate`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/staff/ids")))
      }

      @Test
      fun `will get details for staff`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/staff/id/1234")))
      }

      @Test
      fun `will create mapping for staff`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/staff"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", dpsStaffId.toString())
            .withRequestBodyJsonPath("nomisId", 1234),
        )
      }

      @Test
      fun `will track telemetry for staff migrated`() {
        verify(telemetryClient).trackEvent(
          eq("staff-migration-entity-migrated"),
          check {
            assertThat(it["nomisId"]).isEqualTo("1234")
            assertThat(it["dpsId"]).isEqualTo(dpsStaffId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of staff migrated`() {
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
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetStaffIds(content = listOf(StaffIdResponse(staffId = 1234)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 0, content = listOf(StaffIdResponse(staffId = 1234)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 1234, content = emptyList())

        mappingApiMock.stubGetStaffByNomisIdOrNull(
          mapping = StaffMappingDto(
            dpsId = "4321",
            nomisId = 1234,
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )

        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will pass filter to get all ids endpoint for initial count and first page`() {
        nomisApiMock.verify(
          getRequestedFor(urlPathEqualTo("/staff/ids"))
            .withQueryParam("page", equalTo("0"))
            .withQueryParam("size", equalTo("1")),

        )
        nomisApiMock.verify(
          getRequestedFor(urlPathEqualTo("/staff/ids/all-from-id"))
            .withQueryParam("staffId", equalTo("0"))
            .withQueryParam("size", equalTo("10")),
        )
      }

      @Test
      fun `will not bother retrieving any staff details`() {
        nomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/staff/1234")))
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
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathNomisToDPSMapping {
      private lateinit var migrationResult: MigrationResult

      private val dpsStaffId = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetStaffIds(content = listOf(StaffIdResponse(staffId = 1234), StaffIdResponse(staffId = 2345)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 0, content = listOf(StaffIdResponse(staffId = 1234), StaffIdResponse(staffId = 2345)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 2345, content = emptyList())

        mappingApiMock.stubGetStaffByNomisIdOrNull(mapping = null)
        mappingApiMock.stubGetStaffByNomisIdOrNull(nomisId = 2345, mapping = null)
        nomisApiMock.stubGetStaffDetailsById()
        nomisApiMock.stubGetStaffDetailsById(nomisStaffId = 2345)

        dpsApiMock.stubMigrateStaff(dpsStaffId = dpsStaffId)
        dpsApiMock.stubMigrateStaff(nomisStaffId = 2345, dpsStaffId = dpsStaffId)

        mappingApiMock.stubCreateMapping()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 2)
        migrationResult = performMigration()
      }

      @Test
      fun `will map and transform staff ids`() {
        val migrationRequests: List<UserMigrationRequest> = StaffDpsApiExtension.getRequestBodies(
          postRequestedFor(urlPathEqualTo("/migrate/user")),
        )

        with(migrationRequests.first { it.user.id == "1234" }) {
          with(user) {
            assertThat(id).isEqualTo("1234")
            assertThat(email).isEqualTo("john.smith@justice.gov.uk")
            assertThat(firstName).isEqualTo("JOHN")
            assertThat(lastName).isEqualTo("SMITH")
            assertThat(status).isEqualTo(MigratedUser.Status.ACTIVE)
            assertThat(createdTimestamp).isEqualTo(LocalDateTime.parse("2016-08-01T10:55:00"))
            assertThat(createdBy).isEqualTo("KOFEADDY")
            assertThat(modifiedTimestamp).isEqualTo(LocalDateTime.parse("2017-08-01T10:55:00"))
            assertThat(modifiedBy).isEqualTo("KOFE_MOD")
          }
          assertThat(accounts.size).isEqualTo(1)
          with(accounts[0]) {
            assertThat(username).isEqualTo("JOHNSMITH_ADM")
            assertThat(accountType).isEqualTo(MigratedUserAccount.AccountType.ADMIN)
            assertThat(accountStatus).isEqualTo(MigratedUserAccount.AccountStatus.OPEN)
            assertThat(lastLoggedIn).isEqualTo(LocalDateTime.parse("2026-03-17T12:30:00"))
            assertThat(activeCaseloadId).isEqualTo("MDI")
            assertThat(createdTimestamp).isEqualTo(LocalDateTime.parse("2016-08-01T10:55:00"))
            assertThat(createdBy).isEqualTo("KOFEADDY")
            assertThat(modifiedTimestamp).isEqualTo(LocalDateTime.parse("2017-08-01T10:55:00"))
            assertThat(modifiedBy).isEqualTo("KOFE_MOD")
          }
          assertThat(accessibleCaseloads!!.size).isEqualTo(3)
          with(accessibleCaseloads[0]) {
            assertThat(username).isEqualTo("JOHNSMITH_ADM")
            assertThat(caseloadId).isEqualTo("LEI")
            assertThat(createdTimestamp).isEqualTo(LocalDateTime.parse("2016-08-01T10:55:00"))
            assertThat(createdBy).isEqualTo("KOFEADDY")
          }
          with(accessibleCaseloads[1]) {
            assertThat(username).isEqualTo("JOHNSMITH_ADM")
            assertThat(caseloadId).isEqualTo("MDI")
            assertThat(createdTimestamp).isEqualTo(LocalDateTime.parse("2016-08-01T10:55:00"))
            assertThat(createdBy).isEqualTo("KOFEADDY")
          }
          with(accessibleCaseloads[2]) {
            assertThat(username).isEqualTo("JOHNSMITH_ADM")
            assertThat(caseloadId).isEqualTo("NWEB")
            assertThat(createdTimestamp).isEqualTo(LocalDateTime.parse("2016-08-01T10:55:00"))
            assertThat(createdBy).isEqualTo("KOFEADDY")
          }
          assertThat(roles!!.size).isEqualTo(2)
          with(roles[0]) {
            assertThat(username).isEqualTo("JOHNSMITH_ADM")
            assertThat(roleCode).isEqualTo("DPS_CODE_1")
            assertThat(createdTimestamp).isEqualTo(LocalDateTime.parse("2016-08-01T10:55:00"))
            assertThat(createdBy).isEqualTo("KOFEADDY")
          }
          with(roles[1]) {
            assertThat(username).isEqualTo("JOHNSMITH_ADM")
            assertThat(roleCode).isEqualTo("DPS_CODE_2")
            assertThat(createdTimestamp).isEqualTo(LocalDateTime.parse("2016-08-01T10:55:00"))
            assertThat(createdBy).isEqualTo("KOFEADDY")
          }
        }
      }

      @Test
      fun `will create mappings for nomis id to dps staff`() {
        val mappingRequests: List<StaffMappingDto> =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/staff")))

        with(mappingRequests.find { it.nomisId == 1234L } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisId).isEqualTo(1234)
          assertThat(dpsId).isEqualTo(dpsStaffId.toString())
        }
        with(mappingRequests.find { it.nomisId == 2345L } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisId).isEqualTo(2345)
          assertThat(dpsId).isEqualTo(dpsStaffId.toString())
        }
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class MappingErrorRecovery {
      private lateinit var migrationResult: MigrationResult
      private val dpsStaffId = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setupMigrationTest()
        nomisApiMock.stubGetStaffIds(content = listOf(StaffIdResponse(staffId = 1234)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 0, content = listOf(StaffIdResponse(staffId = 1234)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 1234, content = emptyList())

        mappingApiMock.stubGetStaffByNomisIdOrNull(mapping = null)
        nomisApiMock.stubGetStaffDetailsById()
        dpsApiMock.stubMigrateStaff(dpsStaffId = dpsStaffId)

        mappingApiMock.stubCreateMappingFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will get details only once`() {
        nomisApiMock.verify(1, getRequestedFor(urlPathEqualTo("/staff/id/1234")))
      }

      @Test
      fun `will attempt create mapping twice before succeeding`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/staff"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", dpsStaffId.toString())
            .withRequestBodyJsonPath("nomisId", 1234),
        )
      }

      @Test
      fun `will track telemetry for staff migrated`() {
        verify(telemetryClient).trackEvent(
          eq("staff-migration-entity-migrated"),
          check {
            assertThat(it["nomisId"]).isEqualTo("1234")
            assertThat(it["dpsId"]).isEqualTo(dpsStaffId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of staff migrated`() {
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
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class DuplicateMappingErrorHandling {
      private lateinit var migrationResult: MigrationResult
      private val dpsStaffId = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetStaffIds(content = listOf(StaffIdResponse(staffId = 1234)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 0, content = listOf(StaffIdResponse(staffId = 1234)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 1234, content = emptyList())
        mappingApiMock.stubGetStaffByNomisIdOrNull(mapping = null)
        nomisApiMock.stubGetStaffDetailsById()

        dpsApiMock.stubMigrateStaff(dpsStaffId = dpsStaffId)
        mappingApiMock.stubCreateMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = StaffMappingDto(
                dpsId = dpsStaffId.toString(),
                nomisId = 1234,
                mappingType = MIGRATED,
              ),
              existing = StaffMappingDto(
                dpsId = dpsStaffId.toString(),
                nomisId = 2345,
                mappingType = MIGRATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will get details for staff only once`() {
        nomisApiMock.verify(1, getRequestedFor(urlPathEqualTo("/staff/id/1234")))
      }

      @Test
      fun `will attempt create mapping once before failing`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/staff"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", dpsStaffId)
            .withRequestBodyJsonPath("nomisId", 1234),
        )
      }

      @Test
      fun `will track telemetry for each offender migrated`() {
        verify(telemetryClient).trackEvent(
          eq("staff-migration-duplicate"),
          check {
            assertThat(it["duplicateNomisId"]).isEqualTo("1234")
            assertThat(it["duplicateDpsId"]).isEqualTo(dpsStaffId.toString())
            assertThat(it["existingNomisId"]).isEqualTo("2345")
            assertThat(it["existingDpsId"]).isEqualTo(dpsStaffId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number migrated`() {
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
      @BeforeEach
      fun setup() = setupMigrationTest()

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
              migrationType = MigrationType.STAFF,
            ),
          )
        }
        webTestClient.post().uri("/migrate/staff")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .contentType(MediaType.APPLICATION_JSON)
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
              migrationType = MigrationType.STAFF,
            ),
          )
        }
        webTestClient.post().uri("/migrate/staff")
          .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .contentType(MediaType.APPLICATION_JSON)
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
              migrationType = MigrationType.STAFF,
            ),
          )
        }
        nomisApiMock.stubGetStaffIds(content = listOf(StaffIdResponse(staffId = 1234)))

        nomisApiMock.stubGetStaffIdsFromId(staffId = 0, content = listOf(StaffIdResponse(staffId = 1234)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 1234, content = emptyList())

        mappingApiMock.stubGetStaffByNomisIdOrNull(
          nomisId = 1234,
          mapping = StaffMappingDto(
            dpsId = "4321",
            nomisId = 1234,
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
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
              migrationType = MigrationType.STAFF,
            ),
          )
        }
        nomisApiMock.stubGetStaffIds(content = listOf(StaffIdResponse(staffId = 1234)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 0, content = listOf(StaffIdResponse(staffId = 1234)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 1234, content = emptyList())

        mappingApiMock.stubGetStaffByNomisIdOrNull(
          nomisId = 1234,
          mapping = StaffMappingDto(
            dpsId = "4321",
            nomisId = 1234,
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
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
        nomisApiMock.stubGetStaffIds(content = listOf(StaffIdResponse(staffId = 1234)))

        nomisApiMock.stubGetStaffIdsFromId(staffId = 0, content = listOf(StaffIdResponse(staffId = 1234)))
        nomisApiMock.stubGetStaffIdsFromId(staffId = 1234, content = emptyList())

        mappingApiMock.stubGetStaffByNomisIdOrNull(
          nomisId = 1234,
          mapping = StaffMappingDto(
            dpsId = "4321",
            nomisId = 1234,
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
        performMigration()
      }
    }
  }

  private fun performMigration(): MigrationResult = webTestClient.post().uri("/migrate/staff")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("staff-migration-completed"),
      any(),
      isNull(),
    )
  }
}
