package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

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
import org.mockito.internal.verification.Times
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.CANCEL_MIGRATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITIES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.RETRY_MIGRATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NonAssociationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NonAssociationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.NonAssociation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.UpsertSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatusCheck
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.NON_ASSOCIATIONS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
internal class NonAssociationsMigrationServiceTest {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val auditService: AuditService = mock()
  private val nonAssociationsService: NonAssociationsService = mock()
  private val nonAssociationsMappingService: NonAssociationsMappingService = mock()
  val service = NonAssociationsMigrationService(
    nomisApiService = nomisApiService,
    queueService = queueService,
    migrationHistoryService = migrationHistoryService,
    telemetryClient = telemetryClient,
    auditService = auditService,
    nonAssociationsService = nonAssociationsService,
    nonAssociationsMappingService = nonAssociationsMappingService,
    pageSize = 200,
    completeCheckDelaySeconds = 10,
    completeCheckCount = 9,
  )

  @Nested
  @DisplayName("migrateNonAssociations")
  inner class MigrateNonAssociations {
    private val nomisApiService = mockk<NomisApiService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val migrationHistoryService = mockk<MigrationHistoryService>(relaxed = true)

    private val auditWhatParam = slot<String>()
    private val auditDetailsParam = slot<Map<*, *>>()

    val service = NonAssociationsMigrationService(
      nomisApiService = nomisApiService,
      queueService = queueService,
      migrationHistoryService = migrationHistoryService,
      telemetryClient = telemetryClient,
      auditService = auditService,
      nonAssociationsService = nonAssociationsService,
      nonAssociationsMappingService = nonAssociationsMappingService,
      pageSize = 200,
      completeCheckDelaySeconds = 10,
      completeCheckCount = 9,
    )

    @BeforeEach
    internal fun setUp() {
      coEvery { nomisApiService.getNonAssociationIds(any(), any(), any(), any()) } returns
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
          NonAssociationsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      coVerify {
        nomisApiService.getNonAssociationIds(
          fromDate = LocalDate.parse("2020-01-01"),
          toDate = LocalDate.parse("2020-01-02"),
          pageNumber = 0,
          pageSize = 1,
        )
      }
    }

    @Test
    internal fun `will pass non-association count and filter to queue`(): Unit = runBlocking {
      coEvery { nomisApiService.getNonAssociationIds(any(), any(), any(), any()) } returns
        pages(23)

      runTest {
        service.startMigration(
          NonAssociationsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      verify(queueService).sendMessage(
        message = eq(MIGRATE_ENTITIES),
        context = check<MigrationContext<NonAssociationsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(23)
          assertThat(it.body.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(it.body.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will write migration history record`() {
      val nonAssociationsMigrationFilter = NonAssociationsMigrationFilter(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
      )

      coEvery { nomisApiService.getNonAssociationIds(any(), any(), any(), any()) } returns
        pages(23)

      runTest {
        service.startMigration(
          nonAssociationsMigrationFilter,
        )
      }

      coVerify {
        migrationHistoryService.recordMigrationStarted(
          migrationId = any(),
          migrationType = NON_ASSOCIATIONS,
          estimatedRecordCount = 23,
          filter = coWithArg<NonAssociationsMigrationFilter> {
            assertThat(it.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
            assertThat(it.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
          },
        )
      }

      with(auditDetailsParam.captured) {
        assertThat(this).extracting("migrationId").isNotNull
        assertThat(this).extracting("filter").isEqualTo(nonAssociationsMigrationFilter)
      }
    }

    @Test
    internal fun `will write analytic with estimated count and filter`() {
      coEvery { nomisApiService.getNonAssociationIds(any(), any(), any(), any()) } returns
        pages(23)

      runTest {
        service.startMigration(
          NonAssociationsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("non-associations-migration-started"),
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
        nomisApiService.getNonAssociationIds(
          fromDate = isNull(),
          toDate = isNull(),
          pageNumber = any(),
          pageSize = any(),
        )
      } returns
        pages(23)

      runTest {
        service.startMigration(
          NonAssociationsMigrationFilter(),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("non-associations-migration-started"),
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
  @DisplayName("divideNonAssociationsByPage")
  inner class DivideNonAssociationsByPage {

    @BeforeEach
    internal fun setUp(): Unit = runTest {
      whenever(nomisApiService.getNonAssociationIds(any(), any(), any(), any())).thenReturn(
        pages(100_200),
      )
    }

    @Test
    internal fun `will send a page message for every page (200) of non-associations `(): Unit = runTest {
      service.divideEntitiesByPage(
        MigrationContext(
          type = NON_ASSOCIATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = NonAssociationsMigrationFilter(
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
          type = NON_ASSOCIATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = NonAssociationsMigrationFilter(
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
          type = NON_ASSOCIATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = NonAssociationsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        ),
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        message = eq(MIGRATE_BY_PAGE),
        context = check<MigrationContext<MigrationPage<NonAssociationsMigrationFilter>>> {
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
      val context: KArgumentCaptor<MigrationContext<MigrationPage<NonAssociationsMigrationFilter>>> = argumentCaptor()

      service.divideEntitiesByPage(
        MigrationContext(
          type = NON_ASSOCIATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = NonAssociationsMigrationFilter(
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
      val allContexts: List<MigrationContext<MigrationPage<NonAssociationsMigrationFilter>>> = context.allValues

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
  inner class MigrateNonAssociationsStatusCheck {
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
            type = NON_ASSOCIATIONS,
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
              type = NON_ASSOCIATIONS,
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
        whenever(nonAssociationsMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runTest {
        service.migrateStatusCheck(
          MigrationContext(
            type = NON_ASSOCIATIONS,
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
            type = NON_ASSOCIATIONS,
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
            type = NON_ASSOCIATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("non-associations-migration-completed"),
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
        whenever(nonAssociationsMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.migrateStatusCheck(
          MigrationContext(
            type = NON_ASSOCIATIONS,
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
            type = NON_ASSOCIATIONS,
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
              type = NON_ASSOCIATIONS,
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
        whenever(nonAssociationsMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runTest {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = NON_ASSOCIATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 9),
          ),
        )

        verify(queueService).purgeAllMessages(check { assertThat(it).isEqualTo(NON_ASSOCIATIONS) })

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
            type = NON_ASSOCIATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(queueService, never()).purgeAllMessages(check { assertThat(it).isEqualTo(NON_ASSOCIATIONS) })
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
            type = NON_ASSOCIATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("non-associations-migration-cancelled"),
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
        whenever(nonAssociationsMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = NON_ASSOCIATIONS,
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
  inner class MigrateNonAssociationsForPage {
    @BeforeEach
    internal fun setUp(): Unit = runTest {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
      whenever(nomisApiService.getNonAssociationIds(any(), any(), any(), any())).thenReturn(
        pages(15),
      )
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`(): Unit = runTest {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = NON_ASSOCIATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = NonAssociationsMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
            ),
            pageNumber = 13,
            pageSize = 15,
          ),
        ),
      )

      verify(nomisApiService).getNonAssociationIds(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
        pageNumber = 13,
        pageSize = 15,
      )
    }

    @Test
    internal fun `will send MIGRATE_NON_ASSOCIATION with context for each non-association`(): Unit =
      runTest {
        service.migrateEntitiesForPage(
          MigrationContext(
            type = NON_ASSOCIATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationPage(
              filter = NonAssociationsMigrationFilter(
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
          context = check<MigrationContext<NonAssociationsMigrationFilter>> {
            assertThat(it.estimatedCount).isEqualTo(100_200)
            assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          },
          delaySeconds = eq(0),
        )
      }

    @Test
    internal fun `will send MIGRATE_NON_ASSOCIATION with bookingId for each non-association`(): Unit =
      runTest {
        val context: KArgumentCaptor<MigrationContext<NonAssociationIdResponse>> = argumentCaptor()

        whenever(nomisApiService.getNonAssociationIds(any(), any(), any(), any())).thenReturn(
          pages(
            15,
            startId = 1000,
          ),
        )

        service.migrateEntitiesForPage(
          MigrationContext(
            type = NON_ASSOCIATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationPage(
              filter = NonAssociationsMigrationFilter(
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
        val allContexts: List<MigrationContext<NonAssociationIdResponse>> = context.allValues

        val (firstPage, secondPage, thirdPage) = allContexts
        val lastPage = allContexts.last()

        assertThat(firstPage.body.offenderNo1).isEqualTo("A1000BC")
        assertThat(firstPage.body.offenderNo2).isEqualTo("D1000EF")
        assertThat(secondPage.body.offenderNo1).isEqualTo("A1001BC")
        assertThat(thirdPage.body.offenderNo1).isEqualTo("A1002BC")
        assertThat(lastPage.body.offenderNo1).isEqualTo("A1014BC")
      }

    @Test
    internal fun `will not send MIGRATE_NON_ASSOCIATION when cancelling`(): Unit = runTest {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(true)

      whenever(nomisApiService.getNonAssociationIds(any(), any(), any(), any())).thenReturn(
        pages(
          15,
          startId = 1000,
        ),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = NON_ASSOCIATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = NonAssociationsMigrationFilter(
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
  @DisplayName("migrateNonAssociations")
  inner class MigrateNonAssociation {

    @BeforeEach
    internal fun setUp(): Unit = runTest {
      whenever(nonAssociationsMappingService.findNomisNonAssociationMapping(any(), any(), any())).thenReturn(null)
      whenever(nomisApiService.getNonAssociations(any(), any())).thenReturn(listOf(aNomisNonAssociationResponse()))
      whenever(nonAssociationsService.migrateNonAssociation(any())).thenReturn(aNonAssociation())
      whenever(nonAssociationsMappingService.createMapping(any(), any())).thenReturn(CreateMappingResult())
    }

    @Test
    internal fun `will retrieve all non associations between offender pair from NOMIS`(): Unit = runTest {
      service.migrateNomisEntity(
        MigrationContext(
          type = NON_ASSOCIATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = NonAssociationIdResponse("A1234BC", "D5678EF"),
        ),
      )

      verify(nomisApiService).getNonAssociations("A1234BC", "D5678EF")
    }

    @Test
    internal fun `will transform and send that non-association to the Non-associations service`(): Unit = runTest {
      whenever(nomisApiService.getNonAssociations(any(), any())).thenReturn(listOf(aNomisNonAssociationResponse()))

      service.migrateNomisEntity(
        MigrationContext(
          type = NON_ASSOCIATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = NonAssociationIdResponse("A1234BC", "D5678EF"),
        ),
      )

      verify(nonAssociationsService).migrateNonAssociation(
        eq(
          UpsertSyncRequest(
            firstPrisonerNumber = "A1234BC",
            firstPrisonerReason = UpsertSyncRequest.FirstPrisonerReason.VIC,
            secondPrisonerNumber = "D5678EF",
            secondPrisonerReason = UpsertSyncRequest.SecondPrisonerReason.PER,
            restrictionType = UpsertSyncRequest.RestrictionType.WING,
            comment = "Fight on Wing C",
            authorisedBy = "Jim Smith",
            lastModifiedByUsername = "TJONES_ADM",
            effectiveFromDate = LocalDate.parse("2023-09-25"),
            expiryDate = LocalDate.parse("2023-09-26"),
          ),
        ),
      )
    }

    @Test
    internal fun `will not migrate multiple open non-associations for the same offender pair`(): Unit = runTest {
      whenever(nomisApiService.getNonAssociations(any(), any())).thenReturn(
        listOf(
          aNomisNonAssociationResponse(expiryDate = null),
          aNomisNonAssociationResponse(typeSequence = 2, reason = "RIV"),
          aNomisNonAssociationResponse(typeSequence = 3, reason = "BUL", recipReason = "RIV", expiryDate = null),
        ),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = NON_ASSOCIATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = NonAssociationIdResponse("A1234BC", "D5678EF"),
        ),
      )

      verify(nonAssociationsService, Times(0)).migrateNonAssociation(
        eq(
          UpsertSyncRequest(
            firstPrisonerNumber = "A1234BC",
            firstPrisonerReason = UpsertSyncRequest.FirstPrisonerReason.VIC,
            secondPrisonerNumber = "D5678EF",
            secondPrisonerReason = UpsertSyncRequest.SecondPrisonerReason.PER,
            restrictionType = UpsertSyncRequest.RestrictionType.WING,
            comment = "Fight on Wing C",
            authorisedBy = "Jim Smith",
            lastModifiedByUsername = "TJONES_ADM",
            effectiveFromDate = LocalDate.parse("2023-09-25"),
            expiryDate = null,
          ),
        ),
      )

      verify(nonAssociationsService).migrateNonAssociation(
        eq(
          UpsertSyncRequest(
            firstPrisonerNumber = "A1234BC",
            firstPrisonerReason = UpsertSyncRequest.FirstPrisonerReason.RIV,
            secondPrisonerNumber = "D5678EF",
            secondPrisonerReason = UpsertSyncRequest.SecondPrisonerReason.PER,
            restrictionType = UpsertSyncRequest.RestrictionType.WING,
            comment = "Fight on Wing C",
            authorisedBy = "Jim Smith",
            lastModifiedByUsername = "TJONES_ADM",
            effectiveFromDate = LocalDate.parse("2023-09-25"),
            expiryDate = LocalDate.parse("2023-09-26"),
          ),
        ),
      )
      verify(nonAssociationsService).migrateNonAssociation(
        eq(
          UpsertSyncRequest(
            firstPrisonerNumber = "A1234BC",
            firstPrisonerReason = UpsertSyncRequest.FirstPrisonerReason.BUL,
            secondPrisonerNumber = "D5678EF",
            secondPrisonerReason = UpsertSyncRequest.SecondPrisonerReason.RIV,
            restrictionType = UpsertSyncRequest.RestrictionType.WING,
            comment = "Fight on Wing C",
            authorisedBy = "Jim Smith",
            lastModifiedByUsername = "TJONES_ADM",
            effectiveFromDate = LocalDate.parse("2023-09-25"),
            expiryDate = null,

          ),
        ),
      )
    }

    @Test
    internal fun `will migrate non-association when firstOffenderNo is greater than secondOffenderNo`(): Unit = runTest {
      whenever(nomisApiService.getNonAssociations(any(), any())).thenReturn(
        listOf(
          aNomisNonAssociationResponse(),
        ),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = NON_ASSOCIATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = NonAssociationIdResponse("D5678EF", "A1234BC"),
        ),
      )

      verify(nonAssociationsService).migrateNonAssociation(
        eq(
          UpsertSyncRequest(
            firstPrisonerNumber = "A1234BC",
            firstPrisonerReason = UpsertSyncRequest.FirstPrisonerReason.VIC,
            secondPrisonerNumber = "D5678EF",
            secondPrisonerReason = UpsertSyncRequest.SecondPrisonerReason.PER,
            restrictionType = UpsertSyncRequest.RestrictionType.WING,
            comment = "Fight on Wing C",
            authorisedBy = "Jim Smith",
            lastModifiedByUsername = "TJONES_ADM",
            effectiveFromDate = LocalDate.parse("2023-09-25"),
            expiryDate = LocalDate.parse("2023-09-26"),

          ),
        ),
      )
    }

    @Test
    internal fun `will create a correctly ordered mapping when firstOffenderNo is greater than secondOffenderNo`(): Unit =
      runTest {
        whenever(nomisApiService.getNonAssociations(any(), any())).thenReturn(
          listOf(aNomisNonAssociationResponse()),
        )
        whenever(nonAssociationsService.migrateNonAssociation(any())).thenReturn(
          aNonAssociation(),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = NON_ASSOCIATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = NonAssociationIdResponse("D5678EF", "A1234BC"),
          ),
        )

        verify(nonAssociationsMappingService).createMapping(
          NonAssociationMappingDto(
            nonAssociationId = 4321,
            firstOffenderNo = "A1234BC",
            secondOffenderNo = "D5678EF",
            nomisTypeSequence = 1,
            label = "2020-05-23T11:30:00",
            mappingType = NonAssociationMappingDto.MappingType.MIGRATED,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<NonAssociationMappingDto>>() {},
        )
      }

    @Test
    internal fun `will add telemetry events for migrated and non-migrated entries`(): Unit = runTest {
      whenever(nomisApiService.getNonAssociations(any(), any())).thenReturn(
        listOf(
          aNomisNonAssociationResponse(expiryDate = null),
          aNomisNonAssociationResponse(typeSequence = 2, reason = "RIV"),
          aNomisNonAssociationResponse(typeSequence = 3, reason = "BUL", recipReason = "RIV", expiryDate = null),
        ),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = NON_ASSOCIATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = NonAssociationIdResponse("A1234BC", "D5678EF"),
        ),
      )

      verify(telemetryClient, times(2)).trackEvent(
        eq("non-associations-migration-entity-migrated"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["nonAssociationId"]).isNotNull
          assertThat(it["firstOffenderNo"]).isEqualTo("A1234BC")
          assertThat(it["secondOffenderNo"]).isEqualTo("D5678EF")
          assertThat(it["nomisTypeSequence"]).isNotEqualTo("1")
        },
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("non-association-migration-entity-ignored"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["firstOffenderNo"]).isEqualTo("A1234BC")
          assertThat(it["secondOffenderNo"]).isEqualTo("D5678EF")
          assertThat(it["nomisTypeSequence"]).isEqualTo("1")
        },
        isNull(),
      )
    }

    @Test
    internal fun `will create a mapping between a new Non-Association and a NOMIS Non-Association`(): Unit =
      runTest {
        whenever(nomisApiService.getNonAssociations(any(), any())).thenReturn(
          listOf(aNomisNonAssociationResponse()),
        )
        whenever(nonAssociationsService.migrateNonAssociation(any())).thenReturn(
          aNonAssociation(),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = NON_ASSOCIATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = NonAssociationIdResponse("A1234BC", "D5678EF"),
          ),
        )

        verify(nonAssociationsMappingService).createMapping(
          NonAssociationMappingDto(
            nonAssociationId = 4321,
            firstOffenderNo = "A1234BC",
            secondOffenderNo = "D5678EF",
            nomisTypeSequence = 1,
            label = "2020-05-23T11:30:00",
            mappingType = NonAssociationMappingDto.MappingType.MIGRATED,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<NonAssociationMappingDto>>() {},
        )
      }

    @Test
    internal fun `will not throw an exception (and place message back on queue) but create a new retry message`(): Unit =
      runTest {
        whenever(nomisApiService.getNonAssociations(any(), any())).thenReturn(listOf(aNomisNonAssociationResponse()))
        whenever(nonAssociationsService.migrateNonAssociation(any())).thenReturn(aNonAssociation())

        whenever(
          nonAssociationsMappingService.createMapping(
            any(),
            eq(object : ParameterizedTypeReference<DuplicateErrorResponse<NonAssociationMappingDto>>() {}),
          ),
        ).thenThrow(
          RuntimeException("something went wrong"),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = NON_ASSOCIATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = NonAssociationIdResponse("A1234BC", "B5678EF"),
          ),
        )

        verify(queueService).sendMessage(
          message = eq(RETRY_MIGRATION_MAPPING),
          context = check<MigrationContext<NonAssociationMappingDto>> {
            assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
            assertThat(it.body.firstOffenderNo).isEqualTo("A1234BC")
            assertThat(it.body.secondOffenderNo).isEqualTo("B5678EF")
            assertThat(it.body.nomisTypeSequence).isEqualTo(1)
          },
          delaySeconds = eq(0),
        )
      }

    @Nested
    inner class WhenMigratedAlready {
      @BeforeEach
      internal fun setUp(): Unit = runTest {
        whenever(nonAssociationsMappingService.findNomisNonAssociationMapping(any(), any(), any())).thenReturn(
          NonAssociationMappingDto(
            nonAssociationId = 4321,
            firstOffenderNo = "A1234BC",
            secondOffenderNo = "D5678EF",
            nomisTypeSequence = 1,
            mappingType = NonAssociationMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
      }

      @Test
      internal fun `will do nothing`(): Unit = runTest {
        service.migrateNomisEntity(
          MigrationContext(
            type = NON_ASSOCIATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = NonAssociationIdResponse("A1234BC", "D5678EF"),
          ),
        )

        verifyNoInteractions(nonAssociationsService)
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
          migrationType = NON_ASSOCIATIONS,
          estimatedRecordCount = 100,
        ),
      )

      service.cancel("123-2020-01-01")

      verify(auditService).sendAuditEvent(
        eq("MIGRATION_CANCEL_REQUESTED"),
        check {
          it as Map<*, *>
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["migrationType"]).isEqualTo(NON_ASSOCIATIONS.name)
        },
      )
    }
  }
}

fun aNomisNonAssociationResponse(
  offenderNo: String = "A1234BC",
  nsOffenderNo: String = "D5678EF",
  typeSequence: Int = 1,
  reason: String = "VIC",
  recipReason: String = "PER",
  expiryDate: LocalDate? = LocalDate.parse("2023-09-26"),
) =
  NonAssociationResponse(
    offenderNo = offenderNo,
    nsOffenderNo = nsOffenderNo,
    typeSequence = typeSequence,
    reason = reason,
    recipReason = recipReason,
    type = "WING",
    updatedBy = "TJONES_ADM",
    authorisedBy = "Jim Smith",
    effectiveDate = LocalDate.parse("2023-09-25"),
    expiryDate = expiryDate,
    comment = "Fight on Wing C",
  )

fun aNonAssociation() = NonAssociation(
  id = 4321,
  firstPrisonerNumber = "A1234BC",
  firstPrisonerRole = NonAssociation.FirstPrisonerRole.VICTIM,
  firstPrisonerRoleDescription = "Victim",
  secondPrisonerNumber = "D5678EF",
  secondPrisonerRole = NonAssociation.SecondPrisonerRole.PERPETRATOR,
  secondPrisonerRoleDescription = "Perpetrator",
  reason = NonAssociation.Reason.BULLYING,
  reasonDescription = "Bullying",
  restrictionType = NonAssociation.RestrictionType.CELL,
  restrictionTypeDescription = "Cell",
  comment = "John and Luke always end up fighting",
  whenCreated = "2023-07-05T11:12:45",
  whenUpdated = "2023-07-06T13:35:17",
  updatedBy = "OFF3_GEN",
  isClosed = false,
  closedBy = "null",
  closedReason = "null",
  closedAt = "2023-07-09T15:44:23",
  isOpen = true,
)

fun pages(total: Long, startId: Long = 1): PageImpl<NonAssociationIdResponse> = PageImpl<NonAssociationIdResponse>(
  (startId..total - 1 + startId).map { idCreator(it) },
  Pageable.ofSize(10),
  total,
)

private fun idCreator(id: Long): NonAssociationIdResponse {
  val fourDigitString = String.format("%04d", id)
  return NonAssociationIdResponse("A${fourDigitString}BC", "D${fourDigitString}EF")
}
