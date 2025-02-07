package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.typeReference
import java.time.LocalDate

@Service
class CorporateNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getCorporateOrganisation(nomisCorporateId: Long): CorporateOrganisation = webClient.get()
    .uri(
      "/corporates/{corporateId}",
      nomisCorporateId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getCorporateOrganisationIdsToMigrate(
    fromDate: LocalDate? = null,
    toDate: LocalDate? = null,
    pageNumber: Long = 0,
    pageSize: Long = 1,
  ): PageImpl<CorporateOrganisationIdResponse> = webClient.get()
    .uri {
      it.path("/corporates/ids")
        .queryParam("fromDate", fromDate)
        .queryParam("toDate", toDate)
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<CorporateOrganisationIdResponse>>())
    .awaitSingle()
}
