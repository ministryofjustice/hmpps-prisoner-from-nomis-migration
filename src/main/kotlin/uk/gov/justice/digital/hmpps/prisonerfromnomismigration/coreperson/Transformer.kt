package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Address
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Alias
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.ContactInfo
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.DemographicAttributes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Name
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Sentence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress

private val allowedIdentifiers: Set<String> = Identifier.Type.entries.map { it.value }.toSet()

fun CorePerson.toCprPrisoner(): Prisoner {
  val currentAlias = offenders?.first { it.workingName }!!
  return Prisoner(
    name = currentAlias.toCprName(),
    demographicAttributes = this.toDemographicAttributes(currentAlias),
    addresses = addresses?.map { it.toCprAddress() } ?: emptyList(),
    // TODO: religion has disappeared
    religions = listOf(),
    // TODO: remap in new structure when we get it
    contactInfo = ContactInfo(
      phoneNumbers = emptyList(), // phoneNumbers?.map { it.toCprPhoneNumber() } ?: emptyList(),
      emails = emptyList(), // emailAddresses?.map { Email(it.emailAddressId, it.email) } ?: emptyList(),
    ),
    aliases = offenders.filterNot { it.workingName }.map { it.toCprAlias() },
    identifiers = offenders.flatMap {
      it.identifiers
        // TOOD: confirm only 4 identifier types required
        .filter { i -> allowedIdentifiers.contains(i.type.code) }
        .map { i -> Identifier(type = Identifier.Type.valueOf(i.type.code), value = i.identifier) }
    },
    sentences = sentenceStartDates?.map { Sentence(it) } ?: emptyList(),
  )
}

private fun CoreOffender.toCprName() = Name(
  titleCode = title?.code,
  firstName = firstName,
  middleNames = listOfNotNull(middleName1, middleName2).joinToString(" "),
  lastName = lastName,
)

private fun CoreOffender.toCprAlias() = Alias(
  titleCode = title?.code,
  firstName = firstName,
  middleNames = listOfNotNull(middleName1, middleName2).joinToString(" "),
  lastName = lastName,
  dateOfBirth = dateOfBirth,
  sexCode = sex?.code,
)

private fun CorePerson.toDemographicAttributes(currentAlias: CoreOffender): DemographicAttributes = DemographicAttributes(
  dateOfBirth = currentAlias.dateOfBirth,
  birthPlace = currentAlias.birthPlace,
  birthCountryCode = currentAlias.birthCountry?.code,
  ethnicityCode = currentAlias.ethnicity?.code,
  sexCode = currentAlias.sex?.code,
)

private fun OffenderAddress.toCprAddress() = Address(
  // TODO Check - type is always null in NOMIS - not required as part of Cpr Address?
  type = Address.Type.HOME,
  isPrimary = primaryAddress,
  isMail = mailAddress,
  // TODO: hard coded
  isActive = true,
  // TODO: remove
  id = addressId.toString(),
  flat = flat,
  premise = premise,
  street = street,
  locality = locality,
  townCode = city?.code,
  postcode = postcode,
  countyCode = county?.code,
  countryCode = country?.code,
  // TODO this is a boolean - should the CprAddress noFixedAddress field also be a boolean?
  noFixedAddress = noFixedAddress,
  startDate = startDate,
  endDate = endDate.toString(),
  comment = comment,
)
//
// fun OffenderPhoneNumber.toCprPhoneNumber() = PhoneNumber(
//  phoneId = phoneId,
//  phoneNumber = number,
//  // TODO check this should be a code
//  phoneType = type.toCprPhoneType(),
//  phoneExtension = extension,
// )
//
// fun OffenderBelief.toCprReligion() = Religion(
//  religion = belief.code,
//  startDate = startDate,
//  endDate = endDate,
//  // TODO check this- should this be boolean? ask CPR- derived from if end date null -> ACTIVE or INACTIVE and should also be non-nullable
//  status = endDate?.let { "INACTIVE" } ?: "ACTIVE",
//  // TODO Check Created and Updated should be displayNames
//  createdUserId = audit.createUsername,
//  updatedUserId = audit.modifyUserId,
// )
//
// fun CodeDescription.toCprPhoneType() = when (this.code) {
//  "HOME" -> PhoneNumber.PhoneType.HOME
//  "MOB" -> PhoneNumber.PhoneType.MOBILE
//  "BUS" -> PhoneNumber.PhoneType.BUSINESS
//  // TODO Not all codes are catered for here
//  "FAX" -> PhoneNumber.PhoneType.HOME
//  "ALTB" -> PhoneNumber.PhoneType.HOME
//  "ALTH" -> PhoneNumber.PhoneType.HOME
//  "VISIT" -> PhoneNumber.PhoneType.HOME
//  else -> throw IllegalArgumentException("Invalid phone Type ${this.code} found in NOMIS data")
// }
//
// fun CodeDescription.toCprSexType() = when (this.code) {
//  "F" -> Names.Sex.FEMALE
//  "M" -> Names.Sex.MALE
//  "NK" -> Names.Sex.NOT_KNOWN
//  "NS" -> Names.Sex.NOT_SPECIFIED
//  "REF" -> Names.Sex.REFUSED
//  else -> throw IllegalArgumentException("Invalid Sex Type ${this.code} found in NOMIS data")
// }
//
// fun CodeDescription.toCprNameType() = when (this.code) {
//  "A" -> Names.NameType.ALIAS
//  "CN" -> Names.NameType.CURRENT
//  "MAID" -> Names.NameType.MAIDEN
//  "NICK" -> Names.NameType.NICKNAME
//  else -> throw IllegalArgumentException("Invalid Name Type ${this.code} found in NOMIS data")
// }
