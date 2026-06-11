package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraAssessmentType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraCommitteeCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraEvaluationResultCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraLevel
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraQuestionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraResponseDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraReviewDetailDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.NomisCsraReview
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AssessmentCommittee
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AssessmentLevel
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AssessmentStatusType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AssessmentType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CsraGetDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.EvaluationResultCode

/**
 * See https://dsdmoj.atlassian.net/browse/MAP-3113
 */
fun CsraGetDto.toDPSCreateCsra() = NomisCsraReview(
  bookingId = bookingId,
  nomisSequence = sequence,
  assessmentPrisonId = assessmentCreationLocation,
  assessmentDate = assessmentDate,
  assessmentType = convertType(),
  calculatedLevel = convertLevel(calculatedLevel),
  score = score,
  status = convertStatus(),
  committeeCode = convertCommitteeCode(committeeCode),
  nextReviewDate = nextReviewDate,
  comment = comment,
  placementPrisonId = placementAgencyId,
  createdDateTime = createdDateTime,
  createdBy = createdBy,
  reviewLevel = convertLevel(reviewLevel),
  approvedLevel = convertLevel(approvedLevel),
  evaluationDate = evaluationDate,
  evaluationResultCode = convertResultCode(),
  reviewCommitteeCode = convertCommitteeCode(reviewCommitteeCode),
  reviewCommitteeComment = reviewCommitteeComment,
  reviewPlacementPrisonId = reviewPlacementAgencyId,
  reviewComment = reviewComment,
  reviewDetails = sections.map { section ->
    CsraReviewDetailDto(
      code = section.code,
      description = section.description,
      questions = section.questions.map { question ->
        CsraQuestionDto(
          code = question.code,
          description = question.description,
          responses = question.responses.map { response ->
            CsraResponseDto(
              code = response.code,
              answer = response.answer,
              comment = response.comment,
            )
          },
        )
      },
    )
  },
)

private fun CsraGetDto.convertType(): CsraAssessmentType = when (type) {
  AssessmentType.CSRF -> CsraAssessmentType.CSRF
  AssessmentType.CSRH -> CsraAssessmentType.CSRH
  AssessmentType.CSRDO -> CsraAssessmentType.CSRDO
  AssessmentType.CSR -> CsraAssessmentType.CSR
  AssessmentType.CSR1 -> CsraAssessmentType.CSR1
  AssessmentType.CSRREV -> CsraAssessmentType.CSRREV
  AssessmentType.CATEGORY -> throw RuntimeException("Unknown assessment type: $type")
}

private fun convertLevel(level: AssessmentLevel?): CsraLevel? = when (level) {
  AssessmentLevel.STANDARD -> CsraLevel.STANDARD
  AssessmentLevel.PEND -> CsraLevel.PEND
  AssessmentLevel.LOW -> CsraLevel.LOW
  AssessmentLevel.MED -> CsraLevel.MED
  AssessmentLevel.HI -> CsraLevel.HI
  else -> null
}

private fun CsraGetDto.convertStatus(): CsraStatus = when (status) {
  AssessmentStatusType.A -> CsraStatus.A
  AssessmentStatusType.I -> CsraStatus.I
  AssessmentStatusType.P -> CsraStatus.P
}

private fun convertCommitteeCode(committeeCode: AssessmentCommittee?): CsraCommitteeCode? = when (committeeCode) {
  AssessmentCommittee.GOV -> CsraCommitteeCode.GOV
  AssessmentCommittee.MED -> CsraCommitteeCode.MED
  AssessmentCommittee.OCA -> CsraCommitteeCode.OCA
  AssessmentCommittee.RECP -> CsraCommitteeCode.RECP
  AssessmentCommittee.REVIEW -> CsraCommitteeCode.REVIEW
  AssessmentCommittee.SECSTATE -> CsraCommitteeCode.SECSTATE
  AssessmentCommittee.SECUR -> CsraCommitteeCode.SECUR
  else -> null
}

private fun CsraGetDto.convertResultCode(): CsraEvaluationResultCode? = when (evaluationResultCode) {
  EvaluationResultCode.APP -> CsraEvaluationResultCode.APP
  EvaluationResultCode.REJ -> CsraEvaluationResultCode.REJ
  else -> null
}
