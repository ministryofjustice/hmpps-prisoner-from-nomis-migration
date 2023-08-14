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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ActivityMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.FindActiveActivityIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.GetActivityResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PayRatesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ScheduleRulesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsMigrationFilter
import java.math.BigDecimal
import java.time.LocalDate

class ActivitiesMigrationServiceTest {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val mappingService: ActivitiesMappingService = mock()
  private val activitiesApiService: ActivitiesApiService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val auditService: AuditService = mock()
  val service = ActivitiesMigrationService(
    nomisApiService = nomisApiService,
    queueService = queueService,
    activitiesMappingService = mappingService,
    activitiesApiService = activitiesApiService,
    migrationHistoryService = migrationHistoryService,
    telemetryClient = telemetryClient,
    auditService = auditService,
    pageSize = 3L,
    completeCheckDelaySeconds = 10,
    completeCheckCount = 9,
  )

  @Nested
  inner class MigrateActivities {
    private val nomisApiService = mockk<NomisApiService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val migrationHistoryService = mockk<MigrationHistoryService>(relaxed = true)
    private val activitiesApiService = mockk<ActivitiesApiService>()

    /* coroutine version of service required for this route */
    private val service = ActivitiesMigrationService(
      nomisApiService = nomisApiService,
      queueService = queueService,
      activitiesMappingService = mappingService,
      activitiesApiService = activitiesApiService,
      migrationHistoryService = migrationHistoryService,
      telemetryClient = telemetryClient,
      auditService = auditService,
      pageSize = 3L,
      completeCheckDelaySeconds = 10,
      completeCheckCount = 9,
    )
    private val auditWhatParam = slot<String>()
    private val auditDetailsParam = slot<Map<*, *>>()

    @BeforeEach
    internal fun setUp() {
      coEvery { nomisApiService.getActivityIds(any(), any(), any(), any()) } returns
        pages(1)

      coEvery {
        auditService.sendAuditEvent(
          what = capture(auditWhatParam), // makes mock match calls with any value for `speed` and record it in a slot
          details = capture(auditDetailsParam), // makes mock and capturing only match calls with specific `direction`. Use `any()` to match calls with any `direction`
        )
      } just runs

      coEvery {
        activitiesApiService.getActivityCategories()
      } returns listOf("SAA_EDUCATION")
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`() {
      runBlocking {
        service.startMigration(ActivitiesMigrationFilter(prisonId = "BXI"))
      }

      coVerify {
        nomisApiService.getActivityIds(
          prisonId = "BXI",
          excludeProgramCodes = listOf("SAA_EDUCATION"),
          pageNumber = 0,
          pageSize = 1,
        )
      }
    }

    @Test
    internal fun `will pass visit count and filter to queue`(): Unit = runBlocking {
      coEvery { nomisApiService.getActivityIds(any(), any(), any(), any()) } returns
        pages(totalEntities = 7)

      service.startMigration(ActivitiesMigrationFilter(prisonId = "BXI"))

      verify(queueService).sendMessage(
        message = eq(MigrationMessageType.MIGRATE_ENTITIES),
        context = check<MigrationContext<ActivitiesMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(7)
          assertThat(it.body.prisonId).isEqualTo("BXI")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will write migration history record`() {
      val activitiesMigrationFilter = ActivitiesMigrationFilter(prisonId = "BXI")

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
        service.startMigration(ActivitiesMigrationFilter(prisonId = "BXI"))
      }

      verify(telemetryClient).trackEvent(
        eq("activities-migration-started"),
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
  inner class DivideByPage {

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(nomisApiService.getActivityIds(any(), any(), any(), any())).thenReturn(
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
          body = ActivitiesMigrationFilter(prisonId = "BXI"),
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
          body = ActivitiesMigrationFilter(prisonId = "BXI"),
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
          body = ActivitiesMigrationFilter(prisonId = "BXI"),
        ),
      )

      verify(queueService, times(3)).sendMessage(
        message = eq(MigrationMessageType.MIGRATE_BY_PAGE),
        context = check<MigrationContext<MigrationPage<ActivitiesMigrationFilter>>> {
          assertThat(it.estimatedCount).isEqualTo(7)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.filter.prisonId).isEqualTo("BXI")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `each page will contain page number and page size`(): Unit = runBlocking {
      val context: KArgumentCaptor<MigrationContext<MigrationPage<ActivitiesMigrationFilter>>> = argumentCaptor()

      service.divideEntitiesByPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = ActivitiesMigrationFilter(prisonId = "BXI"),
        ),
      )

      verify(queueService, times(3)).sendMessage(
        eq(MigrationMessageType.MIGRATE_BY_PAGE),
        context.capture(),
        delaySeconds = eq(0),
      )
      val allContexts: List<MigrationContext<MigrationPage<ActivitiesMigrationFilter>>> = context.allValues

      val (firstPage, secondPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.pageNumber).isEqualTo(0)
      assertThat(firstPage.body.pageSize).isEqualTo(3)

      assertThat(secondPage.body.pageNumber).isEqualTo(1)
      assertThat(secondPage.body.pageSize).isEqualTo(3)

      assertThat(lastPage.body.pageNumber).isEqualTo(2)
      assertThat(lastPage.body.pageSize).isEqualTo(3)
    }
  }

  @Nested
  inner class MigrateForPage {
    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
      whenever(nomisApiService.getActivityIds(any(), any(), any(), any())).thenReturn(
        pages(totalEntities = 7, pageSize = 3),
      )
      whenever(activitiesApiService.getActivityCategories()).thenReturn(listOf("SAA_INDUCTION"))
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = MigrationPage(
            filter = ActivitiesMigrationFilter(prisonId = "BXI"),
            pageNumber = 1,
            pageSize = 3,
          ),
        ),
      )

