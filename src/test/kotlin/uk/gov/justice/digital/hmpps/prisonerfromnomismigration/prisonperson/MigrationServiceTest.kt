package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.fasterxml.jackson.databind.node.TextNode
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePage

class MigrationServiceTest {
  private val queueService = mock<MigrationQueueService>()
  private val nomisService = mock<NomisApiService>()
  private val prisonPersonMappingService = mock<MappingApiService>()
  private val migrationHistoryService = mock<MigrationHistoryService>()
  private val telemetryClient = mock<TelemetryClient>()
  private val auditService = mock<AuditService>()
  private val entityMigratorService = mock<EntityMigratorService>()
  private val service = object : MigrationService(
    nomisService,
    prisonPersonMappingService,
    entityMigratorService,
    1000,
    1,
    1,
  ) {
    init {
      queueService = this@MigrationServiceTest.queueService
      migrationHistoryService = this@MigrationServiceTest.migrationHistoryService
      telemetryClient = this@MigrationServiceTest.telemetryClient
      auditService = this@MigrationServiceTest.auditService
    }
  }

  @Nested
  inner class GetIds {
    @Test
    fun `will not call prison API for a filter on prisoner number`() = runTest {
      service.getIds(MigrationFilter("A1234BC"), 1000, 0)

      verify(nomisService, never()).getPrisonerIds(anyLong(), anyLong())
    }

    @Test
    fun `will get all IDs when there is no prisoner number`() = runTest {
      whenever(nomisService.getPrisonerIds(anyLong(), anyLong()))
        .thenReturn(RestResponsePage(emptyList<PrisonerId>(), 0, 1, 0, TextNode("")))

      service.getIds(MigrationFilter(null), 1000, 0)

      verify(nomisService).getPrisonerIds(anyLong(), anyLong())
    }

    @Test
    fun `will get all IDs when the prisoner number is empty`() = runTest {
      whenever(nomisService.getPrisonerIds(anyLong(), anyLong()))
        .thenReturn(RestResponsePage(emptyList<PrisonerId>(), 0, 1, 0, TextNode("")))

      service.getIds(MigrationFilter(""), 1000, 0)

      verify(nomisService).getPrisonerIds(anyLong(), anyLong())
    }
  }
}
