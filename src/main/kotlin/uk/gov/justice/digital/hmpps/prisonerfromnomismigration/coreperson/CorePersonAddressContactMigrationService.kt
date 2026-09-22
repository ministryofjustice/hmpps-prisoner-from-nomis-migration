package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressesAndContactsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressesAndContactsResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressUsageMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByIdRangeMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.CORE_PERSON_ADDRESS_CONTACT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class CorePersonAliasIdentifierMigrationService(
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

internal fun CorePersonAddressContact.toMigrateAddressesAndContactsRequest(): PrisonAddressesAndContactsRequest = PrisonAddressesAndContactsRequest()

fun SysconAddressesAndContactsResponseBody.toCorePersonMappingsDto(
  migrationId: String? = null,
  migrationType: CorePersonMappingsDto.MappingType = CorePersonMappingsDto.MappingType.MIGRATED,
): CorePersonMappingsDto {
  val (aliasMigrationType, identifierMigrationType) = when (migrationType) {
    CorePersonMappingsDto.MappingType.MIGRATED -> CorePersonAddressMappingDto.MappingType.MIGRATED to CorePersonAddressUsageMappingDto.MappingType.MIGRATED
    CorePersonMappingsDto.MappingType.CPR_CREATED -> CorePersonAddressMappingDto.MappingType.CPR_CREATED to CorePersonAddressUsageMappingDto.MappingType.CPR_CREATED
    CorePersonMappingsDto.MappingType.NOMIS_CREATED -> CorePersonAddressMappingDto.MappingType.NOMIS_CREATED to CorePersonAddressUsageMappingDto.MappingType.NOMIS_CREATED
  }
  return CorePersonMappingsDto(
    mappingType = migrationType,
    label = migrationId,
    personMapping = CorePersonMappingIdDto(
      cprId = prisonNumber,
      nomisPrisonNumber = prisonNumber,
    ),
    addresses = emptyList(),
    addressUsages = emptyList(),
    phoneNumbers = emptyList(),
    emailAddresses = emptyList(),
  )
}
