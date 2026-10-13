package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.springframework.test.context.bean.override.mockito.MockReset
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapRepairService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService

/*
 * All movements spy beans are declared here so every movements integration test shares a single Spring context.
 */
abstract class MovementsIntegrationTestBase : SqsIntegrationTestBase() {

  @MockitoSpyBean
  protected lateinit var courtSchedulerMigrationService: CourtSchedulerMigrationService

  @MockitoSpyBean(reset = MockReset.NONE)
  protected lateinit var queueService: SynchronisationQueueService

  @MockitoSpyBean
  protected lateinit var tapRepairService: TapRepairService

  @MockitoSpyBean
  protected lateinit var transferSchedulerMigrationService: TransferScheduleMigrationService
}
