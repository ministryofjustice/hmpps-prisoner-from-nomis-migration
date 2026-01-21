package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.microsoft.applicationinsights.TelemetryClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.tuple
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.CANCEL_MIGRATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.RETRY_MIGRATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatusCheck
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisCodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisLeadVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisVisit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VisitId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.pageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsService.VisitCreated
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
@JsonTest
internal class VisitsMigrationServiceTest(@Autowired private val jsonMapper: JsonMapper) {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val visitMappingService: VisitMappingService = mock()
  private val visitsService: VisitsService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val auditService: AuditService = mock()
  val service = object : VisitsMigrationService(
    nomisApiService = nomisApiService,
    visitMappingService = visitMappingService,
    visitsService = visitsService,
    pageSize = 200,
    completeCheckDelaySeconds = 10,
    completeCheckCount = 9,
    jsonMapper = jsonMapper,
  ) {
    init {
      queueService = this@VisitsMigrationServiceTest.queueService
      migrationHistoryService = this@VisitsMigrationServiceTest.migrationHistoryService
      telemetryClient = this@VisitsMigrationServiceTest.telemetryClient
      auditService = this@VisitsMigrationServiceTest.auditService
    }
  }

  @Nested
  @DisplayName("migrateVisits")
  inner class MigrateVisits {
    private val nomisApiService = mockk<NomisApiService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val migrationHistoryService = mockk<MigrationHistoryService>(relaxed = true)

    /* coroutine version of service required for this route */
    private val service = object : VisitsMigrationService(
      nomisApiService = nomisApiService,
      visitMappingService = visitMappingService,
      visitsService = visitsService,
      pageSize = 200,
      completeCheckDelaySeconds = 10,
      completeCheckCount = 9,
      jsonMapper = jsonMapper,
    ) {
      init {
        queueService = this@VisitsMigrationServiceTest.queueService
        migrationHistoryService = this@MigrateVisits.migrationHistoryService
        telemetryClient = this@VisitsMigrationServiceTest.telemetryClient
        auditService = this@MigrateVisits.auditService
      }
    }
    private val auditWhatParam = slot<String>()
    private val auditDetailsParam = slot<Map<*, *>>()

    @BeforeEach
    internal fun setUp() {
      coEvery { nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any()) } returns
        pages(1)

      coEvery {
        auditService.sendAuditEvent(
          // makes mock match calls with any value for `speed` and record it in a slot
          what = capture(auditWhatParam),
          // makes mock and capturing only match calls with specific `direction`. Use `any()` to match calls with any `direction`
          details = capture(auditDetailsParam),
        )
      } just runs
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`() {
      runBlocking {
        service.startMigration(
          VisitsMigrationFilter(
            prisonIds = listOf("LEI", "BXI"),
            visitTypes = listOf("SCON"),
            fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
            toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
          ),
        )
      }

      coVerify {
        nomisApiService.getVisits(
          prisonIds = listOf("LEI", "BXI"),
          visitTypes = listOf("SCON"),
          fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
          toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
          ignoreMissingRoom = false,
          pageNumber = 0,
          pageSize = 1,
        )
      }
    }

    @Test
    internal fun `will pass visit count and filter to queue`(): Unit = runBlocking {
      coEvery { nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.startMigration(
          VisitsMigrationFilter(
            prisonIds = listOf("LEI"),
            visitTypes = listOf("SCON"),
            fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
            toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
          ),
        )
      }

      verify(queueService).sendMessage(
        message = eq(MigrationMessageType.MIGRATE_ENTITIES),
        context = check<MigrationContext<VisitsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(23)
          assertThat(it.body.prisonIds).containsExactly("LEI")
          assertThat(it.body.visitTypes).containsExactly("SCON")
          assertThat(it.body.fromDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T00:00:00"))
          assertThat(it.body.toDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T23:00:00"))
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will write migration history record`() {
      val visitsMigrationFilter = VisitsMigrationFilter(
        prisonIds = listOf("LEI", "BXI"),
        visitTypes = listOf("SCON"),
        fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
        toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
      )

      coEvery { nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.startMigration(
          visitsMigrationFilter,
        )
      }

      coVerify {
        migrationHistoryService.recordMigrationStarted(
          migrationId = any(),
          migrationType = VISITS,
          estimatedRecordCount = 23,
          filter = coWithArg<VisitsMigrationFilter> {
            assertThat(it.prisonIds).containsExactly("LEI", "BXI")
            assertThat(it.visitTypes).containsExactly("SCON")
            assertThat(it.fromDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T00:00:00"))
            assertThat(it.toDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T23:00:00"))
          },
        )
      }

      with(auditDetailsParam.captured) {
        assertThat(this).extracting("migrationId").isNotNull
        assertThat(this).extracting("migrationType").isEqualTo("VISITS")
        assertThat(this).extracting("filter").isEqualTo(visitsMigrationFilter)
      }
    }

    @Test
    internal fun `will write analytic with estimated count and filter`() {
      coEvery { nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any()) } returns pages(23)

      runBlocking {
        service.startMigration(
          VisitsMigrationFilter(
            prisonIds = listOf("LEI", "BXI"),
            visitTypes = listOf("SCON"),
            fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
            toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
          ),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("visits-migration-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["prisonIds"]).isEqualTo("[LEI, BXI]")
          assertThat(it["visitTypes"]).isEqualTo("[SCON]")
          assertThat(it["fromDateTime"]).isEqualTo("2020-01-01T00:00")
          assertThat(it["toDateTime"]).isEqualTo("2020-01-02T23:00")
        },
        eq(null),
      )
    }

    @Test
    internal fun `will write analytics with empty filter`() {
      coEvery {
        nomisApiService.getVisits(
          prisonIds = any(),
          visitTypes = any(),
          fromDateTime = isNull(),
          toDateTime = isNull(),
          ignoreMissingRoom = any(),
          pageNumber = any(),
          pageSize = any(),
        )
      } returns
        pages(23)

      runBlocking {
        service.startMigration(
          VisitsMigrationFilter(visitTypes = listOf()),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("visits-migration-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["prisonIds"]).isEqualTo("[]")
          assertThat(it["ignoreMissingRoom"]).isEqualTo("false")
          assertThat(it["visitTypes"]).isEqualTo("[]")
          assertThat(it["fromDateTime"]).isNull()
          assertThat(it["toDateTime"]).isNull()
        },
        eq(null),
      )
    }
  }

  @Nested
  @DisplayName("divideVisitsByPage")
  inner class DivideVisitsByPage {

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any())).thenReturn(
        pages(100_200),
      )
    }

    @Test
    internal fun `will send a page message for every page (200) of visits `(): Unit = runBlocking {
      service.divideEntitiesByPage(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitsMigrationFilter(
            prisonIds = listOf("LEI", "BXI"),
            visitTypes = listOf("SCON"),
            fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
            toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
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
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitsMigrationFilter(
            prisonIds = listOf("LEI", "BXI"),
            visitTypes = listOf("SCON"),
            fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
            toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
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
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitsMigrationFilter(
            prisonIds = listOf("LEI", "BXI"),
            visitTypes = listOf("SCON"),
            fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
            toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
          ),
        ),
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        message = eq(MIGRATE_BY_PAGE),
        context = check<MigrationContext<MigrationPage<VisitsMigrationFilter, *>>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.filter.prisonIds).containsExactly("LEI", "BXI")
          assertThat(it.body.filter.visitTypes).containsExactly("SCON")
          assertThat(it.body.filter.fromDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T00:00:00"))
          assertThat(it.body.filter.toDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T23:00:00"))
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `each page will contain page number and page size`(): Unit = runBlocking {
      val context: KArgumentCaptor<MigrationContext<MigrationPage<VisitsMigrationFilter, *>>> = argumentCaptor()

      service.divideEntitiesByPage(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitsMigrationFilter(
            prisonIds = listOf("LEI", "BXI"),
            visitTypes = listOf("SCON"),
            fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
            toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
          ),
        ),
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        eq(MIGRATE_BY_PAGE),
        context.capture(),
        delaySeconds = eq(0),
      )
      val allContexts: List<MigrationContext<MigrationPage<VisitsMigrationFilter, *>>> = context.allValues

      val (firstPage, secondPage, thirdPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.pageNumber()).isEqualTo(0)
      assertThat(firstPage.body.pageSize).isEqualTo(200)

      assertThat(secondPage.body.pageNumber()).isEqualTo(1)
      assertThat(secondPage.body.pageSize).isEqualTo(200)

      assertThat(thirdPage.body.pageNumber()).isEqualTo(2)
      assertThat(thirdPage.body.pageSize).isEqualTo(200)

      assertThat(lastPage.body.pageNumber()).isEqualTo((100_200 / 200) - 1)
      assertThat(lastPage.body.pageSize).isEqualTo(200)
    }
  }

  @Nested
  @DisplayName("migrateStatusCheck")
  inner class MigrateStatusCheck {
    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
    }

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
            type = VISITS,
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
      internal fun `will check again in 10 second and reset even when previously started finishing up phase`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = VISITS,
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
        whenever(visitMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = VISITS,
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
            type = VISITS,
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
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("visits-migration-completed"),
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
        whenever(visitMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.migrateStatusCheck(
          MigrationContext(
            type = VISITS,
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
  @DisplayName("cancelMigrateVisitsStatusCheck")
  inner class CancelMigrateVisitsStatusCheck {
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
            type = VISITS,
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
      internal fun `will check again in 10 second and reset even when previously started finishing up phase`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = VISITS,
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
        whenever(visitMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 9),
          ),
        )

        verify(queueService).purgeAllMessages(check { assertThat(it).isEqualTo(VISITS) })

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
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(queueService, never()).purgeAllMessages(check { assertThat(it).isEqualTo(VISITS) })
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
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("visits-migration-cancelled"),
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
        whenever(visitMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = VISITS,
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
  @DisplayName("migrateVisitsForPage")
  inner class MigrateVisitsForPage {
    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any())).thenReturn(
        pages(15),
      )
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = VisitsMigrationFilter(
              prisonIds = listOf("LEI", "BXI"),
              visitTypes = listOf("SCON"),
              ignoreMissingRoom = true,
              fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
              toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
            ),
            pageKey = ByPageNumber(13),
            pageSize = 15,
          ),
        ),
      )

      verify(nomisApiService).getVisits(
        prisonIds = listOf("LEI", "BXI"),
        visitTypes = listOf("SCON"),
        fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
        toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
        ignoreMissingRoom = true,
        pageNumber = 13,
        pageSize = 15,
      )
    }

    @Test
    internal fun `will send MIGRATE_VISIT with context for each visit`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = VisitsMigrationFilter(
              prisonIds = listOf("LEI", "BXI"),
              visitTypes = listOf("SCON"),
              fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
              toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
            ),
            pageKey = ByPageNumber(13),
            pageSize = 15,
          ),
        ),
      )

      verify(queueService, times(15)).sendMessageNoTracing(
        message = eq(MIGRATE_ENTITY),
        context = check<MigrationContext<VisitsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will send MIGRATE_VISIT with visitId for each visit`(): Unit = runBlocking {
      val context: KArgumentCaptor<MigrationContext<VisitId>> = argumentCaptor()

      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any())).thenReturn(
        pages(
          15,
          startId = 1000,
        ),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = VisitsMigrationFilter(
              prisonIds = listOf("LEI", "BXI"),
              visitTypes = listOf("SCON"),
              fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
              toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
            ),
            pageKey = ByPageNumber(13),
            pageSize = 15,
          ),
        ),
      )

      verify(queueService, times(15)).sendMessageNoTracing(
        eq(MIGRATE_ENTITY),
        context.capture(),
        delaySeconds = eq(0),
      )
      val allContexts: List<MigrationContext<VisitId>> = context.allValues

      val (firstPage, secondPage, thirdPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.visitId).isEqualTo(1000)
      assertThat(secondPage.body.visitId).isEqualTo(1001)
      assertThat(thirdPage.body.visitId).isEqualTo(1002)
      assertThat(lastPage.body.visitId).isEqualTo(1014)
    }

    @Test
    internal fun `will not send MIGRATE_VISIT when cancelling`(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(true)

      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any())).thenReturn(
        pages(
          15,
          startId = 1000,
        ),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = VisitsMigrationFilter(
              prisonIds = listOf("LEI", "BXI"),
              visitTypes = listOf("SCON"),
              fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
              toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
            ),
            pageKey = ByPageNumber(13),
            pageSize = 15,
          ),
        ),
      )

      verifyNoInteractions(queueService)
    }
  }

  @Nested
  @DisplayName("migrateVisit")
  inner class MigrateVisit {
    private val yesterdayDateTime = LocalDateTime.now().minusDays(1)
    private val tomorrowDateTime = LocalDateTime.now().plusDays(1)
    private val inTwoYearsDateTime = LocalDateTime.now().plusYears(2)
    private val tomorrowDateTimeMorning = LocalDateTime.now().plusDays(1).withHour(10).withMinute(30).withSecond(56)
    private val tomorrowDateTimeAfternoon = LocalDateTime.now().plusDays(1).withHour(12).withMinute(0).withSecond(0)

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(visitMappingService.findNomisVisitMapping(any())).thenReturn(null)
      whenever(nomisApiService.getVisit(any())).thenReturn(
        NomisVisit(
          offenderNo = "A1234AA",
          visitId = 1234,
          startDateTime = LocalDateTime.parse("2020-01-01T10:00:00"),
          endDateTime = LocalDateTime.parse("2020-01-02T12:00:00"),
          agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
          prisonId = "BXI",
          visitors = listOf(
            NomisVisitor(
              personId = 4729570,
            ),
            NomisVisitor(
              personId = 4729580,
            ),
          ),
          visitType = NomisCodeDescription("SCON", "Social Contact"),
          visitStatus = NomisCodeDescription("CANC", "cancelled"),
          leadVisitor = NomisLeadVisitor(
            personId = 4729570,
            fullName = "Simon Mine",
            telephones = listOf("000 11111", "000 22222"),
          ),
          visitOutcome = NomisCodeDescription(NomisCancellationOutcome.NO_ID.name, "No Id"),
          commentText = "This is a comment",
          visitorConcernText = "I'm concerned",
          createUserId = "user1",
          whenCreated = LocalDateTime.parse("2020-01-01T09:00:00"),
          whenUpdated = LocalDateTime.parse("2020-01-02T14:00:00"),
        ),
      )
      whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
        RoomMapping(
          vsipId = "VSIP-ROOM-ID",
          isOpen = true,
        ),
      )

      whenever(visitsService.createVisit(any())).thenReturn(
        VisitCreated("654321"),
      )
    }

    @Test
    internal fun `will retrieve visit from NOMIS`(): Unit = runBlocking {
      service.migrateNomisEntity(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitId(123),
        ),
      )

      verify(nomisApiService).getVisit(123)
    }

    @Test
    internal fun `will retrieve room for NOMIS room id using internal agency description (future visits)`(): Unit = runBlocking {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          prisonId = "BXI",
          agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
          prisonerId = "A1234AA",
          startDateTime = LocalDateTime.now().plusDays(5),
        ),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitId(123),
        ),
      )

      verify(visitMappingService).findRoomMapping(
        prisonId = "BXI",
        agencyInternalLocationCode = "MDI-VISITS-OFF_VIS",
      )
    }

    @Test
    internal fun `migration abandoned due to room mapping missing - future date`(): Unit = runBlocking {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          prisonId = "BXI",
          agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
          prisonerId = "A1234AA",
          visitId = 123456,
          startDateTime = tomorrowDateTime,
        ),
      )

      whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(null)

      assertThatThrownBy {
        runBlocking {
          service.migrateNomisEntity(
            MigrationContext(
              type = VISITS,
              migrationId = "2020-05-23T11:30:00",
              estimatedCount = 100_200,
              body = VisitId(123),
            ),
          )
        }
      }.isInstanceOf(NoRoomMappingFoundException::class.java)

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-visit-no-room-mapping"),
        check {
          assertThat(it["migrationId"]).isEqualTo("2020-05-23T11:30:00")
          assertThat(it["prisonId"]).isEqualTo("BXI")
          assertThat(it["offenderNo"]).isEqualTo("A1234AA")
          assertThat(it["visitId"]).isEqualTo("123456")
          assertThat(LocalDateTime.parse(it["startDateTime"])).isEqualTo(tomorrowDateTime)
          assertThat(it["agencyInternalLocation"]).isEqualTo("MDI-VISITS-OFF_VIS")
        },
        eq(null),
      )
    }

    @Test
    internal fun `migration does not look up room mapping for historical visit`(): Unit = runBlocking {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          prisonId = "BXI",
          agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
          prisonerId = "A1234AA",
          visitId = 123456,
          startDateTime = yesterdayDateTime,
        ),
      )

      whenever(visitsService.createVisit(any())).thenReturn(
        VisitCreated("654321"),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitId(123),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("visits-migration-entity-migrated"),
        check {
          assertThat(it["migrationId"]).isEqualTo("2020-05-23T11:30:00")
          assertThat(it["prisonId"]).isEqualTo("BXI")
          assertThat(it["offenderNo"]).isEqualTo("A1234AA")
          assertThat(it["visitId"]).isEqualTo("123456")
          assertThat(it["vsipVisitId"]).isEqualTo("654321")
          assertThat(LocalDateTime.parse(it["startDateTime"])).isEqualTo(yesterdayDateTime)
          assertThat(it["room"]).isEqualTo("MDI-VISITS-OFF_VIS")
        },
        eq(null),
      )

      verify(visitMappingService, never()).findRoomMapping(any(), any())
    }

    @Test
    internal fun `migration does not look up room mapping for bad visit data - defined as more than a year in the future`(): Unit = runBlocking {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          prisonId = "BXI",
          agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
          prisonerId = "A1234AA",
          visitId = 123456,
          startDateTime = inTwoYearsDateTime,
        ),
      )

      whenever(visitsService.createVisit(any())).thenReturn(
        VisitCreated("654321"),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitId(123),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("visits-migration-entity-migrated"),
        check {
          assertThat(it["migrationId"]).isEqualTo("2020-05-23T11:30:00")
          assertThat(it["prisonId"]).isEqualTo("BXI")
          assertThat(it["offenderNo"]).isEqualTo("A1234AA")
          assertThat(it["visitId"]).isEqualTo("123456")
          assertThat(it["vsipVisitId"]).isEqualTo("654321")
          assertThat(LocalDateTime.parse(it["startDateTime"])).isEqualTo(inTwoYearsDateTime)
          assertThat(it["room"]).isEqualTo("MDI-VISITS-OFF_VIS")
        },
        eq(null),
      )

      verify(visitMappingService, never()).findRoomMapping(any(), any())
    }

    @Test
    internal fun `when no room found in NOMIS, migration for this visit is abandoned (future visits)`(): Unit = runBlocking {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          agencyInternalLocation = null,
          startDateTime = tomorrowDateTime,
        ),
      )

      assertThatThrownBy {
        runBlocking {
          service.migrateNomisEntity(
            MigrationContext(
              type = VISITS,
              migrationId = "2020-05-23T11:30:00",
              estimatedCount = 100_200,
              body = VisitId(123),
            ),
          )
        }
      }.isInstanceOf(NoRoomMappingFoundException::class.java)
    }

    @Test
    internal fun `when no room found in NOMIS, migration for this visit is allowed (historical visits)`(): Unit = runBlocking {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          agencyInternalLocation = null,
          startDateTime = yesterdayDateTime,
        ),
      )

      whenever(visitsService.createVisit(any())).thenReturn(
        VisitCreated("654321"),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitId(123),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("visits-migration-entity-migrated"),
        check {
          assertThat(LocalDateTime.parse(it["startDateTime"])).isEqualTo(yesterdayDateTime)
          assertThat(it["room"]).isEqualTo("UNKNOWN")
        },
        eq(null),
      )
    }

    @Test
    internal fun `will create a visit in VSIP`(): Unit = runBlocking {
      service.migrateNomisEntity(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitId(123),
        ),
      )

      verify(visitsService).createVisit(any())
    }

    @Test
    internal fun `will map future visit times to designated slots for Hewell - morning `(): Unit = runBlocking {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          prisonId = "HEI",
          agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
          prisonerId = "A1234AA",
          visitId = 123456,
          startDateTime = tomorrowDateTimeMorning,
          endDateTime = tomorrowDateTimeMorning.plusHours(2),
        ),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitId(123),
        ),
      )

      verify(visitsService).createVisit(
        check {
          assertThat(it.startTimestamp).isEqualTo(tomorrowDateTimeMorning.toLocalDate().atTime(9, 0))
          assertThat(it.endTimestamp).isEqualTo(tomorrowDateTimeMorning.toLocalDate().atTime(10, 0))
        },
      )
    }

    @Test
    internal fun `will map future visit times to designated slots for Hewell - afternoon `(): Unit = runBlocking {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          prisonId = "HEI",
          agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
          prisonerId = "A1234AA",
          visitId = 123456,
          startDateTime = tomorrowDateTimeAfternoon,
          endDateTime = tomorrowDateTimeAfternoon.plusHours(2),
        ),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitId(123),
        ),
      )

      verify(visitsService).createVisit(
        check {
          assertThat(it.startTimestamp).isEqualTo(tomorrowDateTimeAfternoon.toLocalDate().atTime(13, 45))
          assertThat(it.endTimestamp).isEqualTo(tomorrowDateTimeAfternoon.toLocalDate().atTime(14, 45))
        },
      )
    }

    @Test
    internal fun `will leave historical visit times unchanged for Hewell`(): Unit = runBlocking {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          prisonId = "HEI",
          agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
          prisonerId = "A1234AA",
          visitId = 123456,
          startDateTime = yesterdayDateTime,
          endDateTime = yesterdayDateTime.plusHours(2),
        ),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitId(123),
        ),
      )

      verify(visitsService).createVisit(
        check {
          assertThat(it.startTimestamp).isEqualTo(yesterdayDateTime)
          assertThat(it.endTimestamp).isEqualTo(yesterdayDateTime.plusHours(2))
        },
      )
    }

    @Nested
    inner class NomisToVsipMapping {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
            prisonId = "BXI",
            prisonerId = "A1234AA",
            startDateTime = LocalDateTime.parse("2020-01-01T10:00:00"),
            endDateTime = LocalDateTime.parse("2020-01-02T12:00:00"),
            createUserId = "create-user",
            modifyUserId = "modify-user",
          ),
        )
        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = true,
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )
      }

      @Test
      internal fun `prisonId is copied`(): Unit = runBlocking {
        verify(visitsService).createVisit(
          check {
            assertThat(it.prisonId).isEqualTo("BXI")
          },
        )
      }

      @Test
      internal fun `prisonerId is copied`(): Unit = runBlocking {
        verify(visitsService).createVisit(
          check {
            assertThat(it.prisonerId).isEqualTo("A1234AA")
          },
        )
      }

      @Test
      internal fun `visit start date time is copied`(): Unit = runBlocking {
        verify(visitsService).createVisit(
          check {
            assertThat(it.startTimestamp).isEqualTo(LocalDateTime.parse("2020-01-01T10:00:00"))
          },
        )
      }

      @Test
      internal fun `visit end date time is copied`(): Unit = runBlocking {
        verify(visitsService).createVisit(
          check {
            assertThat(it.endTimestamp).isEqualTo(LocalDateTime.parse("2020-01-02T12:00:00"))
          },
        )
      }

      @Test
      internal fun `actioned by is copied`(): Unit = runBlocking {
        verify(visitsService).createVisit(
          check {
            assertThat(it.actionedBy).isEqualTo("modify-user")
          },
        )
      }

      @Test
      internal fun `actioned uses create if not modified`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(456)).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
            prisonId = "BXI",
            prisonerId = "A1234AA",
            startDateTime = LocalDateTime.parse("2020-01-01T10:00:00"),
            endDateTime = LocalDateTime.parse("2020-01-02T12:00:00"),
            createUserId = "create-user",
            modifyUserId = null,
          ),
        )
        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(456),
          ),
        )
        verify(visitsService).createVisit(
          check {
            assertThat(it.actionedBy).isEqualTo("create-user")
          },
        )
      }

      @Test
      internal fun `when created is copied`(): Unit = runBlocking {
        verify(visitsService).createVisit(
          check {
            assertThat(it.createDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T09:00:00"))
          },
        )
      }

      @Test
      internal fun `when updated is copied`(): Unit = runBlocking {
        verify(visitsService).createVisit(
          check {
            assertThat(it.modifyDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T14:00:00"))
          },
        )
      }

      @Test
      internal fun `contact information is copied`(): Unit = runBlocking {
        verify(visitsService).createVisit(
          check {
            assertThat(it.visitContact).isEqualTo(VsipLegacyContactOnVisit("Vince Hoyland", "0000 11111"))
          },
        )
      }

      @Test
      internal fun `legacy lead visitor personId is copied`(): Unit = runBlocking {
        verify(visitsService).createVisit(
          check {
            assertThat(it.legacyData).isEqualTo(VsipLegacyData(4729570))
          },
        )
      }

      @Test
      internal fun `outcome status when null is copied`(): Unit = runBlocking {
        verify(visitsService).createVisit(
          check {
            assertThat(it.outcomeStatus).isNull()
          },
        )
      }
    }

    @Nested
    @DisplayName("Visit restriction mapping (open/closed/unknown)")
    inner class NomisToVisitRestrictionMapping {
      @Test
      internal fun `visit restriction is set to UNKNOWN for historical visits (prior to today)`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("VSIP-ROOM-ID", "An open room"),
            startDateTime = LocalDateTime.now().minusDays(1),
            endDateTime = LocalDateTime.now().minusDays(1).plusHours(1),
          ),
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = true,
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitRestriction).isEqualTo(VisitRestriction.UNKNOWN)
          },
        )
      }

      @Test
      internal fun `visit restriction is set to correct value for a visit with today's date`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("VSIP-ROOM-ID", "An open room"),
            startDateTime = LocalDateTime.now(),
            endDateTime = LocalDateTime.now().plusHours(1),
          ),
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = true,
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitRestriction).isEqualTo(VisitRestriction.OPEN)
          },
        )
      }

      @Test
      internal fun `visit restriction is set to correct value for a visit booked with a future date`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("VSIP-ROOM-ID", "A closed room"),
            startDateTime = LocalDateTime.now().plusDays(5),
            endDateTime = LocalDateTime.now().plusDays(5).plusHours(1),
          ),
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = false,
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitRestriction).isEqualTo(VisitRestriction.CLOSED)
          },
        )
      }
    }

    @Nested
    @DisplayName("Visit comments mapping")
    inner class NomisToVisitCommentsMapping {
      @Test
      internal fun `visit comments are excluded from migration for historical visits (prior to today)`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            startDateTime = LocalDateTime.now().minusDays(1),
            endDateTime = LocalDateTime.now().minusDays(1).plusHours(1),
          ),
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = true,
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitNotes).isEmpty()
          },
        )
      }

      @Test
      internal fun `visit comments are included in the migration for a visit with today's date`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            startDateTime = LocalDateTime.now(),
            endDateTime = LocalDateTime.now().plusHours(1),
          ),
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = true,
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitNotes).extracting("text", "type").contains(
              tuple(
                "This is a comment",
                VsipVisitNoteType.VISIT_COMMENT,
              ),
              tuple("this is concerning", VsipVisitNoteType.VISITOR_CONCERN),
            )
          },
        )
      }

      @Test
      internal fun `visit comments are included in the migration for a visit booked with a future date`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            startDateTime = LocalDateTime.now().plusDays(5),
            endDateTime = LocalDateTime.now().plusDays(5).plusHours(1),
          ),
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = false,
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitNotes).extracting("text", "type").contains(
              tuple(
                "This is a comment",
                VsipVisitNoteType.VISIT_COMMENT,
              ),
              tuple("this is concerning", VsipVisitNoteType.VISITOR_CONCERN),
            )
          },
        )
      }
    }

    @Nested
    @DisplayName("Visit room mapping (date dependent)")
    inner class NomisToVisitRoomMapping {
      @Test
      internal fun `visit room is set to the nomis description, ignoring the VSIP mapping for historical visits (prior to today)`(): Unit = runBlocking {
        val aVisit = aVisit(
          agencyInternalLocation = NomisCodeDescription("NOMIS-ROOM-CODE", "NOMIS-ROOM-DESC"),
          startDateTime = LocalDateTime.now().minusDays(1),
          endDateTime = LocalDateTime.now().minusDays(1).plusHours(1),
        )
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit,
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = true,
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitRoom).isEqualTo("NOMIS-ROOM-DESC")
          },
        )
      }

      @Test
      internal fun `visit room is set to correct value for a visit with today's date`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("NOMIS-ROOM-CODE", "NOMIS-ROOM-DESC"),
            startDateTime = LocalDateTime.now(),
            endDateTime = LocalDateTime.now().plusHours(1),
          ),
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = true,
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitRoom).isEqualTo("VSIP-ROOM-ID")
          },
        )
      }

      @Test
      internal fun `visit room is set to correct value for a visit booked with a future date`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("NOMIS-ROOM-CODE", "NOMIS-ROOM-DESC"),
            startDateTime = LocalDateTime.now().plusDays(5),
            endDateTime = LocalDateTime.now().plusDays(5).plusHours(1),
          ),
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = false,
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitRoom).isEqualTo("VSIP-ROOM-ID")
          },
        )
      }
    }

    @Nested
    @DisplayName("visit status mapping ** REQUIRES ACCEPTANCE CRITERIA - currently wrong")
    inner class NomisToVisitStatusMapping {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = true,
          ),
        )
      }

      @Test
      internal fun `cancelled is mapped to vsip cancelled status`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            visitStatus = NomisCodeDescription("CANC", "Cancelled"),
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitStatus).isEqualTo(VsipStatus.CANCELLED)
          },
        )
      }

      @Test
      internal fun `all other statuses are mapped to booked`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            visitStatus = NomisCodeDescription("SCH", "Scheduled"),
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitStatus).isEqualTo(VsipStatus.BOOKED)
          },
        )
      }

      @Test
      internal fun `even completed is mapped to booked`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            visitStatus = NomisCodeDescription("COMP", "Completed"),
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitStatus).isEqualTo(VsipStatus.BOOKED)
          },
        )
      }
    }

    @Nested
    @DisplayName("visit type mapping")
    inner class NomisToVisitTypeMapping {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = true,
          ),
        )
      }

      @Test
      internal fun `social type is mapped to standard social`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            visitType = NomisCodeDescription("SCON", "Social Contact"),
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitType).isEqualTo("SOCIAL")
          },
        )
      }

      @Test
      internal fun `official type is mapped to official`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            visitType = NomisCodeDescription("OFFI", "Official Visit"),
          ),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitType).isEqualTo("OFFICIAL")
          },
        )
      }
    }

    @Test
    internal fun `will create mapping between VSIP and NOMIS visit`(): Unit = runBlocking {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          visitId = 123456,
        ),
      )
      whenever(visitsService.createVisit(any())).thenReturn(
        VisitCreated("654321"),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitId(123456),
        ),
      )

      verify(visitMappingService).createMapping(
        VisitNomisMapping(
          nomisId = 123456,
          vsipId = "654321",
          label = "2020-05-23T11:30:00",
          mappingType = "MIGRATED",
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<VisitNomisMapping>>() {},
      )
    }

    @Test
    internal fun `will not throw exception (and place message back on queue) but create a new retry message`(): Unit = runBlocking {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          visitId = 123456,
        ),
      )
      whenever(visitsService.createVisit(any())).thenReturn(
        VisitCreated("654321"),
      )

      whenever(visitMappingService.createMapping(any(), any())).thenThrow(
        RuntimeException("something went wrong"),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = VISITS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = VisitId(123456),
        ),
      )

      verify(queueService).sendMessage(
        message = eq(RETRY_MIGRATION_MAPPING),
        context = check<MigrationContext<VisitNomisMapping>> {
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.nomisId).isEqualTo(123456)
          assertThat(it.body.vsipId).isEqualTo("654321")
        },
        delaySeconds = eq(0),
      )
    }

    @Nested
    inner class Analytics {

      @Test
      internal fun `when successfully migrated`(): Unit = runBlocking {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            prisonId = "BXI",
            agencyInternalLocation = NomisCodeDescription("OFF_VIS", "NOMIS_ROOM_DESCRIPTION"),
            prisonerId = "A1234AA",
            visitId = 123456,
            startDateTime = LocalDateTime.parse("2018-05-23T11:30:00"),
          ),
        )
        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID",
            isOpen = true,
          ),
        )

        whenever(visitsService.createVisit(any())).thenReturn(
          VisitCreated("654321"),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("visits-migration-entity-migrated"),
          check {
            assertThat(it["migrationId"]).isEqualTo("2020-05-23T11:30:00")
            assertThat(it["prisonId"]).isEqualTo("BXI")
            assertThat(it["offenderNo"]).isEqualTo("A1234AA")
            assertThat(it["visitId"]).isEqualTo("123456")
            assertThat(it["vsipVisitId"]).isEqualTo("654321")
            assertThat(it["startDateTime"]).isEqualTo("2018-05-23T11:30:00")
            assertThat(it["room"]).isEqualTo("NOMIS_ROOM_DESCRIPTION")
          },
          eq(null),
        )
      }
    }

    @Nested
    inner class WhenMigratedAlready {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(visitMappingService.findNomisVisitMapping(any())).thenReturn(
          VisitNomisMapping(
            nomisId = 123,
            vsipId = "456",
            label = "2020-01-01T00:00:00",
            mappingType = "MIGRATED",
          ),
        )
      }

      @Test
      internal fun `will do nothing`(): Unit = runBlocking {
        service.migrateNomisEntity(
          MigrationContext(
            type = VISITS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitId(123),
          ),
        )

        verify(visitMappingService, never()).createMapping(any(), any())
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
          migrationType = VISITS,
          estimatedRecordCount = 100,
        ),
      )

      service.cancel("123-2020-01-01")

      verify(auditService).sendAuditEvent(
        eq("MIGRATION_CANCEL_REQUESTED"),
        check {
          it as Map<*, *>
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["migrationType"]).isEqualTo(VISITS.name)
        },
      )
    }
  }
}

