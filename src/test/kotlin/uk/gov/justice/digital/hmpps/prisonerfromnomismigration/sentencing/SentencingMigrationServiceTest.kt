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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.CANCEL_MIGRATE_SENTENCING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCE_ADJUSTMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCE_ADJUSTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCE_ADJUSTMENTS_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCING_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.RETRY_SENTENCE_ADJUSTMENT_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.SENTENCING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisSentenceAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SentenceAdjustmentId
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
internal class SentencingMigrationServiceTest {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val auditService: AuditService = mock()
  private val sentencingService: SentencingService = mock()
  private val sentencingMappingService: SentencingMappingService = mock()
  val service = SentencingMigrationService(
    nomisApiService = nomisApiService,
    queueService = queueService,
    migrationHistoryService = migrationHistoryService,
    telemetryClient = telemetryClient,
    auditService = auditService,
    sentencingService = sentencingService,
    sentencingMappingService = sentencingMappingService,
    pageSize = 200
  )

  @Nested
  @DisplayName("migrateSentenceAdjustments")
  inner class MigrateSentenceAdjustments {
    private val nomisApiService = mockk<NomisApiService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val migrationHistoryService = mockk<MigrationHistoryService>(relaxed = true)

    private val auditWhatParam = slot<String>()
    private val auditDetailsParam = slot<Map<*, *>>()

    val service = SentencingMigrationService(
      nomisApiService = nomisApiService,
      queueService = queueService,
      migrationHistoryService = migrationHistoryService,
      telemetryClient = telemetryClient,
      auditService = auditService,
      sentencingService = sentencingService,
      sentencingMappingService = sentencingMappingService,
      pageSize = 200
    )

    @BeforeEach
    internal fun setUp() {
      coEvery { nomisApiService.getSentenceAdjustments(any(), any(), any(), any()) } returns
        pages(1)

      coEvery {
        auditService.sendAuditEvent(
          what = capture(auditWhatParam),
          details = capture(auditDetailsParam)
        )
      } just runs
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`() {
      runBlocking {
        service.migrateSentenceAdjustments(
          SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          )
        )
      }

      coVerify {
        nomisApiService.getSentenceAdjustments(
          fromDate = LocalDate.parse("2020-01-01"),
          toDate = LocalDate.parse("2020-01-02"),
          pageNumber = 0,
          pageSize = 1
        )
      }
    }

    @Test
    internal fun `will pass sentencing count and filter to queue`() {
      coEvery { nomisApiService.getSentenceAdjustments(any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.migrateSentenceAdjustments(
          SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          )
        )
      }

      verify(queueService).sendMessage(
        message = eq(MIGRATE_SENTENCE_ADJUSTMENTS),
        context = check<MigrationContext<SentencingMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(23)
          assertThat(it.body.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(it.body.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
        },
        delaySeconds = eq(0)
      )
    }

    @Test
    internal fun `will write migration history record`() {
      val sentencingMigrationFilter = SentencingMigrationFilter(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
      )

      coEvery { nomisApiService.getSentenceAdjustments(any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.migrateSentenceAdjustments(
          sentencingMigrationFilter
        )
      }

      coVerify {
        migrationHistoryService.recordMigrationStarted(
          migrationId = any(),
          migrationType = SENTENCING,
          estimatedRecordCount = 23,
          filter = coWithArg<SentencingMigrationFilter> {
            assertThat(it.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
            assertThat(it.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
          }
        )
      }

      with(auditDetailsParam.captured) {
        assertThat(this).extracting("migrationId").isNotNull
        assertThat(this).extracting("migrationType").isEqualTo("SENTENCING")
        assertThat(this).extracting("filter").isEqualTo(sentencingMigrationFilter)
      }
    }

    @Test
    internal fun `will write analytic with estimated count and filter`() {
      coEvery { nomisApiService.getSentenceAdjustments(any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.migrateSentenceAdjustments(
          SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          )
        )
      }

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-sentence-adjustments-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["fromDate"]).isEqualTo("2020-01-01")
          assertThat(it["toDate"]).isEqualTo("2020-01-02")
        },
        eq(null)
      )
    }

    @Test
    internal fun `will write analytics with empty filter`() {
      coEvery {
        nomisApiService.getSentenceAdjustments(
          fromDate = isNull(),
          toDate = isNull(),
          pageNumber = any(),
          pageSize = any()
        )
      } returns
        pages(23)

      runBlocking {
        service.migrateSentenceAdjustments(
          SentencingMigrationFilter()
        )
      }

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-sentence-adjustments-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["fromDate"]).isEqualTo("")
          assertThat(it["toDate"]).isEqualTo("")
        },
        eq(null)
      )
    }
  }

  @Nested
  @DisplayName("divideSentenceAdjustmentsByPage")
  inner class DivideSentenceAdjustmentsByPage {

    @BeforeEach
    internal fun setUp() {
      whenever(nomisApiService.getSentenceAdjustmentsBlocking(any(), any(), any(), any())).thenReturn(
        pages(100_200)
      )
    }

    @Test
    internal fun `will send a page message for every page (200) of sentencing `() {
      service.divideSentenceAdjustmentsByPage(
        MigrationContext(
          type = SENTENCING,
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          )
        )
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        eq(MIGRATE_SENTENCE_ADJUSTMENTS_BY_PAGE), any(), delaySeconds = eq(0)
      )
    }

    @Test
    internal fun `will also send a single MIGRATION_STATUS_CHECK message`() {
      service.divideSentenceAdjustmentsByPage(
        MigrationContext(
          type = SENTENCING,
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          )
        )
      )

      verify(queueService).sendMessage(
        eq(MIGRATE_SENTENCING_STATUS_CHECK), any(), any()
      )
    }

    @Test
    internal fun `each page with have the filter and context attached`() {
      service.divideSentenceAdjustmentsByPage(
        MigrationContext(
          type = SENTENCING,
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          )
        )
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        message = eq(MIGRATE_SENTENCE_ADJUSTMENTS_BY_PAGE),
        context = check<MigrationContext<SentencingPage>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.filter.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(it.body.filter.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
        },
        delaySeconds = eq(0)
      )
    }

    @Test
    internal fun `each page will contain page number and page size`() {
      val context: KArgumentCaptor<MigrationContext<SentencingPage>> = argumentCaptor()

      service.divideSentenceAdjustmentsByPage(
        MigrationContext(
          type = SENTENCING,
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = SentencingMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          )
        )
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        eq(MIGRATE_SENTENCE_ADJUSTMENTS_BY_PAGE), context.capture(), delaySeconds = eq(0)
      )
      val allContexts: List<MigrationContext<SentencingPage>> = context.allValues

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
  @DisplayName("migrateSentencingStatusCheck")
  inner class MigrateSentencingStatusCheck {
    @Nested
    @DisplayName("when there are still messages on the queue")
    inner class MessagesOnQueue {
      @BeforeEach
      internal fun setUp() {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(true)
      }

      @Test
      internal fun `will check again in 10 seconds`() {
        service.migrateSentencingStatusCheck(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = SentencingMigrationStatusCheck()
          )
        )

        verify(queueService).sendMessage(
          eq(MIGRATE_SENTENCING_STATUS_CHECK), any(), eq(10)
        )
      }

      @Test
      internal fun `will check again in 10 second and reset even when previously started finishing up phase`() {
        service.migrateSentencingStatusCheck(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = SentencingMigrationStatusCheck(checkCount = 4)
          )
        )

        verify(queueService).sendMessage(
          message = eq(MIGRATE_SENTENCING_STATUS_CHECK),
          context = check<MigrationContext<SentencingMigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(0)
          },
          delaySeconds = eq(10)
        )
      }
    }

    @Nested
    @DisplayName("when there are no messages on the queue")
    inner class NoMessagesOnQueue {
      @BeforeEach
      internal fun setUp() {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(0)
        whenever(sentencingMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`() {
        service.migrateSentencingStatusCheck(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = SentencingMigrationStatusCheck(checkCount = 9)
          )
        )

        verify(queueService).sendMessage(
          message = eq(MIGRATE_SENTENCING_STATUS_CHECK),
          context = check<MigrationContext<SentencingMigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(10)
          },
          delaySeconds = eq(1)
        )
      }

