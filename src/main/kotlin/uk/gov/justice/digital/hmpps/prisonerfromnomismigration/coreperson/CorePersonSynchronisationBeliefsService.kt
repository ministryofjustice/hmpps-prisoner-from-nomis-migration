package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionUpdateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import java.util.UUID

private const val TELEMETRY_PREFIX = "coreperson-beliefs-synchronisation"

@Service
class CorePersonSynchronisationBeliefsService(
  override val telemetryClient: TelemetryClient,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val corePersonCprApiService: CorePersonCprApiService,
) : TelemetryEnabled {

  suspend fun offenderBeliefUpdated(event: OffenderBeliefEvent) {
    val (offenderIdDisplay) = event
    val telemetry = telemetryOf(
      "offenderNo" to offenderIdDisplay,
    )
    track(TELEMETRY_PREFIX, telemetry) {
      // TODO: work out if we need to grab all the religions or just the one we need
      corePersonNomisApiService.getOffenderReligions(event.offenderIdDisplay).mapIndexed { i, r ->
        PrisonReligionUpdateRequest(
          nomisReligionId = r.beliefId.toString(),
          current = i == 0,
          comments = r.comments,
          verified = r.verified,
          endDate = r.endDate,
          modifyDateTime = r.audit.modifyDatetime ?: r.audit.createDatetime,
          modifyUserId = r.audit.modifyUserId ?: r.audit.createUsername,
        )
      }.first { it.nomisReligionId == event.offenderBeliefId.toString() }.apply {
        corePersonCprApiService.syncUpdateOffenderBelief(
          prisonNumber = offenderIdDisplay,
          // TODO: grab the cpr religion id from the mapping service
          cprReligionId = UUID.randomUUID().toString(),
          religion = this,
        )
      }
    }
  }

  suspend fun offenderBeliefCreated(event: OffenderBeliefEvent) {
    val (offenderIdDisplay) = event
    val telemetry = telemetryOf(
      "offenderNo" to offenderIdDisplay,
    )
    track(TELEMETRY_PREFIX, telemetry) {
      // TODO: work out if we need to grab all the religions or just the one we need
      corePersonNomisApiService.getOffenderReligions(event.offenderIdDisplay).mapIndexed { i, r ->
        PrisonReligion(
          nomisReligionId = r.beliefId.toString(),
          current = i == 0,
          comments = r.comments,
          verified = r.verified,
          startDate = r.startDate,
          endDate = r.endDate,
          religionCode = r.belief.code,
          changeReasonKnown = r.changeReason,
          modifyDateTime = r.audit.modifyDatetime ?: r.audit.createDatetime,
          modifyUserId = r.audit.modifyUserId ?: r.audit.createUsername,
        )
      }.first { it.nomisReligionId == event.offenderBeliefId.toString() }.apply {
        corePersonCprApiService.syncCreateOffenderBelief(
          prisonNumber = offenderIdDisplay,
          religion = this,
        )
        // TODO: grab the cpr religion id and write to the mapping service
      }
    }
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
