package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.identifyingmarks

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IdentifyingMarkMappingDto
import java.util.UUID

@Service
class IdentifyingMarksMappingApiService(@Qualifier("mappingApiWebClient") private val webClient: WebClient) {
  suspend fun getByNomisId(bookingId: Long, idMarksSeq: Long): IdentifyingMarkMappingDto? =
    webClient.get()
      .uri("/mapping/prisonperson/nomis-booking-id/{bookingId}/identifying-mark-sequence/{sequence}", bookingId, idMarksSeq)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun getByDpsId(dpsId: UUID): IdentifyingMarkMappingDto? =
    webClient.get()
      .uri("/mapping/prisonperson/dps-identifying-mark-id/{dpsId}", dpsId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun createMapping(mapping: IdentifyingMarkMappingDto) {
    webClient.post()
      .uri("/mapping/prisonperson/identifying-mark")
      .bodyValue(mapping)
      .retrieve()
      .awaitBodilessEntity()
  }
}
