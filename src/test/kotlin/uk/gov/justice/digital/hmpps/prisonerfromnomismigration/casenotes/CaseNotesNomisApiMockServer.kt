package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse.SourceSystem
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerCaseNotesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class CaseNotesNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetCaseNote(
    caseNoteId: Long = 1001,
    bookingId: Long = 123456,
    auditModuleName: String = "OIDNOMIS",
    caseNote: CaseNoteResponse = CaseNoteResponse(
      caseNoteId = caseNoteId,
      bookingId = bookingId,
      caseNoteType = CodeDescription("X", "Security"),
      caseNoteSubType = CodeDescription("X", "Security"),
      authorUsername = "me",
      authorStaffId = 123456L,
      authorFirstName = "First",
      authorLastName = "Last",
      amendments = emptyList(),
      createdDatetime = "2021-02-03T04:05:06",
      createdUsername = "John",
      noteSourceCode = CaseNoteResponse.NoteSourceCode.INST,
      occurrenceDateTime = "2021-02-03T04:05:06",
      prisonId = "SWI",
      caseNoteText = "the actual casenote",
      auditModuleName = auditModuleName,
      sourceSystem = SourceSystem.NOMIS,
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
    auditModuleName: String = "OIDNOMIS",
    type: String = "GEN",
    caseNote: CaseNoteResponse = CaseNoteResponse(
      bookingId = 0,
      caseNoteId = 0,
      caseNoteText = "text",
      caseNoteType = CodeDescription(type, "desc"),
      caseNoteSubType = CodeDescription("OUTCOME", "desc"),
      authorUsername = "me",
      authorStaffId = 123456L,
      authorFirstName = "First",
      authorLastName = "Last",
      amendments = emptyList(),
      createdDatetime = "2021-02-03T04:05:06",
      createdUsername = "John",
      noteSourceCode = CaseNoteResponse.NoteSourceCode.INST,
      occurrenceDateTime = "2021-02-03T04:05:06",
      prisonId = "SWI",
      auditModuleName = auditModuleName,
      sourceSystem = SourceSystem.NOMIS,
    ),
  ) {
    val response = PrisonerCaseNotesResponse(
      caseNotes = (1..currentCaseNoteCount).map { caseNote.copy(bookingId = it, caseNoteId = it) },
    )
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/casenotes")).willReturn(
        okJson(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
