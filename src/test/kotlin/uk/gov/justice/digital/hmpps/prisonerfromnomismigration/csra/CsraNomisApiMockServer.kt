package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CsraGetDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CsraGetDto.Type
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CsraQuestionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CsraResponseDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CsraSectionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerCsrasResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CsraNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetCsra(
    csraId: Long = 1001,
    bookingId: Long = 123456,
    auditModuleName: String = "OIDNOMIS",
    csra: CsraGetDto = csraGetDto(bookingId),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/csras/$csraId")).willReturn(
        okJson(jsonMapper.writeValueAsString(csra)),
      ),
    )
  }

  fun stubGetCsraForPrisoner(
    offenderNo: String,
    bookingId: Long = 1L,
    currentCsraStart: Int = 1,
    currentCsraCount: Int = 1,
    auditModuleName: String = "OIDNOMIS",
    csra: CsraGetDto = csraGetDto(bookingId),
  ) {
    val response = PrisonerCsrasResponse(
      csras = (0..<currentCsraCount).map { i ->
        csra.copy(
          sequence = i + currentCsraStart,
          sections = csra.sections.map { section ->
            section.copy(
              questions = section.questions.map { question ->
                question.copy(
                  code = "CODE2",
                  responses = question.responses.map { response -> response.copy() },
                )
              },
            )
          },
        )
      },
    )
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/csras")).willReturn(
        okJson(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetCsrasForPrisoner(
    offenderNo: String,
    csras: List<CsraGetDto> = listOf(csraGetDto(101)),
  ) {
    val response = PrisonerCsrasResponse(csras = csras)
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/csras")).willReturn(
        okJson(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubPutCsra(
    csraId: Long,
    status: HttpStatus = HttpStatus.OK,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/csras/$csraId")).willReturn(status(status.value())),
    )
  }

  fun stubDeleteCsra(
    csraId: Long,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/csras/$csraId")).willReturn(status(HttpStatus.NO_CONTENT.value())),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun csraGetDto(bookingId: Long): CsraGetDto = CsraGetDto(
  bookingId = bookingId,
  sequence = 1,
  assessmentDate = LocalDate.parse("2021-02-03"),
  type = Type.CSR,
  score = BigDecimal.valueOf(1001),
  status = CsraGetDto.Status.A,
  assessmentStaffId = 1001,
  createdDateTime = LocalDateTime.parse("2024-11-03T04:05:06"),
  createdBy = "me",
  agencyId = "MDI",
  calculatedLevel = CsraGetDto.CalculatedLevel.STANDARD,
  committeeCode = CsraGetDto.CommitteeCode.GOV,
  nextReviewDate = LocalDate.parse("2021-02-03"),
  comment = "comment",
  placementAgencyId = "placementAgencyId",
  reviewLevel = CsraGetDto.ReviewLevel.LOW,
  approvedLevel = CsraGetDto.ApprovedLevel.MED,
  evaluationDate = LocalDate.parse("2021-02-03"),
  evaluationResultCode = CsraGetDto.EvaluationResultCode.APP,
  reviewCommitteeCode = CsraGetDto.ReviewCommitteeCode.SECSTATE,
  reviewCommitteeComment = "reviewCommitteeComment",
  reviewPlacementAgencyId = "reviewPlacementAgencyId",
  reviewComment = "reviewComment",
  sections = listOf(
    CsraSectionDto(
      code = "CODE1",
      description = "section description",
      questions = listOf(
        CsraQuestionDto(
          code = "CODE2",
          description = "question description",
          responses = listOf(
            CsraResponseDto(code = "CODE3", answer = "answer", comment = "response comment"),
          ),
        ),
      ),
    ),
  ),
)
