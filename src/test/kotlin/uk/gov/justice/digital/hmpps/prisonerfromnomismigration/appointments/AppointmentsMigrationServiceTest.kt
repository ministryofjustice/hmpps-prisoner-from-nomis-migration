package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AppointmentInstance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AppointmentMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.CANCEL_MIGRATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITIES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.RETRY_MIGRATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NomisDpsLocationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatusCheck
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.APPOINTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.pageNumber
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@JsonTest
internal class AppointmentsMigrationServiceTest(@Autowired private val jsonMapper: JsonMapper) {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val auditService: AuditService = mock()
  private val appointmentsService: AppointmentsService = mock()
  private val appointmentsMappingService: AppointmentsMappingService = mock()
  val service = object : AppointmentsMigrationService(
    nomisApiService = nomisApiService,
    appointmentsService = appointmentsService,
    appointmentsMappingService = appointmentsMappingService,
    pageSize = 200,
    completeCheckDelaySeconds = 10,
    completeCheckCount = 9,
    jsonMapper = jsonMapper,
  ) {
    init {
      queueService = this@AppointmentsMigrationServiceTest.queueService
      migrationHistoryService = this@AppointmentsMigrationServiceTest.migrationHistoryService
      telemetryClient = this@AppointmentsMigrationServiceTest.telemetryClient
      auditService = this@AppointmentsMigrationServiceTest.auditService
    }
  }

  private val nomisLocationId = 12345L
  private val dpsLocationId = UUID.randomUUID()

  @Nested
  inner class MigrateAppointments {
    private val nomisApiService = mockk<NomisApiService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val migrationHistoryService = mockk<MigrationHistoryService>(relaxed = true)

    private val auditWhatParam = slot<String>()
    private val auditDetailsParam = slot<Map<*, *>>()

    val service = object : AppointmentsMigrationService(
      nomisApiService = nomisApiService,
      appointmentsService = appointmentsService,
      appointmentsMappingService = appointmentsMappingService,
      pageSize = 200,
      completeCheckDelaySeconds = 10,
      completeCheckCount = 9,
      jsonMapper = jsonMapper,
    ) {
      init {
        queueService = this@AppointmentsMigrationServiceTest.queueService
        migrationHistoryService = this@MigrateAppointments.migrationHistoryService
        telemetryClient = this@AppointmentsMigrationServiceTest.telemetryClient
        auditService = this@MigrateAppointments.auditService
      }
    }

    @BeforeEach
    fun setUp() {
      coEvery { nomisApiService.getAppointmentIds(any(), any(), any(), any(), any()) } returns
        pages(1)

      coEvery {
        auditService.sendAuditEvent(
          what = capture(auditWhatParam),
          details = capture(auditDetailsParam),
        )
      } just runs
    }

    @Test
    fun `will pass filter through to get total count along with a tiny page count`() {
      runBlocking {
        service.startMigration(
          AppointmentsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
            prisonIds = listOf("MDI"),
          ),
        )
      }

      coVerify {
        nomisApiService.getAppointmentIds(
          fromDate = LocalDate.parse("2020-01-01"),
          toDate = LocalDate.parse("2020-01-02"),
          prisonIds = listOf("MDI"),
          pageNumber = 0,
          pageSize = 1,
        )
      }
    }

