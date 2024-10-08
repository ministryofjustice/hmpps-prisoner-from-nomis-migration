package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonSequenceMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonSimpleMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@Service
class ContactPersonMigrationService(
  val contactPersonMappingService: ContactPersonMappingApiService,
  val nomisApiService: ContactPersonNomisApiService,
  val dpsApiService: ContactPersonDpsApiService,
  @Value("\${contactperson.page.size:1000}") pageSize: Long,
  @Value("\${contactperson.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${contactperson.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value("\${contactperson.complete-check.count}") completeCheckCount: Int,
) : MigrationService<ContactPersonMigrationFilter, PersonIdResponse, ContactPersonMappingsDto>(
  mappingService = contactPersonMappingService,
  migrationType = MigrationType.CONTACTPERSON,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

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

  override suspend fun migrateNomisEntity(context: MigrationContext<PersonIdResponse>) {
    val nomisPersonId = context.body.personId
    val alreadyMigratedMapping = contactPersonMappingService.getByNomisPersonIdOrNull(nomisPersonId)

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the nomis person=$nomisPersonId since it was already mapped to DPS contact ${this.dpsId} during migration ${this.label}")
    } ?: run {
      val person = nomisApiService.getPerson(nomisPersonId = context.body.personId)
      val mapping = dpsApiService.migratePersonContact(person.toDpsMigrateContactRequest()).toContactPersonMappingsDto(context.migrationId)
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

  override suspend fun retryCreateMapping(context: MigrationContext<ContactPersonMappingsDto>) =
    createMappingOrOnFailureDo(context, context.body) {
      throw it
    }

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: ContactPersonMappingsDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      contactPersonMappingService.createMappingsForMigration(mapping)
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-contactperson-duplicate",
          mapOf(
            "duplicateDpsId" to duplicateErrorDetails.duplicate.dpsId,
            "duplicateNomisId" to duplicateErrorDetails.duplicate.nomisId,
            "existingDpsId" to duplicateErrorDetails.existing.dpsId,
            "existingNomisId" to duplicateErrorDetails.existing.nomisId,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "contactperson-migration-entity-migrated",
          mapOf(
            "nomisId" to mapping.personMapping.nomisId,
            "dpsId" to mapping.personMapping.dpsId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }
}

// speculative code of how the DPS mapping might mapping to our mappings
private fun DpsContactPersonMapping.toContactPersonMappingsDto(migrationId: String) = ContactPersonMappingsDto(
  mappingType = ContactPersonMappingsDto.MappingType.MIGRATED,
  label = migrationId,
  personMapping = person.toContactPersonSimpleMappingIdDto(),
  personAddressMapping = addresses.map { it.toContactPersonSimpleMappingIdDto() },
  personPhoneMapping = phoneNumbers.map { it.toContactPersonSimpleMappingIdDto() },
  personEmailMapping = emailAddresses.map { it.toContactPersonSimpleMappingIdDto() },
  personEmploymentMapping = employments.map { it.toContactPersonSimpleMappingIdDto() },
  personIdentifierMapping = identifiers.map { it.toContactPersonSimpleMappingIdDto() },
  personRestrictionMapping = restrictions.map { it.toContactPersonSimpleMappingIdDto() },
  personContactMapping = contacts.map { it.toContactPersonSimpleMappingIdDto() },
  personContactRestrictionMapping = contactRestrictions.map { it.toContactPersonSimpleMappingIdDto() },

)

private fun DpsToNomisId.toContactPersonSimpleMappingIdDto() = ContactPersonSimpleMappingIdDto(
  dpsId = dpsId,
  nomisId = nomisId,
)
private fun DpsToNomisIdWithSequence.toContactPersonSimpleMappingIdDto() = ContactPersonSequenceMappingIdDto(
  dpsId = dpsId,
  nomisPersonId = nomisId,
  nomisSequenceNumber = nomisSequence,
)

// NO DPS DTO defined yet - so just use the NOMIS model
private fun ContactPerson.toDpsMigrateContactRequest(): ContactPerson = this
