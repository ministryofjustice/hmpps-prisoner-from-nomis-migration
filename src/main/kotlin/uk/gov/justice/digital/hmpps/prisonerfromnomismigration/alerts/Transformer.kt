package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.CreateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.UpdateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse

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
