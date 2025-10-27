package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PageVisitTimeSlotIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PageableObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SortObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitInternalLocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

@Component
class VisitSlotsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
    fun pageVisitTimeSlotIdResponse(content: List<VisitTimeSlotIdResponse>, totalElements: Long = content.size.toLong(), pageSize: Int = 20, pageNumber: Int = 1): PageVisitTimeSlotIdResponse = PageVisitTimeSlotIdResponse(
      content = content,
      totalElements = totalElements,
      totalPages = (totalElements / pageSize + 1).toInt(),
      number = pageNumber,
      first = true,
      last = false,
      sort = SortObject(),
      numberOfElements = content.size,
      pageable = PageableObject(
        offset = 0,
        pageSize = pageSize,
        pageNumber = pageNumber,
        paged = true,
        unpaged = false,
      ),
      empty = false,
    )

    fun visitTimeSlotResponse() = VisitTimeSlotResponse(
      prisonId = "LEI",
      dayOfWeek = VisitTimeSlotResponse.DayOfWeek.MONDAY,
      timeSlotSequence = 1,
      // TODO - find out why these are not LocalTime instances
      startTime = "10:00",
      endTime = "11:00",
      effectiveDate = LocalDate.parse("2020-01-01"),
      visitSlots = listOf(visitSlotResponse()),
    )

    fun visitSlotResponse() = VisitSlotResponse(
      // TODO missing id
      internalLocation = VisitInternalLocationResponse(id = 122, "LEI-VISIT-1"),
    )
  }
  fun stubGetVisitTimeSlotIds(
    pageNumber: Int = 0,
    pageSize: Int = 20,
    totalElements: Long = content.size.toLong(),
    content: List<VisitTimeSlotIdResponse>,
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/visits/configuration/time-slots/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(pageVisitTimeSlotIdResponse(content, pageSize = pageSize, pageNumber = pageNumber, totalElements = totalElements))),
      ),
    )
  }
  fun stubGetVisitTimeSlot(
    prisonId: String = "LEI",
    dayOfWeek: DayOfWeekGetVisitTimeSlot,
    timeSlotSequence: Int,
    response: VisitTimeSlotResponse = visitTimeSlotResponse(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/visits/configuration/time-slots/prison-id/$prisonId/day-of-week/$dayOfWeek/time-slot-sequence/$timeSlotSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
