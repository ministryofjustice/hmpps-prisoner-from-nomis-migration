package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

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

private const val OFFENDER_NUMBER = "G4803UT"

@SpringAPIServiceTest
@Import(CsraNomisApiService::class, CsraConfiguration::class, CsraNomisApiMockServer::class)
class CsraNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CsraNomisApiService

  @Autowired
  private lateinit var csraNomisApiMockServer: CsraNomisApiMockServer

  @Nested
  inner class GetCsra {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      csraNomisApiMockServer.stubGetCsrasForPrisoner(OFFENDER_NUMBER)

      apiService.getCsras(OFFENDER_NUMBER)

      csraNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      csraNomisApiMockServer.stubGetCsrasForPrisoner(OFFENDER_NUMBER)

      apiService.getCsras(OFFENDER_NUMBER)

      csraNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/$OFFENDER_NUMBER/csras")),
      )
    }

    @Test
    fun `will return csras`() = runTest {
      csraNomisApiMockServer.stubGetCsrasForPrisoner(OFFENDER_NUMBER, listOf(csraGetDto(101), csraGetDto(102)))

      val response = apiService.getCsras(OFFENDER_NUMBER)

      assertThat(response.csras[0].bookingId).isEqualTo(101L)
      assertThat(response.csras[1].bookingId).isEqualTo(102L)
    }
  }
}
