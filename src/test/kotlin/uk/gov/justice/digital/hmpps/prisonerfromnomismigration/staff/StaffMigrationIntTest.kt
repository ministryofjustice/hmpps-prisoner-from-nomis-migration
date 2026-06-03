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
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.StaffMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.StaffMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
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
          .bodyValue(StaffMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/staff")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(StaffMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/staff")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(StaffMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetStaffIds(
          content = listOf(StaffIdResponse(staffId = 1234)),
        )
        nomisApiMock.stubGetStaffIdsFromId(
          staffId = 0,
          content = listOf(StaffIdResponse(staffId = 1234)),
        )
        nomisApiMock.stubGetStaffIdsFromId(
          staffId = 1234,
          content = emptyList(),
        )

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
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult

      private val dpsStaffId = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetStaffIds(
          content = listOf(StaffIdResponse(staffId = 1234)),
        )
        nomisApiMock.stubGetStaffIdsFromId(
          staffId = 0,
          content = listOf(StaffIdResponse(staffId = 1234)),
        )
        nomisApiMock.stubGetStaffIdsFromId(
          staffId = 1234,
          content = emptyList(),
        )
        mappingApiMock.stubGetStaffByNomisIdOrNull(mapping = null)
        nomisApiMock.stubGetStaffDetails()

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
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/staff/1234")))
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
  }

  private fun performMigration(body: StaffMigrationFilter = StaffMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/staff")
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
      eq("staff-migration-completed"),
      any(),
      isNull(),
    )
  }
}
