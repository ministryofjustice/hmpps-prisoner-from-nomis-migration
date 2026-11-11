package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.NomisIdentifierId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAlias
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAliasesAndIdentifiersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAliasesAndIdentifiersResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.CORE_PERSON_ALIAS_IDENTIFIER
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class CorePersonAliasIdentifierMigrationService(
  private val corePersonAliasIdentifierMappingService: CorePersonAliasIdentifierMappingService,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val cprApiService: CorePersonCprApiService,
  nomisApiService: NomisApiService,
  jsonMapper: JsonMapper,
  @Value($$"${coreperson.page.size:1000}") pageSize: Long,
  @Value($$"${coreperson.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${coreperson.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${coreperson.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : CorePersonMigrationService<CorePersonAliasIdentifierMappingsDto>(
  mappingService = corePersonAliasIdentifierMappingService,
  migrationType = CORE_PERSON_ALIAS_IDENTIFIER,
  nomisApiService = nomisApiService,
  jsonMapper = jsonMapper,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
) {

  override suspend fun getExistingMapping(prisonNumber: String): CorePersonMappingDto? = corePersonAliasIdentifierMappingService.getCorePersonByPrisonNumberOrNull(prisonNumber = prisonNumber)

  override suspend fun migratePrisoner(prisonNumber: String, migrationId: String): CorePersonAliasIdentifierMappingsDto {
    val aliases = corePersonNomisApiService.getOffenderAliasesAndIdentifiers(nomisPrisonNumber = prisonNumber)
    return cprApiService.migrateCorePersonAliasesAndIdentifiers(
      prisonNumber,
      aliases.toMigrateAliasesAndIdentifiersRequest(),
    ).toCorePersonAliasIdentifierMappingsDto(migrationId = migrationId)
  }

  override suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: CorePersonAliasIdentifierMappingsDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      mappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CorePersonAliasIdentifierMappingsDto>>() {},
      )
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "${CORE_PERSON_ALIAS_IDENTIFIER.telemetryName}-migration-duplicate",
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
          "${CORE_PERSON_ALIAS_IDENTIFIER.telemetryName}-migration-entity-migrated",
          mapOf(
            "nomisPrisonNumber" to mapping.personMapping.nomisPrisonNumber,
            "cprId" to mapping.personMapping.cprId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }

  override fun parseContextMapping(json: String): MigrationMessage<*, CorePersonAliasIdentifierMappingsDto> = jsonMapper.readValue(json)
}

internal fun List<CoreOffender>.toMigrateAliasesAndIdentifiersRequest(): PrisonAliasesAndIdentifiersRequest = PrisonAliasesAndIdentifiersRequest(aliases = map { it.toPrisonAlias() })

internal fun CoreOffender.toPrisonAlias(): PrisonAlias = PrisonAlias(
  nomisOffenderId = offenderId,
  isPrimary = workingName,
  titleCode = title?.let { PrisonAlias.TitleCode.entries.fromCode(it.code.mapTitleCode(), "title") },
  firstName = firstName,
  middleNames = listOfNotNull(middleName1, middleName2).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null },
  lastName = lastName,
  dateOfBirth = dateOfBirth,
  sexCode = sex?.let { PrisonAlias.SexCode.entries.fromCode(it.code.mapSexCode(), "sex") },
  birthPlace = birthPlace,
  birthCountry = birthCountry?.let { PrisonAlias.BirthCountry.entries.fromCode(it.code.mapBirthCountryCode(), "birth country") },
  ethnicity = ethnicity?.let { PrisonAlias.Ethnicity.entries.fromCode(it.code.mapEthnicityCode(), "ethnicity") },
  suffix = suffix?.let { PrisonAlias.Suffix.entries.fromCode(it.code.mapSuffixCode(), "suffix") },
  nameType = nameType?.let { PrisonAlias.NameType.entries.fromCode(it.code, "name type") },
  identifiers = identifiers.map { it.toPrisonIdentifier() },
  createDateTime = createdDateTime,
  createUserId = createdByUsername,
  modifyDateTime = lastUpdatedDateTime,
  modifyUserId = lastUpdatedByUsername,
)

