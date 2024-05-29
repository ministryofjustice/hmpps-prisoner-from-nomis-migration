package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

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
import org.mockito.kotlin.argThat
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.Capacity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.Certification
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.ChangeHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.NomisMigrateLocationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AmendmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.LocationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.UsageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatusCheck
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.LOCATIONS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

private const val NOMIS_PARENT_ID = 23456L
private const val DPS_PARENT_ID = "fedcba98-3e3e-3e3e-3e3e-3e3e3e3e3e3e"

@ExtendWith(MockitoExtension::class)
class LocationsMigrationServiceTest {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val auditService: AuditService = mock()
  private val locationsService: LocationsService = mock()
  private val locationsMappingService: LocationsMappingService = mock()
  val service = LocationsMigrationService(
    nomisApiService = nomisApiService,
    queueService = queueService,
    migrationHistoryService = migrationHistoryService,
    telemetryClient = telemetryClient,
    auditService = auditService,
    locationsService = locationsService,
    locationsMappingService = locationsMappingService,
    pageSize = 200,
    completeCheckDelaySeconds = 10,
    completeCheckCount = 9,
  )

  @Nested
  @DisplayName("migrateLocations")
  inner class MigrateLocations {
    private val nomisApiService = mockk<NomisApiService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val migrationHistoryService = mockk<MigrationHistoryService>(relaxed = true)

    private val auditWhatParam = slot<String>()
    private val auditDetailsParam = slot<Map<*, *>>()

    val service = LocationsMigrationService(
      nomisApiService = nomisApiService,
      queueService = queueService,
      migrationHistoryService = migrationHistoryService,
      telemetryClient = telemetryClient,
      auditService = auditService,
      locationsService = locationsService,
      locationsMappingService = locationsMappingService,
      pageSize = 200,
      completeCheckDelaySeconds = 10,
      completeCheckCount = 9,
    )

    @BeforeEach
    fun setUp() {
      coEvery { nomisApiService.getLocationIds(any(), any()) } returns
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
      runTest {
        service.startMigration(
          LocationsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      coVerify {
        nomisApiService.getLocationIds(
          pageNumber = 0,
          pageSize = 1,
        )
      }
    }

    @Test
    fun `will pass location count and filter to queue`() = runBlocking {
      coEvery { nomisApiService.getLocationIds(any(), any()) } returns
        pages(23)

      runTest {
        service.startMigration(
          LocationsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      verify(queueService).sendMessage(
        message = eq(MIGRATE_ENTITIES),
        context = check<MigrationContext<LocationsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(23)
          assertThat(it.body.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(it.body.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    fun `will write migration history record`() {
      val locationsMigrationFilter = LocationsMigrationFilter(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
      )

      coEvery { nomisApiService.getLocationIds(any(), any()) } returns
        pages(23)

      runTest {
        service.startMigration(locationsMigrationFilter)
      }

      coVerify {
        migrationHistoryService.recordMigrationStarted(
          migrationId = any(),
          migrationType = LOCATIONS,
          estimatedRecordCount = 23,
          filter = coWithArg<LocationsMigrationFilter> {
            assertThat(it.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
            assertThat(it.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
          },
        )
      }

      with(auditDetailsParam.captured) {
        assertThat(this).extracting("migrationId").isNotNull
        assertThat(this).extracting("filter").isEqualTo(locationsMigrationFilter)
      }
    }

    @Test
    fun `will write analytic with estimated count and filter`() {
      coEvery { nomisApiService.getLocationIds(any(), any()) } returns
        pages(23)

      runTest {
        service.startMigration(
          LocationsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("locations-migration-started"),
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
    fun `will write analytics with empty filter`() {
      coEvery {
        nomisApiService.getLocationIds(
          pageNumber = any(),
          pageSize = any(),
        )
      } returns
        pages(23)

      runTest {
        service.startMigration(LocationsMigrationFilter())
      }

      verify(telemetryClient).trackEvent(
        eq("locations-migration-started"),
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
  @DisplayName("divideLocationsByPage")
  inner class DivideLocationsByPage {

    @BeforeEach
    fun setUp() = runTest {
      whenever(nomisApiService.getLocationIds(any(), any())).thenReturn(pages(100_200))
    }

    @Test
    fun `will send a page message for every page (200) of locations `() = runTest {
      service.divideEntitiesByPage(
        MigrationContext(
          type = LOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = LocationsMigrationFilter(
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
    fun `will also send a single MIGRATION_STATUS_CHECK message`() = runTest {
      service.divideEntitiesByPage(
        MigrationContext(
          type = LOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = LocationsMigrationFilter(
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
    fun `each page with have the filter and context attached`() = runTest {
      service.divideEntitiesByPage(
        MigrationContext(
          type = LOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = LocationsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        ),
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        message = eq(MIGRATE_BY_PAGE),
        context = check<MigrationContext<MigrationPage<LocationsMigrationFilter>>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.filter.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(it.body.filter.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    fun `each page will contain page number and page size`() = runTest {
      val context: KArgumentCaptor<MigrationContext<MigrationPage<LocationsMigrationFilter>>> = argumentCaptor()

      service.divideEntitiesByPage(
        MigrationContext(
          type = LOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = LocationsMigrationFilter(
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
      val allContexts: List<MigrationContext<MigrationPage<LocationsMigrationFilter>>> = context.allValues

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
  inner class MigrateLocationsStatusCheck {
    @Nested
    @DisplayName("when there are still messages on the queue")
    inner class MessagesOnQueue {
      @BeforeEach
      fun setUp() = runTest {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(true)
      }

      @Test
      fun `will check again in 10 seconds`() = runTest {
        service.migrateStatusCheck(
          MigrationContext(
            type = LOCATIONS,
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
      fun `will check again in 10 second and reset even when previously started finishing up phase`() =
        runTest {
          service.migrateStatusCheck(
            MigrationContext(
              type = LOCATIONS,
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
      fun setUp() = runTest {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(0)
        whenever(locationsMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      fun `will increment check count and try again a second when only checked 9 times`() = runTest {
        service.migrateStatusCheck(
          MigrationContext(
            type = LOCATIONS,
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
      fun `will finish off when checked 10 times previously`() = runTest {
        service.migrateStatusCheck(
          MigrationContext(
            type = LOCATIONS,
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
      fun `will add completed telemetry when finishing off`() = runTest {
        service.migrateStatusCheck(
          MigrationContext(
            type = LOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("locations-migration-completed"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("23")
            assertThat(it["durationMinutes"]).isNotNull()
          },
          isNull(),
        )
      }

      @Test
      fun `will update migration history record when finishing off`() = runTest {
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(2)
        whenever(locationsMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.migrateStatusCheck(
          MigrationContext(
            type = LOCATIONS,
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
      fun setUp() = runTest {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(true)
      }

      @Test
      fun `will check again in 10 seconds`() = runTest {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = LOCATIONS,
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
      fun `will check again in 10 second and reset even when previously started finishing up phase`() =
        runTest {
          service.cancelMigrateStatusCheck(
            MigrationContext(
              type = LOCATIONS,
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
      fun setUp() = runTest {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed(any())).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(0)
        whenever(locationsMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      fun `will increment check count and try again a second when only checked 9 times`() = runTest {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = LOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 9),
          ),
        )

        verify(queueService).purgeAllMessages(check { assertThat(it).isEqualTo(LOCATIONS) })

        verify(queueService).sendMessage(
          message = eq(CANCEL_MIGRATION),
          context = check<MigrationContext<MigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(10)
          },
          delaySeconds = eq(1),
        )
      }

      @Test
      fun `will finish off when checked 10 times previously`() = runTest {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = LOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(queueService, never()).purgeAllMessages(check { assertThat(it).isEqualTo(LOCATIONS) })
        verify(queueService, never()).sendMessage(
          message = eq(CANCEL_MIGRATION),
          context = any(),
          delaySeconds = any(),
        )
      }

      @Test
      fun `will add completed telemetry when finishing off`() = runTest {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = LOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("locations-migration-cancelled"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("23")
            assertThat(it["durationMinutes"]).isNotNull()
          },
          isNull(),
        )
      }

      @Test
      fun `will update migration history record when cancelling`() = runTest {
        whenever(queueService.countMessagesThatHaveFailed(any())).thenReturn(2)
        whenever(locationsMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = LOCATIONS,
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
  inner class MigrateLocationsForPage {
    @BeforeEach
    fun setUp() = runTest {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
      whenever(nomisApiService.getLocationIds(any(), any())).thenReturn(
        pages(15),
      )
    }

    @Test
    fun `will pass filter through to get total count along with a tiny page count`() = runTest {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = LOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = LocationsMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
            ),
            pageNumber = 13,
            pageSize = 15,
          ),
        ),
      )

      verify(nomisApiService).getLocationIds(
        pageNumber = 13,
        pageSize = 15,
      )
    }

    @Test
    fun `will send MIGRATE_LOCATION with context for each location`() =
      runTest {
        service.migrateEntitiesForPage(
          MigrationContext(
            type = LOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationPage(
              filter = LocationsMigrationFilter(
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
          context = check<MigrationContext<LocationsMigrationFilter>> {
            assertThat(it.estimatedCount).isEqualTo(100_200)
            assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          },
          delaySeconds = eq(0),
        )
      }

    @Test
    fun `will send MIGRATE_LOCATION with id for each location`() =
      runTest {
        val context: KArgumentCaptor<MigrationContext<LocationIdResponse>> = argumentCaptor()

        whenever(nomisApiService.getLocationIds(any(), any())).thenReturn(
          pages(
            15,
            startId = 1000,
          ),
        )

        service.migrateEntitiesForPage(
          MigrationContext(
            type = LOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationPage(
              filter = LocationsMigrationFilter(
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
        val allContexts: List<MigrationContext<LocationIdResponse>> = context.allValues

        val (firstPage, secondPage, thirdPage) = allContexts
        val lastPage = allContexts.last()

        assertThat(firstPage.body.locationId).isEqualTo(1000L)
        assertThat(secondPage.body.locationId).isEqualTo(1001L)
        assertThat(thirdPage.body.locationId).isEqualTo(1002L)
        assertThat(lastPage.body.locationId).isEqualTo(1014L)
      }

    @Test
    fun `will not send MIGRATE_LOCATION when cancelling`() = runTest {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(true)

      whenever(nomisApiService.getLocationIds(any(), any())).thenReturn(
        pages(
          15,
          startId = 1000,
        ),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = LOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = LocationsMigrationFilter(
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
  @DisplayName("migrateLocation")
  inner class MigrateLocation {
    val createdId = "45678901-3e3e-3e3e-3e3e-3e3e3e3e3e3e"

    val basicLocation = Location(
      id = UUID.fromString(createdId),
      // Note we only care about the id
      code = "3",
      locationType = Location.LocationType.LANDING,
      prisonId = "MDI",
      active = true,
      isResidential = true,
      key = "key",
      topLevelId = UUID.fromString(DPS_PARENT_ID),
      pathHierarchy = "MDI-C",
      deactivatedByParent = false,
      permanentlyInactive = false,
      status = Location.Status.ACTIVE,
      lastModifiedBy = "me",
      lastModifiedDate = "2024-05-25",
    )

    @BeforeEach
    fun setUp() = runTest {
      whenever(locationsMappingService.getMappingGivenNomisId(any())).thenReturn(null)
      whenever(locationsMappingService.getMappingGivenNomisId(NOMIS_PARENT_ID)).thenReturn(
        LocationMappingDto(
          dpsLocationId = DPS_PARENT_ID,
          nomisLocationId = NOMIS_PARENT_ID,
          label = "2020-05-23T11:30:00",
          mappingType = LocationMappingDto.MappingType.MIGRATED,
        ),
      )
      whenever(nomisApiService.getLocation(any())).thenReturn(aNomisLocationResponse())
      whenever(locationsService.migrateLocation(any())).thenReturn(aDpsLocation())
      whenever(locationsMappingService.createMapping(any(), any())).thenReturn(CreateMappingResult())
    }

    @Test
    fun `will retrieve location from NOMIS`() = runTest {
      service.migrateNomisEntity(
        MigrationContext(
          type = LOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = LocationIdResponse(12345L),
        ),
      )

      verify(nomisApiService).getLocation(12345L)
    }

    @Test
    internal fun `will transform and send that location to the Locations service`() = runTest {
      whenever(nomisApiService.getLocation(any())).thenReturn(aNomisLocationResponse())

      service.migrateNomisEntity(
        MigrationContext(
          type = LOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = LocationIdResponse(12345L),
        ),
      )

      verify(locationsService).migrateLocation(
        eq(
          NomisMigrateLocationRequest(
            code = "3",
            localName = "Wing C, landing 3",
            comments = "landing 3",
            locationType = NomisMigrateLocationRequest.LocationType.LANDING,
            prisonId = "MDI",
            orderWithinParentLocation = 1,
            parentId = UUID.fromString(DPS_PARENT_ID),
            residentialHousingType = NomisMigrateLocationRequest.ResidentialHousingType.RECEPTION,
            capacity = Capacity(42, 41),
            certification = Certification(true, 40),
            lastUpdatedBy = "TJONES_ADM",
            createDate = "2023-09-25T11:12:45",
            attributes = setOf(
              NomisMigrateLocationRequest.Attributes.CAT_C,
            ),
            usage = setOf(
              NonResidentialUsageDto(
                usageType = NonResidentialUsageDto.UsageType.OCCURRENCE,
                capacity = 42,
                sequence = 5,
              ),
            ),
            isDeactivated = false,
            isCell = false,
            history = listOf(
              ChangeHistory(
                amendedDate = "2023-09-25T11:12:45",
                attribute = "LOCATION_TYPE",
                oldValue = "41",
                newValue = "42",
                amendedBy = "STEVE_ADM",
              ),
            ),
          ),
        ),
      )
    }

    @Test
    fun `will transform inactive location correctly`() = runTest {
      whenever(nomisApiService.getLocation(any())).thenReturn(aNomisLocationResponse().copy(active = false))
      whenever(locationsService.migrateLocation(any())).thenReturn(basicLocation)

      service.migrateNomisEntity(
        MigrationContext(
          type = LOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = LocationIdResponse(12345L),
        ),
      )

      verify(locationsService).migrateLocation(
        check {
          assertThat(it.deactivatedDate).isEqualTo(LocalDate.parse("2023-09-20"))
          assertThat(it.deactivationReason).isEqualTo(NomisMigrateLocationRequest.DeactivationReason.CELL_RECLAIMS)
          assertThat(it.proposedReactivationDate).isEqualTo(LocalDate.parse("2023-09-30"))
        },
      )
    }

    @Test
    fun `will transform inactive location with no reason`() = runTest {
      whenever(nomisApiService.getLocation(any())).thenReturn(aNomisLocationResponse().copy(active = false, reasonCode = null))
      whenever(locationsService.migrateLocation(any())).thenReturn(basicLocation)

      service.migrateNomisEntity(
        MigrationContext(
          type = LOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = LocationIdResponse(12345L),
        ),
      )

      verify(locationsService).migrateLocation(
        check {
          assertThat(it.deactivatedDate).isEqualTo(LocalDate.parse("2023-09-20"))
          assertThat(it.deactivationReason).isEqualTo(NomisMigrateLocationRequest.DeactivationReason.OTHER)
          assertThat(it.proposedReactivationDate).isEqualTo(LocalDate.parse("2023-09-30"))
        },
      )
    }

    @Test
    fun `will add telemetry events for migrated entries`() = runTest {
      whenever(nomisApiService.getLocation(any())).thenReturn(aNomisLocationResponse())

      service.migrateNomisEntity(
        MigrationContext(
          type = LOCATIONS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = LocationIdResponse(12345L),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("locations-migration-entity-migrated"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["dpsLocationId"]).isNotNull
          assertThat(it["key"]).isNotNull
          assertThat(it["nomisLocationId"]).isEqualTo("12345")
        },
        isNull(),
      )
    }

    @Test
    fun `will create a mapping between a new Location and a NOMIS Location`() =
      runTest {
        whenever(nomisApiService.getLocation(any())).thenReturn(aNomisLocationResponse())
        whenever(locationsService.migrateLocation(any())).thenReturn(aDpsLocation())

        service.migrateNomisEntity(
          MigrationContext(
            type = LOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = LocationIdResponse(12345L),
          ),
        )

        verify(locationsMappingService).createMapping(
          LocationMappingDto(
            dpsLocationId = "f1c1e3e3-3e3e-3e3e-3e3e-3e3e3e3e3e3e",
            nomisLocationId = 12345L,
            label = "2020-05-23T11:30:00",
            mappingType = LocationMappingDto.MappingType.MIGRATED,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<LocationMappingDto>>() {},
        )
      }

    @Test
    fun `will skip an invalid attribute`() =
      runTest {
        whenever(nomisApiService.getLocation(any())).thenReturn(
          aNomisLocationResponse().copy(
            profiles = listOf(ProfileRequest(ProfileRequest.ProfileType.SUP_LVL_TYPE, "DUFF")),
          ),
        )
        whenever(locationsService.migrateLocation(any())).thenReturn(aDpsLocation())

        service.migrateNomisEntity(
          MigrationContext(
            type = LOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = LocationIdResponse(12345L),
          ),
        )

        verify(locationsService).migrateLocation(
          argThat {
            attributes!!.isEmpty()
          },
        )
      }

    @Test
    fun `will not throw an exception (and place message back on queue) but create a new retry message`() =
      runTest {
        whenever(nomisApiService.getLocation(any())).thenReturn(aNomisLocationResponse())
        whenever(locationsService.migrateLocation(any())).thenReturn(aDpsLocation())

        whenever(
          locationsMappingService.createMapping(
            any(),
            eq(object : ParameterizedTypeReference<DuplicateErrorResponse<LocationMappingDto>>() {}),
          ),
        ).thenThrow(
          RuntimeException("something went wrong"),
        )

        service.migrateNomisEntity(
          MigrationContext(
            type = LOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = LocationIdResponse(12345L),
          ),
        )

        verify(queueService).sendMessage(
          message = eq(RETRY_MIGRATION_MAPPING),
          context = check<MigrationContext<LocationMappingDto>> {
            assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
            assertThat(it.body.dpsLocationId).isEqualTo("f1c1e3e3-3e3e-3e3e-3e3e-3e3e3e3e3e3e")
            assertThat(it.body.nomisLocationId).isEqualTo(12345L)
          },
          delaySeconds = eq(0),
        )
      }

    @Nested
    inner class WhenMigratedAlready {
      @BeforeEach
      fun setUp() = runTest {
        whenever(locationsMappingService.getMappingGivenNomisId(any())).thenReturn(
          LocationMappingDto(
            dpsLocationId = "4321abcd",
            nomisLocationId = 12345L,
            mappingType = LocationMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
      }

      @Test
      fun `will do nothing`() = runTest {
        service.migrateNomisEntity(
          MigrationContext(
            type = LOCATIONS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = LocationIdResponse(12345L),
          ),
        )

        verifyNoInteractions(locationsService)
      }
    }
  }

  @Test
  fun `will create audit event on user cancel`() {
    runTest {
      whenever(migrationHistoryService.get("123-2020-01-01")).thenReturn(
        MigrationHistory(
          migrationId = "123-2020-01-01 ",
          status = MigrationStatus.CANCELLED,
          whenEnded = LocalDateTime.parse("2020-01-01T00:00:00"),
          whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
          migrationType = LOCATIONS,
          estimatedRecordCount = 100,
        ),
      )

      service.cancel("123-2020-01-01")

      verify(auditService).sendAuditEvent(
        eq("MIGRATION_CANCEL_REQUESTED"),
        check {
          it as Map<*, *>
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["migrationType"]).isEqualTo(LOCATIONS.name)
        },
      )
    }
  }
}

fun aNomisLocationResponse() = LocationResponse(
  locationId = 12345L,
  comment = "landing 3",
  locationType = "LAND",
  locationCode = "3",
  description = "MDI-C-3",
  parentLocationId = NOMIS_PARENT_ID,
  parentKey = "MDI-C",
  userDescription = "Wing C, landing 3",
  prisonId = "MDI",
  listSequence = 1,
  unitType = LocationResponse.UnitType.REC,
  capacity = 42,
  operationalCapacity = 41,
  cnaCapacity = 40,
  certified = true,
  createUsername = "TJONES_ADM",
  createDatetime = "2023-09-25T11:12:45",
  active = true,
  deactivateDate = LocalDate.parse("2023-09-20"),
  reasonCode = LocationResponse.ReasonCode.B,
  reactivateDate = LocalDate.parse("2023-09-30"),
  profiles = listOf(ProfileRequest(ProfileRequest.ProfileType.SUP_LVL_TYPE, "C")),
  usages = listOf(
    UsageRequest(UsageRequest.InternalLocationUsageType.OCCUR, 42, 5),
  ),
  amendments = listOf(
    AmendmentResponse(
      amendDateTime = "2023-09-25T11:12:45",
      columnName = "Accommodation Type",
      oldValue = "41",
      newValue = "42",
      amendedBy = "STEVE_ADM",
    ),
  ),
)

fun aDpsLocation() = Location(
  id = UUID.fromString("f1c1e3e3-3e3e-3e3e-3e3e-3e3e3e3e3e3e"),
  locationType = Location.LocationType.LANDING,
  code = "3",
  localName = "Wing C",
  prisonId = "MDI",
  comments = "Test comment",
  active = true,
  isResidential = true,
  key = "key",
  topLevelId = UUID.fromString(DPS_PARENT_ID),
  pathHierarchy = "MDI-C",
  parentId = UUID.fromString(DPS_PARENT_ID),
  deactivatedByParent = false,
  permanentlyInactive = false,
  status = Location.Status.ACTIVE,
  lastModifiedBy = "me",
  lastModifiedDate = "2024-05-25",
)

fun pages(total: Long, startId: Long = 1): PageImpl<LocationIdResponse> = PageImpl<LocationIdResponse>(
  (startId..total - 1 + startId).map { LocationIdResponse(it) },
  Pageable.ofSize(10),
  total,
)
