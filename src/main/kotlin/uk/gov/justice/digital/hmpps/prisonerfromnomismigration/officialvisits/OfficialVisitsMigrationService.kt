package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitorMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.AttendanceType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.RelationshipType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SearchLevelType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitCompletionType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitStatusType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastIdMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationDivision
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class OfficialVisitsMigrationService(
  private val officialVisitsMappingService: OfficialVisitsMappingService,
  private val visitSlotsMappingService: VisitSlotsMappingService,
  private val nomisApiService: OfficialVisitsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
  jsonMapper: JsonMapper,
  @Value($$"${officialvisits.page.size:1000}") pageSize: Long,
  @Value($$"${officialvisits.parallel.count:8}") getIdsParallelCount: Int,
  @Value($$"${officialvisits.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${officialvisits.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${officialvisits.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByLastIdMigrationService<OfficialVisitsMigrationFilter, VisitIdResponse, OfficialVisitMigrationMappingDto>(
  officialVisitsMappingService,
  MigrationType.OFFICIAL_VISITS,
  pageSize = pageSize,
  getIdsParallelCount = getIdsParallelCount,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckRetrySeconds,
  completeCheckRetrySeconds = completeCheckCount,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getPageOfIdsFromId(
    lastId: VisitIdResponse?,
    migrationFilter: OfficialVisitsMigrationFilter,
    pageSize: Long,
  ): List<VisitIdResponse> = nomisApiService.getOfficialVisitIdsByLastId(
    lastVisitId = lastId?.visitId ?: 0,
    pageSize = pageSize,
    prisonIds = migrationFilter.prisonIds,
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
  ).ids

  override fun compare(
    first: VisitIdResponse,
    second: VisitIdResponse?,
  ): Int = first.visitId.compareTo(second?.visitId ?: Long.MAX_VALUE)

  override suspend fun getPageOfIds(
    migrationFilter: OfficialVisitsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<VisitIdResponse> = nomisApiService.getOfficialVisitIds(
    pageNumber = pageNumber,
    pageSize = pageSize,
    prisonIds = migrationFilter.prisonIds,
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
  ).content!!

  override suspend fun getTotalNumberOfIds(migrationFilter: OfficialVisitsMigrationFilter): Long = nomisApiService.getOfficialVisitIds(
    pageNumber = 0,
    pageSize = 1,
    prisonIds = migrationFilter.prisonIds,
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
  ).page!!.totalElements!!

  override suspend fun migrateNomisEntity(context: MigrationContext<VisitIdResponse>) {
    val nomisVisitId = context.body.visitId
    val alreadyMigratedMapping = officialVisitsMappingService.getByVisitNomisIdsOrNull(
      nomisVisitId = nomisVisitId,
    )

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the nomis official visit id=$nomisVisitId since it was already mapped to DPS visit $dpsId during migration $label")
    } ?: run {
      val nomisOfficialVisit = nomisApiService.getOfficialVisit(visitId = nomisVisitId)
      val dpsOfficialVisit = dpsApiService.migrateVisit(nomisOfficialVisit.toMigrateVisitRequest())

      val mapping = OfficialVisitMigrationMappingDto(
        dpsId = dpsOfficialVisit.visit.dpsId.toString(),
        nomisId = nomisVisitId,
        visitors = dpsOfficialVisit.visitors.map {
          VisitorMigrationMappingDto(
            dpsId = it.dpsId.toString(),
            nomisId = it.nomisId,
          )
        },
        mappingType = OfficialVisitMigrationMappingDto.MappingType.MIGRATED,
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
    mapping: OfficialVisitMigrationMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      mappingService.createMapping(mapping, object : ParameterizedTypeReference<DuplicateErrorResponse<OfficialVisitMigrationMappingDto>>() {})
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-officialvisits-duplicate",
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
          "officialvisits-migration-entity-migrated",
          mapOf(
            "nomisId" to mapping.nomisId,
            "dpsId" to mapping.dpsId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }
  override suspend fun retryCreateMapping(context: MigrationContext<OfficialVisitMigrationMappingDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  suspend fun OfficialVisitResponse.toMigrateVisitRequest() = this.toMigrateVisitRequest(prisonVisitSlotLookup = { it.lookUpDpsVisitSlotId() }, dpsLocationLookup = { it.lookUpDpsLocationId() })

  private suspend fun Long.lookUpDpsLocationId(): UUID = officialVisitsMappingService.getInternalLocationByNomisId(this).dpsLocationId.let { UUID.fromString(it) }
  private suspend fun Long.lookUpDpsVisitSlotId(): Long = visitSlotsMappingService.getVisitSlotByNomisId(this).dpsId.toLong()
  override fun parseContextFilter(json: String): MigrationMessage<*, OfficialVisitsMigrationFilter> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<OfficialVisitsMigrationFilter, ByLastId<VisitIdResponse>>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, VisitIdResponse> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, OfficialVisitMigrationMappingDto> = jsonMapper.readValue(json)

  override fun parseContextDivisionFilter(json: String): MigrationMessage<*, MigrationDivision<OfficialVisitsMigrationFilter, VisitIdResponse>> = jsonMapper.readValue(json)
}

internal suspend fun OfficialVisitResponse.toMigrateVisitRequest(prisonVisitSlotLookup: suspend (Long) -> Long, dpsLocationLookup: suspend (Long) -> UUID): MigrateVisitRequest = MigrateVisitRequest(
  offenderVisitId = visitId,
  prisonVisitSlotId = prisonVisitSlotLookup(visitSlotId),
  prisonCode = prisonId,
  offenderBookId = bookingId,
  prisonerNumber = offenderNo,
  currentTerm = currentTerm,
  visitDate = startDateTime.toLocalDate(),
  startTime = startDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
  endTime = endDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
  dpsLocationId = dpsLocationLookup(internalLocationId),
  visitStatusCode = visitStatus.toDpsVisitStatusType(),
  visitTypeCode = VisitType.UNKNOWN,
  visitCompletionCode = cancellationReason.toDpsVisitCompletionType(visitStatus),
  visitOrderNumber = visitOrder?.number,
  createDateTime = audit.createDatetime,
  createUsername = audit.createUsername,
  visitors = visitors.map { visitor ->
    MigrateVisitor(
      offenderVisitVisitorId = visitor.id,
      personId = visitor.personId,
      createDateTime = visitor.audit.createDatetime,
      createUsername = visitor.audit.createUsername,
      firstName = visitor.firstName,
      lastName = visitor.lastName,
      dateOfBirth = visitor.dateOfBirth,
      relationshipToPrisoner = visitor.relationships.firstOrNull()?.relationshipType?.code,
      relationshipTypeCode = visitor.relationships.firstOrNull()?.contactType?.toDpsRelationshipType(),
      attendanceCode = visitor.visitorAttendanceOutcome?.toDpsAttendanceType(),
      groupLeaderFlag = visitor.leadVisitor,
      assistedVisitFlag = visitor.assistedVisit,
      commentText = visitor.commentText,
      modifyDateTime = visitor.audit.modifyDatetime,
      modifyUsername = visitor.audit.modifyUserId,
    )
  },
  commentText = commentText,
  searchTypeCode = prisonerSearchType?.toDpsSearchLevelType(),
  visitorConcernText = visitorConcernText,
  overrideBanStaffUsername = overrideBanStaffUsername,
  modifyDateTime = audit.modifyDatetime,
  modifyUsername = audit.modifyUserId,
)

private fun CodeDescription.toDpsAttendanceType(): AttendanceType = when (code) {
  "ATT" -> AttendanceType.ATTENDED
  "ABS" -> AttendanceType.ABSENT
  else -> throw IllegalArgumentException("Unknown attendance type code: $code")
}
private fun CodeDescription.toDpsRelationshipType(): RelationshipType = when (code) {
  "O" -> RelationshipType.OFFICIAL
  "S" -> RelationshipType.SOCIAL
  else -> throw IllegalArgumentException("Unknown relationship type code: $code")
}
private fun CodeDescription.toDpsSearchLevelType(): SearchLevelType = when (code) {
  "FULL" -> SearchLevelType.FULL
  "PAT" -> SearchLevelType.PAT
  "RUB" -> SearchLevelType.RUB
  "RUB_A" -> SearchLevelType.RUB_A
  "RUB_B" -> SearchLevelType.RUB_B
  "STR" -> SearchLevelType.STR
  else -> throw IllegalArgumentException("Unknown search type code: $code")
}

private fun CodeDescription.toDpsVisitStatusType(): VisitStatusType = when (code) {
  "CANC" -> VisitStatusType.CANCELLED
  "VDE" -> VisitStatusType.COMPLETED
  "HMPOP" -> VisitStatusType.COMPLETED
  "OFFEND" -> VisitStatusType.COMPLETED
  "VISITOR" -> VisitStatusType.COMPLETED
  "NORM" -> VisitStatusType.COMPLETED
  "SCH" -> VisitStatusType.SCHEDULED
  "EXP" -> VisitStatusType.EXPIRED
  else -> throw IllegalArgumentException("Unknown visit status code: $code")
}

private fun CodeDescription?.toDpsVisitCompletionType(visitStatus: CodeDescription): VisitCompletionType? = when (this?.code) {
  "NO_VO" -> VisitCompletionType.STAFF_CANCELLED

  "VO_CANCEL" -> VisitCompletionType.STAFF_CANCELLED

  "REFUSED" -> VisitCompletionType.PRISONER_REFUSED

  "OFFCANC" -> VisitCompletionType.PRISONER_CANCELLED

  "VISCANC" -> VisitCompletionType.VISITOR_CANCELLED

  "NSHOW" -> VisitCompletionType.VISITOR_NO_SHOW

  "ADMIN" -> VisitCompletionType.STAFF_CANCELLED

  "ADMIN_CANCEL" -> VisitCompletionType.STAFF_CANCELLED

  "HMP" -> VisitCompletionType.STAFF_CANCELLED

  "NO_ID" -> VisitCompletionType.VISITOR_DENIED

  "BATCH_CANC" -> VisitCompletionType.STAFF_CANCELLED

  null -> when (visitStatus.code) {
    "VDE" -> VisitCompletionType.VISITOR_DENIED
    "HMPOP" -> VisitCompletionType.STAFF_EARLY
    "OFFEND" -> VisitCompletionType.PRISONER_EARLY
    "VISITOR" -> VisitCompletionType.VISITOR_EARLY
    "NORM" -> VisitCompletionType.NORMAL
    "SCH" -> null
    "EXP" -> VisitCompletionType.NORMAL
    else -> null
  }

  else -> null
}
