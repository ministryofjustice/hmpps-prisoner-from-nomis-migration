package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.CreateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.MigrateAlertRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.UpdateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import java.time.LocalDateTime

fun AlertResponse.toDPSCreateAlert(offenderNo: String) = CreateAlert(
  prisonNumber = offenderNo,
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
  appendComment = null,
)

fun AlertResponse.toDPSMigratedAlert(offenderNo: String) = MigrateAlertRequest(
  prisonNumber = offenderNo,
  alertCode = this.alertCode.code,
  description = this.comment,
  activeFrom = this.date,
  activeTo = this.expiryDate,
  authorisedBy = this.authorisedBy,
  createdAt = LocalDateTime.parse(this.audit.createDatetime),
  createdBy = this.audit.createUsername,
  createdByDisplayName = this.audit.createDisplayName ?: this.audit.createUsername,
  updatedAt = this.audit.modifyDatetime?.let { LocalDateTime.parse(this.audit.modifyDatetime) },
  updatedBy = this.audit.modifyUserId,
  updatedByDisplayName = this.audit.modifyDisplayName,
  comments = emptyList(),
)
