package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.api.SysconSyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressesAndContactsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAliasesAndIdentifiersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonDisabilityStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonImmigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonMerge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonNationality
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionSaveResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionUpdateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonSexualOrientation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressesAndContactsResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAliasesAndIdentifiersResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconContactMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconReligionResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class CorePersonCprApiService(@Qualifier("corePersonApiWebClient") private val webClient: WebClient) {
  private val api = SysconSyncApi(webClient)

  suspend fun migrateCorePersonAliasesAndIdentifiers(prisonNumber: String, request: PrisonAliasesAndIdentifiersRequest): SysconAliasesAndIdentifiersResponseBody = api
    .prepare(api.saveAliasesAndIdentifiersRequestConfig(prisonNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun migrateCorePersonAddressesAndContacts(prisonNumber: String, request: PrisonAddressesAndContactsRequest): SysconAddressesAndContactsResponseBody = api
    .prepare(api.saveAddressesAndContactsRequestConfig(prisonNumber, request))
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

  suspend fun syncCreateOffenderBelief(prisonNumber: String, religion: PrisonReligionHistory): PrisonReligionSaveResponse = api
    .prepare(api.savePrisonReligionRequestConfig(prisonNumber, religion))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncUpdateOffenderBelief(prisonNumber: String, cprReligionId: String, religion: PrisonReligionUpdateRequest): Unit = api
    .updatePrisonReligion(prisonNumber, cprReligionId, religion)
    .awaitSingle()

  suspend fun syncCreateEmail(prisonNumber: String, email: PrisonContact): SysconContactMapping = api
    .prepare(api.createPrisonerContactRequestConfig(prisonNumber, email))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncUpdateEmail(prisonNumber: String, cprContactId: String, email: PrisonContact): Unit = api
    .updatePrisonerContact(prisonNumber, cprContactId, email)
    .awaitSingle()

  suspend fun syncDeleteEmail(prisonNumber: String, cprContactId: String): Unit = api
    .deletePrisonerContact(prisonNumber, cprContactId)
    .awaitSingle()

  suspend fun processPrisonMerge(prisonNumber: String, prisonMerge: PrisonMerge): Unit = api
    .processPrisonMerge(prisonNumber, prisonMerge)
    .awaitSingle()
}
