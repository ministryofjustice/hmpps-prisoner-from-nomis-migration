package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.OffenderTapsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.TapApplicationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.TapScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTapsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TapApplication
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TapScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceReturnResponse

@Service
class TapsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  private val applicationApi = TapApplicationResourceApi(webClient)
  private val offenderApi = OffenderTapsResourceApi(webClient)
  private val scheduleApi = TapScheduleResourceApi(webClient)

  suspend fun getAllOffenderTapsOrNull(offenderNo: String): OffenderTapsResponse? = offenderApi.prepare(offenderApi.getAllOffenderTapsRequestConfig(offenderNo))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getTapApplication(offenderNo: String, applicationId: Long): TapApplication = applicationApi.getTapApplication(offenderNo, applicationId)
    .awaitSingle()

  suspend fun getTapScheduleOut(offenderNo: String, eventId: Long): TapScheduleOut = scheduleApi.getTapScheduleOut(offenderNo, eventId)
    .awaitSingle()

  suspend fun getTemporaryAbsenceScheduledReturnMovement(offenderNo: String, eventId: Long) = webClient.get()
    .uri {
      it.path("/movements/{offenderNo}/temporary-absences/scheduled-temporary-absence-return/{eventId}")
        .build(offenderNo, eventId)
    }
    .retrieve()
    .awaitBody<ScheduledTemporaryAbsenceReturnResponse>()

  suspend fun getTemporaryAbsenceMovement(offenderNo: String, bookingId: Long, movementSeq: Int) = webClient.get()
    .uri {
      it.path("/movements/{offenderNo}/temporary-absences/temporary-absence/{bookingId}/{movementSeq}")
        .build(offenderNo, bookingId, movementSeq)
    }
    .retrieve()
    .awaitBody<TemporaryAbsenceResponse>()

  suspend fun getTemporaryAbsenceReturnMovement(offenderNo: String, bookingId: Long, movementSeq: Int) = webClient.get()
    .uri {
      it.path("/movements/{offenderNo}/temporary-absences/temporary-absence-return/{bookingId}/{movementSeq}")
        .build(offenderNo, bookingId, movementSeq)
    }
    .retrieve()
    .awaitBody<TemporaryAbsenceReturnResponse>()
}
