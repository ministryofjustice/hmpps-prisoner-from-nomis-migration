package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.hmpps.kotlin.auth.HmppsReactiveAuthenticationHolder
import uk.gov.justice.hmpps.sqs.audit.HmppsAuditService

@Service
class AuditService(
  private val hmppsAuditService: HmppsAuditService,
  private val securityUserContext: HmppsReactiveAuthenticationHolder,
  private val mapper: JsonMapper,
) {
  suspend fun sendAuditEvent(what: String, details: Any) {
    hmppsAuditService.publishEvent(
      what = what,
      who = securityUserContext.getPrincipal(),
      details = mapper.writeValueAsString(details),
    )
  }
}

enum class AuditType {
  MIGRATION_STARTED,
  MIGRATION_CANCEL_REQUESTED,
}
