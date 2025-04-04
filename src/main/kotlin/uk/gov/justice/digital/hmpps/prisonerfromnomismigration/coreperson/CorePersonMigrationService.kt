package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.AddressId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.CreateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.EmailId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PhoneId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonPhoneMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonPhoneMappingIdDto.CprPhoneType.CORE_PERSON
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonSimpleMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
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
    // TODO add filter - if required
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
      val mapping = cprApiService.migrateCorePerson(nomisPrisonNumber, corePerson.toCprPrisoner()).toCorePersonMappingsDto(nomisPrisonNumber, context.migrationId)
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

private fun CreateResponse.toCorePersonMappingsDto(nomisPrisonNumber: String, migrationId: String) = CorePersonMappingsDto(
  mappingType = CorePersonMappingsDto.MappingType.MIGRATED,
  label = migrationId,
  personMapping = CorePersonMappingIdDto(cprId = nomisPrisonNumber, nomisPrisonNumber = nomisPrisonNumber),
  // TODO check if following lists can send null rather than emptyList
  addressMappings = addressIds.map { it.toCorePersonSimpleMappingIdDto() },
  phoneMappings = phoneIds.map { it.toCorePersonPhoneMappingIdDto() },
  emailMappings = emailIds.map { it.toCorePersonSimpleMappingIdDto() },
  // TODO set other mappings beliefs etc
)

// TODO check why these are nullable fields in the Id pairs
private fun AddressId.toCorePersonSimpleMappingIdDto() = CorePersonSimpleMappingIdDto(cprId = this.cprAddressId!!, nomisId = this.prisonAddressId!!)
private fun PhoneId.toCorePersonPhoneMappingIdDto() = CorePersonPhoneMappingIdDto(cprId = this.cprPhoneId!!, nomisId = this.prisonPhoneId!!, cprPhoneType = CORE_PERSON)
private fun EmailId.toCorePersonSimpleMappingIdDto() = CorePersonSimpleMappingIdDto(cprId = this.cprEmailId!!, nomisId = this.prisonEmailId!!)