fun pages(total: Long, startId: Long = 1): PageImpl<VisitId> = PageImpl<VisitId>(
  (startId..total - 1 + startId).map { VisitId(it) },
  Pageable.ofSize(10),
  total,
)

fun aVisit(
  prisonId: String = "BXI",
  agencyInternalLocation: NomisCodeDescription? = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
  prisonerId: String = "A1234AA",
  startDateTime: LocalDateTime = LocalDateTime.parse("2020-01-01T10:00:00"),
  endDateTime: LocalDateTime = LocalDateTime.parse("2020-01-02T12:00:00"),
  whenCreated: LocalDateTime = LocalDateTime.parse("2020-01-01T09:00:00"),
  whenUpdated: LocalDateTime = LocalDateTime.parse("2020-01-02T14:00:00"),
  visitType: NomisCodeDescription = NomisCodeDescription("SCON", "Social Contact"),
  visitStatus: NomisCodeDescription = NomisCodeDescription("SCH", "Scheduled"),
  visitId: Long = 123,
  createUserId: String = "user1",
  modifyUserId: String? = "user-modified",
) = NomisVisit(
  offenderNo = prisonerId,
  visitId = visitId,
  startDateTime = startDateTime,
  endDateTime = endDateTime,
  agencyInternalLocation = agencyInternalLocation,
  prisonId = prisonId,
  visitors = listOf(
    NomisVisitor(
      personId = 4729570,
    ),
    NomisVisitor(
      personId = 4729580,
    ),
  ),
  visitType = visitType,
  visitStatus = visitStatus,
  commentText = "This is a comment",
  visitorConcernText = "this is concerning",
  /* no outcome as only visits with a status of Cancelled will have an outcome returned from nomis */
  leadVisitor = NomisLeadVisitor(4729570, fullName = "Vince Hoyland", telephones = listOf("0000 11111", "0000 22222")),
  createUserId = createUserId,
  modifyUserId = modifyUserId,
  whenUpdated = whenUpdated,
  whenCreated = whenCreated,
)
