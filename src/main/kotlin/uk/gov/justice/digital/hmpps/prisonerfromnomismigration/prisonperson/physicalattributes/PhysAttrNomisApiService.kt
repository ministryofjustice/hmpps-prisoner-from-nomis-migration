package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse

@Service
class PhysAttrNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getPhysicalAttributes(offenderNo: String): PrisonerPhysicalAttributesResponse = webClient.get()
    .uri("/prisoners/{offenderNo}/physical-attributes", offenderNo)
    .retrieve()
    .awaitBody()
}
