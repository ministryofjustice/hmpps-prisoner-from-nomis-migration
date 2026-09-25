package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAdvanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import kotlin.Long
import kotlin.String

@Component
class AdvancesNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetAdvanceById(
    advanceId: Long = 12345,
    prisonNumber: String = "A0001BC",
    prisonerAdvance: PrisonerAdvanceDto? = prisonerAdvance(prisonNumber = prisonNumber),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/finance/prisoners/advances/$advanceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            jsonMapper.writeValueAsString(prisonerAdvance?.copy(id = advanceId)),
          ),
      ),
    )
  }

  fun stubGetAdvanceByIdNotFound(
    advanceId: Long = 12345,
    status: HttpStatus = HttpStatus.NOT_FOUND,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/finance/prisoners/advances/$advanceId")).willReturn(
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

fun prisonerAdvance(prisonNumber: String = "A0001BC"): PrisonerAdvanceDto = PrisonerAdvanceDto(
  id = 12345L,
  prisonNumber = prisonNumber,
  caseloadId = "LEI",
  advanceAmount = 210,
  advanceDate = LocalDate.of(2024, Month.JUNE, 18),
  repaymentAmount = 50,
  startDate = LocalDate.of(2024, Month.JUNE, 18),
  reference = "description of the advance",
  comment = "This is a comment",
  status = "active",
  createdBy = "JD12345",
  createDatetime = LocalDateTime.of(2024, Month.JUNE, 18, 12, 30, 45),
)
