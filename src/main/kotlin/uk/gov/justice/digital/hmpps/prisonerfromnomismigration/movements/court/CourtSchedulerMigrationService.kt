package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.AtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.CourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.CourtEventMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncCourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncCourtEventMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncCourtEvents
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BookingCourtMovementMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BookingCourtScheduleMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerBookingMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingCourtMovementIn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingCourtMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderCourtMovementsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId

@Service
class CourtSchedulerMigrationService(
  private val nomisApi: CourtSchedulerNomisApiService,
  private val mappingApi: CourtSchedulerMappingApiService,
  private val sentencingMappingApi: CourtSentencingMappingApiService,
  private val dpsApi: CourtSchedulerDpsApiService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    val offenderNo = context.body.offenderNo
    val migrationId = context.migrationId
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "migrationId" to migrationId,
    )

    val offenderCourtMovements = nomisApi.getOffenderCourtMovementsOrNull(offenderNo)
      ?: OffenderCourtMovementsResponse(bookings = listOf())
    val oldMappingIds = mappingApi.getCourtSchedulerPrisonMappingIds(offenderNo)
    val courtSentencingMappings = offenderCourtMovements.findCourtAppearanceIds()
      .let { courtAppearanceIds -> sentencingMappingApi.getAllCourtAppearancesByNomisIds(courtAppearanceIds) }
    val dpsResponse = dpsApi.resyncPrisoner(offenderNo, offenderCourtMovements.toDpsRequest(oldMappingIds, courtSentencingMappings))
      ?: ResyncResponse(listOf(), listOf())
    val mappings = offenderCourtMovements.buildMappings(offenderNo, migrationId, dpsResponse)

    createMappingOrOnFailureDo(mappings) {}
  }

  private suspend fun createMappingOrOnFailureDo(
    mapping: CourtSchedulerPrisonerMappingsDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      createMapping(mapping)
    }.onSuccess {
      publishTelemetry(
        if (it.isError) "duplicate" else "migrated",
        mapOf(
          "offenderNo" to mapping.offenderNo,
          "migrationId" to mapping.migrationId,
        ),
      )
    }.onFailure {
      failureHandler(it)
    }
  }

  private suspend fun createMapping(mapping: CourtSchedulerPrisonerMappingsDto) = mappingApi.createMapping(
    mapping,
    object :
      ParameterizedTypeReference<DuplicateErrorResponse<CourtSchedulerPrisonerMappingsDto>>() {},
  )

  private fun publishTelemetry(type: String, telemetry: Map<String, String>) {
    telemetryClient.trackEvent(
      "temporary-absences-migration-entity-$type",
      telemetry,
    )
  }
}

private fun OffenderCourtMovementsResponse.findCourtAppearanceIds(): List<Long> = bookings
  .flatMap { booking ->
    booking.courtSchedules
      .filter { schedule -> schedule.courtCaseId != null }
      .map { schedule -> schedule.eventId }
  }

fun OffenderCourtMovementsResponse.toDpsRequest(
  oldMappingIds: CourtSchedulerPrisonerMappingIdsDto,
  sentencingMappings: List<CourtAppearanceMappingDto>,
) = ResyncCourtEvents(
  courtEvents = bookings.flatMap { booking ->
    booking.courtSchedules.map { schedule ->
      ResyncCourtEvent(
        courtEvent = CourtEvent(
          dpsId = oldMappingIds.schedules.find { it.nomisEventId == schedule.eventId }?.dpsCourtAppearanceId,
          prisonCodeAtTimeOfScheduling = schedule.prison,
          agyLocId = schedule.court,
          eventDate = schedule.eventDate,
          startTime = "${schedule.startTime}",
          courtEventType = schedule.eventType,
          eventStatus = schedule.eventStatus,
          eventId = schedule.eventId,
          commentText = schedule.comment,
          externalReferenceUrn = sentencingMappings.find { it.nomisCourtAppearanceId == schedule.eventId }
            ?.dpsCourtAppearanceId
            ?.let { "$EXTERNAL_REF_PREFIX$it" },
        ),
        created = AtAndBy(schedule.audit.createDatetime, schedule.audit.createUsername),
        modified = schedule.audit.modifyDatetime?.let { AtAndBy(schedule.audit.modifyDatetime, schedule.audit.modifyUserId!!) },
        movements = listOfNotNull(
          schedule.courtMovementOut?.toDpsRequest(oldMappingIds, booking.bookingId, schedule.eventId),
          schedule.courtMovementIn?.toDpsRequest(oldMappingIds, booking.bookingId, schedule.eventId),
        ),
      )
    }
  },
  unscheduledMovements = bookings.flatMap { booking ->
    booking.unscheduledCourtMovementOuts.map { movementOut ->
      movementOut.toDpsRequest(oldMappingIds, booking.bookingId, null)
    } +
      booking.unscheduledCourtMovementIns.map { movementIn ->
        movementIn.toDpsRequest(oldMappingIds, booking.bookingId, null)
      }
  },
)

