package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.CalculateReleaseDatesDpsApiExtension.Companion.calculateReleaseDatesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.CalculateReleaseDatesNomisApiMockServer.Companion.sentenceCalculationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueue
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CalculateReleaseDatesMigrationIntTest(
  @Autowired private val nomisApiMock: CalculateReleaseDatesNomisApiMockServer,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : CalculateReleaseDatesIntegrationTestBase() {
  private val nomisApi = NomisApiExtension.nomisApi
  private val dpsApi = calculateReleaseDatesApi
  private val migrationQueue by lazy { hmppsQueueService.findByQueueId(MigrationType.CALCULATE_RELEASE_DATES.queueId) as HmppsQueue }

  override fun resetTelemetryClient() {}

  override fun getQueues() = listOf(migrationQueue)

  internal fun setupMigrationTest() = runBlocking {
    migrationHistoryRepository.deleteAll()
    NomisApiExtension.resetAndDisableResetBeforeEach()
    CalculateReleaseDatesDpsApiExtension.resetAndDisableResetBeforeEach()
    tearDownTelemetryClient()
  }

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class StartMigration {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/calculate-release-dates")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("{}")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/calculate-release-dates")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("{}")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/calculate-release-dates")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("{}")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        setupMigrationTest()
        nomisApi.stubGetPrisonerIds(totalElements = 1, pageSize = 1)
        nomisApi.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetSentenceCalculationsForPrisoner("A0000KT", listOf(fullyPopulatedNomisCalculation()))
        dpsApi.stubMigratePrisoner("A0000KT")

        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve all prisoner ids to migrate`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/id-ranges")))
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/ids-in-range")))
      }

      @Test
      fun `will retrieve the sentence calculations for the prisoner`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/A0000KT/sentence-calculations")))
      }

      @Test
      fun `will migrate the prisoner into DPS`() {
        dpsApi.verify(postRequestedFor(urlPathEqualTo("/legacy-sentence-calculation/migrate/A0000KT")))
      }

      @Test
      fun `will pass every field to DPS`() {
        val request: LegacySentenceCalculationMigrationRequest = CalculateReleaseDatesDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/legacy-sentence-calculation/migrate/A0000KT")))
        assertThat(request.sentenceCalculations).hasSize(1)
        assertThat(request.sentenceCalculations[0])
          .usingRecursiveComparison()
          .isEqualTo(expectedDpsCalculation())
      }

      @Test
      fun `will track telemetry for the entity`() {
        verify(telemetryClient).trackEvent(
          eq("calculatereleasedates-migration-entity-migrated"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A0000KT")
            assertThat(it["migrationId"]).isEqualTo(migrationResult.migrationId)
            assertThat(it["count"]).isEqualTo("1")
          },
          isNull(),
        )
      }

      @Test
      fun `will mark migration as complete`() {
        assertMigrationCompleted(migrationResult.migrationId)
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathMultiplePrisoners {
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        setupMigrationTest()
        nomisApi.stubGetPrisonerIds(totalElements = 3, pageSize = 1)
        nomisApi.stubGetAllPrisonersIdRangesAndInRange(pageSize = 2, totalElements = 3)
        listOf("A0000KT", "A0001KT", "A0002KT").forEach {
          nomisApiMock.stubGetSentenceCalculationsForPrisoner(it, listOf(sentenceCalculationResponse(offenderNo = it)))
          dpsApi.stubMigratePrisoner(it)
        }

        migrationResult = performMigration()
      }

      @Test
      fun `will migrate every prisoner into DPS`() {
        listOf("A0000KT", "A0001KT", "A0002KT").forEach {
          dpsApi.verify(postRequestedFor(urlPathEqualTo("/legacy-sentence-calculation/migrate/$it")))
        }
      }

      @Test
      fun `will mark migration as complete`() {
        assertMigrationCompleted(migrationResult.migrationId)
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SinglePrisoner {
      @BeforeAll
      fun setUp() {
        setupMigrationTest()
        nomisApiMock.stubGetSentenceCalculationsForPrisoner("A1234BC")
        dpsApi.stubMigratePrisoner("A1234BC")

        performMigration(prisonerNumber = "A1234BC")
      }

      @Test
      fun `will not look up prisoner ids in NOMIS`() {
        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/id-ranges")))
        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/ids-in-range")))
      }

      @Test
      fun `will migrate only the requested prisoner`() {
        dpsApi.verify(1, postRequestedFor(urlPathEqualTo("/legacy-sentence-calculation/migrate/A1234BC")))
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class PrisonerWithNoCalculations {
      @BeforeAll
      fun setUp() {
        setupMigrationTest()
        nomisApiMock.stubGetSentenceCalculationsForPrisoner("A1234BC", emptyList())

        performMigration(prisonerNumber = "A1234BC")
      }

      @Test
      fun `will not call DPS`() {
        dpsApi.verify(0, postRequestedFor(urlPathEqualTo("/legacy-sentence-calculation/migrate/A1234BC")))
      }

      @Test
      fun `will track ignored telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("calculatereleasedates-migration-entity-ignored"),
          check { assertThat(it["offenderNo"]).isEqualTo("A1234BC") },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class DpsFailure {
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        setupMigrationTest()
        nomisApiMock.stubGetSentenceCalculationsForPrisoner("A1234BC")
        dpsApi.stubFor(
          com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/legacy-sentence-calculation/migrate/A1234BC"))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(HttpStatus.BAD_REQUEST.value())),
        )

        migrationResult = performMigration(prisonerNumber = "A1234BC")
      }

      @Test
      fun `will publish fail telemetry`() {
        verify(telemetryClient, atLeastOnce()).trackEvent(
          eq("calculatereleasedates-migration-entity-failed"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["migrationId"]).isEqualTo(migrationResult.migrationId)
            assertThat(it["reason"]).contains("400 Bad Request")
          },
          isNull(),
        )
      }
    }
  }

  private fun assertMigrationCompleted(migrationId: String) {
    webTestClient.get().uri("/migrate/history/$migrationId")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
      .header("Content-Type", "application/json")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.migrationId").isEqualTo(migrationId)
      .jsonPath("$.status").isEqualTo("COMPLETED")
  }

  private fun performMigration(prisonerNumber: String? = null): MigrationResult = webTestClient.post().uri("/migrate/calculate-release-dates")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(CalculateReleaseDatesMigrationFilter(prisonerNumber = prisonerNumber))
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      await atMost Duration.ofSeconds(60) untilAsserted {
        verify(telemetryClient).trackEvent(eq("calculatereleasedates-migration-completed"), any(), isNull())
      }
    }

  // Every NOMIS field is given a distinct value so that any mix-up between fields is detected
  private fun fullyPopulatedNomisCalculation() = sentenceCalculationResponse(id = 111, bookingId = 222, offenderNo = "A0000KT").copy(
    calculationDate = LocalDateTime.parse("2024-03-04T05:06:07"),
    calculationReasonCode = "NEW",
    commentText = "A comment",
    createDatetime = LocalDateTime.parse("2024-03-04T05:06:08"),
    createUsername = "CREATE.USER",
    modifyDatetime = LocalDateTime.parse("2024-03-05T05:06:09"),
    modifyUserId = "MODIFY.USER",
    recordDatetime = LocalDateTime.parse("2024-03-06T05:06:10"),
    recordUserId = "RECORD.USER",
    // The following have no DPS equivalent and are not migrated
    staffId = 999,
    effectiveSentenceEndDate = LocalDate.parse("2040-01-01"),
    effectiveSentenceLength = "10/0/0",
    judiciallyImposedSentenceLength = "12/0/0",
    workflowHistoryId = 888,
    hdcedCalculatedDate = LocalDate.parse("2030-01-01"),
    hdcedOverridedDate = LocalDate.parse("2030-01-02"),
    hdcadCalculatedDate = LocalDate.parse("2030-01-03"),
    hdcadOverridedDate = LocalDate.parse("2030-01-04"),
    etdCalculatedDate = LocalDate.parse("2030-01-05"),
    etdOverridedDate = LocalDate.parse("2030-01-06"),
    mtdCalculatedDate = LocalDate.parse("2030-01-07"),
    mtdOverridedDate = LocalDate.parse("2030-01-08"),
    ltdCalculatedDate = LocalDate.parse("2030-01-09"),
    ltdOverridedDate = LocalDate.parse("2030-01-10"),
    ardCalculatedDate = LocalDate.parse("2030-01-11"),
    ardOverridedDate = LocalDate.parse("2030-01-12"),
    crdCalculatedDate = LocalDate.parse("2030-01-13"),
    crdOverridedDate = LocalDate.parse("2030-01-14"),
    pedCalculatedDate = LocalDate.parse("2030-01-15"),
    pedOverridedDate = LocalDate.parse("2030-01-16"),
    apdCalculatedDate = LocalDate.parse("2030-01-17"),
    apdOverridedDate = LocalDate.parse("2030-01-18"),
    npdCalculatedDate = LocalDate.parse("2030-01-19"),
    npdOverridedDate = LocalDate.parse("2030-01-20"),
    ledCalculatedDate = LocalDate.parse("2030-01-21"),
    ledOverridedDate = LocalDate.parse("2030-01-22"),
    sedCalculatedDate = LocalDate.parse("2030-01-23"),
    sedOverridedDate = LocalDate.parse("2030-01-24"),
    prrdCalculatedDate = LocalDate.parse("2030-01-25"),
    prrdOverridedDate = LocalDate.parse("2030-01-26"),
    tariffCalculatedDate = LocalDate.parse("2030-01-27"),
    tariffOverridedDate = LocalDate.parse("2030-01-28"),
    dprrdCalculatedDate = LocalDate.parse("2030-02-01"),
    dprrdOverridedDate = LocalDate.parse("2030-02-02"),
    tusedCalculatedDate = LocalDate.parse("2030-02-03"),
    tusedOverridedDate = LocalDate.parse("2030-02-04"),
    ersedOverridedDate = LocalDate.parse("2030-02-05"),
    tersedOverridedDate = LocalDate.parse("2030-02-06"),
    rotlOverridedDate = LocalDate.parse("2030-02-07"),
  )

  private fun expectedDpsCalculation() = LegacySentenceCalculationDto(
    nomisCalculationId = 111,
    prisonerId = "A0000KT",
    bookingId = 222,
    calculationDate = LocalDateTime.parse("2024-03-04T05:06:07"),
    calculationReasonCode = "NEW",
    commentText = "A comment",
    nomisCreatedAt = LocalDateTime.parse("2024-03-04T05:06:08"),
    nomisCreatedBy = "CREATE.USER",
    nomisModifiedAt = LocalDateTime.parse("2024-03-05T05:06:09"),
    nomisModifiedBy = "MODIFY.USER",
    nomisRecordedAt = LocalDateTime.parse("2024-03-06T05:06:10"),
    nomisRecordedBy = "RECORD.USER",
    hdcedCalculatedDate = LocalDate.parse("2030-01-01"),
    hdcedOverriddenDate = LocalDate.parse("2030-01-02"),
    hdcadCalculatedDate = LocalDate.parse("2030-01-03"),
    hdcadOverriddenDate = LocalDate.parse("2030-01-04"),
    etdCalculatedDate = LocalDate.parse("2030-01-05"),
    etdOverriddenDate = LocalDate.parse("2030-01-06"),
    mtdCalculatedDate = LocalDate.parse("2030-01-07"),
    mtdOverriddenDate = LocalDate.parse("2030-01-08"),
    ltdCalculatedDate = LocalDate.parse("2030-01-09"),
    ltdOverriddenDate = LocalDate.parse("2030-01-10"),
    ardCalculatedDate = LocalDate.parse("2030-01-11"),
    ardOverriddenDate = LocalDate.parse("2030-01-12"),
    crdCalculatedDate = LocalDate.parse("2030-01-13"),
    crdOverriddenDate = LocalDate.parse("2030-01-14"),
    pedCalculatedDate = LocalDate.parse("2030-01-15"),
    pedOverriddenDate = LocalDate.parse("2030-01-16"),
    apdCalculatedDate = LocalDate.parse("2030-01-17"),
    apdOverriddenDate = LocalDate.parse("2030-01-18"),
    npdCalculatedDate = LocalDate.parse("2030-01-19"),
    npdOverriddenDate = LocalDate.parse("2030-01-20"),
    ledCalculatedDate = LocalDate.parse("2030-01-21"),
    ledOverriddenDate = LocalDate.parse("2030-01-22"),
    sedCalculatedDate = LocalDate.parse("2030-01-23"),
    sedOverriddenDate = LocalDate.parse("2030-01-24"),
    prrdCalculatedDate = LocalDate.parse("2030-01-25"),
    prrdOverriddenDate = LocalDate.parse("2030-01-26"),
    tariffCalculatedDate = LocalDate.parse("2030-01-27"),
    tariffOverriddenDate = LocalDate.parse("2030-01-28"),
    dprrdCalculatedDate = LocalDate.parse("2030-02-01"),
    dprrdOverriddenDate = LocalDate.parse("2030-02-02"),
    tusedCalculatedDate = LocalDate.parse("2030-02-03"),
    tusedOverriddenDate = LocalDate.parse("2030-02-04"),
    ersedOverriddenDate = LocalDate.parse("2030-02-05"),
    tersedOverriddenDate = LocalDate.parse("2030-02-06"),
    rotlOverriddenDate = LocalDate.parse("2030-02-07"),
  )
}
