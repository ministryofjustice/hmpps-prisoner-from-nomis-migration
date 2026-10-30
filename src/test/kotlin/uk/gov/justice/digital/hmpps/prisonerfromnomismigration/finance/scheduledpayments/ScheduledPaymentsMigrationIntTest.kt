package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncCreateSubscriptionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorContent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledPaymentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledPaymentMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduledPaymentsMigrationIntTest(
  @Autowired private val scheduledPaymentsNomisApiMock: ScheduledPaymentsNomisApiMockServer,
  @Autowired private val mappingApiMock: ScheduledPaymentsMappingApiMockServer,
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
  @DisplayName("POST /migrate/scheduled-payments")
  inner class StartMigration {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/scheduled-payments")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/scheduled-payments")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/scheduled-payments")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult
      private val dpsScheduledPaymentId = UUID.randomUUID()
      private lateinit var migrationStartedAt: LocalDateTime
      private lateinit var migrationCompletedAt: LocalDateTime

      private val prisonerScheduledPayments = listOf(scheduledPayment(), scheduledPayment().copy(id = 54321))

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        scheduledPaymentsNomisApiMock.stubGetActiveScheduledPaymentsCount(count = 20)
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1, firstOffenderNo = "A0000BC")
        scheduledPaymentsNomisApiMock.stubGetActivePrisonerScheduledPayments(rootOffenderId = 0, scheduledPayments = prisonerScheduledPayments)
        financeApi.stubCreateScheduledPayment(subscriptionId = dpsScheduledPaymentId)
        mappingApiMock.stubGetScheduledPaymentByNomisIdOrNull(nomisScheduledPaymentId = 12345, mapping = null)
        mappingApiMock.stubGetScheduledPaymentByNomisIdOrNull(nomisScheduledPaymentId = 54321, mapping = null)
        mappingApiMock.stubCreateMapping()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 2)

        migrationStartedAt = LocalDateTime.now()
        migrationResult = performMigration()
        migrationCompletedAt = LocalDateTime.now()
      }

      @Test
      fun `will call nomis prisoner to retrieve the prisoner id ranges`() {
        scheduledPaymentsNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/id-ranges")))
      }

      @Test
      fun `will call nomis prisoner to retrieve the first range`() {
        scheduledPaymentsNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/ids-in-range")))
      }

      @Test
      fun `will call nomis prisoner to get active prisoner scheduled payment details`() {
        scheduledPaymentsNomisApiMock.verify(
          getRequestedFor(urlPathEqualTo("/finance/prisoners/root-offender-id/0/scheduled-payments")),
        )
      }

      @Test
      fun `will transform and migrate the scheduled payments to DPS`() {
        val migrationRequests: List<SyncCreateSubscriptionRequest> =
          getRequestBodies(postRequestedFor(urlPathEqualTo("/sync/subscriptions")))

        assertThat(migrationRequests).hasSize(2)
        assertThat(migrationRequests.map { it.legacyPaymentProfileId })
          .containsExactlyInAnyOrder(12345L, 54321L)
        assertThat(migrationRequests[0].prisonNumber).isEqualTo("A0001BC")
        assertThat(migrationRequests[0].prisonId).isEqualTo("LEI")
        assertThat(migrationRequests[0].amount).isEqualTo(BigDecimal("2.10"))
        assertThat(migrationRequests[0].dayOfPayment).isEqualTo(SyncCreateSubscriptionRequest.DayOfPayment.WEDNESDAY)
        assertThat(migrationRequests[0].startDate).isEqualTo(LocalDate.parse("2024-06-18"))
        assertThat(migrationRequests[0].endDate).isEqualTo(LocalDate.parse("2024-07-30"))
        assertThat(migrationRequests[0].subAccountCode).isEqualTo(2103)
        assertThat(migrationRequests[0].subscriptionType).isEqualTo("TELE")
        assertThat(migrationRequests[0].reference).isEqualTo("description of the scheduled payment")
        assertThat(migrationRequests[0].createdBy).isEqualTo("JD12345")
        assertThat(migrationRequests[0].createdAt).isBetween(migrationStartedAt, migrationCompletedAt)
      }

      @Test
      fun `will create mapping for each scheduled payment`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/scheduled-payments"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", dpsScheduledPaymentId)
            .withRequestBodyJsonPath("nomisScheduledPaymentId", 12345),
        )
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/scheduled-payments"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", dpsScheduledPaymentId)
            .withRequestBodyJsonPath("nomisScheduledPaymentId", 54321),
        )
      }

      @Test
      fun `will track telemetry for each scheduled payment migrated`() {
        verify(telemetryClient).trackEvent(
          eq("scheduled-payments-migration-entity-migrated"),
          check {
            assertThat(it["nomisScheduledPaymentId"]).isEqualTo("12345")
            assertThat(it["dpsId"]).isEqualTo(dpsScheduledPaymentId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("scheduled-payments-migration-entity-migrated"),
          check {
            assertThat(it["nomisScheduledPaymentId"]).isEqualTo("54321")
            assertThat(it["dpsId"]).isEqualTo(dpsScheduledPaymentId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of scheduled payments migrated`() {
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
        scheduledPaymentsNomisApiMock.stubGetActiveScheduledPaymentsCount(count = 20)
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1, firstOffenderNo = "A0000BC")
        scheduledPaymentsNomisApiMock.stubGetActivePrisonerScheduledPayments(rootOffenderId = 0)
        mappingApiMock.stubGetScheduledPaymentByNomisIdOrNull(nomisScheduledPaymentId = 12345)
        mappingApiMock.stubGetScheduledPaymentByNomisIdOrNull(nomisScheduledPaymentId = 54321)

        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve active scheduled payment details`() {
        scheduledPaymentsNomisApiMock.verify(
          getRequestedFor(urlPathEqualTo("/finance/prisoners/root-offender-id/0/scheduled-payments")),
        )
      }

      @Test
      fun `will not migrate scheduled payments to DPS`() {
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
    inner class MappingFailureFollowedBySuccess {
      private lateinit var migrationResult: MigrationResult
      private val dpsScheduledPaymentId = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        scheduledPaymentsNomisApiMock.stubGetActiveScheduledPaymentsCount(count = 20)
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1, firstOffenderNo = "A0000BC")
        scheduledPaymentsNomisApiMock.stubGetActivePrisonerScheduledPayments(rootOffenderId = 0)
        financeApi.stubCreateScheduledPayment(subscriptionId = dpsScheduledPaymentId)
        mappingApiMock.stubGetScheduledPaymentByNomisIdOrNull(nomisScheduledPaymentId = 12345, mapping = null)
        mappingApiMock.stubCreateMappingFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)

        migrationResult = performMigration {
          mappingApiMock.verify(2, postRequestedFor(urlPathEqualTo("/mapping/scheduled-payments")))
        }
      }

      @Test
      fun `will retrieve scheduled payment details only once`() {
        scheduledPaymentsNomisApiMock.verify(
          1,
          getRequestedFor(urlPathEqualTo("/finance/prisoners/root-offender-id/0/scheduled-payments")),
        )
      }

      @Test
      fun `will migrate the scheduled payment to DPS only once`() {
        financeApi.verify(1, postRequestedFor(urlPathEqualTo("/sync/subscriptions")))
      }

      @Test
      fun `will retry creating the same mapping after failure`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/scheduled-payments"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", dpsScheduledPaymentId)
            .withRequestBodyJsonPath("nomisScheduledPaymentId", 12345),
        )
      }

      @Test
      fun `will record the scheduled payment as migrated after retry`() {
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
      private val dpsScheduledPaymentId = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        scheduledPaymentsNomisApiMock.stubGetActiveScheduledPaymentsCount(count = 20)
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1, firstOffenderNo = "A0000BC")

        scheduledPaymentsNomisApiMock.stubGetActivePrisonerScheduledPayments(rootOffenderId = 0)
        financeApi.stubCreateScheduledPayment(subscriptionId = dpsScheduledPaymentId)
        mappingApiMock.stubGetScheduledPaymentByNomisIdOrNull(nomisScheduledPaymentId = 12345, mapping = null)
        mappingApiMock.stubCreateMapping(
          error = DuplicateErrorResponse(
            moreInfo = DuplicateErrorContent(
              duplicate = ScheduledPaymentMappingDto(
                dpsId = dpsScheduledPaymentId.toString(),
                nomisScheduledPaymentId = 12345,
                mappingType = MIGRATED,
              ),
              existing = ScheduledPaymentMappingDto(
                dpsId = dpsScheduledPaymentId.toString(),
                nomisScheduledPaymentId = 23456,
                mappingType = MIGRATED,
              ),
            ),
          ),
        )
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)

        migrationResult = performMigration()
      }

      @Test
      fun `will get scheduled payment details for offender only once`() {
        scheduledPaymentsNomisApiMock.verify(
          1,
          getRequestedFor(urlPathEqualTo("/finance/prisoners/root-offender-id/0/scheduled-payments")),
        )
      }

      @Test
      fun `will attempt create mapping once before failing`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/scheduled-payments"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", dpsScheduledPaymentId)
            .withRequestBodyJsonPath("nomisScheduledPaymentId", "12345"),
        )
      }

      @Test
      fun `will track telemetry for the duplicate scheduled payment mapping`() {
        verify(telemetryClient).trackEvent(
          eq("scheduled-payments-migration-duplicate"),
          check {
            assertThat(it["duplicateNomisScheduledPaymentId"]).isEqualTo("12345")
            assertThat(it["duplicateDpsScheduledPaymentId"]).isEqualTo(dpsScheduledPaymentId.toString())
            assertThat(it["existingNomisScheduledPaymentId"]).isEqualTo("23456")
            assertThat(it["existingDpsScheduledPaymentId"]).isEqualTo(dpsScheduledPaymentId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of scheduled payments migrated`() {
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
  ): MigrationResult = webTestClient.post().uri("/migrate/scheduled-payments")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted(waitUntilVerify)
    }

  private fun waitUntilCompleted(waitUntilVerify: () -> Unit) = await atMost Duration.ofSeconds(60) untilAsserted {
    waitUntilVerify()
    verify(telemetryClient).trackEvent(eq("scheduled-payments-migration-completed"), any(), isNull())
  }
}
