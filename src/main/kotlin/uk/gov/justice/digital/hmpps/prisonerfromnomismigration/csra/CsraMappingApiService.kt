package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CsraMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingDto

@Service
class CsraMappingApiService(
  @Qualifier("mappingApiWebClient") webClient: WebClient,
) : MigrationMapping<CsraMappingDto>("/mapping/csras", webClient) {
  private val api = CsraMappingResourceApi(webClient)

  // TODO
}
