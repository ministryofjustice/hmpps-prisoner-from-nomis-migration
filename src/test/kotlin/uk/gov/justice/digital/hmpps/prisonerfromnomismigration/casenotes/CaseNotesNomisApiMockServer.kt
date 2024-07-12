package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
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
  fun stubGetCaseNote(
    caseNoteId: Long = 1001,
    bookingId: Long = 123456,
    alertSequence: Long = 1,
    auditModuleName: String = "OIDNOMIS",
    caseNote: CaseNoteResponse = CaseNoteResponse(
      caseNoteId = caseNoteId,
      bookingId = bookingId,
      caseNoteType = CodeDescription("X", "Security"),
      caseNoteSubType = CodeDescription("X", "Security"),
      authorUsername = "me",
      amended = false,
      auditModuleName = auditModuleName,
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/casenotes/$caseNoteId")).willReturn(
        okJson(objectMapper.writeValueAsString(caseNote)),
      ),
    )
  }

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
        okJson(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetCaseNotesToMigrate(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/.+/casenotes")).willReturn(
        okJson(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
