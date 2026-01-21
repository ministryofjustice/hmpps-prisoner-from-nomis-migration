package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisCodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisVisit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VisitId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.asStringOrBlank
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes
import java.time.LocalDate
import java.time.LocalTime

@Service
class VisitsMigrationService(
  private val nomisApiService: NomisApiService,
  private val visitsService: VisitsService,
  private val visitMappingService: VisitMappingService,
  jsonMapper: JsonMapper,
  @Value("\${visits.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : ByPageNumberMigrationService<VisitsMigrationFilter, VisitId, VisitNomisMapping>(
  mappingService = visitMappingService,
  migrationType = VISITS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  jsonMapper = jsonMapper,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getIds(
    migrationFilter: VisitsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<VisitId> = nomisApiService.getVisits(
    prisonIds = migrationFilter.prisonIds,
    visitTypes = migrationFilter.visitTypes,
    fromDateTime = migrationFilter.fromDateTime,
    toDateTime = migrationFilter.toDateTime,
    ignoreMissingRoom = migrationFilter.ignoreMissingRoom,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun getPageOfIds(
    migrationFilter: VisitsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<VisitId> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: VisitsMigrationFilter): Long = getIds(migrationFilter, 1, 0).totalElements

  override suspend fun migrateNomisEntity(context: MigrationContext<VisitId>) {
    visitMappingService.findNomisVisitMapping(context.body.visitId)
      ?.run {
        log.info("Will not migrate visit since it is migrated already, NOMIS id is ${context.body.visitId}, VSIP id is ${this.vsipId} as part migration ${this.label ?: "NONE"} (${this.mappingType})")
      }
      ?: run {
        val nomisVisit = nomisApiService.getVisit(context.body.visitId)
        when (val roomMapping = determineRoomMapping(nomisVisit)) {
          is DateAwareRoomMapping -> {
            when (val visitResponse = visitsService.createVisit(mapNomisVisit(nomisVisit, roomMapping))) {
              is VisitsService.VisitCreateAborted -> {
                telemetryClient.trackEvent(
                  "nomis-migration-visit-rejected",
                  mapOf(
                    "migrationId" to context.migrationId,
                    "prisonId" to nomisVisit.prisonId,
                    "offenderNo" to nomisVisit.offenderNo,
                    "nomisId" to nomisVisit.visitId.toString(),
                    "startDateTime" to nomisVisit.startDateTime.asStringOrBlank(),
                    "room" to roomMapping.room,
                  ),
                  null,
                )
              }

              is VisitsService.VisitCreated -> {
                createNomisVisitMapping(
                  nomisVisitId = nomisVisit.visitId,
                  vsipVisitId = visitResponse.dpsVisitId,
                  context = context,
                ).also {
                  telemetryClient.trackEvent(
                    "visits-migration-entity-migrated",
                    mapOf(
                      "migrationId" to context.migrationId,
                      "prisonId" to nomisVisit.prisonId,
                      "offenderNo" to nomisVisit.offenderNo,
                      "visitId" to nomisVisit.visitId.toString(),
                      "vsipVisitId" to visitResponse.dpsVisitId,
                      "startDateTime" to nomisVisit.startDateTime.asStringOrBlank(),
                      "room" to roomMapping.room,
                    ),
                    null,
                  )
                }
              }
            }
          }

          is NoRoomMapping -> handleNoRoomMappingFound(context.migrationId, nomisVisit)
        }
      }
  }

  private suspend fun determineRoomMapping(
    nomisVisit: NomisVisit,
  ): RoomMappingResponse = if (isFutureVisit(nomisVisit) && !isErroneousFutureVisit(nomisVisit)) {
    nomisVisit.agencyInternalLocation?.let {
      visitMappingService.findRoomMapping(
        agencyInternalLocationCode = nomisVisit.agencyInternalLocation.description,
        prisonId = nomisVisit.prisonId,
      )?.let {
        DateAwareRoomMapping(
          it.vsipId,
          restriction = if (it.isOpen) VisitRestriction.OPEN else VisitRestriction.CLOSED,
        )
      }
    } ?: NoRoomMapping
  } else {
    DateAwareRoomMapping(
      room = nomisVisit.agencyInternalLocation?.let { nomisVisit.agencyInternalLocation.description } ?: "UNKNOWN",
      restriction = VisitRestriction.UNKNOWN,
    )
  }

  private fun isFutureVisit(nomisVisit: NomisVisit) = nomisVisit.startDateTime.toLocalDate() >= LocalDate.now()

  private fun isErroneousFutureVisit(nomisVisit: NomisVisit) = nomisVisit.startDateTime.toLocalDate() > LocalDate.now().plusYears(1)

  private suspend fun createNomisVisitMapping(
    nomisVisitId: Long,
    vsipVisitId: String,
    context: MigrationContext<*>,
  ) = try {
    visitMappingService.createMapping(
      VisitNomisMapping(
        nomisId = nomisVisitId,
        vsipId = vsipVisitId,
        label = context.migrationId,
        mappingType = "MIGRATED",
      ),
      object : ParameterizedTypeReference<DuplicateErrorResponse<VisitNomisMapping>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-visit-duplicate",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "duplicateVsipId" to duplicateErrorDetails.duplicate.vsipId,
            "duplicateNomisId" to duplicateErrorDetails.duplicate.nomisId.toString(),
            "existingVsipId" to duplicateErrorDetails.existing.vsipId,
            "existingNomisId" to duplicateErrorDetails.existing.nomisId.toString(),
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      }
    }
  } catch (e: Exception) {
    log.error("Failed to create mapping for visit $nomisVisitId, VSIP id $vsipVisitId", e)
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = VisitNomisMapping(
          nomisId = nomisVisitId,
          vsipId = vsipVisitId,
          label = context.migrationId,
          mappingType = "MIGRATED",
        ),
      ),
    )
  }

  private suspend fun handleNoRoomMappingFound(migrationId: String, nomisVisit: NomisVisit) {
    telemetryClient.trackEvent(
      "nomis-migration-visit-no-room-mapping",
      mapOf<String, String>(
        "migrationId" to migrationId,
        "prisonId" to nomisVisit.prisonId,
        "offenderNo" to nomisVisit.offenderNo,
        "visitId" to nomisVisit.visitId.toString(),
        "startDateTime" to nomisVisit.startDateTime.asStringOrBlank(),
        "agencyInternalLocation" to nomisVisit.agencyInternalLocation?.description.orEmpty(),
      ),
      null,
    )

    throw NoRoomMappingFoundException(
      prisonId = nomisVisit.prisonId,
      agencyInternalLocationDescription = nomisVisit.agencyInternalLocation?.description
        ?: "NO NOMIS ROOM MAPPING FOUND",
    )
  }

  suspend fun findRoomUsageByFilter(filter: VisitsMigrationFilter): List<VisitRoomUsageResponse> {
    val roomUsage = nomisApiService.getRoomUsage(filter)
    return roomUsage.map { usage ->
      usage.copy(
        vsipRoom = visitMappingService.findRoomMapping(
          usage.agencyInternalLocationDescription,
          usage.prisonId,
        )?.vsipId,
      )
    }.sortedWith(compareBy(VisitRoomUsageResponse::prisonId, VisitRoomUsageResponse::agencyInternalLocationDescription))
  }

  private fun mapNomisVisit(nomisVisit: NomisVisit, dateAwareRoomMapping: DateAwareRoomMapping): CreateVsipVisit {
    val visitNotesSet = mutableSetOf<VsipVisitNote>()
    val futureVisit = isFutureVisit(nomisVisit)
    nomisVisit.commentText?.apply {
      if (futureVisit) {
        visitNotesSet.add(
          VsipVisitNote(
            VsipVisitNoteType.VISIT_COMMENT,
            this,
          ),
        )
      }
    }
    nomisVisit.visitorConcernText?.apply {
      if (futureVisit) {
        visitNotesSet.add(
          VsipVisitNote(
            VsipVisitNoteType.VISITOR_CONCERN,
            this,
          ),
        )
      }
    }

    val startDateTime = applyPrisonSpecificVisitStartTimeMapping(nomisVisit)
    val endDateTime = applyPrisonSpecificVisitEndTimeMapping(nomisVisit)

    return CreateVsipVisit(
      prisonId = nomisVisit.prisonId,
      prisonerId = nomisVisit.offenderNo,
      startTimestamp = startDateTime,
      endTimestamp = endDateTime,
      visitType = nomisVisit.visitType.toVisitType(),
      visitStatus = getVsipVisitStatus(nomisVisit),
      outcomeStatus = getVsipOutcome(nomisVisit),
      visitRoom = dateAwareRoomMapping.room,
      contactList = nomisVisit.visitors.map {
        VsipVisitor(
          nomisPersonId = it.personId,
        )
      },
      legacyData = nomisVisit.leadVisitor?.let { VsipLegacyData(it.personId) },
      visitContact = nomisVisit.leadVisitor?.let {
        VsipLegacyContactOnVisit(
          name = it.fullName,
          telephone = it.telephones.firstOrNull(),
        )
      },
      visitNotes = visitNotesSet,
      visitors = nomisVisit.visitors.map { v -> VsipVisitor(v.personId) }.toSet(),
      visitRestriction = dateAwareRoomMapping.restriction,
      actionedBy = nomisVisit.modifyUserId ?: nomisVisit.createUserId,
      createDateTime = nomisVisit.whenCreated,
      modifyDateTime = nomisVisit.whenUpdated,
    )
  }

  private fun applyPrisonSpecificVisitStartTimeMapping(nomisVisit: NomisVisit) = if (isFutureVisit(nomisVisit) && nomisVisit.prisonId == "HEI") {
    if (nomisVisit.startDateTime.toLocalTime().isBefore(LocalTime.NOON)) {
      nomisVisit.startDateTime.toLocalDate().atTime(LocalTime.of(9, 0))
    } else {
      nomisVisit.startDateTime.toLocalDate().atTime(LocalTime.of(13, 45))
    }
  } else {
    nomisVisit.startDateTime
  }

  private fun applyPrisonSpecificVisitEndTimeMapping(nomisVisit: NomisVisit) = if (isFutureVisit(nomisVisit) && nomisVisit.prisonId == "HEI") {
    if (nomisVisit.startDateTime.toLocalTime().isBefore(LocalTime.NOON)) {
      nomisVisit.endDateTime.toLocalDate().atTime(LocalTime.of(10, 0))
    } else {
      nomisVisit.endDateTime.toLocalDate().atTime(LocalTime.of(14, 45))
    }
  } else {
    nomisVisit.endDateTime
  }
  override fun parseContextFilter(json: String): MigrationMessage<*, VisitsMigrationFilter> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<VisitsMigrationFilter, ByPageNumber>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, VisitId> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, VisitNomisMapping> = jsonMapper.readValue(json)
}

sealed interface RoomMappingResponse
data class DateAwareRoomMapping(val room: String?, val restriction: VisitRestriction) : RoomMappingResponse
data object NoRoomMapping : RoomMappingResponse

private fun NomisCodeDescription.toVisitType() = when (this.code) {
  "SCON" -> "SOCIAL"
  "OFFI" -> "OFFICIAL"
  else -> throw IllegalArgumentException("Unknown visit type ${this.code}")
}

class NoRoomMappingFoundException(val prisonId: String, val agencyInternalLocationDescription: String) : RuntimeException("No room mapping found for prisonId $prisonId and agencyInternalLocationDescription $agencyInternalLocationDescription")
