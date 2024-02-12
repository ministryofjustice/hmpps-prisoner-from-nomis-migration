package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import uk.gov.justice.hmpps.kotlin.auth.AuthAwareAuthenticationToken

@Component
class SecurityUserContext {
  val authentication: AuthAwareAuthenticationToken?
    get() = with(SecurityContextHolder.getContext().authentication) {
      when (this) {
        is AuthAwareAuthenticationToken -> this
        else -> null
      }
    }

  // TODO revisit this - will only currently be for audit calls but could be improved.
  suspend fun username() = ReactiveSecurityContextHolder.getContext()?.map { it.authentication.principal }?.awaitFirstOrNull().toString()

  val principal: String
    get() {
      return if (authentication?.principal != null) {
        authentication?.principal!!
      } else {
        "anonymous"
      }
    }
}
