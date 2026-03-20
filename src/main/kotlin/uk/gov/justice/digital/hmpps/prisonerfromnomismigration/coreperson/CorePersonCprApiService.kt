package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.api.HMPPSPersonAPIApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.api.SysconSyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonDisabilityStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonImmigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonNationality
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionUpdateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonSexualOrientation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconReligionResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class CorePersonCprApiService(@Qualifier("corePersonApiWebClient") private val webClient: WebClient) {
  private val api = SysconSyncApi(webClient)
  private val personApi = HMPPSPersonAPIApi(webClient)

  suspend fun migrateCorePerson(prisonNumber: String, corePerson: Prisoner): String = api
    .prepare(api.updateRequestConfig(prisonNumber, corePerson))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun migrateCorePersonReligion(prisonNumber: String, request: PrisonReligionRequest): SysconReligionResponseBody = api
    .prepare(api.savePrisonerReligionsRequestConfig(prisonNumber, request))
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

  suspend fun syncCreateImmigrationStatus(prisonNumber: String, request: PrisonImmigrationStatus): String = api
    .prepare(api.updateImmigrationStatusRequestConfig(prisonNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateNationality(prisonNumber: String, request: PrisonNationality): String = api
    .prepare(api.saveNationalityRequestConfig(prisonNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateOffenderBelief(prisonNumber: String, religion: PrisonReligion): PrisonReligionResponse = personApi
    .prepare(personApi.savePrisonReligionRequestConfig(prisonNumber, religion))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncUpdateOffenderBelief(prisonNumber: String, cprReligionId: String, religion: PrisonReligionUpdateRequest): PrisonReligionResponse = personApi
    .prepare(personApi.updatePrisonReligionRequestConfig(prisonNumber, cprReligionId, religion))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
