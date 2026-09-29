package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapOccurrence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncAtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapApplicationMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapBookingMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapMovementMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapScheduleMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTapMovementIn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTapMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTapScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTapsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.util.*

@Service
class TapRepairService(
  private val migrationMappingService: TapMappingApiService,
  private val nomisApiService: TapsNomisApiService,
  private val dpsApiService: TapDpsApiService,
  private val queueService: MigrationQueueService,
  private val telemetryClient: TelemetryClient,
) {
  suspend fun resyncPrisonerTaps(prisonerNumber: String) = resyncPrisonerTaps(
    MigrationContext(
      MigrationType.EXTERNAL_MOVEMENTS,
      generateBatchId(),
      1,
      prisonerNumber,
    ),
  )

  private suspend fun resyncPrisonerTaps(context: MigrationContext<String>) {
    val offenderNo = context.body
    val migrationId = context.migrationId
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "migrationId" to migrationId,
    )

    runCatching {
      val offenderTaps = nomisApiService.getAllOffenderTapsOrNull(offenderNo)
        // carry on even if there is no offender in NOMIS - this could be for a merged prisoner and we still need to update the mappings etc.
        ?: OffenderTapsResponse(bookings = emptyList())
      val oldMappingIds = migrationMappingService.getTapPrisonerMappingIds(offenderNo)
      // Note that we're not expecting to perform a clean migration again so we're calling the DPS /resync endpoint which performs "patch migrations"
      val dpsResponse = dpsApiService.resyncPrisonerTaps(offenderNo, offenderTaps.toDpsRequest(oldMappingIds))
        ?: MigrateTapResponse(temporaryAbsences = emptyList(), unscheduledMovements = emptyList())
      val mappings = offenderTaps.buildMappings(offenderNo, migrationId, dpsResponse)

      createMappingOrOnFailureDo(mappings) {
        requeueCreateMapping(mappings, context)
      }
    }
      .onFailure {
        publishTelemetry("failed", telemetry.apply { this["reason"] = it.message ?: "Unknown error" })
        throw it
      }
  }

  suspend fun retryCreateMapping(context: MigrationContext<TapPrisonerMappingsDto>) {
    createMappingOrOnFailureDo(context.body) {
      throw it
    }
  }

  private suspend fun createMappingOrOnFailureDo(
    mapping: TapPrisonerMappingsDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      createMapping(mapping)
    }.onSuccess {
      publishTelemetry(
        if (it.isError) "duplicate" else "migrated",
        mapOf(
          "offenderNo" to mapping.prisonerNumber,
          "migrationId" to mapping.migrationId,
        ),
      )
    }.onFailure {
      failureHandler(it)
    }
  }

  private suspend fun createMapping(mapping: TapPrisonerMappingsDto) = migrationMappingService.createMapping(
    mapping,
    object :
      ParameterizedTypeReference<DuplicateErrorResponse<TapPrisonerMappingsDto>>() {},
  )

  private suspend fun requeueCreateMapping(
    mapping: TapPrisonerMappingsDto,
    context: MigrationContext<*>,
  ) {
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = mapping,
      ),
    )
  }

  private fun publishTelemetry(type: String, telemetry: Map<String, String>) {
    telemetryClient.trackEvent(
      "temporary-absences-migration-entity-$type",
      telemetry,
    )
  }

  private fun OffenderTapsResponse.buildMappings(prisonerNumber: String, migrationId: String, dpsResponse: MigrateTapResponse) = TapPrisonerMappingsDto(
    prisonerNumber = prisonerNumber,
    migrationId = migrationId,
    bookings = bookings.map { booking ->
      TapBookingMappingsDto(
        bookingId = booking.bookingId,
        applications = booking.tapApplications.map { application ->
          TapApplicationMappingsDto(
            nomisApplicationId = application.tapApplicationId,
            dpsAuthorisationId = dpsResponse.findDpsAuthorisationId(application.tapApplicationId),
            schedules = application.taps.mapNotNull { it.tapScheduleOut }.map { it.toMappingDto(dpsResponse) },
            movements = application.taps.mapNotNull { it.tapMovementOut }.map { it.toMappingDto(booking.bookingId, dpsResponse) } +
              application.taps.mapNotNull { it.tapMovementIn }.map { it.toMappingDto(booking.bookingId, dpsResponse) },
          )
        },
        unscheduledMovements = booking.unscheduledTapMovementOuts.map { it.toMappingDto(booking.bookingId, dpsResponse) } +
          booking.unscheduledTapMovementIns.map { it.toMappingDto(booking.bookingId, dpsResponse) },
      )
    },
  )

  private fun BookingTapScheduleOut.toMappingDto(dpsResponse: MigrateTapResponse): TapScheduleMappingsDto = TapScheduleMappingsDto(
    nomisEventId = this.eventId,
    dpsOccurrenceId = dpsResponse.findDpsScheduleId(this.eventId),
    nomisAddressId = this.toAddressId,
    nomisAddressOwnerClass = this.toAddressOwnerClass,
    dpsAddressText = this.toFullAddress ?: "",
    dpsDescription = this.toAddressDescription,
    dpsPostcode = this.toAddressPostcode,
    eventTime = "${this.startTime}",
  )

  private fun BookingTapMovementOut.toMappingDto(bookingId: Long, dpsResponse: MigrateTapResponse): TapMovementMappingsDto = TapMovementMappingsDto(
    nomisMovementSeq = this.sequence,
    dpsMovementId = dpsResponse.findDpsMovementId(bookingId, this.sequence),
    nomisAddressId = this.toAddressId,
    nomisAddressOwnerClass = this.toAddressOwnerClass,
    dpsAddressText = this.toFullAddress ?: "",
    dpsDescription = this.toAddressDescription,
    dpsPostcode = this.toAddressPostcode,
  )

  private fun BookingTapMovementIn.toMappingDto(bookingId: Long, dpsResponse: MigrateTapResponse): TapMovementMappingsDto = TapMovementMappingsDto(
    nomisMovementSeq = this.sequence,
    dpsMovementId = dpsResponse.findDpsMovementId(bookingId, this.sequence),
    nomisAddressId = this.fromAddressId,
    nomisAddressOwnerClass = this.fromAddressOwnerClass,
    dpsAddressText = this.fromFullAddress ?: "",
    dpsDescription = this.fromAddressDescription,
    dpsPostcode = this.fromAddressPostcode,
  )

  private fun MigrateTapResponse.findDpsAuthorisationId(nomisApplicationId: Long) = temporaryAbsences.firstOrNull { it.legacyId == nomisApplicationId }
    ?.id
    ?: throw TapMigrationException("No matching DPS authorisation found for nomis application id $nomisApplicationId, we found only ${temporaryAbsences.map { it.legacyId to it.id }}")

  private fun MigrateTapResponse.findDpsScheduleId(nomisEventId: Long): UUID {
    val occurrenceResponses = temporaryAbsences.flatMap { it.occurrences }
    return occurrenceResponses
      .firstOrNull { it.legacyId == nomisEventId }
      ?.id
      ?: throw TapMigrationException("No matching DPS occurrence found for nomis event id $nomisEventId, we found only ${occurrenceResponses.map { it.legacyId to it.id }}")
  }

  private fun MigrateTapResponse.findDpsMovementId(nomisBookingId: Long, nomisMovementSeq: Int): UUID {
    val movementResponses = (temporaryAbsences.flatMap { it.occurrences.flatMap { it.movements } } + unscheduledMovements)
    return movementResponses
      .firstOrNull {
        val (bookingId, sequence) = it.legacyId.parseNomisMovementId()
        bookingId == nomisBookingId && sequence == nomisMovementSeq
      }
      ?.id
      ?: throw TapMigrationException("No matching DPS movement found for nomis booking with sequence $nomisBookingId / $nomisMovementSeq, we found only ${movementResponses.map { it.legacyId to it.id }}")
  }

  private fun String.parseNomisMovementId() = split("_").let { it[0].toLong() to it[1].toInt() }
}

