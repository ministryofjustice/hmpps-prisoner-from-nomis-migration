package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.SuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CsraMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingDto

@Service
class CsraMappingService(
  @Qualifier("mappingApiWebClient") webClient: WebClient,
) : MigrationMapping<CsraMigrationMapping>("/mapping/csras", webClient) {
  private val api = CsraMappingResourceApi(webClient)

  suspend fun createMapping(dto: CsraMigrationMapping): SuccessOrDuplicate<CsraMappingDto> = api
    .prepare(api.createMappingsForPrisoner1RequestConfig(dto.offenderNo, dto.prisonerMappings))
    .retrieve()
    .awaitSuccessOrDuplicate()
}
