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
import java.time.LocalDateTime

@Component
class DrugTestingNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetRandomTestingProgram(
    rtpId: Long = 12345,
    prisonId: String = "MDI",
    month: LocalDate = LocalDate.parse("2025-07-01"),
    response: RandomTestingProgramResponse = randomTestingProgramResponse(rtpId = rtpId, prisonId = prisonId, month = month),
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/drug-testing/${response.rtpId}")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error)),
      ),
    )
  }

  fun stubGetDrugTestingIdRanges(pageSize: Long = 10, totalElements: Long = 20) {
    val content: List<IdRange> = (0..(totalElements / pageSize + if (totalElements % pageSize > 0) 1 else 0))
      .zipWithNext()
      .map { IdRange(it.first * pageSize, it.second * pageSize) }
    nomisApi.stubFor(
      get(urlPathEqualTo("/drug-testing/id-ranges"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(content)),
        ),
    )
  }

  fun stubGetDrugTestingIdsInRange(fromId: Long = 1L, toId: Long = 20L) {
    val content: List<Long> = (fromId..<toId).toList()
    nomisApi.stubFor(
      get(urlPathEqualTo("/drug-testing/ids-in-range"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(content)),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun randomTestingProgramResponse(
  rtpId: Long = 12345,
  prisonId: String = "MDI",
  month: LocalDate = LocalDate.parse("2025-07-01"),
): RandomTestingProgramResponse = RandomTestingProgramResponse(
  rtpId = rtpId,
  caseloadId = prisonId,
  rtpDate = month,
  createdByUsername = "billy",
  createdDateTime = LocalDateTime.parse("2026-02-01T10:20:30"),
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
