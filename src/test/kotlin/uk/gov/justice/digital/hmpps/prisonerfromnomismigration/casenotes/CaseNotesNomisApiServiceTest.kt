package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription

private const val OFFENDER_NUMBER = "G4803UT"

@SpringAPIServiceTest
@Import(CaseNotesNomisApiService::class, CaseNotesConfiguration::class, CaseNotesNomisApiMockServer::class)
class CaseNotesNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CaseNotesNomisApiService

  @Autowired
  private lateinit var caseNotesNomisApiMockServer: CaseNotesNomisApiMockServer

  @Nested
  inner class GetDpsCaseNotesToMigrate {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      caseNotesNomisApiMockServer.stubGetCaseNotesToMigrate(offenderNo = OFFENDER_NUMBER)

      apiService.getCaseNotesToMigrate(OFFENDER_NUMBER)

      caseNotesNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      caseNotesNomisApiMockServer.stubGetCaseNotesToMigrate(offenderNo = OFFENDER_NUMBER)

      apiService.getCaseNotesToMigrate(OFFENDER_NUMBER)

      caseNotesNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/$OFFENDER_NUMBER/casenotes")),
      )
    }

    @Test
    fun `will return caseNotes`() = runTest {
      caseNotesNomisApiMockServer.stubGetCaseNotesToMigrate(
        offenderNo = OFFENDER_NUMBER,
        currentCaseNoteCount = 2,
        caseNote = CaseNoteResponse(
          bookingId = 1,
          caseNoteType = CodeDescription("X", "Security"),
          caseNoteSubType = CodeDescription("X", "Security"),
          authorUsername = "me",
          amended = false,
          caseNoteId = 1001,
        ),
      )

      val response = apiService.getCaseNotesToMigrate(OFFENDER_NUMBER)!!

      assertThat(response.caseNotes).hasSize(2)
      assertThat(response.caseNotes[0].bookingId).isEqualTo(1L)
      assertThat(response.caseNotes[0].caseNoteId).isEqualTo(1001L)
    }
  }

  @Nested
  inner class GetCaseNote {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      caseNotesNomisApiMockServer.stubGetCaseNote()

      apiService.getCaseNote(1001)

      caseNotesNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      caseNotesNomisApiMockServer.stubGetCaseNote()

      apiService.getCaseNote(1001)

      caseNotesNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/casenotes/1001")),
      )
    }

    @Test
    fun `will return caseNotes`() = runTest {
      caseNotesNomisApiMockServer.stubGetCaseNote(
        caseNote = CaseNoteResponse(
          bookingId = 1,
          caseNoteType = CodeDescription("X", "Security"),
          caseNoteSubType = CodeDescription("X", "Security"),
          authorUsername = "me",
          amended = false,
          caseNoteId = 1001,
        ),
      )

      val response = apiService.getCaseNote(1001)

      assertThat(response.bookingId).isEqualTo(1L)
      assertThat(response.caseNoteId).isEqualTo(1001L)
    }
  }
}
