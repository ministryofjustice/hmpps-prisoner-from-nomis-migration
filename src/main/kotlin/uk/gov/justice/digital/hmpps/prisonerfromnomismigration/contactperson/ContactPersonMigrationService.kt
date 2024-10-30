package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactResponse
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
  @Value("\${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : MigrationService<ContactPersonMigrationFilter, PersonIdResponse, ContactPersonMappingsDto>(
  mappingService = contactPersonMappingService,
  migrationType = MigrationType.CONTACTPERSON,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
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
      val mapping = dpsApiService.migrateContact(person.toDpsMigrateContactRequest()).toContactPersonMappingsDto(context.migrationId)
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

private fun MigrateContactResponse.toContactPersonMappingsDto(migrationId: String) = ContactPersonMappingsDto(
  mappingType = ContactPersonMappingsDto.MappingType.MIGRATED,
  label = migrationId,
  personMapping = ContactPersonSimpleMappingIdDto(dpsId = this.dpsContactId.toString(), nomisId = this.nomisPersonId),
  personAddressMapping = addresses.map { ContactPersonSimpleMappingIdDto(dpsId = it.dpsId.toString(), nomisId = it.nomisId) },
  personPhoneMapping = phoneNumbers.map { ContactPersonSimpleMappingIdDto(dpsId = it.dpsId.toString(), nomisId = it.nomisId) },
  personEmailMapping = emailAddresses.map { ContactPersonSimpleMappingIdDto(dpsId = it.dpsId.toString(), nomisId = it.nomisId) },
  // TODO - when in DPS response
  personEmploymentMapping = emptyList(),
  personIdentifierMapping = identities.map { ContactPersonSequenceMappingIdDto(dpsId = it.dpsId.toString(), nomisSequenceNumber = it.nomisId, nomisPersonId = this.nomisPersonId) },
  // TODO - when in DPS response
  personRestrictionMapping = emptyList(),
  personContactMapping = prisonerContacts.map { ContactPersonSimpleMappingIdDto(dpsId = it.dpsId.toString(), nomisId = it.nomisId) },
  personContactRestrictionMapping = prisonerContactRestrictions.map { ContactPersonSimpleMappingIdDto(dpsId = it.dpsId.toString(), nomisId = it.nomisId) },

)

private fun ContactPerson.toDpsMigrateContactRequest(): MigrateContactRequest = MigrateContactRequest(
  // TODO map these correctly when final version released
  personId = this.personId,
  lastName = this.lastName,
  firstName = this.firstName,
  remitter = false,
  staff = false,
  keepBiometrics = this.keepBiometrics,
  interpreterRequired = this.interpreterRequired,
  phoneNumbers = emptyList(),
  addresses = emptyList(),
  emailAddresses = emptyList(),
  identifiers = emptyList(),

)
