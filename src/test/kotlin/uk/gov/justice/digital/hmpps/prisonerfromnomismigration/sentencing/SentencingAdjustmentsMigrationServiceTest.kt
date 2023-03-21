package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.CANCEL_MIGRATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITIES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.RETRY_MIGRATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatusCheck
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.SENTENCING_ADJUSTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisAdjustmentId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisCodeDescription
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
internal class SentencingAdjustmentsMigrationServiceTest {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val auditService: AuditService = mock()
  private val sentencingService: SentencingService = mock()
  private val sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService = mock()
  val service = SentencingAdjustmentsMigrationService(
    nomisApiService = nomisApiService,
    queueService = queueService,
    migrationHistoryService = migrationHistoryService,
    telemetryClient = telemetryClient,
    auditService = auditService,
    sentencingService = sentencingService,
    sentencingAdjustmentsMappingService = sentencingAdjustmentsMappingService,
    pageSize = 200,
  )

  @Nested
  @DisplayName("migrateSentenceAdjustments")
  inner class MigrateSentenceAdjustments {
    private val nomisApiService = mockk<NomisApiService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val migrationHistoryService = mockk<MigrationHistoryService>(relaxed = true)

    private val auditWhatParam = slot<String>()
    private val auditDetailsParam = slot<Map<*, *>>()

    val service = SentencingAdjustmentsMigrationService(
      nomisApiService = nomisApiService,
      queueService = queueService,
      migrationHistoryService = migrationHistoryService,
      telemetryClient = telemetryClient,
      auditService = auditService,
      sentencingService = sentencingService,
      sentencingAdjustmentsMappingService = sentencingAdjustmentsMappingService,
      pageSize = 200,
    )

    @BeforeEach
    internal fun setUp() {
      coEvery { nomisApiService.getSentencingAdjustmentIds(any(), any(), any(), any()) } returns
        pages(1)

      coEvery {
        auditService.sendAuditEvent(
          what = capture(auditWhatParam),
          details = capture(auditDetailsParam),
        )
      } just runs
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`() {
      runBlocking {
        service.startMigration(
          SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      coVerify {
        nomisApiService.getSentencingAdjustmentIds(
          fromDate = LocalDate.parse("2020-01-01"),
          toDate = LocalDate.parse("2020-01-02"),
          pageNumber = 0,
          pageSize = 1,
        )
      }
    }

    @Test
    internal fun `will pass sentencing count and filter to queue`(): Unit = runBlocking {
      coEvery { nomisApiService.getSentencingAdjustmentIds(any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.startMigration(
          SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      verify(queueService).sendMessage(
        message = eq(MIGRATE_ENTITIES),
        context = check<MigrationContext<SentencingMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(23)
          assertThat(it.body.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(it.body.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will write migration history record`() {
      val sentencingMigrationFilter = SentencingMigrationFilter(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
      )

      coEvery { nomisApiService.getSentencingAdjustmentIds(any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.startMigration(
          sentencingMigrationFilter,
        )
      }

      coVerify {
        migrationHistoryService.recordMigrationStarted(
          migrationId = any(),
          migrationType = SENTENCING_ADJUSTMENTS,
          estimatedRecordCount = 23,
          filter = coWithArg<SentencingMigrationFilter> {
            assertThat(it.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
            assertThat(it.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
          },
        )
      }

      with(auditDetailsParam.captured) {
        assertThat(this).extracting("migrationId").isNotNull
        assertThat(this).extracting("filter").isEqualTo(sentencingMigrationFilter)
      }
    }

    @Test
    internal fun `will write analytic with estimated count and filter`() {
      coEvery { nomisApiService.getSentencingAdjustmentIds(any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.startMigration(
          SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("sentencing-adjustments-migration-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["fromDate"]).isEqualTo("2020-01-01")
          assertThat(it["toDate"]).isEqualTo("2020-01-02")
        },
        eq(null),
      )
    }

    @Test
    internal fun `will write analytics with empty filter`() {
      coEvery {
        nomisApiService.getSentencingAdjustmentIds(
          fromDate = isNull(),
          toDate = isNull(),
          pageNumber = any(),
          pageSize = any(),
        )
      } returns
        pages(23)

      runBlocking {
        service.startMigration(
          SentencingMigrationFilter(),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("sentencing-adjustments-migration-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["fromDate"]).isNull()
          assertThat(it["toDate"]).isNull()
        },
        eq(null),
      )
    }
  }

  @Nested
  @DisplayName("divideSentenceAdjustmentsByPage")
  inner class DivideSentenceAdjustmentsByPage {

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(nomisApiService.getSentencingAdjustmentIds(any(), any(), any(), any())).thenReturn(
        pages(100_200),
      )
    }

    @Test
    internal fun `will send a page message for every page (200) of sentencing `(): Unit = runBlocking {
      service.divideEntitiesByPage(
        MigrationContext(
          type = SENTENCING_ADJUSTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        ),
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        eq(MIGRATE_BY_PAGE),
        any(),
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will also send a single MIGRATION_STATUS_CHECK message`(): Unit = runBlocking {
      service.divideEntitiesByPage(
        MigrationContext(
          type = SENTENCING_ADJUSTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        ),
      )

      verify(queueService).sendMessage(
        eq(MIGRATE_STATUS_CHECK),
        any(),
        any(),
      )
    }

    @Test
    internal fun `each page with have the filter and context attached`(): Unit = runBlocking {
      service.divideEntitiesByPage(
        MigrationContext(
          type = SENTENCING_ADJUSTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        ),
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        message = eq(MIGRATE_BY_PAGE),
        context = check<MigrationContext<MigrationPage<SentencingMigrationFilter>>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.filter.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(it.body.filter.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `each page will contain page number and page size`(): Unit = runBlocking {
      val context: KArgumentCaptor<MigrationContext<MigrationPage<SentencingMigrationFilter>>> = argumentCaptor()

      service.divideEntitiesByPage(
        MigrationContext(
          type = SENTENCING_ADJUSTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        ),
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        eq(MIGRATE_BY_PAGE),
        context.capture(),
        delaySeconds = eq(0),
      )
      val allContexts: List<MigrationContext<MigrationPage<SentencingMigrationFilter>>> = context.allValues

      val (firstPage, secondPage, thirdPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.pageNumber).isEqualTo(0)
      assertThat(firstPage.body.pageSize).isEqualTo(200)

      assertThat(secondPage.body.pageNumber).isEqualTo(1)
      assertThat(secondPage.body.pageSize).isEqualTo(200)

      assertThat(thirdPage.body.pageNumber).isEqualTo(2)
      assertThat(thirdPage.body.pageSize).isEqualTo(200)

      assertThat(lastPage.body.pageNumber).isEqualTo((100_200 / 200) - 1)
      assertThat(lastPage.body.pageSize).isEqualTo(200)
    }
  }

  @Nested
  @DisplayName("migrateStatusCheck")
  inner class MigrateSentencingStatusCheck {
    @Nested
    @DisplayName("when there are still messages on the queue")
    inner class MessagesOnQueue {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(true)
      }

      @Test
      internal fun `will check again in 10 seconds`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(),
          ),
        )

        verify(queueService).sendMessage(
          eq(MIGRATE_STATUS_CHECK),
          any(),
          eq(10),
        )
      }

      @Test
      internal fun `will check again in 10 second and reset even when previously started finishing up phase`(): Unit =
        runBlocking {
          service.migrateStatusCheck(
            MigrationContext(
              type = SENTENCING_ADJUSTMENTS,
              migrationId = "2020-05-23T11:30:00",
              estimatedCount = 100_200,
              body = MigrationStatusCheck(checkCount = 4),
            ),
          )

          verify(queueService).sendMessage(
            message = eq(MIGRATE_STATUS_CHECK),
            context = check<MigrationContext<MigrationStatusCheck>> {
              assertThat(it.body.checkCount).isEqualTo(0)
            },
            delaySeconds = eq(10),
          )
        }
    }

    @Nested
    @DisplayName("when there are no messages on the queue")
    inner class NoMessagesOnQueue {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(0)
        whenever(sentencingAdjustmentsMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 9),
          ),
        )

        verify(queueService).sendMessage(
          message = eq(MIGRATE_STATUS_CHECK),
          context = check<MigrationContext<MigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(10)
          },
          delaySeconds = eq(1),
        )
      }

      @Test
      internal fun `will finish off when checked 10 times previously`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(queueService, never()).sendMessage(
          message = eq(MIGRATE_STATUS_CHECK),
          context = any(),
          delaySeconds = any(),
        )
      }

      @Test
      internal fun `will add completed telemetry when finishing off`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("sentencing-adjustments-migration-completed"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("23")
            assertThat(it["durationMinutes"]).isNotNull()
          },
          eq(null),
        )
      }

      @Test
      internal fun `will update migration history record when finishing off`(): Unit = runBlocking {
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(2)
        whenever(sentencingAdjustmentsMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.migrateStatusCheck(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(migrationHistoryService).recordMigrationCompleted(
          migrationId = eq("2020-05-23T11:30:00"),
          recordsFailed = eq(2),
          recordsMigrated = eq(21),
        )
      }
    }
  }

  @Nested
  @DisplayName("cancelMigrateStatusCheck")
  inner class CancelMigrateStatusCheck {
    @Nested
    @DisplayName("when there are still messages on the queue")
    inner class MessagesOnQueue {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(true)
      }

      @Test
      internal fun `will check again in 10 seconds`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(),
          ),
        )

        verify(queueService).purgeAllMessages(any())
        verify(queueService).sendMessage(
          eq(CANCEL_MIGRATION),
          any(),
          eq(10),
        )
      }

      @Test
      internal fun `will check again in 10 second and reset even when previously started finishing up phase`(): Unit =
        runBlocking {
          service.cancelMigrateStatusCheck(
            MigrationContext(
              type = SENTENCING_ADJUSTMENTS,
              migrationId = "2020-05-23T11:30:00",
              estimatedCount = 100_200,
              body = MigrationStatusCheck(checkCount = 4),
            ),
          )

          verify(queueService).purgeAllMessages(any())
          verify(queueService).sendMessage(
            message = eq(CANCEL_MIGRATION),
            context = check<MigrationContext<MigrationStatusCheck>> {
              assertThat(it.body.checkCount).isEqualTo(0)
            },
            delaySeconds = eq(10),
          )
        }
    }

    @Nested
    @DisplayName("when there are no messages on the queue")
    inner class NoMessagesOnQueue {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(0)
        whenever(sentencingAdjustmentsMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 9),
          ),
        )

        verify(queueService).purgeAllMessages(check { assertThat(it).isEqualTo(SENTENCING_ADJUSTMENTS) })

        verify(queueService).sendMessage(
          message = eq(CANCEL_MIGRATION),
          context = check<MigrationContext<MigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(10)
          },
          delaySeconds = eq(1),
        )
      }

      @Test
      internal fun `will finish off when checked 10 times previously`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(queueService, never()).purgeAllMessages(check { assertThat(it).isEqualTo(SENTENCING_ADJUSTMENTS) })
        verify(queueService, never()).sendMessage(
          message = eq(CANCEL_MIGRATION),
          context = any(),
          delaySeconds = any(),
        )
      }

      @Test
      internal fun `will add completed telemetry when finishing off`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("sentencing-adjustments-migration-cancelled"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("23")
            assertThat(it["durationMinutes"]).isNotNull()
          },
          eq(null),
        )
      }

      @Test
      internal fun `will update migration history record when cancelling`(): Unit = runBlocking {
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(2)
        whenever(sentencingAdjustmentsMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(migrationHistoryService).recordMigrationCancelled(
          migrationId = eq("2020-05-23T11:30:00"),
          recordsFailed = eq(2),
          recordsMigrated = eq(21),
        )
      }
    }
  }

  @Nested
  @DisplayName("migrateEntitiesForPage")
  inner class MigrateSentenceAdjustmentsForPage {
    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
      whenever(nomisApiService.getSentencingAdjustmentIds(any(), any(), any(), any())).thenReturn(
        pages(15),
      )
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = SENTENCING_ADJUSTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = SentencingMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
            ),
            pageNumber = 13,
            pageSize = 15,
          ),
        ),
      )

      verify(nomisApiService).getSentencingAdjustmentIds(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
        pageNumber = 13,
        pageSize = 15,
      )
    }

    @Test
    internal fun `will send MIGRATE_SENTENCE_ADJUSTMENT with context for each sentence adjustment`(): Unit =
      runBlocking {
        service.migrateEntitiesForPage(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationPage(
              filter = SentencingMigrationFilter(
                fromDate = LocalDate.parse("2020-01-01"),
                toDate = LocalDate.parse("2020-01-02"),
              ),
              pageNumber = 13,
              pageSize = 15,
            ),
          ),
        )

        verify(queueService, times(15)).sendMessage(
          message = eq(MIGRATE_ENTITY),
          context = check<MigrationContext<SentencingMigrationFilter>> {
            assertThat(it.estimatedCount).isEqualTo(100_200)
            assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          },
          delaySeconds = eq(0),
        )
      }

    @Test
    internal fun `will send MIGRATE_SENTENCE_ADJUSTMENT with bookingId for each sentence adjustment`(): Unit =
      runBlocking {
        val context: KArgumentCaptor<MigrationContext<NomisAdjustmentId>> = argumentCaptor()

        whenever(nomisApiService.getSentencingAdjustmentIds(any(), any(), any(), any())).thenReturn(
          pages(
            15,
            startId = 1000,
          ),
        )

        service.migrateEntitiesForPage(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationPage(
              filter = SentencingMigrationFilter(
                fromDate = LocalDate.parse("2020-01-01"),
                toDate = LocalDate.parse("2020-01-02"),
              ),
              pageNumber = 13,
              pageSize = 15,
            ),
          ),
        )

        verify(queueService, times(15)).sendMessage(
          eq(MIGRATE_ENTITY),
          context.capture(),
          delaySeconds = eq(0),

        )
        val allContexts: List<MigrationContext<NomisAdjustmentId>> = context.allValues

        val (firstPage, secondPage, thirdPage) = allContexts
        val lastPage = allContexts.last()

        assertThat(firstPage.body.adjustmentId).isEqualTo(1000)
        assertThat(secondPage.body.adjustmentId).isEqualTo(1001)
        assertThat(thirdPage.body.adjustmentId).isEqualTo(1002)
        assertThat(lastPage.body.adjustmentId).isEqualTo(1014)
      }

    @Test
    internal fun `will not send MIGRATE_SENTENCE_ADJUSTMENT when cancelling`(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(true)

      whenever(nomisApiService.getSentencingAdjustmentIds(any(), any(), any(), any())).thenReturn(
        pages(
          15,
          startId = 1000,
        ),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = SENTENCING_ADJUSTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = SentencingMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
            ),
            pageNumber = 13,
            pageSize = 15,
          ),
        ),
      )

      verifyNoInteractions(queueService)
    }
  }

  @Nested
  @DisplayName("migrateSentenceAdjustments")
  inner class MigrateSentenceAdjustment {

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(any(), any())).thenReturn(null)
      whenever(nomisApiService.getSentenceAdjustment(any())).thenReturn(
        aNomisSentenceAdjustment(),
      )

      whenever(sentencingService.migrateSentencingAdjustment(any())).thenReturn(CreateSentencingAdjustmentResponse("999"))
    }

    @Test
    internal fun `will retrieve sentencing adjustment from NOMIS`(): Unit = runBlocking {
      service.migrateNomisEntity(
        MigrationContext(
          type = SENTENCING_ADJUSTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = NomisAdjustmentId(123, "SENTENCE"),
        ),
      )

      verify(nomisApiService).getSentenceAdjustment(123)
    }

    @Test
    internal fun `will transform and send that adjustment to the Sentencing service`(): Unit = runBlocking {
      val adjustmentDate = LocalDate.parse("2020-01-01")
      val adjustmentFromDate = LocalDate.parse("2020-02-01")
      whenever(nomisApiService.getSentenceAdjustment(any())).thenReturn(
        aNomisSentenceAdjustment(),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = SENTENCING_ADJUSTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = NomisAdjustmentId(123, "SENTENCE"),
        ),
      )

      verify(sentencingService).migrateSentencingAdjustment(
        eq(
          SentencingAdjustment(
            adjustmentDate = adjustmentDate,
            adjustmentFromDate = adjustmentFromDate,
            adjustmentDays = 8,
            bookingId = 606,
            sentenceSequence = 2,
            comment = "a comment",
            active = true,
            adjustmentType = "ADA",
            offenderNo = "G4803UT",
          ),
        ),
      )
    }

    @Test
    internal fun `will create a mapping between a new Sentence Adjustment and a NOMIS Sentence Adjustment`(): Unit =
      runBlocking {
        whenever(nomisApiService.getSentenceAdjustment(any())).thenReturn(
          aNomisSentenceAdjustment(),
        )
        whenever(sentencingService.migrateSentencingAdjustment(any())).thenReturn(CreateSentencingAdjustmentResponse("999"))

        service.migrateNomisEntity(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = NomisAdjustmentId(123, "SENTENCE"),
          ),
        )

        verify(sentencingAdjustmentsMappingService).createMapping(
          SentencingAdjustmentNomisMapping(
            nomisAdjustmentId = 123,
            nomisAdjustmentCategory = "SENTENCE",
            adjustmentId = "999",
            label = "2020-05-23T11:30:00",
            mappingType = "MIGRATED",
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<SentencingAdjustmentNomisMapping>>() {},
        )
      }

    @Test
    internal fun `will not throw exception (and place message back on queue) but create a new retry message`(): Unit =
      runBlocking {
        whenever(nomisApiService.getSentenceAdjustment(any())).thenReturn(
          aNomisSentenceAdjustment(),
        )
        whenever(sentencingService.migrateSentencingAdjustment(any())).thenReturn(CreateSentencingAdjustmentResponse("999"))

        whenever(
          sentencingAdjustmentsMappingService.createMapping(
            any(),
            eq(object : ParameterizedTypeReference<DuplicateErrorResponse<SentencingAdjustmentNomisMapping>>() {}),
          ),
        ).thenThrow(
          RuntimeException("something went wrong"),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = NomisAdjustmentId(123, "SENTENCE"),
          ),
        )

        verify(queueService).sendMessage(
          message = eq(RETRY_MIGRATION_MAPPING),
          context = check<MigrationContext<SentencingAdjustmentNomisMapping>> {
            assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
            assertThat(it.body.nomisAdjustmentId).isEqualTo(123)
            assertThat(it.body.nomisAdjustmentCategory).isEqualTo("SENTENCE")
            assertThat(it.body.adjustmentId).isEqualTo("999")
          },
          delaySeconds = eq(0),
        )
      }

    @Nested
    inner class WhenMigratedAlready {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(any(), any())).thenReturn(
          SentencingAdjustmentNomisMapping(
            nomisAdjustmentId = 123,
            nomisAdjustmentCategory = "SENTENCE",
            adjustmentId = "54321",
            mappingType = "MIGRATION",
          ),
        )
      }

      @Test
      internal fun `will do nothing`(): Unit = runBlocking {
        service.migrateNomisEntity(
          MigrationContext(
            type = SENTENCING_ADJUSTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = NomisAdjustmentId(123, "SENTENCE"),
          ),
        )

        verifyNoInteractions(sentencingService)
      }
    }
  }

  @Test
  internal fun `will create audit event on user cancel`() {
    runBlocking {
      whenever(migrationHistoryService.get("123-2020-01-01")).thenReturn(
        MigrationHistory(
          migrationId = "123-2020-01-01 ",
          status = MigrationStatus.CANCELLED,
          whenEnded = LocalDateTime.parse("2020-01-01T00:00:00"),
          whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
          migrationType = SENTENCING_ADJUSTMENTS,
          estimatedRecordCount = 100,
        ),
      )

      service.cancel("123-2020-01-01")

      verify(auditService).sendAuditEvent(
        eq("MIGRATION_CANCEL_REQUESTED"),
        check {
          it as Map<*, *>
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["migrationType"]).isEqualTo(SENTENCING_ADJUSTMENTS.name)
        },
      )
    }
  }
}

fun aNomisSentenceAdjustment(
  id: Long = 1,
  bookingId: Long = 606,
  offenderNo: String = "G4803UT",
  sentenceSequence: Long = 2,
  adjustmentType: NomisCodeDescription = NomisCodeDescription(code = "ADA", "type description"),
  adjustmentDate: LocalDate = LocalDate.parse("2020-01-01"),
  adjustmentFromDate: LocalDate? = LocalDate.parse("2020-02-01"),
  adjustmentToDate: LocalDate? = null,
  adjustmentDays: Long = 8,
  comment: String? = "a comment",
  active: Boolean = true,
) = NomisAdjustment(
  id = id,
  bookingId = bookingId,
  offenderNo = offenderNo,
  sentenceSequence = sentenceSequence,
  adjustmentType = adjustmentType,
  adjustmentDate = adjustmentDate,
  adjustmentFromDate = adjustmentFromDate,
  adjustmentToDate = adjustmentToDate,
  adjustmentDays = adjustmentDays,
  comment = comment,
  active = active,
  hiddenFromUsers = false,
)

fun pages(total: Long, startId: Long = 1): PageImpl<NomisAdjustmentId> = PageImpl<NomisAdjustmentId>(
  (startId..total - 1 + startId).map { NomisAdjustmentId(it, "SENTENCE") },
  Pageable.ofSize(10),
  total,
)
