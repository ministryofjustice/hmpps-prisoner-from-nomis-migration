package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISIT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisCodeDescription
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

  val service = VisitsMigrationService(
    nomisApiService = nomisApiService,
    queueService = queueService,
    visitMappingService = visitMappingService,
    visitsService = visitsService,
    pageSize = 200
  )

  @Nested
  @DisplayName("migrateVisits")
  inner class MigrateVisits {
    @BeforeEach
    internal fun setUp() {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any())).thenReturn(
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
        listOf("LEI", "BXI"),
        listOf("SCON"),
        LocalDateTime.parse("2020-01-01T00:00:00"),
        LocalDateTime.parse("2020-01-02T23:00:00"),
        0,
        1
      )
    }

    @Test
    internal fun `will pass visit count and filter to queue`() {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any())).thenReturn(
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
        eq(MIGRATE_VISITS),
        check<MigrationContext<VisitsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(23)
          assertThat(it.body.prisonIds).containsExactly("LEI")
          assertThat(it.body.visitTypes).containsExactly("SCON")
          assertThat(it.body.fromDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T00:00:00"))
          assertThat(it.body.toDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T23:00:00"))
        }
      )
    }
  }

  @Nested
  @DisplayName("divideVisitsByPage")
  inner class DivideVisitsByPage {

    @BeforeEach
    internal fun setUp() {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any())).thenReturn(
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
        eq(MIGRATE_VISITS_BY_PAGE), any()
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
        eq(MIGRATE_VISITS_BY_PAGE),
        check<MigrationContext<VisitsPage>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
          assertThat(it.body.filter.prisonIds).containsExactly("LEI", "BXI")
          assertThat(it.body.filter.visitTypes).containsExactly("SCON")
          assertThat(it.body.filter.fromDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T00:00:00"))
          assertThat(it.body.filter.toDateTime).isEqualTo(LocalDateTime.parse("2020-01-02T23:00:00"))
        }
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
        any(), context.capture()
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
  @DisplayName("migrateVisitsForPage")
  inner class MigrateVisitsForPage {
    @BeforeEach
    internal fun setUp() {
      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any())).thenReturn(
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
              fromDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
              toDateTime = LocalDateTime.parse("2020-01-02T23:00:00"),
            ),
            pageNumber = 13, pageSize = 15
          )
        )
      )

      verify(nomisApiService).getVisits(
        listOf("LEI", "BXI"),
        listOf("SCON"),
        LocalDateTime.parse("2020-01-01T00:00:00"),
        LocalDateTime.parse("2020-01-02T23:00:00"),
        13,
        15
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
        eq(MIGRATE_VISIT),
        check<MigrationContext<VisitsMigrationFilter>> {
          assertThat(it.estimatedCount).isEqualTo(100_200)
          assertThat(it.migrationId).isEqualTo("2020-05-23T11:30:00")
        }
      )
    }

    @Test
    internal fun `will send MIGRATE_VISIT with visitId for each visit`() {

      val context: KArgumentCaptor<MigrationContext<VisitId>> = argumentCaptor()

      whenever(nomisApiService.getVisits(any(), any(), any(), any(), any(), any())).thenReturn(
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
        eq(MIGRATE_VISIT), context.capture()
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
              leadVisitor = true,
            ),
            NomisVisitor(
              personId = 4729580,
              leadVisitor = false,
            )
          ),
          visitType = NomisCodeDescription("SCON", "Social Contact"),
          visitStatus = NomisCodeDescription("SCH", "Scheduled"),
          commentText = "This is a comment",
        )
      )
      whenever(visitMappingService.findRoomMapping(any(), any())).thenReturn(
        RoomMapping(
          vsipId = "VSIP-ROOM-ID", isOpen = true
        )
      )

      whenever(visitsService.createVisit(any())).thenReturn(
        VsipVisit(visitId = "654321")
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
      internal fun `room from room mapping is copied`() {
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
      internal fun `cancelled is mapped to cancelled by prison (WHICH IS PROABLY WRONG)`() {
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
            assertThat(it.visitStatus).isEqualTo("CANCELLED_BY_PRISON")
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
            assertThat(it.visitStatus).isEqualTo("BOOKED")
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
            assertThat(it.visitStatus).isEqualTo("BOOKED")
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
            assertThat(it.visitType).isEqualTo("STANDARD_SOCIAL")
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
        VsipVisit(visitId = "654321")
      )

      service.migrateVisit(
        MigrationContext(
          migrationId = "2020-05-23T11:30:00", estimatedCount = 100_200, body = VisitId(123456)
        )
      )

      verify(visitMappingService).createNomisVisitMapping(123456, "654321", "2020-05-23T11:30:00")
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
        leadVisitor = true,
      ),
      NomisVisitor(
        personId = 4729580,
        leadVisitor = false,
      )
    ),
    visitType = visitType,
    visitStatus = visitStatus,
    commentText = "This is a comment",
  )
  