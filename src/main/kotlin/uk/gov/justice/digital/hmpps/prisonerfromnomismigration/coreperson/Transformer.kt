package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderPhoneNumber

// This method is subject to change once the CPR endpoint is available
fun CorePerson.toMigrateCorePersonRequest(): MigrateCorePersonRequest = MigrateCorePersonRequest(
  nomisPrisonNumber = this.prisonNumber,
  firstName = this.offenders[0].firstName,
  middleName1 = this.offenders[0].middleName1,
  middleName2 = this.offenders[0].middleName2,
  lastName = this.offenders[0].lastName,
  activeFlag = this.activeFlag,
  inOutStatus = this.inOutStatus,
  addresses = this.addresses.map { it.toCprAddress() },
  phoneNumbers = this.phoneNumbers.map { it.toCprPhoneNumber() },
  emailAddresses = this.emailAddresses.map { MockCprEmailAddress(it.emailAddressId, it.email) },
  // TODO add other lists/mappings
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
