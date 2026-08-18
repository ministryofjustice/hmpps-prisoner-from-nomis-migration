package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription

class TransformerTest {
  @Test
  internal fun `formats postcode to uppercase and one space`() {
    val address = agencyAddress(postcode = " s3   8pH ")

    val transformed = address.toLegacyAgencyAddressDto()

    assertThat(transformed.postcode).isEqualTo("S3 8PH")
  }

  @Test
  internal fun `returns null postcode when source is null`() {
    val address = agencyAddress(postcode = null)

    val transformed = address.toLegacyAgencyAddressDto()

    assertThat(transformed.postcode).isNull()
  }

  @Test
  internal fun `reduces so no more than 8 characters`() {
    val address = agencyAddress(postcode = "SA 61 2AZ")

    val transformed = address.toLegacyAgencyAddressDto()

    assertThat(transformed.postcode).isEqualTo("SA61 2AZ")
  }

  private fun agencyAddress(postcode: String?) = AgencyAddress(
    id = 1,
    phoneNumbers = emptyList(),
    validatedPAF = false,
    primaryAddress = true,
    mailAddress = true,
    type = CodeDescription(code = "BUS", description = "Business Address"),
    flat = null,
    premise = "Sheffield Combined Crt Centre",
    street = "The Law Courts",
    locality = "50 West Bar",
    postcode = postcode,
    city = CodeDescription(code = "SHEFF", description = "Sheffield"),
    county = CodeDescription(code = "S.YORKSHIRE", description = "South Yorkshire"),
    country = CodeDescription(code = "ENG", description = "England"),
    noFixedAddress = false,
    comment = null,
    startDate = null,
    endDate = null,
  )
}