      @Test
      internal fun `will finish off when checked 10 times previously`() {
        service.migrateSentencingStatusCheck(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = SentencingMigrationStatusCheck(checkCount = 10)
          )
        )

        verify(queueService, never()).sendMessage(
          message = eq(MIGRATE_SENTENCING_STATUS_CHECK), context = any(), delaySeconds = any()
        )
      }

      @Test
      internal fun `will add completed telemetry when finishing off`() {
        service.migrateSentencingStatusCheck(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = SentencingMigrationStatusCheck(checkCount = 10)
          )
        )

        verify(telemetryClient).trackEvent(
          eq("nomis-migration-sentencing-completed"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("23")
            assertThat(it["durationMinutes"]).isNotNull()
          },
          eq(null)
        )
      }

      @Test
      internal fun `will update migration history record when finishing off`() {
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(2)
        whenever(sentencingMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.migrateSentencingStatusCheck(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = SentencingMigrationStatusCheck(checkCount = 10)
          )
        )

        verify(migrationHistoryService).recordMigrationCompleted(
          migrationId = eq("2020-05-23T11:30:00"),
          recordsFailed = eq(2),
          recordsMigrated = eq(21)
        )
      }
    }
  }

  @Nested
  @DisplayName("cancelMigrateSentencingStatusCheck")
  inner class CancelMigrateSentencingStatusCheck {
    @Nested
    @DisplayName("when there are still messages on the queue")
    inner class MessagesOnQueue {
      @BeforeEach
      internal fun setUp() {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(true)
      }

      @Test
      internal fun `will check again in 10 seconds`() {
        service.cancelMigrateSentencingStatusCheck(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = SentencingMigrationStatusCheck()
          )
        )

        verify(queueService).purgeAllMessages(any())
        verify(queueService).sendMessage(
          eq(CANCEL_MIGRATE_SENTENCING), any(), eq(10)
        )
      }

      @Test
      internal fun `will check again in 10 second and reset even when previously started finishing up phase`() {
        service.cancelMigrateSentencingStatusCheck(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = SentencingMigrationStatusCheck(checkCount = 4)
          )
        )

        verify(queueService).purgeAllMessages(any())
        verify(queueService).sendMessage(
          message = eq(CANCEL_MIGRATE_SENTENCING),
          context = check<MigrationContext<SentencingMigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(0)
          },
          delaySeconds = eq(10)
        )
      }
    }

    @Nested
    @DisplayName("when there are no messages on the queue")
    inner class NoMessagesOnQueue {
      @BeforeEach
      internal fun setUp() {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(0)
        whenever(sentencingMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`() {
        service.cancelMigrateSentencingStatusCheck(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = SentencingMigrationStatusCheck(checkCount = 9)
          )
        )

        verify(queueService).purgeAllMessages(check { assertThat(it).isEqualTo(SENTENCING) })

        verify(queueService).sendMessage(
          message = eq(CANCEL_MIGRATE_SENTENCING),
          context = check<MigrationContext<SentencingMigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(10)
          },
          delaySeconds = eq(1)
        )
      }

      @Test
      internal fun `will finish off when checked 10 times previously`() {
        service.cancelMigrateSentencingStatusCheck(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = SentencingMigrationStatusCheck(checkCount = 10)
          )
        )

        verify(queueService, never()).purgeAllMessages(check { assertThat(it).isEqualTo(SENTENCING) })
        verify(queueService, never()).sendMessage(
          message = eq(CANCEL_MIGRATE_SENTENCING), context = any(), delaySeconds = any()
        )
      }

      @Test
      internal fun `will add completed telemetry when finishing off`() {
        service.cancelMigrateSentencingStatusCheck(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = SentencingMigrationStatusCheck(checkCount = 10)
          )
        )

        verify(telemetryClient).trackEvent(
          eq("nomis-migration-sentencing-cancelled"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("23")
            assertThat(it["durationMinutes"]).isNotNull()
          },
          eq(null)
        )
      }

      @Test
      internal fun `will update migration history record when cancelling`() {
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(2)
        whenever(sentencingMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.cancelMigrateSentencingStatusCheck(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = SentencingMigrationStatusCheck(checkCount = 10)
          )
        )

        verify(migrationHistoryService).recordMigrationCancelled(
          migrationId = eq("2020-05-23T11:30:00"),
          recordsFailed = eq(2),
          recordsMigrated = eq(21)
        )
      }
    }
  }

  @Nested
  @DisplayName("migrateSentenceAdjustmentsForPage")
  inner class MigrateSentenceAdjustmentsForPage {
    @BeforeEach
    internal fun setUp() {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
      whenever(nomisApiService.getSentenceAdjustmentsBlocking(any(), any(), any(), any())).thenReturn(
        pages(15)
      )
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`() {
      service.migrateSentenceAdjustmentsForPage(
        MigrationContext(
          type = SENTENCING,
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = SentencingPage(
            filter = SentencingMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
            ),
            pageNumber = 13, pageSize = 15
          )
        )
      )

      verify(nomisApiService).getSentenceAdjustmentsBlocking(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
        pageNumber = 13,
        pageSize = 15
      )
    }

    @Test
    internal fun `will send MIGRATE_SENTENCE_ADJUSTMENT with context for each sentence adjustment`() {
      service.migrateSentenceAdjustmentsForPage(
        MigrationContext(
          type = SENTENCING,
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = SentencingPage(
            filter = SentencingMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
            ),
            pageNumber = 13, pageSize = 15
          )
        )
      )

      verify(queueService, times(15)).sendMessage(
        message = eq(MIGRATE_SENTENCE_ADJUSTMENT),
        context = check<MigrationContext<SentencingMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
        },
        delaySeconds = eq(0)
      )
    }

    @Test
    internal fun `will send MIGRATE_SENTENCE_ADJUSTMENT with bookingId for each sentence adjustment`() {

      val context: KArgumentCaptor<MigrationContext<SentenceAdjustmentId>> = argumentCaptor()

      whenever(nomisApiService.getSentenceAdjustmentsBlocking(any(), any(), any(), any())).thenReturn(
        pages(
          15, startId = 1000
        )
      )

      service.migrateSentenceAdjustmentsForPage(
        MigrationContext(
          type = SENTENCING,
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = SentencingPage(
            filter = SentencingMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
            ),
            pageNumber = 13, pageSize = 15
          )
        )
      )

      verify(queueService, times(15)).sendMessage(
        eq(MIGRATE_SENTENCE_ADJUSTMENT), context.capture(), delaySeconds = eq(0)

      )
      val allContexts: List<MigrationContext<SentenceAdjustmentId>> = context.allValues

      val (firstPage, secondPage, thirdPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.sentenceAdjustmentId).isEqualTo(1000)
      assertThat(secondPage.body.sentenceAdjustmentId).isEqualTo(1001)
      assertThat(thirdPage.body.sentenceAdjustmentId).isEqualTo(1002)
      assertThat(lastPage.body.sentenceAdjustmentId).isEqualTo(1014)
    }

    @Test
    internal fun `will not send MIGRATE_SENTENCE_ADJUSTMENT when cancelling`() {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(true)

      whenever(nomisApiService.getSentenceAdjustmentsBlocking(any(), any(), any(), any())).thenReturn(
        pages(
          15, startId = 1000
        )
      )

      service.migrateSentenceAdjustmentsForPage(
        MigrationContext(
          type = SENTENCING,
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = SentencingPage(
            filter = SentencingMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
            ),
            pageNumber = 13, pageSize = 15
          )
        )
      )

      verifyNoInteractions(queueService)
    }
  }

  @Nested
  @DisplayName("migrateSentenceAdjustments")
  inner class MigrateSentenceAdjustment {

    @BeforeEach
    internal fun setUp() {
      whenever(sentencingMappingService.findNomisSentenceAdjustmentMapping(any())).thenReturn(null)
      whenever(nomisApiService.getSentenceAdjustmentBlocking(any())).thenReturn(
        NomisSentenceAdjustment(
          date = LocalDateTime.parse("2020-01-01T00:00:00"),
        )
      )

      whenever(sentencingService.migrateSentenceAdjustment(any())).thenReturn(CreateSentenceAdjustmentResponse(999L))
    }

    @Test
    internal fun `will retrieve sentence adjustment from NOMIS`() {
      service.migrateSentenceAdjustment(
        MigrationContext(
          type = SENTENCING,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = SentenceAdjustmentId(123)
        )
      )

      verify(nomisApiService).getSentenceAdjustmentBlocking(123)
    }

    @Test
    internal fun `will transform and send that adjustment to the Sentencing service`() {
      whenever(nomisApiService.getSentenceAdjustmentBlocking(any())).thenReturn(
        NomisSentenceAdjustment(
          date = LocalDateTime.parse("2020-01-01T00:00:00"),
        )
      )

      service.migrateSentenceAdjustment(
        MigrationContext(
          type = SENTENCING,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = SentenceAdjustmentId(123)
        )
      )

      verify(sentencingService).migrateSentenceAdjustment(
        eq(
          CreateSentenceAdjustment(
            date = LocalDateTime.parse("2020-01-01T00:00:00"),
          )
        )
      )
    }

    @Test
    internal fun `will create a mapping between a new Sentence Adjustment and a NOMIS Sentence Adjustment`() {
      whenever(nomisApiService.getSentenceAdjustmentBlocking(any())).thenReturn(
        NomisSentenceAdjustment(
          date = LocalDateTime.parse("2020-01-01T00:00:00"),
        )
      )
      whenever(sentencingService.migrateSentenceAdjustment(any())).thenReturn(CreateSentenceAdjustmentResponse(999L))

      service.migrateSentenceAdjustment(
        MigrationContext(
          type = SENTENCING,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = SentenceAdjustmentId(123)
        )
      )

      verify(sentencingMappingService).createNomisSentenceAdjustmentMigrationMapping(
        nomisSentenceAdjustmentId = 123,
        sentenceAdjustmentId = 999,
        migrationId = "2020-05-23T11:30:00",
      )
    }

    @Test
    internal fun `will not throw exception (and place message back on queue) but create a new retry message`() {
      whenever(nomisApiService.getSentenceAdjustmentBlocking(any())).thenReturn(
        NomisSentenceAdjustment(
          date = LocalDateTime.parse("2020-01-01T13:10:00"),
        )
      )
      whenever(sentencingService.migrateSentenceAdjustment(any())).thenReturn(CreateSentenceAdjustmentResponse(999L))

      whenever(sentencingMappingService.createNomisSentenceAdjustmentMigrationMapping(any(), any(), any())).thenThrow(
        RuntimeException("something went wrong")
      )

      service.migrateSentenceAdjustment(
        MigrationContext(
          type = SENTENCING,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = SentenceAdjustmentId(123)
        )
      )

      verify(queueService).sendMessage(
        message = eq(RETRY_SENTENCE_ADJUSTMENT_MAPPING),
        context = check<MigrationContext<SentenceAdjustmentMapping>> {
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.nomisSentenceAdjustmentId).isEqualTo(123)
          assertThat(it.body.sentenceAdjustmentId).isEqualTo(999)
        },
        delaySeconds = eq(0)
      )
    }

    @Nested
    inner class WhenMigratedAlready {
      @BeforeEach
      internal fun setUp() {
        whenever(sentencingMappingService.findNomisSentenceAdjustmentMapping(any())).thenReturn(
          SentenceAdjustmentNomisMapping(
            nomisSentenceAdjustmentId = 123,
            sentenceAdjustmentId = 54321,
            mappingType = "MIGRATION",
          )
        )
      }

      @Test
      internal fun `will do nothing`() {

        service.migrateSentenceAdjustment(
          MigrationContext(
            type = SENTENCING,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = SentenceAdjustmentId(123)
          )
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
          migrationType = SENTENCING,
          estimatedRecordCount = 100,
        )
      )

      service.cancel("123-2020-01-01")

      verify(auditService).sendAuditEvent(
        eq("MIGRATION_CANCEL_REQUESTED"),
        check {
          it as Map<*, *>
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["migrationType"]).isEqualTo(SENTENCING.name)
        },
      )
    }
  }
}

fun pages(total: Long, startId: Long = 1): PageImpl<SentenceAdjustmentId> = PageImpl<SentenceAdjustmentId>(
  (startId..total - 1 + startId).map { SentenceAdjustmentId(it) }, Pageable.ofSize(10), total
)