package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PagedModelLong
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAccountDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.collections.map
import kotlin.math.min

@Component
class PrisonerBalanceNomisApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubGetRootOffenderIdsToMigrate(totalElements: Long = 20, pageSize: Long = 20, firstRootOffenderId: Long = 10000) {
    val content: List<Long> = (1..min(pageSize, totalElements)).map { firstRootOffenderId + it - 1 }
    nomisApi.stubFor(
      get(urlPathEqualTo("/finance/prisoners/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            objectMapper.writeValueAsString(
              PagedModelLong(
                content = content,
                page = PageMetadata(
                  propertySize = pageSize,
                  number = 0,
                  totalPages = totalElements,
                  totalElements = totalElements,
                ),
              ),
            ),
          ),
      ),
    )
  }

  fun stubGetPrisonerBalance(
    rootOffenderId: Long = 10000,
    prisonNumber: String = "A0001BC",
    prisonerBalance: PrisonerBalanceDto = prisonerBalance(rootOffenderId = rootOffenderId, prisonNumber = prisonNumber),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/finance/prisoners/$rootOffenderId/balance")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            objectMapper.writeValueAsString(prisonerBalance),
          ),
      ),
    )
  }

  fun stubGetPrisonerBalanceNotFound(
    rootOffenderId: Long = 1,
    status: HttpStatus = HttpStatus.NOT_FOUND,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/finance/prisoners/$rootOffenderId/balance")).willReturn(
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

fun prisonerBalance(rootOffenderId: Long = 12345, prisonNumber: String = "A0001BC"): PrisonerBalanceDto = PrisonerBalanceDto(
  rootOffenderId = rootOffenderId,
  prisonNumber = prisonNumber,
  accounts = listOf(
    PrisonerAccountDto(
      prisonId = "ASI",
      lastTransactionId = 173,
      accountCode = 2101,
      balance = BigDecimal.valueOf(23.50),
      holdBalance = BigDecimal.valueOf(1.25),
      transactionDate = LocalDateTime.parse("2025-07-02T01:02:05"),
    ),
  ),
)
