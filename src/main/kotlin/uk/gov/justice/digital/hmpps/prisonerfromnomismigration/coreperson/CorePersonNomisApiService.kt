package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.CorePersonResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CreateOffenderEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CreateOffenderEmailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.UpdateOffenderEmailRequest

@Service
class CorePersonNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = CorePersonResourceApi(webClient)

  suspend fun getCorePerson(nomisPrisonNumber: String): CorePerson = api
    .getOffender(prisonNumber = nomisPrisonNumber)
    .awaitSingle()

  suspend fun getCorePersonAddressesAndContacts(nomisPrisonNumber: String): CorePersonAddressContact = api
    .getOffenderAddressesAndContactsByPrisonNumber(prisonNumber = nomisPrisonNumber)
    .awaitSingle()

  suspend fun getOffenderReligions(nomisPrisonNumber: String) = api.getOffenderReligionsByPrisonNumber(nomisPrisonNumber)
    .awaitSingle()

  suspend fun createOffenderEmail(offenderId: Long, request: CreateOffenderEmailRequest): CreateOffenderEmailResponse = api
    .createOffenderEmail(offenderId, request)
    .awaitSingle()

  suspend fun getOffenderEmail(offenderId: Long, emailAddressId: Long): OffenderEmailAddress = api
    .getOffenderEmail(offenderId, emailAddressId)
    .awaitSingle()

  suspend fun updateOffenderEmail(offenderId: Long, emailAddressId: Long, request: UpdateOffenderEmailRequest): Unit = api
    .updateOffenderEmail(offenderId, emailAddressId, request)
    .awaitSingle()

  suspend fun deleteOffenderEmail(offenderId: Long, emailAddressId: Long): Unit = api
    .deleteOffenderEmail(offenderId, emailAddressId)
    .awaitSingle()
}
