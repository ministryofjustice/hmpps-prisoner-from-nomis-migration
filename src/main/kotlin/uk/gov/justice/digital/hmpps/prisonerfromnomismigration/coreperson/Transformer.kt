package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Address
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Email
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Names
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Religion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Identifier as CprIdentifier

fun CorePerson.toCprPrisoner() = Prisoner(
  religion = beliefs?.map { it.toCprReligion() } ?: emptyList(),
  sentenceStartDates = sentenceStartDates ?: emptyList(),
  phoneNumbers = phoneNumbers?.map { it.toCprPhoneNumber() } ?: emptyList(),
  emails = emailAddresses?.map { Email(it.emailAddressId, it.email) } ?: emptyList(),
  offenders = offenders?.map { it.toCprNames() } ?: emptyList(),
  addresses = addresses?.map { it.toCprAddress() } ?: emptyList(),
  nationality = nationalities?.find { it.latestBooking }?.nationality?.code,
  secondaryNationality = nationalityDetails?.find { it.latestBooking }?.details,
  sexualOrientation = sexualOrientations?.find { it.latestBooking }?.sexualOrientation?.code,
  interestToImmigration = interestsToImmigration?.find { it.latestBooking }?.interestToImmigration,
  disability = disabilities?.find { it.latestBooking }?.disability,
  // TODO Check this - and why is this nullable?
  status = if (activeFlag) "ACTIVE" else "INACTIVE",
)

fun CoreOffender.toCprNames() = Names(
  // TODO - this is a long in the model
  offenderId = offenderId.toString(),
  title = title?.code,
  firstName = firstName,
  middleName1 = middleName1,
  middleName2 = middleName2,
  lastName = lastName,
  // TODO we should really just pass in the code (like we do for others)
  nameType = nameType?.toCprNameType(),
  dateOfBirth = dateOfBirth,
  birthPlace = birthPlace,
  birthCountry = birthCountry?.code,
  raceCode = ethnicity?.code,
  // TODO we should really just pass in the code (like we do for others)
  sex = sex?.toCprSexType(),
  workingName = workingName,
  created = createDate,
  identifiers = identifiers.map { it.toCprIdentifier() },
)

fun Identifier.toCprIdentifier() = CprIdentifier(
  type = type.code,
  // TODO cpr value is nullable but nomis identifier is not - CPR to update to non-nullable
  value = identifier,
)

fun OffenderAddress.toCprAddress() = Address(
  id = addressId,
  // TODO Check - type is always null in NOMIS - not required as part of Cpr Address?
  type = null,
  flat = flat,
  premise = premise,
  street = street,
  locality = locality,
  town = city?.code,
  postcode = postcode,
  county = county?.code,
  country = country?.code,
  // TODO this is a boolean - should the CprAddress noFixedAddress field also be a boolean?
  noFixedAddress = noFixedAddress?.toString(),
  startDate = startDate,
  endDate = endDate,
  comment = comment,
  isPrimary = primaryAddress,
  // TODO check - this is non-nullable in NOMIS but nullable in CPR
  mail = mailAddress,
)

fun OffenderPhoneNumber.toCprPhoneNumber() = PhoneNumber(
  phoneId = phoneId,
  phoneNumber = number,
  // TODO check this should be a code
  phoneType = type.toCprPhoneType(),
  phoneExtension = extension,
)

fun OffenderBelief.toCprReligion() = Religion(
  religion = belief.code,
  startDate = startDate,
  endDate = endDate,
  // TODO check this- should this be boolean? ask CPR- derived from if end date null -> ACTIVE or INACTIVE and should also be non-nullable
  status = endDate?. let { "INACTIVE" } ?: "ACTIVE",
  // TODO Check Created and Updated should be displayNames
  createdUserId = audit.createUsername,
  updatedUserId = audit.modifyUserId,
)

fun CodeDescription.toCprPhoneType() = when (this.code) {
  "HOME" -> PhoneNumber.PhoneType.HOME
  "MOB" -> PhoneNumber.PhoneType.MOBILE
  "BUS" -> PhoneNumber.PhoneType.BUSINESS
  // TODO Not all codes are catered for here
  "FAX" -> PhoneNumber.PhoneType.HOME
  "ALTB" -> PhoneNumber.PhoneType.HOME
  "ALTH" -> PhoneNumber.PhoneType.HOME
  "VISIT" -> PhoneNumber.PhoneType.HOME
  else -> throw IllegalArgumentException("Invalid phone Type ${this.code} found in NOMIS data")
}

fun CodeDescription.toCprSexType() = when (this.code) {
  "F" -> Names.Sex.FEMALE
  "M" -> Names.Sex.MALE
  "NK" -> Names.Sex.NOT_KNOWN
  "NS" -> Names.Sex.NOT_SPECIFIED
  "REF" -> Names.Sex.REFUSED
  else -> throw IllegalArgumentException("Invalid Sex Type ${this.code} found in NOMIS data")
}

fun CodeDescription.toCprNameType() = when (this.code) {
  "A" -> Names.NameType.ALIAS
  "CN" -> Names.NameType.CURRENT
  "MAID" -> Names.NameType.MAIDEN
  "NICK" -> Names.NameType.NICKNAME
  else -> throw IllegalArgumentException("Invalid Name Type ${this.code} found in NOMIS data")
}
