package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.api.NOMISMigrationSyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.NomisCsraReview

@Service
class CsraDpsApiService(
  @Qualifier("csraApiWebClient") private val webClient: WebClient,
) {
  private val syncApi = NOMISMigrationSyncApi(webClient)

  suspend fun migratePrisoner(prisonerNumber: String, csra: List<NomisCsraReview>): List<CsraMigrationResponse> = syncApi
    .migrate(prisonerNumber, csra).awaitSingle()
}
