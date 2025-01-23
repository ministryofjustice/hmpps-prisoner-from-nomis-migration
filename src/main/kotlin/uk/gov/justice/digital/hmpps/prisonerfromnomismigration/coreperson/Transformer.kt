package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderDisability
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderInterestToImmigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderNationality
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderNationalityDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderSexualOrientation
import java.time.LocalDateTime

// This method is subject to change once the CPR endpoint is available
fun CorePerson.toMigrateCorePersonRequest(): MigrateCorePersonRequest = MigrateCorePersonRequest(
  nomisPrisonNumber = prisonNumber,
  activeFlag = activeFlag,
  inOutStatus = inOutStatus,
  offenders = offenders?.map { it.toMockCprOffender() },
  addresses = addresses?.map { it.toCprAddress() },
  phoneNumbers = phoneNumbers?.map { it.toCprPhoneNumber() },
  emailAddresses = emailAddresses?.map { MockCprEmailAddress(it.emailAddressId, it.email) },
  religions = beliefs?.map { it.toCprBelief() },
  sentenceStartDates = sentenceStartDates,
  nationalities = nationalities?.map { it.toCprNationality() },
  nationalityDetails = nationalityDetails?.map { it.toCprNationalityDetails() },
  sexualOrientations = sexualOrientations?.map { it.toCprSexualOrientation() },
  disabilities = disabilities?.map { it.toCprDisability() },
  interestsToImmigration = interestsToImmigration?.map { it.toCprInterestToImmigration() },
)

fun CoreOffender.toMockCprOffender() = MockCprOffender(
  nomisOffenderId = offenderId,
  title = title?.code,
  firstName = firstName,
  middleName1 = middleName1,
  middleName2 = middleName2,
  lastName = lastName,
  dateOfBirth = dateOfBirth,
  birthPlace = birthPlace,
  birthCountry = birthCountry?.code,
  race = ethnicity?.code,
  sex = sex?.code,
  workingName = workingName,
  nameType = nameType?.code,
  createDate = createDate,
  identifiers = identifiers.map { it.toMockCprIdentifier() },
)
fun Identifier.toMockCprIdentifier() = MockCprIdentifier(
  nomisSequence = sequence,
  identifier = identifier,
  type = type.code,
  issuedBy = issuedAuthority,
  issuedDate = issuedDate,
  verified = verified,
)
fun OffenderAddress.toCprAddress() =
  MockCprAddress(
    nomisAddressId = addressId,
    flat = flat,
    premise = premise,
    street = street,
    locality = locality,
    postcode = postcode,
    town = city?.code,
    county = county?.code,
    country = country?.code,
    noFixedAddress = noFixedAddress,
    isPrimary = primaryAddress,
    mail = mailAddress,
    comment = comment,
    startDate = startDate,
    endDate = endDate,
    validatedPAF = validatedPAF,
    usages = usages?.map { addrUsage -> MockCprAddressUsage(addrUsage.usage.code, addrUsage.active) },
    phoneNumbers = phoneNumbers?.map { it.toCprPhoneNumber() },
  )

fun OffenderPhoneNumber.toCprPhoneNumber() =
  MockCprPhoneNumber(
    nomisPhoneId = phoneId,
    phoneNumber = number,
    phoneType = type.code,
    phoneExtension = extension,
  )
fun OffenderBelief.toCprBelief() = MockCprBelief(
  nomisBeliefId = beliefId,
  religion = belief.code,
  startDate = startDate,
  endDate = endDate,
  changeReason = changeReason,
  comment = comments,
  createdByDisplayName = audit.createDisplayName,
  updatedDisplayName = audit.modifyDisplayName,
)
fun OffenderNationality.toCprNationality() = MockCprNationality(
  nomisBookingId = bookingId,
  nationality = nationality.code,
  startDateTime = startDateTime.toDateTime()!!,
  endDateTime = endDateTime.toDateTime(),
  latestBooking = latestBooking,
)
fun OffenderNationalityDetails.toCprNationalityDetails() = MockCprNationalityDetails(
  nomisBookingId = bookingId,
  details = details,
  startDateTime = startDateTime.toDateTime()!!,
  endDateTime = endDateTime.toDateTime(),
  latestBooking = latestBooking,
)
fun OffenderSexualOrientation.toCprSexualOrientation() = MockCprSexualOrientation(
  nomisBookingId = bookingId,
  sexualOrientation = sexualOrientation.code,
  startDateTime = startDateTime.toDateTime()!!,
  endDateTime = endDateTime.toDateTime(),
  latestBooking = latestBooking,
)
fun OffenderDisability.toCprDisability() = MockCprDisability(
  nomisBookingId = bookingId,
  disability = disability,
  startDateTime = startDateTime.toDateTime()!!,
  endDateTime = endDateTime.toDateTime(),
  latestBooking = latestBooking,
)
fun OffenderInterestToImmigration.toCprInterestToImmigration() = MockCprInterestToImmigration(
  nomisBookingId = bookingId,
  interestToImmigration = interestToImmigration,
  startDateTime = startDateTime.toDateTime()!!,
  endDateTime = endDateTime.toDateTime(),
  latestBooking = latestBooking,
)

private fun String?.toDateTime() = this?.let { LocalDateTime.parse(it) }
