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

fun AgencyResponse.toLegacyAgencyDto() = LegacyAgencyDto(
  agencyType = type.toLegacyAgencyType(),
  name = description,
  active = active,
  addresses = addresses.map { it.toLegacyAgencyAddressDto() },
  emailAddresses = emailAddresses.map { it.toLegacyAgencyEmailDto() },
  phoneNumbers = phones.map { it.toLegacyAgencyPhoneDto() },
  description = longDescription,
  inactiveDate = deactivationDate,
  cjitCode = cjitCode,
  areaCode = area?.code,
  regionCode = region?.code,
  geographicalAreaCode = district?.code,
  payrollRegionCode = payrollRegion?.code,
  courtTypeCode = courtType?.code,
  // there is no reliable mapping from the NOMIS disabilityAccessCode free-format code to the DPS
  // AccessibleAccess enum so it is not migrated
  accessibleAccess = null,
  contact = contactName,
)

fun AgencyAddress.toLegacyAgencyAddressDto() = LegacyAgencyAddressDto(
  addressLine1 = flat?.let { "$it, $premise" } ?: premise,
  addressLine2 = street ?: locality,
  town = city?.description,
  county = county?.description,
  postcode = postcode,
  country = country?.description,
)

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
  else -> throw IllegalArgumentException("Unknown NOMIS agency type code: $code")
}
