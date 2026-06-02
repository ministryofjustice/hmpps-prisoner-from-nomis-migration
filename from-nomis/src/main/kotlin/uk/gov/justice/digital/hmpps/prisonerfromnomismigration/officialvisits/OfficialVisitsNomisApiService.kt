package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.SuccessOrBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrBadRequestErrorMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.OfficialVisitsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PagedModelVisitIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitIdsPage
import java.time.LocalDate

@Service
class OfficialVisitsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = OfficialVisitsResourceApi(webClient)

  suspend fun getOfficialVisitIds(
    pageNumber: Long = 0,
    pageSize: Long = 1,
    prisonIds: List<String>,
    fromDate: LocalDate?,
    toDate: LocalDate?,
  ): PagedModelVisitIdResponse = api.getOfficialVisitIds(
    page = pageNumber.toInt(),
    size = pageSize.toInt(),
    prisonIds = prisonIds,
    fromDate = fromDate,
    toDate = toDate,
  ).awaitSingle()

  suspend fun getOfficialVisitIdsByLastId(
    lastVisitId: Long = 0,
    pageSize: Long,
    prisonIds: List<String>,
    fromDate: LocalDate?,
    toDate: LocalDate?,
  ): VisitIdsPage = api.getOfficialVisitIdsFromIds(
    visitId = lastVisitId,
    size = pageSize.toInt(),
    prisonIds = prisonIds,
    fromDate = fromDate,
    toDate = toDate,
  ).awaitSingle()

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
