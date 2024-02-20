package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.NomisAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// The initial contract with DPS was to send the entire NOMIS alert, hence the DTO name. This is likely to change
// but for now copy as if we are sending all this data
@Suppress("ktlint:standard:discouraged-comment-location")
fun AlertResponse.toDPsAlert() = NomisAlert(
  alertDate = this.date,
  offenderBookId = this.bookingId,
  offenderNo = "A1234TT", // TODO - return in NOMIS API
  alertSeq = this.alertSequence.toInt(),
  alertType = this.type.code,
  alertCode = this.alertCode.code,
  alertStatus = if (this.isActive) {
    "ACTIVE"
  } else {
    "INACTIVE"
  },
  verifiedFlag = this.isVerified,
  createDatetime = LocalDateTime.parse(this.audit.createDatetime, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
  rootOffenderId = 0, // TODO - this will be removed
  authorizePersonText = this.authorisedBy,
  createDate = null, // TODO - always null and will be removed
  verifiedDatetime = null, // TODO - not being returned from NOMIS yet
  verifiedUserId = null, // TODO - not being returned from NOMIS yet
  expiryDate = this.expiryDate,
  commentText = this.comment,
  caseloadId = null, // TODO - always NULL and will be removed
  modifyUserId = this.audit.modifyUserId,
  modifyDatetime = this.audit.modifyDatetime?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
  caseloadType = "INST", // TODO always this value and will be removed
  createUserId = this.audit.createUsername,
  auditTimestamp = this.audit.auditTimestamp?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
  auditUserId = this.audit.auditUserId,
  auditModuleName = this.audit.auditModuleName,
  auditClientUserId = this.audit.auditClientUserId,
  auditClientIpAddress = this.audit.auditClientIpAddress,
  auditClientWorkstationName = this.audit.auditClientWorkstationName,
  auditAdditionalInfo = this.audit.auditAdditionalInfo,
)
