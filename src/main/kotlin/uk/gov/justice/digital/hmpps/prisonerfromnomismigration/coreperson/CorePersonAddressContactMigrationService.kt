package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressUsage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressUsage.AddressUsageCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressesAndContactsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressesAndContactsResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconContactMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressUsageMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonEmailAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddressUsage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByIdRangeMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.CORE_PERSON_ADDRESS_CONTACT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class CorePersonAddressContactMigrationService(
  private val corePersonMappingService: CorePersonMappingService,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val cprApiService: CorePersonCprApiService,
  private val nomisApiService: NomisApiService,
  jsonMapper: JsonMapper,
  @Value($$"${coreperson.page.size:1000}") pageSize: Long,
  @Value($$"${coreperson.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${coreperson.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${coreperson.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByIdRangeMigrationService<Any, PrisonNumberAndRootOffenderId, CorePersonMappingsDto>(
  mappingService = corePersonMappingService,
  migrationType = CORE_PERSON_ADDRESS_CONTACT,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getTotalNumberOfIds(migrationFilter: Any): Long = nomisApiService.getPrisonerIds(0, 1).totalElements

  override suspend fun getRangeOfIds(
    body: Any,
    pageSize: Long,
  ): List<Pair<PrisonNumberAndRootOffenderId, PrisonNumberAndRootOffenderId>> = nomisApiService.getAllPrisonersIdRanges(pageSize)
    .map {
      Pair(
        PrisonNumberAndRootOffenderId(it.fromId, ""),
        PrisonNumberAndRootOffenderId(it.toId, ""),
      )
    }

  override suspend fun getPageOfIdsFromIdRange(
    firstId: PrisonNumberAndRootOffenderId?,
    lastId: PrisonNumberAndRootOffenderId?,
    migrationFilter: Any,
  ): List<PrisonNumberAndRootOffenderId> = nomisApiService.getAllPrisonersInRange(firstId!!.rootOffenderId, lastId!!.rootOffenderId)

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonNumberAndRootOffenderId>) {
    val prisonNumber = context.body.prisonNumber
    val alreadyMigratedMapping = corePersonMappingService.getCorePersonByPrisonNumberOrNull(
      prisonNumber = prisonNumber,
    )

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the prisoner=$nomisPrisonNumber since it was already mapped to CPR $cprId during migration $label")
    } ?: run {
      val addressesAndContacts = corePersonNomisApiService.getCorePersonAddressesAndContacts(nomisPrisonNumber = prisonNumber)
      val response = cprApiService.migrateCorePersonAddressesAndContacts(
        prisonNumber,
        addressesAndContacts.toMigrateAddressesAndContactsRequest(),
      )
      val mapping = response.toCorePersonMappingsDto(migrationId = context.migrationId)
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

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: CorePersonMappingsDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      mappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonMappingsDto>>() {},
      )
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "${CORE_PERSON_ADDRESS_CONTACT.telemetryName}-migration-duplicate",
          mapOf(
            "duplicateCprId" to duplicateErrorDetails.duplicate.personMapping.cprId,
            "duplicateNomisPrisonNumber" to duplicateErrorDetails.duplicate.personMapping.nomisPrisonNumber,
            "existingCprId" to duplicateErrorDetails.existing.personMapping.cprId,
            "existingNomisPrisonNumber" to duplicateErrorDetails.existing.personMapping.nomisPrisonNumber,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "${CORE_PERSON_ADDRESS_CONTACT.telemetryName}-migration-entity-migrated",
          mapOf(
            "nomisPrisonNumber" to mapping.personMapping.nomisPrisonNumber,
            "cprId" to mapping.personMapping.cprId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }

  override suspend fun retryCreateMapping(context: MigrationContext<CorePersonMappingsDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)
  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<Any, ByLastId<PrisonNumberAndRootOffenderId>>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonNumberAndRootOffenderId> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, CorePersonMappingsDto> = jsonMapper.readValue(json)
}

internal fun CorePersonAddressContact.toMigrateAddressesAndContactsRequest(): PrisonAddressesAndContactsRequest = PrisonAddressesAndContactsRequest(
  addresses = addresses?.map { it.toPrisonAddressRequest() },
  contacts = (phoneNumbers?.map { it.toPrisonPhoneNumberRequest() } ?: emptyList()) + (emailAddresses?.map { it.toPrisonEmailAddressRequest() } ?: emptyList()),
)

private fun OffenderAddress.toPrisonAddressRequest(): PrisonAddress = PrisonAddress(
  nomisAddressId = addressId,
  subBuildingName = flat,
  buildingNumber = premise,
  thoroughfareName = street,
  dependentLocality = locality,
  postcode = postcode,
  postTown = city?.description,
  county = county?.description,
  countryCode = country.mapCountryCode(),
  isPrimary = primaryAddress,
  noFixedAbode = noFixedAddress,
  isMail = mailAddress,
  comment = comment,
  startDate = startDate,
  endDate = endDate,
  createDateTime = createdDateTime,
  createUserId = createdByUsername,
  modifyDateTime = lastUpdatedDateTime,
  modifyUserId = lastUpdatedByUsername,
  addressUsage = usages?.map { it.toPrisonAddressUsageRequest() } ?: emptyList(),
  contacts = phoneNumbers?.map { it.toPrisonPhoneNumberRequest() } ?: emptyList(),
)

private fun CodeDescription?.mapCountryCode(): PrisonAddress.CountryCode? = when (this?.code) {
  null -> null
  "IOM" -> PrisonAddress.CountryCode.IMN
  "ROM" -> PrisonAddress.CountryCode.ROU
  else -> PrisonAddress.CountryCode.valueOf(this.code)
}

private fun OffenderAddressUsage.toPrisonAddressUsageRequest(): PrisonAddressUsage = PrisonAddressUsage(
  nomisAddressUsageId = addressId,
  addressUsageCode = if (usage.code == "DISC") AddressUsageCode.RELEASE else AddressUsageCode.valueOf(usage.code),
  isActive = active,
  createDateTime = createdDateTime,
  createUserId = createdByUsername,
  modifyDateTime = lastUpdatedDateTime,
  modifyUserId = lastUpdatedByUsername,
)

private fun OffenderPhoneNumber.toPrisonPhoneNumberRequest(): PrisonContact = PrisonContact(
  type = if (type.code == "MOB") PrisonContact.Type.MOBILE else PrisonContact.Type.valueOf(type.code),
  createDateTime = createdDateTime,
  createUserId = createdByUsername,
  nomisContactId = phoneId,
  value = number,
  extension = extension,
  modifyDateTime = lastUpdatedDateTime,
  modifyUserId = lastUpdatedByUsername,
)

private fun OffenderEmailAddress.toPrisonEmailAddressRequest(): PrisonContact = PrisonContact(
  type = PrisonContact.Type.EMAIL,
  createDateTime = createdDateTime,
  createUserId = createdByUsername,
  nomisContactId = emailAddressId,
  value = email,
  modifyDateTime = lastUpdatedDateTime,
  modifyUserId = lastUpdatedByUsername,
)

fun SysconAddressesAndContactsResponseBody.toCorePersonMappingsDto(
  migrationId: String? = null,
  migrationType: CorePersonMappingsDto.MappingType = CorePersonMappingsDto.MappingType.MIGRATED,
): CorePersonMappingsDto {
  val migrationTypes = when (migrationType) {
    CorePersonMappingsDto.MappingType.MIGRATED -> MigrationTypes(
      CorePersonAddressMappingDto.MappingType.MIGRATED,
      CorePersonAddressUsageMappingDto.MappingType.MIGRATED,
      CorePersonPhoneMappingDto.MappingType.MIGRATED,
      CorePersonEmailAddressMappingDto.MappingType.MIGRATED,
    )
    CorePersonMappingsDto.MappingType.CPR_CREATED -> MigrationTypes(
      CorePersonAddressMappingDto.MappingType.CPR_CREATED,
      CorePersonAddressUsageMappingDto.MappingType.CPR_CREATED,
      CorePersonPhoneMappingDto.MappingType.CPR_CREATED,
      CorePersonEmailAddressMappingDto.MappingType.CPR_CREATED,
    )
    CorePersonMappingsDto.MappingType.NOMIS_CREATED -> MigrationTypes(
      CorePersonAddressMappingDto.MappingType.NOMIS_CREATED,
      CorePersonAddressUsageMappingDto.MappingType.NOMIS_CREATED,
      CorePersonPhoneMappingDto.MappingType.NOMIS_CREATED,
      CorePersonEmailAddressMappingDto.MappingType.NOMIS_CREATED,
    )
  }
  return CorePersonMappingsDto(
    mappingType = migrationType,
    label = migrationId,
    personMapping = CorePersonMappingIdDto(
      cprId = prisonNumber,
      nomisPrisonNumber = prisonNumber,
    ),
    addresses = addressesMappings.map {
      CorePersonAddressMappingDto(
        cprId = it.cprAddressId,
        nomisId = it.nomisAddressId,
        nomisPrisonNumber = prisonNumber,
        mappingType = migrationTypes.addressType,
        label = migrationId,
      )
    },
    addressUsages = addressesMappings.flatMap { a ->
      a.addressUsageMappings.map {
        CorePersonAddressUsageMappingDto(
          cprId = it.cprAddressUsageId,
          nomisId = it.nomisAddressUsageId,
          nomisPrisonNumber = prisonNumber,
          mappingType = migrationTypes.addressUsageType,
          addressUsageCode = it.nomisAddressUsageCode.value,
          label = migrationId,
        )
      }
    },
    phoneNumbers = addressesMappings.flatMap { a ->
      a.contactMappings.map {
        CorePersonPhoneMappingDto(
          cprId = it.cprContactId,
          nomisId = it.nomisContactId,
          nomisPrisonNumber = prisonNumber,
          mappingType = migrationTypes.phoneType,
          label = migrationId,
        )
      }
    } +
      contactMappings.filter { it.nomisContactType != SysconContactMapping.NomisContactType.EMAIL }.map {
        CorePersonPhoneMappingDto(
          cprId = it.cprContactId,
          nomisId = it.nomisContactId,
          nomisPrisonNumber = prisonNumber,
          mappingType = migrationTypes.phoneType,
          label = migrationId,
        )
      },
    emailAddresses = contactMappings.filter { it.nomisContactType == SysconContactMapping.NomisContactType.EMAIL }.map {
      CorePersonEmailAddressMappingDto(
        cprId = it.cprContactId,
        nomisId = it.nomisContactId,
        nomisPrisonNumber = prisonNumber,
        mappingType = migrationTypes.emailType,
        label = migrationId,
      )
    },
  )
}

data class MigrationTypes(
  val addressType: CorePersonAddressMappingDto.MappingType,
  val addressUsageType: CorePersonAddressUsageMappingDto.MappingType,
  val phoneType: CorePersonPhoneMappingDto.MappingType,
  val emailType: CorePersonEmailAddressMappingDto.MappingType,
)
