package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyAddressDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyEmailDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyPhoneDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import java.util.Locale

fun AgencyResponse.toLegacyAgencyDto() = LegacyAgencyDto(
  agencyType = type.toLegacyAgencyType(),
  name = description,
  active = active,
  addresses = addresses.map { it.toLegacyAgencyAddressDto() },
  emailAddresses = emailAddresses.map { it.toLegacyAgencyEmailDto() },
  phoneNumbers = (phones.map { it.toLegacyAgencyPhoneDto() } + addresses.flatMap { it.phoneNumbers.map { phone -> phone.toLegacyAgencyPhoneDto() } })
    .distinctBy { it.number },
  description = longDescription,
  inactiveDate = deactivationDate,
  cjitCode = cjitCode,
  areaCode = area?.code,
  subareaCode = subArea?.code,
  regionCode = nomsRegion?.code,
  geographicalAreaCode = region?.code,
  payrollRegionCode = payrollRegion?.code,
  courtTypeCode = courtType?.code,
  accessibleAccess = when (disabilityAccessCode) {
    "WHEEL" -> LegacyAgencyDto.AccessibleAccess.WHEELCHAIR_ACCESS
    "Y", "Yes" -> LegacyAgencyDto.AccessibleAccess.ACCESSIBLE
    "N", "No" -> LegacyAgencyDto.AccessibleAccess.NONE
    "BA" -> LegacyAgencyDto.AccessibleAccess.BY_ARRANGEMENT_ONLY
    else -> null
  },
  contact = contactName,
  // NOMIS never has more than one local authority for an agency, so we can just take the first one if it exists
  localAuthorityCode = localAuthorities.firstOrNull()?.code,
)

fun AgencyAddress.toLegacyAgencyAddressDto() = LegacyAgencyAddressDto(
  addressLine1 = addressLine1(flat, premise, street),
  addressLine2 = locality,
  town = city?.description,
  county = county?.description,
  postcode = postcode.postcode(),
  country = country?.description,
)

fun addressLine1(flat: String?, premise: String?, street: String?): String? {
  val parts = listOfNotNull(flat, premise, street)
  return parts.joinToString(", ").takeUnless { it.isBlank() }
}

private fun String?.postcode() = this
  ?.trim()
  ?.replace("\\s+".toRegex(), "")
  ?.uppercase(Locale.UK)
  ?.let { postcode ->
    if (postcode.length in 4..<8) "${postcode.dropLast(3)} ${postcode.takeLast(3)}" else postcode
  }

fun AgencyEmailAddress.toLegacyAgencyEmailDto() = LegacyAgencyEmailDto(
  address = emailAddress,
)

fun AgencyPhoneNumber.toLegacyAgencyPhoneDto() = LegacyAgencyPhoneDto(
  number = number,
)

// NOMIS agency type codes are from the reference domain AGY_LOC_TYPE
fun CodeDescription.toLegacyAgencyType(): LegacyAgencyType = when (code) {
  "INST" -> LegacyAgencyType.PRISON
  "CRT" -> LegacyAgencyType.COURT
  "HOSPITAL" -> LegacyAgencyType.HOSPITAL
  "HSHOSP" -> LegacyAgencyType.SECURE_HOSPITAL
  "COMM" -> LegacyAgencyType.PROBATION_OFFICE
  "CRC" -> LegacyAgencyType.PROBATION_CRC
  "POLICE", "POLSTN" -> LegacyAgencyType.POLICE_CUSTODY_SUITE
  "APPR" -> LegacyAgencyType.APPROVED_PREMISE
  "AIRPORT" -> LegacyAgencyType.AIRPORT
  "HOST" -> LegacyAgencyType.VOLUNTARY_HOSTEL
  "IMDC" -> LegacyAgencyType.IMMIGRATION_DETENTION_CENTRE
  "OUT" -> LegacyAgencyType.OUTSIDE
  "PECS" -> LegacyAgencyType.PECS
  "PSY" -> LegacyAgencyType.PSYCHIATRIC_CARE
  "SCH" -> LegacyAgencyType.CHILDREN_SECURE_HOME
  "STC" -> LegacyAgencyType.SECURE_TRAINING_CENTRE
  "YOT" -> LegacyAgencyType.YOT
  "FNP" -> LegacyAgencyType.FOREIGN_NATIONAL_PRISON
  else -> throw IllegalArgumentException("Unknown NOMIS agency type code: $code")
}
