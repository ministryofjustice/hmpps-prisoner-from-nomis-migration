package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderSentenceCalculationResponse

fun List<OffenderSentenceCalculationResponse>.toMigrationRequest() = LegacySentenceCalculationMigrationRequest(sentenceCalculations = map { it.toLegacySentenceCalculation() })

/**
 * NOMIS fields with no equivalent in DPS and therefore not sent: staffId, effectiveSentenceEndDate,
 * effectiveSentenceLength, judiciallyImposedSentenceLength and workflowHistoryId.
 */
fun OffenderSentenceCalculationResponse.toLegacySentenceCalculation() = LegacySentenceCalculationDto(
  nomisCalculationId = id,
  prisonerId = offenderNo,
  bookingId = bookingId,
  calculationDate = calculationDate,
  calculationReasonCode = calculationReasonCode,
  nomisCreatedAt = createDatetime,
  nomisCreatedBy = createUsername,
  commentText = commentText,
  hdcedCalculatedDate = hdcedCalculatedDate,
  hdcedOverriddenDate = hdcedOverridedDate,
  hdcadCalculatedDate = hdcadCalculatedDate,
  hdcadOverriddenDate = hdcadOverridedDate,
  etdCalculatedDate = etdCalculatedDate,
  etdOverriddenDate = etdOverridedDate,
  mtdCalculatedDate = mtdCalculatedDate,
  mtdOverriddenDate = mtdOverridedDate,
  ltdCalculatedDate = ltdCalculatedDate,
  ltdOverriddenDate = ltdOverridedDate,
  ardCalculatedDate = ardCalculatedDate,
  ardOverriddenDate = ardOverridedDate,
  crdCalculatedDate = crdCalculatedDate,
  crdOverriddenDate = crdOverridedDate,
  pedCalculatedDate = pedCalculatedDate,
  pedOverriddenDate = pedOverridedDate,
  apdCalculatedDate = apdCalculatedDate,
  apdOverriddenDate = apdOverridedDate,
  npdCalculatedDate = npdCalculatedDate,
  npdOverriddenDate = npdOverridedDate,
  ledCalculatedDate = ledCalculatedDate,
  ledOverriddenDate = ledOverridedDate,
  sedCalculatedDate = sedCalculatedDate,
  sedOverriddenDate = sedOverridedDate,
  prrdCalculatedDate = prrdCalculatedDate,
  prrdOverriddenDate = prrdOverridedDate,
  tariffCalculatedDate = tariffCalculatedDate,
  tariffOverriddenDate = tariffOverridedDate,
  dprrdCalculatedDate = dprrdCalculatedDate,
  dprrdOverriddenDate = dprrdOverridedDate,
  ersedOverriddenDate = ersedOverridedDate,
  tersedOverriddenDate = tersedOverridedDate,
  rotlOverriddenDate = rotlOverridedDate,
  tusedCalculatedDate = tusedCalculatedDate,
  tusedOverriddenDate = tusedOverridedDate,
  nomisModifiedAt = modifyDatetime,
  nomisModifiedBy = modifyUserId,
  nomisRecordedAt = recordDatetime,
  nomisRecordedBy = recordUserId,
)
