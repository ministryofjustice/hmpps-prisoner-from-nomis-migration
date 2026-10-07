package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.CorePersonResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddressUsage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPhoneNumber

@Service
class CorePersonNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = CorePersonResourceApi(webClient)

  suspend fun getCorePerson(nomisPrisonNumber: String): CorePerson = api
    .getOffender(prisonNumber = nomisPrisonNumber)
    .awaitSingle()

  suspend fun getCorePersonAddressesAndContacts(nomisPrisonNumber: String): CorePersonAddressContact = api
    .getOffenderAddressesAndContactsByPrisonNumber(prisonNumber = nomisPrisonNumber)
    .awaitSingle()

  suspend fun getOffenderAddress(offenderId: Long, addressId: Long): OffenderAddress = api
    .getOffenderAddress(offenderId, addressId)
    .awaitSingle()

  suspend fun getOffenderAddressUsage(offenderId: Long, addressId: Long, usageCode: String): OffenderAddressUsage = api
    .getOffenderAddressUsage(offenderId, addressId, usageCode)
    .awaitSingle()

  suspend fun getOffenderPhone(offenderId: Long, phoneId: Long): OffenderPhoneNumber = api
    .getOffenderPhone(offenderId, phoneId)
    .awaitSingle()

  suspend fun getOffenderAddressPhone(offenderId: Long, addressId: Long, phoneId: Long): OffenderPhoneNumber = api
    .getOffenderAddressPhone(offenderId, addressId, phoneId)
    .awaitSingle()

  suspend fun getOffenderReligions(nomisPrisonNumber: String) = api.getOffenderReligionsByPrisonNumber(nomisPrisonNumber)
    .awaitSingle()

  suspend fun getOffenderEmail(offenderId: Long, emailAddressId: Long): OffenderEmailAddress = api
    .getOffenderEmail(offenderId, emailAddressId)
    .awaitSingle()
}
