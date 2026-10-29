package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CAS_URN_PREFIX
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.RAS_URN_PREFIX
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TAP_URN_PREFIX
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TRN_URN_PREFIX
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPersonLocationsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationBooking.BookingStatus.CLOSED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationBooking.BookingStatus.OPEN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByIdRangeMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.PERSON_LOCATIONS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import java.util.*

@Service
class PersonLocationMigrationService(
  private val mappingApi: PersonLocationMappingApiService,
  private val personLocationNomisApi: PersonLocationNomisApiService,
  private val dpsApi: PersonLocationDpsApiService,
  private val nomisApi: NomisApiService,
  private val tapMappingApi: TapMappingApiService,
  private val courtSchedulerMappingApi: CourtSchedulerMappingApiService,
  private val courtSentencingMappingApi: CourtSentencingMappingApiService,
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
    val ignoreMissingPersonLocations = context.properties["ignoreMissingPersonLocations"] as Boolean? ?: true

    runCatching {
      val nomisPersonLocations = personLocationNomisApi.getOffenderPersonLocationsOrNull(offenderNo)
        ?: OffenderPersonLocationsResponse(bookings = listOf())
      if (ignoreMissingPersonLocations && nomisPersonLocations.bookings.isEmpty()) {
        publishTelemetry("ignored", telemetry.apply { this["reason"] = "The offender has no person locations" })
        return
      }

      val existingMappings = nomisPersonLocations.getExistingMappings()
      val dpsScheduleReferences = nomisPersonLocations.getDpsScheduleReferences(offenderNo)
      val dpsResponse = dpsApi.resyncPerson(offenderNo, nomisPersonLocations.toDpsRequest(existingMappings, dpsScheduleReferences))
        ?: ResyncResponse(custodialSeries = listOf())
      val mappings = dpsResponse.toMappings(offenderNo, migrationId)

      createMappingOrOnFailureDo(mappings) { requeueCreateMapping(mappings, context) }
    }
      .onFailure {
        publishTelemetry("failed", telemetry.apply { this["reason"] = it.message ?: "Unknown error" })
        throw it
      }
  }

  suspend fun resyncPrisonerPersonLocations(prisonerNumber: String) = migrateNomisEntity(
    MigrationContext(
      PERSON_LOCATIONS,
      generateBatchId(),
      1,
      PrisonNumberAndRootOffenderId(rootOffenderId = 0, prisonNumber = prisonerNumber),
      mutableMapOf("ignoreMissingPersonLocations" to false),
    ),
  )

  private suspend fun OffenderPersonLocationsResponse.getExistingMappings(): ExistingMappings = bookings
    .map { it.booking.bookingId }
    .takeIf { it.isNotEmpty() }
    ?.let { mappingApi.getMappings(it) }
    .orEmpty()
    .associateBy { it.bookingId }

  private suspend fun OffenderPersonLocationsResponse.getDpsScheduleReferences(offenderNo: String): DpsScheduleReferences = bookings
    .flatMap { it.movements }
    .filter { it.scheduleId != null }
    .map { it.movementType }
    .distinct()
    .associateWith { getDpsScheduleReferences(offenderNo, it, this) }

  private suspend fun getDpsScheduleReferences(
    offenderNo: String,
    movementType: PersonLocationMovement.MovementType,
    nomisPersonLocations: OffenderPersonLocationsResponse,
  ): Map<Long, String> = when (movementType) {
    TAP -> tapMappingApi.getTapPrisonerMappingIds(offenderNo).schedules
      .associate { it.nomisEventId to "$TAP_URN_PREFIX${it.dpsOccurrenceId}" }
    CRT -> buildMap {
      putAll(
        getCourtSentencingMappings(nomisPersonLocations)
          .associate { it.nomisCourtAppearanceId to "$RAS_URN_PREFIX${it.dpsCourtAppearanceId}" },
      )
      putAll(
        courtSchedulerMappingApi.getCourtSchedulerPrisonMappingIds(offenderNo).schedules
          .associate { it.nomisEventId to "$CAS_URN_PREFIX${it.dpsCourtAppearanceId}" },
      )
    }
    TRN -> transferScheduleMappingApi.getMappings(offenderNo).schedules
      .associate { it.nomisEventId to "$TRN_URN_PREFIX${it.dpsTransferScheduleId}" }
    // There are no DPS schedules for admissions or releases
    ADM, REL -> emptyMap()
  }

  private suspend fun getCourtSentencingMappings(
    nomisPersonLocations: OffenderPersonLocationsResponse,
  ): List<CourtAppearanceMappingDto> = // Schedules owned by court sentencing are not in the court scheduler mappings, so use any sentencing mappings for the rest
    nomisPersonLocations.bookings
      .flatMap { it.movements }
      .filter { it.movementType == CRT }
      .mapNotNull { it.scheduleId }
      .distinct()
      .takeIf { it.isNotEmpty() }
      ?.let { courtSentencingMappingApi.getAllCourtAppearancesByNomisIds(it) }
      .orEmpty()

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

// Existing mappings keyed by NOMIS booking ID
typealias ExistingMappings = Map<Long, PersonLocationBookingMappingDto>

fun OffenderPersonLocationsResponse.toDpsRequest(existingMappings: ExistingMappings, dpsScheduleReferences: DpsScheduleReferences) = ResyncExternalMovementsRequest(
  custodialSeries = bookings.map { personLocationBooking ->
    with(personLocationBooking.booking) {
      val bookingMapping = existingMappings[bookingId]
      val dpsMovementIds = bookingMapping?.movements.orEmpty().associate { it.nomisMovementSeq to it.dpsExternalMovementId }
      ResyncCustodialSeries(
        custodialSeries = SyncCustodialSeries(
          dpsId = bookingMapping?.dpsCustodialSeriesId,
          status = when (bookingStatus) {
            OPEN -> SyncCustodialSeries.Status.OPEN
            CLOSED -> SyncCustodialSeries.Status.CLOSED
          },
          isActive = activeBooking,
          openedAt = bookingBeginTime,
          closedAt = bookingEndTime,
          legacyBookingId = bookingId,
          legacyBookingReference = bookingNumber,
        ),
        movements = personLocationBooking.movements.map {
          it.toDpsMovement(dpsMovementIds[it.sequence], bookingMapping?.dpsCustodialSeriesId, dpsScheduleReferences)
        },
        created = audit.toCreated(),
        modified = audit.toModified(),
      )
    }
  },
)

private fun PersonLocationMovement.toDpsMovement(
  dpsId: UUID?,
  dpsCustodySeriesId: UUID?,
  dpsScheduleReferences: DpsScheduleReferences,
) = ResyncExternalMovement(
  movement = toSyncExternalMovement(
    dpsId = dpsId,
    dpsCustodySeriesId = dpsCustodySeriesId,
    scheduleReference = scheduleId?.let { dpsScheduleReferences[movementType]?.get(it) },
  ),
  created = audit.toCreated(),
  modified = audit.toModified(),
)

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
