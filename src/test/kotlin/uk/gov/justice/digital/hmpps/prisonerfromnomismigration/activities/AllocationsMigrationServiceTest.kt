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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AllocationMigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.DaysOfWeek.MONDAY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.DaysOfWeek.SATURDAY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.DaysOfWeek.SUNDAY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.DaysOfWeek.TUESDAY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.DaysOfWeek.WEDNESDAY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ActivityMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AllocationMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AllocationExclusion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AllocationExclusion.Day.MON
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AllocationExclusion.Day.SAT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AllocationExclusion.Day.SUN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AllocationExclusion.Day.TUE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AllocationExclusion.Day.WED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AllocationExclusion.Slot.AM
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AllocationExclusion.Slot.ED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AllocationExclusion.Slot.PM
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.FindActiveAllocationIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GetAllocationResponse
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
import java.time.LocalDate

@JsonTest
class AllocationsMigrationServiceTest(@Autowired private val jsonMapper: JsonMapper) {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val mappingService: AllocationsMappingService = mock()
  private val activityMappingService: ActivitiesMappingService = mock()
  private val activitiesApiService: ActivitiesApiService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val auditService: AuditService = mock()
  val service = object : AllocationsMigrationService(
    nomisApiService = nomisApiService,
    allocationsMappingService = mappingService,
    activityMappingService = activityMappingService,
    activitiesApiService = activitiesApiService,
    pageSize = 3L,
    completeCheckDelaySeconds = 10,
    completeCheckCount = 9,
    completeCheckScheduledRetrySeconds = 10,
    jsonMapper = jsonMapper,
  ) {
    init {
      queueService = this@AllocationsMigrationServiceTest.queueService
      migrationHistoryService = this@AllocationsMigrationServiceTest.migrationHistoryService
      telemetryClient = this@AllocationsMigrationServiceTest.telemetryClient
      auditService = this@AllocationsMigrationServiceTest.auditService
    }
  }

  @Nested
  inner class StartMigration {
    private val nomisApiService = mockk<NomisApiService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val migrationHistoryService = mockk<MigrationHistoryService>(relaxed = true)
    private val activitiesApiService = mockk<ActivitiesApiService>()

    /* coroutine version of service required for this route */
    private val service = object : AllocationsMigrationService(
      nomisApiService = nomisApiService,
      allocationsMappingService = mappingService,
      activityMappingService = activityMappingService,
      activitiesApiService = activitiesApiService,
      pageSize = 3L,
      completeCheckDelaySeconds = 10,
      completeCheckCount = 9,
      completeCheckScheduledRetrySeconds = 0,
      jsonMapper = jsonMapper,
    ) {
      init {
        queueService = this@AllocationsMigrationServiceTest.queueService
        migrationHistoryService = this@StartMigration.migrationHistoryService
        telemetryClient = this@AllocationsMigrationServiceTest.telemetryClient
        auditService = this@StartMigration.auditService
      }
    }
    private val auditWhatParam = slot<String>()
    private val auditDetailsParam = slot<Map<*, *>>()

    @BeforeEach
    internal fun setUp() {
      coEvery { nomisApiService.getAllocationIds(any(), any(), any(), any(), any()) } returns
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
        service.startMigration(AllocationsMigrationFilter(prisonId = "BXI", activityStartDate = LocalDate.now().plusDays(2)))
      }

      coVerify {
        nomisApiService.getAllocationIds(
          prisonId = "BXI",
          pageNumber = 0,
          pageSize = 1,
          activeOnDate = LocalDate.now().plusDays(2),
        )
      }
    }

