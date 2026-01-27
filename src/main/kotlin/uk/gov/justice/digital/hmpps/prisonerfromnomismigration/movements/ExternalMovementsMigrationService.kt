package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapOccurrence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MigrateTapResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncAtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ExternalMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsencesPrisonerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTemporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceReturn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException
import java.time.LocalDateTime
import java.util.*

@Service
class ExternalMovementsMigrationService(
  val migrationMappingService: ExternalMovementsMappingApiService,
  val nomisIdsApiService: NomisApiService,
  val nomisApiService: ExternalMovementsNomisApiService,
  val dpsApiService: ExternalMovementsDpsApiService,
  jsonMapper: JsonMapper,
  @Value($$"${externalmovements.page.size:1000}") pageSize: Long,
  @Value($$"${externalmovements.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${externalmovements.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${externalmovements.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds:10}") completeCheckScheduledRetrySeconds: Int,
) : ByPageNumberMigrationService<ExternalMovementsMigrationFilter, PrisonerId, TemporaryAbsencesPrisonerMappingDto>(
  mappingService = migrationMappingService,
  migrationType = MigrationType.EXTERNAL_MOVEMENTS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {
  suspend fun getIds(
    migrationFilter: ExternalMovementsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerId> = if (migrationFilter.prisonerNumber.isNullOrEmpty()) {
    nomisIdsApiService.getPrisonerIds(
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  } else {
    // If a single prisoner migration is requested, then we'll trust the input as we're probably testing. Pretend that we called nomis-prisoner-api which found a single prisoner.
    PageImpl(mutableListOf(PrisonerId(migrationFilter.prisonerNumber)), Pageable.ofSize(1), 1)
  }

  override suspend fun getPageOfIds(
    migrationFilter: ExternalMovementsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<PrisonerId> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: ExternalMovementsMigrationFilter): Long = getIds(migrationFilter, 1, 0).totalElements

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    val offenderNo = context.body.offenderNo
    val migrationId = context.migrationId
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "migrationId" to migrationId,
    )

    runCatching {
      val temporaryAbsences = nomisApiService.getTemporaryAbsencesOrNull(offenderNo)
        ?: throw NotFoundException("Prisoner $offenderNo not found")
      if (temporaryAbsences.bookings.isEmpty()) {
        publishTelemetry("ignored", telemetry.apply { this["reason"] = "The offender has no bookings" })
        return
      }
      val dpsResponse = dpsApiService.migratePrisonerTaps(offenderNo, temporaryAbsences.toDpsRequest())
      val mappings = temporaryAbsences.buildMappings(offenderNo, migrationId, dpsResponse)

      createMappingOrOnFailureDo(mappings) {
        requeueCreateMapping(mappings, context)
      }
    }
      .onFailure {
        publishTelemetry("failed", telemetry.apply { this["reason"] = it.message ?: "Unknown error" })
        throw it
      }
  }

  override suspend fun retryCreateMapping(context: MigrationContext<TemporaryAbsencesPrisonerMappingDto>) {
    createMappingOrOnFailureDo(context.body) {
      throw it
    }
  }

  private suspend fun createMappingOrOnFailureDo(
    mapping: TemporaryAbsencesPrisonerMappingDto,
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

  private suspend fun createMapping(mapping: TemporaryAbsencesPrisonerMappingDto) = migrationMappingService.createMapping(
    mapping,
    object :
      ParameterizedTypeReference<DuplicateErrorResponse<TemporaryAbsencesPrisonerMappingDto>>() {},
  )

  private suspend fun requeueCreateMapping(
    mapping: TemporaryAbsencesPrisonerMappingDto,
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

  private fun OffenderTemporaryAbsencesResponse.buildMappings(prisonerNumber: String, migrationId: String, dpsResponse: MigrateTapResponse) = TemporaryAbsencesPrisonerMappingDto(
    prisonerNumber = prisonerNumber,
    migrationId = migrationId,
    bookings = bookings.map { booking ->
      TemporaryAbsenceBookingMappingDto(
        bookingId = booking.bookingId,
        applications = booking.temporaryAbsenceApplications.map { application ->
          TemporaryAbsenceApplicationMappingDto(
            nomisMovementApplicationId = application.movementApplicationId,
            dpsMovementApplicationId = dpsResponse.findDpsAuthorisationId(application.movementApplicationId),
            schedules = application.absences.mapNotNull { it.scheduledTemporaryAbsence }.map { it.toMappingDto(dpsResponse) },
            movements = application.absences.mapNotNull { it.temporaryAbsence }.map { it.toMappingDto(booking.bookingId, dpsResponse) } +
              application.absences.mapNotNull { it.temporaryAbsenceReturn }.map { it.toMappingDto(booking.bookingId, dpsResponse) },
          )
        },
        unscheduledMovements = booking.unscheduledTemporaryAbsences.map { it.toMappingDto(booking.bookingId, dpsResponse) } +
          booking.unscheduledTemporaryAbsenceReturns.map { it.toMappingDto(booking.bookingId, dpsResponse) },
      )
    },
  )

  private fun ScheduledTemporaryAbsence.toMappingDto(dpsResponse: MigrateTapResponse): ScheduledMovementMappingDto = ScheduledMovementMappingDto(
    nomisEventId = this.eventId,
    dpsOccurrenceId = dpsResponse.findDpsScheduleId(this.eventId),
    nomisAddressId = this.toAddressId,
    nomisAddressOwnerClass = this.toAddressOwnerClass,
    dpsAddressText = this.toFullAddress ?: "",
    dpsDescription = this.toAddressDescription,
    dpsPostcode = this.toAddressPostcode,
    eventTime = "${this.startTime}",
  )

  private fun TemporaryAbsence.toMappingDto(bookingId: Long, dpsResponse: MigrateTapResponse): ExternalMovementMappingDto = ExternalMovementMappingDto(
    nomisMovementSeq = this.sequence,
    dpsMovementId = dpsResponse.findDpsMovementId(bookingId, this.sequence),
    nomisAddressId = this.toAddressId,
    nomisAddressOwnerClass = this.toAddressOwnerClass,
    dpsAddressText = this.toFullAddress ?: "",
    dpsDescription = this.toAddressDescription,
    dpsPostcode = this.toAddressPostcode,
  )

  private fun TemporaryAbsenceReturn.toMappingDto(bookingId: Long, dpsResponse: MigrateTapResponse): ExternalMovementMappingDto = ExternalMovementMappingDto(
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
    ?: throw ExternalMovementMigrationException("No matching DPS authorisation found for nomis application id $nomisApplicationId, we found only ${temporaryAbsences.map { it.legacyId to it.id }}")

  private fun MigrateTapResponse.findDpsScheduleId(nomisEventId: Long): UUID {
    val occurrenceResponses = temporaryAbsences.flatMap { it.occurrences }
    return occurrenceResponses
      .firstOrNull { it.legacyId == nomisEventId }
      ?.id
      ?: throw ExternalMovementMigrationException("No matching DPS occurrence found for nomis event id $nomisEventId, we found only ${occurrenceResponses.map { it.legacyId to it.id }}")
  }

  private fun MigrateTapResponse.findDpsMovementId(nomisBookingId: Long, nomisMovementSeq: Int): UUID {
    val movementResponses = (temporaryAbsences.flatMap { it.occurrences.flatMap { it.movements } } + unscheduledMovements)
    return movementResponses
      .firstOrNull {
        val (bookingId, sequence) = it.legacyId.parseNomisMovementId()
        bookingId == nomisBookingId && sequence == nomisMovementSeq
      }
      ?.id
      ?: throw ExternalMovementMigrationException("No matching DPS movement found for nomis booking with sequence $nomisBookingId / $nomisMovementSeq, we found only ${movementResponses.map { it.legacyId to it.id }}")
  }

  private fun String.parseNomisMovementId() = split("_").let { it[0].toLong() to it[1].toInt() }

  private fun OffenderTemporaryAbsencesResponse.toDpsRequest() = MigrateTapRequest(
    temporaryAbsences = this.bookings.flatMap { booking ->
      booking.temporaryAbsenceApplications.map { application ->
        MigrateTapAuthorisation(
          prisonCode = application.prisonId,
          statusCode = application.applicationStatus.toDpsAuthorisationStatusCode(),
          absenceTypeCode = application.temporaryAbsenceType,
          absenceSubTypeCode = application.temporaryAbsenceSubType,
          absenceReasonCode = application.eventSubType,
          accompaniedByCode = application.escortCode ?: DEFAULT_ESCORT_CODE,
          transportCode = application.transportType ?: DEFAULT_TRANSPORT_TYPE,
          repeat = application.applicationType == "REPEATING",
          start = application.fromDate,
          end = application.toDate,
          comments = application.comment,
          created = SyncAtAndBy(application.audit.createDatetime, application.audit.createUsername),
          updated = application.audit.modifyDatetime?.let { SyncAtAndBy(application.audit.modifyDatetime, application.audit.modifyUserId ?: "") },
          legacyId = application.movementApplicationId,
          occurrences = application.absences.mapNotNull { absence ->
            absence.scheduledTemporaryAbsence?.let { scheduleOut ->
              scheduleOut.toDpsRequest(
                returnTime = absence.scheduledTemporaryAbsenceReturn?.startTime ?: scheduleOut.returnTime,
                schedulePrison = scheduleOut.fromPrison ?: application.prisonId,
                bookingId = booking.bookingId,
                movementOut = absence.temporaryAbsence,
                movementIn = absence.temporaryAbsenceReturn,
                temporaryAbsenceType = application.temporaryAbsenceType,
                temporaryAbsenceSubType = application.temporaryAbsenceSubType,
              )
            }
          },
        )
      }
    },
    unscheduledMovements = this.bookings.flatMap { booking ->
      booking.unscheduledTemporaryAbsences.map { movementOut ->
        movementOut.toDpsRequest(booking.bookingId, movementOut.fromPrison ?: "")
      } +
        booking.unscheduledTemporaryAbsenceReturns.map { movementIn ->
          movementIn.toDpsRequest(booking.bookingId, movementIn.toPrison ?: "")
        }
    },
  )

  private fun ScheduledTemporaryAbsence.toDpsRequest(
    temporaryAbsenceType: String?,
    temporaryAbsenceSubType: String?,
    returnTime: LocalDateTime,
    schedulePrison: String,
    bookingId: Long,
    movementOut: TemporaryAbsence?,
    movementIn: TemporaryAbsenceReturn?,
  ): MigrateTapOccurrence = MigrateTapOccurrence(
    isCancelled = eventStatus == "CANC",
    start = startTime,
    end = returnTime,
    location = Location(description = toAddressDescription, address = toFullAddress, postcode = toAddressPostcode),
    absenceTypeCode = temporaryAbsenceType,
    absenceSubTypeCode = temporaryAbsenceSubType,
    absenceReasonCode = eventSubType,
    accompaniedByCode = escort ?: DEFAULT_ESCORT_CODE,
    transportCode = transportType ?: DEFAULT_TRANSPORT_TYPE,
    contactInformation = contactPersonName,
    comments = comment,
    created = SyncAtAndBy(audit.createDatetime, audit.createUsername),
    updated = audit.modifyDatetime?.let { modified -> SyncAtAndBy(modified, audit.modifyUserId ?: "") },
    legacyId = eventId,
    movements = listOfNotNull(movementOut?.toDpsRequest(bookingId, schedulePrison), movementIn?.toDpsRequest(bookingId, schedulePrison)),
  )

  private fun TemporaryAbsenceReturn.toDpsRequest(
    bookingId: Long,
    schedulePrison: String,
  ): MigrateTapMovement = MigrateTapMovement(
    occurredAt = movementTime,
    direction = MigrateTapMovement.Direction.IN,
    absenceReasonCode = movementReason,
    location = Location(description = fromAddressDescription, address = fromFullAddress, postcode = fromAddressPostcode),
    accompaniedByCode = escort ?: DEFAULT_ESCORT_CODE,
    created = SyncAtAndBy(audit.createDatetime, audit.createUsername),
    legacyId = "${bookingId}_$sequence",
    accompaniedByComments = escortText,
    comments = commentText,
    updated = audit.modifyDatetime?.let { modified -> SyncAtAndBy(modified, audit.modifyUserId ?: "") },
    prisonCode = toPrison ?: schedulePrison,
  )

  private fun TemporaryAbsence.toDpsRequest(
    bookingId: Long,
    schedulePrison: String,
  ): MigrateTapMovement = MigrateTapMovement(
    occurredAt = movementTime,
    direction = MigrateTapMovement.Direction.OUT,
    absenceReasonCode = movementReason,
    location = Location(description = toAddressDescription, address = toFullAddress, postcode = toAddressPostcode),
    accompaniedByCode = escort ?: DEFAULT_ESCORT_CODE,
    created = SyncAtAndBy(audit.createDatetime, audit.createUsername),
    legacyId = "${bookingId}_$sequence",
    accompaniedByComments = escortText,
    comments = commentText,
    updated = audit.modifyDatetime?.let { modified -> SyncAtAndBy(modified, audit.modifyUserId ?: "") },
    prisonCode = fromPrison ?: schedulePrison,
  )
  override fun parseContextFilter(json: String): MigrationMessage<*, ExternalMovementsMigrationFilter> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<ExternalMovementsMigrationFilter, ByPageNumber>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonerId> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, TemporaryAbsencesPrisonerMappingDto> = jsonMapper.readValue(json)
}

class ExternalMovementMigrationException(message: String) : RuntimeException(message)