    @Test
    fun `will pass appointments count and filter to queue`(): Unit = runBlocking {
      coEvery { nomisApiService.getAppointmentIds(any(), any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.startMigration(
          AppointmentsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
            prisonIds = listOf("MDI"),
          ),
        )
      }

      verify(queueService).sendMessage(
        message = eq(MIGRATE_ENTITIES),
        context = check<MigrationContext<AppointmentsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(23)
          assertThat(it.body.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(it.body.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    fun `will write migration history record`() {
      val appointmentsMigrationFilter = AppointmentsMigrationFilter(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
        prisonIds = listOf("MDI"),
      )

      coEvery { nomisApiService.getAppointmentIds(any(), any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.startMigration(
          appointmentsMigrationFilter,
        )
      }

      coVerify {
        migrationHistoryService.recordMigrationStarted(
          migrationId = any(),
          migrationType = APPOINTMENTS,
          estimatedRecordCount = 23,
          filter = coWithArg<AppointmentsMigrationFilter> {
            assertThat(it.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
            assertThat(it.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
          },
        )
      }

      with(auditDetailsParam.captured) {
        assertThat(this).extracting("migrationId").isNotNull
        assertThat(this).extracting("filter").isEqualTo(appointmentsMigrationFilter)
      }
    }

    @Test
    fun `will write analytic with estimated count and filter`() {
      coEvery { nomisApiService.getAppointmentIds(any(), any(), any(), any(), any()) } returns
        pages(23)

      runBlocking {
        service.startMigration(
          AppointmentsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
            prisonIds = listOf("MDI"),
          ),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("appointments-migration-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["fromDate"]).isEqualTo("2020-01-01")
          assertThat(it["toDate"]).isEqualTo("2020-01-02")
          assertThat(it["prisonIds"]).isEqualTo("[MDI]")
        },
        eq(null),
      )
    }

    @Test
    fun `will write analytics with empty filter`() {
      coEvery {
        nomisApiService.getAppointmentIds(
          fromDate = isNull(),
          toDate = isNull(),
          prisonIds = listOf("MDI"),
          pageNumber = any(),
          pageSize = any(),
        )
      } returns
        pages(23)

      runBlocking {
        service.startMigration(
          AppointmentsMigrationFilter(prisonIds = listOf("MDI")),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("appointments-migration-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["fromDate"]).isNull()
          assertThat(it["toDate"]).isNull()
          assertThat(it["prisonIds"]).isEqualTo("[MDI]")
        },
        eq(null),
      )
    }
  }

  @Nested
  inner class DivideAppointmentsByPage {

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(nomisApiService.getAppointmentIds(any(), any(), any(), any(), any())).thenReturn(
        pages(100_200),
      )
    }

    @Test
    fun `will send a page message for every page (200) of appointments`(): Unit = runBlocking {
      service.divideEntitiesByPage(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = AppointmentsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
            prisonIds = listOf("MDI"),
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
    fun `will also send a single MIGRATION_STATUS_CHECK message`(): Unit = runBlocking {
      service.divideEntitiesByPage(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = AppointmentsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
            prisonIds = listOf("MDI"),
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
    fun `each page with have the filter and context attached`(): Unit = runBlocking {
      service.divideEntitiesByPage(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = AppointmentsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
            prisonIds = listOf("MDI"),
          ),
        ),
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        message = eq(MIGRATE_BY_PAGE),
        context = check<MigrationContext<MigrationPage<AppointmentsMigrationFilter, *>>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.filter.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(it.body.filter.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    fun `each page will contain page number and page size`(): Unit = runBlocking {
      val context: KArgumentCaptor<MigrationContext<MigrationPage<AppointmentsMigrationFilter, *>>> = argumentCaptor()

      service.divideEntitiesByPage(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = AppointmentsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
            prisonIds = listOf("MDI"),
          ),
        ),
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        eq(MIGRATE_BY_PAGE),
        context.capture(),
        delaySeconds = eq(0),
      )
      val allContexts: List<MigrationContext<MigrationPage<AppointmentsMigrationFilter, *>>> = context.allValues

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
  inner class MigrateAppointmentsStatusCheck {
    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
    }

    @Nested
    @DisplayName("when there are still messages on the queue")
    inner class MessagesOnQueue {
      @BeforeEach
      fun setUp(): Unit = runBlocking {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(true)
      }

      @Test
      fun `will check again in 10 seconds`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = APPOINTMENTS,
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
      fun `will check again in 10 second and reset even when previously started finishing up phase`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = APPOINTMENTS,
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
      fun setUp(): Unit = runBlocking {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(0)
        whenever(appointmentsMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      fun `will increment check count and try again a second when only checked 9 times`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = APPOINTMENTS,
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
      fun `will finish off when checked 10 times previously`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = APPOINTMENTS,
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
      fun `will add completed telemetry when finishing off`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = APPOINTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("appointments-migration-completed"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("23")
            assertThat(it["durationMinutes"]).isGreaterThan("0")
          },
          eq(null),
        )
      }

      @Test
      fun `will update migration history record when finishing off`(): Unit = runBlocking {
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(2)
        whenever(appointmentsMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.migrateStatusCheck(
          MigrationContext(
            type = APPOINTMENTS,
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
  inner class CancelMigrateStatusCheck {
    @Nested
    @DisplayName("when there are still messages on the queue")
    inner class MessagesOnQueue {
      @BeforeEach
      fun setUp(): Unit = runBlocking {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(true)
      }

      @Test
      fun `will check again in 10 seconds`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = APPOINTMENTS,
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
      fun `will check again in 10 second and reset even when previously started finishing up phase`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = APPOINTMENTS,
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
      fun setUp(): Unit = runBlocking {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(0)
        whenever(appointmentsMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      fun `will increment check count and try again a second when only checked 9 times`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = APPOINTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 9),
          ),
        )

        verify(queueService).purgeAllMessages(check { assertThat(it).isEqualTo(APPOINTMENTS) })

        verify(queueService).sendMessage(
          message = eq(CANCEL_MIGRATION),
          context = check<MigrationContext<MigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(10)
          },
          delaySeconds = eq(1),
        )
      }

      @Test
      fun `will finish off when checked 10 times previously`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = APPOINTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(queueService, never()).purgeAllMessages(check { assertThat(it).isEqualTo(APPOINTMENTS) })
        verify(queueService, never()).sendMessage(
          message = eq(CANCEL_MIGRATION),
          context = any(),
          delaySeconds = any(),
        )
      }

      @Test
      fun `will add completed telemetry when finishing off`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = APPOINTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("appointments-migration-cancelled"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("23")
            assertThat(it["durationMinutes"]).isGreaterThan("0")
          },
          eq(null),
        )
      }

      @Test
      fun `will update migration history record when cancelling`(): Unit = runBlocking {
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(2)
        whenever(appointmentsMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = APPOINTMENTS,
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
  inner class MigrateAppointmentsForPage {
    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
      whenever(nomisApiService.getAppointmentIds(any(), any(), any(), any(), any())).thenReturn(
        pages(15),
      )
    }

    @Test
    fun `will pass filter through to get total count along with a tiny page count`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = AppointmentsMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
              prisonIds = listOf("MDI"),
            ),
            pageKey = ByPageNumber(13),
            pageSize = 15,
          ),
        ),
      )

      verify(nomisApiService).getAppointmentIds(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
        prisonIds = listOf("MDI"),
        pageNumber = 13,
        pageSize = 15,
      )
    }

    @Test
    fun `will send MIGRATE_ENTITY with context for each appointment`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = AppointmentsMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
              prisonIds = listOf("MDI"),
            ),
            pageKey = ByPageNumber(13),
            pageSize = 15,
          ),
        ),
      )

      verify(queueService, times(15)).sendMessageNoTracing(
        message = eq(MIGRATE_ENTITY),
        context = check<MigrationContext<AppointmentsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    fun `will send MIGRATE_ENTITY with bookingId for each appointment`(): Unit = runBlocking {
      val context: KArgumentCaptor<MigrationContext<AppointmentIdResponse>> = argumentCaptor()

      whenever(nomisApiService.getAppointmentIds(any(), any(), any(), any(), any())).thenReturn(
        pages(
          15,
          startId = 1000,
        ),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = AppointmentsMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
              prisonIds = listOf("MDI"),
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
      val allContexts: List<MigrationContext<AppointmentIdResponse>> = context.allValues

      val (firstPage, secondPage, thirdPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.eventId).isEqualTo(1000)
      assertThat(secondPage.body.eventId).isEqualTo(1001)
      assertThat(thirdPage.body.eventId).isEqualTo(1002)
      assertThat(lastPage.body.eventId).isEqualTo(1014)
    }

    @Test
    fun `will not send MIGRATE_ENTITY when cancelling`(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(true)

      whenever(nomisApiService.getAppointmentIds(any(), any(), any(), any(), any())).thenReturn(
        pages(
          15,
          startId = 1000,
        ),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = AppointmentsMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
              prisonIds = listOf("MDI"),
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
  inner class MigrateAppointment {

    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(appointmentsMappingService.findNomisMapping(any())).thenReturn(null)
      whenever(nomisApiService.getAppointment(any())).thenReturn(
        aNomisAppointmentResponse(),
      )
      whenever(appointmentsMappingService.getDpsLocation(any())).thenReturn(
        NomisDpsLocationMapping(
          nomisLocationId = nomisLocationId,
          dpsLocationId = dpsLocationId.toString(),
        ),
      )

      whenever(appointmentsService.createAppointment(any())).thenReturn(sampleAppointmentInstance(999))
      whenever(appointmentsMappingService.createMapping(any(), any())).thenReturn(CreateMappingResult<AppointmentMapping>())
    }

    @Test
    fun `will retrieve appointment from NOMIS`(): Unit = runBlocking {
      service.migrateNomisEntity(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = AppointmentIdResponse(123),
        ),
      )

      verify(nomisApiService).getAppointment(123)
    }

    @Test
    fun `will transform the NOMIS location`(): Unit = runBlocking {
      service.migrateNomisEntity(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = AppointmentIdResponse(123),
        ),
      )

      verify(appointmentsMappingService).getDpsLocation(nomisLocationId)
    }

    @Test
    fun `will transform and send that appointment to the Appointments service`(): Unit = runBlocking {
      whenever(nomisApiService.getAppointment(any())).thenReturn(aNomisAppointmentResponse())

      service.migrateNomisEntity(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = AppointmentIdResponse(123),
        ),
      )

      verify(appointmentsService).createAppointment(
        eq(
          AppointmentMigrateRequest(
            bookingId = 606,
            prisonerNumber = "G4803UT",
            prisonCode = "MDI",
            dpsLocationId = dpsLocationId,
            startDate = LocalDate.parse("2020-01-01"),
            startTime = "10:00",
            endTime = "12:00",
            comment = "a comment",
            categoryCode = "SUB",
            isCancelled = false,
            createdBy = "ITAG_USER",
            created = LocalDateTime.parse("2020-01-01T10:00"),
            updatedBy = "another user",
            updated = LocalDateTime.parse("2020-05-05T12:00"),
          ),
        ),
      )
    }

    @Test
    fun `will create a mapping between a new appointment and a NOMIS appointment`(): Unit = runBlocking {
      whenever(nomisApiService.getAppointment(any())).thenReturn(
        aNomisAppointmentResponse(),
      )
      whenever(appointmentsService.createAppointment(any())).thenReturn(sampleAppointmentInstance(999))

      service.migrateNomisEntity(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = AppointmentIdResponse(123),
        ),
      )

      verify(appointmentsMappingService).createMapping(
        AppointmentMapping(
          nomisEventId = 123,
          appointmentInstanceId = 999,
          label = "2020-05-23T11:30:00",
          mappingType = "MIGRATED",
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<AppointmentMapping>>() {},
      )
    }

    @Test
    fun `will not throw exception (and place message back on queue) but create a new retry message`(): Unit = runBlocking {
      whenever(nomisApiService.getAppointment(any())).thenReturn(aNomisAppointmentResponse())
      whenever(appointmentsService.createAppointment(any())).thenReturn(sampleAppointmentInstance(999))

      whenever(
        appointmentsMappingService.createMapping(
          any(),
          eq(object : ParameterizedTypeReference<DuplicateErrorResponse<AppointmentMapping>>() {}),
        ),
      ).thenThrow(
        RuntimeException("something went wrong"),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = APPOINTMENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = AppointmentIdResponse(123),
        ),
      )

      verify(queueService).sendMessage(
        message = eq(RETRY_MIGRATION_MAPPING),
        context = check<MigrationContext<AppointmentMapping>> {
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.nomisEventId).isEqualTo(123)
          assertThat(it.body.appointmentInstanceId).isEqualTo(999)
        },
        delaySeconds = eq(0),
      )
    }

    @Nested
    inner class WhenMigratedAlready {
      @BeforeEach
      fun setUp(): Unit = runBlocking {
        whenever(appointmentsMappingService.findNomisMapping(any())).thenReturn(
          AppointmentMapping(
            nomisEventId = 123,
            appointmentInstanceId = 999,
            mappingType = "MIGRATION",
          ),
        )
      }

      @Test
      fun `will do nothing`(): Unit = runBlocking {
        service.migrateNomisEntity(
          MigrationContext(
            type = APPOINTMENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = AppointmentIdResponse(123),
          ),
        )

        verifyNoInteractions(appointmentsService)
      }
    }
  }

  @Test
  fun `will create audit event on user cancel`() {
    runBlocking {
      whenever(migrationHistoryService.get("123-2020-01-01")).thenReturn(
        MigrationHistory(
          migrationId = "123-2020-01-01 ",
          status = MigrationStatus.CANCELLED,
          whenEnded = LocalDateTime.parse("2020-01-01T00:00:00"),
          whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
          migrationType = APPOINTMENTS,
          estimatedRecordCount = 100,
        ),
      )

      service.cancel("123-2020-01-01")

      verify(auditService).sendAuditEvent(
        eq("MIGRATION_CANCEL_REQUESTED"),
        check {
          it as Map<*, *>
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["migrationType"]).isEqualTo(APPOINTMENTS.name)
        },
      )
    }
  }
}

fun sampleAppointmentInstance(appointmentInstanceId: Long) = AppointmentInstance(
  id = appointmentInstanceId,
  appointmentSeriesId = 10,
  appointmentId = 50,
  appointmentAttendeeId = appointmentInstanceId,
  appointmentType = AppointmentInstance.AppointmentType.INDIVIDUAL,
  prisonCode = "MDI",
  prisonerNumber = "A1234AA",
  bookingId = 4567,
  categoryCode = "MEDO",
  inCell = false,
  appointmentDate = LocalDate.parse("2020-05-23"),
  startTime = "11:30",
  endTime = "12:30",
  extraInformation = "some comment",
  createdTime = LocalDateTime.parse("2023-01-01T12:00:00"),
  createdBy = "ITAG_USER",
)

fun aNomisAppointmentResponse(
  bookingId: Long = 606,
  offenderNo: String = "G4803UT",
  prisonId: String = "MDI",
  internalLocation: Long = 12345L,
  startDateTime: LocalDateTime = LocalDateTime.parse("2020-01-01T10:00:00"),
  endDateTime: LocalDateTime = LocalDateTime.parse("2020-01-01T12:00:00"),
  comment: String = "a comment",
  subtype: String = "SUB",
  status: String = "SCH",
) = AppointmentResponse(
  bookingId = bookingId,
  offenderNo = offenderNo,
  prisonId = prisonId,
  internalLocation = internalLocation,
  startDateTime = startDateTime,
  endDateTime = endDateTime,
  comment = comment,
  subtype = subtype,
  status = status,
  createdBy = "ITAG_USER",
  createdDate = LocalDateTime.parse("2020-01-01T10:00:00"),
  modifiedBy = "another user",
  modifiedDate = LocalDateTime.parse("2020-05-05T12:00:00"),
)

fun pages(total: Long, startId: Long = 1): PageImpl<AppointmentIdResponse> = PageImpl<AppointmentIdResponse>(
  (startId..total - 1 + startId).map { AppointmentIdResponse(it) },
  Pageable.ofSize(10),
  total,
)
