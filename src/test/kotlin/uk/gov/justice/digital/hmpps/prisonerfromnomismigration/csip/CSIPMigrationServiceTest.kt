package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.microsoft.applicationinsights.TelemetryClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsCsipReportSyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiMockServer.Companion.dpsSyncCsipRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPNomisApiMockServer.Companion.nomisCSIPReport
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.CANCEL_MIGRATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITIES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.RETRY_MIGRATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto.MappingType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatusCheck
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.CSIP
import java.time.LocalDate
import java.time.LocalDateTime

private const val NOMIS_CSIP_ID = 1234L
private const val DPS_CSIP_ID = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5"

@ExtendWith(MockitoExtension::class)
internal class CSIPMigrationServiceTest {
  private val nomisApiService: CSIPNomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val auditService: AuditService = mock()
  private val csipDpsService: CSIPDpsApiService = mock()
  private val csipMappingService: CSIPMappingService = mock()
  val service = CSIPMigrationService(
    nomisApiService = nomisApiService,
    queueService = queueService,
    migrationHistoryService = migrationHistoryService,
    telemetryClient = telemetryClient,
    auditService = auditService,
    csipService = csipDpsService,
    csipMappingService = csipMappingService,
    pageSize = 200,
    completeCheckDelaySeconds = 10,
    completeCheckCount = 9,
  )

