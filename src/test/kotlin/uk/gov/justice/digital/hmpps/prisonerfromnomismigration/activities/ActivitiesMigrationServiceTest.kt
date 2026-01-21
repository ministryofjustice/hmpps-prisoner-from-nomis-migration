package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ActivityMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NomisDpsLocationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.FindActiveActivityIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GetActivityResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PayRatesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduleRulesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatusCheck
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.pageNumber
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@JsonTest
class ActivitiesMigrationServiceTest(@Autowired private val jsonMapper: JsonMapper) {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val mappingService: ActivitiesMappingService = mock()
  private val activitiesApiService: ActivitiesApiService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val auditService: AuditService = mock()
  private val tomorrow = LocalDate.now().plusDays(1)
  val service = object : ActivitiesMigrationService(
    nomisApiService = nomisApiService,
    activitiesMappingService = mappingService,
    activitiesApiService = activitiesApiService,
    jsonMapper = jsonMapper,
    pageSize = 3L,
    completeCheckDelaySeconds = 10,
    completeCheckCount = 9,
    completeCheckScheduledRetrySeconds = 10,
  ) {
    init {
      queueService = this@ActivitiesMigrationServiceTest.queueService
      migrationHistoryService = this@ActivitiesMigrationServiceTest.migrationHistoryService
      telemetryClient = this@ActivitiesMigrationServiceTest.telemetryClient
      auditService = this@ActivitiesMigrationServiceTest.auditService
    }
  }

  @Nested
  inner class StartMigration {
    private val nomisApiService = mockk<NomisApiService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val migrationHistoryService = mockk<MigrationHistoryService>(relaxed = true)
    private val activitiesApiService = mockk<ActivitiesApiService>()

    /* coroutine version of service required for this route */
    private val service = object : ActivitiesMigrationService(
      nomisApiService = nomisApiService,
      activitiesMappingService = mappingService,
      activitiesApiService = activitiesApiService,
      jsonMapper = jsonMapper,
      pageSize = 3L,
      completeCheckDelaySeconds = 10,
      completeCheckCount = 9,
      completeCheckScheduledRetrySeconds = 0,
    ) {
      init {
        queueService = this@ActivitiesMigrationServiceTest.queueService
        migrationHistoryService = this@StartMigration.migrationHistoryService
        telemetryClient = this@ActivitiesMigrationServiceTest.telemetryClient
        auditService = this@StartMigration.auditService
      }
    }
    private val auditWhatParam = slot<String>()
    private val auditDetailsParam = slot<Map<*, *>>()

    @BeforeEach
    internal fun setUp() {
      coEvery { nomisApiService.getActivityIds(any(), any(), any(), any()) } returns
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
        service.startMigration(ActivitiesMigrationFilter(prisonId = "BXI", activityStartDate = tomorrow))
      }

      coVerify {
        nomisApiService.getActivityIds(
          prisonId = "BXI",
          pageNumber = 0,
          pageSize = 1,
        )
      }
    }

