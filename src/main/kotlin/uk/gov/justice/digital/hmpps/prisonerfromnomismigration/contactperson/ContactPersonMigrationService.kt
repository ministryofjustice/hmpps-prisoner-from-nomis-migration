package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@Service
class ContactPersonMigrationService(
  mappingService: ContactPersonMappingApiService,
  val nomisApiService: ContactPersonNomisApiService,
  @Value("\${contactperson.page.size:1000}") pageSize: Long,
  @Value("\${contactperson.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${contactperson.complete-check.count}") completeCheckCount: Int,
) : MigrationService<ContactPersonMigrationFilter, PersonIdResponse, ContactPersonMappingsDto>(
  mappingService = mappingService,
  migrationType = MigrationType.CONTACTPERSON,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  override suspend fun getIds(
    migrationFilter: ContactPersonMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PersonIdResponse> = nomisApiService.getPersonIdsToMigrate(
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<PersonIdResponse>) = TODO()

  override suspend fun retryCreateMapping(context: MigrationContext<ContactPersonMappingsDto>) = TODO()
}
