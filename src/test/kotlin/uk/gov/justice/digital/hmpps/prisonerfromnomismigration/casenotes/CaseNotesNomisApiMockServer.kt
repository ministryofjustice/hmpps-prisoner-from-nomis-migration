package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
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
      creationDateTime = "2021-02-03T04:05:06",
      createdDatetime = "2024-11-03T04:05:06",
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

  fun stubGetCaseNotesForPrisoner(
    offenderNo: String,
    currentCaseNoteStart: Long = 1,
    currentCaseNoteCount: Long = 1,
    auditModuleName: String = "OIDNOMIS",
    type: String = "GEN",
    bookingId: Long = 1L,
    caseNote: CaseNoteResponse = CaseNoteResponse(
      bookingId = bookingId,
      caseNoteId = 0,
      caseNoteText = "will be overwritten below",
      caseNoteType = CodeDescription(type, "desc"),
      caseNoteSubType = CodeDescription("OUTCOME", "desc"),
      authorUsername = "me",
      authorStaffId = 123456L,
      authorFirstName = "First",
      authorLastName = "Last",
      amendments = emptyList(),
      creationDateTime = "2021-02-03T04:05:06",
      createdDatetime = "2024-11-03T04:05:06",
      createdUsername = "John",
      noteSourceCode = CaseNoteResponse.NoteSourceCode.INST,
      occurrenceDateTime = "2021-02-03T04:05:06",
      prisonId = "SWI",
      auditModuleName = auditModuleName,
      sourceSystem = SourceSystem.NOMIS,
    ),
  ) {
    val response = PrisonerCaseNotesResponse(
      caseNotes = (0..currentCaseNoteCount - 1).map {
        caseNote.copy(
          caseNoteId = it + currentCaseNoteStart,
          caseNoteText = "text $it",
        )
      },
    )
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/casenotes")).willReturn(
        okJson(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetCaseNotesForPrisoner(
    offenderNo: String,
    caseNotes: List<CaseNoteResponse>,
  ) {
    val response = PrisonerCaseNotesResponse(caseNotes = caseNotes)
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/casenotes")).willReturn(
        okJson(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubPutCaseNote(
    caseNoteId: Long,
    status: HttpStatus = HttpStatus.OK,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/casenotes/$caseNoteId")).willReturn(status(status.value())),
    )
  }

  fun stubDeleteCaseNote(
    caseNoteId: Long,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/casenotes/$caseNoteId")).willReturn(status(HttpStatus.NO_CONTENT.value())),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
