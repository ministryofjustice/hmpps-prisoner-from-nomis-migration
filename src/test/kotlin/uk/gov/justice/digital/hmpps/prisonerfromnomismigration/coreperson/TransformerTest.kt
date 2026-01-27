package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import java.time.LocalDate

class TransformerTest {
  @Nested
  @DisplayName("buildFullAddress")
  inner class Addresses {
    @Test
    fun `should map all address fields`() {
      val address = OffenderAddress(
        addressId = 101,
        flat = "2",
        premise = "3",
        street = "Main Street",
        locality = "Crookes",
        city = CodeDescription("SHF", "Sheffield"),
        county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
        country = CodeDescription("ENG", "England"),
        postcode = "S10 1AB",
        primaryAddress = true,
        validatedPAF = false,
        mailAddress = true,
        startDate = LocalDate.parse("2020-11-01"),
      ).buildFullAddress()

      assertThat(address).isEqualTo(
        "Flat 2, 3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England",
      )
    }

    @Test
    fun `should handle missing flat`() {
      val address = OffenderAddress(
        addressId = 101,
        premise = "3",
        street = "Main Street",
        locality = "Crookes",
        city = CodeDescription("SHF", "Sheffield"),
        county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
        country = CodeDescription("ENG", "England"),
        postcode = "S10 1AB",
        primaryAddress = true,
        validatedPAF = false,
        mailAddress = true,
      ).buildFullAddress()

      assertThat(address).isEqualTo(
        "3 Main Street, Crookes, Sheffield, South Yorkshire, S10 1AB, England",
      )
    }

    @Test
    fun `should handle different combinations of premise and street 1`() {
      val address = OffenderAddress(
        addressId = 101,
        premise = "1",
        street = "Main Street",
        locality = "locality",
        city = CodeDescription("SHF", "any"),
        county = CodeDescription("S.YORKSHIRE", "any"),
        country = CodeDescription("ENG", "any"),
        postcode = "any",
        primaryAddress = true,
        validatedPAF = false,
        mailAddress = true,
      ).buildFullAddress()

      assertThat(address).startsWith("1 Main Street, locality, ")
    }

    @Test
    fun `should handle different combinations of premise and street 2`() {
      val address = OffenderAddress(
        addressId = 101,
        premise = "Big House",
        street = "Main Street",
        locality = "locality",
        city = CodeDescription("SHF", "any"),
        county = CodeDescription("S.YORKSHIRE", "any"),
        country = CodeDescription("ENG", "any"),
        postcode = "any",
        primaryAddress = true,
        validatedPAF = false,
        mailAddress = true,
      ).buildFullAddress()

      assertThat(address).startsWith("Big House, Main Street, locality, ")
    }

    @Test
    fun `should handle different combinations of premise and street 3`() {
      val address = OffenderAddress(
        addressId = 101,
        premise = "Big House",
        locality = "locality",
        city = CodeDescription("SHF", "any"),
        county = CodeDescription("S.YORKSHIRE", "any"),
        country = CodeDescription("ENG", "any"),
        postcode = "any",
        primaryAddress = true,
        validatedPAF = false,
        mailAddress = true,
      ).buildFullAddress()

      assertThat(address).startsWith("Big House, locality, ")
    }

    @Test
    fun `should handle different combinations of premise and street 4`() {
      val address = OffenderAddress(
        addressId = 101,
        street = "Main Street",
        locality = "locality",
        city = CodeDescription("SHF", "any"),
        county = CodeDescription("S.YORKSHIRE", "any"),
        country = CodeDescription("ENG", "any"),
        postcode = "any",
        primaryAddress = true,
        validatedPAF = false,
        mailAddress = true,
      ).buildFullAddress()

      assertThat(address).startsWith("Main Street, locality, ")
    }

    @Test
    fun `should handle missing address fields`() {
      val address = OffenderAddress(
        addressId = 101,
        postcode = "S11 1BB",
        primaryAddress = true,
        validatedPAF = false,
        mailAddress = true,
      ).buildFullAddress()

      assertThat(address).isEqualTo(
        "S11 1BB",
      )
    }

    @Test
    fun `should map no fixed address`() {
      val address = OffenderAddress(
        addressId = 101,
        flat = "2",
        premise = "3",
        street = "Main Street",
        locality = "Crookes",
        city = CodeDescription("SHF", "Sheffield"),
        county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
        country = CodeDescription("ENG", "England"),
        primaryAddress = true,
        validatedPAF = false,
        mailAddress = true,
        noFixedAddress = true,
      ).buildFullAddress()

      assertThat(address).isEqualTo(
        "No fixed address",
      )
    }
  }
}
