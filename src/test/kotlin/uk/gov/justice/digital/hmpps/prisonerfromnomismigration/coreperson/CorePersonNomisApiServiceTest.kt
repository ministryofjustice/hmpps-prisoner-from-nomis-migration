package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.LocalDateTime

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(CorePersonNomisApiService::class, CorePersonNomisApiMockServer::class)
class CorePersonNomisApiServiceTest(
  @Autowired private val apiService: CorePersonNomisApiService,
  @Autowired private val mockServer: CorePersonNomisApiMockServer,
) {
  @Nested
  inner class GetPersonAddressesAndContacts {
    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetAddressesAndContacts(prisonNumber = "A12345BC")

      apiService.getCorePersonAddressesAndContacts(nomisPrisonNumber = "A12345BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/A12345BC/addresses-contacts")),
      )
    }

    @Test
    fun `will return the addresses and contacts`() = runTest {
      mockServer.stubGetAddressesAndContacts(
        prisonNumber = "A12345BC",
        addressesAndContacts = CorePersonAddressContact(
          addresses = listOf(
            OffenderAddress(
              addressId = 12345,
              primaryAddress = true,
              mailAddress = true,
              createdDateTime = LocalDateTime.parse("2024-06-01T12:00:00"),
              createdByUsername = "billybob",
              lastUpdatedDateTime = null,
              lastUpdatedByUsername = null,
            ),
          ),
        ),
      )

      val addressesAndContacts = apiService.getCorePersonAddressesAndContacts(nomisPrisonNumber = "A12345BC")

      assertThat(addressesAndContacts.addresses!![0].addressId).isEqualTo(12345)
    }
  }

  @Nested
  inner class GetOffenderEmail {
    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubGetOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      apiService.getOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/12345/email/45678")),
      )
    }

    @Test
    fun `will return the email`() = runTest {
      mockServer.stubGetOffenderEmail(
        offenderId = 12345,
        emailAddressId = 45678,
        emailAddress = offenderEmailAddress(45678).copy(email = "updated@example.com"),
      )

      val email = apiService.getOffenderEmail(offenderId = 12345, emailAddressId = 45678)

      assertThat(email.emailAddressId).isEqualTo(45678)
      assertThat(email.email).isEqualTo("updated@example.com")
    }
  }

  @Nested
  inner class GetOffenderAddress {
    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubGetOffenderAddress(offenderId = 12345, addressId = 45678)

      apiService.getOffenderAddress(offenderId = 12345, addressId = 45678)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/12345/address/45678")),
      )
    }

    @Test
    fun `will return the address`() = runTest {
      mockServer.stubGetOffenderAddress(
        offenderId = 12345,
        addressId = 45678,
        address = OffenderAddress(
          addressId = 45678,
          primaryAddress = true,
          mailAddress = false,
          createdDateTime = LocalDateTime.parse("2024-06-01T12:00:00"),
          createdByUsername = "billybob",
          lastUpdatedDateTime = null,
          lastUpdatedByUsername = null,
          postcode = "MK15 2ST",
        ),
      )

      val address = apiService.getOffenderAddress(offenderId = 12345, addressId = 45678)

      assertThat(address.addressId).isEqualTo(45678)
      assertThat(address.postcode).isEqualTo("MK15 2ST")
    }
  }

  @Nested
  inner class GetOffenderPhone {
    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubGetOffenderPhone(offenderId = 12345, phoneId = 45678)

      apiService.getOffenderPhone(offenderId = 12345, phoneId = 45678)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/12345/phone/45678")),
      )
    }

    @Test
    fun `will return the phone`() = runTest {
      mockServer.stubGetOffenderPhone(
        offenderId = 12345,
        phoneId = 45678,
        phone = offenderPhoneNumber(45678).copy(number = "07700 900123"),
      )

      val phone = apiService.getOffenderPhone(offenderId = 12345, phoneId = 45678)

      assertThat(phone.phoneId).isEqualTo(45678)
      assertThat(phone.number).isEqualTo("07700 900123")
    }
  }

  @Nested
  inner class GetOffenderAddressPhone {
    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubGetOffenderAddressPhone(offenderId = 12345, addressId = 23456, phoneId = 45678)

      apiService.getOffenderAddressPhone(offenderId = 12345, addressId = 23456, phoneId = 45678)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/12345/address/23456/phone/45678")),
      )
    }

    @Test
    fun `will return the phone`() = runTest {
      mockServer.stubGetOffenderAddressPhone(
        offenderId = 12345,
        addressId = 23456,
        phoneId = 45678,
        phone = offenderPhoneNumber(45678).copy(number = "07700 900123"),
      )

      val phone = apiService.getOffenderAddressPhone(offenderId = 12345, addressId = 23456, phoneId = 45678)

      assertThat(phone.phoneId).isEqualTo(45678)
      assertThat(phone.number).isEqualTo("07700 900123")
    }
  }

  @Nested
  inner class GetOffenderAddressUsage {
    @Test
    fun `will pass NOMIS ids and usage code to service`() = runTest {
      mockServer.stubGetOffenderAddressUsage(offenderId = 12345, addressId = 23456, usageCode = "CURFEW")

      apiService.getOffenderAddressUsage(offenderId = 12345, addressId = 23456, usageCode = "CURFEW")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/12345/address/23456/usage/CURFEW")),
      )
    }

    @Test
    fun `will return the address usage`() = runTest {
      mockServer.stubGetOffenderAddressUsage(
        offenderId = 12345,
        addressId = 23456,
        usageCode = "CURFEW",
        addressUsage = offenderAddressUsage(23456, "CURFEW").copy(active = false),
      )

      val usage = apiService.getOffenderAddressUsage(offenderId = 12345, addressId = 23456, usageCode = "CURFEW")

      assertThat(usage.addressId).isEqualTo(23456)
      assertThat(usage.usage).isEqualTo("CURFEW")
      assertThat(usage.active).isFalse()
    }
  }

  @Nested
  inner class GetOffenderAliasesAndIdentifiers {
    @Test
    fun `will pass NOMIS prison number to service`() = runTest {
      mockServer.stubGetAliasesAndIdentifiers(prisonNumber = "A12345BC")

      apiService.getOffenderAliasesAndIdentifiers(nomisPrisonNumber = "A12345BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/A12345BC/aliases-identifiers")),
      )
    }

    @Test
    fun `will return the aliases and identifiers`() = runTest {
      val offender = corePerson().offenders!!.first()
      mockServer.stubGetAliasesAndIdentifiers(
        prisonNumber = "A12345BC",
        aliasesAndIdentifiers = listOf(offender, offender.copy(offenderId = 2, lastName = "JONES", workingName = false, identifiers = emptyList())),
      )

      val aliases = apiService.getOffenderAliasesAndIdentifiers(nomisPrisonNumber = "A12345BC")

      assertThat(aliases).hasSize(2)
      with(aliases[0]) {
        assertThat(offenderId).isEqualTo(1)
        assertThat(lastName).isEqualTo("SMITH")
        assertThat(identifiers.single().identifier).isEqualTo("20/0071818T")
      }
      with(aliases[1]) {
        assertThat(offenderId).isEqualTo(2)
        assertThat(lastName).isEqualTo("JONES")
        assertThat(identifiers).isEmpty()
      }
    }
  }

  @Nested
  inner class GetOffenderAlias {
    @Test
    fun `will pass NOMIS offender id to service`() = runTest {
      mockServer.stubGetOffenderAlias(offenderId = 12345)

      apiService.getOffenderAlias(offenderId = 12345)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/12345/alias")),
      )
    }

    @Test
    fun `will return the alias`() = runTest {
      mockServer.stubGetOffenderAlias(
        offenderId = 12345,
        alias = corePerson().offenders!!.first().copy(offenderId = 12345, lastName = "JONES"),
      )

      val alias = apiService.getOffenderAlias(offenderId = 12345)

      assertThat(alias.offenderId).isEqualTo(12345)
      assertThat(alias.lastName).isEqualTo("JONES")
    }
  }

  @Nested
  inner class GetOffenderIdentifier {
    @Test
    fun `will pass NOMIS offender id and sequence to service`() = runTest {
      mockServer.stubGetOffenderIdentifier(offenderId = 12345, sequenceNumber = 3)

      apiService.getOffenderIdentifier(offenderId = 12345, sequenceNumber = 3)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/12345/identifier/3")),
      )
    }

    @Test
    fun `will return the identifier`() = runTest {
      mockServer.stubGetOffenderIdentifier(offenderId = 12345, sequenceNumber = 3)

      val identifier = apiService.getOffenderIdentifier(offenderId = 12345, sequenceNumber = 3)

      assertThat(identifier.offenderId).isEqualTo(12345)
      assertThat(identifier.sequence).isEqualTo(3)
      assertThat(identifier.type.code).isEqualTo("PNC")
      assertThat(identifier.identifier).isEqualTo("20/0071818T")
    }
  }

  @Nested
  inner class GetOffenderIdentifiers {
    @Test
    fun `will pass NOMIS offender id to service`() = runTest {
      mockServer.stubGetOffenderIdentifiers(offenderId = 12345)

      apiService.getOffenderIdentifiers(offenderId = 12345)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/12345/identifiers")),
      )
    }

    @Test
    fun `will return the identifiers`() = runTest {
      mockServer.stubGetOffenderIdentifiers(offenderId = 12345)

      val identifiers = apiService.getOffenderIdentifiers(offenderId = 12345)

      assertThat(identifiers).hasSize(1)
      with(identifiers.first()) {
        assertThat(offenderId).isEqualTo(12345)
        assertThat(sequence).isEqualTo(1)
        assertThat(type.code).isEqualTo("PNC")
        assertThat(identifier).isEqualTo("20/0071818T")
      }
    }

    @Test
    fun `will return an empty list when there are no identifiers`() = runTest {
      mockServer.stubGetOffenderIdentifiers(offenderId = 12345, identifiers = emptyList())

      assertThat(apiService.getOffenderIdentifiers(offenderId = 12345)).isEmpty()
    }

    @Test
    fun `will throw if offender not found`() = runTest {
      mockServer.stubGetOffenderIdentifiers(offenderId = 12345, status = HttpStatus.NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getOffenderIdentifiers(offenderId = 12345)
      }
    }
  }

  @Nested
  inner class GetOffenderReligions {
    @Test
    fun `will pass NOMIS prison number to service`() = runTest {
      mockServer.stubGetOffenderReligions(prisonNumber = "A1234BC")

      apiService.getOffenderReligions(nomisPrisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/core-person/A1234BC/religions")),
      )
    }

    @Test
    fun `will return the religions`() = runTest {
      mockServer.stubGetOffenderReligions(prisonNumber = "A1234BC", religions = multipleBeliefs())

      val religions = apiService.getOffenderReligions(nomisPrisonNumber = "A1234BC")

      assertThat(religions.map { it.beliefId }).containsExactly(1, 2)
      assertThat(religions.map { it.belief.code }).containsExactly("ZORO", "DRU")
    }

    @Test
    fun `will throw if prisoner not found`() = runTest {
      mockServer.stubGetOffenderReligions(prisonNumber = "A1234BC", status = HttpStatus.NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getOffenderReligions(nomisPrisonNumber = "A1234BC")
      }
    }
  }
}
