package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PagedModelVisitTimeSlotIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotResponse

@Service
class VisitSlotsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = VisitsConfigurationResourceApi(webClient)

  suspend fun getVisitTimeSlotIds(
    pageNumber: Long = 0,
    pageSize: Long = 1,
  ): PagedModelVisitTimeSlotIdResponse = api.getVisitTimeSlotIds(
    page = pageNumber.toInt(),
    size = pageSize.toInt(),
  ).awaitSingle()

  suspend fun getVisitTimeSlot(
    prisonId: String,
    dayOfWeek: DayOfWeekGetVisitTimeSlot,
    timeSlotSequence: Int,
  ): VisitTimeSlotResponse = api.getVisitTimeSlot(
    prisonId = prisonId,
    dayOfWeek = dayOfWeek,
    timeSlotSequence = timeSlotSequence,
  ).awaitSingle()
}