    @Test
    internal fun `will pass allocation count and filter to queue`(): Unit = runBlocking {
      coEvery { nomisApiService.getAllocationIds(any(), any(), any(), any(), any()) } returns
        pages(totalEntities = 7)

      service.startMigration(AllocationsMigrationFilter(prisonId = "BXI"))

      verify(queueService).sendMessage(
        message = eq(MigrationMessageType.MIGRATE_ENTITIES),
        context = check<MigrationContext<AllocationsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(7)
          assertThat(it.body.prisonId).isEqualTo("BXI")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will write migration history record`() {
      val allocationsMigrationFilter = AllocationsMigrationFilter(prisonId = "BXI")

      coEvery { nomisApiService.getAllocationIds(any(), any(), any(), any(), any()) } returns
        pages(totalEntities = 7)

      runBlocking {
        service.startMigration(allocationsMigrationFilter)
      }

      coVerify {
        migrationHistoryService.recordMigrationStarted(
          migrationId = any(),
          migrationType = MigrationType.ALLOCATIONS,
          estimatedRecordCount = 7,
          filter = coWithArg<AllocationsMigrationFilter> {
            assertThat(it.prisonId).isEqualTo("BXI")
          },
        )
      }

      with(auditDetailsParam.captured) {
        assertThat(this).extracting("migrationId").isNotNull
        assertThat(this).extracting("migrationType").isEqualTo("ALLOCATIONS")
        assertThat(this).extracting("filter").isEqualTo(allocationsMigrationFilter)
      }
    }

    @Test
    internal fun `will write analytic with estimated count and filter`() {
      coEvery { nomisApiService.getAllocationIds(any(), any(), any(), any(), any()) } returns pages(totalEntities = 7)

      runBlocking {
        service.startMigration(AllocationsMigrationFilter(prisonId = "BXI"))
      }

      verify(telemetryClient).trackEvent(
        eq("activity-allocation-migration-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("7")
          assertThat(it["prisonId"]).isEqualTo("BXI")
        },
        eq(null),
      )
    }
  }

  @Nested
  inner class DivideEntitiesByPage {

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(nomisApiService.getAllocationIds(any(), anyOrNull(), any(), any(), any())).thenReturn(
        pages(totalEntities = 7, pageSize = 3),
      )
    }

    @Test
    internal fun `will send a page message for every page`(): Unit = runBlocking {
      service.divideEntitiesByPage(
        MigrationContext(
          type = MigrationType.ALLOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = AllocationsMigrationFilter(prisonId = "BXI"),
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
          type = MigrationType.ALLOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = AllocationsMigrationFilter(prisonId = "BXI"),
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
          type = MigrationType.ALLOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = AllocationsMigrationFilter(prisonId = "BXI"),
        ),
      )

      verify(queueService, times(3)).sendMessage(
        message = eq(MigrationMessageType.MIGRATE_BY_PAGE),
        context = check<MigrationContext<MigrationPage<AllocationsMigrationFilter, *>>> {
          assertThat(it.estimatedCount).isEqualTo(7)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.filter.prisonId).isEqualTo("BXI")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `each page will contain page number and page size`(): Unit = runBlocking {
      val context: KArgumentCaptor<MigrationContext<MigrationPage<AllocationsMigrationFilter, *>>> = argumentCaptor()

      service.divideEntitiesByPage(
        MigrationContext(
          type = MigrationType.ALLOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = AllocationsMigrationFilter(prisonId = "BXI"),
        ),
      )

      verify(queueService, times(3)).sendMessage(
        eq(MigrationMessageType.MIGRATE_BY_PAGE),
        context.capture(),
        delaySeconds = eq(0),
      )
      val allContexts: List<MigrationContext<MigrationPage<AllocationsMigrationFilter, *>>> = context.allValues

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
    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
      whenever(nomisApiService.getAllocationIds(any(), anyOrNull(), any(), any(), any())).thenReturn(
        pages(totalEntities = 7, pageSize = 3),
      )
    }

    @Test
    internal fun `will pass filter through to get allocations`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = MigrationType.ALLOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = MigrationPage(
            filter = AllocationsMigrationFilter(prisonId = "BXI", activityStartDate = LocalDate.now().plusDays(2)),
            pageKey = ByPageNumber(1),
            pageSize = 3,
          ),
        ),
      )

      verify(nomisApiService).getAllocationIds(
        prisonId = "BXI",
        activeOnDate = LocalDate.now().plusDays(2),
        pageNumber = 1,
        pageSize = 3,
      )
    }

    @Test
    internal fun `will send MIGRATE_ALLOCATIONS with context for each allocation`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = MigrationType.ALLOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = MigrationPage(
            filter = AllocationsMigrationFilter(prisonId = "BXI"),
            pageKey = ByPageNumber(1),
            pageSize = 3,
          ),
        ),
      )

      verify(queueService, times(7)).sendMessageNoTracing(
        message = eq(MigrationMessageType.MIGRATE_ENTITY),
        context = check<MigrationContext<AllocationsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(7)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will send MIGRATE_ALLOCATIONS with allocationId for each allocation`(): Unit = runBlocking {
      val context: KArgumentCaptor<MigrationContext<FindActiveAllocationIdsResponse>> = argumentCaptor()

      whenever(nomisApiService.getAllocationIds(any(), anyOrNull(), any(), any(), any())).thenReturn(
        pages(totalEntities = 7, pageSize = 3, startId = 1000),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = MigrationType.ALLOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = MigrationPage(
            filter = AllocationsMigrationFilter(
              prisonId = "BXI",
            ),
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
      val allContexts: List<MigrationContext<FindActiveAllocationIdsResponse>> = context.allValues

      val (firstPage, secondPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.allocationId).isEqualTo(1000)
      assertThat(secondPage.body.allocationId).isEqualTo(1001)
      assertThat(lastPage.body.allocationId).isEqualTo(1006)
    }

    @Test
    internal fun `will not send MIGRATE_ALLOCATIONS when cancelling`(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(true)

      whenever(nomisApiService.getAllocationIds(any(), anyOrNull(), any(), any(), any())).thenReturn(
        pages(totalEntities = 7, pageSize = 3, startId = 1000),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = MigrationType.ALLOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = MigrationPage(
            filter = AllocationsMigrationFilter(
              prisonId = "BXI",
            ),
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

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(mappingService.findNomisMapping(any())).thenReturn(null)
      whenever(nomisApiService.getAllocation(any())).thenReturn(
        GetAllocationResponse(
          prisonId = "BXI",
          courseActivityId = 123,
          nomisId = "A1234AA",
          bookingId = 4321,
          startDate = yesterday,
          endDate = tomorrow,
          suspended = false,
          endComment = "Ended",
          endReasonCode = "WDRAWN",
          payBand = "1",
          livingUnitDescription = "BXI-A-1-01",
          exclusions = listOf(
            AllocationExclusion(day = MON, slot = AM),
            AllocationExclusion(day = TUE, slot = null),
          ),
          scheduleRules = listOf(
            ScheduleRulesResponse("10:00", "12:00", true, true, true, true, true, false, false, slotCategoryCode = "AM"),
          ),
          activityStartDate = today,
        ),

      )

      whenever(activityMappingService.findNomisMapping(any())).thenReturn(
        ActivityMigrationMappingDto(
          nomisCourseActivityId = 123,
          activityId = 789,
          label = "activity mapping",
          activityId2 = 890,
        ),
      )

      whenever(activitiesApiService.migrateAllocation(any())).thenReturn(
        AllocationMigrateResponse(allocationId = 456, activityId = 789),
      )

      whenever(mappingService.createMapping(any(), any())).thenReturn(CreateMappingResult())
    }

    @Test
    internal fun `will retrieve allocation from NOMIS`(): Unit = runBlocking {
      service.migrateNomisEntity(
        MigrationContext(
          type = MigrationType.ALLOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = FindActiveAllocationIdsResponse(123),
        ),
      )

      verify(nomisApiService).getAllocation(123)
    }

    @Test
    internal fun `will migrate an allocation to DPS`(): Unit = runBlocking {
      service.migrateNomisEntity(
        MigrationContext(
          type = MigrationType.ALLOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = FindActiveAllocationIdsResponse(123),
        ),
      )

      verify(activitiesApiService).migrateAllocation(
        check {
          assertThat(it.prisonCode).isEqualTo("BXI")
          assertThat(it.activityId).isEqualTo(789)
          assertThat(it.splitRegimeActivityId).isEqualTo(890)
          // This must be activity start date if after allocation start date
          assertThat(it.startDate).isEqualTo(today)
          assertThat(it.endDate).isEqualTo(tomorrow)
          assertThat(it.prisonerNumber).isEqualTo("A1234AA")
          assertThat(it.bookingId).isEqualTo(4321)
          assertThat(it.suspendedFlag).isEqualTo(false)
          assertThat(it.cellLocation).isEqualTo("BXI-A-1-01")
          assertThat(it.nomisPayBand).isEqualTo("1")
          assertThat(it.endComment).isEqualTo("Ended")
          assertThat(it.exclusions).containsExactlyInAnyOrder(
            aSlot(timeSlot = Slot.TimeSlot.AM, monday = true, tuesday = true, daysOfWeek = setOf(MONDAY, TUESDAY)),
          )
        },
      )
    }

    @Test
    internal fun `will throw after an error checking the mapping service so the message is rejected and retried`(): Unit = runBlocking {
      whenever(mappingService.findNomisMapping(any())).thenThrow(WebClientResponseException.create(HttpStatus.BAD_GATEWAY, "error", HttpHeaders.EMPTY, ByteArray(0), null, null))

      assertThrows<WebClientResponseException.BadGateway> {
        service.migrateNomisEntity(
          MigrationContext(
            type = MigrationType.ALLOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = FindActiveAllocationIdsResponse(123),
          ),
        )
      }

      verifyNoInteractions(queueService)
    }

    @Test
    internal fun `will throw after an error retrieving the Nomis entity so the message is rejected and retried`(): Unit = runBlocking {
      whenever(nomisApiService.getAllocation(any())).thenThrow(WebClientResponseException.create(HttpStatus.BAD_GATEWAY, "error", HttpHeaders.EMPTY, ByteArray(0), null, null))

      assertThrows<WebClientResponseException.BadGateway> {
        service.migrateNomisEntity(
          MigrationContext(
            type = MigrationType.ALLOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = FindActiveAllocationIdsResponse(123),
          ),
        )
      }

      verifyNoInteractions(queueService)
      verify(telemetryClient).trackEvent(
        eq("activity-allocation-migration-entity-failed"),
        check<Map<String, String>> {
          assertThat(it["nomisAllocationId"]).isEqualTo("123")
          assertThat(it["reason"]).contains("BadGateway")
          assertThat(it["migrationId"]).isEqualTo("2020-05-23T11:30:00")
        },
        isNull(),
      )
    }

    @Test
    internal fun `will throw after an error retrieving the activity mapping so the message is rejected and retried`(): Unit = runBlocking {
      whenever(activityMappingService.findNomisMapping(any())).thenThrow(WebClientResponseException.create(HttpStatus.NOT_FOUND, "error", HttpHeaders.EMPTY, ByteArray(0), null, null))

      assertThrows<WebClientResponseException.NotFound> {
        service.migrateNomisEntity(
          MigrationContext(
            type = MigrationType.ALLOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = FindActiveAllocationIdsResponse(123),
          ),
        )
      }

      verifyNoInteractions(queueService)
      verify(telemetryClient).trackEvent(
        eq("activity-allocation-migration-entity-failed"),
        check<Map<String, String>> {
          assertThat(it["nomisAllocationId"]).isEqualTo("123")
          assertThat(it["reason"]).contains("NotFound")
          assertThat(it["migrationId"]).contains("2020-05-23T11:30:00")
        },
        isNull(),
      )
    }

    @Test
    internal fun `will throw after an error creating the allocations entity so the message is rejected and retried`(): Unit = runBlocking {
      whenever(activitiesApiService.migrateAllocation(any())).thenThrow(WebClientResponseException.create(HttpStatus.BAD_GATEWAY, "error", HttpHeaders.EMPTY, ByteArray(0), null, null))

      assertThrows<WebClientResponseException.BadGateway> {
        service.migrateNomisEntity(
          MigrationContext(
            type = MigrationType.ALLOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = FindActiveAllocationIdsResponse(123),
          ),
        )
      }

      verifyNoInteractions(queueService)
    }

    @Test
    internal fun `will NOT throw but will publish a retry mapping message after an error creating the new mapping`(): Unit = runBlocking {
      whenever(mappingService.createMapping(any(), any())).thenThrow(WebClientResponseException(HttpStatus.BAD_GATEWAY, "error", null, null, null, null))

      assertDoesNotThrow {
        service.migrateNomisEntity(
          MigrationContext(
            type = MigrationType.ALLOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = FindActiveAllocationIdsResponse(123),
          ),
        )
      }

      verify(queueService).sendMessage(
        message = eq(MigrationMessageType.RETRY_MIGRATION_MAPPING),
        context = check<MigrationContext<AllocationMigrationMappingDto>> {
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.nomisAllocationId).isEqualTo(123)
          assertThat(it.body.activityAllocationId).isEqualTo(456)
          assertThat(it.body.activityId).isEqualTo(789)
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    fun `will do nothing when already migrated`() = runBlocking {
      whenever(mappingService.findNomisMapping(any()))
        .thenReturn(
          AllocationMigrationMappingDto(
            nomisAllocationId = 123L,
            activityAllocationId = 456,
            activityId = 456,
            label = "An old migration",
          ),
        )

      service.migrateNomisEntity(
        MigrationContext(
          type = MigrationType.ALLOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = FindActiveAllocationIdsResponse(123),
        ),
      )

      verifyNoInteractions(activitiesApiService)
      verify(telemetryClient).trackEvent(
        eq("activity-allocation-migration-entity-ignored"),
        check<Map<String, String>> {
          assertThat(it["nomisAllocationId"]).isEqualTo("123")
          assertThat(it["migrationId"]).isEqualTo("2020-05-23T11:30:00")
        },
        isNull(),
      )
    }

    @Test
    fun `will publish telemetry when migration successful`() = runBlocking {
      service.migrateNomisEntity(
        MigrationContext(
          type = MigrationType.ALLOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = FindActiveAllocationIdsResponse(123),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("activity-allocation-migration-entity-migrated"),
        check<Map<String, String>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "nomisAllocationId" to "123",
              "dpsAllocationId" to "456",
              "dpsActivityId" to "789",
              "migrationId" to "2020-05-23T11:30:00",
            ),
          )
        },
        isNull(),
      )
    }

    private fun aSlot(timeSlot: Slot.TimeSlot, monday: Boolean = false, tuesday: Boolean = false, daysOfWeek: Set<Slot.DaysOfWeek> = setOf()) = Slot(
      weekNumber = 1,
      timeSlot = timeSlot,
      monday = monday,
      tuesday = tuesday,
      wednesday = false,
      thursday = false,
      friday = false,
      saturday = false,
      sunday = false,
      daysOfWeek = daysOfWeek,
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
            type = MigrationType.ALLOCATIONS,
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
            type = MigrationType.ALLOCATIONS,
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
            type = MigrationType.ALLOCATIONS,
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
            type = MigrationType.ALLOCATIONS,
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
            type = MigrationType.ALLOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("activity-allocation-migration-completed"),
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
            type = MigrationType.ALLOCATIONS,
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
            type = MigrationType.ALLOCATIONS,
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
            type = MigrationType.ALLOCATIONS,
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
            type = MigrationType.ALLOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 9),
          ),
        )

        verify(queueService).purgeAllMessages(check { assertThat(it).isEqualTo(MigrationType.ALLOCATIONS) })

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
            type = MigrationType.ALLOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(queueService, never()).purgeAllMessages(check { assertThat(it).isEqualTo(MigrationType.ALLOCATIONS) })
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
            type = MigrationType.ALLOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("activity-allocation-migration-cancelled"),
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
            type = MigrationType.ALLOCATIONS,
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

  @Nested
  inner class ToSlots {
    @Test
    fun `should return empty list when empty list`() {
      assertThat(emptyList<AllocationExclusion>().toDpsExclusions(listOf())).isEmpty()
    }

    @Test
    fun `should return empty list if no schedule rules`() {
      val exclusions = listOf(AllocationExclusion(MON, AM))

      assertThat(exclusions.toDpsExclusions(listOf())).isEmpty()
    }

    @Test
    fun `should map a single slot`() {
      val exclusions = listOf(AllocationExclusion(MON, AM))
      val scheduleRules = listOf(aScheduleRule("AM", listOf("MON")))

      assertThat(exclusions.toDpsExclusions(scheduleRules)).containsExactly(
        Slot(
          weekNumber = 1,
          timeSlot = Slot.TimeSlot.AM,
          monday = true,
          tuesday = false,
          wednesday = false,
          thursday = false,
          friday = false,
          saturday = false,
          sunday = false,
          daysOfWeek = setOf(MONDAY),
        ),
      )
    }

    @Test
    fun `should map a multiple slots`() {
      val exclusions = listOf(
        AllocationExclusion(MON, AM),
        AllocationExclusion(TUE, AM),
        AllocationExclusion(WED, PM),
      )
      val scheduleRules = listOf(
        aScheduleRule("AM", listOf("MON", "TUE")),
        aScheduleRule("PM", listOf("WED")),
      )

      assertThat(exclusions.toDpsExclusions(scheduleRules)).containsExactlyInAnyOrder(
        Slot(
          weekNumber = 1,
          timeSlot = Slot.TimeSlot.AM,
          monday = true,
          tuesday = true,
          wednesday = false,
          thursday = false,
          friday = false,
          saturday = false,
          sunday = false,
          daysOfWeek = setOf(MONDAY, TUESDAY),
        ),
        Slot(
          weekNumber = 1,
          timeSlot = Slot.TimeSlot.PM,
          monday = false,
          tuesday = false,
          wednesday = true,
          thursday = false,
          friday = false,
          saturday = false,
          sunday = false,
          daysOfWeek = setOf(WEDNESDAY),
        ),
      )
    }

    @Test
    fun `should ignore slots without a schedule rule`() {
      val exclusions = listOf(
        AllocationExclusion(MON, AM),
        AllocationExclusion(TUE, AM),
        AllocationExclusion(WED, PM),
      )
      val scheduleRules = listOf(
        aScheduleRule("AM", listOf("MON")),
      )

      assertThat(exclusions.toDpsExclusions(scheduleRules)).containsExactlyInAnyOrder(
        Slot(
          weekNumber = 1,
          timeSlot = Slot.TimeSlot.AM,
          monday = true,
          tuesday = false,
          wednesday = false,
          thursday = false,
          friday = false,
          saturday = false,
          sunday = false,
          daysOfWeek = setOf(MONDAY),
        ),
      )
    }

    @Test
    fun `should map a full day`() {
      val exclusions = listOf(
        AllocationExclusion(MON, null),
      )
      val scheduleRules = listOf(
        aScheduleRule("AM", listOf("MON")),
        aScheduleRule("PM", listOf("MON")),
        aScheduleRule("ED", listOf("MON")),
      )

      assertThat(exclusions.toDpsExclusions(scheduleRules)).containsExactlyInAnyOrder(
        Slot(
          weekNumber = 1,
          timeSlot = Slot.TimeSlot.AM,
          monday = true,
          tuesday = false,
          wednesday = false,
          thursday = false,
          friday = false,
          saturday = false,
          sunday = false,
          daysOfWeek = setOf(MONDAY),
        ),
        Slot(
          weekNumber = 1,
          timeSlot = Slot.TimeSlot.PM,
          monday = true,
          tuesday = false,
          wednesday = false,
          thursday = false,
          friday = false,
          saturday = false,
          sunday = false,
          daysOfWeek = setOf(MONDAY),
        ),
        Slot(
          weekNumber = 1,
          timeSlot = Slot.TimeSlot.ED,
          monday = true,
          tuesday = false,
          wednesday = false,
          thursday = false,
          friday = false,
          saturday = false,
          sunday = false,
          daysOfWeek = setOf(MONDAY),
        ),
      )
    }

    @Test
    fun `should map a combination of scenarios`() {
      val exclusions = listOf(
        AllocationExclusion(MON, null),
        AllocationExclusion(TUE, AM),
        AllocationExclusion(WED, null),
        AllocationExclusion(SAT, PM),
        AllocationExclusion(SUN, ED),
      )
      val scheduleRules = listOf(
        aScheduleRule("AM", listOf("MON", "TUE", "WED")),
        aScheduleRule("PM", listOf("MON", "WED", "SAT")),
        aScheduleRule("ED", listOf("MON", "WED", "SUN")),
      )

      assertThat(exclusions.toDpsExclusions(scheduleRules)).containsExactlyInAnyOrder(
        Slot(
          weekNumber = 1,
          timeSlot = Slot.TimeSlot.AM,
          monday = true,
          tuesday = true,
          wednesday = true,
          thursday = false,
          friday = false,
          saturday = false,
          sunday = false,
          daysOfWeek = setOf(MONDAY, TUESDAY, WEDNESDAY),
        ),
        Slot(
          weekNumber = 1,
          timeSlot = Slot.TimeSlot.PM,
          monday = true,
          tuesday = false,
          wednesday = true,
          thursday = false,
          friday = false,
          saturday = true,
          sunday = false,
          daysOfWeek = setOf(MONDAY, WEDNESDAY, SATURDAY),
        ),
        Slot(
          weekNumber = 1,
          timeSlot = Slot.TimeSlot.ED,
          monday = true,
          tuesday = false,
          wednesday = true,
          thursday = false,
          friday = false,
          saturday = false,
          sunday = true,
          daysOfWeek = setOf(MONDAY, WEDNESDAY, SUNDAY),
        ),
      )
    }

    private fun aScheduleRule(slot: String, days: List<String>): ScheduleRulesResponse {
      val startTime = when (slot) {
        "AM" -> "10:00"
        "PM" -> "13:00"
        else -> "18:00"
      }
      val endTime = when (slot) {
        "AM" -> "11:30"
        "PM" -> "14:30"
        else -> "19:30"
      }
      return ScheduleRulesResponse(
        startTime = startTime,
        endTime = endTime,
        monday = "MON" in days,
        tuesday = "TUE" in days,
        wednesday = "WED" in days,
        thursday = "THU" in days,
        friday = "FRI" in days,
        saturday = "SAT" in days,
        sunday = "SUN" in days,
        slotCategoryCode = slot,
      )
    }
  }
}

private fun pages(totalEntities: Int, pageSize: Int = 3, startId: Long = 1): PageImpl<FindActiveAllocationIdsResponse> = PageImpl<FindActiveAllocationIdsResponse>(
  (startId..totalEntities - 1 + startId).map { FindActiveAllocationIdsResponse(it) },
  Pageable.ofSize(pageSize),
  totalEntities.toLong(),
)
