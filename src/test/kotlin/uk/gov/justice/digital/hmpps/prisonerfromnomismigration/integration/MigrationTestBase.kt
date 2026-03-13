package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration

import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.reset
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.CsraApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension

class MigrationTestBase : SqsIntegrationTestBase() {
  @Autowired
  protected lateinit var migrationHistoryRepository: MigrationHistoryRepository

  override fun resetTelemetryClient() {}

  internal fun setupMigrationTest() = runBlocking {
    migrationHistoryRepository.deleteAll()

    NomisApiExtension.resetAndDisableResetBeforeEach()
    CorePersonCprApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    FinanceApiExtension.resetAndDisableResetBeforeEach()
    OfficialVisitsDpsApiExtension.resetAndDisableResetBeforeEach()
    CsraApiExtension.resetAndDisableResetBeforeEach()
    ExternalMovementsDpsApiExtension.resetAndDisableResetBeforeEach()

    reset(telemetryClient)
  }

  internal fun deleteMigrationHistory() = runBlocking {
    migrationHistoryRepository.deleteAll()
  }
}
