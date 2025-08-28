package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTemporaryAbsencesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceApplicationOutsideMovementResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TemporaryAbsenceReturnResponse

@Service
class ExternalMovementsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getTemporaryAbsences(offenderNo: String): OffenderTemporaryAbsencesResponse = webClient.get()
    .uri {
      it.path("/prisoners/{offenderNo}/temporary-absences")
        .build(offenderNo)
    }
    .retrieve()
    .awaitBody()

  suspend fun getTemporaryAbsenceApplication(offenderNo: String, applicationId: Long) = webClient.get()
    .uri {
      it.path("/prisoners/{offenderNo}/temporary-absences/application/{applicationId}")
        .build(offenderNo, applicationId)
    }
    .retrieve()
    .awaitBody<TemporaryAbsenceApplicationResponse>()

  suspend fun getTemporaryAbsenceApplicationOutsideMovement(offenderNo: String, appMultiId: Long) = webClient.get()
    .uri {
      it.path("/movements/{offenderNo}/temporary-absences/outside-movement/{appMultiId}")
        .build(offenderNo, appMultiId)
    }
    .retrieve()
    .awaitBody<TemporaryAbsenceApplicationOutsideMovementResponse>()

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
