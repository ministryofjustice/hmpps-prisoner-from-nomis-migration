package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdvanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdvanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvancesMigrationIntTest(
  @Autowired private val advancesNomisApiMock: AdvancesNomisApiMockServer,
  @Autowired private val mappingApiMock: AdvancesMappingApiMockServer,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : FinanceIntegrationTestBase() {
  private val nomisApiMock = NomisApiExtension.nomisApi

  override fun resetTelemetryClient() {}

  internal fun setupMigrationTest() = runBlocking {
    migrationHistoryRepository.deleteAll()

    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    FinanceApiExtension.resetAndDisableResetBeforeEach()

    tearDownTelemetryClient()
  }

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  @Nested
  @DisplayName("POST /migrate/advances")
  inner class StartMigration {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/advances")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/advances")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/advances")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult
      private val dpsAdvanceId = UUID.randomUUID()

      private val prisonerAdvances = listOf(prisonerAdvance(), prisonerAdvance().copy(id = 54321))

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, "A0000BC")
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1, firstOffenderNo = "A0000BC")
        advancesNomisApiMock.stubGetPrisonerAdvances(rootOffenderId = 0, prisonerAdvances = prisonerAdvances)
        financeApi.stubMigrateAdvance(advanceUuid = dpsAdvanceId)
        mappingApiMock.stubGetAdvanceByNomisIdOrNull(nomisAdvanceId = 12345, mapping = null)
        mappingApiMock.stubGetAdvanceByNomisIdOrNull(nomisAdvanceId = 54321, mapping = null)
        mappingApiMock.stubCreateMapping()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 2)

        migrationResult = performMigration()
      }

      @Test
      fun `will call nomis prisoner to retrieve the prisoner id ranges`() {
        advancesNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/id-ranges")))
      }

      @Test
      fun `will call nomis prisoner to retrieve the first range`() {
        advancesNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/ids-in-range")))
      }

      @Test
      fun `will call nomis prisoner to get prisoner advance details`() {
        advancesNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/finance/prisoners/root-offender-id/0/advances")))
      }

      @Test
      fun `will transform and migrate the advances to DPS`() {
        val migrationRequests: List<SyncCreateAdvanceRecordRequest> =
          getRequestBodies(postRequestedFor(urlPathEqualTo("/migrate/advances")))

        assertThat(migrationRequests).hasSize(2)
        assertThat(migrationRequests.map { it.legacyPaymentProfileId })
          .containsExactlyInAnyOrder(12345L, 54321L)
        assertThat(migrationRequests[0].legacyInformationNumber).isEqualTo("info-123")
        assertThat(migrationRequests[0].comment).isEqualTo("This is a comment")
        assertThat(migrationRequests[0].prisonNumber).isEqualTo("A0001BC")
        assertThat(migrationRequests[0].prisonID).isEqualTo("LEI")
        assertThat(migrationRequests[0].amount).isEqualTo(BigDecimal("2.10"))
        assertThat(migrationRequests[0].repaymentAmount).isEqualTo(BigDecimal("0.50"))
        assertThat(migrationRequests[0].repaymentStartDate).isEqualTo(LocalDateTime.parse("2024-06-18T00:00:00"))
        assertThat(migrationRequests[0].reference).isEqualTo("description of the advance")
        // TODO Migration shouldn't have status - remove when api set up
        assertThat(migrationRequests[0].status).isEqualTo(SyncCreateAdvanceRecordRequest.Status.ACTIVE)
        assertThat(migrationRequests[0].createdBy).isEqualTo("JD12345")
        assertThat(migrationRequests[0].createdOn).isEqualTo(LocalDateTime.parse("2024-06-18T12:30:45"))
      }

      @Test
      fun `will create mapping for each advance`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/advances"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", dpsAdvanceId)
            .withRequestBodyJsonPath("nomisAdvanceId", 12345),
        )
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/advances"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", dpsAdvanceId)
            .withRequestBodyJsonPath("nomisAdvanceId", 54321),
        )
      }

      @Test
      fun `will track telemetry for each advance migrated`() {
        verify(telemetryClient).trackEvent(
          eq("advances-migration-entity-migrated"),
          check {
            assertThat(it["nomisAdvanceId"]).isEqualTo("12345")
            assertThat(it["dpsId"]).isEqualTo(dpsAdvanceId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("advances-migration-entity-migrated"),
          check {
            assertThat(it["nomisAdvanceId"]).isEqualTo("54321")
            assertThat(it["dpsId"]).isEqualTo(dpsAdvanceId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of advances migrated`() {
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
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        setupMigrationTest()
        nomisApiMock.stubGetPrisonerIds(1, 1, "A0000BC")
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1, firstOffenderNo = "A0000BC")
        advancesNomisApiMock.stubGetPrisonerAdvances(rootOffenderId = 0)
        mappingApiMock.stubGetAdvanceByNomisIdOrNull(nomisAdvanceId = 12345)
        mappingApiMock.stubGetAdvanceByNomisIdOrNull(nomisAdvanceId = 54321)

        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve  advance details`() {
        advancesNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/finance/prisoners/root-offender-id/0/advances")))
      }

      @Test
      fun `will not migrate advances to DPS`() {
        financeApi.verify(0, postRequestedFor(anyUrl()))
      }

      @Test
      fun `will not attempt to resave mapping`() {
        mappingApiMock.verify(0, postRequestedFor(anyUrl()))
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
    inner class DuplicateMappingErrorHandling {
      private lateinit var migrationResult: MigrationResult
      private val dpsAdvanceId = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, "A0000BC")
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1, firstOffenderNo = "A0000BC")

        advancesNomisApiMock.stubGetPrisonerAdvances(rootOffenderId = 0)
        financeApi.stubMigrateAdvance(advanceUuid = dpsAdvanceId)
        mappingApiMock.stubGetAdvanceByNomisIdOrNull(nomisAdvanceId = 12345, mapping = null)
        mappingApiMock.stubCreateMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = AdvanceMappingDto(
                dpsId = dpsAdvanceId.toString(),
                nomisAdvanceId = 12345,
                mappingType = MIGRATED,
              ),
              existing = AdvanceMappingDto(
                dpsId = dpsAdvanceId.toString(),
                nomisAdvanceId = 23456,
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
      fun `will get details for offender only once`() {
        advancesNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/finance/prisoners/root-offender-id/0/advances")))
      }

      @Test
      fun `will attempt create mapping once before failing`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/advances"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", dpsAdvanceId)
            .withRequestBodyJsonPath("nomisAdvanceId", "12345"),
        )
      }

      @Test
      fun `will track telemetry for each offender migrated`() {
        verify(telemetryClient).trackEvent(
          eq("advances-migration-duplicate"),
          check {
            assertThat(it["duplicateNomisAdvanceId"]).isEqualTo("12345")
            assertThat(it["duplicateDpsAdvanceId"]).isEqualTo(dpsAdvanceId.toString())
            assertThat(it["existingNomisAdvanceId"]).isEqualTo("23456")
            assertThat(it["existingDpsAdvanceId"]).isEqualTo(dpsAdvanceId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of offenders migrated`() {
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
  }

  private fun performMigration(
    waitUntilVerify: () -> Unit = { },
  ): MigrationResult = webTestClient.post().uri("/migrate/advances")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted(waitUntilVerify)
    }

  private fun waitUntilCompleted(waitUntilVerify: () -> Unit) = await atMost Duration.ofSeconds(60) untilAsserted {
    waitUntilVerify()
    verify(telemetryClient).trackEvent(eq("advances-migration-completed"), any(), isNull())
  }
}
