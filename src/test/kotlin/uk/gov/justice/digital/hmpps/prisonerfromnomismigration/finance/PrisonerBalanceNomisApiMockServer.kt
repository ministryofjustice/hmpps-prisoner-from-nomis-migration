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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAccountDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAccountsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pagedModelContent
import java.math.BigDecimal
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
            pagedModelContent(
              objectMapper = NomisApiExtension.objectMapper,
              content = content,
              pageSize = pageSize,
              pageNumber = 0,
              totalElements = totalElements,
            ),
          ),
      ),
    )
  }

  fun stubGetPrisonerBalance(
    rootOffenderId: Long = 10000,
    prisonNumber: String = "A0001BC",
    prisonerBalance: PrisonerAccountsDto = prisonerAccounts(rootOffenderId = rootOffenderId, prisonNumber = prisonNumber),
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

  fun stubGetPrisonerBalanceForPrisonerNotFound(
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

fun prisonerAccounts(rootOffenderId: Long = 12345, prisonNumber: String = "A0001BC"): PrisonerAccountsDto = PrisonerAccountsDto(
  rootOffenderId = rootOffenderId,
  prisonNumber = prisonNumber,
  accounts = listOf(
    PrisonerAccountDto(
      prisonId = "ASI",
      lastTransactionId = 173,
      accountCode = 2101,
      balance = BigDecimal.valueOf(23.50),
      holdBalance = BigDecimal.valueOf(1.25),
    ),
  ),
)
