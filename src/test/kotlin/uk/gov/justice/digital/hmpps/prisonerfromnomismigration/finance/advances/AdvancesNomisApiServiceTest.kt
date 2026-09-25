package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(AdvancesNomisApiService::class, AdvancesNomisApiMockServer::class)
class AdvancesNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: AdvancesNomisApiService

  @Autowired
  private lateinit var mockServer: AdvancesNomisApiMockServer

  @Nested
  inner class GetAdvanceById {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetAdvanceById()

      apiService.getPrisonerAdvanceById(12345)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetAdvanceById()

      apiService.getPrisonerAdvanceById(12345)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/finance/prisoners/advances/12345")),
      )
    }

    @Test
    fun `will return advance details`() = runTest {
      mockServer.stubGetAdvanceById()

      val advance = apiService.getPrisonerAdvanceById(12345)!!

      with(advance) {
        assertThat(id).isEqualTo(12345)
        assertThat(prisonNumber).isEqualTo("A0001BC")
        assertThat(caseloadId).isEqualTo("LEI")
        assertThat(advanceAmount).isEqualTo(210)
        assertThat(advanceDate).isEqualTo(LocalDate.parse("2024-06-18"))
        assertThat(repaymentAmount).isEqualTo(50)
        assertThat(startDate).isEqualTo(LocalDate.parse("2024-06-18"))
        assertThat(reference).isEqualTo("description of the advance")
        assertThat(comment).isEqualTo("This is a comment")
        assertThat(status).isEqualTo("active")
        assertThat(createDatetime).isEqualTo(LocalDateTime.parse("2024-06-18T12:30:45"))
        assertThat(createdBy).isEqualTo("JD12345")
      }
    }
  }
}
