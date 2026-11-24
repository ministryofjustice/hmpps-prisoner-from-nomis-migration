package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAlias
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonMerge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressUsageMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressesAndContactsResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconContactMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconPersonMergeResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingDeletedEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingReassignedEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.countAllMessagesOnDLQQueue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AddressUsageByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMergeMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressUsageMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto.NomisContactType.EMAIL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto.NomisContactType.PHONE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsByNomisIdsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IdentifierByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorePersonSynchronisationIntTest(
  @Autowired private val corePersonNomisApiMock: CorePersonNomisApiMockServer,
  @Autowired private val corePersonMappingApiMock: CorePersonMappingApiMockServer,
  @Autowired private val aliasIdentifierMappingApiMock: CorePersonAliasIdentifierMappingApiMockServer,
) : CorePersonIntegrationTestBase() {
  override fun resetTelemetryClient() {}

  private fun setupSynchronisationTest() {
    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    CorePersonCprApiExtension.resetAndDisableResetBeforeEach()
    tearDownTelemetryClient()
  }

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  @Nested
  @DisplayName("prison-offender-events.prisoner.merged")
  inner class PrisonerMerge {
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      val bookingId = 1234567L
      val offenderNo = "A1234KT"
      val removedOffenderNo = "A1000KT"

      @BeforeAll
      fun setUp() {
        setupSynchronisationTest()
        corePersonNomisApiMock.stubGetAddressesAndContacts(
          prisonNumber = offenderNo,
          addressesAndContacts = CorePersonAddressContact(
            addresses = emptyList(),
            phoneNumbers = emptyList(),
            emailAddresses = emptyList(),
          ),
        )
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(prisonNumber = offenderNo, aliasesAndIdentifiers = emptyList())
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
        waitForAnyProcessingToComplete("coreperson-prisoner-merge-synchronisation-success")
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-prisoner-merge-synchronisation-success"),
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
                  },
                  "toPrisonAliasesAndIdentifiersRequest": {
                    "aliases": []
                  }
                }
                """.trimIndent(),
              ),
            ),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathWithExistingMappings {
      val bookingId = 1234567L
      val offenderNo = "A1234KT"
      val removedOffenderNo = "A1000KT"

      @BeforeAll
      fun setUp() {
        setupSynchronisationTest()
        corePersonNomisApiMock.stubGetAddressesAndContacts(prisonNumber = offenderNo)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(prisonNumber = offenderNo)
        corePersonMappingApiMock.stubGetMappingsFromNomisIds(
          CorePersonMappingsResult(
            aliases = listOf(
              OffenderAliasMappingDto(
                cprId = "cpr-alias-1",
                nomisOffenderId = 1,
                nomisPrisonNumber = offenderNo,
                mappingType = OffenderAliasMappingDto.MappingType.MIGRATED,
              ),
            ),
            identifiers = listOf(
              OffenderIdentifierMappingDto(
                cprId = "cpr-identifier-1-1",
                nomisOffenderId = 1,
                nomisIdentifierSequence = 1,
                nomisPrisonNumber = offenderNo,
                mappingType = OffenderIdentifierMappingDto.MappingType.MIGRATED,
              ),
            ),
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
        waitForAnyProcessingToComplete("coreperson-prisoner-merge-synchronisation-success")
      }

      @Test
      fun `will merge the mappings returned from CPR`() {
        val request: CorePersonAddressContactMergeMappingsDto =
          MappingApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/core-person/merge")))

        assertThat(request.toNomisPrisonNumber).isEqualTo(offenderNo)
        assertThat(request.fromNomisPrisonNumber).isEqualTo(removedOffenderNo)
        with(request.addresses.single()) {
          assertThat(nomisId).isEqualTo(12345L)
          assertThat(cprId).isEqualTo("cpr-address-12345")
          assertThat(nomisPrisonNumber).isEqualTo(offenderNo)
          assertThat(mappingType).isEqualTo(CorePersonAddressMappingDto.MappingType.NOMIS_CREATED)
        }
        with(request.addressUsages.single()) {
          assertThat(nomisId).isEqualTo(10000L)
          assertThat(addressUsageCode).isEqualTo("HOME")
          assertThat(cprId).isEqualTo("cpr-usage-10000")
          assertThat(mappingType).isEqualTo(CorePersonAddressUsageMappingDto.MappingType.NOMIS_CREATED)
        }
        val contacts = request.contacts
        assertThat(contacts.filter { it.nomisId == 20000L && it.nomisContactType == PHONE && it.cprId == "cpr-phone-20000" }).isNotEmpty()
        assertThat(contacts.filter { it.nomisId == 30000L && it.nomisContactType == PHONE && it.cprId == "cpr-phone-30000" }).isNotEmpty()
        assertThat(contacts.filter { it.nomisId == 40000L && it.nomisContactType == EMAIL && it.cprId == "cpr-email-40000" }).isNotEmpty()
      }

      @Test
      fun `will request existing mappings for all the NOMIS ids`() {
        val request: CorePersonMappingsByNomisIdsRequest =
          MappingApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/core-person/merge/mappings/by-nomis-ids")))

        assertThat(request.aliases).containsExactly(1L)
        assertThat(request.identifiers).containsExactly(IdentifierByNomisIdRequest(nomisId = 1, nomisSequence = 1))

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
        assertThat(request.fromPrisonNumber).isEqualTo(removedOffenderNo)
        with(request.toPrisonAddressesAndContactsRequest) {
          with(addresses.single()) {
            assertThat(nomisAddressId).isEqualTo(12345L)
            assertThat(nomisAddressId).isEqualTo(12345L)
            assertThat(cprAddressId).isEqualTo("cpr-address-12345")
            with(addressUsage.single()) {
              assertThat(nomisAddressUsageId).isEqualTo(10000L)
              assertThat(cprAddressUsageId).isEqualTo("cpr-usage-10000")
            }
            with(contacts.single()) {
              assertThat(nomisContactId).isEqualTo(20000L)
              assertThat(cprContactId).isEqualTo("cpr-phone-20000")
            }
          }
          val phoneContact = contacts.first { it.nomisContactId == 30000L }
          assertThat(phoneContact.type).isEqualTo(PrisonContact.Type.MOBILE)
          assertThat(phoneContact.cprContactId).isEqualTo("cpr-phone-30000")
          val emailContact = contacts.first { it.nomisContactId == 40000L }
          assertThat(emailContact.type).isEqualTo(PrisonContact.Type.EMAIL)
          assertThat(emailContact.cprContactId).isEqualTo("cpr-email-40000")
        }
      }

      @Test
      fun `will send the NOMIS addresses and contacts to the merge request`() {
        val request: PrisonMerge =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/person/$offenderNo/merge")))
        with(request.toPrisonAddressesAndContactsRequest) {
          assertThat(addresses.size).isEqualTo(1)
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
          assertThat(contacts[0].nomisContactId).isEqualTo(30000L)
          assertThat(contacts[0].type).isEqualTo(PrisonContact.Type.MOBILE)
          assertThat(contacts[0].value).isEqualTo("07700 900123")
          assertThat(contacts[1].nomisContactId).isEqualTo(40000L)
          assertThat(contacts[1].type).isEqualTo(PrisonContact.Type.EMAIL)
          assertThat(contacts[1].value).isEqualTo("test@example.com")
        }
      }

      @Test
      fun `will add existing CPR alias and identifier ids to the merge request`() {
        val request: PrisonMerge =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/person/$offenderNo/merge")))
        with(request.toPrisonAliasesAndIdentifiersRequest.aliases.single()) {
          assertThat(nomisOffenderId).isEqualTo(1L)
          assertThat(cprAliasId).isEqualTo("cpr-alias-1")
          with(identifiers.single()) {
            assertThat(nomisIdentifierId.nomisOffenderId).isEqualTo(1L)
            assertThat(nomisIdentifierId.nomisSequence).isEqualTo(1)
            assertThat(cprIdentifierId).isEqualTo("cpr-identifier-1-1")
          }
        }
      }

      @Test
      fun `will send the NOMIS aliases and identifiers to the merge request`() {
        val request: PrisonMerge =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/person/$offenderNo/merge")))
        with(request.toPrisonAliasesAndIdentifiersRequest.aliases.single()) {
          assertThat(isPrimary).isTrue()
          assertThat(titleCode).isEqualTo(PrisonAlias.TitleCode.MR)
          assertThat(firstName).isEqualTo("JOHN")
          assertThat(middleNames).isEqualTo("FRED JAMES")
          assertThat(lastName).isEqualTo("SMITH")
          assertThat(dateOfBirth).isEqualTo(LocalDate.parse("1980-01-01"))
          assertThat(birthPlace).isEqualTo("LONDON")
          assertThat(sexCode).isEqualTo(PrisonAlias.SexCode.M)
          with(identifiers.single()) {
            assertThat(type).isEqualTo(PrisonIdentifier.Type.PNC)
            assertThat(value).isEqualTo("20/0071818T")
            assertThat(comment).isEqualTo("Met Police")
            assertThat(verified).isTrue()
          }
        }
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathWithNoExistingMappings {
      val bookingId = 1234567L
      val offenderNo = "A1234KT"
      val removedOffenderNo = "A1000KT"

      @BeforeAll
      fun setUp() {
        setupSynchronisationTest()
        corePersonNomisApiMock.stubGetAddressesAndContacts(prisonNumber = offenderNo)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(prisonNumber = offenderNo)
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
        waitForAnyProcessingToComplete("coreperson-prisoner-merge-synchronisation-success")
      }

      @Test
      fun `will send the merge request without any CPR ids`() {
        val request: PrisonMerge =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/person/$offenderNo/merge")))

        val toPrisonAddressesAndContactsRequest = request.toPrisonAddressesAndContactsRequest
        assertThat(request.fromPrisonNumber).isEqualTo(removedOffenderNo)
        with(toPrisonAddressesAndContactsRequest.addresses.single()) {
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
        val contacts = toPrisonAddressesAndContactsRequest.contacts
        assertThat(contacts.map { it.nomisContactId }).containsExactlyInAnyOrder(30000L, 40000L)
        assertThat(contacts.map { it.cprContactId }).containsOnlyNulls()

        with(request.toPrisonAliasesAndIdentifiersRequest.aliases.single()) {
          assertThat(nomisOffenderId).isEqualTo(1L)
          assertThat(cprAliasId).isNull()
          with(identifiers.single()) {
            assertThat(nomisIdentifierId.nomisOffenderId).isEqualTo(1L)
            assertThat(nomisIdentifierId.nomisSequence).isEqualTo(1)
            assertThat(cprIdentifierId).isNull()
          }
        }
      }

      @Test
      fun `will merge the mappings`() {
        corePersonMappingApiMock.verify(postRequestedFor(urlPathEqualTo("/mapping/core-person/merge")))
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-prisoner-merge-synchronisation-success"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenNomisAliasesAndIdentifiersFails {
      val bookingId = 1234567L
      val offenderNo = "A1234KT"
      val removedOffenderNo = "A1000KT"

      @BeforeEach
      fun setUp() {
        setupSynchronisationTest()
        corePersonNomisApiMock.stubGetAddressesAndContacts(prisonNumber = offenderNo)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(prisonNumber = offenderNo, status = HttpStatus.INTERNAL_SERVER_ERROR)
        corePersonMappingApiMock.stubGetMappingsFromNomisIds()
        corePersonCprApiMockServer.stubProcessPrisonMerge(offenderNo)
        corePersonMappingApiMock.stubMerge()
        awsSqsCorePersonOffenderEventsClient.sendMessage(
          corePersonQueueOffenderEventsUrl,
          mergeDomainEvent(bookingId = bookingId, offenderNo = offenderNo, removedOffenderNo = removedOffenderNo),
        )
        waitForAnyProcessingToComplete("coreperson-prisoner-merge-synchronisation-error", times = 2)
      }

      @Test
      fun `will retry and send the event to the DLQ without calling CPR or merging the mappings`() {
        corePersonNomisApiMock.verify(2, getRequestedFor(urlPathEqualTo("/core-person/$offenderNo/aliases-identifiers")))
        corePersonMappingApiMock.verify(0, postRequestedFor(urlPathEqualTo("/mapping/core-person/merge/mappings/by-nomis-ids")))
        corePersonCprApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/syscon-sync/person/$offenderNo/merge")))
        corePersonMappingApiMock.verify(0, postRequestedFor(urlPathEqualTo("/mapping/core-person/merge")))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("coreperson-prisoner-merge-synchronisation-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["removedOffenderNo"]).isEqualTo(removedOffenderNo)
            assertThat(it["error"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCprFails {
      val bookingId = 1234567L
      val offenderNo = "A1234KT"
      val removedOffenderNo = "A1000KT"

      @BeforeEach
      fun setUp() {
        reset(telemetryClient)
        corePersonNomisApiMock.stubGetAddressesAndContacts(prisonNumber = offenderNo)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(prisonNumber = offenderNo)
        corePersonMappingApiMock.stubGetMappingsFromNomisIds()
        corePersonCprApiMockServer.stubProcessPrisonMerge(offenderNo, status = HttpStatus.INTERNAL_SERVER_ERROR)
        awsSqsCorePersonOffenderEventsClient.sendMessage(
          corePersonQueueOffenderEventsUrl,
          mergeDomainEvent(bookingId = bookingId, offenderNo = offenderNo, removedOffenderNo = removedOffenderNo),
        )
        waitForAnyProcessingToComplete("coreperson-prisoner-merge-synchronisation-error", times = 2)
      }

      @Test
      fun `will retry and send the event to the DLQ without merging the mappings`() {
        corePersonCprApiMockServer.verify(2, postRequestedFor(urlPathEqualTo("/syscon-sync/person/$offenderNo/merge")))
        corePersonMappingApiMock.verify(0, postRequestedFor(urlPathEqualTo("/mapping/core-person/merge")))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("coreperson-prisoner-merge-synchronisation-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["removedOffenderNo"]).isEqualTo(removedOffenderNo)
            assertThat(it["error"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingMergeFails {
      val bookingId = 1234567L
      val offenderNo = "A1234KT"
      val removedOffenderNo = "A1000KT"

      @BeforeEach
      fun setUp() {
        reset(telemetryClient)
        corePersonNomisApiMock.stubGetAddressesAndContacts(prisonNumber = offenderNo)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(prisonNumber = offenderNo)
        corePersonMappingApiMock.stubGetMappingsFromNomisIds()
        corePersonCprApiMockServer.stubProcessPrisonMerge(offenderNo)
        corePersonMappingApiMock.stubMerge(status = HttpStatus.INTERNAL_SERVER_ERROR)
        awsSqsCorePersonOffenderEventsClient.sendMessage(
          corePersonQueueOffenderEventsUrl,
          mergeDomainEvent(bookingId = bookingId, offenderNo = offenderNo, removedOffenderNo = removedOffenderNo),
        )
        waitForAnyProcessingToComplete("coreperson-prisoner-merge-synchronisation-error", times = 2)
      }

      @Test
      fun `will retry the whole merge and send the event to the DLQ`() {
        corePersonCprApiMockServer.verify(2, postRequestedFor(urlPathEqualTo("/syscon-sync/person/$offenderNo/merge")))
        corePersonMappingApiMock.verify(2, postRequestedFor(urlPathEqualTo("/mapping/core-person/merge")))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("coreperson-prisoner-merge-synchronisation-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["error"]).isNotNull()
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("BOOKING-DELETED")
  inner class BookingDeleted {
    private val bookingId = 1234567L
    private val offenderNo = "A1234KT"
    private val offenderId = 123456L
    private val cprAliasId = "cpr-alias-id"
    private val primaryNameUrl = "/syscon-sync/person/$offenderNo/primary-name/$cprAliasId"

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      @BeforeAll
      fun setUp() {
        setupSynchronisationTest()

        corePersonNomisApiMock.stubGetWorkingName(prisonNumber = offenderNo, offenderId = offenderId)
        aliasIdentifierMappingApiMock.stubGetByNomisAliasIdOrNull(
          nomisOffenderId = offenderId,
          mapping = aliasMapping(),
        )
        corePersonCprApiMockServer.stubSyncPrimaryName(prisonNumber = offenderNo, cprAliasId = cprAliasId)
        sendBookingDeletedEvent()
          .also { waitForAnyProcessingToComplete("coreperson-booking-deleted-synchronisation-success") }
      }

      @Test
      fun `will get the working name from NOMIS`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/core-person/$offenderNo/working-name")))
      }

      @Test
      fun `will get the alias mapping for the working name`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/alias/nomis-offender-id/$offenderId")))
      }

      @Test
      fun `will set the primary name in CPR`() {
        corePersonCprApiMockServer.verify(postRequestedFor(urlPathEqualTo(primaryNameUrl)))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-booking-deleted-synchronisation-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisOffenderId"]).isEqualTo(offenderId.toString())
            assertThat(it["cprAliasId"]).isEqualTo(cprAliasId)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenNomisFails {
      @BeforeEach
      fun setUp() {
        setupSynchronisationTest()
        corePersonNomisApiMock.stubGetWorkingName(prisonNumber = offenderNo, status = HttpStatus.INTERNAL_SERVER_ERROR)
        sendBookingDeletedEvent()
          .also { waitForAnyProcessingToComplete("coreperson-booking-deleted-synchronisation-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ without updating CPR`() {
        mappingApi.verify(0, getRequestedFor(urlPathMatching("/mapping/core-person/alias/.*")))
        corePersonCprApiMockServer.verify(0, postRequestedFor(urlPathEqualTo(primaryNameUrl)))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("coreperson-booking-deleted-synchronisation-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it).doesNotContainKey("nomisOffenderId")
            assertThat(it).doesNotContainKey("cprAliasId")
            assertThat(it["error"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        setupSynchronisationTest()
        corePersonNomisApiMock.stubGetWorkingName(prisonNumber = offenderNo, offenderId = offenderId)
        aliasIdentifierMappingApiMock.stubGetByNomisAliasIdOrNull(nomisOffenderId = offenderId, mapping = null)
        sendBookingDeletedEvent()
          .also { waitForAnyProcessingToComplete("coreperson-booking-deleted-synchronisation-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ without updating CPR`() {
        corePersonCprApiMockServer.verify(0, postRequestedFor(urlPathEqualTo(primaryNameUrl)))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("coreperson-booking-deleted-synchronisation-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisOffenderId"]).isEqualTo(offenderId.toString())
            assertThat(it).doesNotContainKey("cprAliasId")
            assertThat(it["error"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCprFails {
      @BeforeEach
      fun setUp() {
        setupSynchronisationTest()
        corePersonNomisApiMock.stubGetWorkingName(prisonNumber = offenderNo, offenderId = offenderId)
        aliasIdentifierMappingApiMock.stubGetByNomisAliasIdOrNull(
          nomisOffenderId = offenderId,
          mapping = aliasMapping(),
        )
        corePersonCprApiMockServer.stubSyncPrimaryName(
          prisonNumber = offenderNo,
          cprAliasId = cprAliasId,
          status = HttpStatus.INTERNAL_SERVER_ERROR,
        )
        sendBookingDeletedEvent()
          .also { waitForAnyProcessingToComplete("coreperson-booking-deleted-synchronisation-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ`() {
        corePersonCprApiMockServer.verify(2, postRequestedFor(urlPathEqualTo(primaryNameUrl)))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("coreperson-booking-deleted-synchronisation-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisOffenderId"]).isEqualTo(offenderId.toString())
            assertThat(it["cprAliasId"]).isEqualTo(cprAliasId)
            assertThat(it["error"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    private fun sendBookingDeletedEvent() = awsSqsCorePersonOffenderEventsClient.sendMessage(
      corePersonQueueOffenderEventsUrl,
      bookingDeletedEvent(bookingId = bookingId, offenderNo = offenderNo),
    )

    private fun aliasMapping() = OffenderAliasMappingDto(
      cprId = cprAliasId,
      nomisOffenderId = offenderId,
      nomisPrisonNumber = offenderNo,
      mappingType = OffenderAliasMappingDto.MappingType.MIGRATED,
    )
  }

  @Nested
  @DisplayName("OFFENDER_BOOKING-REASSIGNED")
  inner class BookingReassigned {
    private val bookingId = 1234567L
    private val offenderNo = "A1234KT"
    private val offenderId = 123456L
    private val previousOffenderNo = "A1000KT"
    private val previousOffenderId = 100000L
    private val workingNameOffenderId = 100001L
    private val cprAliasId = "cpr-alias-id"
    private val primaryNameUrl = "/syscon-sync/person/$previousOffenderNo/primary-name/$cprAliasId"

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      @BeforeAll
      fun setUp() {
        setupSynchronisationTest()

        corePersonNomisApiMock.stubGetWorkingName(prisonNumber = previousOffenderNo, offenderId = workingNameOffenderId)
        aliasIdentifierMappingApiMock.stubGetByNomisAliasIdOrNull(
          nomisOffenderId = workingNameOffenderId,
          mapping = aliasMapping(),
        )
        corePersonCprApiMockServer.stubSyncPrimaryName(prisonNumber = previousOffenderNo, cprAliasId = cprAliasId)
        sendBookingReassignedEvent()
          .also { waitForAnyProcessingToComplete("coreperson-booking-reassigned-synchronisation-success") }
      }

      @Test
      fun `will get the working name from NOMIS for the previous prisoner`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/core-person/$previousOffenderNo/working-name")))
      }

      @Test
      fun `will get the alias mapping for the working name`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/alias/nomis-offender-id/$workingNameOffenderId")))
      }

      @Test
      fun `will set the primary name in CPR for the previous prisoner`() {
        corePersonCprApiMockServer.verify(postRequestedFor(urlPathEqualTo(primaryNameUrl)))
      }

      @Test
      fun `will not update the primary name for the new prisoner`() {
        corePersonCprApiMockServer.verify(0, postRequestedFor(urlPathMatching("/syscon-sync/person/$offenderNo/primary-name/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-booking-reassigned-synchronisation-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["previousOffenderNo"]).isEqualTo(previousOffenderNo)
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["nomisOffenderId"]).isEqualTo(workingNameOffenderId.toString())
            assertThat(it["cprAliasId"]).isEqualTo(cprAliasId)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenNomisFails {
      @BeforeEach
      fun setUp() {
        setupSynchronisationTest()
        corePersonNomisApiMock.stubGetWorkingName(prisonNumber = previousOffenderNo, status = HttpStatus.INTERNAL_SERVER_ERROR)
        sendBookingReassignedEvent()
          .also { waitForAnyProcessingToComplete("coreperson-booking-reassigned-synchronisation-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ without updating CPR`() {
        mappingApi.verify(0, getRequestedFor(urlPathMatching("/mapping/core-person/alias/.*")))
        corePersonCprApiMockServer.verify(0, postRequestedFor(urlPathMatching("/syscon-sync/person/.*/primary-name/.*")))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("coreperson-booking-reassigned-synchronisation-error"),
          check {
            assertThat(it["previousOffenderNo"]).isEqualTo(previousOffenderNo)
            assertThat(it).doesNotContainKey("nomisOffenderId")
            assertThat(it).doesNotContainKey("cprAliasId")
            assertThat(it["error"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        setupSynchronisationTest()
        corePersonNomisApiMock.stubGetWorkingName(prisonNumber = previousOffenderNo, offenderId = workingNameOffenderId)
        aliasIdentifierMappingApiMock.stubGetByNomisAliasIdOrNull(nomisOffenderId = workingNameOffenderId, mapping = null)
        sendBookingReassignedEvent()
          .also { waitForAnyProcessingToComplete("coreperson-booking-reassigned-synchronisation-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ without updating CPR`() {
        corePersonCprApiMockServer.verify(0, postRequestedFor(urlPathMatching("/syscon-sync/person/.*/primary-name/.*")))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("coreperson-booking-reassigned-synchronisation-error"),
          check {
            assertThat(it["previousOffenderNo"]).isEqualTo(previousOffenderNo)
            assertThat(it["nomisOffenderId"]).isEqualTo(workingNameOffenderId.toString())
            assertThat(it).doesNotContainKey("cprAliasId")
            assertThat(it["error"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCprFails {
      @BeforeEach
      fun setUp() {
        setupSynchronisationTest()
        corePersonNomisApiMock.stubGetWorkingName(prisonNumber = previousOffenderNo, offenderId = workingNameOffenderId)
        aliasIdentifierMappingApiMock.stubGetByNomisAliasIdOrNull(
          nomisOffenderId = workingNameOffenderId,
          mapping = aliasMapping(),
        )
        corePersonCprApiMockServer.stubSyncPrimaryName(
          prisonNumber = previousOffenderNo,
          cprAliasId = cprAliasId,
          status = HttpStatus.INTERNAL_SERVER_ERROR,
        )
        sendBookingReassignedEvent()
          .also { waitForAnyProcessingToComplete("coreperson-booking-reassigned-synchronisation-error", times = 2) }
      }

      @Test
      fun `will retry and send the event to the DLQ`() {
        corePersonCprApiMockServer.verify(2, postRequestedFor(urlPathEqualTo(primaryNameUrl)))
        await untilAsserted {
          assertThat(corePersonOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1)
        }
      }

      @Test
      fun `will track the error for each attempt`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("coreperson-booking-reassigned-synchronisation-error"),
          check {
            assertThat(it["previousOffenderNo"]).isEqualTo(previousOffenderNo)
            assertThat(it["cprAliasId"]).isEqualTo(cprAliasId)
            assertThat(it["error"]).isNotNull()
          },
          isNull(),
        )
      }
    }

    private fun sendBookingReassignedEvent() = awsSqsCorePersonOffenderEventsClient.sendMessage(
      corePersonQueueOffenderEventsUrl,
      bookingReassignedEvent(
        bookingId = bookingId,
        offenderId = offenderId,
        offenderNo = offenderNo,
        previousOffenderId = previousOffenderId,
        previousOffenderNo = previousOffenderNo,
      ),
    )

    private fun aliasMapping() = OffenderAliasMappingDto(
      cprId = cprAliasId,
      nomisOffenderId = workingNameOffenderId,
      nomisPrisonNumber = previousOffenderNo,
      mappingType = OffenderAliasMappingDto.MappingType.MIGRATED,
    )
  }
}
