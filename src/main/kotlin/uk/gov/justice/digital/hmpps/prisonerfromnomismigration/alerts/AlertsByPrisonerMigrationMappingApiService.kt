package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto

@Service
class AlertsByPrisonerMigrationMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<List<AlertMappingDto>>(domainUrl = "/mapping/alerts/all", webClient)
