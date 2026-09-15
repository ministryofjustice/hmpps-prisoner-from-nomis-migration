package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IdRange
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTestSelectionResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RandomTestingProgramResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

@Component
class DrugTestingNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetRandomTestingProgram(
    rtpId: Long = 12345,
    response: RandomTestingProgramResponse = randomTestingProgramResponse(rtpId = rtpId),
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/drug-testing/$rtpId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error)),
      ),
    )
  }

  fun stubGetDrugTestingIdRanges(
    response: List<IdRange> = listOf(IdRange(1, 10), IdRange(11, 20)),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/drug-testing/id-ranges"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetDrugTestingIdsInRange(
    response: List<Long> = listOf(2, 3, 4),
  ) {
    val builder = get(urlPathEqualTo("/drug-testing/ids-in-range"))

    nomisApi.stubFor(
      builder.willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun randomTestingProgramResponse(rtpId: Long = 12345): RandomTestingProgramResponse = RandomTestingProgramResponse(
  rtpId = rtpId,
  caseloadId = "MDI",
  rtpDate = LocalDate.parse("2025-07-01"),
  mainPercentage = 10,
  reservePercentage = 5,
  offenderTestSelection = listOf(
    OffenderTestSelectionResponse(
      offenderBookId = 1001,
      prisonNumber = "A1234BC",
      testSelectionType = "MAIN",
      testSelectionNo = 1,
      testedFlag = true,
      notes = "Selected for testing",
    ),
  ),
  selectionsCount = 1,
  eligibleCount = 20,
  reserveCount = 2,
)
