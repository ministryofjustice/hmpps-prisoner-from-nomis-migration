package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Address
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Address.CountryCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.AddressUsage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.AddressUsage.AddressUsageCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Alias
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Alias.TitleCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Contact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.DemographicAttributes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.DemographicAttributes.BirthCountryCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.DemographicAttributes.EthnicityCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.DemographicAttributes.NationalityCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.DemographicAttributes.SexualOrientation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Identifier
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
    demographicAttributes = toDemographicAttributes(currentAlias),
    addresses = addresses?.map { it.toCprAddress() } ?: emptyList(),
    personContacts = (phoneNumbers?.map { it.toCprContact() } ?: emptyList()) +
      (emailAddresses?.map { it.toCprContact() } ?: emptyList()) +
      (addresses?.flatMap { it.toCprContact() ?: emptyList() } ?: emptyList()),
    pseudonyms = offenders.filterNot { it.workingName }.map { it.toCprAlias() },
    sentences = sentenceStartDates?.map { Sentence(it) } ?: emptyList(),
  )
}

private fun CoreOffender.toCprAlias() = Alias(
  nomisAliasId = offenderId,
  isPrimary = workingName,
  titleCode = title?.code?.toTitleCode(),
  firstName = firstName,
  middleNames = listOfNotNull(middleName1, middleName2).takeIf { it.isNotEmpty() }?.joinToString(" "),
  lastName = lastName,
  dateOfBirth = dateOfBirth,
  sexCode = sex?.code?.let { Alias.SexCode.valueOf(it) },
  identifiers = identifiers
    // TODO: confirm only 4 identifier types required
    .filter { i -> allowedIdentifiers.contains(i.type.code) }
    .map { i ->
      Identifier(
        type = Identifier.Type.valueOf(i.type.code),
        value = i.identifier,
        nomisIdentifierId = i.sequence,
        comment = i.issuedAuthority,
      )
    },
)

private fun CorePerson.toDemographicAttributes(currentAlias: CoreOffender): DemographicAttributes = DemographicAttributes(
  birthPlace = currentAlias.birthPlace,
  birthCountryCode = currentAlias.birthCountry?.code?.toBirthCountryCode(),
  ethnicityCode = currentAlias.ethnicity?.code?.toEthnicityCode(),
  sexCode = currentAlias.sex?.code?.let { DemographicAttributes.SexCode.valueOf(it) },
  sexualOrientation = sexualOrientations?.firstOrNull()?.sexualOrientation?.code?.toSexualOrientation(),
  disability = disabilities?.firstOrNull()?.disability,
  religionCode = this.beliefs?.firstOrNull()?.belief?.code,
  nationalityCode = nationalities?.firstOrNull()?.nationality?.code?.toNationalityCode(),
  nationalityNote = nationalityDetails?.firstOrNull()?.details,
  interestToImmigration = interestsToImmigration?.firstOrNull()?.interestToImmigration,
)

private fun String.toNationalityCode(): NationalityCode = NationalityCode.valueOf(this)
private fun String.toSexualOrientation(): SexualOrientation = SexualOrientation.valueOf(this)

private fun String.toBirthCountryCode(): BirthCountryCode = when (this) {
  "IOM" -> BirthCountryCode.IMN
  "ROM" -> BirthCountryCode.ROU
  else -> BirthCountryCode.valueOf(this)
}

private fun String.toCountryCode(): CountryCode = when (this) {
  "IOM" -> CountryCode.IMN
  "ROM" -> CountryCode.ROU
  else -> CountryCode.valueOf(this)
}

private fun String.toEthnicityCode(): EthnicityCode = when (this) {
  "W10" -> EthnicityCode.W5
  else -> EthnicityCode.valueOf(this)
}

private fun String.toTitleCode(): TitleCode = when (this) {
  "DAME" -> TitleCode.DME
  "LADY" -> TitleCode.LDY
  "LORD" -> TitleCode.LRD
  else -> TitleCode.valueOf(this)
}

private fun OffenderAddress.toCprAddress() = Address(
  nomisAddressId = addressId,
  fullAddress = buildFullAddress(),
  noFixedAbode = noFixedAddress,
  startDate = startDate,
  endDate = endDate,
  postcode = postcode,
  subBuildingName = flat,
  buildingName = premise,
  buildingNumber = null,
  thoroughfareName = street,
  dependentLocality = locality,
  postTown = city?.code,
  county = county?.description,
  countryCode = country?.code?.toCountryCode(),
  comment = comment,
  isPrimary = primaryAddress,
  isMail = mailAddress,
  addressUsage = usages?.map { AddressUsage(addressId, AddressUsageCode.valueOf(it.usage.code), it.active) } ?: emptyList(),
  contacts = this.toCprContact() ?: emptyList(),
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

fun OffenderPhoneNumber.toCprContact() = Contact(
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
  value = email,
  type = Contact.Type.EMAIL,
)

fun OffenderAddress.toCprContact(): List<Contact>? = this.phoneNumbers?.map { it.toCprContact() }

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
