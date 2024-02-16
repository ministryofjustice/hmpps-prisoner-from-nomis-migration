package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AlertsSynchronisationService(
  private val mappingApiService: AlertsMappingApiService,
  private val nomisApiService: AlertsNomisApiService,
  private val dpsApiService: AlertsDpsApiService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun nomisAlertInserted(event: AlertInsertedEvent) {
    log.debug("TODO: handle {}", event)
  }

  suspend fun nomisAlertUpdated(event: AlertUpdatedEvent) {
    log.debug("TODO: handle {}", event)
  }
}
