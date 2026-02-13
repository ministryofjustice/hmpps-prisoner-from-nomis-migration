package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DPS_SYNC_AUDIT_MODULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.tryFetchParent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OfficialVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsSynchronisationMessageType.RETRY_SYNCHRONISATION_OFFICIAL_VISITOR_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsSynchronisationMessageType.RETRY_SYNCHRONISATION_OFFICIAL_VISIT_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.SyncCreateOfficialVisitorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class OfficialVisitsSynchronisationService(
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

  suspend fun visitAdded(event: VisitEvent) {
    val telemetryName = "officialvisits-visit-synchronisation-created"
    if (event.auditExactMatchOrHasMissingAudit("${DPS_SYNC_AUDIT_MODULE}_OFFICIAL_VISITS")) {
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
        track(telemetryName, telemetry) {
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
  }
  fun visitUpdated(event: VisitEvent) = track("officialvisits-visit-synchronisation-updated", event.asTelemetry()) {}

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
    if (event.personId == null) {
      telemetryClient.trackEvent("officialvisits-visitor-synchronisation-created-ignored", event.asTelemetry())
    } else {
      val telemetryName = "officialvisits-visitor-synchronisation-created"
      if (event.auditExactMatchOrHasMissingAudit("${DPS_SYNC_AUDIT_MODULE}_OFFICIAL_VISITS")) {
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
  fun visitorUpdated(event: VisitVisitorEvent) {
    if (event.personId == null) {
      telemetryClient.trackEvent("officialvisits-visitor-synchronisation-updated-ignored", event.asTelemetry())
    } else {
      track("officialvisits-visitor-synchronisation-updated", event.asTelemetry()) {}
    }
  }
  fun visitorDeleted(event: VisitVisitorEvent) {
    if (event.personId == null) {
      telemetryClient.trackEvent("officialvisits-visitor-synchronisation-deleted-ignored", event.asTelemetry())
    } else {
      track("officialvisits-visitor-synchronisation-deleted", event.asTelemetry()) {}
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
    dateOfBirth = dateOfBirth,
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
