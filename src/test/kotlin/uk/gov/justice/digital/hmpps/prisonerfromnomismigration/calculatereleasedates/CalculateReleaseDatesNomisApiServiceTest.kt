package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(CalculateReleaseDatesNomisApiService::class, CalculateReleaseDatesNomisApiMockServer::class)
class CalculateReleaseDatesNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CalculateReleaseDatesNomisApiService

  @Autowired
  private lateinit var mockServer: CalculateReleaseDatesNomisApiMockServer

  @Nested
  inner class GetSentenceCalculation {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetSentenceCalculation(1234567)

      apiService.getSentenceCalculation(1234567)

      mockServer.verify(getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `will request the calculation by id`() = runTest {
      mockServer.stubGetSentenceCalculation(1234567)

      apiService.getSentenceCalculation(1234567)

      mockServer.verify(getRequestedFor(urlEqualTo("/sentence-calculations/1234567")))
    }

    @Test
    fun `will return the calculation`() = runTest {
      mockServer.stubGetSentenceCalculation(1234567)

      val calculation = apiService.getSentenceCalculation(1234567)

      assertThat(calculation.id).isEqualTo(1234567)
      assertThat(calculation.bookingId).isEqualTo(123456)
      assertThat(calculation.offenderNo).isEqualTo("A1234KT")
      assertThat(calculation.calculationReasonCode).isEqualTo("NEW")
      assertThat(calculation.crdCalculatedDate.toString()).isEqualTo("2030-01-02")
      assertThat(calculation.sedCalculatedDate.toString()).isEqualTo("2031-02-03")
    }

    @Test
    fun `will throw when the calculation is not found`() = runTest {
      mockServer.stubGetSentenceCalculationNotFound(1234567)

      val exception = assertThrows<WebClientResponseException.NotFound> { apiService.getSentenceCalculation(1234567) }

      assertThat(exception.statusCode.value()).isEqualTo(404)
    }
  }

  @Nested
  inner class GetSentenceCalculationsForPrisoner {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetSentenceCalculationsForPrisoner("A1234KT")

      apiService.getSentenceCalculationsForPrisoner("A1234KT")

      mockServer.verify(getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `will request the calculations for the prisoner`() = runTest {
      mockServer.stubGetSentenceCalculationsForPrisoner("A1234KT")

      apiService.getSentenceCalculationsForPrisoner("A1234KT")

      mockServer.verify(getRequestedFor(urlEqualTo("/prisoners/A1234KT/sentence-calculations")))
    }

    @Test
    fun `will return all the calculations`() = runTest {
      mockServer.stubGetSentenceCalculationsForPrisoner(
        "A1234KT",
        "[${sentenceCalculationJson(id = 1)},${sentenceCalculationJson(id = 2, bookingId = 234567)}]",
      )

      val calculations = apiService.getSentenceCalculationsForPrisoner("A1234KT")

      assertThat(calculations.map { it.id }).containsExactly(1L, 2L)
      assertThat(calculations.map { it.bookingId }).containsExactly(123456L, 234567L)
    }

    @Test
    fun `will return an empty list when the prisoner has no calculations`() = runTest {
      mockServer.stubGetSentenceCalculationsForPrisoner("A1234KT", "[]")

      assertThat(apiService.getSentenceCalculationsForPrisoner("A1234KT")).isEmpty()
    }

    @Test
    fun `will throw when the prisoner is not found`() = runTest {
      mockServer.stubGetSentenceCalculationsForPrisonerNotFound("A1234KT")

      assertThrows<WebClientResponseException.NotFound> { apiService.getSentenceCalculationsForPrisoner("A1234KT") }
    }
  }
}
