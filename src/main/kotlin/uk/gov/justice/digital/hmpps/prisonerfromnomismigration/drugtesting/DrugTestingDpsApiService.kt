package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.api.MigrationApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.model.MigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.DuplicateDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.DuplicateError
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.SuccessOrDuplicate
import java.time.LocalDate

@Service
class DrugTestingDpsApiService(@Qualifier("drugTestingApiWebClient") private val webClient: WebClient) {
  private val api = MigrationApi(webClient)

  suspend fun migrate(prisonCode: String, rtpDate: LocalDate, migrationRequest: MigrationRequest): SuccessOrDuplicate<MigrateDrugTestDuplicate> = api.prepare(
    api.migrateTestingListsRequestConfig(prisonCode = prisonCode, rtpDate = rtpDate, migrationRequest),
  )
    .retrieve()
    .toBodilessEntity()
    .map { SuccessOrDuplicate<MigrateDrugTestDuplicate>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(SuccessOrDuplicate(DuplicateError(DuplicateDetails(duplicate = MigrateDrugTestDuplicate(prisonCode, rtpDate), existing = null))))
    }
    .awaitSingle()
}

data class MigrateDrugTestDuplicate(val prisonCode: String, val rtpDate: LocalDate)
