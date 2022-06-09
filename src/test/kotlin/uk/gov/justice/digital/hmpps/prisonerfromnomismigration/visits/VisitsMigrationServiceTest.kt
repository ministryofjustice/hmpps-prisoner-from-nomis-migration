package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.microsoft.applicationinsights.TelemetryClient
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
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISIT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.RETRY_VISIT_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisCodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisLeadVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisVisit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VisitId
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
internal class VisitsMigrationServiceTest {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val visitMappingService: VisitMappingService = mock()
  private val visitsService: VisitsService = mock()
  private val migrationHistoryService: MigrationHistoryService = mock()
  private val telemetryClient: TelemetryClient = mock()

  val service = VisitsMigrationService(
    nomisApiService = nomisApiService,
    queueService = queueService,
    visitMappingService = visitMappingService,
    visitsService = visitsService,
    migrationHistoryService = migrationHistoryService,
    telemetryClient = telemetryClient,
    pageSize = 200
  )

  @Nested
  @DisplayName("migrateVisits")
  inner class MigrateVisits {
    @BeforeEach
    internal fun setUp() {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any())).thenReturn(
        pages(1)
      )
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`() {
      service.migrateVisits(
        VisitsMigrationFilter(
          prisonIds = listOf("LEI", "BXI"),
          visitTypes = listOf("SCON"),
          fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
          toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
        )
      )

      verify(nomisApiService).getVisits(
        prisonIds = listOf("LEI", "BXI"),
        visitTypes = listOf("SCON"),
        fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
        toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
        ignoreMissingRoom = false,
        pageNumber = 0,
        pageSize = 1
      )
    }

    @Test
    internal fun `will pass visit count and filter to queue`() {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any())).thenReturn(
        pages(23)
      )
      service.migrateVisits(
        VisitsMigrationFilter(
          prisonIds = listOf("LEI"),
          visitTypes = listOf("SCON"),
          fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
          toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
        )
      )

      verify(queueService).sendMessage(
        message = eq(MIGRATE_VISITS),
        context = check<MigrationContext<VisitsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(23)
          assertThat(it.body.prisonIds).containsExactly("LEI")
          assertThat(it.body.visitTypes).containsExactly("SCON")
          assertThat(it.body.fromDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T00:00:00"))
          assertThat(it.body.toDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T23:00:00"))
        },
        delaySeconds = eq(0)
      )
    }

    @Test
    internal fun `will write migration history record`() {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any())).thenReturn(
        pages(23)
      )
      service.migrateVisits(
        VisitsMigrationFilter(
          prisonIds = listOf("LEI", "BXI"),
          visitTypes = listOf("SCON"),
          fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
          toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
        )
      )

      verify(migrationHistoryService).recordMigrationStarted(
        migrationId = any(),
        migrationType = eq(VISITS),
        estimatedRecordCount = eq(23),
        filter = check<VisitsMigrationFilter> {
          assertThat(it.prisonIds).containsExactly("LEI", "BXI")
          assertThat(it.visitTypes).containsExactly("SCON")
          assertThat(it.fromDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T00:00:00"))
          assertThat(it.toDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T23:00:00"))
        }
      )
    }

    @Test
    internal fun `will write analytic with estimated count and filter`() {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any())).thenReturn(
        pages(23)
      )
      service.migrateVisits(
        VisitsMigrationFilter(
          prisonIds = listOf("LEI", "BXI"),
          visitTypes = listOf("SCON"),
          fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
          toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
        )
      )

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-visits-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["prisonIds"]).isEqualTo("LEI, BXI")
          assertThat(it["visitTypes"]).isEqualTo("SCON")
          assertThat(it["fromDateTime"]).isEqualTo("2020-01-01T00:00:00")
          assertThat(it["toDateTime"]).isEqualTo("2020-01-02T23:00:00")
        },
        eq(null)
      )
    }

    @Test
    internal fun `will write analytics with empty filter`() {
      whenever(
        nomisApiService.getVisits(
          prisonIds = any(),
          visitTypes = any(),
          fromDateTime = isNull(),
          toDateTime = isNull(),
          ignoreMissingRoom = any(),
          pageNumber = any(),
          pageSize = any()
        )
      ).thenReturn(
        pages(23)
      )
      service.migrateVisits(
        VisitsMigrationFilter(visitTypes = listOf())
      )

      verify(telemetryClient).trackEvent(
        eq("nomis-migration-visits-started"),
        check {
          assertThat(it["migrationId"]).isNotNull
          assertThat(it["estimatedCount"]).isEqualTo("23")
          assertThat(it["prisonIds"]).isEqualTo("")
          assertThat(it["ignoreMissingRoom"]).isEqualTo("false")
          assertThat(it["visitTypes"]).isEqualTo("")
          assertThat(it["fromDateTime"]).isEqualTo("")
          assertThat(it["toDateTime"]).isEqualTo("")
        },
        eq(null)
      )
    }
  }

  @Nested
  @DisplayName("divideVisitsByPage")
  inner class DivideVisitsByPage {

    @BeforeEach
    internal fun setUp() {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any())).thenReturn(
        pages(100_200)
      )
    }

    @Test
    internal fun `will send a page message for every page (200) of visits `() {
      service.divideVisitsByPage(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = VisitsMigrationFilter(
            prisonIds = listOf("LEI", "BXI"),
            visitTypes = listOf("SCON"),
            fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
            toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
          )
        )
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        eq(MIGRATE_VISITS_BY_PAGE), any(), delaySeconds = eq(0)
      )
    }

    @Test
    internal fun `will also send a single MIGRATION_STATUS_CHECK message`() {
      service.divideVisitsByPage(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = VisitsMigrationFilter(
            prisonIds = listOf("LEI", "BXI"),
            visitTypes = listOf("SCON"),
            fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
            toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
          )
        )
      )

      verify(queueService).sendMessage(
        eq(MIGRATE_VISITS_STATUS_CHECK), any(), any()
      )
    }

    @Test
    internal fun `each page with have the filter and context attached`() {
      service.divideVisitsByPage(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = VisitsMigrationFilter(
            prisonIds = listOf("LEI", "BXI"),
            visitTypes = listOf("SCON"),
            fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
            toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
          )
        )
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        message = eq(MIGRATE_VISITS_BY_PAGE),
        context = check<MigrationContext<VisitsPage>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.filter.prisonIds).containsExactly("LEI", "BXI")
          assertThat(it.body.filter.visitTypes).containsExactly("SCON")
          assertThat(it.body.filter.fromDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T00:00:00"))
          assertThat(it.body.filter.toDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T23:00:00"))
        },
        delaySeconds = eq(0)
      )
    }

    @Test
    internal fun `each page will contain page number and page size`() {
      val context: KArgumentCaptor<MigrationContext<VisitsPage>> = argumentCaptor()

      service.divideVisitsByPage(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = VisitsMigrationFilter(
            prisonIds = listOf("LEI", "BXI"),
            visitTypes = listOf("SCON"),
            fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
            toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
          )
        )
      )

      verify(queueService, times(100_200 / 200)).sendMessage(
        eq(MIGRATE_VISITS_BY_PAGE), context.capture(), delaySeconds = eq(0)
      )
      val allContexts: List<MigrationContext<VisitsPage>> = context.allValues

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
  @DisplayName("migrateVisitsStatusCheck")
  inner class MigrateVisitsStatusCheck {
    @Nested
    @DisplayName("when there are still messages on the queue")
    inner class MessagesOnQueue {
      @BeforeEach
      internal fun setUp() {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed()).thenReturn(true)
      }

      @Test
      internal fun `will check again in 10 seconds`() {
        service.migrateVisitsStatusCheck(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitMigrationStatusCheck()
          )
        )

        verify(queueService).sendMessage(
          eq(MIGRATE_VISITS_STATUS_CHECK), any(), eq(10)
        )
      }

      @Test
      internal fun `will check again in 10 second and reset even when previously started finishing up phase`() {
        service.migrateVisitsStatusCheck(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitMigrationStatusCheck(checkCount = 4)
          )
        )

        verify(queueService).sendMessage(
          message = eq(MIGRATE_VISITS_STATUS_CHECK),
          context = check<MigrationContext<VisitMigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(0)
          },
          delaySeconds = eq(10)
        )
      }
    }

    @Nested
    @DisplayName("when there are no messages on the queue")
    inner class NoMessagesOnQueue {
      @BeforeEach
      internal fun setUp() {
        whenever(queueService.isItProbableThatThereAreStillMessagesToBeProcessed()).thenReturn(false)
        whenever(queueService.countMessagesThatHaveFailed()).thenReturn(0)
        whenever(visitMappingService.getMigrationCount(any())).thenReturn(0)
      }

      @Test
      internal fun `will increment check count and try again a second when only checked 9 times`() {
        service.migrateVisitsStatusCheck(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitMigrationStatusCheck(checkCount = 9)
          )
        )

        verify(queueService).sendMessage(
          message = eq(MIGRATE_VISITS_STATUS_CHECK),
          context = check<MigrationContext<VisitMigrationStatusCheck>> {
            assertThat(it.body.checkCount).isEqualTo(10)
          },
          delaySeconds = eq(1)
        )
      }

      @Test
      internal fun `will finish off when checked 10 times previously`() {
        service.migrateVisitsStatusCheck(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00",
            estimatedCount = 100_200,
            body = VisitMigrationStatusCheck(checkCount = 10)
          )
        )

        verify(queueService, never()).sendMessage(
          message = eq(MIGRATE_VISITS_STATUS_CHECK), context = any(), delaySeconds = any()
        )
      }

      @Test
      internal fun `will add completed telemetry when finishing off`() {
        service.migrateVisitsStatusCheck(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 23, body = VisitMigrationStatusCheck(checkCount = 10)
          )
        )

        verify(telemetryClient).trackEvent(
          eq("nomis-migration-visits-completed"),
          check {
            assertThat(it["migrationId"]).isNotNull
            assertThat(it["estimatedCount"]).isEqualTo("23")
            assertThat(it["durationMinutes"]).isNotNull()
          },
          eq(null)
        )
      }

      @Test
      internal fun `will update migration history record when finishing off`() {
        whenever(queueService.countMessagesThatHaveFailed()).thenReturn(2)
        whenever(visitMappingService.getMigrationCount("2020-05-23T11:30:00")).thenReturn(21)

        service.migrateVisitsStatusCheck(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 23, body = VisitMigrationStatusCheck(checkCount = 10)
          )
        )

        verify(migrationHistoryService).recordMigrationCompleted(
          migrationId = eq("2020-05-23T11:30:00"), recordsFailed = eq(2), recordsMigrated = eq(21)
        )
      }
    }
  }

  @Nested
  @DisplayName("migrateVisitsForPage")
  inner class MigrateVisitsForPage {
    @BeforeEach
    internal fun setUp() {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any())).thenReturn(
        pages(15)
      )
    }

    @Test
    internal fun `will pass filter through to get total count along with a tiny page count`() {
      service.migrateVisitsForPage(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = VisitsPage(
            filter = VisitsMigrationFilter(
              prisonIds = listOf("LEI", "BXI"),
              visitTypes = listOf("SCON"),
              ignoreMissingRoom = true,
              fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
              toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
            ),
            pageNumber = 13, pageSize = 15
          )
        )
      )

      verify(nomisApiService).getVisits(
        prisonIds = listOf("LEI", "BXI"),
        visitTypes = listOf("SCON"),
        fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
        toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
        ignoreMissingRoom = true,
        pageNumber = 13,
        pageSize = 15
      )
    }

    @Test
    internal fun `will send MIGRATE_VISIT with context for each visit`() {
      service.migrateVisitsForPage(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = VisitsPage(
            filter = VisitsMigrationFilter(
              prisonIds = listOf("LEI", "BXI"),
              visitTypes = listOf("SCON"),
              fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
              toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
            ),
            pageNumber = 13, pageSize = 15
          )
        )
      )

      verify(queueService, times(15)).sendMessage(
        message = eq(MIGRATE_VISIT),
        context = check<MigrationContext<VisitsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
        },
        delaySeconds = eq(0)
      )
    }

    @Test
    internal fun `will send MIGRATE_VISIT with visitId for each visit`() {

      val context: KArgumentCaptor<MigrationContext<VisitId>> = argumentCaptor()

      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any(), any())).thenReturn(
        pages(
          15, startId = 1000
        )
      )

      service.migrateVisitsForPage(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200,
          body = VisitsPage(
            filter = VisitsMigrationFilter(
              prisonIds = listOf("LEI", "BXI"),
              visitTypes = listOf("SCON"),
              fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
              toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
            ),
            pageNumber = 13, pageSize = 15
          )
        )
      )

      verify(queueService, times(15)).sendMessage(
        eq(MIGRATE_VISIT), context.capture(), delaySeconds = eq(0)

      )
      val allContexts: List<MigrationContext<VisitId>> = context.allValues

      val (firstPage, secondPage, thirdPage) = allContexts
      val lastPage = allContexts.last()

      assertThat(firstPage.body.visitId).isEqualTo(1000)
      assertThat(secondPage.body.visitId).isEqualTo(1001)
      assertThat(thirdPage.body.visitId).isEqualTo(1002)
      assertThat(lastPage.body.visitId).isEqualTo(1014)
    }
  }

  @Nested
  @DisplayName("migrateVisit")
  inner class MigrateVisit {
    @BeforeEach
    internal fun setUp() {
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
            )
          ),
          visitType = NomisCodeDescription("SCON", "Social Contact"),
          visitStatus = NomisCodeDescription("CANC", "cancelled"),
          leadVisitor = NomisLeadVisitor(
            personId = 4729570, fullName = "Simon Mine", telephones = listOf("000 11111", "000 22222")
          ),
          visitOutcome = NomisCodeDescription(NomisCancellationOutcome.NO_ID.name, "No Id"),
          commentText = "This is a comment",
          visitorConcernText = "I'm concerned",
        )
      )
      whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
        RoomMapping(
          vsipId = "VSIP-ROOM-ID", isOpen = true
        )
      )

      whenever(visitsService.createVisit(any())).thenReturn(
        "654321"
      )
    }

    @Test
    internal fun `will retrieve visit from NOMIS`() {
      service.migrateVisit(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
        )
      )

      verify(nomisApiService).getVisit(123)
    }

    @Test
    internal fun `will retrieve room for NOMIS room id using internal agency description`() {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          prisonId = "BXI",
          agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
          prisonerId = "A1234AA"
        )
      )

      service.migrateVisit(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
        )
      )

      verify(visitMappingService).findRoomMapping(prisonId = "BXI", agencyInternalLocationCode = "MDI-VISITS-OFF_VIS")
    }

    @Test
    internal fun `when no room found, migration for this visit is abandoned`() {
      whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(null)

      assertThatThrownBy {
        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )
      }.isInstanceOf(NoRoomMappingFoundException::class.java)
    }

    @Test
    internal fun `when no room found in NOMIS, migration for this visit is abandoned`() {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          agencyInternalLocation = null,
        )
      )

      assertThatThrownBy {
        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )
      }.isInstanceOf(NoRoomMappingFoundException::class.java)
    }

    @Test
    internal fun `will create a visit in VSIP`() {
      service.migrateVisit(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
        )
      )

      verify(visitsService).createVisit(any())
    }

    @Nested
    inner class NomisToVsipMapping {
      @BeforeEach
      internal fun setUp() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
            prisonId = "BXI",
            prisonerId = "A1234AA",
            startDateTime = LocalDateTime.parse("2020-01-01T10:00:00"),
            endDateTime = LocalDateTime.parse("2020-01-02T12:00:00"),
          )
        )
        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID", isOpen = true
          )
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )
      }

      @Test
      internal fun `prisonId is copied`() {
        verify(visitsService).createVisit(
          check {
            assertThat(it.prisonId).isEqualTo("BXI")
          }
        )
      }

      @Test
      internal fun `prisonerId is copied`() {
        verify(visitsService).createVisit(
          check {
            assertThat(it.prisonerId).isEqualTo("A1234AA")
          }
        )
      }

      @Test
      internal fun `visit start date time is copied`() {
        verify(visitsService).createVisit(
          check {
            assertThat(it.startTimestamp).isEqualTo(LocalDateTime.parse("2020-01-01T10:00:00"))
          }
        )
      }

      @Test
      internal fun `visit end date time is copied`() {
        verify(visitsService).createVisit(
          check {
            assertThat(it.endTimestamp).isEqualTo(LocalDateTime.parse("2020-01-02T12:00:00"))
          }
        )
      }

      @Test
      internal fun `contact information is copied`() {
        verify(visitsService).createVisit(
          check {
            assertThat(it.visitContact).isEqualTo(VsipLegacyContactOnVisit("Vince Hoyland", "0000 11111"))
          }
        )
      }

      @Test
      internal fun `legacy lead visitor personId is copied`() {
        verify(visitsService).createVisit(
          check {
            assertThat(it.legacyData).isEqualTo(VsipLegacyData(4729570))
          }
        )
      }

      @Test
      internal fun `outcome status when null is copied`() {
        verify(visitsService).createVisit(
          check {
            assertThat(it.outcomeStatus).isNull()
          }
        )
      }

      @Test
      internal fun `comments are copied`() {
        verify(visitsService).createVisit(
          check {
            assertThat(it.visitNotes).extracting("text", "type").contains(
              tuple(
                "This is a comment", VsipVisitNoteType.VISIT_COMMENT
              ),
              tuple("this is concerning", VsipVisitNoteType.VISITOR_CONCERN)
            )
          }
        )
      }
    }

    @Nested
    @DisplayName("Visit restriction mapping (open/closed/unknown)")
    inner class NomisToVisitRestrictionMapping {
      @Test
      internal fun `visit restriction is set to UNKNOWN for historical visits (prior to today)`() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("VSIP-ROOM-ID", "An open room"),
            startDateTime = LocalDateTime.now().minusDays(1),
            endDateTime = LocalDateTime.now().minusDays(1).plusHours(1),
          )
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID", isOpen = true
          )
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitRestriction).isEqualTo(VisitRestriction.UNKNOWN)
          }
        )
      }

      @Test
      internal fun `visit restriction is set to correct value for a visit with today's date`() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("VSIP-ROOM-ID", "An open room"),
            startDateTime = LocalDateTime.now(),
            endDateTime = LocalDateTime.now().plusHours(1),
          )
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID", isOpen = true
          )
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitRestriction).isEqualTo(VisitRestriction.OPEN)
          }
        )
      }

      @Test
      internal fun `visit restriction is set to correct value for a visit booked with a future date`() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("VSIP-ROOM-ID", "A closed room"),
            startDateTime = LocalDateTime.now().plusDays(5),
            endDateTime = LocalDateTime.now().plusDays(5).plusHours(1),
          )
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID", isOpen = false
          )
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitRestriction).isEqualTo(VisitRestriction.CLOSED)
          }
        )
      }
    }

    @Nested
    @DisplayName("Visit room mapping (date dependent)")
    inner class NomisToVisitRoomMapping {
      @Test
      internal fun `visit room is set to the nomis description, ignoring the VSIP mapping for historical visits (prior to today)`() {
        val aVisit = aVisit(
          agencyInternalLocation = NomisCodeDescription("VSIP-ROOM-ID", "VSIP-ROOM-DESC"),
          startDateTime = LocalDateTime.now().minusDays(1),
          endDateTime = LocalDateTime.now().minusDays(1).plusHours(1),
        )
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID", isOpen = true
          )
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitRoom).isEqualTo(aVisit.agencyInternalLocation?.description)
          }
        )
      }

      @Test
      internal fun `visit room is set to correct value for a visit with today's date`() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("VSIP-ROOM-ID", "VSIP-ROOM-DESC"),
            startDateTime = LocalDateTime.now(),
            endDateTime = LocalDateTime.now().plusHours(1),
          )
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID", isOpen = true
          )
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitRoom).isEqualTo("VSIP-ROOM-ID")
          }
        )
      }

      @Test
      internal fun `visit room is set to correct value for a visit booked with a future date`() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            agencyInternalLocation = NomisCodeDescription("VSIP-ROOM-ID", "VSIP-ROOM-DESC"),
            startDateTime = LocalDateTime.now().plusDays(5),
            endDateTime = LocalDateTime.now().plusDays(5).plusHours(1),
          )
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID", isOpen = false
          )
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitRoom).isEqualTo("VSIP-ROOM-ID")
          }
        )
      }
    }

    @Nested
    @DisplayName("visit status mapping ** REQUIRES ACCEPTANCE CRITERIA - currently wrong")
    inner class NomisToVisitStatusMapping {
      @BeforeEach
      internal fun setUp() {
        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID", isOpen = true
          )
        )
      }

      @Test
      internal fun `cancelled is mapped to vsip cancelled status`() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            visitStatus = NomisCodeDescription("CANC", "Cancelled")
          )
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitStatus).isEqualTo(VsipStatus.CANCELLED)
          }
        )
      }

      @Test
      internal fun `all other statuses are mapped to booked`() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            visitStatus = NomisCodeDescription("SCH", "Scheduled")
          )
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitStatus).isEqualTo(VsipStatus.BOOKED)
          }
        )
      }

      @Test
      internal fun `even completed is mapped to booked`() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            visitStatus = NomisCodeDescription("COMP", "Completed")
          )
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitStatus).isEqualTo(VsipStatus.BOOKED)
          }
        )
      }
    }

    @Nested
    @DisplayName("visit type mapping")
    inner class NomisToVisitTypeMapping {
      @BeforeEach
      internal fun setUp() {
        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID", isOpen = true
          )
        )
      }

      @Test
      internal fun `social type is mapped to standard social`() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            visitType = NomisCodeDescription("SCON", "Social Contact")
          )
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitType).isEqualTo("SOCIAL")
          }
        )
      }

      @Test
      internal fun `official type is mapped to official`() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            visitType = NomisCodeDescription("OFFI", "Official Visit")
          )
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(visitsService).createVisit(
          check {
            assertThat(it.visitType).isEqualTo("OFFICIAL")
          }
        )
      }
    }

    @Test
    internal fun `will create mapping between VSIP and NOMIS visit`() {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          visitId = 123456
        )
      )
      whenever(visitsService.createVisit(any())).thenReturn(
        "654321"
      )

      service.migrateVisit(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123456)
        )
      )

      verify(visitMappingService).createNomisVisitMapping(123456, "654321", "2020-05-23T11:30:00")
    }

    @Test
    internal fun `will not throw exception (and place message back on queue) but create a new retry message`() {
      whenever(nomisApiService.getVisit(any())).thenReturn(
        aVisit(
          visitId = 123456
        )
      )
      whenever(visitsService.createVisit(any())).thenReturn(
        "654321"
      )

      whenever(visitMappingService.createNomisVisitMapping(any(), any(), any())).thenThrow(
        RuntimeException("something went wrong")
      )

      service.migrateVisit(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123456)
        )
      )

      verify(queueService).sendMessage(
        message = eq(RETRY_VISIT_MAPPING),
        context = check<MigrationContext<VisitMapping>> {
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.nomisVisitId).isEqualTo(123456)
          assertThat(it.body.vsipVisitId).isEqualTo("654321")
        },
        delaySeconds = eq(0)
      )
    }

    @Nested
    inner class Analytics {
      @Test
      internal fun `migration abandoned due to room mapping missing`() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            prisonId = "BXI",
            agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
            prisonerId = "A1234AA",
            visitId = 123456,
            startDateTime = LocalDateTime.parse("2018-05-23T11:30:00"),
          )
        )

        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(null)

        assertThatThrownBy {
          service.migrateVisit(
            MigrationContext(
              migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
            )
          )
        }

        verify(telemetryClient).trackEvent(
          eq("nomis-migration-visit-no-room-mapping"),
          check {
            assertThat(it["migrationId"]).isEqualTo("2020-05-23T11:30:00")
            assertThat(it["prisonId"]).isEqualTo("BXI")
            assertThat(it["offenderNo"]).isEqualTo("A1234AA")
            assertThat(it["visitId"]).isEqualTo("123456")
            assertThat(it["startDateTime"]).isEqualTo("2018-05-23T11:30:00")
            assertThat(it["agencyInternalLocation"]).isEqualTo("MDI-VISITS-OFF_VIS")
          },
          eq(null)
        )
      }

      @Test
      internal fun `when successfully migrated`() {
        whenever(nomisApiService.getVisit(any())).thenReturn(
          aVisit(
            prisonId = "BXI",
            agencyInternalLocation = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
            prisonerId = "A1234AA",
            visitId = 123456,
            startDateTime = LocalDateTime.parse("2018-05-23T11:30:00"),
          )
        )
        whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
          RoomMapping(
            vsipId = "VSIP-ROOM-ID", isOpen = true
          )
        )

        whenever(visitsService.createVisit(any())).thenReturn(
          "654321"
        )

        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(telemetryClient).trackEvent(
          eq("nomis-migration-visit-migrated"),
          check {
            assertThat(it["migrationId"]).isEqualTo("2020-05-23T11:30:00")
            assertThat(it["prisonId"]).isEqualTo("BXI")
            assertThat(it["offenderNo"]).isEqualTo("A1234AA")
            assertThat(it["visitId"]).isEqualTo("123456")
            assertThat(it["vsipVisitId"]).isEqualTo("654321")
            assertThat(it["startDateTime"]).isEqualTo("2018-05-23T11:30:00")
            assertThat(it["room"]).isEqualTo("VSIP-ROOM-ID")
          },
          eq(null)
        )
      }
    }

    @Nested
    inner class WhenMigratedAlready {
      @BeforeEach
      internal fun setUp() {
        whenever(visitMappingService.findNomisVisitMapping(any())).thenReturn(
          VisitNomisMapping(
            nomisId = 123, vsipId = "456", label = "2020-01-01T00:00:00", mappingType = "MIGRATED"
          )
        )
      }

      @Test
      internal fun `will do nothing`() {
        service.migrateVisit(
          MigrationContext(
            migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123)
          )
        )

        verify(visitMappingService, never()).createNomisVisitMapping(any(), any(), any())
      }
    }
  }
}

