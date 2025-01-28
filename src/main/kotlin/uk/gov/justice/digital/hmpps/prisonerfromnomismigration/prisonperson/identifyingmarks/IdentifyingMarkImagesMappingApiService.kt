package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.identifyingmarks

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IdentifyingMarkImageMappingDto
import java.util.UUID

@Service
class IdentifyingMarkImagesMappingApiService(@Qualifier("mappingApiWebClient") private val webClient: WebClient) {
  suspend fun getByNomisId(offenderImageId: Long): IdentifyingMarkImageMappingDto? = webClient.get()
    .uri("/mapping/prisonperson/nomis-offender-image-id/{nomisImageId}", offenderImageId)
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getByDpsId(dpsId: UUID): IdentifyingMarkImageMappingDto? = webClient.get()
    .uri("/mapping/prisonperson/dps-image-id/{dpsId}", dpsId)
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun createMapping(mapping: IdentifyingMarkImageMappingDto) {
    webClient.post()
      .uri("/mapping/prisonperson/identifying-mark-image")
      .bodyValue(mapping)
      .retrieve()
      .awaitBodilessEntity()
  }
}
