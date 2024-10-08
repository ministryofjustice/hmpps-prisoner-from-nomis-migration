package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.CreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.GetContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactPerson

@Service
class ContactPersonDpsApiService(@Qualifier("contactPersonApiWebClient") private val webClient: WebClient) {
  // TODO - use migrate endpoint when available
  suspend fun createPerson(person: CreateContactRequest): GetContactResponse = webClient.post()
    .uri("/contact")
    .bodyValue(person)
    .retrieve()
    .awaitBody()

  // essentially a mock speculative DPS API that just echo back the created entity with an DPS ID
  suspend fun migratePersonContact(person: ContactPerson): DpsContactPersonMapping = DpsContactPersonMapping(
    person = person.personId.toDpsToNomisId(),
    phoneNumbers = person.phoneNumbers.map { it.phoneId.toDpsToNomisId() },
    addresses = person.addresses.map { it.addressId.toDpsToNomisId() },
    emailAddresses = person.emailAddresses.map { it.emailAddressId.toDpsToNomisId() },
    employments = person.employments.map { person.personId.toDpsToNomisId(it.sequence) },
    identifiers = person.identifiers.map { person.personId.toDpsToNomisId(it.sequence) },
    contacts = person.contacts.map { it.id.toDpsToNomisId() },
    contactRestrictions = person.contacts.flatMap { it.restrictions.map { it.id.toDpsToNomisId() } },
    restrictions = person.restrictions.map { it.id.toDpsToNomisId() },
  )
}

fun Long.toDpsToNomisId() = DpsToNomisId(dpsId = (this * 10).toString(), nomisId = this)
fun Long.toDpsToNomisId(sequence: Long) = DpsToNomisIdWithSequence(dpsId = (this * 10).toString(), nomisId = this, nomisSequence = sequence)

// speculative DPS return model
data class DpsContactPersonMapping(
  val person: DpsToNomisId,
  val phoneNumbers: List<DpsToNomisId>,
  val addresses: List<DpsToNomisId>,
  val emailAddresses: List<DpsToNomisId>,
  val employments: List<DpsToNomisIdWithSequence>,
  val identifiers: List<DpsToNomisIdWithSequence>,
  val contacts: List<DpsToNomisId>,
  val contactRestrictions: List<DpsToNomisId>,
  val restrictions: List<DpsToNomisId>,
)

// speculative DPS return model
data class DpsToNomisId(val dpsId: String, val nomisId: Long)
data class DpsToNomisIdWithSequence(val dpsId: String, val nomisId: Long, val nomisSequence: Long)
