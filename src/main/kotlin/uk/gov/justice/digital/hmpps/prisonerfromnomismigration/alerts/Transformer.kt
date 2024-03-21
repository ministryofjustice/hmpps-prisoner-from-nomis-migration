package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.CommentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.CreateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.UpdateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun AlertResponse.toDPSCreateAlert(offenderNo: String) = CreateAlert(
  prisonNumber = offenderNo,
  alertCode = this.alertCode.code,
  description = this.comment,
  activeFrom = this.date,
  activeTo = this.expiryDate,
  authorisedBy = this.authorisedBy,
)

fun AlertResponse.toDPSUpdateAlert() = UpdateAlert(
  description = this.comment.asInitialComment(),
  comments = this.comment.asCommentAmendments(),
  activeFrom = this.date,
  activeTo = this.expiryDate,
  authorisedBy = this.authorisedBy,
)

// example `[A_MARK_ADM on 21-03-2024 10:58:51]`
const val SEPARATOR_EXPRESSION = "\\[(.+)\\s+on\\s+(\\d{2}-\\d{2}-\\d{4}\\s+\\d{2}:\\d{2}:\\d{2})\\]"
private fun String?.asCommentAmendments(): List<CommentRequest> {
  val comments = this?.split(SEPARATOR_EXPRESSION.toPattern())?.drop(1)?.map { it.trim() } ?: emptyList()
  return SEPARATOR_EXPRESSION
    .toRegex()
    .findAll(this ?: "")
    .toList()
    .zip(comments)
    .map { (matchResult, comment) ->
      CommentRequest(
        comment = comment.trim(),
        createdBy = matchResult.groupValues.getOrNull(1) ?: "",
        createdAt = matchResult.groupValues.getOrNull(2).safeParseDate(),
      )
    }
}

private fun String?.asInitialComment(): String? {
  return this?.split(SEPARATOR_EXPRESSION.toPattern())?.firstOrNull()?.trim()
}

private fun String?.safeParseDate(): LocalDateTime {
  return try {
    this?.let { LocalDateTime.parse(this, DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")) } ?: LocalDateTime.now()
  } catch (e: Exception) {
    LocalDateTime.now()
  }
}
