package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

class PrisonPersonMigrationServiceTest {
  private val queueService = mock<MigrationQueueService>()
  private val prisonPersonNomisApiService = mock<PrisonPersonNomisApiService>()
  private val nomisService = mock<NomisApiService>()
  private val prisonPersonMappingService = mock<PrisonPersonMappingApiService>()
  private val prisonPersonDpsService = mock<PrisonPersonDpsApiService>()
  private val migrationHistoryService = mock<MigrationHistoryService>()
  private val telemetryClient = mock<TelemetryClient>()
  private val auditService = mock<AuditService>()
  private val service = PrisonPersonMigrationService(
    queueService,
    prisonPersonNomisApiService,
    nomisService,
    prisonPersonMappingService,
    prisonPersonDpsService,
    migrationHistoryService,
    telemetryClient,
    auditService,
    1000,
    1,
    1,
  )

  @Nested
  inner class GetIds {
    @Test
    fun `will not call prison API for a filter on prisoner number`() = runTest {
      service.getIds(PrisonPersonMigrationFilter("A1234BC"), 1000, 0)

      verify(nomisService, never()).getPrisonerIds(anyLong(), anyLong())
    }

    @Test
    fun `will get all IDs when there is no prisoner number`() = runTest {
      service.getIds(PrisonPersonMigrationFilter(null), 1000, 0)

      verify(nomisService).getPrisonerIds(anyLong(), anyLong())
    }

    @Test
    fun `will get all IDs when the prisoner number is empty`() = runTest {
      service.getIds(PrisonPersonMigrationFilter(""), 1000, 0)

      verify(nomisService).getPrisonerIds(anyLong(), anyLong())
    }
  }
}
