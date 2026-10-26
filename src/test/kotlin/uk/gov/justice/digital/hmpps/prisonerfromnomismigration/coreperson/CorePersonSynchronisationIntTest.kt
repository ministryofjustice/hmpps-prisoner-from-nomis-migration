package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonMerge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressUsageMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressesAndContactsResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconContactMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconPersonMergeResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AddressUsageByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressUsageMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto.NomisContactType.EMAIL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto.NomisContactType.PHONE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsByNomisIdsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMergeMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension

class CorePersonSynchronisationIntTest(
  @Autowired private val corePersonNomisApiMock: CorePersonNomisApiMockServer,
  @Autowired private val corePersonMappingApiMock: CorePersonMappingApiMockServer,
) : CorePersonIntegrationTestBase() {
  @Nested
  @DisplayName("prison-offender-events.prisoner.merged")
  inner class PrisonerMerge {
    @Nested
    inner class HappyPath {
      val bookingId = 1234567L
      val offenderNo = "A1234KT"
      val removedOffenderNo = "A1000KT"

      @BeforeEach
      fun setUp() {
        corePersonNomisApiMock.stubGetAddressesAndContacts(
          prisonNumber = offenderNo,
          addressesAndContacts = CorePersonAddressContact(
            addresses = emptyList(),
            phoneNumbers = emptyList(),
            emailAddresses = emptyList(),
          ),
        )
        corePersonMappingApiMock.stubGetMappingsFromNomisIds()
        corePersonCprApiMockServer.stubProcessPrisonMerge(offenderNo)
        corePersonMappingApiMock.stubMerge()
        awsSqsCorePersonOffenderEventsClient.sendMessage(
          corePersonQueueOffenderEventsUrl,
          mergeDomainEvent(
            bookingId = bookingId,
            offenderNo = offenderNo,
            removedOffenderNo = removedOffenderNo,
          ),
        )
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-prisoner-merge-synchronisation"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["removedOffenderNo"]).isEqualTo(removedOffenderNo)
          },
          isNull(),
        )
      }

      @Test
      fun `will call core person for the merge`() {
        corePersonCprApiMockServer.verify(
          postRequestedFor(urlMatching("/syscon-sync/person/$offenderNo/merge"))
            .withRequestBody(
              equalToJson(
                """
                {
                  "fromPrisonNumber": "$removedOffenderNo",
                  "toPrisonAddressesAndContactsRequest": {
                    "addresses": [],
                    "contacts": []
                  }
                }
                """.trimIndent(),
              ),
            ),
        )
      }
    }

    @Nested
    inner class HappyPathWithExistingMappings {
      val bookingId = 1234567L
      val offenderNo = "A1234KT"
      val removedOffenderNo = "A1000KT"

      @BeforeEach
      fun setUp() {
        corePersonNomisApiMock.stubGetAddressesAndContacts(prisonNumber = offenderNo)
        corePersonMappingApiMock.stubGetMappingsFromNomisIds(
          CorePersonMappingsResult(
            addresses = listOf(
              CorePersonAddressMappingDto(
                cprId = "cpr-address-12345",
                nomisId = 12345,
                nomisPrisonNumber = offenderNo,
                mappingType = CorePersonAddressMappingDto.MappingType.MIGRATED,
              ),
            ),
            addressUsages = listOf(
              CorePersonAddressUsageMappingDto(
                cprId = "cpr-usage-10000",
                nomisId = 10000,
                addressUsageCode = "HOME",
                nomisPrisonNumber = offenderNo,
                mappingType = CorePersonAddressUsageMappingDto.MappingType.MIGRATED,
              ),
            ),
            contacts = listOf(
              CorePersonContactMappingDto(
                cprId = "cpr-phone-20000",
                nomisId = 20000,
                nomisContactType = PHONE,
                nomisPrisonNumber = offenderNo,
                mappingType = MIGRATED,
              ),
              CorePersonContactMappingDto(
                cprId = "cpr-phone-30000",
                nomisId = 30000,
                nomisContactType = PHONE,
                nomisPrisonNumber = offenderNo,
                mappingType = MIGRATED,
              ),
              CorePersonContactMappingDto(
                cprId = "cpr-email-40000",
                nomisId = 40000,
                nomisContactType = EMAIL,
                nomisPrisonNumber = offenderNo,
                mappingType = MIGRATED,
              ),
            ),
          ),
        )
        corePersonCprApiMockServer.stubProcessPrisonMerge(
          prisonNumber = offenderNo,
          response = SysconPersonMergeResponseBody(
            toPrisonNumber = offenderNo,
            sysconAddressesAndContactsResponseBody = SysconAddressesAndContactsResponseBody(
              prisonNumber = offenderNo,
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
          ),
        )
        corePersonMappingApiMock.stubMerge()
        awsSqsCorePersonOffenderEventsClient.sendMessage(
          corePersonQueueOffenderEventsUrl,
          mergeDomainEvent(
            bookingId = bookingId,
            offenderNo = offenderNo,
            removedOffenderNo = removedOffenderNo,
          ),
        )
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will merge the mappings returned from CPR`() {
        val request: CorePersonMergeMappingsDto =
          MappingApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/core-person/merge")))

        assertThat(request.toNomisPrisonNumber).isEqualTo(offenderNo)
        assertThat(request.fromNomisPrisonNumber).isEqualTo(removedOffenderNo)
        val address = request.addresses.single()
        assertThat(address.nomisId).isEqualTo(12345L)
        assertThat(address.cprId).isEqualTo("cpr-address-12345")
        assertThat(address.nomisPrisonNumber).isEqualTo(offenderNo)
        assertThat(address.mappingType).isEqualTo(CorePersonAddressMappingDto.MappingType.NOMIS_CREATED)

        val addressUsage = request.addressUsages.single()

        assertThat(addressUsage.nomisId).isEqualTo(10000L)
        assertThat(addressUsage.addressUsageCode).isEqualTo("HOME")
        assertThat(addressUsage.cprId).isEqualTo("cpr-usage-10000")
        assertThat(addressUsage.mappingType).isEqualTo(CorePersonAddressUsageMappingDto.MappingType.NOMIS_CREATED)
        val contacts = request.contacts
        assertThat(contacts.filter { it.nomisId == 20000L && it.nomisContactType == PHONE && it.cprId == "cpr-phone-20000" }).isNotEmpty()
        assertThat(contacts.filter { it.nomisId == 30000L && it.nomisContactType == PHONE && it.cprId == "cpr-phone-30000" }).isNotEmpty()
        assertThat(contacts.filter { it.nomisId == 40000L && it.nomisContactType == EMAIL && it.cprId == "cpr-email-40000" }).isNotEmpty()
      }

      @Test
      fun `will request existing mappings for all the NOMIS ids`() {
        val request: CorePersonMappingsByNomisIdsRequest =
          MappingApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/core-person/mappings/by-nomis-ids")))

        assertThat(request.addresses).containsExactly(12345L)
        assertThat(request.addressUsages).containsExactly(AddressUsageByNomisIdRequest(10000, "HOME"))
        assertThat(request.contacts).containsExactlyInAnyOrder(
          ContactByNomisIdRequest(20000, ContactByNomisIdRequest.NomisContactType.PHONE),
          ContactByNomisIdRequest(30000, ContactByNomisIdRequest.NomisContactType.PHONE),
          ContactByNomisIdRequest(40000, ContactByNomisIdRequest.NomisContactType.EMAIL),
        )
      }

      @Test
      fun `will add existing CPR ids to the merge request`() {
        val request: PrisonMerge =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/person/$offenderNo/merge")))
        val toPrisonAddressesAndContactsRequest = request.toPrisonAddressesAndContactsRequest!!
        val address = toPrisonAddressesAndContactsRequest.addresses.single()
        assertThat(request.fromPrisonNumber).isEqualTo(removedOffenderNo)
        assertThat(address.nomisAddressId).isEqualTo(12345L)
        assertThat(address.nomisAddressId).isEqualTo(12345L)
        assertThat(address.cprAddressId).isEqualTo("cpr-address-12345")
        val addressUsage = address.addressUsage.single()
        assertThat(addressUsage.nomisAddressUsageId).isEqualTo(10000L)
        assertThat(addressUsage.cprAddressUsageId).isEqualTo("cpr-usage-10000")
        val addressContact = address.contacts.single()
        assertThat(addressContact.nomisContactId).isEqualTo(20000L)
        assertThat(addressContact.cprContactId).isEqualTo("cpr-phone-20000")
        val contacts = toPrisonAddressesAndContactsRequest.contacts
        val phoneContact = contacts.first { it.nomisContactId == 30000L }
        assertThat(phoneContact.type).isEqualTo(PrisonContact.Type.MOBILE)
        assertThat(phoneContact.cprContactId).isEqualTo("cpr-phone-30000")
        val emailContact = contacts.first { it.nomisContactId == 40000L }
        assertThat(emailContact.type).isEqualTo(PrisonContact.Type.EMAIL)
        assertThat(emailContact.cprContactId).isEqualTo("cpr-email-40000")
      }

      @Test
      fun `will send the NOMIS addresses and contacts to the merge request`() {
        val request: PrisonMerge =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/person/$offenderNo/merge")))

        assertThat(request.toPrisonAddressesAndContactsRequest!!.addresses).hasSize(1)
        with(request.toPrisonAddressesAndContactsRequest.addresses.first()) {
          assertThat(nomisAddressId).isEqualTo(12345L)
          assertThat(isPrimary).isTrue()
          with(addressUsage.single()) {
            assertThat(nomisAddressUsageId).isEqualTo(10000L)
            assertThat(addressUsageCode.name).isEqualTo("HOME")
          }
          with(contacts.single()) {
            assertThat(nomisContactId).isEqualTo(20000L)
            assertThat(type).isEqualTo(PrisonContact.Type.HOME)
            assertThat(value).isEqualTo("0114 123 4567")
          }
          assertThat(subBuildingName).isEqualTo("Flat 2")
          assertThat(buildingName).isEqualTo("The Priory")
          assertThat(thoroughfareName).isEqualTo("Main Street")
          assertThat(dependentLocality).isEqualTo("Sheffield")
          assertThat(postcode).isEqualTo("S1 1AA")
        }
        with(request.toPrisonAddressesAndContactsRequest) {
          assertThat(contacts[0].nomisContactId).isEqualTo(30000L)
          assertThat(contacts[0].type).isEqualTo(PrisonContact.Type.MOBILE)
          assertThat(contacts[0].value).isEqualTo("07700 900123")
          assertThat(contacts[1].nomisContactId).isEqualTo(40000L)
          assertThat(contacts[1].type).isEqualTo(PrisonContact.Type.EMAIL)
          assertThat(contacts[1].value).isEqualTo("test@example.com")
        }
      }
    }

    @Nested
    inner class HappyPathWithNoExistingMappings {
      val bookingId = 1234567L
      val offenderNo = "A1234KT"
      val removedOffenderNo = "A1000KT"

      @BeforeEach
      fun setUp() {
        corePersonNomisApiMock.stubGetAddressesAndContacts(prisonNumber = offenderNo)
        corePersonMappingApiMock.stubGetMappingsFromNomisIds()
        corePersonCprApiMockServer.stubProcessPrisonMerge(offenderNo)
        corePersonMappingApiMock.stubMerge()
        awsSqsCorePersonOffenderEventsClient.sendMessage(
          corePersonQueueOffenderEventsUrl,
          mergeDomainEvent(
            bookingId = bookingId,
            offenderNo = offenderNo,
            removedOffenderNo = removedOffenderNo,
          ),
        )
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send the merge request without any CPR ids`() {
        val request: PrisonMerge =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/person/$offenderNo/merge")))
        val toPrisonAddressesAndContactsRequest = request.toPrisonAddressesAndContactsRequest!!
        val address = toPrisonAddressesAndContactsRequest.addresses.single()
        assertThat(request.fromPrisonNumber).isEqualTo(removedOffenderNo)
        assertThat(address.nomisAddressId).isEqualTo(12345L)
        assertThat(address.cprAddressId).isNull()
        val addressUsage = address.addressUsage.single()
        assertThat(addressUsage.nomisAddressUsageId).isEqualTo(10000L)
        assertThat(addressUsage.cprAddressUsageId).isNull()
        val addressContact = address.contacts.single()
        assertThat(addressContact.nomisContactId).isEqualTo(20000L)
        assertThat(addressContact.cprContactId).isNull()
        val contacts = toPrisonAddressesAndContactsRequest.contacts
        assertThat(contacts.map { it.nomisContactId }).containsExactlyInAnyOrder(30000L, 40000L)
        assertThat(contacts.map { it.cprContactId }).containsOnlyNulls()
      }

      @Test
      fun `will merge the mappings`() {
        corePersonMappingApiMock.verify(postRequestedFor(urlPathEqualTo("/mapping/core-person/merge")))
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-prisoner-merge-synchronisation"),
          any(),
          isNull(),
        )
      }
    }
  }
}
