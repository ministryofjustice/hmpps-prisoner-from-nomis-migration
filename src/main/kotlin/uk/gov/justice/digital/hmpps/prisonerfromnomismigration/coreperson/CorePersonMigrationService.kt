package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.NomisIdentifierId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAlias
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAlias.BirthCountry
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAlias.Ethnicity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAlias.SexCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAlias.TitleCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAliasesAndIdentifiersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonIdentifier.Type
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAliasesAndIdentifiersResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByIdRangeMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.CORE_PERSON
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import java.time.LocalDateTime

@Service
class CorePersonMigrationService(
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
  migrationType = CORE_PERSON,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckRetrySeconds,
  completeCheckRetrySeconds = completeCheckCount,
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
        PrisonNumberAndRootOffenderId(it.fromRootOffenderId, ""),
        PrisonNumberAndRootOffenderId(it.toRootOffenderId, ""),
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
      val aliases = corePersonNomisApiService.getCorePerson(nomisPrisonNumber = prisonNumber).offenders ?: emptyList()
      val identifiers = aliases.flatMap { it.identifiers }
      val response = cprApiService.migrateCorePersonAliasesAndIdentifiers(
        prisonNumber,
        toMigrateAliasesAndIdentifiersRequest(aliases, identifiers),
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
          "${CORE_PERSON.telemetryName}-migration-duplicate",
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
          "${CORE_PERSON.telemetryName}-migration-entity-migrated",
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

  private fun toMigrateAliasesAndIdentifiersRequest(
    aliases: List<CoreOffender>,
    identifiers: List<Identifier>,
  ): PrisonAliasesAndIdentifiersRequest = PrisonAliasesAndIdentifiersRequest(
    aliases = aliases.map {
      PrisonAlias(
        firstName = it.firstName,
        middleNames = it.middleName1, // TODO work out the mappings
        lastName = it.lastName,
        dateOfBirth = it.dateOfBirth,
        nomisOffenderId = it.offenderId,
        titleCode = it.title?.code?.let { code -> TitleCode.valueOf(code) },
        sexCode = it.sex?.code?.let { code -> SexCode.valueOf(code) },
        isPrimary = it.workingName,
        birthPlace = it.birthPlace,
        birthCountry = it.birthCountry?.code?.let { code -> BirthCountry.valueOf(code) },
        ethnicity = it.ethnicity?.code?.let { code -> Ethnicity.valueOf(code) },
        createDate = it.createDate,
      )
    },
    identifiers = identifiers.map {
      PrisonIdentifier(
        nomisIdentifierId = NomisIdentifierId(it.offenderId, it.sequence.toInt()),
        type = Type.valueOf(it.type.code),
        value = it.identifier,
        verified = it.verified,
        comment = null, // TODO work out the mappings
        issuedAuthority = it.issuedAuthority,
        issuedDate = it.issuedDate,
      )
    },
  )

  fun SysconAliasesAndIdentifiersResponseBody.toCorePersonMappingsDto(
    whenCreated: String = LocalDateTime.now().toString(),
    migrationId: String,
  ) = CorePersonMappingsDto(
    mappingType = CorePersonMappingsDto.MappingType.MIGRATED,
    label = migrationId,
    personMapping = CorePersonMappingIdDto(
      cprId = prisonNumber, // TODO rename this field so it makes more sense.
      nomisPrisonNumber = prisonNumber,
    ),
    aliases = aliasesMappings.map {
      OffenderAliasMappingDto(
        cprId = it.cprAliasId,
        nomisOffenderId = it.nomisOffenderId,
        nomisPrisonNumber = prisonNumber,
        mappingType = OffenderAliasMappingDto.MappingType.MIGRATED,
        label = migrationId,
        whenCreated = whenCreated,
      )
    },
    identifiers = identifiersMappings.map {
      OffenderIdentifierMappingDto(
        cprId = it.cprIdentifierId,
        nomisOffenderId = it.nomisIdentifierId.nomisOffenderId,
        nomisIdentifierSequence = it.nomisIdentifierId.nomisSequence,
        nomisPrisonNumber = prisonNumber,
        mappingType = OffenderIdentifierMappingDto.MappingType.MIGRATED,
        label = migrationId,
        whenCreated = whenCreated,
      )
    },
  )
}
