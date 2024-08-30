package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest

@Service("prisonPersonMappingApiService")
class MappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<PrisonPersonMigrationMappingRequest>(domainUrl = "/mapping/prisonperson/migration", webClient)