      verify(nomisApiService).getActivityIds(
        prisonId = "BXI",
        excludeProgramCodes = listOf("SAA_INDUCTION"),
        pageNumber = 1,
        pageSize = 3,
      )
    }

    @Test
    internal fun `will send MIGRATE_ACTIVITIES with context for each activity`(): Unit = runBlocking {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = MigrationPage(
            filter = ActivitiesMigrationFilter(prisonId = "BXI"),
            pageNumber = 1,
            pageSize = 3,
          ),
        ),
      )

      verify(queueService, times(7)).sendMessage(
        message = eq(MigrationMessageType.MIGRATE_ENTITY),
        context = check<MigrationContext<VisitsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(7)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will send MIGRATE_ACTIVITIES with courseActivityId for each activity`(): Unit = runBlocking {
      val context: KArgumentCaptor<MigrationContext<FindActiveActivityIdsResponse>> = argumentCaptor()

      whenever(nomisApiService.getActivityIds(any(), any(), any(), any())).thenReturn(
        pages(totalEntities = 7, pageSize = 3, startId = 1000),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = MigrationPage(
            filter = ActivitiesMigrationFilter(
              prisonId = "BXI",
            ),
            pageNumber = 2,
            pageSize = 3,
          ),
        ),
      )

      verify(queueService, times(7)).sendMessage(
        eq(MigrationMessageType.MIGRATE_ENTITY),
        context.capture(),
        delaySeconds = eq(0),

      )
      val allContexts: List<MigrationContext<FindActiveActivityIdsResponse>> = context.allValues

      val (firstPage, secondPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.courseActivityId).isEqualTo(1000)
      assertThat(secondPage.body.courseActivityId).isEqualTo(1001)
      assertThat(lastPage.body.courseActivityId).isEqualTo(1006)
    }

    @Test
    internal fun `will not send MIGRATE_VISIT when cancelling`(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(true)

      whenever(nomisApiService.getActivityIds(any(), any(), any(), any())).thenReturn(
        pages(totalEntities = 7, pageSize = 3, startId = 1000),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = MigrationPage(
            filter = ActivitiesMigrationFilter(
              prisonId = "BXI",
            ),
            pageNumber = 2,
            pageSize = 3,
          ),
        ),
      )

      verifyNoInteractions(queueService)
    }
  }

  @Nested
  inner class Migration {

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)
    private val tomorrow = today.plusDays(1)

    @BeforeEach
    internal fun setUp(): Unit = runBlocking {
      whenever(mappingService.findNomisMapping(any())).thenReturn(null)
      whenever(nomisApiService.getActivity(any())).thenReturn(
        GetActivityResponse(
          courseActivityId = 123,
          programCode = "SOME_PROGRAM",
          prisonId = "BXI",
          startDate = yesterday,
          endDate = tomorrow,
          capacity = 10,
          description = "Some activity",
          payPerSession = "H",
          minimumIncentiveLevel = "BAS",
          excludeBankHolidays = true,
          internalLocationDescription = "BXI-A-1-01",
          internalLocationCode = "CELL-01",
          internalLocationId = 123,
          scheduleRules = listOf(
            ScheduleRulesResponse(
              startTime = "${today.atTime(8, 0)}",
              endTime = "${today.atTime(11, 30)}",
              monday = true,
              tuesday = true,
              wednesday = true,
              thursday = false,
              friday = false,
              saturday = false,
              sunday = false,
            ),
            ScheduleRulesResponse(
              startTime = "${today.atTime(13, 0)}",
              endTime = "${today.atTime(16, 30)}",
              monday = false,
              tuesday = false,
              wednesday = true,
              thursday = true,
              friday = true,
              saturday = true,
              sunday = true,
            ),
          ),
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
        ),
      )

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
      service.migrateNomisEntity(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = FindActiveActivityIdsResponse(123),
        ),
      )

      verify(nomisApiService).getActivity(123)
    }

    @Test
    internal fun `will migrate an activity to DPS`(): Unit = runBlocking {
      service.migrateNomisEntity(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = FindActiveActivityIdsResponse(123),
        ),
      )

      verify(activitiesApiService).migrateActivity(
        check {
          assertThat(it.programServiceCode).isEqualTo("SOME_PROGRAM")
          assertThat(it.prisonCode).isEqualTo("BXI")
          assertThat(it.startDate).isEqualTo(yesterday)
          assertThat(it.endDate).isEqualTo(tomorrow)
          assertThat(it.capacity).isEqualTo(10)
          assertThat(it.description).isEqualTo("Some activity")
          assertThat(it.payPerSession).isEqualTo(ActivityMigrateRequest.PayPerSession.H)
          assertThat(it.minimumIncentiveLevel).isEqualTo("BAS")
          assertThat(it.runsOnBankHoliday).isEqualTo(false)
          assertThat(it.internalLocationId).isEqualTo(123)
          assertThat(it.internalLocationCode).isEqualTo("CELL-01")
          assertThat(it.internalLocationDescription).isEqualTo("BXI-A-1-01")
          with(it.payRates.first { it.nomisPayBand == "1" }) {
            assertThat(incentiveLevel).isEqualTo("BAS")
            assertThat(rate).isEqualTo(120)
          }
          with(it.payRates.first { it.nomisPayBand == "2" }) {
            assertThat(incentiveLevel).isEqualTo("BAS")
            assertThat(rate).isEqualTo(140)
          }
          with(it.scheduleRules[0]) {
            assertThat(startTime).isEqualTo("${today}T08:00")
            assertThat(endTime).isEqualTo("${today}T11:30")
            assertThat(monday).isTrue()
            assertThat(tuesday).isTrue()
            assertThat(wednesday).isTrue()
            assertThat(thursday).isFalse()
            assertThat(friday).isFalse()
            assertThat(saturday).isFalse()
            assertThat(sunday).isFalse()
          }
          with(it.scheduleRules[1]) {
            assertThat(startTime).isEqualTo("${today}T13:00")
            assertThat(endTime).isEqualTo("${today}T16:30")
            assertThat(monday).isFalse()
            assertThat(tuesday).isFalse()
            assertThat(wednesday).isTrue()
            assertThat(thursday).isTrue()
            assertThat(friday).isTrue()
            assertThat(saturday).isTrue()
            assertThat(sunday).isTrue()
          }
        },
      )
    }

    @Test
    internal fun `will throw after an error checking the mapping service so the message is rejected and retried`(): Unit =
      runBlocking {
        whenever(mappingService.findNomisMapping(any())).thenThrow(WebClientResponseException.BadGateway::class.java)

        assertThrows<WebClientResponseException.BadGateway> {
          service.migrateNomisEntity(
            MigrationContext(
              type = MigrationType.ACTIVITIES,
              migrationId = "2020-05-23T11:30:00",
              estimatedCount = 7,
              body = FindActiveActivityIdsResponse(123),
            ),
          )
        }

        verifyNoInteractions(queueService)
      }

    @Test
    internal fun `will throw after an error retrieving the Nomis entity so the message is rejected and retried`(): Unit =
      runBlocking {
        whenever(nomisApiService.getActivity(any())).thenThrow(WebClientResponseException.BadGateway::class.java)

        assertThrows<WebClientResponseException.BadGateway> {
          service.migrateNomisEntity(
            MigrationContext(
              type = MigrationType.ACTIVITIES,
              migrationId = "2020-05-23T11:30:00",
              estimatedCount = 7,
              body = FindActiveActivityIdsResponse(123),
            ),
          )
        }

        verifyNoInteractions(queueService)
      }

    @Test
    internal fun `will throw after an error creating the Activities entity so the message is rejected and retried`(): Unit =
      runBlocking {
        whenever(activitiesApiService.migrateActivity(any())).thenThrow(WebClientResponseException.BadGateway::class.java)

        assertThrows<WebClientResponseException.BadGateway> {
          service.migrateNomisEntity(
            MigrationContext(
              type = MigrationType.ACTIVITIES,
              migrationId = "2020-05-23T11:30:00",
              estimatedCount = 7,
              body = FindActiveActivityIdsResponse(123),
            ),
          )
        }

        verifyNoInteractions(queueService)
      }

    @Test
    internal fun `will NOT throw but will publish a retry mapping message after an error creating the new mapping`(): Unit =
      runBlocking {
        whenever(mappingService.createMapping(any(), any())).thenThrow(WebClientResponseException.BadGateway::class.java)

        assertDoesNotThrow {
          service.migrateNomisEntity(
            MigrationContext(
              type = MigrationType.ACTIVITIES,
              migrationId = "2020-05-23T11:30:00",
              estimatedCount = 7,
              body = FindActiveActivityIdsResponse(123),
            ),
          )
        }

        verify(queueService).sendMessage(
          message = eq(MigrationMessageType.RETRY_MIGRATION_MAPPING),
          context = check<MigrationContext<ActivityMigrationMappingDto>> {
            assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
            assertThat(it.body.nomisCourseActivityId).isEqualTo(123)
            assertThat(it.body.activityScheduleId).isEqualTo(456)
            assertThat(it.body.activityScheduleId2).isEqualTo(789)
          },
          delaySeconds = eq(0),
        )
      }

    @Test
    fun `will do nothing when already migrated`() = runBlocking {
      whenever(mappingService.findNomisMapping(any()))
        .thenReturn(
          ActivityMigrationMappingDto(
            nomisCourseActivityId = 123L,
            activityScheduleId = 456,
            activityScheduleId2 = 789,
            label = "An old migration",
          ),
        )

      service.migrateNomisEntity(
        MigrationContext(
          type = MigrationType.ACTIVITIES,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 7,
          body = FindActiveActivityIdsResponse(123),
        ),
      )

      verifyNoInteractions(activitiesApiService)
    }
  }
}

private fun pages(totalEntities: Int, pageSize: Int = 3, startId: Long = 1): PageImpl<FindActiveActivityIdsResponse> = PageImpl<FindActiveActivityIdsResponse>(
  (startId..totalEntities - 1 + startId).map { FindActiveActivityIdsResponse(it) },
  Pageable.ofSize(pageSize),
  totalEntities.toLong(),
)
