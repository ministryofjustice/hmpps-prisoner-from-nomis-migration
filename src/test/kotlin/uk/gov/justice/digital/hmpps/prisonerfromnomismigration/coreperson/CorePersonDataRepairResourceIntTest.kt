package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.NomisIdentifierId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressesAndContactsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAliasesAndIdentifiersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressUsageMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressesAndContactsResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAliasMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconContactMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconIdentifierMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AddressUsageByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMappingsByNomisIdsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressUsageMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsByNomisIdsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IdentifierByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddressUsage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime

class CorePersonDataRepairResourceIntTest(
  @Autowired private val nomisApiMockServer: CorePersonNomisApiMockServer,
  @Autowired private val mappingApiMockServer: CorePersonAddressContactMappingApiMockServer,
  @Autowired private val aliasIdentifierMappingApiMockServer: CorePersonAliasIdentifierMappingApiMockServer,
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
                usage = "HOME",
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
        nomisApiMockServer.stubGetAddressesAndContacts(prisonNumber, addressesAndContacts = addressesAndContacts)
        mappingApiMockServer.stubGetMappingsFromNomisIds()
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
            WireMock.urlPathEqualTo("/mapping/core-person/address-contact/replace"),
          ).withRequestBodyJsonPath("mappingType", CorePersonAddressContactMappingsDto.MappingType.NOMIS_CREATED.toString()),
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
    inner class HappyPathWithExistingMappings {
      private val prisonNumber = "A1234KT"

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetAddressesAndContacts(prisonNumber = prisonNumber)
        mappingApiMockServer.stubGetMappingsFromNomisIds(
          CorePersonAddressContactMappingsResult(
            addresses = listOf(
              CorePersonAddressMappingDto(
                cprId = "cpr-address-12345",
                nomisId = 12345,
                nomisPrisonNumber = prisonNumber,
                mappingType = CorePersonAddressMappingDto.MappingType.MIGRATED,
              ),
            ),
            addressUsages = listOf(
              CorePersonAddressUsageMappingDto(
                cprId = "cpr-usage-10000",
                nomisId = 10000,
                addressUsageCode = "HOME",
                nomisPrisonNumber = prisonNumber,
                mappingType = CorePersonAddressUsageMappingDto.MappingType.MIGRATED,
              ),
            ),
            contacts = listOf(
              CorePersonContactMappingDto(
                cprId = "cpr-phone-20000",
                nomisId = 20000,
                nomisContactType = CorePersonContactMappingDto.NomisContactType.PHONE,
                nomisPrisonNumber = prisonNumber,
                mappingType = CorePersonContactMappingDto.MappingType.MIGRATED,
              ),
              CorePersonContactMappingDto(
                cprId = "cpr-phone-30000",
                nomisId = 30000,
                nomisContactType = CorePersonContactMappingDto.NomisContactType.PHONE,
                nomisPrisonNumber = prisonNumber,
                mappingType = CorePersonContactMappingDto.MappingType.MIGRATED,
              ),
              CorePersonContactMappingDto(
                cprId = "cpr-email-40000",
                nomisId = 40000,
                nomisContactType = CorePersonContactMappingDto.NomisContactType.EMAIL,
                nomisPrisonNumber = prisonNumber,
                mappingType = CorePersonContactMappingDto.MappingType.MIGRATED,
              ),
            ),
          ),
        )
        cprApiMock.stubMigrateAddressesAndContacts(
          nomisPrisonNumber = prisonNumber,
          response = SysconAddressesAndContactsResponseBody(
            prisonNumber = prisonNumber,
            addressesMappings = listOf(
              SysconAddressMapping(
                nomisAddressId = 12345,
                cprAddressId = "cpr-address-12345",
                addressUsageMappings = listOf(
                  SysconAddressUsageMapping(
                    nomisAddressUsageId = 10000,
                    nomisAddressUsageCode = SysconAddressUsageMapping.NomisAddressUsageCode.HOME,
                    cprAddressUsageId = "cpr-usage-10000",
                  ),
                ),
                contactMappings = listOf(
                  SysconContactMapping(
                    nomisContactId = 20000,
                    nomisContactType = SysconContactMapping.NomisContactType.HOME,
                    cprContactId = "cpr-phone-20000",
                  ),
                ),
              ),
            ),
            contactMappings = listOf(
              SysconContactMapping(
                nomisContactId = 30000,
                nomisContactType = SysconContactMapping.NomisContactType.MOBILE,
                cprContactId = "cpr-phone-30000",
              ),
              SysconContactMapping(
                nomisContactId = 40000,
                nomisContactType = SysconContactMapping.NomisContactType.EMAIL,
                cprContactId = "cpr-email-40000",
              ),
            ),
          ),
        )
        mappingApiMockServer.stubReplaceMappings()

        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/addresses-contacts/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will request existing mappings for all the NOMIS ids`() {
        val request: CorePersonAddressContactMappingsByNomisIdsRequest =
          MappingApiExtension.getRequestBody(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/core-person/address-contact/mappings/by-nomis-ids")))

        assertThat(request.addresses).containsExactly(12345L)
        assertThat(request.addressUsages).containsExactly(AddressUsageByNomisIdRequest(10000, "HOME"))
        assertThat(request.contacts).containsExactlyInAnyOrder(
          ContactByNomisIdRequest(20000, ContactByNomisIdRequest.NomisContactType.PHONE),
          ContactByNomisIdRequest(30000, ContactByNomisIdRequest.NomisContactType.PHONE),
          ContactByNomisIdRequest(40000, ContactByNomisIdRequest.NomisContactType.EMAIL),
        )
      }

      @Test
      fun `will add existing CPR ids to the request sent to CPR`() {
        val request: PrisonAddressesAndContactsRequest =
          CorePersonCprApiExtension.getRequestBody(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/syscon-sync/addresses-contacts/$prisonNumber")))

        with(request.addresses.first()) {
          assertThat(nomisAddressId).isEqualTo(12345L)
          assertThat(cprAddressId).isEqualTo("cpr-address-12345")
          with(addressUsage.first()) {
            assertThat(nomisAddressUsageId).isEqualTo(10000L)
            assertThat(cprAddressUsageId).isEqualTo("cpr-usage-10000")
          }
          with(contacts.first()) {
            assertThat(nomisContactId).isEqualTo(20000L)
            assertThat(cprContactId).isEqualTo("cpr-phone-20000")
          }
        }
        val phoneContact = request.contacts.first { it.nomisContactId == 30000L }
        assertThat(phoneContact.type).isEqualTo(PrisonContact.Type.MOBILE)
        assertThat(phoneContact.cprContactId).isEqualTo("cpr-phone-30000")
        val emailContact = request.contacts.first { it.nomisContactId == 40000L }
        assertThat(emailContact.type).isEqualTo(PrisonContact.Type.EMAIL)
        assertThat(emailContact.cprContactId).isEqualTo("cpr-email-40000")
      }

      @Test
      fun `will replace the mappings returned from CPR`() {
        val request: CorePersonAddressContactMappingsDto =
          MappingApiExtension.getRequestBody(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/core-person/address-contact/replace")))

        assertThat(request.mappingType).isEqualTo(CorePersonAddressContactMappingsDto.MappingType.NOMIS_CREATED)
        assertThat(request.personMapping.nomisPrisonNumber).isEqualTo(prisonNumber)
        with(request.addresses.first()) {
          assertThat(nomisId).isEqualTo(12345L)
          assertThat(cprId).isEqualTo("cpr-address-12345")
          assertThat(nomisPrisonNumber).isEqualTo(prisonNumber)
          assertThat(mappingType).isEqualTo(CorePersonAddressMappingDto.MappingType.NOMIS_CREATED)
        }
        with(request.addressUsages.first()) {
          assertThat(nomisId).isEqualTo(10000L)
          assertThat(addressUsageCode).isEqualTo("HOME")
          assertThat(cprId).isEqualTo("cpr-usage-10000")
          assertThat(mappingType).isEqualTo(CorePersonAddressUsageMappingDto.MappingType.NOMIS_CREATED)
        }
        val contacts = request.contacts
        assertThat(contacts.filter { it.nomisId == 20000L && it.nomisContactType == CorePersonContactMappingDto.NomisContactType.PHONE && it.cprId == "cpr-phone-20000" }).isNotEmpty()
        assertThat(contacts.filter { it.nomisId == 30000L && it.nomisContactType == CorePersonContactMappingDto.NomisContactType.PHONE && it.cprId == "cpr-phone-30000" }).isNotEmpty()
        assertThat(contacts.filter { it.nomisId == 40000L && it.nomisContactType == CorePersonContactMappingDto.NomisContactType.EMAIL && it.cprId == "cpr-email-40000" }).isNotEmpty()
      }
    }

    @Nested
    inner class HappyPathWithNoExistingMappings {
      private val prisonNumber = "A1234KT"

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetAddressesAndContacts(prisonNumber = prisonNumber)
        mappingApiMockServer.stubGetMappingsFromNomisIds()
        cprApiMock.stubMigrateAddressesAndContacts(nomisPrisonNumber = prisonNumber)
        mappingApiMockServer.stubReplaceMappings()

        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/addresses-contacts/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will send the request to CPR without any CPR ids`() {
        val request: PrisonAddressesAndContactsRequest =
          CorePersonCprApiExtension.getRequestBody(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/syscon-sync/addresses-contacts/$prisonNumber")))

        with(request.addresses.single()) {
          assertThat(nomisAddressId).isEqualTo(12345L)
          assertThat(cprAddressId).isNull()
          with(addressUsage.single()) {
            assertThat(nomisAddressUsageId).isEqualTo(10000L)
            assertThat(cprAddressUsageId).isNull()
          }
          with(contacts.single()) {
            assertThat(nomisContactId).isEqualTo(20000L)
            assertThat(cprContactId).isNull()
          }
        }
        assertThat(request.contacts.map { it.nomisContactId }).containsExactlyInAnyOrder(30000L, 40000L)
        assertThat(request.contacts.map { it.cprContactId }).containsOnlyNulls()
      }
    }

    @Nested
    inner class HappyPathNotFound {
      val prisonNumber = "A1234KT"

      @BeforeEach
      fun setUp() {
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

  @DisplayName("POST /prisoners/{prisonNumber}/core-person/aliases-identifiers/repair")
  @Nested
  inner class RepairCorePersonAliasesAndIdentifiers {
    val prisonNumber = "A1234KT"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/aliases-identifiers/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/aliases-identifiers/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/aliases-identifiers/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      private val aliases = listOf(
        CoreOffender(
          offenderId = 10000,
          title = CodeDescription("MR", "Mr"),
          firstName = "JOHN",
          middleName1 = "FRED",
          middleName2 = "JAMES",
          lastName = "SMITH",
          suffix = CodeDescription("SR", "Senior"),
          workingName = true,
          dateOfBirth = LocalDate.parse("1980-01-01"),
          birthCountry = CodeDescription("ROM", "Romania"),
          sex = CodeDescription("M", "Male"),
          nameType = CodeDescription("CN", "Current Name"),
          identifiers = listOf(
            Identifier(
              offenderId = 10000,
              sequence = 1,
              type = CodeDescription("NINO", "National Insurance Number"),
              identifier = "AA123456A",
              issuedAuthority = "DWP",
              verified = true,
              createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
              createdByUsername = "SYSTEM",
              lastUpdatedDateTime = null,
              lastUpdatedByUsername = null,
            ),
          ),
          createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
          createdByUsername = "SYSTEM",
          lastUpdatedDateTime = null,
          lastUpdatedByUsername = null,
        ),
        CoreOffender(
          offenderId = 10001,
          firstName = "JOHNNY",
          lastName = "SMITHE",
          workingName = false,
          identifiers = emptyList(),
          createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
          createdByUsername = "SYSTEM",
          lastUpdatedDateTime = null,
          lastUpdatedByUsername = null,
        ),
      )

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetAliasesAndIdentifiers(prisonNumber, aliasesAndIdentifiers = aliases)
        aliasIdentifierMappingApiMockServer.stubGetMappingsFromNomisIds()
        cprApiMock.stubMigrateAliasesAndIdentifiers(
          nomisPrisonNumber = prisonNumber,
          aliasMappings = listOf(
            SysconAliasMapping(nomisOffenderId = 10000, cprAliasId = "4d1d4b0e-2a3c-4f3e-9d6a-0f0c1b2a3c4d"),
            SysconAliasMapping(nomisOffenderId = 10001, cprAliasId = "5e2e5c1f-3b4d-4a4f-8e7b-1a1d2c3b4d5e"),
          ),
          identifierMappings = listOf(
            SysconIdentifierMapping(
              nomisIdentifierId = NomisIdentifierId(nomisOffenderId = 10000, nomisSequence = 1),
              cprIdentifierId = "6f3f6d2a-4c5e-4b5a-9f8c-2b2e3d4c5e6f",
            ),
          ),
        )
        aliasIdentifierMappingApiMockServer.stubReplaceMappings()

        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/aliases-identifiers/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will retrieve current aliases and identifiers for the prisoner`() {
        nomisApiMockServer.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/core-person/$prisonNumber/aliases-identifiers")))
      }

      @Test
      fun `will request existing mappings for all the NOMIS ids`() {
        val request: CorePersonAliasIdentifierMappingsByNomisIdsRequest =
          MappingApiExtension.getRequestBody(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/core-person/alias-identifier/mappings/by-nomis-ids")))

        assertThat(request.aliases).containsExactly(10000L, 10001L)
        assertThat(request.identifiers).containsExactly(IdentifierByNomisIdRequest(nomisId = 10000, nomisSequence = 1))
      }

      @Test
      fun `will not send any CPR ids when there are no existing mappings`() {
        val request: PrisonAliasesAndIdentifiersRequest =
          CorePersonCprApiExtension.getRequestBody(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/syscon-sync/aliases-identifiers/$prisonNumber")))

        assertThat(request.aliases.map { it.cprAliasId }).containsOnlyNulls()
        assertThat(request.aliases.flatMap { it.identifiers }.map { it.cprIdentifierId }).containsOnlyNulls()
      }

      @Test
      fun `will send aliases and identifiers to CPR`() {
        cprApiMock.verify(
          WireMock.postRequestedFor(WireMock.urlPathEqualTo("/syscon-sync/aliases-identifiers/$prisonNumber"))
            .withRequestBodyJsonPath("aliases[0].nomisOffenderId", 10000)
            .withRequestBodyJsonPath("aliases[0].isPrimary", true)
            .withRequestBodyJsonPath("aliases[0].titleCode", "MR")
            .withRequestBodyJsonPath("aliases[0].firstName", "JOHN")
            .withRequestBodyJsonPath("aliases[0].middleNames", "FRED JAMES")
            .withRequestBodyJsonPath("aliases[0].lastName", "SMITH")
            .withRequestBodyJsonPath("aliases[0].suffix", "Senior")
            .withRequestBodyJsonPath("aliases[0].birthCountry", "ROU")
            .withRequestBodyJsonPath("aliases[0].sexCode", "M")
            .withRequestBodyJsonPath("aliases[0].nameType", "CN")
            .withRequestBodyJsonPath("aliases[0].createDateTime", "2001-03-03T00:00:00")
            .withRequestBodyJsonPath("aliases[0].createUserId", "SYSTEM")
            .withRequestBodyJsonPath("aliases[0].identifiers[0].nomisIdentifierId.nomisOffenderId", 10000)
            .withRequestBodyJsonPath("aliases[0].identifiers[0].nomisIdentifierId.nomisSequence", 1)
            .withRequestBodyJsonPath("aliases[0].identifiers[0].type", "NATIONAL_INSURANCE_NUMBER")
            .withRequestBodyJsonPath("aliases[0].identifiers[0].value", "AA123456A")
            .withRequestBodyJsonPath("aliases[0].identifiers[0].comment", "DWP")
            .withRequestBodyJsonPath("aliases[1].nomisOffenderId", 10001)
            .withRequestBodyJsonPath("aliases[1].isPrimary", false),
        )
      }

      @Test
      fun `will replace mappings with the mappings returned from CPR`() {
        val request: CorePersonAliasIdentifierMappingsDto = MappingApiExtension.getRequestBody(
          WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/core-person/alias-identifier/replace")),
        )
        with(request) {
          assertThat(mappingType).isEqualTo(CorePersonAliasIdentifierMappingsDto.MappingType.NOMIS_CREATED)
          assertThat(personMapping.cprId).isEqualTo(prisonNumber)
          assertThat(personMapping.nomisPrisonNumber).isEqualTo(prisonNumber)
          assertThat(aliases).containsExactly(
            OffenderAliasMappingDto(
              cprId = "4d1d4b0e-2a3c-4f3e-9d6a-0f0c1b2a3c4d",
              nomisOffenderId = 10000,
              nomisPrisonNumber = prisonNumber,
              mappingType = OffenderAliasMappingDto.MappingType.NOMIS_CREATED,
            ),
            OffenderAliasMappingDto(
              cprId = "5e2e5c1f-3b4d-4a4f-8e7b-1a1d2c3b4d5e",
              nomisOffenderId = 10001,
              nomisPrisonNumber = prisonNumber,
              mappingType = OffenderAliasMappingDto.MappingType.NOMIS_CREATED,
            ),
          )
          assertThat(identifiers).containsExactly(
            OffenderIdentifierMappingDto(
              cprId = "6f3f6d2a-4c5e-4b5a-9f8c-2b2e3d4c5e6f",
              nomisOffenderId = 10000,
              nomisIdentifierSequence = 1,
              nomisPrisonNumber = prisonNumber,
              mappingType = OffenderIdentifierMappingDto.MappingType.NOMIS_CREATED,
            ),
          )
        }
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          ArgumentMatchers.eq("coreperson-alias-identifier-resynchronisation-repair"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathWithExistingMappings {
      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetAliasesAndIdentifiers(
          prisonNumber,
          aliasesAndIdentifiers = listOf(
            CoreOffender(
              offenderId = 10000,
              firstName = "JOHN",
              lastName = "SMITH",
              workingName = true,
              identifiers = listOf(
                Identifier(
                  offenderId = 10000,
                  sequence = 1,
                  type = CodeDescription("PNC", "PNC Number"),
                  identifier = "2001/0123456X",
                  verified = false,
                  createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
                  createdByUsername = "SYSTEM",
                  lastUpdatedDateTime = null,
                  lastUpdatedByUsername = null,
                ),
                Identifier(
                  offenderId = 10000,
                  sequence = 2,
                  type = CodeDescription("CRO", "CRO Number"),
                  identifier = "123456/01A",
                  verified = false,
                  createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
                  createdByUsername = "SYSTEM",
                  lastUpdatedDateTime = null,
                  lastUpdatedByUsername = null,
                ),
              ),
              createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
              createdByUsername = "SYSTEM",
              lastUpdatedDateTime = null,
              lastUpdatedByUsername = null,
            ),
            CoreOffender(
              offenderId = 10001,
              firstName = "JOHNNY",
              lastName = "SMITHE",
              workingName = false,
              identifiers = emptyList(),
              createdDateTime = LocalDateTime.parse("2001-03-03T00:00:00"),
              createdByUsername = "SYSTEM",
              lastUpdatedDateTime = null,
              lastUpdatedByUsername = null,
            ),
          ),
        )
        aliasIdentifierMappingApiMockServer.stubGetMappingsFromNomisIds(
          CorePersonAliasIdentifierMappingsResult(
            aliases = listOf(
              OffenderAliasMappingDto(
                cprId = "cpr-alias-10000",
                nomisOffenderId = 10000,
                nomisPrisonNumber = prisonNumber,
                mappingType = OffenderAliasMappingDto.MappingType.MIGRATED,
              ),
            ),
            identifiers = listOf(
              OffenderIdentifierMappingDto(
                cprId = "cpr-identifier-10000-1",
                nomisOffenderId = 10000,
                nomisIdentifierSequence = 1,
                nomisPrisonNumber = prisonNumber,
                mappingType = OffenderIdentifierMappingDto.MappingType.MIGRATED,
              ),
            ),
          ),
        )
        cprApiMock.stubMigrateAliasesAndIdentifiers(nomisPrisonNumber = prisonNumber)
        aliasIdentifierMappingApiMockServer.stubReplaceMappings()

        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/aliases-identifiers/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will request existing mappings for all the NOMIS ids`() {
        val request: CorePersonAliasIdentifierMappingsByNomisIdsRequest =
          MappingApiExtension.getRequestBody(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/core-person/alias-identifier/mappings/by-nomis-ids")))

        assertThat(request.aliases).containsExactly(10000L, 10001L)
        assertThat(request.identifiers).containsExactly(
          IdentifierByNomisIdRequest(nomisId = 10000, nomisSequence = 1),
          IdentifierByNomisIdRequest(nomisId = 10000, nomisSequence = 2),
        )
      }

      @Test
      fun `will add existing CPR ids to the request sent to CPR`() {
        val request: PrisonAliasesAndIdentifiersRequest =
          CorePersonCprApiExtension.getRequestBody(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/syscon-sync/aliases-identifiers/$prisonNumber")))

        val alias = request.aliases.single { it.nomisOffenderId == 10000L }
        assertThat(alias.cprAliasId).isEqualTo("cpr-alias-10000")
        assertThat(alias.identifiers.single { it.nomisIdentifierId.nomisSequence == 1 }.cprIdentifierId).isEqualTo("cpr-identifier-10000-1")
        assertThat(alias.identifiers.single { it.nomisIdentifierId.nomisSequence == 2 }.cprIdentifierId).isNull()
        assertThat(request.aliases.single { it.nomisOffenderId == 10001L }.cprAliasId).isNull()
      }

      @Test
      fun `will replace mappings`() {
        aliasIdentifierMappingApiMockServer.verify(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/core-person/alias-identifier/replace")))
      }
    }

    @Nested
    inner class UnmappedCode {
      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetAliasesAndIdentifiers(
          prisonNumber,
          aliasesAndIdentifiers = corePerson(prisonNumber).offenders!!.map { alias ->
            alias.copy(identifiers = alias.identifiers.map { it.copy(type = CodeDescription("STAFF", "Staff Pass/ Identity Card")) })
          },
        )
        aliasIdentifierMappingApiMockServer.stubGetMappingsFromNomisIds()

        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/aliases-identifiers/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().is5xxServerError
      }

      @Test
      fun `will not send aliases and identifiers to CPR`() {
        cprApiMock.verify(0, WireMock.postRequestedFor(WireMock.anyUrl()))
      }

      @Test
      fun `will not replace any mappings`() {
        MappingApiExtension.mappingApi.verify(0, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/core-person/alias-identifier/replace")))
      }

      @Test
      fun `will not track telemetry for the repair`() {
        verifyNoInteractions(telemetryClient)
      }
    }

    @Nested
    inner class HappyPathNotFound {
      @BeforeEach
      fun setUp() {
        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/aliases-identifiers/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
      }

      @Test
      fun `will try to retrieve current aliases and identifiers for the prisoner`() {
        nomisApiMockServer.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/core-person/$prisonNumber/aliases-identifiers")))
      }

      @Test
      fun `will not send aliases and identifiers to CPR`() {
        cprApiMock.verify(0, WireMock.postRequestedFor(WireMock.anyUrl()))
      }

      @Test
      fun `will not replace any mappings`() {
        MappingApiExtension.mappingApi.verify(0, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/core-person/alias-identifier/replace")))
      }

      @Test
      fun `will not track telemetry for the repair`() {
        verifyNoInteractions(telemetryClient)
      }
    }
  }
}
