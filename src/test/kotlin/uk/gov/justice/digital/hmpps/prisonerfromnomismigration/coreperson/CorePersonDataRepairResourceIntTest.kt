package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressesAndContactsResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconContactMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddressUsage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime

class CorePersonDataRepairResourceIntTest(
  @Autowired private val nomisApiMockServer: CorePersonNomisApiMockServer,
  @Autowired private val mappingApiMockServer: CorePersonMappingApiMockServer,
) : CorePersonIntegrationTestBase() {

  private val cprApiMock = CorePersonCprApiExtension.cprCorePersonServer

  @DisplayName("POST /prisoners/{prisonNumber}/core-person/addresses-contacts/repair")
  @Nested
  inner class RepairCorePersonAddressesAndContacts {
    val prisonNumber = "A1234KT"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/addresses-contacts/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/addresses-contacts/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/addresses-contacts/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      private val prisonNumber = "A1234KT"
      private val addressesAndContacts = CorePersonAddressContact(
        addresses = listOf(
          OffenderAddress(
            addressId = 10000,
            primaryAddress = true,
            mailAddress = true,
            createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
            createdByUsername = "SYSTEM",
            lastUpdatedDateTime = null,
            lastUpdatedByUsername = null,
            flat = "Flat 2",
            premise = "The Priory",
            street = "Main Street",
            locality = "Sheffield",
            postcode = "S1 1AA",
            city = CodeDescription("SHEF", "Sheffield"),
            county = CodeDescription("YOR", "Yorkshire"),
            country = CodeDescription("GBR", "United Kingdom"),
            phoneNumbers = listOf(
              OffenderPhoneNumber(
                phoneId = 20000,
                number = "0114 123 4567",
                type = CodeDescription("HOME", "Home"),
                createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
                createdByUsername = "SYSTEM",
                lastUpdatedDateTime = null,
                lastUpdatedByUsername = null,
                extension = "123",
              ),
            ),
            noFixedAddress = false,
            comment = "Address comment",
            startDate = LocalDate.of(2001, 3, 3),
            endDate = null,
            usages = listOf(
              OffenderAddressUsage(
                addressId = 10000,
                usage = CodeDescription("HOME", "Home"),
                active = true,
                createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
                createdByUsername = "SYSTEM",
                lastUpdatedDateTime = null,
                lastUpdatedByUsername = null,
              ),
            ),
          ),
        ),
        phoneNumbers = listOf(
          OffenderPhoneNumber(
            phoneId = 30000,
            number = "07700 900123",
            type = CodeDescription("MOBILE", "Mobile"),
            createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
            createdByUsername = "SYSTEM",
            lastUpdatedDateTime = null,
            lastUpdatedByUsername = null,
          ),
        ),
        emailAddresses = listOf(
          OffenderEmailAddress(
            emailAddressId = 40000,
            email = "test@example.com",
            createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
            createdByUsername = "SYSTEM",
            lastUpdatedDateTime = null,
            lastUpdatedByUsername = null,
          ),
        ),
      )

      private val addressesMapping = listOf(
        SysconAddressMapping(
          nomisAddressId = 10000L,
          cprAddressId = "7981274e-bcb6-4879-9712-4164743f2ee4",
          addressUsageMappings = emptyList(),
          contactMappings = emptyList(),
        ),
      )
      private val contactsMapping = listOf(
        SysconContactMapping(
          nomisContactId = 10000L,
          nomisContactType = SysconContactMapping.NomisContactType.HOME,
          cprContactId = "84cdb577-63da-44e6-9293-150dc7d5cd14",
        ),
      )

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetCorePersonAddressesAndContacts(prisonNumber, addressesAndContacts = addressesAndContacts)
        cprApiMock.stubMigrateAddressesAndContacts(
          nomisPrisonNumber = prisonNumber,
          response = SysconAddressesAndContactsResponseBody(
            prisonNumber = prisonNumber,
            addressesMappings = addressesMapping,
            contactMappings = contactsMapping,
          ),
        )
        mappingApiMockServer.stubReplaceMappings()

        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/addresses-contacts/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will retrieve current addresses and contacts for the prisoner`() {
        nomisApiMockServer.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/core-person/$prisonNumber/addresses-contacts")))
      }

      @Test
      fun `will send addresses and contacts to CPR`() {
        cprApiMock.verify(
          WireMock.postRequestedFor(WireMock.urlPathEqualTo("/syscon-sync/addresses-contacts/$prisonNumber"))
            .withRequestBodyJsonPath("addresses[0].nomisAddressId", 10000)
            .withRequestBodyJsonPath("addresses[0].isPrimary", true)
            .withRequestBodyJsonPath("addresses[0].addressUsage[0].nomisAddressUsageId", 10000)
            .withRequestBodyJsonPath("addresses[0].addressUsage[0].addressUsageCode", "HOME")
            .withRequestBodyJsonPath("addresses[0].contacts[0].nomisContactId", 20000)
            .withRequestBodyJsonPath("addresses[0].contacts[0].type", "HOME")
            .withRequestBodyJsonPath("addresses[0].contacts[0].value", "0114 123 4567")
            .withRequestBodyJsonPath("addresses[0].createDateTime", "2001-03-03T00:00:00")
            .withRequestBodyJsonPath("addresses[0].createUserId", "SYSTEM")
            .withRequestBodyJsonPath("addresses[0].postcode", "S1 1AA")
            .withRequestBodyJsonPath("contacts[0].nomisContactId", 30000)
            .withRequestBodyJsonPath("contacts[0].type", "MOBILE")
            .withRequestBodyJsonPath("contacts[0].value", "07700 900123")
            .withRequestBodyJsonPath("contacts[1].nomisContactId", 40000)
            .withRequestBodyJsonPath("contacts[1].type", "EMAIL")
            .withRequestBodyJsonPath("contacts[1].value", "test@example.com"),
        )
      }

      @Test
      fun `will replace mappings with the correct mapping type`() {
        mappingApiMockServer.verify(
          WireMock.postRequestedFor(
            WireMock.urlPathEqualTo("/mapping/core-person/replace"),
          ).withRequestBodyJsonPath("mappingType", CorePersonMappingsDto.MappingType.NOMIS_CREATED.toString()),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          ArgumentMatchers.eq("coreperson-address-contact-resynchronisation-repair"),
          check {
            Assertions.assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathNotFound {
      val prisonNumber = "A1234KT"

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetCorePerson(prisonNumber, status = HttpStatus.NOT_FOUND)

        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/addresses-contacts/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
      }

      @Test
      fun `will try to retrieve current addresses and contacts for the prisoner`() {
        nomisApiMockServer.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/core-person/$prisonNumber/addresses-contacts")))
      }

      @Test
      fun `will not send addresses and contacts to CPR`() {
        cprApiMock.verify(0, WireMock.postRequestedFor(WireMock.anyUrl()))
      }

      @Test
      fun `will not track telemetry for the repair`() {
        verifyNoInteractions(telemetryClient)
      }
    }
  }
}
