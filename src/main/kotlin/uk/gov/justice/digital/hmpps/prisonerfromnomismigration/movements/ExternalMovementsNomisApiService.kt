package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTemporaryAbsencesResponse

@Service
class ExternalMovementsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getTemporaryAbsences(offenderNo: String): OffenderTemporaryAbsencesResponse = webClient.get()
    .uri {
      it.path("/prisoners/{offenderNo}/temporary-absences")
        .build(offenderNo)
    }
    .retrieve()
    .awaitBody()
}
