import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.AuditorAware
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SecurityUserContext
import java.util.Optional

@Configuration
class AuditConfiguration

@Service(value = "auditorAware")
class AuditorAwareImpl(private val securityUserContext: SecurityUserContext) : AuditorAware<String> {
  override fun getCurrentAuditor(): Optional<String> {
    return Optional.ofNullable(securityUserContext.principal)
  }
}
