package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.api.SysconSyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonDisabilityStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonImmigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonImmigrationStatusResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonNationality
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonSexualOrientation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class CorePersonCprApiService(@Qualifier("corePersonApiWebClient") private val webClient: WebClient) {
  private val api = SysconSyncApi(webClient)

  suspend fun migrateCorePerson(prisonNumber: String, corePerson: Prisoner): String = api
    .prepare(api.createRequestConfig(prisonNumber, corePerson))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateSexualOrientation(prisonNumber: String, request: PrisonSexualOrientation): String = api
    .prepare(api.updateSexualOrientationRequestConfig(prisonNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateDisability(prisonNumber: String, request: PrisonDisabilityStatus): String = api
    .prepare(api.updateDisabilityStatusRequestConfig(prisonNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateImmigrationStatus(request: PrisonImmigrationStatus): PrisonImmigrationStatusResponse = api
    .prepare(api.createImmigrationStatusRequestConfig(request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateNationality(prisonNumber: String, request: PrisonNationality): String = api
    .prepare(api.saveNationalityRequestConfig(prisonNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateOffenderBelief(prisonNumber: String, religion: PrisonReligionRequest): String? = api
    .prepare(api.saveReligionsRequestConfig(prisonNumber, religion))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
