package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.api.MigrationApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.model.MigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import java.time.LocalDate

@Service
class DrugTestingDpsApiService(@Qualifier("drugTestingApiWebClient") private val webClient: WebClient) {
  private val api = MigrationApi(webClient)

  suspend fun migrate(prisonCode: String, rtpDate: LocalDate, migrationRequest: MigrationRequest) = api.prepare(
    api.migrateTestingListsRequestConfig(prisonCode = prisonCode, rtpDate = rtpDate, migrationRequest),
  )
    .retrieve()
    .awaitBodilessEntityOrLogAndRethrowBadRequest()
}
