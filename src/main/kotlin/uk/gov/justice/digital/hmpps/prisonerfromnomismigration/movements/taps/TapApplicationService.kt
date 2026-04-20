package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_APPLICATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementApplicationEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncAtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TapApplication
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.*

private const val TELEMETRY_PREFIX: String = "${TAP_TELEMETRY_PREFIX}-application"

@Service
class TapApplicationService(
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
  private val mappingApiService: ExternalMovementsMappingApiService,
  private val nomisApiService: TapsNomisApiService,
  private val dpsApiService: ExternalMovementsDpsApiService,
) : TelemetryEnabled {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun movementApplicationInserted(event: MovementApplicationEvent) {
    val (nomisApplicationId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisApplicationId" to nomisApplicationId,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-skipped", telemetry)
      return
    }

    mappingApiService.getApplicationMappingOrNull(nomisApplicationId)
      ?.also { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-ignored", telemetry) }
      ?: run {
        track("${TELEMETRY_PREFIX}-inserted", telemetry) {
          nomisApiService.getTapApplication(prisonerNumber, nomisApplicationId)
            .also {
              val dpsApplicationId = dpsApiService.syncTapAuthorisation(prisonerNumber, it.toDpsRequest())
                .id
                .also { telemetry["dpsAuthorisationId"] = it }
              val mapping = TemporaryAbsenceApplicationSyncMappingDto(prisonerNumber, bookingId, nomisApplicationId, dpsApplicationId, NOMIS_CREATED)
              tryToCreateApplicationMapping(mapping, telemetry)
            }
        }
      }
  }

  suspend fun movementApplicationUpdated(event: MovementApplicationEvent) {
    val (nomisApplicationId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisApplicationId" to nomisApplicationId,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-updated-skipped", telemetry)
      return
    }

    track("${TELEMETRY_PREFIX}-updated", telemetry) {
      val dpsApplicationId = mappingApiService.getApplicationMappingOrNull(nomisApplicationId)?.dpsMovementApplicationId
        ?.also { telemetry["dpsAuthorisationId"] = it }
        ?: throw IllegalStateException("No mapping found when handling an update event for TAP application $nomisApplicationId - hopefully messages are being processed out of order and this event will succeed on a retry once the create event is processed. Otherwise we need to understand why the original create event was never processed.")

      val nomisApplication = nomisApiService.getTapApplication(prisonerNumber, nomisApplicationId)
      dpsApiService.syncTapAuthorisation(prisonerNumber, nomisApplication.toDpsRequest(dpsApplicationId))
    }
  }

  suspend fun movementApplicationDeleted(event: MovementApplicationEvent) {
    val (nomisApplicationId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisApplicationId" to nomisApplicationId,
    )
    mappingApiService.getApplicationMappingOrNull(nomisApplicationId)?.also {
      track("${TELEMETRY_PREFIX}-deleted", telemetry) {
        telemetry["dpsAuthorisationId"] = it.dpsMovementApplicationId
        dpsApiService.deleteTapAuthorisation(it.dpsMovementApplicationId)
        mappingApiService.deleteApplicationMapping(nomisApplicationId)
      }
    } ?: run { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-ignored", telemetry) }
  }
  private suspend fun tryToCreateApplicationMapping(mapping: TemporaryAbsenceApplicationSyncMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      mappingApiService.createApplicationMapping(mapping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "${TELEMETRY_PREFIX}-inserted-duplicate",
            mapOf(
              "existingOffenderNo" to existing!!.prisonerNumber,
              "existingBookingId" to existing.bookingId,
              "existingNomisApplicationId" to existing.nomisMovementApplicationId,
              "existingDpsApplicationId" to existing.dpsMovementApplicationId,
              "duplicateOffenderNo" to duplicate.prisonerNumber,
              "duplicateBookingId" to duplicate.bookingId,
              "duplicateNomisApplicationId" to duplicate.nomisMovementApplicationId,
              "duplicateDpsApplicationId" to duplicate.dpsMovementApplicationId,
            ),
          )
        }
      }
    } catch (e: Exception) {
      log.error("Failed to create mapping for temporary absence application NOMIS id ${mapping.nomisMovementApplicationId}", e)
      queueService.sendMessage(
        messageType = RETRY_MAPPING_TEMPORARY_ABSENCE_APPLICATION.name,
        synchronisationType = SynchronisationType.EXTERNAL_MOVEMENTS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateApplicationMapping(retryMessage: InternalMessage<TemporaryAbsenceApplicationSyncMappingDto>) {
    mappingApiService.createApplicationMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "${TELEMETRY_PREFIX}-mapping-retry-created",
        retryMessage.telemetryAttributes,
      )
    }
  }
}

fun TapApplication.toDpsRequest(id: UUID? = null) = SyncWriteTapAuthorisation(
  id = id,
  prisonCode = prisonId,
  statusCode = applicationStatus.toDpsAuthorisationStatusCode(toDate, latestBooking),
  absenceTypeCode = tapType,
  absenceSubTypeCode = tapSubType,
  absenceReasonCode = eventSubType,
  accompaniedByCode = escortCode ?: DEFAULT_ESCORT_CODE,
  repeat = applicationType == "REPEATING",
  start = fromDate,
  end = toDate,
  comments = comment,
  created = SyncAtAndBy(audit.createDatetime, audit.createUsername),
  updated = audit.modifyDatetime?.let { SyncAtAndBy(audit.modifyDatetime, audit.modifyUserId!!) },
  legacyId = tapApplicationId,
  transportCode = transportType ?: DEFAULT_TRANSPORT_TYPE,
  startTime = "${releaseTime.toLocalTime()}",
  endTime = "${returnTime.toLocalTime()}",
  location = Location(description = toAddressDescription, address = toFullAddress, postcode = toAddressPostcode),
)
