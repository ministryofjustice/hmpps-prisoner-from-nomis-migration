package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.CodedValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.Corporate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateEmployment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigratePhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigratePrisonerContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateRelationship
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateRestriction
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

@Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
private fun MigrateContactResponse.toContactPersonMappingsDto(migrationId: String) = ContactPersonMappingsDto(
  mappingType = ContactPersonMappingsDto.MappingType.MIGRATED,
  label = migrationId,
  personMapping = this.contact!!.toContactPersonSimpleMappingIdDto(),
  personAddressMapping = this.addresses.map { it.address!!.toContactPersonSimpleMappingIdDto() },
  personPhoneMapping = phoneNumbers.map { it.toContactPersonSimpleMappingIdDto() } + this.addresses.flatMap { address -> address.phones.map { it.toContactPersonSimpleMappingIdDto() } },
  personEmailMapping = emailAddresses.map { ContactPersonSimpleMappingIdDto(dpsId = it.dpsId.toString(), nomisId = it.nomisId) },
  // TODO - getting IntelliJ smart cast error here - so for now use unnecessary  `!!` to avoid rebuilding all the time
  personEmploymentMapping = employments?.map { it.toContactPersonSequenceMappingIdDto(this.contact!!.nomisId) } ?: emptyList(),
  personIdentifierMapping = identities.map { it.toContactPersonSequenceMappingIdDto(this.contact!!.nomisId) },
  personRestrictionMapping = restrictions.map { it.toContactPersonSimpleMappingIdDto() },
  personContactMapping = relationships.map { it.relationship!!.toContactPersonSimpleMappingIdDto() },
  personContactRestrictionMapping = relationships.flatMap { it.restrictions.map { restriction -> restriction.toContactPersonSimpleMappingIdDto() } },
)

fun ContactPerson.toDpsMigrateContactRequest(): MigrateContactRequest = MigrateContactRequest(
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
  phoneNumbers = this.phoneNumbers.map {
    MigratePhoneNumber(
      phoneId = it.phoneId,
      number = it.number,
      extension = it.extension,
      type = it.type.toCodedValue(),
      createDateTime = it.audit.createDatetime.toDateTime(),
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime.toDateTime(),
      modifyUsername = it.audit.modifyUserId,
    )
  },
  addresses = this.addresses.map {
    MigrateAddress(
      addressId = it.addressId,
      type = it.type?.toCodedValue() ?: CodedValue("HOME", "Home"),
      flat = it.flat,
      premise = it.premise,
      street = it.street,
      locality = it.locality,
      postCode = it.postcode,
      city = it.city?.toCodedValue(),
      county = it.county?.toCodedValue(),
      country = it.country?.toCodedValue(),
      validatedPAF = it.validatedPAF,
      noFixedAddress = it.noFixedAddress,
      primaryAddress = it.primaryAddress,
      mailAddress = it.mailAddress,
      comment = it.comment,
      startDate = it.startDate,
      endDate = it.endDate,
      phoneNumbers = it.phoneNumbers.map { phone ->
        MigratePhoneNumber(
          phoneId = phone.phoneId,
          number = phone.number,
          extension = phone.extension,
          type = phone.type.toCodedValue(),
          createDateTime = phone.audit.createDatetime.toDateTime(),
          createUsername = phone.audit.createUsername,
          modifyDateTime = phone.audit.modifyDatetime.toDateTime(),
          modifyUsername = phone.audit.modifyUserId,
        )
      },
      createDateTime = it.audit.createDatetime.toDateTime(),
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime.toDateTime(),
      modifyUsername = it.audit.modifyUserId,
    )
  },
  emailAddresses = this.emailAddresses.map {
    MigrateEmailAddress(
      emailAddressId = it.emailAddressId,
      email = it.email,
      createDateTime = it.audit.createDatetime.toDateTime(),
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime.toDateTime(),
      modifyUsername = it.audit.modifyUserId,
    )
  },
  employments = this.employments.map {
    MigrateEmployment(
      sequence = it.sequence,
      active = it.active,
      corporate = it.corporate.let { corporate ->
        Corporate(
          id = corporate.id,
          name = corporate.name,
        )
      },
      createDateTime = it.audit.createDatetime.toDateTime(),
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime.toDateTime(),
      modifyUsername = it.audit.modifyUserId,
    )
  },
  identifiers = this.identifiers.map {
    MigrateIdentifier(
      sequence = it.sequence,
      type = it.type.toCodedValue(),
      identifier = it.identifier,
      issuedAuthority = it.issuedAuthority,
      createDateTime = it.audit.createDatetime.toDateTime(),
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime.toDateTime(),
      modifyUsername = it.audit.modifyUserId,
    )
  },
  contacts = this.contacts.map {
    MigrateRelationship(
      id = it.id,
      contactType = it.contactType.toCodedValue(),
      relationshipType = it.relationshipType.toCodedValue(),
      currentTerm = it.prisoner.bookingSequence == 1L,
      active = it.active,
      expiryDate = it.expiryDate,
      approvedVisitor = it.approvedVisitor,
      nextOfKin = it.nextOfKin,
      emergencyContact = it.emergencyContact,
      comment = it.comment,
      prisonerNumber = it.prisoner.offenderNo,
      createDateTime = it.audit.createDatetime.toDateTime(),
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime.toDateTime(),
      modifyUsername = it.audit.modifyUserId,
      restrictions = it.restrictions.map { restriction ->
        MigratePrisonerContactRestriction(
          id = restriction.id,
          restrictionType = restriction.type.toCodedValue(),
          startDate = restriction.effectiveDate,
          expiryDate = restriction.expiryDate,
          comment = restriction.comment,
          createDateTime = restriction.audit.createDatetime.toDateTime(),
          createUsername = restriction.audit.createUsername,
          modifyDateTime = restriction.audit.modifyDatetime.toDateTime(),
          modifyUsername = restriction.audit.modifyUserId,
        )
      },
    )
  },
  restrictions = this.restrictions.map {
    MigrateRestriction(
      id = it.id,
      type = it.type.toCodedValue(),
      effectiveDate = it.effectiveDate,
      expiryDate = it.expiryDate,
      comment = it.comment,
      createDateTime = it.audit.createDatetime.toDateTime(),
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime.toDateTime(),
      modifyUsername = it.audit.modifyUserId,
    )
  },
  createDateTime = this.audit.createDatetime.toDateTime(),
  createUsername = this.audit.createUsername,
  modifyDateTime = this.audit.modifyDatetime.toDateTime(),
  modifyUsername = this.audit.modifyUserId,
)

private fun IdPair.toContactPersonSimpleMappingIdDto() = ContactPersonSimpleMappingIdDto(dpsId = this.dpsId.toString(), nomisId = this.nomisId)
private fun IdPair.toContactPersonSequenceMappingIdDto(personId: Long) = ContactPersonSequenceMappingIdDto(dpsId = this.dpsId.toString(), nomisSequenceNumber = this.nomisId, nomisPersonId = personId)
private fun CodeDescription.toCodedValue() = CodedValue(code = this.code, description = this.description)
private fun String?.toDateTime() = this?.let { java.time.LocalDateTime.parse(it) }
