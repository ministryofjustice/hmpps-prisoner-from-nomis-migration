package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.api.NOMISMigrationSyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.NomisCsraReview
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.SyncResult
import java.util.UUID

@Service
class CsraDpsApiService(
  @Qualifier("csraApiWebClient") private val webClient: WebClient,
) {
  private val syncApi = NOMISMigrationSyncApi(webClient)

  suspend fun migratePrisoner(prisonerNumber: String, csra: List<NomisCsraReview>): List<CsraMigrationResponse> = syncApi
    .migrate(prisonerNumber, csra).awaitSingle()

  suspend fun sync(prisonerNumber: String, csra: CsraSyncRequest): SyncResult = syncApi
    .sync(prisonerNumber, csra).awaitSingle()

  suspend fun moveCsras(fromPrisonerNumber: String, toPrisonerNumber: String, csras: List<UUID>) {
    // syncApi.???
    webClient.put()
      .uri("/nomis-sync/move/from/{fromPrisonerNumber}/to/{toPrisonerNumber}", fromPrisonerNumber, toPrisonerNumber)
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(csras)
      .retrieve()
      .awaitBodilessEntity()
  }
}
