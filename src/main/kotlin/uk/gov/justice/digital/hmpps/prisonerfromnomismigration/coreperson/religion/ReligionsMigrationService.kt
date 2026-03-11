package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonNomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.toMigrateReligionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByIdRangeMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.CORE_PERSON_RELIGION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class ReligionsMigrationService(
  private val religionsMappingService: ReligionsMappingService,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val cprApiService: CorePersonCprApiService,
  private val nomisApiService: NomisApiService,
  jsonMapper: JsonMapper,
  @Value($$"${coreperson.page.size:1000}") pageSize: Long,
  @Value($$"${coreperson.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${coreperson.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${coreperson.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByIdRangeMigrationService<Any, PrisonNumberAndRootOffenderId, ReligionsMigrationMappingDto>(
  mappingService = religionsMappingService,
  migrationType = CORE_PERSON_RELIGION,
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
    .map { Pair(PrisonNumberAndRootOffenderId(it.fromRootOffenderId, ""), PrisonNumberAndRootOffenderId(it.toRootOffenderId, "")) }

  override suspend fun getPageOfIdsFromIdRange(
    firstId: PrisonNumberAndRootOffenderId?,
    lastId: PrisonNumberAndRootOffenderId?,
    migrationFilter: Any,
  ): List<PrisonNumberAndRootOffenderId> = nomisApiService.getAllPrisonersInRange(firstId!!.rootOffenderId, lastId!!.rootOffenderId)

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonNumberAndRootOffenderId>) {
    val prisonNumber = context.body.prisonNumber
    val alreadyMigratedMapping = religionsMappingService.getReligionsByPrisonNumberOrNull(
      prisonNumber = prisonNumber,
    )

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the prisoner=$nomisPrisonNumber since it was already mapped to CPR $cprId during migration $label")
    } ?: run {
      val religions = corePersonNomisApiService.getOffenderReligions(nomisPrisonNumber = prisonNumber)
      val cprReligions =
        if (religions.isNotEmpty()) {
          cprApiService.migrateCorePersonReligion(
            prisonNumber,
            religions.toMigrateReligionsRequest(),
          )
        } else {
          SysconReligionResponseBody(prisonNumber = prisonNumber, emptyList())
        }

      val mapping = ReligionsMigrationMappingDto(
        cprId = cprReligions.prisonNumber,
        nomisPrisonNumber = prisonNumber,
        religions = cprReligions.religionMappings.map {
          ReligionMigrationMappingDto(
            cprId = it.cprReligionId,
            nomisId = it.nomisReligionId.toLong(),
            nomisPrisonNumber = prisonNumber,
          )
        },
        mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
        label = context.migrationId,
      )
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
    mapping: ReligionsMigrationMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      mappingService.createMapping(mapping, object : ParameterizedTypeReference<DuplicateErrorResponse<ReligionsMigrationMappingDto>>() {})
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "${CORE_PERSON_RELIGION.telemetryName}-migration-duplicate",
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
          "${CORE_PERSON_RELIGION.telemetryName}-migration-entity-migrated",
          mapOf(
            "nomisPrisonNumber" to mapping.nomisPrisonNumber,
            "cprId" to mapping.cprId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }
  override suspend fun retryCreateMapping(context: MigrationContext<ReligionsMigrationMappingDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)
  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<Any, ByLastId<PrisonNumberAndRootOffenderId>>> = jsonMapper.readValue(json)
  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonNumberAndRootOffenderId> = jsonMapper.readValue(json)
  override fun parseContextMapping(json: String): MigrationMessage<*, ReligionsMigrationMappingDto> = jsonMapper.readValue(json)
}
