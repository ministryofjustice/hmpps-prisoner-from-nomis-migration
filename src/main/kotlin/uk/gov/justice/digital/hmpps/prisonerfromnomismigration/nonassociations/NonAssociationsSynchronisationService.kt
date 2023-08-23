package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class NonAssociationsSynchronisationService(
  private val nomisApiService: NomisApiService,
  private val nonAssociationsService: NonAssociationsService,
  private val telemetryClient: TelemetryClient,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseNonAssociationCreateOrUpdate(event: NonAssociationsOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "non-association-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    if (event.isDuplicate()) {
      telemetryClient.trackEvent(
        "non-association-synchronisation-duplicate-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    nomisApiService.getNonAssociation(event.offenderIdDisplay, event.nsOffenderIdDisplay)
      .also {
        nonAssociationsService.createNonAssociation(it.toCreateSyncRequest())
        telemetryClient.trackEvent(
          "non-association-created-synchronisation-success",
          event.toTelemetryProperties(),
        )
      }
  }
}

// Two non-association events occur for each non-association relationship - use the offenderIds to uniquely identify each pair
private fun NonAssociationsOffenderEvent.isDuplicate(): Boolean = offenderIdDisplay > nsOffenderIdDisplay

private fun NonAssociationsOffenderEvent.toTelemetryProperties(
  nonAssociationId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "offenderNo" to this.offenderIdDisplay,
  "nsOffenderNo" to this.nsOffenderIdDisplay,
) + (nonAssociationId?.let { mapOf("nonAssociationId" to it) } ?: emptyMap()) + (
  mappingFailed?.takeIf { it }
    ?.let { mapOf("mapping" to "initial-failure") } ?: emptyMap()
  )
