package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingPersonLocations
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPersonLocationsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.AtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncCustodialSeries
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncExternalMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeries
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovement
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

    val nomisPersonLocations = personLocationNomisApi.getOffenderPersonLocationsOrNull(offenderNo)
      ?: OffenderPersonLocationsResponse(bookings = listOf())
    val dpsResponse = dpsApi.resyncPerson(offenderNo, nomisPersonLocations.toDpsRequest())
      ?: ResyncResponse(custodialSeries = listOf())
    val mappings = dpsResponse.toMappings(offenderNo, migrationId)

    // TODO SDIT-4312 - handle failures and requeue failed mappings
    createMappings(mappings).also {
      publishTelemetry(
        if (it.isError) "duplicate" else "migrated",
        mapOf(
          "offenderNo" to offenderNo,
          "migrationId" to migrationId,
        ),
      )
    }
  }

  private suspend fun createMappings(mappings: PersonLocationsMappingDto) = mappingApi.createMapping(
    mappings,
    object : ParameterizedTypeReference<DuplicateErrorResponse<PersonLocationsMappingDto>>() {},
  )

  private fun publishTelemetry(type: String, telemetry: Map<String, String>) {
    telemetryClient.trackEvent(
      "person-location-migration-entity-$type",
      telemetry,
    )
  }

  override suspend fun retryCreateMapping(context: MigrationContext<PersonLocationsMappingDto>) {
    // TODO SDIT-4312 - implement retrying the person location mappings
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, PersonLocationMigrationFilter> = jsonMapper.readValue(json)
  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<PersonLocationMigrationFilter, ByLastId<PrisonNumberAndRootOffenderId>>> = jsonMapper.readValue(json)
  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonNumberAndRootOffenderId> = jsonMapper.readValue(json)
  override fun parseContextMapping(json: String): MigrationMessage<*, PersonLocationsMappingDto> = jsonMapper.readValue(json)
}

fun OffenderPersonLocationsResponse.toDpsRequest() = ResyncExternalMovementsRequest(
  custodialSeries = bookings.map { booking ->
    ResyncCustodialSeries(
      custodialSeries = SyncCustodialSeries(
        status = when (booking.bookingStatus) {
          BookingPersonLocations.BookingStatus.OPEN -> SyncCustodialSeries.Status.OPEN
          BookingPersonLocations.BookingStatus.CLOSED -> SyncCustodialSeries.Status.CLOSED
        },
        isActive = booking.activeBooking,
        openedAt = booking.bookingBeginTime,
        closedAt = booking.bookingEndTime,
        legacyBookingId = booking.bookingId,
        legacyBookingReference = booking.bookingNumber,
      ),
      movements = booking.movements.map { it.toDpsMovement(booking.bookingId) },
      created = booking.audit.toCreated(),
      modified = booking.audit.toModified(),
    )
  },
)

private fun PersonLocationMovement.toDpsMovement(bookingId: Long) = ResyncExternalMovement(
  movement = SyncExternalMovement(
    type = when (directionCode) {
      PersonLocationMovement.DirectionCode.IN -> SyncExternalMovement.Type.ARRIVAL
      PersonLocationMovement.DirectionCode.OUT -> SyncExternalMovement.Type.DEPARTURE
    },
    reason = SyncExternalMovementReason(
      description = movementReason.description,
      reasonType = "CodedReason",
      code = movementReason.code,
      hierarchy = listOf(),
    ),
    occurredAt = movementTime,
    // TODO SDIT-4312 - DPS requires a from location but some very old NOMIS movements don't have one - waiting on DPS to decide how to handle this
    from = from?.toDpsLocation() ?: SyncExternalMovementFrom(description = "", locationType = "SimpleLocation", code = ""),
    to = to?.toDpsLocation(),
    journey = SyncExternalJourney(
      type = when (movementType) {
        PersonLocationMovement.MovementType.ADM -> SyncExternalJourney.Type.ADMISSION
        PersonLocationMovement.MovementType.CRT -> SyncExternalJourney.Type.COURT_APPEARANCE
        PersonLocationMovement.MovementType.TAP -> SyncExternalJourney.Type.TEMPORARY_ABSENCE
        PersonLocationMovement.MovementType.TRN -> SyncExternalJourney.Type.TRANSFER
        PersonLocationMovement.MovementType.REL -> SyncExternalJourney.Type.RELEASE
      },
      scheduleReference = scheduleId?.toString(),
    ),
    legacyBookingId = bookingId,
    legacySequenceNumber = sequence,
    notes = commentText,
  ),
  created = audit.toCreated(),
  modified = audit.toModified(),
)

private fun MovementLocation.toDpsLocation() = when (type) {
  MovementLocation.Type.PRISON -> SyncExternalMovementFrom(description = description, locationType = "Prison", code = code!!)
  MovementLocation.Type.COURT -> SyncExternalMovementFrom(description = description, locationType = "Court", code = code!!)
  // A DPS SimpleLocation has no code - it's only required here because the generated model flattens the oneOf location types
  MovementLocation.Type.OTHER -> SyncExternalMovementFrom(description = description, locationType = "SimpleLocation", code = "")
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
