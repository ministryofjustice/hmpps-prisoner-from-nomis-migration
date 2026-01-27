package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Address
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.AddressUsage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.AddressUsage.AddressUsageCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Alias
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Contact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.DemographicAttributes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Name
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Sentence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPhoneNumber

private val allowedIdentifiers: Set<String> = Identifier.Type.entries.map { it.value }.toSet()

fun CorePerson.toCprPrisoner(): Prisoner {
  val currentAlias = offenders?.first { it.workingName }!!
  return Prisoner(
    name = currentAlias.toCprName(),
    demographicAttributes = toDemographicAttributes(currentAlias),
    addresses = addresses?.map { it.toCprAddress() } ?: emptyList(),
    contacts = (phoneNumbers?.map { it.toCprContact() } ?: emptyList()) +
      (emailAddresses?.map { it.toCprContact() } ?: emptyList()) +
      (addresses?.flatMap { it.toCprContact() ?: emptyList() } ?: emptyList()),
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
  middleNames = listOfNotNull(middleName1, middleName2).takeIf { it.isNotEmpty() }?.joinToString(" "),
  lastName = lastName,
)

private fun CoreOffender.toCprAlias() = Alias(
  titleCode = title?.code,
  firstName = firstName,
  middleNames = listOfNotNull(middleName1, middleName2).takeIf { it.isNotEmpty() }?.joinToString(" "),
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
  sexualOrientation = sexualOrientations?.firstOrNull()?.sexualOrientation?.code,
  disability = disabilities?.firstOrNull()?.disability,
  religionCode = this.beliefs?.firstOrNull()?.belief?.code,
  nationalityCode = nationalities?.firstOrNull()?.nationality?.code,
  nationalityNote = nationalityDetails?.firstOrNull()?.details,
  interestToImmigration = interestsToImmigration?.firstOrNull()?.interestToImmigration,
)

private fun OffenderAddress.toCprAddress() = Address(
  fullAddress = buildFullAddress(),
  noFixedAbode = noFixedAddress,
  startDate = startDate,
  endDate = endDate?.toString(),
  postcode = postcode,
  subBuildingName = flat,
  buildingName = premise,
  buildingNumber = null,
  thoroughfareName = street,
  dependentLocality = locality,
  postTown = city?.code,
  county = county?.description,
  countryCode = country?.code,
  comment = comment,
  isPrimary = primaryAddress,
  isMail = mailAddress,
  addressUsage = this.usages?.map { AddressUsage(AddressUsageCode.valueOf(it.usage.code), it.active) } ?: emptyList(),
)

private fun MutableList<String>.addIfNotEmpty(value: String?) {
  if (!value.isNullOrBlank()) add(value.trim())
}

fun OffenderAddress.buildFullAddress(): String {
  // Code duplicated from Prisoner Search Indexer
  if (noFixedAddress == true) return "No fixed address"

  val address = mutableListOf<String>()

  // Append "Flat" if there is one
  if (!flat.isNullOrBlank()) {
    address.add("Flat ${flat.trim()}")
  }
  // Don't separate a numeric premise from the street, only if it's a name
  val hasPremise = !premise.isNullOrBlank()
  val premiseIsNumber = premise?.all { it.isDigit() } ?: false
  val hasStreet = !street.isNullOrBlank()
  when {
    hasPremise && premiseIsNumber && hasStreet -> address.add("$premise $street")
    hasPremise && !premiseIsNumber && hasStreet -> address.add("$premise, $street")
    hasPremise -> address.add(premise)
    hasStreet -> address.add(street)
  }
  // Add others if they exist
  address.addIfNotEmpty(locality)
  address.addIfNotEmpty(city?.description)
  address.addIfNotEmpty(county?.description)
  address.addIfNotEmpty(postcode)
  address.addIfNotEmpty(country?.description)
  return address.joinToString(", ")
}

fun OffenderPhoneNumber.toCprContact(fromAddress: Boolean = false) = Contact(
  isPersonContact = fromAddress.not(),
  isAddressContact = fromAddress,
  value = number,
  type = when (type.code) {
    "HOME" -> Contact.Type.HOME
    "MOB" -> Contact.Type.MOBILE
    // TODO Not all codes are catered for here
    else -> Contact.Type.HOME
  },
  extension = extension,
)

fun OffenderEmailAddress.toCprContact() = Contact(
  isPersonContact = true,
  isAddressContact = false,
  value = email,
  type = Contact.Type.EMAIL,
)

fun OffenderAddress.toCprContact(): List<Contact>? = this.phoneNumbers?.map { it.toCprContact(fromAddress = true) }

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
