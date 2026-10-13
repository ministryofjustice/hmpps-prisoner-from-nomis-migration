package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingPersonLocations.BookingStatus.CLOSED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingPersonLocations.BookingStatus.OPEN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation.Type.COURT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation.Type.OTHER
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation.Type.PRISON
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPersonLocationsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.DirectionCode.IN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.DirectionCode.OUT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.ADM
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.CRT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.REL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.TAP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.TRN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.AtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncCustodialSeries
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncExternalMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeries
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney.Type.ADMISSION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney.Type.COURT_APPEARANCE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney.Type.RELEASE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney.Type.TEMPORARY_ABSENCE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney.Type.TRANSFER
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovement.Type.ARRIVAL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovement.Type.DEPARTURE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovementFrom
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovementReason
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByIdRangeMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.PERSON_LOCATIONS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class PersonLocationMigrationService(
  private val mappingApi: PersonLocationMappingApiService,
  private val personLocationNomisApi: PersonLocationNomisApiService,
  private val dpsApi: PersonLocationDpsApiService,
  private val nomisApi: NomisApiService,
  private val tapMappingApi: TapMappingApiService,
  private val courtSchedulerMappingApi: CourtSchedulerMappingApiService,
  private val transferScheduleMappingApi: TransferScheduleMappingApiService,
  jsonMapper: JsonMapper,
  @Value($$"${personlocations.page.size:1000}") pageSize: Long,
  @Value($$"${personlocations.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${personlocations.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${personlocations.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByIdRangeMigrationService<PersonLocationMigrationFilter, PrisonNumberAndRootOffenderId, PersonLocationsMappingDto>(
  mappingService = mappingApi,
  migrationType = PERSON_LOCATIONS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  override suspend fun getTotalNumberOfIds(migrationFilter: PersonLocationMigrationFilter): Long = if (migrationFilter.prisonerNumber.isNullOrBlank()) {
    nomisApi.getPrisonerIds(0, 1).totalElements
  } else {
    1L
  }

  override suspend fun getRangeOfIds(
    body: PersonLocationMigrationFilter,
    pageSize: Long,
  ): List<Pair<PrisonNumberAndRootOffenderId, PrisonNumberAndRootOffenderId>> = if (body.prisonerNumber.isNullOrBlank()) {
    nomisApi.getAllPrisonersIdRanges(pageSize)
      .map { Pair(PrisonNumberAndRootOffenderId(it.fromId, ""), PrisonNumberAndRootOffenderId(it.toId, "")) }
  } else {
    // The prisoner number is supplied to us, so pretend there's a single range to get
    listOf(PrisonNumberAndRootOffenderId(0, "") to PrisonNumberAndRootOffenderId(1, ""))
  }

  override suspend fun getPageOfIdsFromIdRange(
    firstId: PrisonNumberAndRootOffenderId?,
    lastId: PrisonNumberAndRootOffenderId?,
    migrationFilter: PersonLocationMigrationFilter,
  ): List<PrisonNumberAndRootOffenderId> = if (migrationFilter.prisonerNumber.isNullOrBlank()) {
    nomisApi.getAllPrisonersInRange(firstId!!.rootOffenderId, lastId!!.rootOffenderId)
  } else {
    // If a single prisoner migration is requested, then we'll trust the input as we're probably testing. Pretend that we called nomis-prisoner-api which found a single prisoner.
    listOf(PrisonNumberAndRootOffenderId(0, migrationFilter.prisonerNumber))
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonNumberAndRootOffenderId>) {
    val offenderNo = context.body.prisonNumber
    val migrationId = context.migrationId
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "migrationId" to migrationId,
    )

    runCatching {
      val nomisPersonLocations = personLocationNomisApi.getOffenderPersonLocationsOrNull(offenderNo)
        ?: OffenderPersonLocationsResponse(bookings = listOf())
      if (nomisPersonLocations.bookings.isEmpty()) {
        publishTelemetry("ignored", telemetry.apply { this["reason"] = "The offender has no person locations" })
        return
      }

      val dpsScheduleReferences = nomisPersonLocations.getDpsScheduleReferences(offenderNo)
      val dpsResponse = dpsApi.resyncPerson(offenderNo, nomisPersonLocations.toDpsRequest(dpsScheduleReferences))
        ?: ResyncResponse(custodialSeries = listOf())
      val mappings = dpsResponse.toMappings(offenderNo, migrationId)

      createMappingOrOnFailureDo(mappings) { requeueCreateMapping(mappings, context) }
    }
      .onFailure {
        publishTelemetry("failed", telemetry.apply { this["reason"] = it.message ?: "Unknown error" })
        throw it
      }
  }

  private suspend fun OffenderPersonLocationsResponse.getDpsScheduleReferences(offenderNo: String): DpsScheduleReferences = bookings
    .flatMap { it.movements }
    .filter { it.scheduleId != null }
    .map { it.movementType }
    .distinct()
    .associateWith { getDpsScheduleReferences(offenderNo, it) }

  private suspend fun getDpsScheduleReferences(offenderNo: String, movementType: PersonLocationMovement.MovementType): Map<Long, String> = when (movementType) {
    TAP -> tapMappingApi.getTapPrisonerMappingIds(offenderNo).schedules
      .associate { it.nomisEventId to "urn:temporary-absence-scheduler:temporary-absence:${it.dpsOccurrenceId}" }
    CRT -> courtSchedulerMappingApi.getCourtSchedulerPrisonMappingIds(offenderNo).schedules
      .associate { it.nomisEventId to "urn:court-appearance-scheduler:court-appearance:${it.dpsCourtAppearanceId}" }
    TRN -> transferScheduleMappingApi.getMappings(offenderNo).schedules
      .associate { it.nomisEventId to "urn:transfer-scheduler:transfer:${it.dpsTransferScheduleId}" }
    // There are no DPS schedules for admissions or releases
    ADM, REL -> emptyMap()
  }

  private suspend fun createMappingOrOnFailureDo(
    mappings: PersonLocationsMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      createMappings(mappings)
    }.onSuccess {
      publishTelemetry(
        if (it.isError) "duplicate" else "migrated",
        mapOf(
          "offenderNo" to mappings.offenderNo,
          "migrationId" to mappings.migrationId,
        ),
      )
    }.onFailure {
      failureHandler(it)
    }
  }

  private suspend fun createMappings(mappings: PersonLocationsMappingDto) = mappingApi.createMapping(
    mappings,
    object : ParameterizedTypeReference<DuplicateErrorResponse<PersonLocationsMappingDto>>() {},
  )

  private suspend fun requeueCreateMapping(
    mappings: PersonLocationsMappingDto,
    context: MigrationContext<*>,
  ) {
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = mappings,
      ),
    )
  }

  private fun publishTelemetry(type: String, telemetry: Map<String, String>) {
    telemetryClient.trackEvent(
      "person-location-migration-entity-$type",
      telemetry,
    )
  }

  override suspend fun retryCreateMapping(context: MigrationContext<PersonLocationsMappingDto>) {
    createMappingOrOnFailureDo(context.body) {
      throw it
    }
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, PersonLocationMigrationFilter> = jsonMapper.readValue(json)
  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<PersonLocationMigrationFilter, ByLastId<PrisonNumberAndRootOffenderId>>> = jsonMapper.readValue(json)
  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonNumberAndRootOffenderId> = jsonMapper.readValue(json)
  override fun parseContextMapping(json: String): MigrationMessage<*, PersonLocationsMappingDto> = jsonMapper.readValue(json)
}

