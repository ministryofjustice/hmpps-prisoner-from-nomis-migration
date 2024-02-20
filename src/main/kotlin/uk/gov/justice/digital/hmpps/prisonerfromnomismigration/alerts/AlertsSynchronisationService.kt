package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.DPS_CREATED

@Service
class AlertsSynchronisationService(
  private val mappingApiService: AlertsMappingApiService,
  private val nomisApiService: AlertsNomisApiService,
  private val dpsApiService: AlertsDpsApiService,
  private val telemetryClient: TelemetryClient,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun nomisAlertInserted(event: AlertInsertedEvent) {
    val telemetry = mapOf("bookingId" to event.bookingId, "alertSequence" to event.alertSeq)
    val nomisAlert = nomisApiService.getAlert(bookingId = event.bookingId, alertSequence = event.alertSeq)
    if (nomisAlert.audit.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("alert-synchronisation-skipped", telemetry)
    } else {
      val mapping = mappingApiService.getOrNullByNomisId(event.bookingId, event.alertSeq)
      if (mapping != null) {
        // TODO - probably update alert??
      } else {
        dpsApiService.createAlert(nomisAlert.toDPsAlert()).run {
          // TODO - deal wit error scenario
          mappingApiService.createMapping(
            AlertMappingDto(
              dpsAlertId = this.alertUuid.toString(),
              nomisBookingId = nomisAlert.bookingId,
              nomisAlertSequence = nomisAlert.alertSequence,
              mappingType = DPS_CREATED,
            ),
          )
        }
      }
    }
  }

  suspend fun nomisAlertUpdated(event: AlertUpdatedEvent) {
    log.debug("TODO: handle {}", event)
  }
}
