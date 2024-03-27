package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

private const val OFFENDER_NO = "AN12345"
private const val NOMIS_COURT_CASE_ID = 3L

@SpringAPIServiceTest
@Import(CourtSentencingNomisApiService::class, CourtSentencingConfiguration::class, CourtSentencingNomisApiMockServer::class)
class CourtSentencingNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CourtSentencingNomisApiService

  @Autowired
  private lateinit var courtSentencingNomisApiMockServer: CourtSentencingNomisApiMockServer

  @Nested
  inner class GetCourtCase {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtSentencingNomisApiMockServer.stubGetCourtCase(offenderNo = OFFENDER_NO, courtCaseId = NOMIS_COURT_CASE_ID)

      apiService.getCourtCase(offenderNo = OFFENDER_NO, courtCaseId = NOMIS_COURT_CASE_ID)

      courtSentencingNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      courtSentencingNomisApiMockServer.stubGetCourtCase(offenderNo = OFFENDER_NO, courtCaseId = NOMIS_COURT_CASE_ID)

      apiService.getCourtCase(offenderNo = OFFENDER_NO, courtCaseId = NOMIS_COURT_CASE_ID)

      courtSentencingNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/$NOMIS_COURT_CASE_ID")),
      )
    }

    @Test
    fun `will return court case`() = runTest {
      courtSentencingNomisApiMockServer.stubGetCourtCase(offenderNo = OFFENDER_NO, courtCaseId = NOMIS_COURT_CASE_ID)

      val courtCase = apiService.getCourtCase(offenderNo = OFFENDER_NO, courtCaseId = NOMIS_COURT_CASE_ID)

      assertThat(courtCase.id).isEqualTo(NOMIS_COURT_CASE_ID)
      assertThat(courtCase.offenderNo).isEqualTo(OFFENDER_NO)
      assertThat(courtCase.caseStatus.code).isEqualTo("A")
    }

    @Test
    fun `will throw error when court case does not exist`() = runTest {
      courtSentencingNomisApiMockServer.stubGetCourtCase(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getCourtCase(offenderNo = OFFENDER_NO, courtCaseId = NOMIS_COURT_CASE_ID)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      courtSentencingNomisApiMockServer.stubGetCourtCase(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getCourtCase(offenderNo = OFFENDER_NO, courtCaseId = NOMIS_COURT_CASE_ID)
      }
    }
  }
}
