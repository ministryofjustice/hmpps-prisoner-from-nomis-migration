package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.api.SysconSyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressesAndContactsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonMerge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionSaveResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionUpdateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressesAndContactsResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconContactMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconReligionResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class CorePersonCprApiService(@Qualifier("corePersonApiWebClient") private val webClient: WebClient) {
  private val api = SysconSyncApi(webClient)

  suspend fun migrateCorePersonAddressesAndContacts(prisonNumber: String, request: PrisonAddressesAndContactsRequest): SysconAddressesAndContactsResponseBody = api
    .prepare(api.saveAddressesAndContactsRequestConfig(prisonNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun migrateCorePersonReligion(prisonNumber: String, request: PrisonReligionRequest): SysconReligionResponseBody = api
    .prepare(api.savePrisonerReligionsRequestConfig(prisonNumber, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCreateOffenderBelief(prisonNumber: String, religion: PrisonReligionHistory): PrisonReligionSaveResponse = api
    .prepare(api.savePrisonReligionRequestConfig(prisonNumber, religion))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncUpdateOffenderBelief(prisonNumber: String, cprReligionId: String, religion: PrisonReligionUpdateRequest): Unit = api
    .updatePrisonReligion(prisonNumber, cprReligionId, religion)
    .awaitSingle()

  suspend fun syncCreateContact(prisonNumber: String, contact: PrisonContact): SysconContactMapping = api
    .prepare(api.createPrisonerContactRequestConfig(prisonNumber, contact))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncUpdateContact(prisonNumber: String, cprContactId: String, contact: PrisonContact): Unit = api
    .updatePrisonerContact(prisonNumber, cprContactId, contact)
    .awaitSingle()

  suspend fun syncDeleteContact(prisonNumber: String, cprContactId: String): Unit = api
    .deletePrisonerContact(prisonNumber, cprContactId)
    .awaitSingle()

  suspend fun syncCreateAddress(prisonNumber: String, address: PrisonAddress): SysconAddressMapping = api
    .prepare(api.createPrisonerAddressRequestConfig(prisonNumber, address))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncUpdateAddress(prisonNumber: String, cprAddressId: String, address: PrisonAddress): Unit = api
    .updatePrisonerAddress(prisonNumber, cprAddressId, address)
    .awaitSingle()

  suspend fun syncDeleteAddress(prisonNumber: String, cprAddressId: String): Unit = api
    .deletePrisonerAddress(prisonNumber, cprAddressId)
    .awaitSingle()

  suspend fun processPrisonMerge(prisonNumber: String, prisonMerge: PrisonMerge): Unit = api
    .processPrisonMerge(prisonNumber, prisonMerge)
    .awaitSingle()
}
