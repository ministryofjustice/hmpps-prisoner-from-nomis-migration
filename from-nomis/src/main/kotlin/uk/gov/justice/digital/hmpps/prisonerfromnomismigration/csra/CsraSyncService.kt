package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CsraGetDto
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CsraSyncService {
  fun todo() {}
}

data class CsraDPSDto(
  @Schema(description = "Prisoner number", example = "A1234BC", required = true)
  val prisonerNumber: String,

  @Schema(description = "CSRA reviews for this prisoner", required = true)
  val reviews: List<CsraReviewDto>,
)

data class CsraReviewDto(
  val bookingId: Long,
  val sequenceNumber: Int,
  // val prisonId: String, prison where the assessment took place? not easy to obtain
  val assessmentDate: LocalDate,
  val assessmentType: CsraGetDto.Type,
  val calculatedLevel: CsraGetDto.CalculatedLevel? = null,
  val score: BigDecimal,
  val status: CsraGetDto.Status,
  val staffId: Long,
  val committeeCode: CsraGetDto.CommitteeCode? = null,
  val nextReviewDate: LocalDate? = null,
  val comment: String? = null,
  val placementPrisonId: String? = null,
  val createdDateTime: LocalDateTime,
  val createdBy: String,
  val reviewLevel: CsraGetDto.ReviewLevel? = null,
  val approvedLevel: CsraGetDto.ApprovedLevel? = null,
  val evaluationDate: LocalDate? = null,
  val evaluationResultCode: CsraGetDto.EvaluationResultCode? = null,
  val reviewCommitteeCode: CsraGetDto.ReviewCommitteeCode? = null,
  val reviewCommitteeComment: String? = null,
  val reviewPlacementPrisonId: String? = null,
  val reviewComment: String? = null,
  val reviewDetails: List<CsraReviewDetailDto>,
)

data class CsraReviewDetailDto(
  val code: String,
  val description: String? = null,
  val questions: List<CsraQuestionDto>,
)

data class CsraQuestionDto(
  val code: String,
  val description: String? = null,
  val responses: List<CsraResponseDto>,
)

data class CsraResponseDto(
  val code: String,
  val answer: String? = null,
  val comment: String? = null,
)
