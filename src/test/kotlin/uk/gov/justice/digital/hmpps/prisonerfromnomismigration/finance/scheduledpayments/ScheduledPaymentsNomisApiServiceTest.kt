package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.LocalDate

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(ScheduledPaymentsNomisApiService::class, ScheduledPaymentsNomisApiMockServer::class)
class ScheduledPaymentsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: ScheduledPaymentsNomisApiService

  @Autowired
  private lateinit var mockServer: ScheduledPaymentsNomisApiMockServer

  @Nested
  inner class GetScheduledPayment {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetScheduledPayment()

      apiService.getScheduledPayment(12345)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetScheduledPayment()

      apiService.getScheduledPayment(12345)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/finance/scheduled-payments/12345")),
      )
    }

    @Test
    fun `will return scheduled payment details`() = runTest {
      mockServer.stubGetScheduledPayment()

      val scheduledPayment = apiService.getScheduledPayment(12345)

      with(scheduledPayment) {
        assertThat(id).isEqualTo(12345)
        assertThat(prisonNumber).isEqualTo("A0001BC")
        assertThat(caseloadId).isEqualTo("LEI")
        assertThat(transactionType).isEqualTo("TELE")
        assertThat(paymentAmount).isEqualTo(210)
        assertThat(startDate).isEqualTo(LocalDate.parse("2024-06-18"))
        assertThat(endDate).isEqualTo(LocalDate.parse("2024-07-30"))
        assertThat(reference).isEqualTo("description of the scheduled payment")
        assertThat(comment).isEqualTo("This is a comment")
        assertThat(createdBy).isEqualTo("JD12345")
      }
    }

    @Test
    fun `will throw when scheduled payment is not found`() = runTest {
      mockServer.stubGetScheduledPaymentNotFound()

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getScheduledPayment(12345)
      }
    }
  }

  @Nested
  inner class GetScheduledPaymentOrNull {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetScheduledPayment()

      apiService.getScheduledPaymentOrNull(12345)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetScheduledPayment()

      apiService.getScheduledPaymentOrNull(12345)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/finance/scheduled-payments/12345")),
      )
    }

    @Test
    fun `will return scheduled payment details`() = runTest {
      val scheduledPayment = prisonerScheduledPayment()
      mockServer.stubGetScheduledPayment(scheduledPayment = scheduledPayment)

      assertThat(apiService.getScheduledPaymentOrNull(12345)).isEqualTo(scheduledPayment)
    }

    @Test
    fun `will return null when scheduled payment is not found`() = runTest {
      mockServer.stubGetScheduledPaymentNotFound()

      assertThat(apiService.getScheduledPaymentOrNull(12345)).isNull()
    }

    @Test
    fun `will propagate server errors`() = runTest {
      mockServer.stubGetScheduledPaymentNotFound(status = HttpStatus.INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getScheduledPaymentOrNull(12345)
      }
    }
  }

  @Nested
  inner class GetActivePrisonerScheduledPayments {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetActivePrisonerScheduledPayments()

      apiService.getActivePrisonerScheduledPayments(12345)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS root offender id to service`() = runTest {
      mockServer.stubGetActivePrisonerScheduledPayments()

      apiService.getActivePrisonerScheduledPayments(12345)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/finance/prisoners/root-offender-id/12345/scheduled-payments")),
      )
    }

    @Test
    fun `will return scheduled payment details`() = runTest {
      mockServer.stubGetActivePrisonerScheduledPayments()

      val scheduledPayments = apiService.getActivePrisonerScheduledPayments(12345)

      assertThat(scheduledPayments).hasSize(1)
      with(scheduledPayments.first()) {
        assertThat(id).isEqualTo(12345)
        assertThat(prisonNumber).isEqualTo("A0001BC")
        assertThat(caseloadId).isEqualTo("LEI")
        assertThat(transactionType).isEqualTo("TELE")
        assertThat(paymentAmount).isEqualTo(210)
        assertThat(startDate).isEqualTo(LocalDate.parse("2024-06-18"))
        assertThat(endDate).isEqualTo(LocalDate.parse("2024-07-30"))
        assertThat(reference).isEqualTo("description of the scheduled payment")
        assertThat(comment).isEqualTo("This is a comment")
        assertThat(createdBy).isEqualTo("JD12345")
      }
    }

    @Test
    fun `will return an empty list when prisoner has no active scheduled payments`() = runTest {
      mockServer.stubGetActivePrisonerScheduledPayments(scheduledPayments = emptyList())

      assertThat(apiService.getActivePrisonerScheduledPayments(12345)).isEmpty()
    }
  }

  @Nested
  @Disabled("Waiting for the NOMIS active scheduled payments count endpoint")
  inner class GetActiveScheduledPaymentsCount {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetActiveScheduledPaymentsCount()

      apiService.getActiveScheduledPaymentsCount()

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the active scheduled payments count endpoint`() = runTest {
      mockServer.stubGetActiveScheduledPaymentsCount()

      apiService.getActiveScheduledPaymentsCount()

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/finance/prisoners/scheduled-payments/active-count")),
      )
    }

    @Test
    fun `will return active scheduled payments count`() = runTest {
      mockServer.stubGetActiveScheduledPaymentsCount(count = 235)

      val scheduledPaymentsCount = apiService.getActiveScheduledPaymentsCount()

      assertThat(scheduledPaymentsCount.activeCount).isEqualTo(235)
    }
  }
}
