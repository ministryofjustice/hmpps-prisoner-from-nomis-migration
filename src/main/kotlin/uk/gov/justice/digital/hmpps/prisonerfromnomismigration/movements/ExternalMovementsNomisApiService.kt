package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTemporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceReturnResponse

@Service
class ExternalMovementsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getTemporaryAbsencesOrNull(offenderNo: String): OffenderTemporaryAbsencesResponse? = webClient.get()
    .uri {
      it.path("/movements/{offenderNo}/temporary-absences")
        .build(offenderNo)
    }
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getTemporaryAbsenceApplication(offenderNo: String, applicationId: Long) = webClient.get()
    .uri {
      it.path("/movements/{offenderNo}/temporary-absences/application/{applicationId}")
        .build(offenderNo, applicationId)
    }
    .retrieve()
    .awaitBody<TemporaryAbsenceApplicationResponse>()

  suspend fun getTemporaryAbsenceScheduledMovement(offenderNo: String, eventId: Long) = webClient.get()
    .uri {
      it.path("/movements/{offenderNo}/temporary-absences/scheduled-temporary-absence/{eventId}")
        .build(offenderNo, eventId)
    }
    .retrieve()
    .awaitBody<ScheduledTemporaryAbsenceResponse>()

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
