package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.model.BedAssignmentNomisMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.model.NomisBedAssignment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.LocationsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BedAssignmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerBedAssignmentHistoryResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.UUID

private const val TELEMETRY_BASE = "bedassignment-synchronisation"

@Service
class BedAssignmentSyncService(
  private val bedAssignmentNomisApiService: BedAssignmentNomisApiService,
  private val locationsMappingService: LocationsMappingService,
  private val dpsApiService: BedAssignmentDpsApiService,
  private val mappingApiService: BedAssignmentMappingApiService,
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun create(event: BedAssignmentEvent) = synchronise(event, "created")

  suspend fun update(event: BedAssignmentEvent) = synchronise(event, "updated")

  suspend fun delete(event: BedAssignmentEvent) = synchronise(event, "deleted")

  private suspend fun synchronise(event: BedAssignmentEvent, action: String) {
    val telemetryName = "$TELEMETRY_BASE-$action"
    val telemetry = telemetryOf(
      "offenderNo" to event.offenderIdDisplay,
      "bookingId" to event.bookingId.toString(),
      "sequence" to event.bedAssignmentSeq.toString(),
    )
    // TODO not yet clear which events will have no audit module, skip for now and pick up via recon
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$telemetryName-skipped", telemetry.valuesAsStrings())
      return
    }
    track(telemetryName, telemetry) {
      val history = toDps(bedAssignmentNomisApiService.getBedAssignments(event.offenderIdDisplay))
      val result = dpsApiService.sync(event.offenderIdDisplay, history)
      telemetry["historyCount"] = history.size
      telemetry["createdCount"] = result.created.size
      telemetry["updatedCount"] = result.updated.size
      telemetry["deletedCount"] = result.deleted.size
      val mappings = BedAssignmentMappingRetry(
        mappings = (result.created + result.updated).map { it.toMapping() },
        deletedDpsIds = result.deleted.map { it.id.toString() },
      )
      try {
        reconcileMappings(mappings)
      } catch (e: Exception) {
        log.error("Failed to synchronise bed-assignment mappings for {}", event.offenderIdDisplay, e)
        telemetry["original-error"] = e.message ?: e.javaClass.name
        // Keep the original deleted IDs: a repeated DPS sync will no longer return those deletions.
        queueService.sendMessage(
          messageType = RETRY_SYNCHRONISATION_MAPPING.name,
          synchronisationType = SynchronisationType.BED_ASSIGNMENTS,
          message = mappings,
          telemetryAttributes = telemetry.valuesAsStrings(),
        )
        telemetry["mapping"] = "initial-failure"
      }
    }
  }

  suspend fun toDps(nomisHistory: PrisonerBedAssignmentHistoryResponse): List<NomisBedAssignment> {
    val locations = nomisHistory.bedAssignments.map { it.livingUnitId }.distinct().associateWith { toDpsLocation(it) }
    return nomisHistory.bedAssignments.map {
      NomisBedAssignment(
        bookingId = it.bookingId,
        sequence = it.sequence,
        livingUnitId = it.livingUnitId,
        livingUnitDescription = it.livingUnitDescription,
        locationId = locations[it.livingUnitId],
        assignmentDateTime = it.assignmentDateTime,
        assignmentEndDateTime = it.assignmentEndDateTime,
        assignmentReasonCode = it.assignmentReasonCode,
        createdBy = it.audit.createUsername,
      )
    }
  }

  private suspend fun toDpsLocation(livingUnitId: Long): UUID? = locationsMappingService
    .getMappingGivenNomisId(livingUnitId)?.dpsLocationId?.let(UUID::fromString)

  suspend fun retryCreateMapping(retryMessage: InternalMessage<BedAssignmentMappingRetry>) {
    try {
      reconcileMappings(retryMessage.body)
      telemetryClient.trackEvent("$TELEMETRY_BASE-mapping-created-success", retryMessage.telemetryAttributes)
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "$TELEMETRY_BASE-mapping-created-failure",
        retryMessage.telemetryAttributes + ("error" to (e.message ?: e.javaClass.name)),
      )
      throw e
    }
  }

  private suspend fun reconcileMappings(retry: BedAssignmentMappingRetry) {
    // Delete by DPS identity, never by a NOMIS key that might now point to a replacement row.
    retry.deletedDpsIds.forEach { mappingApiService.deleteMappingByDpsId(it) }
    retry.mappings.forEach { mapping ->
      val existing = mappingApiService.getMappingByNomisIdOrNull(mapping.nomisBookingId, mapping.nomisSequence)
      if (existing == null) {
        val result = mappingApiService.createMapping(
          mapping,
          object : ParameterizedTypeReference<DuplicateErrorResponse<BedAssignmentMappingDto>>() {},
        )
        result.errorResponse?.moreInfo?.let { duplicate ->
          reportDuplicate(duplicate.duplicate, duplicate.existing)
          check(duplicate.duplicate.isSameMappingAs(duplicate.existing)) {
            "Conflicting bed-assignment mapping for booking ${mapping.nomisBookingId}, sequence ${mapping.nomisSequence}"
          }
        }
      } else if (!mapping.isSameMappingAs(existing)) {
        reportDuplicate(mapping, existing)
        error("Conflicting bed-assignment mapping for booking ${mapping.nomisBookingId}, sequence ${mapping.nomisSequence}")
      }
    }
  }

  private fun reportDuplicate(duplicate: BedAssignmentMappingDto, existing: BedAssignmentMappingDto) {
    telemetryClient.trackEvent(
      "$TELEMETRY_BASE-duplicate",
      mapOf(
        "duplicateDpsBedAssignmentId" to duplicate.dpsBedAssignmentId,
        "duplicateBookingId" to duplicate.nomisBookingId.toString(),
        "duplicateSequence" to duplicate.nomisSequence.toString(),
        "existingDpsBedAssignmentId" to existing.dpsBedAssignmentId,
        "existingBookingId" to existing.nomisBookingId.toString(),
        "existingSequence" to existing.nomisSequence.toString(),
      ),
    )
  }
}

fun BedAssignmentMappingDto.isSameMappingAs(other: BedAssignmentMappingDto?): Boolean = other != null &&
  dpsBedAssignmentId == other.dpsBedAssignmentId &&
  nomisBookingId == other.nomisBookingId &&
  nomisSequence == other.nomisSequence

private fun BedAssignmentNomisMapping.toMapping() = BedAssignmentMappingDto(
  dpsBedAssignmentId = id.toString(),
  nomisBookingId = bookingId,
  nomisSequence = sequence,
  mappingType = BedAssignmentMappingDto.MappingType.NOMIS_CREATED,
)

data class BedAssignmentMappingRetry(
  val mappings: List<BedAssignmentMappingDto>,
  val deletedDpsIds: List<String>,
)
