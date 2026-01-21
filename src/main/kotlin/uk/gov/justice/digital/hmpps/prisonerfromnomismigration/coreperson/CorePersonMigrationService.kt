package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class CorePersonMigrationService(
  val nomisApiService: NomisApiService,
  val corePersonMappingService: CorePersonMappingApiService,
  val corePersonNomisApiService: CorePersonNomisApiService,
  val cprApiService: CorePersonCprApiService,
  jsonMapper: JsonMapper,
  @Value("\${coreperson.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : ByPageNumberMigrationService<CorePersonMigrationFilter, PrisonerId, CorePersonMappingsDto>(
  mappingService = corePersonMappingService,
  migrationType = MigrationType.CORE_PERSON,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  jsonMapper = jsonMapper,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getIds(
    migrationFilter: CorePersonMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerId> = nomisApiService.getPrisonerIds(
    // TODO add filter - if required
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun getPageOfIds(
    migrationFilter: CorePersonMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<PrisonerId> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: CorePersonMigrationFilter): Long = getIds(migrationFilter, 1, 0).totalElements

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
  override fun parseContextFilter(json: String): MigrationMessage<*, CorePersonMigrationFilter> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<CorePersonMigrationFilter, ByPageNumber>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonerId> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, CorePersonMappingsDto> = jsonMapper.readValue(json)
}

private fun String.toCorePersonMappingsDto(nomisPrisonNumber: String, migrationId: String) = CorePersonMappingsDto(
  mappingType = CorePersonMappingsDto.MappingType.MIGRATED,
  label = migrationId,
  personMapping = CorePersonMappingIdDto(cprId = nomisPrisonNumber, nomisPrisonNumber = nomisPrisonNumber),
  // TODO check if following lists can send null rather than emptyList
  addressMappings = emptyList(), // addressIds.map { it.toCorePersonSimpleMappingIdDto() },
  phoneMappings = emptyList(), // phoneIds.map { it.toCorePersonPhoneMappingIdDto() },
  emailMappings = emptyList(), // emailIds.map { it.toCorePersonSimpleMappingIdDto() },
  profileMappings = emptyList(),
  // TODO set other mappings beliefs etc
)

// TODO check why these are nullable fields in the Id pairs
// private fun Address.toCorePersonSimpleMappingIdDto() = CorePersonSimpleMappingIdDto(cprId = this.cprAddressId!!, nomisId = this.prisonAddressId!!)
// private fun Phone.toCorePersonPhoneMappingIdDto() = CorePersonPhoneMappingIdDto(cprId = this.cprPhoneId!!, nomisId = this.prisonPhoneId!!, cprPhoneType = CORE_PERSON)
// private fun Email.toCorePersonSimpleMappingIdDto() = CorePersonSimpleMappingIdDto(cprId = this.cprEmailId!!, nomisId = this.prisonEmailId!!)
