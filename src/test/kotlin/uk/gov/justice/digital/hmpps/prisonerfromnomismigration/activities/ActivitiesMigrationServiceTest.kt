package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.microsoft.applicationinsights.TelemetryClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.FindActiveActivityIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

private const val PAGE_SIZE = 3

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
    pageSize = 200,
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
      pageSize = 200,
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
        pages(3)

      runBlocking {
        service.startMigration(ActivitiesMigrationFilter(prisonId = "BXI"))
      }

      verify(queueService).sendMessage(
        message = eq(MigrationMessageType.MIGRATE_ENTITIES),
        context = check<MigrationContext<ActivitiesMigrationFilter>> {
          Assertions.assertThat(it.estimatedCount).isEqualTo(3)
          Assertions.assertThat(it.body.prisonId).isEqualTo("BXI")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will write migration history record`() {
      val activitiesMigrationFilter = ActivitiesMigrationFilter(prisonId = "BXI")

      coEvery { nomisApiService.getActivityIds(any(), any(), any(), any()) } returns
        pages(3)

      runBlocking {
        service.startMigration(activitiesMigrationFilter)
      }

      coVerify {
        migrationHistoryService.recordMigrationStarted(
          migrationId = any(),
          migrationType = MigrationType.ACTIVITIES,
          estimatedRecordCount = 3,
          filter = coWithArg<ActivitiesMigrationFilter> {
            Assertions.assertThat(it.prisonId).isEqualTo("BXI")
          },
        )
      }

      with(auditDetailsParam.captured) {
        Assertions.assertThat(this).extracting("migrationId").isNotNull
        Assertions.assertThat(this).extracting("migrationType").isEqualTo("ACTIVITIES")
        Assertions.assertThat(this).extracting("filter").isEqualTo(activitiesMigrationFilter)
      }
    }

    @Test
    internal fun `will write analytic with estimated count and filter`() {
      coEvery { nomisApiService.getActivityIds(any(), any(), any(), any()) } returns pages(3)

      runBlocking {
        service.startMigration(ActivitiesMigrationFilter(prisonId = "BXI"))
      }

      verify(telemetryClient).trackEvent(
        eq("activities-migration-started"),
        check {
          Assertions.assertThat(it["migrationId"]).isNotNull
          Assertions.assertThat(it["estimatedCount"]).isEqualTo("3")
          Assertions.assertThat(it["prisonId"]).isEqualTo("BXI")
        },
        eq(null),
      )
    }
  }
}

private fun pages(total: Long, startId: Long = 1): PageImpl<FindActiveActivityIdsResponse> = PageImpl<FindActiveActivityIdsResponse>(
  (startId..total - 1 + startId).map { FindActiveActivityIdsResponse(it) },
  Pageable.ofSize(PAGE_SIZE),
  total,
)
