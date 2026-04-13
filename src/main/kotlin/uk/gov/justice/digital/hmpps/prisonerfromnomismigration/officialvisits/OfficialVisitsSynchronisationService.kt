package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DPS_SYNC_AUDIT_MODULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.SuccessOrBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.tryFetchParent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitReplaceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitorMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OfficialVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsSynchronisationMessageType.RETRY_SYNCHRONISATION_OFFICIAL_VISITOR_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsSynchronisationMessageType.RETRY_SYNCHRONISATION_OFFICIAL_VISIT_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.RepairPrisonerVisitsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateOfficialVisitorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncUpdateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncUpdateOfficialVisitorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class OfficialVisitsSynchronisationService(
  private val migrationService: OfficialVisitsMigrationService,
  private val mappingApiService: OfficialVisitsMappingService,
  private val visitSlotsMappingApiService: VisitSlotsMappingService,
  private val nomisApiService: OfficialVisitsNomisApiService,
  private val dpsApiService: OfficialVisitsDpsApiService,
  private val queueService: SynchronisationQueueService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun visitAdded(event: VisitEvent): OfficialVisitResponse? {
    val telemetryName = "officialvisits-visit-synchronisation-created"
    if (event.isFromDPSOfficialVisits()) {
      telemetryClient.trackEvent("$telemetryName-skipped", event.asTelemetry())
    } else {
      val mapping = mappingApiService.getByVisitNomisIdOrNull(
        nomisVisitId = event.visitId,
      )
      if (mapping != null) {
        telemetryClient.trackEvent(
          "$telemetryName-ignored",
          event.asTelemetry() + ("dpsOfficialVisitId" to mapping.dpsId),
        )
      } else {
        val telemetry = event.asTelemetry()
        return track(telemetryName, telemetry) {
          nomisApiService.getOfficialVisit(
            event.visitId,
          ).also { nomisVisit ->
            dpsApiService.createVisit(nomisVisit.toSyncCreateOfficialVisitRequest()).also { dpsVisit ->
              telemetry["dpsOfficialVisitId"] = dpsVisit.officialVisitId
              tryToCreateMapping(
                OfficialVisitMappingDto(
                  dpsId = dpsVisit.officialVisitId.toString(),
                  nomisId = event.visitId,
                  mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
                ),
                telemetry = telemetry,
              )
            }
          }
        }
      }
    }
    return null
  }
  suspend fun visitUpdated(event: VisitEvent): OfficialVisitResponse? {
    val telemetryName = "officialvisits-visit-synchronisation-updated"
    val telemetry = event.asTelemetry()

    return if (event.isFromDPSOfficialVisits()) {
      telemetryClient.trackEvent("$telemetryName-skipped", telemetry)
      null
    } else {
      track(telemetryName, telemetry) {
        val mapping = mappingApiService.getByVisitNomisIdOrNull(nomisVisitId = event.visitId)
        if (mapping != null) {
          telemetry["dpsOfficialVisitId"] = mapping.dpsId
          val nomisVisitResult = nomisApiService.getOfficialVisitOrBadRequestErrorMessage(event.visitId)
          if (nomisVisitResult.isError) {
            nomisVisitResult.assertNoLongerOfficialVisitOrThrow()
            officialVisitConvertedToSocialVisit(event, mapping.dpsId)
          } else {
            dpsApiService.updateVisit(
              mapping.dpsId.toLong(),
              nomisVisitResult.successResponse!!.toSyncUpdateOfficialVisitRequest(),
            )
          }
          nomisVisitResult.successResponse
        } else {
          // assume we never got the create event for this visit because it was a social visit converted to an official visit
          socialVisitConvertedToOfficialVisit(event)
        }
      }
    }
  }

  fun <T> SuccessOrBadRequest<T>.assertNoLongerOfficialVisitOrThrow() {
    if (!this.errorResponse!!.contains("not an official visit")) {
      throw IllegalStateException("Got 400 bad request trying to get visit details. Was expecting it to be a social visit")
    }
  }

  suspend fun officialVisitConvertedToSocialVisit(event: VisitEvent, dpsVisitId: String) {
    val telemetryName = "officialvisits-visit-synchronisation-official-visit-switched"
    val telemetry = event.asTelemetry()
    telemetry["dpsOfficialVisitId"] = dpsVisitId
    track(telemetryName, telemetry) {
      dpsApiService.deleteVisit(dpsVisitId.toLong())
      mappingApiService.deleteByVisitNomisId(nomisVisitId = event.visitId)
    }
  }

  suspend fun socialVisitConvertedToOfficialVisit(event: VisitEvent): OfficialVisitResponse {
    val telemetryName = "officialvisits-visit-synchronisation-social-visit-switched"
    val telemetry = event.asTelemetry()
    return track(telemetryName, telemetry) {
      visitAdded(event)?.also { nomisVisit ->
        val nomisVisitorIds = nomisVisit.visitors.map { visitor ->
          visitorAdded(
            VisitVisitorEvent(
              visitVisitorId = visitor.id,
              visitId = event.visitId,
              bookingId = event.bookingId,
              personId = visitor.personId,
              offenderIdDisplay = event.offenderIdDisplay,
              auditModuleName = event.auditModuleName,
            ),
          )
          visitor.id
        }
        telemetry["nomisVisitorIds"] = nomisVisitorIds.joinToString()
        telemetry["reason"] = "Visit created. Assumed switched from social to official visit"
      } ?: throw IllegalStateException("Assumed social visit converted to official visit but no visit created")
    }
  }

  suspend fun createVisitFromNomis(offenderNo: String, prisonId: String, nomisVisitId: Long): OfficialVisitResponse {
    val telemetryName = "officialvisits-visit-create-repair"
    val telemetry = mutableMapOf<String, Any>(
      "nomisVisitId" to nomisVisitId.toString(),
      "offenderNo" to offenderNo,
      "prisonId" to prisonId,
    )
    return track(telemetryName, telemetry) {
      visitAdded(
        VisitEvent(
          visitId = nomisVisitId,
          bookingId = 0,
          offenderIdDisplay = offenderNo,
          agencyLocationId = prisonId,
          auditModuleName = "REPAIR",
        ),
      )?.also { nomisVisit ->
        val nomisVisitorIds = nomisVisit.visitors.map { visitor ->
          visitorAdded(
            VisitVisitorEvent(
              visitVisitorId = visitor.id,
              visitId = nomisVisitId,
              bookingId = 0,
              personId = visitor.personId,
              offenderIdDisplay = offenderNo,
              auditModuleName = "REPAIR",
            ),
          )
          visitor.id
        }
        telemetry["nomisVisitorIds"] = nomisVisitorIds.joinToString()
        telemetry["reason"] = "Visit created. Manual repair"
      } ?: throw BadRequestException("Visit was not created since mapping already exists")
    }
  }
  suspend fun updateVisitFromNomis(offenderNo: String, prisonId: String, nomisVisitId: Long) {
    val telemetryName = "officialvisits-visit-update-repair"
    val telemetry = mutableMapOf<String, Any>(
      "nomisVisitId" to nomisVisitId.toString(),
      "offenderNo" to offenderNo,
      "prisonId" to prisonId,
    )
    if (!mappingApiService.hasVisitForNomisId(nomisVisitId)) {
      throw NotFoundException("Visit $nomisVisitId not found")
    }

    track(telemetryName, telemetry) {
      visitUpdated(
        VisitEvent(
          visitId = nomisVisitId,
          bookingId = 0,
          offenderIdDisplay = offenderNo,
          agencyLocationId = prisonId,
          auditModuleName = "REPAIR",
        ),
      )!!.also { nomisVisit ->
        val nomisVisitorIds = nomisVisit.visitors.map { visitor ->
          val visitorEvent = VisitVisitorEvent(
            visitVisitorId = visitor.id,
            visitId = nomisVisitId,
            bookingId = 0,
            personId = visitor.personId,
            offenderIdDisplay = offenderNo,
            auditModuleName = "REPAIR",
          )
          // either update visit or create visit
          if (mappingApiService.hasVisitorForNomisId(nomisVisitorId = visitor.id)) {
            visitorUpdated(visitorEvent)
          } else {
            visitorAdded(visitorEvent)
          }
          visitor.id
        }
        telemetry["nomisVisitorIds"] = nomisVisitorIds.joinToString()
        telemetry["reason"] = "Visit updated. Manual repair"
      }
    }
  }
  suspend fun recreateVisitsFromNomis(offenderNo: String): List<OfficialVisitResponse> {
    val telemetryName = "officialvisits-visit-prisoner-repair"
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to offenderNo,
      "reason" to "Visits recreated. Manual repair",
    )
    return track(telemetryName, telemetry) {
      val nomisVisits = nomisApiService.getOfficialVisitsForPrisoner(offenderNo).also {
        telemetry["nomisVisitIds"] = it.map { it.visitId }.joinToString()
      }
      val dpsVisitsRequests = nomisVisits.map {
        migrationService.convertToMigrateVisitRequest(it)
      }
      val dpsVisitResponse = dpsApiService.repairVisits(offenderNo, RepairPrisonerVisitsRequest(dpsVisitsRequests))
      mappingApiService.replaceMappingsByNomisId(
        dpsVisitResponse.visits.map {
          OfficialVisitMigrationMappingDto(
            dpsId = it.visit.dpsId.toString(),
            nomisId = it.visit.nomisId,
            visitors = it.visitors.map { visitor ->
              VisitorMigrationMappingDto(
                dpsId = visitor.dpsId.toString(),
                nomisId = visitor.nomisId,
              )
            },
            mappingType = OfficialVisitMigrationMappingDto.MappingType.NOMIS_CREATED,
          )
        }.let { OfficialVisitReplaceMappingDto(it) },
      )
      nomisVisits
    }
  }

  suspend fun visitDeleted(event: VisitEvent) {
    val telemetryName = "officialvisits-visit-synchronisation-deleted"
    val telemetry = event.asTelemetry()
    mappingApiService.getByVisitNomisIdOrNull(
      nomisVisitId = event.visitId,
    )?.also { mapping ->
      telemetry["dpsOfficialVisitId"] = mapping.dpsId
      track(telemetryName, telemetry) {
        dpsApiService.deleteVisit(mapping.dpsId.toLong())
        mappingApiService.deleteByVisitNomisId(
          nomisVisitId = event.visitId,
        )
      }
    } ?: run {
      telemetryClient.trackEvent(
        "$telemetryName-ignored",
        telemetry,
      )
    }
  }
  suspend fun visitorAdded(event: VisitVisitorEvent) {
    if (event.isPrisonerVisitor()) {
      telemetryClient.trackEvent("officialvisits-visitor-synchronisation-created-ignored", event.asTelemetry())
    } else {
      val telemetryName = "officialvisits-visitor-synchronisation-created"
      if (event.isFromDPSOfficialVisits()) {
        telemetryClient.trackEvent("$telemetryName-skipped", event.asTelemetry())
      } else {
        val mapping = mappingApiService.getByVisitorNomisIdOrNull(
          nomisVisitorId = event.visitVisitorId,
        )
        if (mapping != null) {
          telemetryClient.trackEvent(
            "$telemetryName-ignored",
            event.asTelemetry() + ("dpsOfficialVisitorId" to mapping.dpsId),
          )
        } else {
          val telemetry = event.asTelemetry()
          track(telemetryName, telemetry) {
            nomisApiService.getOfficialVisit(
              event.visitId,
            ).let { it.visitors.find { visitor -> visitor.id == event.visitVisitorId } }?.also { nomisVisitor ->
              val visitMapping = tryFetchParent { mappingApiService.getByVisitNomisIdOrNull(event.visitId) }
                .also { telemetry["dpsOfficialVisitId"] = it.dpsId }
              dpsApiService.createVisitor(officialVisitId = visitMapping.dpsId.toLong(), nomisVisitor.toSyncCreateOfficialVisitorRequest()).also { dpsVisitor ->
                telemetry["dpsOfficialVisitorId"] = dpsVisitor.officialVisitorId
                tryToCreateMapping(
                  OfficialVisitorMappingDto(
                    dpsId = dpsVisitor.officialVisitorId.toString(),
                    nomisId = event.visitVisitorId,
                    mappingType = OfficialVisitorMappingDto.MappingType.NOMIS_CREATED,
                  ),
                  telemetry = telemetry,
                )
              }
            } ?: run { telemetryClient.trackEvent("$telemetryName-ignored", telemetry + ("reason" to "visitor no longer found in nomis")) }
          }
        }
      }
    }
  }
  suspend fun visitorUpdated(event: VisitVisitorEvent) {
    val telemetryName = "officialvisits-visitor-synchronisation-updated"
    when {
      event.isPrisonerVisitor() -> telemetryClient.trackEvent("$telemetryName-ignored", event.asTelemetry())

      event.isFromDPSOfficialVisits() -> telemetryClient.trackEvent("$telemetryName-skipped", event.asTelemetry())

      event.isFromNOMISFlushSchedules() -> telemetryClient.trackEvent("$telemetryName-ignored", event.asTelemetry() + ("reason" to "Flush schedules"))

      else -> {
        val telemetry = event.asTelemetry()
        track(telemetryName, telemetry) {
          nomisApiService.getOfficialVisit(
            event.visitId,
          ).let { it.visitors.find { visitor -> visitor.id == event.visitVisitorId } }?.also { nomisVisitor ->
            val visitMapping = mappingApiService.getByVisitNomisId(
              nomisVisitId = event.visitId,
            ).also { telemetry["dpsOfficialVisitId"] = it.dpsId }
            val visitorMapping = mappingApiService.getByVisitorNomisId(
              nomisVisitorId = event.visitVisitorId,
            ).also { telemetry["dpsOfficialVisitorId"] = it.dpsId }

            dpsApiService.updateVisitor(
              officialVisitId = visitMapping.dpsId.toLong(),
              officialVisitorId = visitorMapping.dpsId.toLong(),
              request = nomisVisitor.toSyncUpdateOfficialVisitorRequest(),
            )
          } ?: run {
            telemetryClient.trackEvent(
              "$telemetryName-ignored",
              telemetry + ("reason" to "visitor no longer found in nomis"),
            )
          }
        }
      }
    }
  }

  suspend fun visitorDeleted(event: VisitVisitorEvent) {
    val telemetryName = "officialvisits-visitor-synchronisation-deleted"
    val telemetry = event.asTelemetry()
    val visitMapping = mappingApiService.getByVisitNomisIdOrNull(
      nomisVisitId = event.visitId,
    )
    val visitorMapping = mappingApiService.getByVisitorNomisIdOrNull(
      nomisVisitorId = event.visitVisitorId,
    )
    // visit mapping might have been deleted if we are midway through a
    // cascading delete
    if (visitMapping != null && visitorMapping != null) {
      telemetry["dpsOfficialVisitId"] = visitMapping.dpsId
      telemetry["dpsOfficialVisitorId"] = visitorMapping.dpsId
      track(telemetryName, telemetry) {
        dpsApiService.deleteVisitor(
          officialVisitId = visitMapping.dpsId.toLong(),
          officialVisitorId = visitorMapping.dpsId.toLong(),
        )
        mappingApiService.deleteByVisitorNomisId(
          nomisVisitorId = event.visitVisitorId,
        )
      }
    } else {
      telemetryClient.trackEvent(
        "$telemetryName-ignored",
        telemetry,
      )
    }
  }

  private suspend fun tryToCreateMapping(
    mapping: OfficialVisitMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for official visit id ${mapping.nomisId},${mapping.dpsId}", e)
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_OFFICIAL_VISIT_MAPPING.name,
        synchronisationType = SynchronisationType.OFFICIAL_VISITS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun createMapping(
    mapping: OfficialVisitMappingDto,
  ) {
    mappingApiService.createVisitMapping(
      mapping,
    )
      .takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "from-nomis-sync-officialvisits-duplicate",
            mapOf(
              "existingNomisId" to existing!!.nomisId,
              "existingDpsId" to existing.dpsId,
              "duplicateNomisId" to duplicate.nomisId,
              "duplicateDpsId" to duplicate.dpsId,
              "type" to "OFFICIALVISIT",
            ),
          )
        }
      }
  }

  suspend fun retryCreateOfficialVisitMapping(retryMessage: InternalMessage<OfficialVisitMappingDto>) {
    createMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "officialvisits-visit-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
  private suspend fun tryToCreateMapping(
    mapping: OfficialVisitorMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for official visitor id ${mapping.nomisId},${mapping.dpsId}", e)
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_OFFICIAL_VISITOR_MAPPING.name,
        synchronisationType = SynchronisationType.OFFICIAL_VISITS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun createMapping(
    mapping: OfficialVisitorMappingDto,
  ) {
    mappingApiService.createVisitorMapping(
      mapping,
    )
      .takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "from-nomis-sync-officialvisits-duplicate",
            mapOf(
              "existingNomisId" to existing!!.nomisId,
              "existingDpsId" to existing.dpsId,
              "duplicateNomisId" to duplicate.nomisId,
              "duplicateDpsId" to duplicate.dpsId,
              "type" to "OFFICIALVISITOR",
            ),
          )
        }
      }
  }

  suspend fun retryCreateOfficialVisitorMapping(retryMessage: InternalMessage<OfficialVisitorMappingDto>) {
    createMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "officialvisits-visitor-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }

  private suspend fun OfficialVisitor.toSyncCreateOfficialVisitorRequest(): SyncCreateOfficialVisitorRequest = SyncCreateOfficialVisitorRequest(
    offenderVisitVisitorId = id,
    personId = personId,
    createDateTime = audit.createDatetime,
    createUsername = audit.createUsername,
    firstName = firstName,
    lastName = lastName,
    relationshipToPrisoner = relationships.firstOrNull()?.relationshipType?.code,
    relationshipTypeCode = relationships.firstOrNull()?.contactType?.toDpsRelationshipType(),
    groupLeaderFlag = leadVisitor,
    assistedVisitFlag = assistedVisit,
    commentText = commentText,
    attendanceCode = visitorAttendanceOutcome?.toDpsAttendanceType(),
  )

  private suspend fun OfficialVisitor.toSyncUpdateOfficialVisitorRequest(): SyncUpdateOfficialVisitorRequest = SyncUpdateOfficialVisitorRequest(
    offenderVisitVisitorId = id,
    personId = personId,
    updateDateTime = audit.modifyDatetime ?: audit.createDatetime,
    updateUsername = audit.modifyUserId ?: audit.createUsername,
    firstName = firstName,
    lastName = lastName,
    relationshipToPrisoner = relationships.firstOrNull()?.relationshipType?.code,
    relationshipTypeCode = relationships.firstOrNull()?.contactType?.toDpsRelationshipType(),
    groupLeaderFlag = leadVisitor,
    assistedVisitFlag = assistedVisit,
    commentText = commentText,
    attendanceCode = visitorAttendanceOutcome?.toDpsAttendanceType(),
  )

  private suspend fun OfficialVisitResponse.toSyncCreateOfficialVisitRequest(): SyncCreateOfficialVisitRequest = SyncCreateOfficialVisitRequest(
    offenderVisitId = visitId,
    prisonVisitSlotId = visitSlotsMappingApiService.getVisitSlotByNomisId(visitSlotId).dpsId.toLong(),
    prisonCode = prisonId,
    offenderBookId = bookingId,
    prisonerNumber = offenderNo,
    currentTerm = currentTerm,
    visitDate = startDateTime.toLocalDate(),
    startTime = startDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
    endTime = endDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
    dpsLocationId = visitSlotsMappingApiService.getInternalLocationByNomisId(internalLocationId).dpsLocationId.asUUID(),
    visitStatusCode = visitStatus.toDpsVisitStatusType(),
    createDateTime = audit.createDatetime,
    createUsername = audit.createUsername,
    visitTypeCode = VisitType.UNKNOWN,
    commentText = commentText,
    searchTypeCode = prisonerSearchType?.toDpsSearchLevelType(),
    visitCompletionCode = cancellationReason.toDpsVisitCompletionType(visitStatus),
    visitorConcernText = visitorConcernText,
    overrideBanStaffUsername = overrideBanStaffUsername,
    visitOrderNumber = visitOrder?.number,
  )
  private suspend fun OfficialVisitResponse.toSyncUpdateOfficialVisitRequest(): SyncUpdateOfficialVisitRequest = SyncUpdateOfficialVisitRequest(
    offenderVisitId = visitId,
    prisonVisitSlotId = visitSlotsMappingApiService.getVisitSlotByNomisId(visitSlotId).dpsId.toLong(),
    prisonCode = prisonId,
    offenderBookId = bookingId,
    prisonerNumber = offenderNo,
    visitDate = startDateTime.toLocalDate(),
    startTime = startDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
    endTime = endDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
    dpsLocationId = visitSlotsMappingApiService.getInternalLocationByNomisId(internalLocationId).dpsLocationId.asUUID(),
    visitStatusCode = visitStatus.toDpsVisitStatusType(),
    updateDateTime = audit.modifyDatetime ?: audit.createDatetime,
    updateUsername = audit.modifyUserId ?: audit.createUsername,
    commentText = commentText,
    searchTypeCode = prisonerSearchType?.toDpsSearchLevelType(),
    visitCompletionCode = cancellationReason.toDpsVisitCompletionType(visitStatus),
    visitorConcernText = visitorConcernText,
    overrideBanStaffUsername = overrideBanStaffUsername,
    visitOrderNumber = visitOrder?.number,
  )
}

fun VisitEvent.asTelemetry() = mutableMapOf<String, Any>(
  "nomisVisitId" to visitId,
  "offenderNo" to offenderIdDisplay,
  "prisonId" to agencyLocationId,
  "bookingId" to bookingId,
)

fun VisitVisitorEvent.asTelemetry() = mutableMapOf<String, Any>(
  "nomisVisitorId" to visitVisitorId,
  "nomisVisitId" to visitId,
  "offenderNo" to offenderIdDisplay,
  "bookingId" to bookingId,
).also {
  if (personId != null) {
    it["nomisPersonId"] = personId
  }
}

private fun String.asUUID() = UUID.fromString(this)
private fun EventAudited.isFromDPSOfficialVisits() = this.auditExactMatchOrHasMissingAudit("${DPS_SYNC_AUDIT_MODULE}_OFFICIAL_VISITS")
private fun EventAudited.isFromNOMISFlushSchedules() = this.auditExactMatchOrHasMissingAudit("FLUSH_SCHEDULES")
private fun VisitVisitorEvent.isPrisonerVisitor() = this.personId == null
