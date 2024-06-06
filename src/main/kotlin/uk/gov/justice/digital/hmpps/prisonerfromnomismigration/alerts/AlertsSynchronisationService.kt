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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.NOMIS_CREATED
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
    if (nomisAlert.isSourcedFromDPS()) {
      telemetryClient.trackEvent("alert-synchronisation-created-skipped", telemetry)
    } else {
      if (nomisAlert.isCreatedDueToNewBooking()) {
        moveAlertMappingsToNewBooking(event, telemetry)
      } else {
        val mapping = mappingApiService.getOrNullByNomisId(event.bookingId, event.alertSeq)
        if (mapping != null) {
          telemetryClient.trackEvent(
            "alert-synchronisation-created-ignored",
            telemetry + ("dpsAlertId" to mapping.dpsAlertId),
          )
        } else {
          if (nomisAlert.shouldNotBeCreatedInDPS()) {
            telemetryClient.trackEvent("alert-synchronisation-created-ignored-previous-booking", telemetry + ("bookingSequence" to nomisAlert.bookingSequence))
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
    }
  }

  suspend fun nomisAlertUpdated(event: AlertUpdatedEvent) {
    val telemetry =
      mapOf("bookingId" to event.bookingId, "alertSequence" to event.alertSeq, "offenderNo" to event.offenderIdDisplay)
    val nomisAlert = nomisApiService.getAlert(bookingId = event.bookingId, alertSequence = event.alertSeq)
    if (nomisAlert.isSourcedFromDPS()) {
      telemetryClient.trackEvent("alert-synchronisation-updated-skipped", telemetry)
    } else {
      val mapping = mappingApiService.getOrNullByNomisId(event.bookingId, event.alertSeq)
      if (mapping == null) {
        if (nomisAlert.shouldNotBeCreatedInDPS()) {
          // if we have no mapping and should never have been in DPS in the first place silently ignore
          // if we do have a mapping update anyway even if it shouldn't have been in DPS - since this is edge case that is not
          // significant enough to try to error on i.e. the scenario would have been it was migrated but since then a new alert
          // has been created in DPS so we can just keep the old one forever rather than some weird logic of deleting it from DPS
          telemetryClient.trackEvent("alert-synchronisation-updated-ignored-previous-booking", telemetry + ("bookingSequence" to nomisAlert.bookingSequence))
        } else {
          telemetryClient.trackEvent("alert-synchronisation-updated-failed", telemetry)
          if (hasMigratedAllData) {
            // after migration has run this should not happen so make sure this message goes in DLQ
            throw IllegalStateException("Received ALERT-UPDATED for alert that has never been created")
          }
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
      mappingType = NOMIS_CREATED,
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

  suspend fun synchronisePrisonerMerge(prisonerMergeEvent: PrisonerMergeDomainEvent) {
    val bookingId = prisonerMergeEvent.additionalInformation.bookingId
    val offenderNo = prisonerMergeEvent.additionalInformation.nomsNumber
    val removedOffenderNo = prisonerMergeEvent.additionalInformation.removedNomsNumber
    val alerts = nomisApiService.getAlertsByBookingId(bookingId).alerts
    val newAlerts = alerts.filter { mappingApiService.getOrNullByNomisId(bookingId = it.bookingId, alertSequence = it.alertSequence) == null }
    val telemetry = mapOf(
      "bookingId" to bookingId,
      "offenderNo" to offenderNo,
      "removedOffenderNo" to removedOffenderNo,
      "newAlertsCount" to newAlerts.size,
      "newAlerts" to newAlerts.map { it.alertSequence }.joinToString(),
    )
    val dpsResponse = dpsApiService.mergePrisonerAlerts(offenderNo = offenderNo, removedOffenderNo = removedOffenderNo, alerts = newAlerts.map { it.toDPSMergeAlert() })
    val mappings = dpsResponse.alertsCreated.map {
      AlertMappingDto(
        dpsAlertId = it.alertUuid.toString(),
        nomisBookingId = it.offenderBookId,
        nomisAlertSequence = it.alertSeq.toLong(),
        offenderNo = offenderNo,
        mappingType = NOMIS_CREATED,
      )
    }

    val mappingResponse = tryToCreateMappings(mappings, telemetry)
    telemetryClient.trackEvent(
      "from-nomis-synch-alerts-merge",
      telemetry + ("mappingSuccess" to (mappingResponse == MAPPING_CREATED).toString()),
    )
  }

  private suspend fun tryToCreateMappings(
    mappings: List<AlertMappingDto>,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    try {
      return createMappingsBatch(mappings, telemetry)
    } catch (e: Exception) {
      log.error("Failed to create mappings for alert id $mappings", e)
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING_BATCH.name,
        synchronisationType = SynchronisationType.ALERTS,
        message = mappings,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
      return MAPPING_FAILED
    }
  }

  private suspend fun createMappingsBatch(
    mappings: List<AlertMappingDto>,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    mappingApiService.createMappingsBatch(mappings).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "from-nomis-sync-alert-duplicate",
          mapOf<String, String>(
            "offenderNo" to telemetry["offenderNo"].toString(),
            "duplicateDpsAlertId" to duplicateErrorDetails.duplicate.dpsAlertId,
            "duplicateNomisBookingId" to duplicateErrorDetails.duplicate.nomisBookingId.toString(),
            "duplicateNomisAlertSequence" to duplicateErrorDetails.duplicate.nomisAlertSequence.toString(),
            "existingDpsAlertId" to duplicateErrorDetails.existing.dpsAlertId,
            "existingNomisBookingId" to duplicateErrorDetails.existing.nomisBookingId.toString(),
            "existingNomisAlertSequence" to duplicateErrorDetails.existing.nomisAlertSequence.toString(),
          ),
          null,
        )
        return MAPPING_FAILED
      }
    }
    return MAPPING_CREATED
  }

  suspend fun retryCreateMappingsBatch(retryMessage: InternalMessage<List<AlertMappingDto>>) {
    createMappingsBatch(mappings = retryMessage.body, telemetry = retryMessage.telemetryAttributes)
      .also {
        if (it == MAPPING_CREATED) {
          telemetryClient.trackEvent(
            "alert-mapping-created-merge-success",
            retryMessage.telemetryAttributes,
          )
        }
      }
  }

  suspend fun moveAlertMappingsToNewBooking(event: AlertInsertedEvent, telemetry: Map<String, Any>) {
    val previousBooking = nomisApiService.getBookingPreviousTo(offenderNo = event.offenderIdDisplay, bookingId = event.bookingId)
    mappingApiService.updateNomisMappingId(previousBookingId = previousBooking.bookingId, alertSequence = event.alertSeq, newBookingId = event.bookingId)?.also { mapping ->
      telemetryClient.trackEvent(
        "alert-synchronisation-booking-transfer-success",
        telemetry + mapOf("dpsAlertId" to mapping.dpsAlertId, "previousBookingId" to previousBooking.bookingId.toString()),
      )
    } ?: run {
      telemetryClient.trackEvent(
        "alert-synchronisation-booking-transfer-failed",
        telemetry + ("previousBookingId" to previousBooking.bookingId.toString()),
      )
      throw IllegalStateException("Mapping was not found to update for booking ${previousBooking.bookingId} and alertSequence ${event.alertSeq}")
    }
  }
}

private enum class MappingResponse {
  MAPPING_CREATED,
  MAPPING_FAILED,
}

private fun AlertResponse.isSourcedFromDPS() = audit.auditModuleName == "DPS_SYNCHRONISATION"
private fun AlertResponse.isCreatedDueToNewBooking() = audit.auditAdditionalInfo == "OMKCOPY.COPY_BOOKING_DATA"

private fun AlertResponse.shouldBeCreatedInDPS() = bookingSequence == 1L
private fun AlertResponse.shouldNotBeCreatedInDPS() = !shouldBeCreatedInDPS()