private fun BookingCourtMovementOut.toDpsRequest(
  oldMappingIds: CourtSchedulerPrisonerMappingIdsDto,
  bookingId: Long,
  eventId: Long?,
): ResyncCourtEventMovement = ResyncCourtEventMovement(
  movement = CourtEventMovement(
    dpsId = oldMappingIds.movements.find { it.nomisBookingId == bookingId && it.nomisMovementSeq == sequence }?.dpsCourtMovementId,
    movementDate = movementDate,
    movementTime = "$movementTime",
    movementReasonCode = movementReason,
    directionCode = "OUT",
    fromAgencyId = fromPrison,
    toAgencyId = toCourt ?: MISSING_COURT,
    dpsCourtAppearanceScheduleId = oldMappingIds.schedules.find { it.nomisEventId == eventId }?.dpsCourtAppearanceId,
    offenderBookId = bookingId,
    movementSeq = sequence,
    commentText = commentText,
  ),
  created = AtAndBy(audit.createDatetime, audit.createUsername),
  modified = audit.modifyDatetime?.let {
    AtAndBy(
      audit.modifyDatetime,
      audit.modifyUserId!!,
    )
  },
)

private fun BookingCourtMovementIn.toDpsRequest(
  oldMappingIds: CourtSchedulerPrisonerMappingIdsDto,
  bookingId: Long,
  eventId: Long?,
): ResyncCourtEventMovement = ResyncCourtEventMovement(
  movement = CourtEventMovement(
    dpsId = oldMappingIds.movements.find { it.nomisBookingId == bookingId && it.nomisMovementSeq == sequence }?.dpsCourtMovementId,
    movementDate = movementDate,
    movementTime = "$movementTime",
    movementReasonCode = movementReason,
    directionCode = "IN",
    fromAgencyId = fromCourt ?: MISSING_COURT,
    toAgencyId = toPrison,
    dpsCourtAppearanceScheduleId = oldMappingIds.schedules.find { it.nomisEventId == eventId }?.dpsCourtAppearanceId,
    offenderBookId = bookingId,
    movementSeq = sequence,
    commentText = commentText,
  ),
  created = AtAndBy(audit.createDatetime, audit.createUsername),
  modified = audit.modifyDatetime?.let {
    AtAndBy(
      audit.modifyDatetime,
      audit.modifyUserId!!,
    )
  },
)

private fun OffenderCourtMovementsResponse.buildMappings(offenderNo: String, migrationId: String, dpsResponse: ResyncResponse) = CourtSchedulerPrisonerMappingsDto(
  offenderNo = offenderNo,
  migrationId = migrationId,
  bookings = bookings.map { booking ->
    CourtSchedulerBookingMappingsDto(
      bookingId = booking.bookingId,
      courtSchedules = booking.courtSchedules.map { schedule ->
        BookingCourtScheduleMappingsDto(
          nomisEventId = schedule.eventId,
          dpsCourtAppearanceId = dpsResponse.findDpsId(schedule.eventId),
          movements = listOfNotNull(
            schedule.courtMovementOut?.buildMapping(booking.bookingId, dpsResponse),
            schedule.courtMovementIn?.buildMapping(booking.bookingId, dpsResponse),
          ),
        )
      },
      unscheduledMovements = booking.unscheduledCourtMovementOuts.map { it.buildUnscheduleMapping(booking.bookingId, dpsResponse) } +
        booking.unscheduledCourtMovementIns.map { it.buildUnscheduledMapping(booking.bookingId, dpsResponse) },
    )
  },
)

private fun BookingCourtMovementOut.buildMapping(bookingId: Long, dpsResponse: ResyncResponse) = BookingCourtMovementMappingsDto(
  nomisMovementSeq = sequence,
  dpsCourtMovementId = dpsResponse.findScheduleMovementDpsId(bookingId, sequence),
)

private fun BookingCourtMovementIn.buildMapping(bookingId: Long, dpsResponse: ResyncResponse) = BookingCourtMovementMappingsDto(
  nomisMovementSeq = sequence,
  dpsCourtMovementId = dpsResponse.findScheduleMovementDpsId(bookingId, sequence),
)

private fun BookingCourtMovementOut.buildUnscheduleMapping(bookingId: Long, dpsResponse: ResyncResponse) = BookingCourtMovementMappingsDto(
  nomisMovementSeq = sequence,
  dpsCourtMovementId = dpsResponse.findUnscheduleMovementDpsId(bookingId, sequence),
)

private fun BookingCourtMovementIn.buildUnscheduledMapping(bookingId: Long, dpsResponse: ResyncResponse) = BookingCourtMovementMappingsDto(
  nomisMovementSeq = sequence,
  dpsCourtMovementId = dpsResponse.findUnscheduleMovementDpsId(bookingId, sequence),
)

private fun ResyncResponse.findDpsId(eventId: Long) = courtEvents.first { mapping -> mapping.eventId == eventId }.dpsId

private fun ResyncResponse.findScheduleMovementDpsId(bookingId: Long, movementSeq: Int) = courtEvents.flatMap { it.movements }.first { mapping -> mapping.offenderBookId == bookingId && mapping.movementSeq == movementSeq }.dpsId

private fun ResyncResponse.findUnscheduleMovementDpsId(bookingId: Long, movementSeq: Int) = unscheduledMovements.first { mapping -> mapping.offenderBookId == bookingId && mapping.movementSeq == movementSeq }.dpsId
