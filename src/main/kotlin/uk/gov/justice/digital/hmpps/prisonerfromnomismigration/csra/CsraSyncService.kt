package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CsraGetDto
import java.math.BigDecimal
import java.time.LocalDate

@Service
class CsraSyncService {
  fun todo() {}
}

data class CsraReviewDto(
  val bookingId: Long,
  val sequenceNumber: Int,
  val prisonId: String,
  val assessmentDate: LocalDate,
  val assessmentType: CsraGetDto.Type,
  val calculatedLevel: CsraGetDto.CalculatedLevel,
  val score: BigDecimal,
  val status: CsraGetDto.Status,
  val staffId: Long,

)

data class CsraDPSDto(
  @Schema(description = "Prisoner number", example = "A1234BC", required = true)
  val prisonerNumber: String,

  val reviews: List<CsraReviewDto>,
  /* TODO
  {
    "prisonerNumber:": "AA1234A",
    "reviews": [
      {
        "bookingId": 22333423,
        "sequenceNo": 1,
        "prisonId": "MDI",
        "assessmentDate": "2025-11-22",
        "type": "CSRF",
        "calculatedLevel": "STANDARD",
        "score": 1000,
        "status": "INACTIVE",
        "assessmentStaffId": 123456,
        "committeeCode": "GOV",
        "nextReviewDate": "2026-01-08",
        "comment": "string",
        "placementPrisonId": "LEI",
        "createdDateTime": "2025-12-06T12:34:56",
        "createdBy": "NQP56Y",
        "reviewLevel": "STANDARD",
        "approvedLevel": "STANDARD",
        "evaluationDate": "2026-01-08",
        "evaluationResultCode": "APP",
        "reviewCommitteeCode": "GOV",
        "reviewCommitteeComment": "string",
        "reviewPlacementPrisonId": "string",
        "reviewComment": "string",
        "reviewDetails": [
          {
            "code": "CSRREV1",
            "desc": "Section 1: Cell Share RIsk Review",
            "questions": [
              {
                "code": "9",
                "desc": "Based on the above  the risk of harming a cell-mate is",
                "responses": [
                  {
                    "code": "2",
                    "answer": "Medium"
                  }
                ]
              },
              {
                "code": "4",
                "desc": "Any anti-social or hate-motivated behaviour, bullying, threats, damage to property, aggression, assaults?",
                "responses": [
                  {
                    "code": "1",
                    "answer": "Yes ( Please enter details in Notes )",
                    "comments": "some comments"
                  }
                ]
              }
            ]
          }
        ]
      }
    ]
  }
   */
)
