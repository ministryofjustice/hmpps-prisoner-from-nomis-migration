package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerCaseNotesResponse

@Service
class CaseNotesNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getCaseNotesToMigrate(offenderNo: String): PrisonerCaseNotesResponse? =
    webClient.get()
      .uri("/prisoners/{offenderNo}/casenotes", offenderNo)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

//  suspend fun getAllBookingIds(
//    fromId: Long? = null,
//    toId: Long? = null,
//    activeOnly: Boolean,
//    pageNumber: Long,
//    pageSize: Long,
//  ): RestResponsePage<BookingIdResponse> =
//    webClient.get()
//      .uri {
//        it.path("/bookings/ids")
//          .queryParam("fromId", fromId)
//          .queryParam("toId", toId)
//          .queryParam("activeOnly", activeOnly)
//          .queryParam("page", pageNumber)
//          .queryParam("size", pageSize)
//          .build()
//      }
//      .retrieve()
//      .awaitBody()
}
