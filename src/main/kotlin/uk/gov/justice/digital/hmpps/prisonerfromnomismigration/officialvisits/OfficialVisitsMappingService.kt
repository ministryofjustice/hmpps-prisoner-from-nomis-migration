package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping

@Service
class OfficialVisitsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<Any>("", webClient)
