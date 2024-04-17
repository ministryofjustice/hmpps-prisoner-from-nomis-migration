package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.MappingResponse.MAPPING_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.Alert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.DPS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class AlertsSynchronisationService(
  private val mappingApiService: AlertsMappingApiService,
  private val nomisApiService: AlertsNomisApiService,
  private val dpsApiService: AlertsDpsApiService,
  private val queueService: SynchronisationQueueService,
  private val telemetryClient: TelemetryClient,
  @Value("\${alerts.has-migrated-data:false}")
  private val hasMigratedAllData: Boolean,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun nomisAlertInserted(event: AlertInsertedEvent) {
    val telemetry =
      mapOf("bookingId" to event.bookingId, "alertSequence" to event.alertSeq, "offenderNo" to event.offenderIdDisplay)
    val nomisAlert = nomisApiService.getAlert(bookingId = event.bookingId, alertSequence = event.alertSeq)
    if (nomisAlert.audit.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("alert-synchronisation-created-skipped", telemetry)
    } else {
      val mapping = mappingApiService.getOrNullByNomisId(event.bookingId, event.alertSeq)
      if (mapping != null) {
        telemetryClient.trackEvent(
          "alert-synchronisation-created-ignored",
          telemetry + ("dpsAlertId" to mapping.dpsAlertId),
        )
      } else {
        dpsApiService.createAlert(
          nomisAlert.toDPSCreateAlert(event.offenderIdDisplay),
          createdByUsername = nomisAlert.audit.createUsername,
        ).run {
          tryToCreateMapping(
            offenderNo = event.offenderIdDisplay,
            nomisAlert = nomisAlert,
            dpsAlert = this,
            telemetry = telemetry,
          ).also { mappingCreateResult ->
            val mappingSuccessTelemetry =
              (if (mappingCreateResult == MAPPING_CREATED) mapOf() else mapOf("mapping" to "initial-failure"))
            val additionalTelemetry = mappingSuccessTelemetry + ("dpsAlertId" to this.alertUuid.toString())

            telemetryClient.trackEvent(
              "alert-synchronisation-created-success",
              telemetry + additionalTelemetry,
            )
          }
        }
      }
    }
  }

  suspend fun nomisAlertUpdated(event: AlertUpdatedEvent) {
    val telemetry =
      mapOf("bookingId" to event.bookingId, "alertSequence" to event.alertSeq, "offenderNo" to event.offenderIdDisplay)
    val nomisAlert = nomisApiService.getAlert(bookingId = event.bookingId, alertSequence = event.alertSeq)
    if (nomisAlert.audit.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("alert-synchronisation-updated-skipped", telemetry)
    } else {
      val mapping = mappingApiService.getOrNullByNomisId(event.bookingId, event.alertSeq)
      if (mapping == null) {
        telemetryClient.trackEvent(
          "alert-synchronisation-updated-failed",
          telemetry,
        )
        if (hasMigratedAllData) {
          // after migration has run this should not happen so make sure this message goes in DLQ
          throw IllegalStateException("Received ALERT-UPDATED for alert that has never been created")
        }
      } else {
        dpsApiService.updateAlert(
          alertId = mapping.dpsAlertId,
          nomisAlert.toDPSUpdateAlert(),
          updatedByUsername = nomisAlert.audit.modifyUserId ?: nomisAlert.audit.createUsername,
        )
        telemetryClient.trackEvent(
          "alert-synchronisation-updated-success",
          telemetry + ("dpsAlertId" to mapping.dpsAlertId),
        )
      }
    }
  }

  suspend fun nomisAlertDeleted(event: AlertUpdatedEvent) {
    val telemetry =
      mapOf("bookingId" to event.bookingId, "alertSequence" to event.alertSeq, "offenderNo" to event.offenderIdDisplay)
    val mapping = mappingApiService.getOrNullByNomisId(event.bookingId, event.alertSeq)
    if (mapping == null) {
      telemetryClient.trackEvent(
        "alert-synchronisation-deleted-ignored",
        telemetry,
      )
    } else {
      dpsApiService.deleteAlert(alertId = mapping.dpsAlertId)
      tryToDeletedMapping(mapping.dpsAlertId)
      telemetryClient.trackEvent(
        "alert-synchronisation-deleted-success",
        telemetry + ("dpsAlertId" to mapping.dpsAlertId),
      )
    }
  }

  private suspend fun tryToCreateMapping(
    offenderNo: String,
    nomisAlert: AlertResponse,
    dpsAlert: Alert,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    val mapping = AlertMappingDto(
      offenderNo = offenderNo,
      dpsAlertId = dpsAlert.alertUuid.toString(),
      nomisBookingId = nomisAlert.bookingId,
      nomisAlertSequence = nomisAlert.alertSequence,
      mappingType = DPS_CREATED,
    )

    try {
      mappingApiService.createMapping(mapping)
      return MAPPING_CREATED
    } catch (e: Exception) {
      log.error("Failed to create mapping for alert id $mapping", e)
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.ALERTS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
      return MAPPING_FAILED
    }
  }

  suspend fun retryCreateMapping(retryMessage: InternalMessage<AlertMappingDto>) {
    mappingApiService.createMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "alert-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }

  private suspend fun tryToDeletedMapping(dpsAlertId: String) = runCatching {
    mappingApiService.deleteMappingByDpsId(dpsAlertId)
  }.onFailure { e ->
    telemetryClient.trackEvent("alert-mapping-deleted-failed", mapOf("dpsAlertId" to dpsAlertId))
    log.warn("Unable to delete mapping for alert $dpsAlertId. Please delete manually", e)
  }
}

private enum class MappingResponse {
  MAPPING_CREATED,
  MAPPING_FAILED,
}
