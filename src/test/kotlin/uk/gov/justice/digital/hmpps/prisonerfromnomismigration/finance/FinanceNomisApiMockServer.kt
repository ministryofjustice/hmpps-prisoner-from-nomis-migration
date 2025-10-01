package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonAccountBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.min

@Component
class FinanceNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetOffenderTransaction(
    transactionId: Long = 1001,
    bookingId: Long = 123456,
    response: List<OffenderTransactionDto> = listOf(
      OffenderTransactionDto(
        transactionId = transactionId,
        transactionEntrySequence = 1,
        offenderId = 1234,
        offenderNo = "A1234AA",
        bookingId = bookingId,
        caseloadId = "MDI",
        subAccountType = OffenderTransactionDto.SubAccountType.REG,
        type = "type",
        reference = "FG1/12",
        clientReference = "clientUniqueRef",
        entryDate = LocalDate.parse("2025-06-01"),
        description = "entryDescription",
        amount = BigDecimal.valueOf(2.34),
        createdAt = LocalDateTime.now(),
        postingType = OffenderTransactionDto.PostingType.CR,
        createdBy = "me",
        createdByDisplayName = "Me",
        lastModifiedAt = LocalDateTime.now(),
        lastModifiedBy = "you",
        lastModifiedByDisplayName = "You",
        generalLedgerTransactions = listOf(),
      ),
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/transactions/$transactionId")).willReturn(
        okJson(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetGLTransaction(
    transactionId: Long = 1001,
    type: String = "SPEN",
    response: List<GeneralLedgerTransactionDto> = listOf(
      GeneralLedgerTransactionDto(
        transactionId = transactionId,
        transactionEntrySequence = 1,
        generalLedgerEntrySequence = 1,
        accountCode = 2100,
        transactionTimestamp = LocalDateTime.now(),
        caseloadId = "MDI",
        type = type,
        reference = "FG1/12",
        description = "entryDescription",
        amount = BigDecimal.valueOf(2.34),
        createdAt = LocalDateTime.now(),
        postingType = GeneralLedgerTransactionDto.PostingType.CR,
        createdBy = "me",
        createdByDisplayName = "Me",
        lastModifiedAt = LocalDateTime.now(),
        lastModifiedBy = "you",
        lastModifiedByDisplayName = "You",
      ),
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/transactions/$transactionId/general-ledger")).willReturn(
        okJson(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetPrisonBalanceIds(totalElements: Long = 20, pageSize: Long = 20, firstPrisonBalanceId: String = "MDI") {
    val content: List<String> = (1..min(pageSize, totalElements)).map {
      "$firstPrisonBalanceId$it"
    }
    nomisApi.stubFor(
      get(urlPathEqualTo("/finance/prison/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(content)),
      ),
    )
  }

  fun stubGetPrisonBalance(
    prisonId: String = "MDI",
    prisonBalance: PrisonBalanceDto = PrisonBalanceDto(
      prisonId = prisonId,
      accountBalances = listOf(
        PrisonAccountBalanceDto(
          accountCode = 2101,
          balance = BigDecimal("23.45"),
          transactionDate = LocalDateTime.parse("2025-06-02T02:02:03"),
        ),
      ),
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/finance/prison/$prisonId/balance")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            objectMapper.writeValueAsString(prisonBalance),
          ),
      ),
    )
  }

  fun stubGetPrisonBalanceNotFound(
    nomisId: String = "MDI",
    status: HttpStatus = HttpStatus.NOT_FOUND,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/finance/prison/$nomisId/balance")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(
            objectMapper.writeValueAsString(error),
          ),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun prisonBalance(prisonId: String = "MDI"): PrisonBalanceDto = PrisonBalanceDto(
  prisonId = prisonId,
  accountBalances = listOf(
    PrisonAccountBalanceDto(
      accountCode = 2101,
      balance = BigDecimal.valueOf(23.50),
      transactionDate = LocalDateTime.parse("2025-06-01T01:02:03"),
    ),
  ),
)
