package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.DrugTestingDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.DrugTestingDpsApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.DrugTestingNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.model.MigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(DrugTestingDpsApiExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrugTestingMigrationIntTest(
  @Autowired private val drugTestingNomisApiMock: DrugTestingNomisApiMockServer,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {
  private val dpsApiMock = DrugTestingDpsApiExtension.dpsDrugTestingServer

  override fun resetTelemetryClient() {}

  internal fun setupMigrationTest() = runBlocking {
    migrationHistoryRepository.deleteAll()

    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    DrugTestingDpsApiExtension.resetAndDisableResetBeforeEach()
    tearDownTelemetryClient()
  }

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  @Nested
  @DisplayName("POST /migrate/drug-testing")
  inner class StartMigration {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/drug-testing")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/drug-testing")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/drug-testing")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class FilterOptions {
      private lateinit var migrationResult: MigrationResult
      private val includedPrisonId = "MDI"
      private val excludedPrisonId = "LEI"
      private val rtpDate = LocalDate.parse("2025-07-01")
      private val testData = randomTestingProgramResponse(0, includedPrisonId, rtpDate)

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        drugTestingNomisApiMock.stubGetDrugTestingIdRangesAndInRange(pageSize = 1, totalElements = 1)
        drugTestingNomisApiMock.stubGetRandomTestingProgram(response = testData)
        dpsApiMock.stubMigrate(prisonId = includedPrisonId, rtpDate = rtpDate)
        migrationResult = performMigration(
          DrugTestingMigrationFilter(
            includedPrisonIds = setOf(includedPrisonId),
            excludedPrisonIds = setOf(excludedPrisonId),
          ),
        )
      }

      @Test
      fun `will pass filters to the id range and page requests`() {
        drugTestingNomisApiMock.verify(
          getRequestedFor(urlPathEqualTo("/drug-testing/id-ranges"))
            .withQueryParam("pageSize", equalTo("1000"))
            .withQueryParam("includedPrisonIds", equalTo(includedPrisonId))
            .withQueryParam("excludedPrisonIds", equalTo(excludedPrisonId)),
        )
        drugTestingNomisApiMock.verify(
          getRequestedFor(urlPathEqualTo("/drug-testing/ids-in-range"))
            .withQueryParam("fromId", equalTo("0"))
            .withQueryParam("toId", equalTo("1"))
            .withQueryParam("includedPrisonIds", equalTo(includedPrisonId))
            .withQueryParam("excludedPrisonIds", equalTo(excludedPrisonId)),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult
      private val prisonId = "MDI"
      private val rtpDate = LocalDate.parse("2025-07-01")
      private val testData = randomTestingProgramResponse(0, prisonId, rtpDate)

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        drugTestingNomisApiMock.stubGetDrugTestingIdRangesAndInRange(pageSize = 1, totalElements = 1)
        drugTestingNomisApiMock.stubGetRandomTestingProgram(response = testData)
        dpsApiMock.stubMigrate(prisonId = prisonId, rtpDate = rtpDate)
        migrationResult = performMigration()
      }

      @Test
      fun `will call nomis prisoner to retrieve the program`() {
        drugTestingNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/drug-testing/0")))
      }

      @Test
      fun `will transform and migrate the program into DPS`() {
        val migrationRequest: MigrationRequest =
          getRequestBody(putRequestedFor(urlPathEqualTo("/resync/testing-lists/$prisonId/$rtpDate")))

        assertThat(migrationRequest.mainPercentage).isEqualTo(10)
        assertThat(migrationRequest.reservePercentage).isEqualTo(5)
        assertThat(migrationRequest.eligibleCount).isEqualTo(20)
        assertThat(migrationRequest.reserveCount).isEqualTo(2)
        assertThat(migrationRequest.mainCount).isEqualTo(1)
        assertThat(migrationRequest.createdBy).isEqualTo("billy")
        assertThat(migrationRequest.dateGenerated).isEqualTo(LocalDateTime.parse("2026-02-01T10:20:30"))
        assertThat(migrationRequest.prisoners).hasSize(1)
        assertThat(migrationRequest.prisoners[0].prisonerNumber).isEqualTo("A1234BC")
        assertThat(migrationRequest.prisoners[0].listSelectionNumber).isEqualTo(1)
        assertThat(migrationRequest.prisoners[0].testedStatus).isEqualTo(true)
        assertThat(migrationRequest.prisoners[0].reasonNotTested).isNull()
        assertThat(migrationRequest.prisoners[0].listType).isEqualTo("M")
        assertThat(migrationRequest.prisoners[0].notes).isEqualTo("Selected for testing")
      }

      @Test
      fun `will track telemetry for each program migrated`() {
        verify(telemetryClient).trackEvent(
          eq("drugtesting-migration-entity-migrated"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["rtpDate"]).isEqualTo(rtpDate.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of programs migrated`() {
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("-1")
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class DuplicateOnDpsApi {
      private lateinit var migrationResult: MigrationResult
      private val prisonId = "MDI"
      private val rtpDate = LocalDate.parse("2025-07-01")
      private val testData = randomTestingProgramResponse(0, prisonId, rtpDate)

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        drugTestingNomisApiMock.stubGetDrugTestingIdRangesAndInRange(pageSize = 1, totalElements = 1)
        drugTestingNomisApiMock.stubGetRandomTestingProgram(response = testData)
        dpsApiMock.stubMigrate(prisonId = prisonId, rtpDate = rtpDate, status = 409)
        migrationResult = performMigration()
      }

      @Test
      fun `will track telemetry for a duplicate program response`() {
        verify(telemetryClient).trackEvent(
          eq("drugtesting-migration-entity-duplicate"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["rtpDate"]).isEqualTo(rtpDate.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not retry the duplicate program request`() {
        dpsApiMock.verify(1, putRequestedFor(urlPathEqualTo("/resync/testing-lists/$prisonId/$rtpDate")))
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathNoPrisoners {
      private lateinit var migrationResult: MigrationResult
      private val prisonId = "MDI"
      private val rtpDate = LocalDate.parse("2025-07-01")
      private val testData = randomTestingProgramResponse(rtpId = 0, prisonId = prisonId, month = rtpDate).copy(offenderTestSelection = listOf())

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        drugTestingNomisApiMock.stubGetDrugTestingIdRangesAndInRange(pageSize = 1, totalElements = 1)
        drugTestingNomisApiMock.stubGetRandomTestingProgram(response = testData)
        dpsApiMock.stubMigrate(prisonId = prisonId, rtpDate = rtpDate)
        migrationResult = performMigration()
      }

      @Test
      fun `will call nomis prisoner to retrieve the program`() {
        drugTestingNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/drug-testing/0")))
      }

      @Test
      fun `will not transform and migrate the program into DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathEqualTo("/resync/testing-lists/$prisonId/$rtpDate")))
      }

      @Test
      fun `will track telemetry for the ignored program`() {
        verify(telemetryClient).trackEvent(
          eq("drugtesting-migration-entity-ignored"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["rtpDate"]).isEqualTo(rtpDate.toString())
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
          .jsonPath("$.recordsMigrated").isEqualTo("-1")
      }
    }
  }

  private fun performMigration(
    migrationFilter: DrugTestingMigrationFilter = DrugTestingMigrationFilter(),
    waitUntilVerify: () -> Unit = { },
  ): MigrationResult = webTestClient.post().uri("/migrate/drug-testing")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(migrationFilter)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted(waitUntilVerify)
    }

  private fun waitUntilCompleted(waitUntilVerify: () -> Unit) = await atMost Duration.ofSeconds(60) untilAsserted {
    waitUntilVerify()
    verify(telemetryClient).trackEvent(eq("drugtesting-migration-completed"), any(), isNull())
  }
}