// DPS schedule references keyed by movement type then NOMIS schedule (event) ID
typealias DpsScheduleReferences = Map<PersonLocationMovement.MovementType, Map<Long, String>>

fun OffenderPersonLocationsResponse.toDpsRequest(dpsScheduleReferences: DpsScheduleReferences) = ResyncExternalMovementsRequest(
  custodialSeries = bookings.map { booking ->
    ResyncCustodialSeries(
      custodialSeries = SyncCustodialSeries(
        status = when (booking.bookingStatus) {
          OPEN -> SyncCustodialSeries.Status.OPEN
          CLOSED -> SyncCustodialSeries.Status.CLOSED
        },
        isActive = booking.activeBooking,
        openedAt = booking.bookingBeginTime,
        closedAt = booking.bookingEndTime,
        legacyBookingId = booking.bookingId,
        legacyBookingReference = booking.bookingNumber,
      ),
      movements = booking.movements.map { it.toDpsMovement(booking.bookingId, dpsScheduleReferences) },
      created = booking.audit.toCreated(),
      modified = booking.audit.toModified(),
    )
  },
)

private fun PersonLocationMovement.toDpsMovement(bookingId: Long, dpsScheduleReferences: DpsScheduleReferences) = ResyncExternalMovement(
  movement = SyncExternalMovement(
    type = when (directionCode) {
      IN -> ARRIVAL
      OUT -> DEPARTURE
    },
    reason = SyncExternalMovementReason(
      description = movementReason.description,
      reasonType = "CodedReason",
      code = movementReason.code,
      hierarchy = listOf(),
    ),
    occurredAt = movementTime,
    from = toDpsFromLocation(bookingId),
    to = to?.toDpsLocation(),
    journey = SyncExternalJourney(
      type = when (movementType) {
        ADM -> ADMISSION
        CRT -> COURT_APPEARANCE
        TAP -> TEMPORARY_ABSENCE
        TRN -> TRANSFER
        REL -> RELEASE
      },
      scheduleReference = scheduleId?.let { dpsScheduleReferences[movementType]?.get(it) },
    ),
    legacyBookingId = bookingId,
    legacySequenceNumber = sequence,
    notes = commentText,
  ),
  created = audit.toCreated(),
  modified = audit.toModified(),
)

private fun PersonLocationMovement.toDpsFromLocation(bookingId: Long) = from?.toDpsLocation()
  ?: when (directionCode) {
    // Some very old inbound NOMIS movements have no from location
    IN -> SyncExternalMovementFrom(description = "Outside", locationType = "SimpleLocation", code = "")
    OUT -> throw IllegalStateException("Outbound movement $bookingId/$sequence has no from location")
  }

private fun MovementLocation.toDpsLocation() = when (type) {
  PRISON -> SyncExternalMovementFrom(description = description, locationType = "Prison", code = code!!)
  COURT -> SyncExternalMovementFrom(description = description, locationType = "Court", code = code!!)
  // A DPS SimpleLocation has no code - it's only required here because the generated model flattens the oneOf location types
  OTHER -> SyncExternalMovementFrom(description = description, locationType = "SimpleLocation", code = "")
}

private fun NomisAudit.toCreated() = AtAndBy(at = createDatetime, by = createUsername.toDpsUser())
private fun NomisAudit.toModified() = modifyDatetime?.let { AtAndBy(at = it, by = modifyUserId!!.toDpsUser()) }

fun ResyncResponse.toMappings(offenderNo: String, migrationId: String) = PersonLocationsMappingDto(
  offenderNo = offenderNo,
  migrationId = migrationId,
  bookings = custodialSeries.map { series ->
    PersonLocationBookingMappingDto(
      bookingId = series.legacyBookingId,
      dpsCustodialSeriesId = series.dpsId,
      movements = series.movements.map {
        PersonLocationMovementMappingDto(
          nomisMovementSeq = it.legacySequenceNumber,
          dpsExternalMovementId = it.dpsId,
        )
      },
    )
  },
)
