package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

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
import org.junit.jupiter.api.assertThrows
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisReport
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.PairStringListDescriptionAddendum
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.CANCEL_MIGRATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITIES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.RETRY_MIGRATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IncidentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IncidentMappingDto.MappingType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IncidentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IncidentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IncidentStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Staff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatusCheck
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.INCIDENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.pageNumber
import java.time.LocalDate
import java.time.LocalDateTime

private const val NOMIS_INCIDENT_ID = 1234L
private const val DPS_INCIDENT_ID = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42"

@ExtendWith(MockitoExtension::class)
@JsonTest
internal class IncidentsMigrationServiceTest(@Autowired private val jsonMapper: JsonMapper) {
  private val nomisApiService: IncidentsNomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val auditService: AuditService = mock()
  private val incidentsService: IncidentsService = mock()
  private val incidentsMappingService: IncidentsMappingService = mock()
  val service = object : IncidentsMigrationService(
    nomisApiService = nomisApiService,
    incidentsService = incidentsService,
    incidentsMappingService = incidentsMappingService,
    pageSize = 200,
    completeCheckDelaySeconds = 10,
    completeCheckCount = 9,
    jsonMapper = jsonMapper,
  ) {
    init {
      queueService = this@IncidentsMigrationServiceTest.queueService
      migrationHistoryService = this@IncidentsMigrationServiceTest.migrationHistoryService
      telemetryClient = this@IncidentsMigrationServiceTest.telemetryClient
      auditService = this@IncidentsMigrationServiceTest.auditService
    }
  }

