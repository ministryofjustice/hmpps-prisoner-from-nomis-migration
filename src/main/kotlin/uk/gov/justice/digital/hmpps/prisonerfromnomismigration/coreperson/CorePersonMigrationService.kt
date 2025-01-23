package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonPhoneMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonSimpleMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class CorePersonMigrationService(
  val nomisApiService: NomisApiService,
  val corePersonMappingService: CorePersonMappingApiService,
  val corePersonNomisApiService: CorePersonNomisApiService,
  val cprApiService: CorePersonCprApiService,
  @Value("\${coreperson.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<CorePersonMigrationFilter, PrisonerId, CorePersonMappingsDto>(
  mappingService = corePersonMappingService,
  migrationType = MigrationType.CORE_PERSON,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: CorePersonMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerId> = nomisApiService.getPrisonerIds(
    // TODO add filter
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    val nomisPrisonNumber = context.body.offenderNo
    val alreadyMigratedMapping = corePersonMappingService.getByNomisPrisonNumberOrNull(nomisPrisonNumber)

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the nomis core person=$nomisPrisonNumber since it was already mapped to CPR core person ${this.cprId} during migration ${this.label}")
    } ?: run {
      val corePerson = corePersonNomisApiService.getCorePerson(nomisPrisonNumber = nomisPrisonNumber)
      val mapping = cprApiService.migrateCorePerson(corePerson.toMigrateCorePersonRequest()).toCorePersonMappingsDto(context.migrationId)
      createMappingOrOnFailureDo(context, mapping) {
        queueService.sendMessage(
          MigrationMessageType.RETRY_MIGRATION_MAPPING,
          MigrationContext(
            context = context,
            body = mapping,
          ),
        )
      }
    }
  }

  override suspend fun retryCreateMapping(context: MigrationContext<CorePersonMappingsDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: CorePersonMappingsDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      corePersonMappingService.createMappingsForMigration(mapping)
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-coreperson-duplicate",
          mapOf(
            "duplicateCprId" to duplicateErrorDetails.duplicate.cprId,
            "duplicateNomisPrisonNumber" to duplicateErrorDetails.duplicate.nomisPrisonNumber,
            "existingCprId" to duplicateErrorDetails.existing.cprId,
            "existingNomisPrisonNumber" to duplicateErrorDetails.existing.nomisPrisonNumber,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "coreperson-migration-entity-migrated",
          mapOf(
            "nomisPrisonNumber" to mapping.personMapping.nomisPrisonNumber,
            "cprId" to mapping.personMapping.cprId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }
}

private fun MigrateCorePersonResponse.toCorePersonMappingsDto(migrationId: String) = CorePersonMappingsDto(
  mappingType = CorePersonMappingsDto.MappingType.MIGRATED,
  label = migrationId,
  personMapping = toCorePersonMappingIdDto(),
  addressMappings = addressIds?.map { it.toCorePersonSimpleMappingIdDto() } ?: emptyList(),
  phoneMappings = phoneIds?.map { it.toCorePersonPhoneMappingIdDto(CorePersonPhoneMappingIdDto.CprPhoneType.CORE_PERSON) } ?: emptyList(),
  emailMappings = emailAddressIds?.map { it.toCorePersonSimpleMappingIdDto() } ?: emptyList(),
  // TODO set other mappings beliefs etc
)

private fun MigrateCorePersonResponse.toCorePersonMappingIdDto() = CorePersonMappingIdDto(cprId = this.cprId, nomisPrisonNumber = this.nomisPrisonNumber)
private fun IdPair.toCorePersonSimpleMappingIdDto() = CorePersonSimpleMappingIdDto(cprId = this.cprId, nomisId = this.nomisId)
private fun IdPair.toCorePersonPhoneMappingIdDto(phoneType: CorePersonPhoneMappingIdDto.CprPhoneType) = CorePersonPhoneMappingIdDto(cprId = this.cprId, cprPhoneType = phoneType, nomisId = this.nomisId)
