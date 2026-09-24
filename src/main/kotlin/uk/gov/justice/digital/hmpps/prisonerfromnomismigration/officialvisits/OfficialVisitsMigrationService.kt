package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.VisitType
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class OfficialVisitsMigrationService(
  private val officialVisitsMappingService: OfficialVisitsMappingService,
  private val visitSlotsMappingService: VisitSlotsMappingService,
) {
  suspend fun OfficialVisitResponse.toMigrateVisitRequest() = this.toMigrateVisitRequest(prisonVisitSlotLookup = { it.lookUpDpsVisitSlotId() }, dpsLocationLookup = { it.lookUpDpsLocationId() })
  suspend fun convertToMigrateVisitRequest(nomisVisit: OfficialVisitResponse) = nomisVisit.toMigrateVisitRequest()

  private suspend fun Long.lookUpDpsLocationId(): UUID = officialVisitsMappingService.getInternalLocationByNomisId(this).dpsLocationId.let { UUID.fromString(it) }
  private suspend fun Long.lookUpDpsVisitSlotId(): Long = visitSlotsMappingService.getVisitSlotByNomisId(this).dpsId.toLong()
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
