package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

private const val TELEMETRY_PREFIX = "coreperson-beliefs-synchronisation"

@Service
class CorePersonSynchronisationBeliefsService(
  override val telemetryClient: TelemetryClient,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val corePersonCprApiService: CorePersonCprApiService,
) : TelemetryEnabled {

  suspend fun offenderBeliefChanged(event: OffenderBeliefEvent) {
    val (offenderIdDisplay) = event
    val telemetry = telemetryOf(
      "offenderNo" to offenderIdDisplay,
    )
    track(TELEMETRY_PREFIX, telemetry) {
      corePersonNomisApiService.getOffenderReligions(event.offenderIdDisplay).mapIndexed { i, r ->
        PrisonReligion(
          current = i == 0,
          religionCode = r.belief.code,
          changeReasonKnown = r.changeReason,
          comments = r.comments,
          verified = r.verified,
          startDate = r.startDate,
          endDate = r.endDate,
          modifyDateTime = r.audit.modifyDatetime ?: r.audit.createDatetime,
          modifyUserId = r.audit.modifyUserId ?: r.audit.createUsername,
        )
      }.apply {
        corePersonCprApiService.syncCreateOffenderBelief(
          prisonNumber = offenderIdDisplay,
          religion = PrisonReligionRequest(religions = this),
        )
        telemetry["count"] = this.size
      }
    }
  }

  suspend fun offenderBeliefUpdated(event: OffenderBeliefEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "rootOffenderId" to event.rootOffenderId,
      "nomisOffenderBeliefId" to event.offenderBeliefId,
    )
    telemetryClient.trackEvent("coreperson-belief-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun offenderBeliefDeleted(event: OffenderBeliefEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "rootOffenderId" to event.rootOffenderId,
      "nomisOffenderBeliefId" to event.offenderBeliefId,
    )
    telemetryClient.trackEvent("coreperson-belief-synchronisation-deleted-notimplemented", telemetry)
  }
}