  @Nested
  @DisplayName("migrateCSIPs")
  inner class MigrateCSIPs {
    private val nomisApiService = mockk<CSIPNomisApiService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val migrationHistoryService = mockk<MigrationHistoryService>(relaxed = true)

    private val auditWhatParam = slot<String>()
    private val auditDetailsParam = slot<Map<*, *>>()

    val service = CSIPMigrationService(
      nomisApiService = nomisApiService,
      queueService = queueService,
      migrationHistoryService = migrationHistoryService,
      telemetryClient = telemetryClient,
      auditService = auditService,
      csipService = csipDpsService,
      csipMappingService = csipMappingService,
      pageSize = 200,
      completeCheckDelaySeconds = 10,
      completeCheckCount = 9,
    )

    @BeforeEach
    internal fun setUp() {
      coEvery { nomisApiService.getCSIPIds(any(), any(), any(), any()) } returns
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
      runTest {
        service.startMigration(
          CSIPMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      coVerify {
        nomisApiService.getCSIPIds(
          fromDate = LocalDate.parse("2020-01-01"),
          toDate = LocalDate.parse("2020-01-02"),
          pageNumber = 0,
          pageSize = 1,
        )
      }
    }

    @Test
    internal fun `will pass csip count and filter to queue`(): Unit = runBlocking {
      coEvery { nomisApiService.getCSIPIds(any(), any(), any(), any()) } returns
        pages(23)

      runTest {
        service.startMigration(
          CSIPMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      verify(queueService).sendMessage(
        message = eq(MIGRATE_ENTITIES),
        context = check<MigrationContext<CSIPMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(23)
          assertThat(it.body.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(it.body.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will write migration history record`() {
      val csipMigrationFilter = CSIPMigrationFilter(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
      )

      coEvery { nomisApiService.getCSIPIds(any(), any(), any(), any()) } returns
        pages(23)

      runTest {
        service.startMigration(
          csipMigrationFilter,
        )
      }

      coVerify {
        migrationHistoryService.recordMigrationStarted(
          migrationId = any(),
          migrationType = CSIP,
          estimatedRecordCount = 23,
          filter = coWithArg<CSIPMigrationFilter> {
            assertThat(it.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
            assertThat(it.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
          },
        )
      }

      with(auditDetailsParam.captured) {
        assertThat(this).extracting("migrationId").isNotNull
        assertThat(this).extracting("filter").isEqualTo(csipMigrationFilter)
      }
    }

    @Test
    internal fun `will write analytic with estimated count and filter`() {
      coEvery { nomisApiService.getCSIPIds(any(), any(), any(), any()) } returns
        pages(23)

      runTest {
        service.startMigration(
          CSIPMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("csip-migration-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["fromDate"]).isEqualTo("2020-01-01")
          assertThat(it["toDate"]).isEqualTo("2020-01-02")
        },
        isNull(),
      )
    }

    @Test
    internal fun `will write analytics with empty filter`() {
      coEvery {
        nomisApiService.getCSIPIds(
          fromDate = isNull(),
          toDate = isNull(),
          pageNumber = any(),
          pageSize = any(),
        )
      } returns
        pages(23)

      runTest {
        service.startMigration(
          CSIPMigrationFilter(),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("csip-migration-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["fromDate"]).isNull()
          assertThat(it["toDate"]).isNull()
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("divideCSIPByPage")
  inner class DivideCSIPByPage {

    @BeforeEach
    internal fun setUp(): Unit = runTest {
      whenever(nomisApiService.getCSIPIds(any(), any(), any(), any())).thenReturn(
        pages(100_200),
      )
    }

    @Test
    internal fun `will send a page message for every page (200) of csip `(): Unit = runTest {
      service.divideEntitiesByPage(
        MigrationContext(
          type = CSIP,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = CSIPMigrationFilter(
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
    internal fun `will also send a single MIGRATION_STATUS_CHECK message`(): Unit = runTest {
      service.divideEntitiesByPage(
        MigrationContext(
          type = CSIP,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = CSIPMigrationFilter(
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
    internal fun `each page with have the filter and context attached`(): Unit = runTest {
      service.divideEntitiesByPage(
        MigrationContext(
          type = CSIP,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = CSIPMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        ),
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        message = eq(MIGRATE_BY_PAGE),
        context = check<MigrationContext<MigrationPage<CSIPMigrationFilter>>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.filter.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(it.body.filter.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `each page will contain page number and page size`(): Unit = runTest {
      val context: KArgumentCaptor<MigrationContext<MigrationPage<CSIPMigrationFilter>>> = argumentCaptor()

      service.divideEntitiesByPage(
        MigrationContext(
          type = CSIP,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = CSIPMigrationFilter(
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
      val allContexts: List<MigrationContext<MigrationPage<CSIPMigrationFilter>>> = context.allValues

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
  inner class MigrateCSIPStatusCheck {
    @Nested
    @DisplayName("when there are still messages on the queue")
    inner class MessagesOnQueue {
      @BeforeEach
      internal fun setUp(): Unit = runTest {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(true)
      }

      @Test
      internal fun `will check again in 10 seconds`(): Unit = runTest {
        service.migrateStatusCheck(
          MigrationContext(
            type = CSIP,
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
        runTest {
          service.migrateStatusCheck(
            MigrationContext(
              type = CSIP,
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
      internal fun setUp(): Unit = runTest {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(0)
        whenever(csipMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runTest {
        service.migrateStatusCheck(
          MigrationContext(
            type = CSIP,
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
      internal fun `will finish off when checked 10 times previously`(): Unit = runTest {
        service.migrateStatusCheck(
          MigrationContext(
            type = CSIP,
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
      internal fun `will add completed telemetry when finishing off`(): Unit = runTest {
        service.migrateStatusCheck(
          MigrationContext(
            type = CSIP,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("csip-migration-completed"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("23")
            assertThat(it["durationMinutes"]).isNotNull()
          },
          isNull(),
        )
      }

      @Test
      internal fun `will update migration history record when finishing off`(): Unit = runTest {
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(2)
        whenever(csipMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.migrateStatusCheck(
          MigrationContext(
            type = CSIP,
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
      internal fun setUp(): Unit = runTest {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(true)
      }

      @Test
      internal fun `will check again in 10 seconds`(): Unit = runTest {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = CSIP,
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
        runTest {
          service.cancelMigrateStatusCheck(
            MigrationContext(
              type = CSIP,
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
      internal fun setUp(): Unit = runTest {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(0)
        whenever(csipMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runTest {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = CSIP,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 9),
          ),
        )

        verify(queueService).purgeAllMessages(check { assertThat(it).isEqualTo(CSIP) })

        verify(queueService).sendMessage(
          message = eq(CANCEL_MIGRATION),
          context = check<MigrationContext<MigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(10)
          },
          delaySeconds = eq(1),
        )
      }

      @Test
      internal fun `will finish off when checked 10 times previously`(): Unit = runTest {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = CSIP,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(queueService, never()).purgeAllMessages(check { assertThat(it).isEqualTo(CSIP) })
        verify(queueService, never()).sendMessage(
          message = eq(CANCEL_MIGRATION),
          context = any(),
          delaySeconds = any(),
        )
      }

      @Test
      internal fun `will add completed telemetry when finishing off`(): Unit = runTest {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = CSIP,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("csip-migration-cancelled"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("23")
            assertThat(it["durationMinutes"]).isNotNull()
          },
          isNull(),
        )
      }

      @Test
      internal fun `will update migration history record when cancelling`(): Unit = runTest {
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(2)
        whenever(csipMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = CSIP,
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
  inner class MigrateCSIPForPage {
    @BeforeEach
    internal fun setUp(): Unit = runTest {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
      whenever(nomisApiService.getCSIPIds(any(), any(), any(), any())).thenReturn(
        pages(15),
      )
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`(): Unit = runTest {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = CSIP,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = CSIPMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
            ),
            pageNumber = 13,
            pageSize = 15,
          ),
        ),
      )

      verify(nomisApiService).getCSIPIds(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
        pageNumber = 13,
        pageSize = 15,
      )
    }

    @Test
    internal fun `will send MIGRATE_CSIP with context for each csip`(): Unit =
      runTest {
        service.migrateEntitiesForPage(
          MigrationContext(
            type = CSIP,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationPage(
              filter = CSIPMigrationFilter(
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
          context = check<MigrationContext<CSIPMigrationFilter>> {
            assertThat(it.estimatedCount).isEqualTo(100_200)
            assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          },
          delaySeconds = eq(0),
        )
      }

    @Test
    internal fun `will send MIGRATE_CSIP with bookingId for each csip`(): Unit =
      runTest {
        val context: KArgumentCaptor<MigrationContext<CSIPIdResponse>> = argumentCaptor()

        whenever(nomisApiService.getCSIPIds(any(), any(), any(), any())).thenReturn(
          pages(
            15,
            startId = 1000,
          ),
        )

        service.migrateEntitiesForPage(
          MigrationContext(
            type = CSIP,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationPage(
              filter = CSIPMigrationFilter(
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
        val allContexts: List<MigrationContext<CSIPIdResponse>> = context.allValues

        val (firstPage, secondPage, thirdPage) = allContexts
        val lastPage = allContexts.last()

        assertThat(firstPage.body.csipId).isEqualTo(1000)
        assertThat(secondPage.body.csipId).isEqualTo(1001)
        assertThat(thirdPage.body.csipId).isEqualTo(1002)
        assertThat(lastPage.body.csipId).isEqualTo(1014)
      }

    @Test
    internal fun `will not send MIGRATE_CSIP when cancelling`(): Unit = runTest {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(true)

      whenever(nomisApiService.getCSIPIds(any(), any(), any(), any())).thenReturn(
        pages(
          15,
          startId = 1000,
        ),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = CSIP,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = CSIPMigrationFilter(
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
  @DisplayName("migrateCSIP")
  inner class MigrateCSIP {

    @BeforeEach
    internal fun setUp(): Unit = runTest {
      whenever(csipMappingService.getCSIPReportByNomisId(any())).thenReturn(null)
      whenever(nomisApiService.getCSIP(any())).thenReturn(nomisCSIPReport())
      whenever(csipDpsService.migrateCSIP(any())).thenReturn(dpsCsipReportSyncResponse())
      whenever(csipMappingService.createMapping(any(), any())).thenReturn(CreateMappingResult())
    }

    @Test
    internal fun `will retrieve an csip from NOMIS`(): Unit = runTest {
      service.migrateNomisEntity(
        MigrationContext(
          type = CSIP,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = CSIPIdResponse(NOMIS_CSIP_ID),
        ),
      )

      verify(nomisApiService).getCSIP(NOMIS_CSIP_ID)
    }

    @Test
    internal fun `will transform and send that csip to the csip api service`(): Unit = runTest {
      whenever(nomisApiService.getCSIP(any())).thenReturn(nomisCSIPReport())

      service.migrateNomisEntity(
        MigrationContext(
          type = CSIP,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = CSIPIdResponse(NOMIS_CSIP_ID),
        ),
      )

      verify(csipDpsService).migrateCSIP(
        eq(dpsSyncCsipRequest()),
      )
    }

    @Test
    internal fun `will add telemetry events`(): Unit = runTest {
      whenever(nomisApiService.getCSIP(any())).thenReturn(nomisCSIPReport())

      service.migrateNomisEntity(
        MigrationContext(
          type = CSIP,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = CSIPIdResponse(NOMIS_CSIP_ID),
        ),
      )

      verify(telemetryClient, times(1)).trackEvent(
        eq("csip-migration-entity-migrated"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["nomisCSIPId"]).isNotNull
          assertThat(it["dpsCSIPId"]).isNotNull
        },
        isNull(),
      )
    }

    @Test
    internal fun `will create a mapping between a new csip and a NOMIS csip`(): Unit =
      runTest {
        whenever(nomisApiService.getCSIP(any())).thenReturn(nomisCSIPReport())
        whenever(csipDpsService.migrateCSIP(any())).thenReturn(dpsCsipReportSyncResponse())

        service.migrateNomisEntity(
          MigrationContext(
            type = CSIP,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = CSIPIdResponse(NOMIS_CSIP_ID),
          ),
        )

        verify(csipMappingService).createMapping(
          CSIPMappingDto(
            dpsCSIPId = DPS_CSIP_ID,
            nomisCSIPId = NOMIS_CSIP_ID,
            label = "2020-05-23T11:30:00",
            mappingType = MappingType.MIGRATED,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPMappingDto>>() {},
        )
      }

    @Test
    internal fun `will not throw an exception (and place message back on queue) but create a new retry message`(): Unit =
      runTest {
        whenever(nomisApiService.getCSIP(any())).thenReturn(nomisCSIPReport())
        whenever(csipDpsService.migrateCSIP(any())).thenReturn(dpsCsipReportSyncResponse())

        whenever(
          csipMappingService.createMapping(
            any(),
            eq(object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPMappingDto>>() {}),
          ),
        ).thenThrow(
          RuntimeException("something went wrong"),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = CSIP,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = CSIPIdResponse(NOMIS_CSIP_ID),
          ),
        )

        verify(queueService).sendMessage(
          message = eq(RETRY_MIGRATION_MAPPING),
          context = check<MigrationContext<CSIPMappingDto>> {
            assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
            assertThat(it.body.nomisCSIPId).isEqualTo(NOMIS_CSIP_ID)
            assertThat(it.body.dpsCSIPId).isEqualTo(DPS_CSIP_ID)
          },
          delaySeconds = eq(0),
        )
      }

    @Nested
    inner class WhenMigratedAlready {
      @BeforeEach
      internal fun setUp(): Unit = runTest {
        whenever(csipMappingService.getCSIPReportByNomisId(any())).thenReturn(
          CSIPMappingDto(
            dpsCSIPId = DPS_CSIP_ID,
            nomisCSIPId = NOMIS_CSIP_ID,
            mappingType = MappingType.NOMIS_CREATED,
          ),
        )
      }

      @Test
      internal fun `will do nothing`(): Unit = runTest {
        service.migrateNomisEntity(
          MigrationContext(
            type = CSIP,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = CSIPIdResponse(NOMIS_CSIP_ID),
          ),
        )

        verifyNoInteractions(csipDpsService)
      }
    }
  }

  @Test
  internal fun `will create audit event on user cancel`() {
    runTest {
      whenever(migrationHistoryService.get("123-2020-01-01")).thenReturn(
        MigrationHistory(
          migrationId = "123-2020-01-01 ",
          status = MigrationStatus.CANCELLED,
          whenEnded = LocalDateTime.parse("2020-01-01T00:00:00"),
          whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
          migrationType = CSIP,
          estimatedRecordCount = 100,
        ),
      )

      service.cancel("123-2020-01-01")

      verify(auditService).sendAuditEvent(
        eq("MIGRATION_CANCEL_REQUESTED"),
        check {
          it as Map<*, *>
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["migrationType"]).isEqualTo(CSIP.name)
        },
      )
    }
  }
}

fun pages(total: Long, startId: Long = 1): PageImpl<CSIPIdResponse> = PageImpl<CSIPIdResponse>(
  (startId..total - 1 + startId).map { CSIPIdResponse(it) },
  Pageable.ofSize(10),
  total,
)
