package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.NomisAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// The initial contract with DPS was to send the entire NOMIS alert, hence the DTO name. This is likely to change
// but for now copy as if we are sending all this data
fun AlertResponse.toDPsAlert(offenderNo: String) = NomisAlert(
  alertDate = this.date,
  offenderBookId = this.bookingId,
  offenderNo = offenderNo,
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
  // TODO - this will be removed
  rootOffenderId = 0,
  authorizePersonText = this.authorisedBy,
  // TODO - always null and will be removed
  createDate = null,
  // TODO - not being returned from NOMIS yet
  verifiedDatetime = null,
  // TODO - not being returned from NOMIS yet
  verifiedUserId = null,
  expiryDate = this.expiryDate,
  commentText = this.comment,
  // TODO - always NULL and will be removed
  caseloadId = null,
  modifyUserId = this.audit.modifyUserId,
  modifyDatetime = this.audit.modifyDatetime?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
  // TODO always this value and will be removed
  caseloadType = "INST",
  createUserId = this.audit.createUsername,
  auditTimestamp = this.audit.auditTimestamp?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
  auditUserId = this.audit.auditUserId,
  auditModuleName = this.audit.auditModuleName,
  auditClientUserId = this.audit.auditClientUserId,
  auditClientIpAddress = this.audit.auditClientIpAddress,
  auditClientWorkstationName = this.audit.auditClientWorkstationName,
  auditAdditionalInfo = this.audit.auditAdditionalInfo,
)