fun pages(total: Long, startId: Long = 1): PageImpl<VisitId> = PageImpl<VisitId>(
  (startId..total - 1 + startId).map { VisitId(it) }, Pageable.ofSize(10), total
  )

  fun aVisit(
    prisonId: String = "BXI",
    agencyInternalLocation: NomisCodeDescription? = NomisCodeDescription("OFF_VIS", "MDI-VISITS-OFF_VIS"),
    prisonerId: String = "A1234AA",
    startDateTime: LocalDateTime = LocalDateTime.parse("2020-01-01T10:00:00"),
    endDateTime: LocalDateTime = LocalDateTime.parse("2020-01-02T12:00:00"),
    visitType: NomisCodeDescription = NomisCodeDescription("SCON", "Social Contact"),
    visitStatus: NomisCodeDescription = NomisCodeDescription("SCH", "Scheduled"),
    visitId: Long = 123
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
      )
    ),
    visitType = visitType,
    visitStatus = visitStatus,
    commentText = "This is a comment",
    visitorConcernText = "this is concerning",
    /* no outcome as only visits with a status of Cancelled will have an outcome returned from nomis */
    leadVisitor = NomisLeadVisitor(4729570, fullName = "Vince Hoyland", telephones = listOf("0000 11111", "0000 22222"))
  )
  