  @Nested
  @DisplayName("migrateIncidents")
  inner class MigrateIncidents {
    private val nomisApiService = mockk<IncidentsNomisApiService>()
    private val auditService = mockk<AuditService>(relaxed = true)
    private val migrationHistoryService = mockk<MigrationHistoryService>(relaxed = true)

    private val auditWhatParam = slot<String>()
    private val auditDetailsParam = slot<Map<*, *>>()

    val service = object : IncidentsMigrationService(
      nomisApiService = nomisApiService,
      incidentsService = incidentsService,
      incidentsMappingService = incidentsMappingService,
      pageSize = 200,
      completeCheckDelaySeconds = 10,
      completeCheckCount = 9,
      jsonMapper = jsonMapper,
    ) {
      init {
        queueService = this@IncidentsMigrationServiceTest.queueService
        migrationHistoryService = this@MigrateIncidents.migrationHistoryService
        telemetryClient = this@IncidentsMigrationServiceTest.telemetryClient
        auditService = this@MigrateIncidents.auditService
      }
    }

    @BeforeEach
    internal fun setUp() {
      coEvery { nomisApiService.getIncidentIds(any(), any(), any(), any()) } returns
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
          IncidentsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      coVerify {
        nomisApiService.getIncidentIds(
          fromDate = LocalDate.parse("2020-01-01"),
          toDate = LocalDate.parse("2020-01-02"),
          pageNumber = 0,
          pageSize = 1,
        )
      }
    }

    @Test
    internal fun `will pass incident count and filter to queue`(): Unit = runBlocking {
      coEvery { nomisApiService.getIncidentIds(any(), any(), any(), any()) } returns
        pages(23)

      runTest {
        service.startMigration(
          IncidentsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      verify(queueService).sendMessage(
        message = eq(MIGRATE_ENTITIES),
        context = check<MigrationContext<IncidentsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(23)
          assertThat(it.body.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(it.body.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will write migration history record`() {
      val incidentsMigrationFilter = IncidentsMigrationFilter(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
      )

      coEvery { nomisApiService.getIncidentIds(any(), any(), any(), any()) } returns
        pages(23)

      runTest {
        service.startMigration(
          incidentsMigrationFilter,
        )
      }

      coVerify {
        migrationHistoryService.recordMigrationStarted(
          migrationId = any(),
          migrationType = INCIDENTS,
          estimatedRecordCount = 23,
          filter = coWithArg<IncidentsMigrationFilter> {
            assertThat(it.fromDate).isEqualTo(LocalDate.parse("2020-01-01"))
            assertThat(it.toDate).isEqualTo(LocalDate.parse("2020-01-02"))
          },
        )
      }

      with(auditDetailsParam.captured) {
        assertThat(this).extracting("migrationId").isNotNull
        assertThat(this).extracting("filter").isEqualTo(incidentsMigrationFilter)
      }
    }

    @Test
    internal fun `will write analytic with estimated count and filter`() {
      coEvery { nomisApiService.getIncidentIds(any(), any(), any(), any()) } returns
        pages(23)

      runTest {
        service.startMigration(
          IncidentsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("incidents-migration-started"),
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
        nomisApiService.getIncidentIds(
          fromDate = isNull(),
          toDate = isNull(),
          pageNumber = any(),
          pageSize = any(),
        )
      } returns
        pages(23)

      runTest {
        service.startMigration(
          IncidentsMigrationFilter(),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("incidents-migration-started"),
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
  @DisplayName("divideIncidentsByPage")
  inner class DivideIncidentsByPage {

    @BeforeEach
    internal fun setUp(): Unit = runTest {
      whenever(nomisApiService.getIncidentIds(any(), any(), any(), any())).thenReturn(
        pages(100_200),
      )
    }

    @Test
    internal fun `will send a page message for every page (200) of incidents `(): Unit = runTest {
      service.divideEntitiesByPage(
        MigrationContext(
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncidentsMigrationFilter(
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
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncidentsMigrationFilter(
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
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncidentsMigrationFilter(
            fromDate = LocalDate.parse("2020-01-01"),
            toDate = LocalDate.parse("2020-01-02"),
          ),
        ),
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        message = eq(MIGRATE_BY_PAGE),
        context = check<MigrationContext<MigrationPage<IncidentsMigrationFilter, *>>> {
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
      val context: KArgumentCaptor<MigrationContext<MigrationPage<IncidentsMigrationFilter, *>>> = argumentCaptor()

      service.divideEntitiesByPage(
        MigrationContext(
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncidentsMigrationFilter(
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
      val allContexts: List<MigrationContext<MigrationPage<IncidentsMigrationFilter, *>>> = context.allValues

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
  inner class MigrateIncidentsStatusCheck {
    @BeforeEach
    fun setUp(): Unit = runBlocking {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
    }

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
            type = INCIDENTS,
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
      internal fun `will check again in 10 second and reset even when previously started finishing up phase`(): Unit = runTest {
        service.migrateStatusCheck(
          MigrationContext(
            type = INCIDENTS,
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
        whenever(incidentsMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runTest {
        service.migrateStatusCheck(
          MigrationContext(
            type = INCIDENTS,
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
            type = INCIDENTS,
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
            type = INCIDENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("incidents-migration-completed"),
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
        whenever(incidentsMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.migrateStatusCheck(
          MigrationContext(
            type = INCIDENTS,
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
            type = INCIDENTS,
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
      internal fun `will check again in 10 second and reset even when previously started finishing up phase`(): Unit = runTest {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = INCIDENTS,
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
        whenever(incidentsMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`(): Unit = runTest {
        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = INCIDENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 9),
          ),
        )

        verify(queueService).purgeAllMessages(check { assertThat(it).isEqualTo(INCIDENTS) })

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
            type = INCIDENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(queueService, never()).purgeAllMessages(check { assertThat(it).isEqualTo(INCIDENTS) })
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
            type = INCIDENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 23,
            body = MigrationStatusCheck(checkCount = 10),
          ),
        )

        verify(telemetryClient).trackEvent(
          eq("incidents-migration-cancelled"),
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
        whenever(incidentsMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.cancelMigrateStatusCheck(
          MigrationContext(
            type = INCIDENTS,
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
  inner class MigrateIncidentsForPage {
    @BeforeEach
    internal fun setUp(): Unit = runTest {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(false)
      whenever(nomisApiService.getIncidentIds(any(), any(), any(), any())).thenReturn(
        pages(15),
      )
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`(): Unit = runTest {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = IncidentsMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
            ),
            pageKey = ByPageNumber(13),
            pageSize = 15,
          ),
        ),
      )

      verify(nomisApiService).getIncidentIds(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
        pageNumber = 13,
        pageSize = 15,
      )
    }

    @Test
    internal fun `will send MIGRATE_INCIDENT with context for each incident`(): Unit = runTest {
      service.migrateEntitiesForPage(
        MigrationContext(
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = IncidentsMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
            ),
            pageKey = ByPageNumber(13),
            pageSize = 15,
          ),
        ),
      )

      verify(queueService, times(15)).sendMessageNoTracing(
        message = eq(MIGRATE_ENTITY),
        context = check<MigrationContext<IncidentsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will send MIGRATE_INCIDENT with bookingId for each incident`(): Unit = runTest {
      val context: KArgumentCaptor<MigrationContext<IncidentIdResponse>> = argumentCaptor()

      whenever(nomisApiService.getIncidentIds(any(), any(), any(), any())).thenReturn(
        pages(
          15,
          startId = 1000,
        ),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = IncidentsMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
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
      val allContexts: List<MigrationContext<IncidentIdResponse>> = context.allValues

      val (firstPage, secondPage, thirdPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.incidentId).isEqualTo(1000)
      assertThat(secondPage.body.incidentId).isEqualTo(1001)
      assertThat(thirdPage.body.incidentId).isEqualTo(1002)
      assertThat(lastPage.body.incidentId).isEqualTo(1014)
    }

    @Test
    internal fun `will not send MIGRATE_INCIDENT when cancelling`(): Unit = runTest {
      whenever(migrationHistoryService.isCancelling(any())).thenReturn(true)

      whenever(nomisApiService.getIncidentIds(any(), any(), any(), any())).thenReturn(
        pages(
          15,
          startId = 1000,
        ),
      )

      service.migrateEntitiesForPage(
        MigrationContext(
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = MigrationPage(
            filter = IncidentsMigrationFilter(
              fromDate = LocalDate.parse("2020-01-01"),
              toDate = LocalDate.parse("2020-01-02"),
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
  @DisplayName("migrateIncidents")
  inner class MigrateIncident {

    @BeforeEach
    internal fun setUp(): Unit = runTest {
      whenever(incidentsMappingService.findByNomisId(any())).thenReturn(null)
      whenever(nomisApiService.getIncident(any())).thenReturn(aNomisIncidentResponse())
      whenever(incidentsService.upsertIncident(any())).thenReturn(IncidentsApiMockServer.dpsIncidentReportId(DPS_INCIDENT_ID))
      whenever(incidentsMappingService.createMapping(any(), any())).thenReturn(CreateMappingResult())
    }

    @Test
    internal fun `will retrieve an incident from NOMIS`(): Unit = runTest {
      service.migrateNomisEntity(
        MigrationContext(
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncidentIdResponse(NOMIS_INCIDENT_ID),
        ),
      )

      verify(nomisApiService).getIncident(NOMIS_INCIDENT_ID)
    }

    @Test
    internal fun `will transform and send that incident to the incidents api service`(): Unit = runTest {
      whenever(nomisApiService.getIncident(any())).thenReturn(aNomisIncidentResponse())

      service.migrateNomisEntity(
        MigrationContext(
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncidentIdResponse(NOMIS_INCIDENT_ID),
        ),
      )

      verify(incidentsService).upsertIncident(
        eq(aMigrationRequest()),
      )
    }

    @Test
    internal fun `will add telemetry events`(): Unit = runTest {
      whenever(nomisApiService.getIncident(any())).thenReturn(aNomisIncidentResponse())

      service.migrateNomisEntity(
        MigrationContext(
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncidentIdResponse(NOMIS_INCIDENT_ID),
        ),
      )

      verify(telemetryClient, times(1)).trackEvent(
        eq("incidents-migration-entity-migrated"),
        check {
          assertThat(it["nomisIncidentId"]).isNotNull
          assertThat(it["dpsIncidentId"]).isNotNull
        },
        isNull(),
      )
    }

    @Test
    internal fun `will create a mapping between a new incident and a NOMIS incident`(): Unit = runTest {
      whenever(nomisApiService.getIncident(any())).thenReturn(aNomisIncidentResponse())
      whenever(incidentsService.upsertIncident(any())).thenReturn(IncidentsApiMockServer.dpsIncidentReportId(DPS_INCIDENT_ID))

      service.migrateNomisEntity(
        MigrationContext(
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncidentIdResponse(NOMIS_INCIDENT_ID),
        ),
      )

      verify(incidentsMappingService).createMapping(
        IncidentMappingDto(
          dpsIncidentId = DPS_INCIDENT_ID,
          nomisIncidentId = NOMIS_INCIDENT_ID,
          label = "2020-05-23T11:30:00",
          mappingType = MappingType.MIGRATED,
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<IncidentMappingDto>>() {},
      )
    }

    @Test
    internal fun `will not throw an exception (and place message back on queue) but create a new retry message`(): Unit = runTest {
      whenever(nomisApiService.getIncident(any())).thenReturn(aNomisIncidentResponse())
      whenever(incidentsService.upsertIncident(any())).thenReturn(IncidentsApiMockServer.dpsIncidentReportId(DPS_INCIDENT_ID))

      whenever(
        incidentsMappingService.createMapping(
          any(),
          eq(object : ParameterizedTypeReference<DuplicateErrorResponse<IncidentMappingDto>>() {}),
        ),
      ).thenThrow(
        RuntimeException("something went wrong"),
      )

      service.migrateNomisEntity(
        MigrationContext(
          type = INCIDENTS,
          migrationId = "2020-05-23T11:30:00",
          estimatedCount = 100_200,
          body = IncidentIdResponse(NOMIS_INCIDENT_ID),
        ),
      )

      verify(queueService).sendMessage(
        message = eq(RETRY_MIGRATION_MAPPING),
        context = check<MigrationContext<IncidentMappingDto>> {
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.nomisIncidentId).isEqualTo(NOMIS_INCIDENT_ID)
          assertThat(it.body.dpsIncidentId).isEqualTo(DPS_INCIDENT_ID)
        },
        delaySeconds = eq(0),
      )
    }

    @Test
    internal fun `will throw after an error from the incidents api service so the message is rejected and retried`(): Unit = runBlocking {
      whenever(nomisApiService.getIncident(any())).thenReturn(aNomisIncidentResponse())
      whenever(incidentsService.upsertIncident(any())).thenThrow(WebClientResponseException.create(HttpStatus.BAD_GATEWAY, "error", HttpHeaders.EMPTY, ByteArray(0), null, null))

      assertThrows<WebClientResponseException.BadGateway> {
        service.migrateNomisEntity(
          MigrationContext(
            type = INCIDENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = IncidentIdResponse(123),
          ),
        )
      }

      verify(telemetryClient).trackEvent(
        eq("incidents-migration-entity-migration-failed"),
        check<Map<String, String>> {
          assertThat(it["nomisIncidentId"]).isEqualTo("123")
          assertThat(it["reason"]).contains("BadGateway")
          assertThat(it["migrationId"]).isEqualTo("2020-05-23T11:30:00")
        },
        isNull(),
      )

      verifyNoInteractions(queueService)
    }

    @Test
    internal fun `will throw after an error retrieving the Nomis entity so the message is rejected and retried`(): Unit = runBlocking {
      whenever(nomisApiService.getIncident(any())).thenThrow(WebClientResponseException.create(HttpStatus.BAD_GATEWAY, "error", HttpHeaders.EMPTY, ByteArray(0), null, null))

      assertThrows<WebClientResponseException.BadGateway> {
        service.migrateNomisEntity(
          MigrationContext(
            type = INCIDENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 7,
            body = IncidentIdResponse(123),
          ),
        )
      }

      verifyNoInteractions(queueService)
      verify(telemetryClient).trackEvent(
        eq("incidents-migration-entity-migration-failed"),
        check<Map<String, String>> {
          assertThat(it["nomisIncidentId"]).isEqualTo("123")
          assertThat(it["reason"]).contains("BadGateway")
          assertThat(it["migrationId"]).isEqualTo("2020-05-23T11:30:00")
        },
        isNull(),
      )
    }

    @Nested
    inner class WhenMigratedAlready {
      @BeforeEach
      internal fun setUp(): Unit = runTest {
        whenever(incidentsMappingService.findByNomisId(any())).thenReturn(
          IncidentMappingDto(
            dpsIncidentId = DPS_INCIDENT_ID,
            nomisIncidentId = NOMIS_INCIDENT_ID,
            mappingType = MappingType.NOMIS_CREATED,
          ),
        )
      }

      @Test
      internal fun `will do nothing`(): Unit = runTest {
        service.migrateNomisEntity(
          MigrationContext(
            type = INCIDENTS,
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = IncidentIdResponse(NOMIS_INCIDENT_ID),
          ),
        )

        verifyNoInteractions(incidentsService)
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
          migrationType = INCIDENTS,
          estimatedRecordCount = 100,
        ),
      )

      service.cancel("123-2020-01-01")

      verify(auditService).sendAuditEvent(
        eq("MIGRATION_CANCEL_REQUESTED"),
        check {
          it as Map<*, *>
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["migrationType"]).isEqualTo(INCIDENTS.name)
        },
      )
    }
  }
}

fun aMigrationRequest() = NomisSyncRequest(
  id = null,
  initialMigration = true,
  incidentReport = NomisReport(
    incidentId = NOMIS_INCIDENT_ID,
    questionnaireId = 543,
    title = "There was a fight",
    description = "On 12/04/2023 approx 16:45 John Smith punched Fred Jones",
    status = NomisStatus(code = "AWAN", description = "Awaiting Analysis"),
    type = "ASSAULT",
    prison = NomisCode(code = "BXI", description = "Brixton"),
    lockedResponse = false,
    incidentDateTime = LocalDateTime.parse("2023-04-12T16:45:00"),
    reportedDateTime = LocalDateTime.parse("2023-04-14T17:55:00"),
    reportingStaff = NomisStaff(username = "BQL16C", staffId = 16288, firstName = "JANE", lastName = "BAKER"),
    history = listOf(),
    offenderParties = listOf(),
    staffParties = listOf(),
    questions = listOf(),
    requirements = listOf(),
    followUpDate = LocalDate.parse("2023-05-16"),
    createdBy = "JSMITH",
    createDateTime = LocalDateTime.parse("2024-07-15T18:35:00"),
    descriptionParts = PairStringListDescriptionAddendum("first", listOf()),
  ),
)

fun aNomisIncidentResponse() = IncidentResponse(
  incidentId = NOMIS_INCIDENT_ID,
  questionnaireId = 543,
  title = "There was a fight",
  description = "On 12/04/2023 approx 16:45 John Smith punched Fred Jones",
  status = IncidentStatus(code = "AWAN", description = "Awaiting Analysis", standardUser = true, enhancedUser = false),
  type = "ASSAULT",
  agency = CodeDescription(code = "BXI", description = "Brixton"),
  lockedResponse = false,
  incidentDateTime = LocalDateTime.parse("2023-04-12T16:45:00"),
  reportedDateTime = LocalDateTime.parse("2023-04-14T17:55:00"),
  reportingStaff = Staff(username = "BQL16C", staffId = 16288, firstName = "JANE", lastName = "BAKER"),
  history = listOf(),
  offenderParties = listOf(),
  staffParties = listOf(),
  questions = listOf(),
  requirements = listOf(),
  followUpDate = LocalDate.parse("2023-05-16"),
  createdBy = "JSMITH",
  createDateTime = LocalDateTime.parse("2024-07-15T18:35:00"),
)

fun pages(total: Long, startId: Long = 1): PageImpl<IncidentIdResponse> = PageImpl<IncidentIdResponse>(
  (startId..total - 1 + startId).map { IncidentIdResponse(it) },
  Pageable.ofSize(10),
  total,
)
