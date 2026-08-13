package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@Service
class CorePersonSynchronisationService(
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  suspend fun synchronisePrisonerMerge(prisonerMergeEvent: PrisonerMergeDomainEvent) {
    val bookingId = prisonerMergeEvent.additionalInformation.bookingId
    val offenderNo = prisonerMergeEvent.additionalInformation.nomsNumber
    val removedOffenderNo = prisonerMergeEvent.additionalInformation.removedNomsNumber
    val telemetry = mapOf(
      "offenderNo" to offenderNo,
      "bookingId" to bookingId,
      "removedOffenderNo" to removedOffenderNo,
    )
    telemetryClient.trackEvent("coreperson-prisoner-merge-synchronisation-notimplemented", telemetry)
  }
}

class BookingException(message: String) : IllegalArgumentException(message)
