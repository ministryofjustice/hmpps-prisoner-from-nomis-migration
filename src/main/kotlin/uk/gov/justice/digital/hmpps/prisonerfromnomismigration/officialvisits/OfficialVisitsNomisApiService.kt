package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.SuccessOrBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrBadRequestErrorMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.OfficialVisitsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OfficialVisitResponse

@Service
class OfficialVisitsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = OfficialVisitsResourceApi(webClient)

  suspend fun getOfficialVisit(
    visitId: Long,
  ): OfficialVisitResponse = api.getOfficialVisit(
    visitId = visitId,
  ).awaitSingle()

  suspend fun getOfficialVisitOrBadRequestErrorMessage(
    visitId: Long,
  ): SuccessOrBadRequest<OfficialVisitResponse> = with(api) {
    awaitSuccessOrBadRequestErrorMessage(
      getOfficialVisitRequestConfig(
        visitId = visitId,
      ),
    )
  }

  suspend fun getOfficialVisitsForPrisoner(
    offenderNo: String,
  ): List<OfficialVisitResponse> = api.getOfficialVisitsForPrisoner(
    offenderNo = offenderNo,
    fromDate = null,
    toDate = null,
  ).awaitSingle()
}
