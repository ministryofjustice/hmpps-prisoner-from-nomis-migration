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

//    @Test
//    fun extra() = runTest {
//      caseNotesNomisApiMockServer.stubGetCaseNotesToMigrate(
//        bookingId = 1,
//        currentCaseNoteCount = 1,
//          caseNote = CaseNoteResponse(
//            bookingId = 1,
//            caseNoteType = CodeDescription("X", "Security"),
//            caseNoteSubType = CodeDescription("X", "Security"),
//            authorUsername = "me",
//            amended = false,
//            caseNoteId = 1001,
//          ),
//      )
//      caseNotesNomisApiMockServer.stubGetCaseNotesToMigrate(bookingId = 2, currentCaseNoteCount = 1)
//
//      val response1 = apiService.getCaseNotesToMigrate(1)
//      val response2 = apiService.getCaseNotesToMigrate(2)
//
//      assertThat(response1.caseNotes).hasSize(1)
//    }
  }

//  @Nested
//  inner class GetBookingIds {
//    @Test
//    internal fun `will pass oath2 token to service`() = runTest {
//      caseNotesNomisApiMockServer.stubGetAllBookings()
//
//      apiService.getAllBookingIds(
//        pageNumber = 0,
//        pageSize = 20,
//        activeOnly = true,
//      )
//
//      caseNotesNomisApiMockServer.verify(
//        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
//      )
//    }
//
//    @Test
//    internal fun `will pass page params to service`() = runTest {
//      caseNotesNomisApiMockServer.stubGetAllBookings()
//
//      apiService.getAllBookingIds(
//        pageNumber = 5,
//        pageSize = 100,
//        activeOnly = false,
//      )
//
//      caseNotesNomisApiMockServer.verify(
//        getRequestedFor(urlPathEqualTo("/bookings/ids"))
//          .withQueryParam("page", equalTo("5"))
//          .withQueryParam("size", equalTo("100")),
//      )
//    }
//
//    @Test
//    fun `will return a page of caseNotes`() = runTest {
//      caseNotesNomisApiMockServer.stubGetAllBookings(totalElements = 10)
//
//      val prisonerIds = apiService.getAllBookingIds(
//        pageNumber = 5,
//        pageSize = 100,
//        activeOnly = true,
//      )
//
//      assertThat(prisonerIds.content).hasSize(10)
//      assertThat(prisonerIds.content[0].bookingId).isEqualTo(1)
//      assertThat(prisonerIds.content[1].bookingId).isEqualTo(2)
//    }
//  }
}
