package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

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

// TODO fun stubPutTransaction(
//    transactionId: Long,
//    status: HttpStatus = HttpStatus.OK,
//  ) {
//    nomisApi.stubFor(
//      put(urlEqualTo("/transactions/$transactionId")).willReturn(status(status.value())),
//    )
//  }
//
//  fun stubDeleteTransaction(
//    transactionId: Long,
//  ) {
//    nomisApi.stubFor(
//      delete(urlEqualTo("/transactions/$transactionId")).willReturn(status(HttpStatus.NO_CONTENT.value())),
//    )
//  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