fun OffenderTapsResponse.toDpsRequest(oldMappingIds: TapPrisonerMappingIdsDto) = MigrateTapRequest(
  temporaryAbsences = this.bookings.flatMap { booking ->
    booking.tapApplications.map { application ->
      MigrateTapAuthorisation(
        prisonCode = application.prisonId,
        statusCode = application.applicationStatus.toDpsAuthorisationStatusCode(application.toDate, booking.latestBooking),
        absenceTypeCode = application.tapType,
        absenceSubTypeCode = application.tapSubType,
        absenceReasonCode = application.eventSubType,
        accompaniedByCode = application.escortCode ?: DEFAULT_ESCORT_CODE,
        transportCode = application.transportType ?: DEFAULT_TRANSPORT_TYPE,
        repeat = application.applicationType == "REPEATING",
        start = application.fromDate,
        end = application.toDate,
        comments = application.comment,
        created = SyncAtAndBy(application.audit.createDatetime, application.audit.createUsername.toDpsUser()),
        updated = application.audit.modifyDatetime?.let { SyncAtAndBy(application.audit.modifyDatetime, application.audit.modifyUserId?.toDpsUser() ?: "") },
        legacyId = application.tapApplicationId,
        startTime = "${application.releaseTime.toLocalTime()}",
        endTime = "${application.returnTime.toLocalTime()}",
        location = Location(description = application.toAddressDescription, address = application.toFullAddress, postcode = application.toAddressPostcode),
        id = oldMappingIds.applications.find { it.nomisApplicationId == application.tapApplicationId }?.dpsAuthorisationId,
        occurrences = application.taps.mapNotNull { tap ->
          tap.tapScheduleOut?.let { scheduleOut ->
            scheduleOut.toDpsRequest(
              schedulePrison = scheduleOut.fromPrison ?: application.prisonId,
              bookingId = booking.bookingId,
              movementOut = tap.tapMovementOut,
              movementIn = tap.tapMovementIn,
              tapType = application.tapType,
              tapSubType = application.tapSubType,
              oldMappingIds = oldMappingIds,
            )
          }
        },
      )
    }
  },
  unscheduledMovements = this.bookings.flatMap { booking ->
    booking.unscheduledTapMovementOuts.map { movementOut ->
      movementOut.toDpsRequest(booking.bookingId, movementOut.fromPrison ?: "", oldMappingIds)
    } +
      booking.unscheduledTapMovementIns.map { movementIn ->
        movementIn.toDpsRequest(booking.bookingId, movementIn.toPrison ?: "", oldMappingIds)
      }
  },
)

