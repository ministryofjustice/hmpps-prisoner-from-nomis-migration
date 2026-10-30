package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduledPaymentDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.Month

@Component
class ScheduledPaymentsNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetScheduledPayment(
    scheduledPaymentId: Long = 12345,
    prisonNumber: String = "A0001BC",
    scheduledPayment: ScheduledPaymentDto = scheduledPayment(prisonNumber = prisonNumber),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/finance/scheduled-payments/$scheduledPaymentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            jsonMapper.writeValueAsString(scheduledPayment.copy(id = scheduledPaymentId)),
          ),
      ),
    )
  }

  fun stubGetScheduledPaymentNotFound(
    scheduledPaymentId: Long = 12345,
    status: HttpStatus = HttpStatus.NOT_FOUND,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/finance/scheduled-payments/$scheduledPaymentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(
            jsonMapper.writeValueAsString(error),
          ),
      ),
    )
  }

  fun stubGetActivePrisonerScheduledPayments(
    rootOffenderId: Long = 12345,
    prisonNumber: String = "A0001BC",
    scheduledPayments: List<ScheduledPaymentDto> = listOf(scheduledPayment(prisonNumber = prisonNumber)),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/finance/prisoners/root-offender-id/$rootOffenderId/scheduled-payments"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              jsonMapper.writeValueAsString(scheduledPayments),
            ),
        ),
    )
  }

  fun stubGetActiveScheduledPaymentsCount(count: Long = 235) {
    nomisApi.stubFor(
      get(urlEqualTo("/finance/scheduled-payments/active-count")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            jsonMapper.writeValueAsString(ScheduledPaymentCount(activeCount = count)),
          ),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun scheduledPayment(prisonNumber: String = "A0001BC"): ScheduledPaymentDto = ScheduledPaymentDto(
  id = 12345L,
  prisonNumber = prisonNumber,
  caseloadId = "LEI",
  transactionType = "TELE",
  amount = 210,
  dayOfWeek = ScheduledPaymentDto.DayOfWeek.WED,
  subAccountCode = 2103,
  startDate = LocalDate.of(2024, Month.JUNE, 18),
  endDate = LocalDate.of(2024, Month.JULY, 30),
  reference = "description of the scheduled payment",
  comment = "This is a comment",
  createdBy = "JD12345",
)
