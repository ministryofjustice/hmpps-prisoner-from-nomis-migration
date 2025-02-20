package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.time.LocalDateTime

@Service
class ContactPersonProfileDetailsDpsApiService(@Qualifier("personalRelationshipsApiWebClient") private val webClient: WebClient) {
  suspend fun syncDomesticStatus(
    prisonerNumber: String,
    request: DomesticStatusSyncRequest,
  ): DomesticStatusSyncResponse = webClient
    .put()
    .uri("/sync/domestic-status/{prisonerNumber}", prisonerNumber)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun syncDependants(
    prisonerNumber: String,
    request: DependantsSyncRequest,
  ): DependantsSyncResponse = webClient
    .put()
    .uri("/sync/dependants/{prisonerNumber}", prisonerNumber)
    .bodyValue(request)
    .retrieve()
    .awaitBody()
}

// TODO SDIT-2573 All models to be replaced by models generated from OpenAPI spec when available
data class DomesticStatusSyncRequest(
  val domesticStatusCode: String?,
  val createdBy: String,
  val createdDateTime: LocalDateTime,
  val latestBooking: Boolean,
)

data class DomesticStatusSyncResponse(val domesticStatusId: Long)

data class DependantsSyncRequest(
  val dependants: String,
  val createdBy: String,
  val createdDateTime: LocalDateTime,
)

data class DependantsSyncResponse(val dependantsId: Long)
