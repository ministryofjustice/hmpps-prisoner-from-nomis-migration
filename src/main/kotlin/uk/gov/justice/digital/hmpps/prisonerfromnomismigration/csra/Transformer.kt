package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CsraGetDto

/**
 * See https://dsdmoj.atlassian.net/browse/MAP-3113
 */
fun CsraGetDto.toDPSCreateCaseNote() = CsraReviewDto(
  bookingId = bookingId,
  sequenceNumber = sequence,
  // TODO prisonId = prison,
  assessmentDate = assessmentDate,
  assessmentType = type,
  calculatedLevel = calculatedLevel,
  score = score,
  status = status,
  staffId = assessmentStaffId,
  committeeCode = committeeCode,
  nextReviewDate = nextReviewDate,
  comment = comment,
  placementPrisonId = placementAgencyId,
  createdDateTime = createdDateTime,
  createdBy = createdBy,
  reviewLevel = reviewLevel,
  approvedLevel = approvedLevel,
  evaluationDate = evaluationDate,
  evaluationResultCode = evaluationResultCode,
  reviewCommitteeCode = reviewCommitteeCode,
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