internal fun Identifier.toPrisonIdentifier(): PrisonIdentifier = PrisonIdentifier(
  nomisIdentifierId = NomisIdentifierId(nomisOffenderId = offenderId, nomisSequence = sequence.toInt()),
  type = PrisonIdentifier.Type.entries.fromCode(type.code.mapIdentifierTypeCode(), "identifier type"),
  `value` = identifier,
  verified = verified,
  comment = issuedAuthority,
  issuedDate = issuedDate,
  createDateTime = createdDateTime,
  createUserId = createdByUsername,
  modifyDateTime = lastUpdatedDateTime,
  modifyUserId = lastUpdatedByUsername,
)

private fun String.mapBirthCountryCode(): String = when (this) {
  "IOM" -> "IMN"
  "ROM" -> "ROU"
  else -> this
}

private fun String.mapTitleCode(): String = when (this) {
  "LADY" -> "LDY"
  "LORD" -> "LRD"
  else -> this
}

private fun String.mapSexCode(): String = when (this) {
  "NK" -> "N"
  else -> this
}

private fun String.mapEthnicityCode(): String = when (this) {
  "W10" -> "W5"
  else -> this
}

private fun String.mapSuffixCode(): String = when (this) {
  "JR" -> "Junior"
  "SR" -> "Senior"
  else -> this
}

private fun String.mapIdentifierTypeCode(): String = when (this) {
  "NINO" -> "NATIONAL_INSURANCE_NUMBER"
  "DL" -> "DRIVER_LICENSE_NUMBER"
  else -> this
}

private inline fun <reified E> List<E>.fromCode(code: String, description: String): E where E : Enum<E> = firstOrNull { it.jsonValue() == code }
  ?: throw IllegalArgumentException("Unable to map NOMIS $description code '$code' to ${E::class.simpleName}")

private fun Enum<*>.jsonValue(): String = when (this) {
  is PrisonAlias.TitleCode -> value
  is PrisonAlias.SexCode -> value
  is PrisonAlias.BirthCountry -> value
  is PrisonAlias.Ethnicity -> value
  is PrisonAlias.Suffix -> value
  is PrisonAlias.NameType -> value
  is PrisonIdentifier.Type -> value
  else -> name
}

fun SysconAliasesAndIdentifiersResponseBody.toCorePersonAliasIdentifierMappingsDto(
  migrationId: String? = null,
  mappingType: CorePersonAliasIdentifierMappingsDto.MappingType = CorePersonAliasIdentifierMappingsDto.MappingType.MIGRATED,
): CorePersonAliasIdentifierMappingsDto = CorePersonAliasIdentifierMappingsDto(
  mappingType = mappingType,
  label = migrationId,
  personMapping = CorePersonMappingIdDto(
    cprId = prisonNumber,
    nomisPrisonNumber = prisonNumber,
  ),
  aliases = aliasesMappings.map {
    OffenderAliasMappingDto(
      cprId = it.cprAliasId,
      nomisOffenderId = it.nomisOffenderId,
      nomisPrisonNumber = prisonNumber,
      mappingType = OffenderAliasMappingDto.MappingType.valueOf(mappingType.name),
      label = migrationId,
    )
  },
  identifiers = identifiersMappings.map {
    OffenderIdentifierMappingDto(
      cprId = it.cprIdentifierId,
      nomisOffenderId = it.nomisIdentifierId.nomisOffenderId,
      nomisIdentifierSequence = it.nomisIdentifierId.nomisSequence,
      nomisPrisonNumber = prisonNumber,
      mappingType = OffenderIdentifierMappingDto.MappingType.valueOf(mappingType.name),
      label = migrationId,
    )
  },
)
