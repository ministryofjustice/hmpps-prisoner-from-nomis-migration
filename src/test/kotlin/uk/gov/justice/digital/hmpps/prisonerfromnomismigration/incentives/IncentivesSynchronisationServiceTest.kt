package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.IncentiveUpsertedOffenderEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisCodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisIncentive
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
internal class IncentiveSynchronisationServiceTest {
  private val nomisApiService: NomisApiService = mock()
  private val queueService: MigrationQueueService = mock()
  private val incentivesMappingService: IncentiveMappingService = mock()
  private val incentivesService: IncentivesService = mock()

  private val telemetryClient: TelemetryClient = mock()
  val service = IncentivesSynchronisationService(
    nomisApiService = nomisApiService,
    queueService = queueService,
    mappingService = incentivesMappingService,
    incentiveService = incentivesService,
    telemetryClient = telemetryClient
  )

  @Test
  internal fun `will update an incentive`(): Unit = runBlocking {
    whenever(incentivesMappingService.findNomisIncentiveMapping(any(), any())).thenReturn(
      IncentiveNomisMapping(
        nomisBookingId = 1000L,
        nomisIncentiveSequence = 1L,
        incentiveId = 111L,
        mappingType = "NOMIS_CREATED"
      )
    )

    runBlocking {
      whenever(nomisApiService.getIncentive(any(), any())).thenReturn(
        NomisIncentive(
          bookingId = 1000,
          incentiveSequence = 1,
          commentText = "Doing well",
          iepDateTime = LocalDateTime.parse("2020-01-01T00:00:00"),
          prisonId = "HEI",
          iepLevel = NomisCodeDescription("ENH", "Enhanced"),
          userId = "JANE_SMITH",
          currentIep = true,
          offenderNo = "A1234AA",
          whenCreated = LocalDateTime.parse("2020-01-01T00:00:55"),
        )
      )

      service.synchroniseIncentive(anIncentiveEvent())

      verify(incentivesService).synchroniseUpdateIncentive(
        1000,
        111,
        UpdateIncentiveIEP(iepTime = LocalDateTime.parse("2020-01-01T00:00:55"), comment = "Doing well", current = true)
      )
    }
  }

  @Test
  internal fun `will create an incentive time with seconds portion from the when created timestamp when IEP minute equals the when created minute`(): Unit = runBlocking {
    whenever(incentivesMappingService.findNomisIncentiveMapping(any(), any())).thenReturn(null)

    runBlocking {
      whenever(nomisApiService.getIncentive(any(), any())).thenReturn(
        NomisIncentive(
          bookingId = 1000,
          incentiveSequence = 1,
          commentText = "Doing well",
          iepDateTime = LocalDateTime.parse("2020-01-01T12:20:00"),
          prisonId = "HEI",
          iepLevel = NomisCodeDescription("ENH", "Enhanced"),
          userId = "JANE_SMITH",
          currentIep = true,
          offenderNo = "A1234AA",
          whenCreated = LocalDateTime.parse("2020-01-01T12:20:10"),
        )
      )
      whenever(incentivesService.synchroniseCreateIncentive(any(), any())).thenReturn(CreateIncentiveIEPResponse(999L))

      service.synchroniseIncentive(anIncentiveEvent())

      verify(incentivesService).synchroniseCreateIncentive(
        CreateIncentiveIEP(
          prisonId = "HEI",
          iepTime = LocalDateTime.parse("2020-01-01T12:20:10"),
          comment = "Doing well",
          current = true,
          userId = "JANE_SMITH",
          iepLevel = "ENH",
          reviewType = ReviewType.REVIEW
        ),
        1000
      )
    }
  }

  @Test
  internal fun `will create an incentive time with a seconds portion hardcoded to 59 when created timestamp is not in the same minute as the iep time`(): Unit = runBlocking {
    whenever(incentivesMappingService.findNomisIncentiveMapping(any(), any())).thenReturn(null)

    runBlocking {
      whenever(nomisApiService.getIncentive(any(), any())).thenReturn(
        NomisIncentive(
          bookingId = 1000,
          incentiveSequence = 1,
          commentText = "Doing well",
          iepDateTime = LocalDateTime.parse("2020-01-01T12:20:45"),
          prisonId = "HEI",
          iepLevel = NomisCodeDescription("ENH", "Enhanced"),
          userId = "JANE_SMITH",
          currentIep = true,
          offenderNo = "A1234AA",
          whenCreated = LocalDateTime.parse("2020-01-01T12:21:10"),
        )
      )
      whenever(incentivesService.synchroniseCreateIncentive(any(), any())).thenReturn(CreateIncentiveIEPResponse(999L))

      service.synchroniseIncentive(anIncentiveEvent())

      verify(incentivesService).synchroniseCreateIncentive(
        CreateIncentiveIEP(
          prisonId = "HEI",
          iepTime = LocalDateTime.parse("2020-01-01T12:20:59"),
          comment = "Doing well",
          current = true,
          userId = "JANE_SMITH",
          iepLevel = "ENH",
          reviewType = ReviewType.REVIEW
        ),
        1000
      )
    }
  }
}

fun anIncentiveEvent(
  offenderIdDisplay: String = "A1234AA",
  bookingId: Long = 1000,
  iepSeq: Long = 1,
  auditModuleName: String = "NOMIS_SOMEWHERE"
) = IncentiveUpsertedOffenderEvent(
  offenderIdDisplay = offenderIdDisplay,
  bookingId = bookingId,
  iepSeq = iepSeq,
  auditModuleName = auditModuleName
)
