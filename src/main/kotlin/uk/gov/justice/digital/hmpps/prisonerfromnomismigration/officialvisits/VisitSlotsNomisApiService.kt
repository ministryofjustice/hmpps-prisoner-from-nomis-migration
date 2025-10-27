package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PageVisitTimeSlotIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PageableObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotResponse

@Service
class VisitSlotsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = VisitsConfigurationResourceApi(webClient)

  suspend fun getVisitTimeSlotIds(
    pageNumber: Int = 0,
    pageSize: Int = 1,
  ): PageImpl<VisitTimeSlotIdResponse> = api.getVisitTimeSlotIds(
    page = pageNumber,
    size = pageSize,
  ).awaitSingle().asPageImpl()

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

fun PageVisitTimeSlotIdResponse.asPageImpl(): PageImpl<VisitTimeSlotIdResponse> = PageImpl(this.content!!.toMutableList(), this.pageable!!.asPageable(), this.totalElements!!)

fun PageableObject.asPageable(): Pageable = PageRequest.of(this.pageNumber!!, this.pageSize!!)