    @Test
    internal fun `will pass activity count and filter to queue`(): Unit = runBlocking {
      coEvery { nomisApiService.getActivityIds(any(), any(), any(), any()) } returns
        pages(totalEntities = 7)

      service.startMigration(ActivitiesMigrationFilter(prisonId = "BXI", activityStartDate = tomorrow))

      verify(queueService).sendMessage(
        message = eq(MigrationMessageType.MIGRATE_ENTITIES),
        context = check<MigrationContext<ActivitiesMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(7)
          assertThat(it.body.prisonId).isEqualTo("BXI")
          assertThat(it.body.activityStartDate).isEqualTo(tomorrow)
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will write migration history record`() {
      val activitiesMigrationFilter = ActivitiesMigrationFilter(prisonId = "BXI", activityStartDate = tomorrow)

      coEvery { nomisApiService.getActivityIds(any(), any(), any(), any()) } returns
        pages(totalEntities = 7)

      runBlocking {
        service.startMigration(activitiesMigrationFilter)
      }

      coVerify {
        migrationHistoryService.recordMigrationStarted(
          migrationId = any(),
          migrationType = MigrationType.ACTIVITIES,
          estimatedRecordCount = 7,
          filter = coWithArg<ActivitiesMigrationFilter> {
            assertThat(it.prisonId).isEqualTo("BXI")
            assertThat(it.activityStartDate).isEqualTo(tomorrow)
          },
        )
      }

      with(auditDetailsParam.captured) {
        assertThat(this).extracting("migrationId").isNotNull
        assertThat(this).extracting("migrationType").isEqualTo("ACTIVITIES")
        assertThat(this).extracting("filter").isEqualTo(activitiesMigrationFilter)
      }
    }

    @Test
    internal fun `will write analytic with estimated count and filter`() {
      coEvery { nomisApiService.getActivityIds(any(), any(), any(), any()) } returns pages(totalEntities = 7)

      runBlocking {
        service.startMigration(ActivitiesMigrationFilter(prisonId = "BXI", activityStartDate = tomorrow))
      }

      verify(telemetryClient).trackEvent(
        eq("activity-migration-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("7")
          assertThat(it["prisonId"]).isEqualTo("BXI")
          assertThat(it["activityStartDate"]).isEqualTo("$tomorrow")
        },
        eq(null),
      )
    }
  }

  @Nested
  inner class DivideEntitiesByPage {

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(nomisApiService.getActivityIds(any(), anyOrNull(), any(), any())).thenReturn(
        pages(totalEntities = 7, pageSize = 3),
      )
    }

    @Test
    internal fun `will send a page message for every page`(): Unit = runBlocking {
      service.divideEntitiesByPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = ActivitiesMigrationFilter(prisonId = "BXI", activityStartDate = tomorrow),
        ),
      )

      verify(queueService, times(3)).sendMessage(
        eq(MigrationMessageType.MIGRATE_BY_PAGE),
        any(),
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will also send a single MIGRATION_STATUS_CHECK message`(): Unit = runBlocking {
      service.divideEntitiesByPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = ActivitiesMigrationFilter(prisonId = "BXI", activityStartDate = tomorrow),
        ),
      )

      verify(queueService).sendMessage(
        eq(MigrationMessageType.MIGRATE_STATUS_CHECK),
        any(),
        any(),
      )
    }

