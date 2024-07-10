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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerReceiveDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.ReceivePrisonerAdditionalInformationEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.MergedPrisonerAlertMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerAlertMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerAlertsResponse
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
  @Value("\${alerts.has-migrated-data:true}")
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
        telemetryClient.trackEvent("alert-created-new-booking-ignored", telemetry)
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

    val alerts = nomisApiService.getAlertsToResynchronise(offenderNo) ?: PrisonerAlertsResponse(emptyList())
    val alertsToResynchronise = alerts.latestBookingAlerts.map { it.toDPSResyncAlert() }
    val telemetry = mapOf(
      "offenderNo" to offenderNo,
      "bookingId" to bookingId,
      "removedOffenderNo" to removedOffenderNo,
      "alertsCount" to alertsToResynchronise.size,
      "alerts" to alertsToResynchronise.map { it.alertSeq }.joinToString(),
    )
    dpsApiService.resynchroniseAlerts(
      offenderNo = removedOffenderNo,
      alerts = emptyList(),
    )

    dpsApiService.resynchroniseAlerts(
      offenderNo = offenderNo,
      alerts = alertsToResynchronise,
    ).also {
      val prisonerMappings = PrisonerAlertMappingsDto(
        mappingType = PrisonerAlertMappingsDto.MappingType.NOMIS_CREATED,
        mappings = it.map { dpsAlert ->
          AlertMappingIdDto(
            nomisBookingId = dpsAlert.offenderBookId,
            nomisAlertSequence = dpsAlert.alertSeq.toLong(),
            dpsAlertId = dpsAlert.alertUuid.toString(),
          )
        },
      )

      tryToReplaceMergedMappings(offenderNo, MergedPrisonerAlertMappingsDto(removedOffenderNo, prisonerMappings), telemetry)
      telemetryClient.trackEvent(
        "from-nomis-synch-alerts-merge",
        telemetry,
      )
    }
  }
  suspend fun synchronisePrisonerBookingMoved(prisonerMergeEvent: PrisonerBookingMovedDomainEvent) {
    val bookingId = prisonerMergeEvent.additionalInformation.bookingId
    val movedToNomsNumber = prisonerMergeEvent.additionalInformation.movedToNomsNumber
    val movedFromNomsNumber = prisonerMergeEvent.additionalInformation.movedFromNomsNumber

    val alerts = nomisApiService.getAlertsToResynchronise(movedFromNomsNumber) ?: PrisonerAlertsResponse(emptyList())
    val alertsToResynchronise = alerts.latestBookingAlerts.map { it.toDPSResyncAlert() }
    val telemetry = mapOf(
      "bookingId" to bookingId,
      "movedToNomsNumber" to movedToNomsNumber,
      "movedFromNomsNumber" to movedFromNomsNumber,
      "alertsCount" to alertsToResynchronise.size,
      "alerts" to alertsToResynchronise.map { it.alertSeq }.joinToString(),
    )

    // we only need to update the source prisoner since the prisoner
    // receiving the booking would have already been updated via the
    // prisoner receive event
    dpsApiService.resynchroniseAlerts(
      offenderNo = movedFromNomsNumber,
      alerts = alertsToResynchronise,
    ).also {
      val prisonerMappings = PrisonerAlertMappingsDto(
        mappingType = PrisonerAlertMappingsDto.MappingType.NOMIS_CREATED,
        mappings = it.map { dpsAlert ->
          AlertMappingIdDto(
            nomisBookingId = dpsAlert.offenderBookId,
            nomisAlertSequence = dpsAlert.alertSeq.toLong(),
            dpsAlertId = dpsAlert.alertUuid.toString(),
          )
        },
      )

      tryToReplaceMappings(offenderNo = movedFromNomsNumber, prisonerMappings = prisonerMappings, telemetry)
      telemetryClient.trackEvent(
        "from-nomis-synch-alerts-booking-moved",
        telemetry,
      )
    }
  }

  suspend fun resynchronisePrisonerAlerts(offenderNo: String) = resynchronisePrisonerAlertsForAdmission(
    PrisonerReceiveDomainEvent(
      ReceivePrisonerAdditionalInformationEvent(nomsNumber = offenderNo, reason = "READMISSION_SWITCH_BOOKING"),
    ),
  )

  suspend fun resynchronisePrisonerAlertsForAdmission(prisonerReceiveEvent: PrisonerReceiveDomainEvent) {
    val receiveReason = prisonerReceiveEvent.additionalInformation.reason
    val offenderNo = prisonerReceiveEvent.additionalInformation.nomsNumber

    if (receiveReason !in listOf("READMISSION_SWITCH_BOOKING", "NEW_ADMISSION")) {
      telemetryClient.trackEvent(
        "from-nomis-synch-alerts-resynchronise-ignored",
        mapOf(
          "offenderNo" to offenderNo,
          "receiveReason" to receiveReason,
        ),
      )
      return
    }

    // when NOMIS does not find a booking this will be null so just resynchronise as if there is no alerts
    val nomisAlerts = nomisApiService.getAlertsToResynchronise(offenderNo) ?: PrisonerAlertsResponse(emptyList())
    val alertsToResynchronise = nomisAlerts.latestBookingAlerts.map { it.toDPSResyncAlert() }
    val telemetry = mapOf(
      "offenderNo" to offenderNo,
      "alertsCount" to alertsToResynchronise.size,
      "alerts" to alertsToResynchronise.map { it.alertSeq }.joinToString(),
    )

    dpsApiService.resynchroniseAlerts(
      offenderNo = offenderNo,
      alerts = alertsToResynchronise,
    ).also {
      val prisonerMappings = PrisonerAlertMappingsDto(
        mappingType = PrisonerAlertMappingsDto.MappingType.NOMIS_CREATED,
        mappings = it.map { dpsAlert ->
          AlertMappingIdDto(
            nomisBookingId = dpsAlert.offenderBookId,
            nomisAlertSequence = dpsAlert.alertSeq.toLong(),
            dpsAlertId = dpsAlert.alertUuid.toString(),
          )
        },
      )
      tryToReplaceMappings(
        offenderNo = offenderNo,
        prisonerMappings = prisonerMappings,
        telemetry,
      )
      telemetryClient.trackEvent(
        "from-nomis-synch-alerts-resynchronise",
        telemetry,
      )
    }
  }

  private suspend fun tryToReplaceMappings(
    offenderNo: String,
    prisonerMappings: PrisonerAlertMappingsDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      replaceMappingsBatch(offenderNo = offenderNo, prisonerMappings)
    } catch (e: Exception) {
      log.error("Failed to create mappings for alert id $prisonerMappings", e)
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_RESYNCHRONISATION_MAPPING_BATCH.name,
        synchronisationType = SynchronisationType.ALERTS,
        message = ReplaceMappings(offenderNo = offenderNo, prisonerMappings),
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun tryToReplaceMergedMappings(
    offenderNo: String,
    mergedPrisonerMapping: MergedPrisonerAlertMappingsDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      replaceMergedMappingsBatch(offenderNo = offenderNo, mergedPrisonerMapping)
    } catch (e: Exception) {
      log.error("Failed to create mappings for merge $mergedPrisonerMapping", e)
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_RESYNCHRONISATION_MERGED_MAPPING_BATCH.name,
        synchronisationType = SynchronisationType.ALERTS,
        message = ReplaceMergedMappings(offenderNo = offenderNo, mergedPrisonerMapping),
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
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
  suspend fun retryReplaceMappingsBatch(retryMessage: InternalMessage<ReplaceMappings>) {
    replaceMappingsBatch(offenderNo = retryMessage.body.offenderNo, prisonerMappings = retryMessage.body.prisonerMappings)
      .also {
        telemetryClient.trackEvent(
          "alert-mapping-replace-success",
          retryMessage.telemetryAttributes,
        )
      }
  }
  suspend fun retryReplaceMergedMappingsBatch(retryMessage: InternalMessage<ReplaceMergedMappings>) {
    replaceMergedMappingsBatch(offenderNo = retryMessage.body.offenderNo, mergedPrisonerMapping = retryMessage.body.prisonerMappings)
      .also {
        telemetryClient.trackEvent(
          "alert-mapping-replace-success",
          retryMessage.telemetryAttributes,
        )
      }
  }

  private suspend fun replaceMappingsBatch(
    offenderNo: String,
    prisonerMappings: PrisonerAlertMappingsDto,
  ) =
    mappingApiService.replaceMappings(
      offenderNo,
      prisonerMappings,
    )
  private suspend fun replaceMergedMappingsBatch(
    offenderNo: String,
    mergedPrisonerMapping: MergedPrisonerAlertMappingsDto,
  ) =
    mappingApiService.replaceMappingsForMerge(
      offenderNo,
      mergedPrisonerMapping,
    )
}

private enum class MappingResponse {
  MAPPING_CREATED,
  MAPPING_FAILED,
}

private fun AlertResponse.isSourcedFromDPS() = audit.auditModuleName == "DPS_SYNCHRONISATION"
private fun AlertResponse.isCreatedDueToNewBooking() = audit.auditAdditionalInfo == "OMKCOPY.COPY_BOOKING_DATA"

private fun AlertResponse.shouldBeCreatedInDPS() = bookingSequence == 1L
private fun AlertResponse.shouldNotBeCreatedInDPS() = !shouldBeCreatedInDPS()
data class ReplaceMappings(val offenderNo: String, val prisonerMappings: PrisonerAlertMappingsDto)
data class ReplaceMergedMappings(val offenderNo: String, val prisonerMappings: MergedPrisonerAlertMappingsDto)
