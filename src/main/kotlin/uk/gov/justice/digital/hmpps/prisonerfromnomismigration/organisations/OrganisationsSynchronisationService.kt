package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OrganisationsSynchronisationService {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun corporateInserted(event: CorporateEvent) {
    log.debug("received corporate insert event {}", event)
  }
  suspend fun corporateUpdated(event: CorporateEvent) {
    log.debug("received corporate updated event {}", event)
  }
  suspend fun corporateDeleted(event: CorporateEvent) {
    log.debug("received corporate deleted event {}", event)
  }
}
