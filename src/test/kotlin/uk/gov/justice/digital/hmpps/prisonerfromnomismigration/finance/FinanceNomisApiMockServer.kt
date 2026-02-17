package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.PrisonerTransactionSynchronisationIntTest.Companion.BOOKING_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.PrisonerTransactionSynchronisationIntTest.Companion.NOMIS_TRANSACTION_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.PrisonerTransactionSynchronisationIntTest.Companion.OFFENDER_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.PrisonerTransactionSynchronisationIntTest.Companion.OFFENDER_ID_DISPLAY
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
class FinanceNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetPrisonerTransaction(
    transactionId: Long = 1001,
    bookingId: Long = 123456,
    response: List<OffenderTransactionDto> = nomisTransactions(bookingId = bookingId, transactionId = transactionId),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/transactions/$transactionId")).willReturn(
        okJson(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetPrisonTransaction(
    transactionId: Long = 1001,
    response: List<GeneralLedgerTransactionDto> = nomisGLTransactions(transactionId),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/transactions/$transactionId/general-ledger")).willReturn(
        okJson(jsonMapper.writeValueAsString(response)),
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
          .withBody(jsonMapper.writeValueAsString(content)),
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
            jsonMapper.writeValueAsString(prisonBalance),
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
            jsonMapper.writeValueAsString(error),
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

fun nomisGLTransactions(transactionId: Long = NOMIS_TRANSACTION_ID) = listOf(
  GeneralLedgerTransactionDto(
    transactionId = transactionId,
    transactionEntrySequence = 1,
    generalLedgerEntrySequence = 1,
    caseloadId = "SWI",
    amount = BigDecimal(5.4),
    type = "SPEN",
    postingType = GeneralLedgerTransactionDto.PostingType.CR,
    accountCode = 1021,
    description = "GL desc",
    transactionTimestamp = LocalDateTime.parse("2021-02-03T04:05:09"),
    reference = "ref",
    createdAt = LocalDateTime.parse("2021-02-03T04:05:07"),
    createdBy = "me",
    createdByDisplayName = "Me",
    lastModifiedAt = LocalDateTime.parse("2021-02-03T04:05:59"),
    lastModifiedBy = "you",
    lastModifiedByDisplayName = "You",
  ),
)

fun nomisTransactions(bookingId: Long = BOOKING_ID, transactionId: Long = NOMIS_TRANSACTION_ID) = listOf(
  OffenderTransactionDto(
    transactionId = transactionId,
    transactionEntrySequence = 1,
    offenderId = OFFENDER_ID,
    offenderNo = OFFENDER_ID_DISPLAY,
    caseloadId = "SWI",
    bookingId = bookingId,
    amount = BigDecimal(5.4),
    type = "type",
    postingType = OffenderTransactionDto.PostingType.CR,
    description = "desc",
    entryDate = LocalDate.parse("2021-02-03"),
    subAccountType = OffenderTransactionDto.SubAccountType.REG,
    generalLedgerTransactions = nomisGLTransactions(),
    createdAt = LocalDateTime.parse("2021-02-03T04:05:06"),
    createdBy = "me",
    createdByDisplayName = "Me",
    clientReference = "clientref",
    reference = "ref",
    lastModifiedAt = LocalDateTime.parse("2021-02-03T04:05:59"),
    lastModifiedBy = "you",
    lastModifiedByDisplayName = "You",
  ),
)
