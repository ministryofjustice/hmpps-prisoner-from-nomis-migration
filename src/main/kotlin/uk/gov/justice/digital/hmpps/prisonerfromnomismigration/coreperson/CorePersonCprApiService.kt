package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.api.SysconSyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonDisabilityStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonDisabilityStatusResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonImmigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonImmigrationStatusResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonNationality
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonNationalityResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonSexualOrientation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonSexualOrientationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class CorePersonCprApiService(@Qualifier("corePersonApiWebClient") private val webClient: WebClient) {
  private val api = SysconSyncApi(webClient)

  suspend fun migrateCorePerson(prisonNumber: String, corePerson: Prisoner): String = api
    .prepare(api.createRequestConfig(prisonNumber, corePerson))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateSexualOrientation(request: PrisonSexualOrientation): PrisonSexualOrientationResponse = api
    .prepare(api.createSexualOrientationRequestConfig(request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateDisability(request: PrisonDisabilityStatus): PrisonDisabilityStatusResponse = api
    .prepare(api.insertDisabilityStatusRequestConfig(request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateImmigrationStatus(request: PrisonImmigrationStatus): PrisonImmigrationStatusResponse = api
    .prepare(api.createImmigrationStatusRequestConfig(request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateNationality(request: PrisonNationality): PrisonNationalityResponse = api
    .prepare(api.createNationalityRequestConfig(request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateOffenderBelief(religion: PrisonReligion): PrisonReligionResponse = api
    .prepare(api.createReligionRequestConfig(religion))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
