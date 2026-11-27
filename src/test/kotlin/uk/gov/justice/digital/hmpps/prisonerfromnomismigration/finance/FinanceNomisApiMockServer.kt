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

  fun stubGetPrisonerTransactionNotFound(
    transactionId: Long = 1001,
    status: HttpStatus = HttpStatus.NOT_FOUND,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/transactions/$transactionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(
            jsonMapper.writeValueAsString(error),
          ),
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

fun nomisGLTransactions(transactionId: Long = 1001) = listOf(
  GeneralLedgerTransactionDto(
    transactionId = transactionId,
    transactionEntrySequence = 1,
    generalLedgerEntrySequence = 1,
    caseloadId = "SWI",
    amount = BigDecimal.valueOf(6.71),
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

fun nomisTransactions(bookingId: Long = 1234L, transactionId: Long = 1001) = listOf(
  offenderTransactionDto(bookingId = bookingId, transactionId = transactionId),
)
fun offenderTransactionDto(bookingId: Long = 1234L, transactionId: Long = 1001) = OffenderTransactionDto(
  transactionId = transactionId,
  transactionEntrySequence = 1,
  offenderId = 9876,
  offenderNo = "A3864DZ",
  caseloadId = "SWI",
  bookingId = bookingId,
  amount = BigDecimal.valueOf(5.42),
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
)

fun prisonerAdvanceWithMultipleTxnTypes(
  bookingId: Long = 1234L,
  transactionId: Long = 1001,
): List<OffenderTransactionDto> {
  val template = offenderTransactionDto(bookingId = bookingId, transactionId = transactionId)

  fun generalLedgerEntry(
    entrySequence: Int,
    glEntrySequence: Int,
    type: String,
    amount: BigDecimal,
    postingType: GeneralLedgerTransactionDto.PostingType,
    accountCode: Int,
    description: String,
    offenderDeductionId: Long? = null,
  ) = nomisGLTransactions(transactionId).first().copy(
    transactionEntrySequence = entrySequence,
    generalLedgerEntrySequence = glEntrySequence,
    caseloadId = template.caseloadId,
    amount = amount,
    type = type,
    postingType = postingType,
    accountCode = accountCode,
    description = description,
    offenderDeductionId = offenderDeductionId,
  )

  fun transactionEntry(
    entrySequence: Int,
    type: String,
    amount: BigDecimal,
    postingType: OffenderTransactionDto.PostingType,
    description: String,
    generalLedgerAccountCodes: Pair<Int, Int>? = null,
    offenderDeductionId: Long? = null,
  ) = template.copy(
    transactionEntrySequence = entrySequence,
    amount = amount,
    type = type,
    postingType = postingType,
    description = description,
    generalLedgerTransactions = generalLedgerAccountCodes?.let { (debitCode, creditCode) ->
      listOf(
        generalLedgerEntry(
          entrySequence = entrySequence,
          glEntrySequence = 1,
          type = type,
          amount = amount,
          postingType = GeneralLedgerTransactionDto.PostingType.DR,
          accountCode = debitCode,
          description = description,
          offenderDeductionId = offenderDeductionId,
        ),
        generalLedgerEntry(
          entrySequence = entrySequence,
          glEntrySequence = 2,
          type = type,
          amount = amount,
          postingType = GeneralLedgerTransactionDto.PostingType.CR,
          accountCode = creditCode,
          description = description,
          offenderDeductionId = offenderDeductionId,
        ),
      )
    } ?: emptyList(),
  )

  return listOf(
    transactionEntry(
      entrySequence = 1,
      type = "OT",
      amount = BigDecimal("4.50"),
      postingType = OffenderTransactionDto.PostingType.DR,
      description = "Sub-Account Transfer 1",
      generalLedgerAccountCodes = 2101 to 2102,
    ),
    transactionEntry(
      entrySequence = 2,
      type = "OT",
      amount = BigDecimal("4.50"),
      postingType = OffenderTransactionDto.PostingType.CR,
      description = "Sub-Account Transfer 2",
    ),
    transactionEntry(
      entrySequence = 3,
      type = "DED",
      amount = BigDecimal("15.59"),
      postingType = OffenderTransactionDto.PostingType.DR,
      description = "Advance Deduction 1",
      generalLedgerAccountCodes = 2102 to 1502,
      offenderDeductionId = 3497205,
    ),

    transactionEntry(
      entrySequence = 4,
      type = "DED",
      amount = BigDecimal("1.50"),
      postingType = OffenderTransactionDto.PostingType.DR,
      description = "Advance deduction 2",
      generalLedgerAccountCodes = 2102 to 1502,
      offenderDeductionId = 3497592,
    ),
    transactionEntry(
      entrySequence = 5,
      type = "DED",
      amount = BigDecimal("3.12"),
      postingType = OffenderTransactionDto.PostingType.DR,
      description = "Advance Deduction 3",
      generalLedgerAccountCodes = 2102 to 1502,
      offenderDeductionId = 3499845,
    ),
    transactionEntry(
      entrySequence = 6,
      type = "SGRNT",
      amount = BigDecimal.ZERO,
      postingType = OffenderTransactionDto.PostingType.CR,
      description = "Subsistence Grant",
      generalLedgerAccountCodes = 1503 to 2102,
    ),
    transactionEntry(
      entrySequence = 7,
      type = "TGRNT",
      amount = BigDecimal("3.00"),
      postingType = OffenderTransactionDto.PostingType.CR,
      description = "Travel Grant",
      generalLedgerAccountCodes = 1503 to 2102,
    ),
    transactionEntry(
      entrySequence = 8,
      type = "DGRNT",
      amount = BigDecimal.ZERO,
      postingType = OffenderTransactionDto.PostingType.CR,
      description = "Discharge Grant",
      generalLedgerAccountCodes = 1503 to 2102,
    ),
    transactionEntry(
      entrySequence = 9,
      type = "DISCP",
      amount = BigDecimal("3.00"),
      postingType = OffenderTransactionDto.PostingType.DR,
      description = "Discharge Payment",
      generalLedgerAccountCodes = 2102 to 1103,
    ),
  )
}
