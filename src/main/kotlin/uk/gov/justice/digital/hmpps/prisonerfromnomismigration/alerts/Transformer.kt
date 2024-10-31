package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.CreateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.ResyncAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.UpdateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import java.time.LocalDateTime

fun AlertResponse.toDPSCreateAlert() = CreateAlert(
  alertCode = this.alertCode.code,
  description = this.comment,
  activeFrom = this.date,
  activeTo = this.expiryDate,
  authorisedBy = this.authorisedBy,
)

fun AlertResponse.toDPSUpdateAlert() = UpdateAlert(
  description = this.comment,
  activeFrom = this.date,
  activeTo = this.expiryDate,
  authorisedBy = this.authorisedBy,
)

fun AlertResponse.toDPSResyncAlert() = ResyncAlert(
  offenderBookId = this.bookingId,
  alertSeq = this.alertSequence.toInt(),
  alertCode = this.alertCode.code,
  description = this.comment,
  activeFrom = this.date,
  activeTo = this.expiryDate,
  authorisedBy = this.authorisedBy,
  createdAt = LocalDateTime.parse(this.audit.createDatetime),
  createdBy = this.audit.createUsername,
  createdByDisplayName = this.audit.createDisplayName ?: this.audit.createUsername,
  lastModifiedAt = this.audit.modifyDatetime?.let { LocalDateTime.parse(this.audit.modifyDatetime) },
  lastModifiedBy = this.audit.modifyUserId,
  lastModifiedByDisplayName = this.audit.modifyDisplayName ?: this.audit.modifyUserId,
  isActive = this.isActive,
)
