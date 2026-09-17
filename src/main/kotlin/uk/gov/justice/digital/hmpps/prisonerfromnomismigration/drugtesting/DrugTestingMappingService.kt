package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping

@Service
class DrugTestingMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<Any>("/mapping/drug-testing", webClient)
