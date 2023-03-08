package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.ReviewType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.CANCEL_MIGRATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITIES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.RETRY_MIGRATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.IncentiveId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatusCheck
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.INCENTIVES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisCodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisIncentive
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
internal class IncentivesMigrationServiceTest {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val auditService: AuditService = mock()
  private val incentivesService: IncentivesService = mock()
  private val incentiveMappingService: IncentiveMappingService = mock()
  private val service = IncentivesMigrationService(
    nomisApiService = nomisApiService,
    queueService = queueService,
    migrationHistoryService = migrationHistoryService,
    telemetryClient = telemetryClient,
    auditService = auditService,
    incentivesService = incentivesService,
    incentiveMappingService = incentiveMappingService,
    pageSize = 200,
  )

  @Nested
  @DisplayName("migrateIncentives")
  inner class MigrateIncentives {
    private val nomisApiService = mockk<NomisApiService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val migrationHistoryService = mockk<MigrationHistoryService>(relaxed = true)

    private val auditWhatParam = slot<String>()
    private val auditDetailsParam = slot<Map<*, *>>()

    val service = IncentivesMigrationService(
      nomisApiService = nomisApiService,
      queueService = queueService,
      migrationHistoryService = migrationHistoryService,
      telemetryClient = telemetryClient,
      auditService = auditService,
      incentivesService = incentivesService,
      incentiveMappingService = incentiveMappingService,
      pageSize = 200,
    )

    @BeforeEach
    internal fun setUp() {
      coEvery { nomisApiService.getIncentives(any(), any(), any(), any()) } returns
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
          IncentivesMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      coVerify {
        nomisApiService.getIncentives(
          fromDate = LocalDate.parse("2020-01-01"),
          toDate = LocalDate.parse("2020-01-02"),
          pageNumber = 0,
          pageSize = 1,
        )
      }
    }

    @Test
    internal fun `will pass incentive count and filter to queue`() {
      coEvery { nomisApiService.getIncentives(any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.startMigration(
          IncentivesMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )

        verify(queueService).sendMessage(
          message = eq(MIGRATE_ENTITIES),
          context = check<MigrationContext<IncentivesMigrationFilter>> {
            assertThat(it.estimatedCount).isEqualTo(23)
            assertThat(it.body.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
            assertThat(it.body.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
          },
          delaySeconds = eq(0),
        )
      }
    }

    @Test
    internal fun `will write migration history record`() {
      val incentivesMigrationFilter = IncentivesMigrationFilter(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
      )

      coEvery { nomisApiService.getIncentives(any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.startMigration(
          incentivesMigrationFilter,
        )
      }

      coVerify {
        migrationHistoryService.recordMigrationStarted(
          migrationId = any(),
          migrationType = INCENTIVES,
          estimatedRecordCount = 23,
          filter = coWithArg<IncentivesMigrationFilter> {
            assertThat(it.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
            assertThat(it.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
          },
        )
      }

      with(auditDetailsParam.captured) {
        assertThat(this).extracting("migrationId").isNotNull
        assertThat(this).extracting("migrationType").isEqualTo("INCENTIVES")
        assertThat(this).extracting("filter").isEqualTo(incentivesMigrationFilter)
      }
    }

    @Test
    internal fun `will write analytic with estimated count and filter`() {
      coEvery { nomisApiService.getIncentives(any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.startMigration(
          IncentivesMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-started"),
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
        nomisApiService.getIncentives(
          fromDate = isNull(),
          toDate = isNull(),
          pageNumber = any(),
          pageSize = any(),
        )
      } returns
        pages(23)

      runBlocking {
        service.startMigration(
          IncentivesMigrationFilter(),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["fromDate"]).isEqualTo("")
          assertThat(it["toDate"]).isEqualTo("")
        },
        eq(null),
      )
    }
  }

  @Nested
  @DisplayName("divideEntitiesByPage")
  inner class DivideIncentivesByPage {

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(nomisApiService.getIncentives(any(), any(), any(), any())).thenReturn(
        pages(100_200),
      )
    }

    @Test
    internal fun `will send a page message for every page (200) of incentives `(): Unit = runBlocking {
      service.divideEntitiesByPage(
        MigrationContext(
          type = INCENTIVES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncentivesMigrationFilter(
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
          type = INCENTIVES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncentivesMigrationFilter(
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
          type = INCENTIVES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncentivesMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        ),
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        message = eq(MIGRATE_BY_PAGE),
        context = check<MigrationContext<MigrationPage<IncentivesMigrationFilter>>> {
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
      val context: KArgumentCaptor<MigrationContext<MigrationPage<IncentivesMigrationFilter>>> = argumentCaptor()

      service.divideEntitiesByPage(
        MigrationContext(
          type = INCENTIVES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncentivesMigrationFilter(
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
      val allContexts: List<MigrationContext<MigrationPage<IncentivesMigrationFilter>>> = context.allValues

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
  @DisplayName("migrateIncentivesStatusCheck")
  inner class MigrateIncentivesStatusCheck {
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
            type = INCENTIVES,
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
              type = INCENTIVES,
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
        whenever(incentiveMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = INCENTIVES,
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
            type = INCENTIVES,
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
            type = INCENTIVES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("nomis-migration-completed"),
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
        whenever(incentiveMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.migrateStatusCheck(
          MigrationContext(
            type = INCENTIVES,
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
  inner class CancelMigrateIncentivesStatusCheck {
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
            type = INCENTIVES,
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
              type = INCENTIVES,
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
        whenever(incentiveMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = INCENTIVES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 9),
          ),
        )

        verify(queueService).purgeAllMessages(check { assertThat(it).isEqualTo(INCENTIVES) })

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
            type = INCENTIVES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(queueService, never()).purgeAllMessages(check { assertThat(it).isEqualTo(INCENTIVES) })
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
            type = INCENTIVES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("nomis-migration-cancelled"),
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
        whenever(incentiveMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = INCENTIVES,
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
  inner class MigrateIncentivesForPage {
    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
      whenever(nomisApiService.getIncentives(any(), any(), any(), any())).thenReturn(
        pages(15),
      )
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = INCENTIVES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = IncentivesMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
            ),
            pageNumber = 13,
            pageSize = 15,
          ),
        ),
      )

      verify(nomisApiService).getIncentives(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
        pageNumber = 13,
        pageSize = 15,
      )
    }

    @Test
    internal fun `will send MIGRATE_ENTITY with context for each incentive`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = INCENTIVES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = IncentivesMigrationFilter(
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
        context = check<MigrationContext<IncentivesMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will send MIGRATE_ENTITY with bookingId for each incentive`(): Unit = runBlocking {
      val context: KArgumentCaptor<MigrationContext<IncentiveId>> = argumentCaptor()

      whenever(nomisApiService.getIncentives(any(), any(), any(), any())).thenReturn(
        pages(
          15,
          startId = 1000,
        ),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = INCENTIVES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = IncentivesMigrationFilter(
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
      val allContexts: List<MigrationContext<IncentiveId>> = context.allValues

      val (firstPage, secondPage, thirdPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.bookingId).isEqualTo(1000)
      assertThat(secondPage.body.bookingId).isEqualTo(1001)
      assertThat(thirdPage.body.bookingId).isEqualTo(1002)
      assertThat(lastPage.body.bookingId).isEqualTo(1014)
    }

    @Test
    internal fun `will not send MIGRATE_ENTITY when cancelling`(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(true)

      whenever(nomisApiService.getIncentives(any(), any(), any(), any())).thenReturn(
        pages(
          15,
          startId = 1000,
        ),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = INCENTIVES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = IncentivesMigrationFilter(
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
  @DisplayName("migrateIncentive")
  inner class MigrateIncentive {

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(incentiveMappingService.findNomisIncentiveMapping(any(), any())).thenReturn(null)
      whenever(nomisApiService.getIncentive(any(), any())).thenReturn(
        NomisIncentive(
          bookingId = 1000,
          incentiveSequence = 1,
          commentText = "Doing well",
          iepDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
          prisonId = "HEI",
          iepLevel = NomisCodeDescription("ENH", "Enhanced"),
          userId = "JANE_SMITH",
          currentIep = true,
          offenderNo = "A1234AA",
          whenCreated = LocalDateTime.parse("2020-01-01T00:00:55"),
        ),
      )

      whenever(incentivesService.migrateIncentive(any(), any())).thenReturn(CreateIncentiveIEPResponse(999L))
    }

    @Test
    internal fun `will retrieve incentive from NOMIS`(): Unit = runBlocking {
      service.migrateIncentive(
        MigrationContext(
          type = INCENTIVES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncentiveId(123, 2),
        ),
      )

      verify(nomisApiService).getIncentive(123, 2)
    }

    @Test
    internal fun `will transform and send that IEP to the incentives service`(): Unit = runBlocking {
      whenever(nomisApiService.getIncentive(any(), any())).thenReturn(
        NomisIncentive(
          bookingId = 1000,
          incentiveSequence = 1,
          commentText = "Doing well",
          iepDateTime = LocalDateTime.parse("2020-01-01T13:10:00"),
          prisonId = "HEI",
          iepLevel = NomisCodeDescription("ENH", "Enhanced"),
          userId = "JANE_SMITH",
          currentIep = true,
          offenderNo = "A1234AA",
          whenCreated = LocalDateTime.parse("2020-12-12T13:10:45"),
        ),
      )

      service.migrateIncentive(
        MigrationContext(
          type = INCENTIVES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncentiveId(123, 2),
        ),
      )

      verify(incentivesService).migrateIncentive(
        eq(
          CreateIncentiveIEP(
            comment = "Doing well",
            iepLevel = "ENH",
            iepTime = LocalDateTime.parse("2020-01-01T13:10:45"),
            prisonId = "HEI",
            userId = "JANE_SMITH",
            current = true,
            reviewType = MIGRATED,
          ),
        ),
        eq(123),
      )
    }

    @Test
    internal fun `will create a mapping between Incentives and NOMIS IEP`(): Unit = runBlocking {
      whenever(nomisApiService.getIncentive(any(), any())).thenReturn(
        NomisIncentive(
          bookingId = 123,
          incentiveSequence = 2,
          commentText = "Doing well",
          iepDateTime = LocalDateTime.parse("2020-01-01T13:10:00"),
          prisonId = "HEI",
          iepLevel = NomisCodeDescription("ENH", "Enhanced"),
          userId = "JANE_SMITH",
          currentIep = true,
          offenderNo = "A1234AA",
          whenCreated = LocalDateTime.parse("2020-01-01T00:00:15"),
        ),
      )
      whenever(incentivesService.migrateIncentive(any(), eq(123))).thenReturn(CreateIncentiveIEPResponse(999L))

      service.migrateIncentive(
        MigrationContext(
          type = INCENTIVES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncentiveId(123, 2),
        ),
      )

      verify(incentiveMappingService).createNomisIncentiveMigrationMapping(
        nomisBookingId = 123,
        nomisIncentiveSequence = 2,
        incentiveId = 999,
        migrationId = "2020-05-23T11:30:00",
      )
    }

    @Test
    internal fun `will not throw exception (and place message back on queue) but create a new retry message`(): Unit =
      runBlocking {
        whenever(nomisApiService.getIncentive(any(), any())).thenReturn(
          NomisIncentive(
            bookingId = 123,
            incentiveSequence = 2,
            commentText = "Doing well",
            iepDateTime = LocalDateTime.parse("2020-01-01T13:10:00"),
            prisonId = "HEI",
            iepLevel = NomisCodeDescription("ENH", "Enhanced"),
            userId = "JANE_SMITH",
            currentIep = true,
            offenderNo = "A1234AA",
            whenCreated = LocalDateTime.parse("2020-11-11T13:10:11"),
          ),
        )
        whenever(incentivesService.migrateIncentive(any(), any())).thenReturn(CreateIncentiveIEPResponse(999L))

        whenever(incentiveMappingService.createNomisIncentiveMigrationMapping(any(), any(), any(), any())).thenThrow(
          RuntimeException("something went wrong"),
        )

        service.migrateIncentive(
          MigrationContext(
            type = INCENTIVES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = IncentiveId(123, 2),
          ),
        )

        verify(queueService).sendMessage(
          message = eq(RETRY_MIGRATION_MAPPING),
          context = check<MigrationContext<IncentiveNomisMapping>> {
            assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
            assertThat(it.body.nomisBookingId).isEqualTo(123)
            assertThat(it.body.nomisIncentiveSequence).isEqualTo(2)
            assertThat(it.body.incentiveId).isEqualTo(999)
          },
          delaySeconds = eq(0),
        )
      }

    @Nested
    inner class WhenMigratedAlready {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(incentiveMappingService.findNomisIncentiveMapping(any(), any())).thenReturn(
          IncentiveNomisMapping(
            nomisBookingId = 123,
            nomisIncentiveSequence = 2,
            incentiveId = 54321,
            mappingType = "MIGRATION",
          ),
        )
      }

      @Test
      internal fun `will do nothing`(): Unit = runBlocking {
        service.migrateIncentive(
          MigrationContext(
            type = INCENTIVES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = IncentiveId(123, 2),
          ),
        )

        verifyNoInteractions(incentivesService)
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
          migrationType = INCENTIVES,
          estimatedRecordCount = 100,
        ),
      )

      service.cancel("123-2020-01-01")

      verify(auditService).sendAuditEvent(
        eq("MIGRATION_CANCEL_REQUESTED"),
        check {
          it as Map<*, *>
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["migrationType"]).isEqualTo(INCENTIVES.name)
        },
      )
    }
  }
}

fun pages(total: Long, startId: Long = 1): PageImpl<IncentiveId> = PageImpl<IncentiveId>(
  (startId..total - 1 + startId).map { IncentiveId(it, 1) },
  Pageable.ofSize(10),
  total,
)
