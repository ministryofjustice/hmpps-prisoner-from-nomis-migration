package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.SuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.ReligionResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMigrationMappingDto

@Service
class ReligionsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<ReligionsMigrationMappingDto>("/mapping/core-person-religion", webClient) {
  private val api = ReligionResourceApi(webClient)

  suspend fun getReligionsByPrisonNumberOrNull(prisonNumber: String): ReligionsMappingDto? = api
    .prepare(api.getReligionsMappingByNomisPrisonNumberRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getReligionByNomisId(nomisReligionId: Long): ReligionMappingDto = api
    .getReligionMappingByNomisId(nomisId = nomisReligionId)
    .awaitSingle()

  suspend fun getReligionByNomisIdOrNull(nomisReligionId: Long): ReligionMappingDto? = api
    .prepare(api.getReligionMappingByNomisIdRequestConfig(nomisId = nomisReligionId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteReligionByNomisId(nomisReligionId: Long) {
    api.deleteReligionMappingByNomisId(nomisId = nomisReligionId).awaitSingle()
  }
  suspend fun createReligionMapping(mapping: ReligionMappingDto): SuccessOrDuplicate<ReligionMappingDto> = api
    .prepare(api.createReligionMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate()

  suspend fun replaceMappings(mappings: ReligionsMigrationMappingDto) {
    api.replaceMappings(mappings)
      .awaitSingle()
  }
}