    @Test
    internal fun `each page will have the filter and context attached`(): Unit = runBlocking {
      service.divideEntitiesByPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = ActivitiesMigrationFilter(prisonId = "BXI", activityStartDate = tomorrow),
        ),
      )

      verify(queueService, times(3)).sendMessage(
        message = eq(MigrationMessageType.MIGRATE_BY_PAGE),
        context = check<MigrationContext<MigrationPage<ActivitiesMigrationFilter, *>>> {
          assertThat(it.estimatedCount).isEqualTo(7)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.filter.prisonId).isEqualTo("BXI")
          assertThat(it.body.filter.activityStartDate).isEqualTo("$tomorrow")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `each page will contain page number and page size`(): Unit = runBlocking {
      val context: KArgumentCaptor<MigrationContext<MigrationPage<ActivitiesMigrationFilter, *>>> = argumentCaptor()

      service.divideEntitiesByPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = ActivitiesMigrationFilter(prisonId = "BXI", activityStartDate = tomorrow),
        ),
      )

      verify(queueService, times(3)).sendMessage(
        eq(MigrationMessageType.MIGRATE_BY_PAGE),
        context.capture(),
        delaySeconds = eq(0),
      )
      val allContexts: List<MigrationContext<MigrationPage<ActivitiesMigrationFilter, *>>> = context.allValues

      val (firstPage, secondPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.pageNumber()).isEqualTo(0)
      assertThat(firstPage.body.pageSize).isEqualTo(3)

      assertThat(secondPage.body.pageNumber()).isEqualTo(1)
      assertThat(secondPage.body.pageSize).isEqualTo(3)

      assertThat(lastPage.body.pageNumber()).isEqualTo(2)
      assertThat(lastPage.body.pageSize).isEqualTo(3)
    }
  }

  @Nested
  inner class MigrateEntitiesForPage {
    private val startDate = LocalDate.now().plusDays(2)

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
      whenever(nomisApiService.getActivityIds(any(), anyOrNull(), any(), any())).thenReturn(
        pages(totalEntities = 7, pageSize = 3),
      )
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = MigrationPage(
            filter = ActivitiesMigrationFilter(prisonId = "BXI", activityStartDate = startDate),
            pageKey = ByPageNumber(1),
            pageSize = 3,
          ),
        ),
      )

      verify(nomisApiService).getActivityIds(
        prisonId = "BXI",
        pageNumber = 1,
        pageSize = 3,
      )
    }

    @Test
    internal fun `will send PRISONER_FROM_NOMIS__MIGRATION__RW with context for each activity`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = MigrationPage(
            filter = ActivitiesMigrationFilter(prisonId = "BXI", activityStartDate = startDate),
            pageKey = ByPageNumber(1),
            pageSize = 3,
          ),
        ),
      )

      verify(queueService, times(7)).sendMessageNoTracing(
        message = eq(MigrationMessageType.MIGRATE_ENTITY),
        context = check<MigrationContext<ActivitiesMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(7)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will send PRISONER_FROM_NOMIS__MIGRATION__RW with courseActivityId and start date for each activity`(): Unit = runBlocking {
      val context: KArgumentCaptor<MigrationContext<ActivitiesMigrationRequest>> = argumentCaptor()

      whenever(nomisApiService.getActivityIds(any(), anyOrNull(), any(), any())).thenReturn(
        pages(totalEntities = 7, pageSize = 3, startId = 1000),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = MigrationPage(
            filter = ActivitiesMigrationFilter(prisonId = "BXI", activityStartDate = startDate),
            pageKey = ByPageNumber(2),
            pageSize = 3,
          ),
        ),
      )

      verify(queueService, times(7)).sendMessageNoTracing(
        eq(MigrationMessageType.MIGRATE_ENTITY),
        context.capture(),
        delaySeconds = eq(0),

      )
      val allContexts: List<MigrationContext<ActivitiesMigrationRequest>> = context.allValues

      val (firstPage, secondPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.courseActivityId).isEqualTo(1000)
      assertThat(firstPage.body.activityStartDate).isEqualTo(startDate)
      assertThat(secondPage.body.courseActivityId).isEqualTo(1001)
      assertThat(secondPage.body.activityStartDate).isEqualTo(startDate)
      assertThat(lastPage.body.courseActivityId).isEqualTo(1006)
      assertThat(lastPage.body.activityStartDate).isEqualTo(startDate)
    }

    @Test
    internal fun `will not send PRISONER_FROM_NOMIS__MIGRATION__RW when cancelling`(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(true)

      whenever(nomisApiService.getActivityIds(any(), anyOrNull(), any(), any())).thenReturn(
        pages(totalEntities = 7, pageSize = 3, startId = 1000),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = MigrationPage(
            filter = ActivitiesMigrationFilter(prisonId = "BXI", activityStartDate = startDate),
            pageKey = ByPageNumber(2),
            pageSize = 3,
          ),
        ),
      )

      verifyNoInteractions(queueService)
    }
  }

  @Nested
  inner class MigrateNomisEntity {

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)
    private val startDate = today.plusDays(2)
    private val dpsLocationId = UUID.randomUUID()
    private val dpsLocationResponse = NomisDpsLocationMapping(dpsLocationId.toString(), 123L)

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(mappingService.findNomisMapping(any())).thenReturn(null)
      whenever(mappingService.getDpsLocation(anyLong())).thenReturn(dpsLocationResponse)
      whenever(nomisApiService.getActivity(any())).thenReturn(nomisActivityResponse())

      whenever(activitiesApiService.migrateActivity(any())).thenReturn(
        ActivityMigrateResponse(
          prisonCode = "BXI",
          activityId = 456,
          splitRegimeActivityId = 789,
        ),
      )

      whenever(mappingService.createMapping(any(), any())).thenReturn(CreateMappingResult())
    }

    @Test
    internal fun `will retrieve activity from NOMIS`(): Unit = runBlocking {
      service.migrateNomisEntity(migrationContext())

      verify(nomisApiService).getActivity(123)
    }

    @Test
    internal fun `will migrate an activity to DPS`(): Unit = runBlocking {
      service.migrateNomisEntity(migrationContext())

      verify(activitiesApiService).migrateActivity(
        check {
          assertThat(it.programServiceCode).isEqualTo("SOME_PROGRAM")
          assertThat(it.prisonCode).isEqualTo("BXI")
          assertThat(it.startDate).isEqualTo(startDate)
          assertThat(it.endDate).isEqualTo(tomorrow)
          assertThat(it.capacity).isEqualTo(10)
          assertThat(it.description).isEqualTo("Some activity")
          assertThat(it.payPerSession).isEqualTo(ActivityMigrateRequest.PayPerSession.H)
          assertThat(it.runsOnBankHoliday).isEqualTo(false)
          assertThat(it.dpsLocationId).isEqualTo(dpsLocationId)
          assertThat(it.outsideWork).isEqualTo(true)
          with(it.payRates.first { it.nomisPayBand == "1" }) {
            assertThat(incentiveLevel).isEqualTo("BAS")
            assertThat(rate).isEqualTo(120)
          }
          with(it.payRates.first { it.nomisPayBand == "2" }) {
            assertThat(incentiveLevel).isEqualTo("BAS")
            assertThat(rate).isEqualTo(140)
          }
          with(it.scheduleRules[0]) {
            assertThat(startTime).isEqualTo("08:00")
            assertThat(endTime).isEqualTo("11:30")
            assertThat(monday).isTrue()
            assertThat(tuesday).isTrue()
            assertThat(wednesday).isTrue()
            assertThat(thursday).isFalse()
            assertThat(friday).isFalse()
            assertThat(saturday).isFalse()
            assertThat(sunday).isFalse()
            assertThat(timeSlot!!.value).isEqualTo("AM")
          }
          with(it.scheduleRules[1]) {
            assertThat(startTime).isEqualTo("13:00")
            assertThat(endTime).isEqualTo("16:30")
            assertThat(monday).isFalse()
            assertThat(tuesday).isFalse()
            assertThat(wednesday).isTrue()
            assertThat(thursday).isTrue()
            assertThat(friday).isTrue()
            assertThat(saturday).isTrue()
            assertThat(sunday).isTrue()
            assertThat(timeSlot!!.value).isEqualTo("PM")
          }
        },
      )
    }

    @Test
    internal fun `will ignore an activity if no schedule rules`(): Unit = runBlocking {
      service.migrateNomisEntity(migrationContext(hasScheduleRules = false))

      verify(nomisApiService, never()).getActivity(anyLong())
      verify(activitiesApiService, never()).migrateActivity(any())
    }

    @Test
    internal fun `will default capacity 1 if zero in NOMIS`(): Unit = runBlocking {
      whenever(nomisApiService.getActivity(any())).thenReturn(nomisActivityResponse(capacity = 0))

      service.migrateNomisEntity(migrationContext())

      verify(activitiesApiService).migrateActivity(
        check {
          assertThat(it.capacity).isEqualTo(1)
        },
      )
    }

    @Test
    internal fun `will take start date from activity if after requested start date`(): Unit = runBlocking {
      whenever(nomisApiService.getActivity(any())).thenReturn(nomisActivityResponse(startDate = today.plusDays(5)))

      service.migrateNomisEntity(migrationContext())

      verify(activitiesApiService).migrateActivity(
        check {
          assertThat(it.startDate).isEqualTo(today.plusDays(5))
        },
      )
    }

    @Test
    internal fun `will map rules for all slots`(): Unit = runBlocking {
      whenever(nomisApiService.getActivity(any())).thenReturn(
        nomisActivityResponse(
          scheduleRules = listOf(
            nomisScheduleRulesResponse(
              start = "08:00",
              end = "11:00",
              days = listOf("MON", "TUE", "WED", "THU"),
              slot = "AM",
            ),
            nomisScheduleRulesResponse(
              start = "13:00",
              end = "15:30",
              days = listOf("FRI"),
              slot = "PM",
            ),
            nomisScheduleRulesResponse(
              start = "19:00",
              end = "20:00",
              days = listOf("SAT", "SUN"),
              slot = "ED",
            ),
          ),
        ),
      )

      service.migrateNomisEntity(migrationContext())

      verify(activitiesApiService).migrateActivity(
        check {
          assertThat(it.scheduleRules.size).isEqualTo(3)
          with(it.scheduleRules[0]) {
            assertThat(startTime).isEqualTo("08:00")
            assertThat(endTime).isEqualTo("11:00")
            assertThat(monday).isTrue()
            assertThat(tuesday).isTrue()
            assertThat(wednesday).isTrue()
            assertThat(thursday).isTrue()
            assertThat(friday).isFalse()
            assertThat(saturday).isFalse()
            assertThat(sunday).isFalse()
            assertThat(timeSlot!!.value).isEqualTo("AM")
          }
          with(it.scheduleRules[1]) {
            assertThat(startTime).isEqualTo("13:00")
            assertThat(endTime).isEqualTo("15:30")
            assertThat(monday).isFalse()
            assertThat(tuesday).isFalse()
            assertThat(wednesday).isFalse()
            assertThat(thursday).isFalse()
            assertThat(friday).isTrue()
            assertThat(saturday).isFalse()
            assertThat(sunday).isFalse()
            assertThat(timeSlot!!.value).isEqualTo("PM")
          }
          with(it.scheduleRules[2]) {
            assertThat(startTime).isEqualTo("19:00")
            assertThat(endTime).isEqualTo("20:00")
            assertThat(monday).isFalse()
            assertThat(tuesday).isFalse()
            assertThat(wednesday).isFalse()
            assertThat(thursday).isFalse()
            assertThat(friday).isFalse()
            assertThat(saturday).isTrue()
            assertThat(sunday).isTrue()
            assertThat(timeSlot!!.value).isEqualTo("ED")
          }
        },
      )
    }

    @Test
    internal fun `will allow multiple schedule rules per slot`(): Unit = runBlocking {
      whenever(nomisApiService.getActivity(any())).thenReturn(
        nomisActivityResponse(
          scheduleRules = listOf(
            nomisScheduleRulesResponse(
              start = "08:00",
              end = "11:00",
              days = listOf("MON", "TUE", "WED", "THU"),
              slot = "AM",
            ),
            nomisScheduleRulesResponse(
              start = "09:00",
              end = "11:30",
              days = listOf("FRI"),
              slot = "AM",
            ),
            nomisScheduleRulesResponse(
              start = "09:00",
              end = "12:00",
              days = listOf("SAT", "SUN"),
              slot = "AM",
            ),
          ),
        ),
      )

      service.migrateNomisEntity(migrationContext())

      verify(activitiesApiService).migrateActivity(
        check {
          assertThat(it.scheduleRules.size).isEqualTo(3)
          with(it.scheduleRules[0]) {
            assertThat(startTime).isEqualTo("08:00")
            assertThat(endTime).isEqualTo("11:00")
            assertThat(monday).isTrue()
            assertThat(tuesday).isTrue()
            assertThat(wednesday).isTrue()
            assertThat(thursday).isTrue()
            assertThat(friday).isFalse()
            assertThat(saturday).isFalse()
            assertThat(sunday).isFalse()
            assertThat(timeSlot!!.value).isEqualTo("AM")
          }
          with(it.scheduleRules[1]) {
            assertThat(startTime).isEqualTo("09:00")
            assertThat(endTime).isEqualTo("11:30")
            assertThat(monday).isFalse()
            assertThat(tuesday).isFalse()
            assertThat(wednesday).isFalse()
            assertThat(thursday).isFalse()
            assertThat(friday).isTrue()
            assertThat(saturday).isFalse()
            assertThat(sunday).isFalse()
            assertThat(timeSlot!!.value).isEqualTo("AM")
          }
          with(it.scheduleRules[2]) {
            assertThat(startTime).isEqualTo("09:00")
            assertThat(endTime).isEqualTo("12:00")
            assertThat(monday).isFalse()
            assertThat(tuesday).isFalse()
            assertThat(wednesday).isFalse()
            assertThat(thursday).isFalse()
            assertThat(friday).isFalse()
            assertThat(saturday).isTrue()
            assertThat(sunday).isTrue()
            assertThat(timeSlot!!.value).isEqualTo("AM")
          }
        },
      )
    }

    @Test
    internal fun `will remove duplicate schedule rules`(): Unit = runBlocking {
      whenever(nomisApiService.getActivity(any())).thenReturn(
        nomisActivityResponse(
          scheduleRules = listOf(
            nomisScheduleRulesResponse(
              start = "13:00",
              end = "15:30",
              days = listOf("SAT", "SUN"),
              slot = "PM",
            ),
            nomisScheduleRulesResponse(
              start = "13:00",
              end = "15:30",
              days = listOf("SAT", "SUN"),
              slot = "PM",
            ),
          ),
        ),
      )

      service.migrateNomisEntity(migrationContext())

      verify(activitiesApiService).migrateActivity(
        check {
          assertThat(it.scheduleRules.size).isEqualTo(1)
          with(it.scheduleRules[0]) {
            assertThat(startTime).isEqualTo("13:00")
            assertThat(endTime).isEqualTo("15:30")
            assertThat(monday).isFalse()
            assertThat(tuesday).isFalse()
            assertThat(wednesday).isFalse()
            assertThat(thursday).isFalse()
            assertThat(friday).isFalse()
            assertThat(saturday).isTrue()
            assertThat(sunday).isTrue()
            assertThat(timeSlot!!.value).isEqualTo("PM")
          }
        },
      )
    }

    @Test
    internal fun `will throw after an error checking the mapping service so the message is rejected and retried`(): Unit = runBlocking {
      whenever(mappingService.findNomisMapping(any())).thenThrow(WebClientResponseException.BadGateway::class.java)

      assertThrows<WebClientResponseException.BadGateway> {
        service.migrateNomisEntity(migrationContext())
      }

      verifyNoInteractions(queueService)
    }

    @Test
    internal fun `will throw after an error getting the DPS location ID so the message is rejected and retried`(): Unit = runBlocking {
      whenever(mappingService.getDpsLocation(anyLong())).thenThrow(WebClientResponseException.create(HttpStatus.NOT_FOUND, "404 Not Found", HttpHeaders.EMPTY, ByteArray(0), null, null))

      assertThrows<WebClientResponseException.NotFound> {
        service.migrateNomisEntity(migrationContext())
      }

      verifyNoInteractions(queueService)
    }

    @Test
    internal fun `will throw after an error retrieving the Nomis entity so the message is rejected and retried`(): Unit = runBlocking {
      whenever(nomisApiService.getActivity(any())).thenThrow(WebClientResponseException.create(HttpStatus.BAD_GATEWAY, "error", HttpHeaders.EMPTY, ByteArray(0), null, null))

      assertThrows<WebClientResponseException.BadGateway> {
        service.migrateNomisEntity(migrationContext())
      }

      verifyNoInteractions(queueService)
      verify(telemetryClient).trackEvent(
        eq("activity-migration-entity-failed"),
        check<Map<String, String>> {
          assertThat(it["nomisCourseActivityId"]).isEqualTo("123")
          assertThat(it["reason"]).contains("BadGateway")
          assertThat(it["migrationId"]).contains("2020-05-23T11:30:00")
        },
        isNull(),
      )
    }

    @Test
    internal fun `will throw after an error creating the Activities entity so the message is rejected and retried`(): Unit = runBlocking {
      whenever(activitiesApiService.migrateActivity(any())).thenThrow(WebClientResponseException.create(HttpStatus.BAD_GATEWAY, "error", HttpHeaders.EMPTY, ByteArray(0), null, null))

      assertThrows<WebClientResponseException.BadGateway> {
        service.migrateNomisEntity(migrationContext())
      }

      verifyNoInteractions(queueService)
      verify(telemetryClient).trackEvent(
        eq("activity-migration-entity-failed"),
        check<Map<String, String>> {
          assertThat(it["nomisCourseActivityId"]).isEqualTo("123")
          assertThat(it["reason"]).contains("BadGateway")
          assertThat(it["migrationId"]).contains("2020-05-23T11:30:00")
        },
        isNull(),
      )
    }

    @Test
    internal fun `will NOT throw but will publish a retry mapping message after an error creating the new mapping`(): Unit = runBlocking {
      whenever(mappingService.createMapping(any(), any())).thenThrow(WebClientResponseException(HttpStatus.BAD_GATEWAY, "error", null, null, null, null))

      assertDoesNotThrow {
        service.migrateNomisEntity(migrationContext())
      }

      verify(queueService).sendMessage(
        message = eq(MigrationMessageType.RETRY_MIGRATION_MAPPING),
        context = check<MigrationContext<ActivityMigrationMappingDto>> {
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.nomisCourseActivityId).isEqualTo(123)
          assertThat(it.body.activityId).isEqualTo(456)
          assertThat(it.body.activityId2).isEqualTo(789)
        },
        delaySeconds = eq(0),
      )
      verify(telemetryClient, never()).trackEvent(eq("activity-migration-entity-failed"), any(), isNull())
    }

    @Test
    fun `will do nothing when already migrated`() = runBlocking {
      whenever(mappingService.findNomisMapping(any()))
        .thenReturn(
          ActivityMigrationMappingDto(
            nomisCourseActivityId = 123L,
            activityId = 456,
            activityId2 = 789,
            label = "An old migration",
          ),
        )

      service.migrateNomisEntity(migrationContext())

      verifyNoInteractions(activitiesApiService)
      verify(telemetryClient).trackEvent(
        eq("activity-migration-entity-ignored"),
        check<Map<String, String>> {
          assertThat(it["nomisCourseActivityId"]).isEqualTo("123")
          assertThat(it["migrationId"]).isEqualTo("2020-05-23T11:30:00")
        },
        isNull(),
      )
    }

    @Test
    fun `will publish telemetry when migration successful`() = runBlocking {
      service.migrateNomisEntity(migrationContext())

      verify(telemetryClient).trackEvent(
        eq("activity-migration-entity-migrated"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "nomisCourseActivityId" to "123",
              "dpsActivityId" to "456",
              "dpsActivityId2" to "789",
              "migrationId" to "2020-05-23T11:30:00",
            ),
          )
        },
        isNull(),
      )
    }

    @Test
    fun `will publish ignored telemetry if no schedule rules`() = runBlocking {
      service.migrateNomisEntity(migrationContext(hasScheduleRules = false))

      verify(telemetryClient).trackEvent(
        eq("activity-migration-entity-ignored"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "nomisCourseActivityId" to "123",
              "dpsActivityId" to null,
              "dpsActivityId2" to null,
              "migrationId" to "2020-05-23T11:30:00",
              "reason" to "No schedule rules exist for activity",
            ),
          )
        },
        isNull(),
      )
    }

    private fun nomisActivityResponse(
      scheduleRules: List<ScheduleRulesResponse> = listOf(
        nomisScheduleRulesResponse(
          start = "08:00",
          end = "11:30",
          days = listOf("MON", "TUE", "WED"),
          slot = "AM",
        ),
        nomisScheduleRulesResponse(
          start = "13:00",
          end = "16:30",
          days = listOf("WED", "THU", "FRI", "SAT", "SUN"),
          slot = "PM",
        ),
      ),
      capacity: Int = 10,
      startDate: LocalDate = yesterday,
    ) = GetActivityResponse(
      courseActivityId = 123,
      programCode = "SOME_PROGRAM",
      prisonId = "BXI",
      startDate = startDate,
      endDate = tomorrow,
      capacity = capacity,
      description = "Some activity",
      payPerSession = "H",
      minimumIncentiveLevel = "BAS",
      excludeBankHolidays = true,
      internalLocationDescription = "BXI-A-1-01",
      internalLocationCode = "CELL-01",
      internalLocationId = 123,
      outsideWork = true,
      scheduleRules = scheduleRules,
      payRates = listOf(
        PayRatesResponse(
          incentiveLevelCode = "BAS",
          rate = BigDecimal.valueOf(1.20),
          payBand = "1",
        ),
        PayRatesResponse(
          incentiveLevelCode = "BAS",
          rate = BigDecimal.valueOf(1.40),
          payBand = "2",
        ),
      ),
    )

    private fun nomisScheduleRulesResponse(start: String, end: String, slot: String, days: List<String>) = ScheduleRulesResponse(
      startTime = start,
      endTime = end,
      monday = "MON" in days,
      tuesday = "TUE" in days,
      wednesday = "WED" in days,
      thursday = "THU" in days,
      friday = "FRI" in days,
      saturday = "SAT" in days,
      sunday = "SUN" in days,
      slotCategoryCode = slot,
    )

    private fun migrationContext(hasScheduleRules: Boolean = true) = MigrationContext(
      type = MigrationType.ACTIVITIES,
      migrationId = "2020-05-23T11:30:00",
      estimatedCount = 7,
      body = ActivitiesMigrationRequest(123, startDate, hasScheduleRules),
    )
  }

  @Nested
  inner class MigrateStatusCheck {
    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
    }

    @Nested
    inner class MessagesOnQueue {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(true)
      }

      @Test
      internal fun `will check again in 10 seconds`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = MigrationType.ACTIVITIES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(),
          ),
        )

        verify(queueService).sendMessage(
          eq(MigrationMessageType.MIGRATE_STATUS_CHECK),
          any(),
          eq(10),
        )
      }

      @Test
      internal fun `will check again in 10 second and reset even when previously started finishing up phase`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = MigrationType.ACTIVITIES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 4),
          ),
        )

        verify(queueService).sendMessage(
          message = eq(MigrationMessageType.MIGRATE_STATUS_CHECK),
          context = check<MigrationContext<MigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(0)
          },
          delaySeconds = eq(10),
        )
      }
    }

    @Nested
    inner class NoMessagesOnQueue {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(0)
        whenever(mappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = MigrationType.ACTIVITIES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 9),
          ),
        )

        verify(queueService).sendMessage(
          message = eq(MigrationMessageType.MIGRATE_STATUS_CHECK),
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
            type = MigrationType.ACTIVITIES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(queueService, never()).sendMessage(
          message = eq(MigrationMessageType.MIGRATE_STATUS_CHECK),
          context = any(),
          delaySeconds = any(),
        )
      }

      @Test
      internal fun `will add completed telemetry when finishing off`(): Unit = runBlocking {
        service.migrateStatusCheck(
          MigrationContext(
            type = MigrationType.ACTIVITIES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("activity-migration-completed"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("7")
            assertThat(it["durationMinutes"]).isNotNull()
          },
          eq(null),
        )
      }

      @Test
      internal fun `will update migration history record when finishing off`(): Unit = runBlocking {
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(2)
        whenever(mappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(5)

        service.migrateStatusCheck(
          MigrationContext(
            type = MigrationType.ACTIVITIES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(migrationHistoryService).recordMigrationCompleted(
          migrationId = eq("2020-05-23T11:30:00"),
          recordsFailed = eq(2),
          recordsMigrated = eq(5),
        )
      }
    }
  }

  @Nested
  inner class CancelMigrationStatusCheck {
    @Nested
    inner class MessagesOnQueue {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(true)
      }

      @Test
      internal fun `will check again in 10 seconds`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = MigrationType.ACTIVITIES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(),
          ),
        )

        verify(queueService).purgeAllMessages(any())
        verify(queueService).sendMessage(
          eq(MigrationMessageType.CANCEL_MIGRATION),
          any(),
          eq(10),
        )
      }

      @Test
      internal fun `will check again in 10 second and reset even when previously started finishing up phase`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = MigrationType.ACTIVITIES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 4),
          ),
        )

        verify(queueService).purgeAllMessages(any())
        verify(queueService).sendMessage(
          message = eq(MigrationMessageType.CANCEL_MIGRATION),
          context = check<MigrationContext<MigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(0)
          },
          delaySeconds = eq(10),
        )
      }
    }

    @Nested
    inner class NoMessagesOnQueue {
      @BeforeEach
      internal fun setUp(): Unit = runBlocking {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(0)
        whenever(mappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = MigrationType.ACTIVITIES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 9),
          ),
        )

        verify(queueService).purgeAllMessages(check { assertThat(it).isEqualTo(MigrationType.ACTIVITIES) })

        verify(queueService).sendMessage(
          message = eq(MigrationMessageType.CANCEL_MIGRATION),
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
            type = MigrationType.ACTIVITIES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(queueService, never()).purgeAllMessages(check { assertThat(it).isEqualTo(MigrationType.ACTIVITIES) })
        verify(queueService, never()).sendMessage(
          message = eq(MigrationMessageType.CANCEL_MIGRATION),
          context = any(),
          delaySeconds = any(),
        )
      }

      @Test
      internal fun `will add completed telemetry when finishing off`(): Unit = runBlocking {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = MigrationType.ACTIVITIES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("activity-migration-cancelled"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("7")
            assertThat(it["durationMinutes"]).isNotNull()
          },
          eq(null),
        )
      }

      @Test
      internal fun `will update migration history record when cancelling`(): Unit = runBlocking {
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(2)
        whenever(mappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(5)

        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = MigrationType.ACTIVITIES,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(migrationHistoryService).recordMigrationCancelled(
          migrationId = eq("2020-05-23T11:30:00"),
          recordsFailed = eq(2),
          recordsMigrated = eq(5),
        )
      }
    }
  }
}

private fun pages(totalEntities: Int, pageSize: Int = 3, startId: Long = 1): PageImpl<FindActiveActivityIdsResponse> = PageImpl<FindActiveActivityIdsResponse>(
  (startId..totalEntities - 1 + startId).map { FindActiveActivityIdsResponse(it, true) },
  Pageable.ofSize(pageSize),
  totalEntities.toLong(),
)
