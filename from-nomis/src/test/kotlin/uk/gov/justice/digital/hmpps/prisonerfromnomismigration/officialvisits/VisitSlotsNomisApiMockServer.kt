package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PagedModelVisitTimeSlotIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitInternalLocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class VisitSlotsNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    fun pageVisitTimeSlotIdResponse(content: List<VisitTimeSlotIdResponse>, totalElements: Long = content.size.toLong(), pageSize: Int = 20, pageNumber: Int = 1): PagedModelVisitTimeSlotIdResponse = PagedModelVisitTimeSlotIdResponse(
      content = content,
      page = PageMetadata(
        propertySize = pageSize.toLong(),
        number = pageNumber.toLong(),
        totalElements = totalElements,
        totalPages = Math.ceilDiv(totalElements, pageSize),
      ),
    )

    fun visitTimeSlotResponse() = VisitTimeSlotResponse(
      prisonId = "LEI",
      dayOfWeek = VisitTimeSlotResponse.DayOfWeek.MON,
      timeSlotSequence = 1,
      startTime = "10:00",
      endTime = "11:00",
      effectiveDate = LocalDate.parse("2020-01-01"),
      visitSlots = listOf(visitSlotResponse()),
      audit = NomisAudit(
        createDatetime = LocalDateTime.parse("2020-01-01T10:00"),
        createUsername = "B.BOB",
      ),
    )

    fun visitSlotResponse() = VisitSlotResponse(
      id = 123,
      internalLocation = VisitInternalLocationResponse(id = 122, "LEI-VISIT-1"),
      audit = NomisAudit(
        createDatetime = LocalDateTime.parse("2020-01-01T10:00"),
        createUsername = "B.BOB",
      ),
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
          .withBody(jsonMapper.writeValueAsString(pageVisitTimeSlotIdResponse(content, pageSize = pageSize, pageNumber = pageNumber, totalElements = totalElements))),
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
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