private fun BookingTapScheduleOut.toDpsRequest(
  tapType: String?,
  tapSubType: String?,
  schedulePrison: String,
  bookingId: Long,
  movementOut: BookingTapMovementOut?,
  movementIn: BookingTapMovementIn?,
  oldMappingIds: TapPrisonerMappingIdsDto,
): MigrateTapOccurrence = MigrateTapOccurrence(
  isCancelled = eventStatus == "CANC",
  start = startTime,
  end = returnTime,
  location = Location(description = toAddressDescription, address = toFullAddress, postcode = toAddressPostcode),
  absenceTypeCode = tapType,
  absenceSubTypeCode = tapSubType,
  absenceReasonCode = eventSubType,
  accompaniedByCode = escort ?: DEFAULT_ESCORT_CODE,
  transportCode = transportType ?: DEFAULT_TRANSPORT_TYPE,
  contactInformation = contactPersonName,
  comments = comment,
  created = SyncAtAndBy(audit.createDatetime, audit.createUsername.toDpsUser()),
  updated = audit.modifyDatetime?.let { modified -> SyncAtAndBy(modified, audit.modifyUserId?.toDpsUser() ?: "") },
  legacyId = eventId,
  movements = listOfNotNull(movementOut?.toDpsRequest(bookingId, schedulePrison, oldMappingIds), movementIn?.toDpsRequest(bookingId, schedulePrison, oldMappingIds)),
  id = oldMappingIds.schedules.find { it.nomisEventId == eventId }?.dpsOccurrenceId,
)

private fun BookingTapMovementIn.toDpsRequest(
  bookingId: Long,
  schedulePrison: String,
  oldMappingIds: TapPrisonerMappingIdsDto,
): MigrateTapMovement = MigrateTapMovement(
  occurredAt = movementTime,
  direction = MigrateTapMovement.Direction.IN,
  absenceReasonCode = movementReason,
  location = Location(description = fromAddressDescription, address = fromFullAddress, postcode = fromAddressPostcode),
  accompaniedByCode = escort ?: DEFAULT_ESCORT_CODE,
  created = SyncAtAndBy(audit.createDatetime, audit.createUsername.toDpsUser()),
  legacyId = "${bookingId}_$sequence",
  accompaniedByComments = escortText,
  comments = commentText,
  updated = audit.modifyDatetime?.let { modified -> SyncAtAndBy(modified, audit.modifyUserId?.toDpsUser() ?: "") },
  prisonCode = toPrison ?: schedulePrison,
  id = oldMappingIds.movements.find { it.nomisBookingId == bookingId && it.nomisMovementSeq == sequence }?.dpsMovementId,
)

private fun BookingTapMovementOut.toDpsRequest(
  bookingId: Long,
  schedulePrison: String,
  oldMappingIds: TapPrisonerMappingIdsDto,
): MigrateTapMovement = MigrateTapMovement(
  occurredAt = movementTime,
  direction = MigrateTapMovement.Direction.OUT,
  absenceReasonCode = movementReason,
  location = Location(description = toAddressDescription, address = toFullAddress, postcode = toAddressPostcode),
  accompaniedByCode = escort ?: DEFAULT_ESCORT_CODE,
  created = SyncAtAndBy(audit.createDatetime, audit.createUsername.toDpsUser()),
  legacyId = "${bookingId}_$sequence",
  accompaniedByComments = escortText,
  comments = commentText,
  updated = audit.modifyDatetime?.let { modified -> SyncAtAndBy(modified, audit.modifyUserId?.toDpsUser() ?: "") },
  prisonCode = fromPrison ?: schedulePrison,
  id = oldMappingIds.movements.find { it.nomisBookingId == bookingId && it.nomisMovementSeq == sequence }?.dpsMovementId,
)

class TapMigrationException(message: String) : RuntimeException(message)
