package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigratePrisonerDomesticStatusRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigratePrisonerNumberOfChildrenRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.PrisonerDomesticStatusMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.PrisonerNumberOfChildrenMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerDomesticStatusResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerNumberOfChildrenResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerDomesticStatusRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerNumberOfChildrenRequest

@Service
class ContactPersonProfileDetailsDpsApiService(@Qualifier("personalRelationshipsApiWebClient") private val webClient: WebClient) {
  suspend fun syncDomesticStatus(
    prisonerNumber: String,
    request: SyncUpdatePrisonerDomesticStatusRequest,
  ): SyncPrisonerDomesticStatusResponse = webClient
    .put()
    .uri("/sync/{prisonerNumber}/domestic-status", prisonerNumber)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun syncNumberOfChildren(
    prisonerNumber: String,
    request: SyncUpdatePrisonerNumberOfChildrenRequest,
  ): SyncPrisonerNumberOfChildrenResponse = webClient
    .put()
    .uri("/sync/{prisonerNumber}/number-of-children", prisonerNumber)
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun migrateDomesticStatus(
    request: MigratePrisonerDomesticStatusRequest,
  ): PrisonerDomesticStatusMigrationResponse = webClient
    .post()
    .uri("/migrate/domestic-status")
    .bodyValue(request)
    .retrieve()
    .awaitBody()

  suspend fun migrateNumberOfChildren(
    request: MigratePrisonerNumberOfChildrenRequest,
  ): PrisonerNumberOfChildrenMigrationResponse = webClient
    .post()
    .uri("/migrate/number-of-children")
    .bodyValue(request)
    .retrieve()
    .awaitBody()
}
