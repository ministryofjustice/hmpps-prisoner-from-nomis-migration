package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.CodedValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonSequenceMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonSimpleMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
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
  personMapping = this.contact!!.toContactPersonSimpleMappingIdDto(),
  personAddressMapping = this.addresses.map { it.address!!.toContactPersonSimpleMappingIdDto() } + this.addresses.flatMap { address -> address.phones.map { it.toContactPersonSimpleMappingIdDto() } },
  personPhoneMapping = phoneNumbers.map { it.toContactPersonSimpleMappingIdDto() },
  personEmailMapping = emailAddresses.map { ContactPersonSimpleMappingIdDto(dpsId = it.dpsId.toString(), nomisId = it.nomisId) },
  personEmploymentMapping = employments?.map { it.toContactPersonSequenceMappingIdDto(this.contact.nomisId) } ?: emptyList(),
  personIdentifierMapping = identities.map { it.toContactPersonSequenceMappingIdDto(this.contact.nomisId) },
  personRestrictionMapping = restrictions.map { it.toContactPersonSimpleMappingIdDto() },
  personContactMapping = relationships.map { it.relationship!!.toContactPersonSimpleMappingIdDto() },
  personContactRestrictionMapping = relationships.flatMap { it.restrictions.map { it.toContactPersonSimpleMappingIdDto() } },
)

private fun ContactPerson.toDpsMigrateContactRequest(): MigrateContactRequest = MigrateContactRequest(
  personId = this.personId,
  lastName = this.lastName,
  firstName = this.firstName,
  middleName = this.middleName,
  dateOfBirth = this.dateOfBirth,
  gender = this.gender?.toCodedValue(),
  title = this.title?.toCodedValue(),
  language = this.language?.toCodedValue(),
  interpreterRequired = this.interpreterRequired,
  domesticStatus = this.domesticStatus?.toCodedValue(),
  deceasedDate = this.deceasedDate,
  staff = this.isStaff ?: false,
  phoneNumbers = emptyList(),
  addresses = emptyList(),
  emailAddresses = emptyList(),
  employments = emptyList(),
  identifiers = emptyList(),
  contacts = emptyList(),
  restrictions = emptyList(),
  createDateTime = this.audit.createDatetime.toDateTime(),
  createUsername = this.audit.createUsername,
  modifyDateTime = this.audit.modifyDatetime.toDateTime(),
  modifyUsername = this.audit.modifyUserId,
)

private fun IdPair.toContactPersonSimpleMappingIdDto() = ContactPersonSimpleMappingIdDto(dpsId = this.dpsId.toString(), nomisId = this.nomisId)
private fun IdPair.toContactPersonSequenceMappingIdDto(personId: Long) = ContactPersonSequenceMappingIdDto(dpsId = this.dpsId.toString(), nomisSequenceNumber = this.nomisId, nomisPersonId = personId)
private fun CodeDescription.toCodedValue() = CodedValue(code = this.code, description = this.description)
private fun String?.toDateTime() = this?.let { java.time.LocalDateTime.parse(it) }
