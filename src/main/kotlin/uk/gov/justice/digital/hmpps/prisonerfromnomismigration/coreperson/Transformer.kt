package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderPhoneNumber

// This method is subject to change once the CPR endpoint is available
fun CorePerson.toMigrateCorePersonRequest(): MigrateCorePersonRequest = MigrateCorePersonRequest(
  nomisPrisonNumber = this.prisonNumber,
  offenders = this.offenders.map { it.toMockCprOffender() },
  activeFlag = this.activeFlag,
  inOutStatus = this.inOutStatus,
  addresses = this.addresses.map { it.toCprAddress() },
  phoneNumbers = this.phoneNumbers.map { it.toCprPhoneNumber() },
  emailAddresses = this.emailAddresses.map { MockCprEmailAddress(it.emailAddressId, it.email) },
  religions = this.beliefs.map { it.toCprBelief() },
  // TODO add other lists/mappings
)

fun CoreOffender.toMockCprOffender() = MockCprOffender(
  nomisOffenderId = offenderId,
  title = title?.description,
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
)

fun OffenderAddress.toCprAddress() =
  MockCprAddress(
    nomisAddressId = addressId,
    flat = flat,
    premise = premise,
    street = street,
    locality = locality,
    postcode = postcode,
    town = city?.description,
    county = county?.code,
    country = country?.code,
    noFixedAddress = noFixedAddress ?: false,
    isPrimary = primaryAddress,
    usages = usages.map { addrUsage -> MockCprAddressUsage(addrUsage.usage.code, addrUsage.active) },
    mail = mailAddress,
    comment = comment,
    startDate = startDate,
    endDate = endDate,
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
  religion = belief.description,
  startDate = startDate,
  endDate = endDate,
  changeReason = changeReason,
  comment = comments,
  createdByDisplayName = audit.createDisplayName,
  updatedDisplayName = audit.modifyDisplayName,
)
