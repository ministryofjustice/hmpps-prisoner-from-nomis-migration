package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.doesOriginateInDps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.*

private const val TELEMETRY_PREFIX: String = "temporary-absence-sync"

@Service
class ExternalMovementsSyncService(
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
  private val mappingApiService: ExternalMovementsMappingApiService,
  private val nomisApiService: ExternalMovementsNomisApiService,
) : TelemetryEnabled {
  suspend fun movementApplicationInserted(event: MovementApplicationEvent) {
    val (nomisApplicationId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisApplicationId" to nomisApplicationId,
    )

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-application-inserted-skipped", telemetry)
      return
    }

    mappingApiService.getApplicationMapping(nomisApplicationId)
      ?.also { telemetryClient.trackEvent("$TELEMETRY_PREFIX-application-inserted-ignored", telemetry) }
      ?: run {
        track("$TELEMETRY_PREFIX-application-inserted", telemetry) {
          nomisApiService.getTemporaryAbsenceApplication(prisonerNumber, nomisApplicationId)
            .also {
              // TODO call DPS to synchronise application
              val dpsApplicationId = UUID.randomUUID().also { telemetry["dpsApplicationId"] = it }
              val mapping = TemporaryAbsenceApplicationSyncMappingDto(prisonerNumber, bookingId, nomisApplicationId, dpsApplicationId, NOMIS_CREATED)
              tryToCreateMapping(mapping, telemetry)
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

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-application-updated-skipped", telemetry)
      return
    }

    track("$TELEMETRY_PREFIX-application-updated", telemetry) {
      val dpsApplicationId = mappingApiService.getApplicationMapping(nomisApplicationId)!!.dpsMovementApplicationId
        .also { telemetry["dpsApplicationId"] = it }
      val nomisApplication = nomisApiService.getTemporaryAbsenceApplication(prisonerNumber, nomisApplicationId)
      // TODO update DPS
    }
  }

  private suspend fun tryToCreateMapping(maoping: TemporaryAbsenceApplicationSyncMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      mappingApiService.createApplicationMapping(maoping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "$TELEMETRY_PREFIX-application-inserted-duplicate",
            mapOf(
              "existingOffenderNo" to existing.prisonerNumber,
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
      log.error("Failed to create mapping for temporary absence application NOMIS id ${maoping.nomisMovementApplicationId}", e)
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.EXTERNALL_MOVEMENTS,
        message = maoping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateMapping(retryMessage: InternalMessage<TemporaryAbsenceApplicationSyncMappingDto>) {
    mappingApiService.createApplicationMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-application-inserted--success",
        retryMessage.telemetryAttributes,
      )
    }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
