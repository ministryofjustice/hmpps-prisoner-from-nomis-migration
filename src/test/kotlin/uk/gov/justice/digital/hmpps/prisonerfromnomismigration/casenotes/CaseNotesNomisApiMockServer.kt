package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerCaseNotesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class CaseNotesNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetCaseNotesToMigrate(
    offenderNo: String,
    currentCaseNoteCount: Long = 1,
    caseNote: CaseNoteResponse = CaseNoteResponse(
      bookingId = 0,
      caseNoteId = 1,
      caseNoteText = "text",
      caseNoteType = CodeDescription("type", "desc"),
      caseNoteSubType = CodeDescription("subtype", "desc"),
      amended = false,
      authorUsername = "me",
    ),
  ) {
    val response = PrisonerCaseNotesResponse(
      caseNotes = (1..currentCaseNoteCount).map { caseNote.copy(bookingId = it) },
    )
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/casenotes")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetCaseNotesToMigrate(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/.+/casenotes")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

//  fun stubGetAllBookings(totalElements: Long = 20, pageSize: Long = 20) { // , bookingId: Long = 1) {
//    val content: List<BookingIdResponse> = (1..min(pageSize, totalElements)).map { BookingIdResponse(it.toLong()) }
//    nomisApi.stubFor(
//      get(urlPathEqualTo("/bookings/ids")).willReturn(
//        aResponse()
//          .withHeader("Content-Type", "application/json")
//          .withStatus(HttpStatus.OK.value())
//          .withBody(
//            pageContent(
//              objectMapper = objectMapper,
//              content = content,
//              pageSize = pageSize,
//              pageNumber = 0,
//              totalElements = totalElements,
//              size = pageSize.toInt(),
//            ),
//          ),
//      ),
//    )
//  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
