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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitInternalLocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class VisitSlotsNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
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
