package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.contact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.contactAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.contactAddressPhone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.contactEmail
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.contactIdentity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.contactPhone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.contactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.prisonerContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.prisonerContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactIdentityRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreatePrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncUpdateContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncUpdateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncUpdateContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncUpdateContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncUpdateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncUpdateContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncUpdatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncUpdatePrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactForPrisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactRestrictionEnteredStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate

class ContactPersonSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: ContactPersonNomisApiMockServer

  private val dpsApiMock = dpsContactPersonServer

  @Autowired
  private lateinit var mappingApiMock: ContactPersonMappingApiMockServer

  @Nested
  @DisplayName("PERSON-INSERTED")
  inner class PersonAdded {
    private val nomisPersonId = 123456L
    private val dpsContactId = 123456L

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personEvent(
            eventType = "PERSON-INSERTED",
            personId = nomisPersonId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create contact in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = nomisPersonId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson().copy(
            personId = nomisPersonId,
            firstName = "JOHN",
            lastName = "SMITH",
            middleName = "BOB",
            interpreterRequired = true,
            audit = NomisAudit(
              createUsername = "J.SPEAK",
              createDatetime = "2024-09-01T13:31",
            ),
            dateOfBirth = LocalDate.parse("2024-07-19"),
            gender = CodeDescription("M", "Male"),
            title = CodeDescription("MR", "Mr"),
            language = CodeDescription("EN", "English"),
            domesticStatus = CodeDescription("MAR", "Married"),
            isStaff = true,
            isRemitter = true,
          ),
        )
        dpsApiMock.stubCreateContact(contact().copy(id = dpsContactId))
        mappingApiMock.stubCreatePersonMapping()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personEvent(
            eventType = "PERSON-INSERTED",
            personId = nomisPersonId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists for person`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/person/nomis-person-id/$nomisPersonId")))
      }

      @Test
      fun `will retrieve the person details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will create the contact in DPS from the person`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/contact")))
        val createContactRequest: SyncCreateContactRequest = ContactPersonDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact")))
        with(createContactRequest) {
          assertThat(title).isEqualTo("MR")
          assertThat(lastName).isEqualTo("SMITH")
          assertThat(firstName).isEqualTo("JOHN")
          assertThat(middleName).isEqualTo("BOB")
          assertThat(dateOfBirth).isEqualTo(LocalDate.parse("2024-07-19"))
          assertThat(estimatedIsOverEighteen).isEqualTo(SyncCreateContactRequest.EstimatedIsOverEighteen.NO)
          assertThat(relationship).isNull()
          assertThat(isStaff).isTrue()
          assertThat(remitter).isTrue()
          assertThat(deceasedFlag).isFalse()
          assertThat(deceasedDate).isNull()
          assertThat(gender).isEqualTo("M")
          assertThat(domesticStatus).isEqualTo("MAR")
          assertThat(languageCode).isEqualTo("EN")
          assertThat(interpreterRequired).isTrue()
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/person"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactId)
            .withRequestBodyJsonPath("nomisId", "$nomisPersonId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(dpsContactId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = nomisPersonId, mapping = PersonMappingDto(dpsId = "$dpsContactId", nomisId = nomisPersonId, mappingType = PersonMappingDto.MappingType.NOMIS_CREATED))
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personEvent(
            eventType = "PERSON-INSERTED",
            personId = nomisPersonId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create contact in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(dpsContactId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = nomisPersonId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId),
        )
        dpsApiMock.stubCreateContact(contact().copy(id = dpsContactId))
        mappingApiMock.stubCreatePersonMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PersonMappingDto(
                dpsId = dpsContactId.toString(),
                nomisId = nomisPersonId,
                mappingType = NOMIS_CREATED,
              ),
              existing = PersonMappingDto(
                dpsId = "9999",
                nomisId = nomisPersonId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personEvent(
            eventType = "PERSON-INSERTED",
            personId = nomisPersonId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-contactperson-duplicate") }
      }

      @Test
      fun `will create the contact in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/person"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactId)
            .withRequestBodyJsonPath("nomisId", "$nomisPersonId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(dpsContactId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-contactperson-duplicate"),
          check {
            assertThat(it["existingNomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["existingDpsContactId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["duplicateDpsContactId"]).isEqualTo(dpsContactId.toString())
            assertThat(it["type"]).isEqualTo("PERSON")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = nomisPersonId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId),
        )
        dpsApiMock.stubCreateContact(contact().copy(id = dpsContactId))
        mappingApiMock.stubCreatePersonMappingFailureFollowedBySuccess()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personEvent(
            eventType = "PERSON-INSERTED",
            personId = nomisPersonId,
          ),
        ).also { waitForAnyProcessingToComplete("contactperson-person-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the contact in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/person"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactId)
            .withRequestBodyJsonPath("nomisId", "$nomisPersonId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(dpsContactId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PERSON-UPDATED")
  inner class PersonUpdated {
    private val nomisPersonId = 123456L
    private val dpsContactId = 123456L

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personEvent(
            eventType = "PERSON-UPDATED",
            personId = nomisPersonId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not updated contact in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/contact/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonId(
          nomisPersonId = nomisPersonId,
          mapping = PersonMappingDto(dpsId = dpsContactId.toString(), nomisId = nomisPersonId, mappingType = MIGRATED),
        )
        nomisApiMock.stubGetPerson(
          person = contactPerson().copy(
            personId = nomisPersonId,
            firstName = "JOHN",
            lastName = "SMITH",
            middleName = "BOB",
            interpreterRequired = true,
            audit = NomisAudit(
              createUsername = "J.SPEAK",
              createDatetime = "2024-09-01T13:31",
              modifyUserId = "T.SMITH",
              modifyDatetime = "2024-10-01T13:31",
            ),
            dateOfBirth = LocalDate.parse("1965-07-19"),
            gender = CodeDescription("M", "Male"),
            title = CodeDescription("MR", "Mr"),
            language = CodeDescription("EN", "English"),
            domesticStatus = CodeDescription("MAR", "Married"),
            isStaff = true,
            isRemitter = true,
          ),
        )
        dpsApiMock.stubUpdateContact(contactId = dpsContactId)

        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personEvent(
            eventType = "PERSON-UPDATED",
            personId = nomisPersonId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-synchronisation-updated-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(dpsContactId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will update the contact in DPS from the NOMIS person`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/contact/$dpsContactId")))
        val updateContactRequest: SyncUpdateContactRequest = ContactPersonDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact/$dpsContactId")))
        with(updateContactRequest) {
          assertThat(title).isEqualTo("MR")
          assertThat(lastName).isEqualTo("SMITH")
          assertThat(firstName).isEqualTo("JOHN")
          assertThat(middleName).isEqualTo("BOB")
          assertThat(dateOfBirth).isEqualTo(LocalDate.parse("1965-07-19"))
          assertThat(estimatedIsOverEighteen).isEqualTo(SyncUpdateContactRequest.EstimatedIsOverEighteen.YES)
          assertThat(isStaff).isTrue()
          assertThat(remitter).isTrue()
          assertThat(deceasedFlag).isFalse()
          assertThat(deceasedDate).isNull()
          assertThat(gender).isEqualTo("M")
          assertThat(domesticStatus).isEqualTo("MAR")
          assertThat(languageCode).isEqualTo("EN")
          assertThat(interpreterRequired).isTrue()
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }
    }
  }

  @Nested
  @DisplayName("PERSON-DELETED")
  inner class PersonDeleted {
    private val personId = 123456L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personEvent(
          eventType = "PERSON-DELETED",
          personId = personId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-synchronisation-deleted-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("ADDRESSES_PERSON-INSERTED")
  inner class PersonAddressAdded {
    private val nomisAddressId = 3456L
    private val nomisPersonId = 123456L
    private val dpsContactAddressId = 937373L

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressEvent(
            eventType = "ADDRESSES_PERSON-INSERTED",
            personId = nomisPersonId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create address in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact-address")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-address-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(nomisAddressId = nomisAddressId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withAddress(
              PersonAddress(
                addressId = nomisAddressId,
                phoneNumbers = emptyList(),
                comment = "nice area",
                validatedPAF = false,
                primaryAddress = true,
                mailAddress = true,
                noFixedAddress = false,
                type = CodeDescription("HOME", "Home Address"),
                flat = "Flat 1",
                premise = "Brown Court",
                locality = "Broomhill",
                street = "Broomhill Street",
                postcode = "S1 6GG",
                city = CodeDescription("12345", "Sheffield"),
                county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
                country = CodeDescription("GBR", "United Kingdom"),
                startDate = LocalDate.parse("2021-01-01"),
                endDate = LocalDate.parse("2025-01-01"),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactAddress(contactAddress().copy(contactAddressId = dpsContactAddressId))
        mappingApiMock.stubCreateAddressMapping()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressEvent(
            eventType = "ADDRESSES_PERSON-INSERTED",
            personId = nomisPersonId,
            addressId = nomisAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/address/nomis-address-id/$nomisAddressId")))
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will create the address in DPS from the person`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/contact-address")))
        val request: SyncCreateContactAddressRequest = ContactPersonDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact-address")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(addressType).isEqualTo("HOME")
          assertThat(primaryAddress).isTrue()
          assertThat(flat).isEqualTo("Flat 1")
          assertThat(property).isEqualTo("Brown Court")
          assertThat(street).isEqualTo("Broomhill Street")
          assertThat(area).isEqualTo("Broomhill")
          assertThat(cityCode).isEqualTo("12345")
          assertThat(countyCode).isEqualTo("S.YORKSHIRE")
          assertThat(countryCode).isEqualTo("GBR")
          assertThat(postcode).isEqualTo("S1 6GG")
          assertThat(verified).isNull()
          assertThat(mailFlag).isTrue()
          assertThat(startDate).isEqualTo(LocalDate.parse("2021-01-01"))
          assertThat(endDate).isEqualTo(LocalDate.parse("2025-01-01"))
          assertThat(noFixedAddress).isFalse()
          assertThat(comments).isEqualTo("nice area")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/address"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactAddressId)
            .withRequestBodyJsonPath("nomisId", "$nomisAddressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-address-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            assertThat(it["dpsContactAddressId"]).isEqualTo(dpsContactAddressId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(nomisAddressId = nomisAddressId, mapping = PersonAddressMappingDto(dpsId = "$dpsContactAddressId", nomisId = nomisAddressId, mappingType = PersonAddressMappingDto.MappingType.NOMIS_CREATED))
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressEvent(
            eventType = "ADDRESSES_PERSON-INSERTED",
            personId = nomisPersonId,
            addressId = nomisAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create address in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact-address")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-address-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            assertThat(it["dpsContactAddressId"]).isEqualTo(dpsContactAddressId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(nomisAddressId = nomisAddressId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withAddress(
              PersonAddress(
                addressId = nomisAddressId,
                phoneNumbers = emptyList(),
                comment = "nice area",
                validatedPAF = false,
                primaryAddress = true,
                mailAddress = true,
                noFixedAddress = false,
                type = CodeDescription("HOME", "Home Address"),
                flat = "Flat 1",
                premise = "Brown Court",
                locality = "Broomhill",
                street = "Broomhill Street",
                postcode = "S1 6GG",
                city = CodeDescription("12345", "Sheffield"),
                county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
                country = CodeDescription("GBR", "United Kingdom"),
                startDate = LocalDate.parse("2021-01-01"),
                endDate = LocalDate.parse("2025-01-01"),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactAddress(contactAddress().copy(contactAddressId = dpsContactAddressId))
        mappingApiMock.stubCreateAddressMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PersonAddressMappingDto(
                dpsId = dpsContactAddressId.toString(),
                nomisId = nomisAddressId,
                mappingType = PersonAddressMappingDto.MappingType.NOMIS_CREATED,
              ),
              existing = PersonAddressMappingDto(
                dpsId = "9999",
                nomisId = nomisAddressId,
                mappingType = PersonAddressMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressEvent(
            eventType = "ADDRESSES_PERSON-INSERTED",
            personId = nomisPersonId,
            addressId = nomisAddressId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-contactperson-duplicate") }
      }

      @Test
      fun `will create the address in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact-address")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/address"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactAddressId)
            .withRequestBodyJsonPath("nomisId", "$nomisAddressId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-address-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            assertThat(it["dpsContactAddressId"]).isEqualTo(dpsContactAddressId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-contactperson-duplicate"),
          check {
            assertThat(it["existingNomisAddressId"]).isEqualTo(nomisAddressId.toString())
            assertThat(it["existingDpsContactAddressId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisAddressId"]).isEqualTo(nomisAddressId.toString())
            assertThat(it["duplicateDpsContactAddressId"]).isEqualTo(dpsContactAddressId.toString())
            assertThat(it["type"]).isEqualTo("ADDRESS")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(nomisAddressId = nomisAddressId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withAddress(
              PersonAddress(
                addressId = nomisAddressId,
                phoneNumbers = emptyList(),
                comment = "nice area",
                validatedPAF = false,
                primaryAddress = true,
                mailAddress = true,
                noFixedAddress = false,
                type = CodeDescription("HOME", "Home Address"),
                flat = "Flat 1",
                premise = "Brown Court",
                locality = "Broomhill",
                street = "Broomhill Street",
                postcode = "S1 6GG",
                city = CodeDescription("12345", "Sheffield"),
                county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
                country = CodeDescription("GBR", "United Kingdom"),
                startDate = LocalDate.parse("2021-01-01"),
                endDate = LocalDate.parse("2025-01-01"),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactAddress(contactAddress().copy(contactAddressId = dpsContactAddressId))
        mappingApiMock.stubCreateAddressMappingFollowedBySuccess()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressEvent(
            eventType = "ADDRESSES_PERSON-INSERTED",
            personId = nomisPersonId,
            addressId = nomisAddressId,
          ),
        ).also { waitForAnyProcessingToComplete("contactperson-address-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the address in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact-address")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/address"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactAddressId)
            .withRequestBodyJsonPath("nomisId", "$nomisAddressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-address-synchronisation-created-success"),
          check {
            assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            assertThat(it["dpsContactAddressId"]).isEqualTo(dpsContactAddressId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("ADDRESSES_PERSON-UPDATED")
  inner class PersonAddressUpdated {
    private val nomisPersonId = 123456L
    private val nomisAddressId = 76543L
    private val dpsContactAddressId = 8847L

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressEvent(
            eventType = "ADDRESSES_PERSON-UPDATED",
            personId = nomisPersonId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update address in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/contact-address/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-address-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressId(
          nomisAddressId = nomisAddressId,
          mapping = PersonAddressMappingDto(dpsId = dpsContactAddressId.toString(), nomisId = nomisAddressId, mappingType = PersonAddressMappingDto.MappingType.MIGRATED),
        )
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withAddress(
              PersonAddress(
                addressId = nomisAddressId,
                phoneNumbers = emptyList(),
                comment = "nice area",
                validatedPAF = false,
                primaryAddress = true,
                mailAddress = true,
                noFixedAddress = false,
                type = CodeDescription("HOME", "Home Address"),
                flat = "Flat 1",
                premise = "Brown Court",
                locality = "Broomhill",
                street = "Broomhill Street",
                postcode = "S1 6GG",
                city = CodeDescription("12345", "Sheffield"),
                county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
                country = CodeDescription("GBR", "United Kingdom"),
                startDate = LocalDate.parse("2021-01-01"),
                endDate = LocalDate.parse("2025-01-01"),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                  modifyUserId = "T.SMITH",
                  modifyDatetime = "2024-10-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubUpdateContactAddress(addressId = dpsContactAddressId)

        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressEvent(
            eventType = "ADDRESSES_PERSON-UPDATED",
            personId = nomisPersonId,
            addressId = nomisAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-address-synchronisation-updated-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            assertThat(it["dpsContactAddressId"]).isEqualTo(dpsContactAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will update the contact address in DPS from the NOMIS address`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/contact-address/$dpsContactAddressId")))
        val request: SyncUpdateContactAddressRequest = ContactPersonDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact-address/$dpsContactAddressId")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(addressType).isEqualTo("HOME")
          assertThat(primaryAddress).isTrue()
          assertThat(flat).isEqualTo("Flat 1")
          assertThat(property).isEqualTo("Brown Court")
          assertThat(street).isEqualTo("Broomhill Street")
          assertThat(area).isEqualTo("Broomhill")
          assertThat(cityCode).isEqualTo("12345")
          assertThat(countyCode).isEqualTo("S.YORKSHIRE")
          assertThat(countryCode).isEqualTo("GBR")
          assertThat(postcode).isEqualTo("S1 6GG")
          assertThat(verified).isFalse()
          assertThat(mailFlag).isTrue()
          assertThat(startDate).isEqualTo(LocalDate.parse("2021-01-01"))
          assertThat(endDate).isEqualTo(LocalDate.parse("2025-01-01"))
          assertThat(noFixedAddress).isFalse()
          assertThat(comments).isEqualTo("nice area")
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }
    }
  }

  @Nested
  @DisplayName("ADDRESSES_PERSON-DELETED")
  inner class PersonAddressDeleted {
    private val personId = 123456L
    private val addressId = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personAddressEvent(
          eventType = "ADDRESSES_PERSON-DELETED",
          personId = personId,
          addressId = addressId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-address-synchronisation-deleted-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["addressId"]).isEqualTo(addressId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("PHONES_PERSON-INSERTED")
  inner class PersonPhoneAdded {
    private val nomisPhoneId = 3456L
    private val nomisPersonId = 123456L
    private val dpsContactPhoneId = 937373L

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personPhoneEvent(
            eventType = "PHONES_PERSON-INSERTED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create phone in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact-phone")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(nomisPhoneId = nomisPhoneId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withPhoneNumber(
              PersonPhoneNumber(
                phoneId = nomisPhoneId,
                number = "07973 555 555",
                type = CodeDescription("MOB", "Mobile"),
                extension = "x555",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactPhone(contactPhone().copy(contactPhoneId = dpsContactPhoneId))
        mappingApiMock.stubCreatePhoneMapping()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personPhoneEvent(
            eventType = "PHONES_PERSON-INSERTED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/phone/nomis-phone-id/$nomisPhoneId")))
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will create the phone in DPS from the person`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/contact-phone")))
        val request: SyncCreateContactPhoneRequest = ContactPersonDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact-phone")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(phoneType).isEqualTo("MOB")
          assertThat(phoneNumber).isEqualTo("07973 555 555")
          assertThat(extNumber).isEqualTo("x555")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/phone"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactPhoneId)
            .withRequestBodyJsonPath("dpsPhoneType", "PERSON")
            .withRequestBodyJsonPath("nomisId", "$nomisPhoneId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["dpsContactPhoneId"]).isEqualTo(dpsContactPhoneId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(
          nomisPhoneId = nomisPhoneId,
          mapping = PersonPhoneMappingDto(
            dpsId = "$dpsContactPhoneId",
            nomisId = nomisPhoneId,
            dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
            mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personPhoneEvent(
            eventType = "PHONES_PERSON-INSERTED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create phone in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact-phone")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["dpsContactPhoneId"]).isEqualTo(dpsContactPhoneId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(nomisPhoneId = nomisPhoneId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withPhoneNumber(
              PersonPhoneNumber(
                phoneId = nomisPhoneId,
                number = "07973 555 555",
                type = CodeDescription("MOB", "Mobile"),
                extension = "x555",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactPhone(contactPhone().copy(contactPhoneId = dpsContactPhoneId))
        mappingApiMock.stubCreatePhoneMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PersonPhoneMappingDto(
                dpsId = dpsContactPhoneId.toString(),
                nomisId = nomisPhoneId,
                dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
                mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
              ),
              existing = PersonPhoneMappingDto(
                dpsId = "9999",
                nomisId = nomisPhoneId,
                dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
                mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personPhoneEvent(
            eventType = "PHONES_PERSON-INSERTED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-contactperson-duplicate") }
      }

      @Test
      fun `will create the phone in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact-phone")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/phone"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsPhoneType", "PERSON")
            .withRequestBodyJsonPath("dpsId", dpsContactPhoneId)
            .withRequestBodyJsonPath("nomisId", "$nomisPhoneId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["dpsContactPhoneId"]).isEqualTo(dpsContactPhoneId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-contactperson-duplicate"),
          check {
            assertThat(it["existingNomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["existingDpsContactPhoneId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["duplicateDpsContactPhoneId"]).isEqualTo(dpsContactPhoneId.toString())
            assertThat(it["type"]).isEqualTo("PHONE")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(nomisPhoneId = nomisPhoneId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withPhoneNumber(
              PersonPhoneNumber(
                phoneId = nomisPhoneId,
                number = "07973 555 555",
                type = CodeDescription("MOB", "Mobile"),
                extension = "x555",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactPhone(contactPhone().copy(contactPhoneId = dpsContactPhoneId))
        mappingApiMock.stubCreatePhoneMappingFollowedBySuccess()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personPhoneEvent(
            eventType = "PHONES_PERSON-INSERTED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
          ),
        ).also { waitForAnyProcessingToComplete("contactperson-phone-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the phone in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact-phone")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/phone"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactPhoneId)
            .withRequestBodyJsonPath("nomisId", "$nomisPhoneId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-created-success"),
          check {
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["dpsContactPhoneId"]).isEqualTo(dpsContactPhoneId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PHONES_PERSON-UPDATED")
  inner class PersonPhoneUpdated {
    private val nomisPhoneId = 3456L
    private val nomisPersonId = 123456L
    private val dpsContactPhoneId = 937373L

    @Nested
    inner class WhenUpdateInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personPhoneEvent(
            eventType = "PHONES_PERSON-UPDATED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update phone in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/contact-phone/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneId(
          nomisPhoneId = nomisPhoneId,
          mapping = PersonPhoneMappingDto(
            dpsId = dpsContactPhoneId.toString(),
            nomisId = nomisPhoneId,
            dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
            mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withPhoneNumber(
              PersonPhoneNumber(
                phoneId = nomisPhoneId,
                number = "07973 555 555",
                type = CodeDescription("MOB", "Mobile"),
                extension = "x555",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                  modifyUserId = "T.SWIFT",
                  modifyDatetime = "2024-10-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubUpdateContactPhone(dpsContactPhoneId)
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personPhoneEvent(
            eventType = "PHONES_PERSON-UPDATED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will update the phone in DPS from the person`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/contact-phone/$dpsContactPhoneId")))
        val request: SyncUpdateContactPhoneRequest = ContactPersonDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact-phone/$dpsContactPhoneId")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(phoneType).isEqualTo("MOB")
          assertThat(phoneNumber).isEqualTo("07973 555 555")
          assertThat(extNumber).isEqualTo("x555")
          assertThat(updatedBy).isEqualTo("T.SWIFT")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-updated-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["dpsContactPhoneId"]).isEqualTo(dpsContactPhoneId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PHONES_PERSON-DELETED")
  inner class PersonPhoneDeleted {
    private val personId = 123456L
    private val phoneId = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personPhoneEvent(
          eventType = "PHONES_PERSON-DELETED",
          personId = personId,
          phoneId = phoneId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-phone-synchronisation-deleted-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["phoneId"]).isEqualTo(phoneId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("PHONES_PERSON-INSERTED - address")
  inner class PersonAddressPhoneAdded {
    private val nomisPhoneId = 3456L
    private val nomisPersonId = 123456L
    private val nomisAddressId = 652882L
    private val dpsContactAddressId = 637373L
    private val dpsContactAddressPhoneId = 937373L

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressPhoneEvent(
            eventType = "PHONES_PERSON-INSERTED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create phone in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact-address-phone")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(nomisPhoneId = nomisPhoneId, mapping = null)
        mappingApiMock.stubGetByNomisAddressId(
          nomisAddressId = nomisAddressId,
          PersonAddressMappingDto(
            nomisId = nomisAddressId,
            dpsId = dpsContactAddressId.toString(),
            mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
          ),
        )
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withAddress(
              nomisAddressId,
              PersonPhoneNumber(
                phoneId = nomisPhoneId,
                number = "07973 555 555",
                type = CodeDescription("MOB", "Mobile"),
                extension = "x555",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactAddressPhone(contactAddressPhone().copy(contactAddressPhoneId = dpsContactAddressPhoneId))
        mappingApiMock.stubCreatePhoneMapping()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressPhoneEvent(
            eventType = "PHONES_PERSON-INSERTED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/phone/nomis-phone-id/$nomisPhoneId")))
      }

      @Test
      fun `will retrieve the address mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/address/nomis-address-id/$nomisAddressId")))
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will create the address phone in DPS from the person`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/contact-address-phone")))
        val request: SyncCreateContactAddressPhoneRequest = ContactPersonDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact-address-phone")))
        with(request) {
          assertThat(contactAddressId).isEqualTo(dpsContactAddressId)
          assertThat(phoneType).isEqualTo("MOB")
          assertThat(phoneNumber).isEqualTo("07973 555 555")
          assertThat(extNumber).isEqualTo("x555")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/phone"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactAddressPhoneId)
            .withRequestBodyJsonPath("dpsPhoneType", "ADDRESS")
            .withRequestBodyJsonPath("nomisId", "$nomisPhoneId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            assertThat(it["dpsContactAddressId"]).isEqualTo(dpsContactAddressId.toString())
            assertThat(it["dpsContactAddressPhoneId"]).isEqualTo(dpsContactAddressPhoneId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(
          nomisPhoneId = nomisPhoneId,
          mapping = PersonPhoneMappingDto(
            dpsId = "$dpsContactAddressPhoneId",
            nomisId = nomisPhoneId,
            dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
            mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressPhoneEvent(
            eventType = "PHONES_PERSON-INSERTED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create phone in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact-address-phone")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["dpsContactPhoneId"]).isEqualTo(dpsContactAddressPhoneId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(nomisPhoneId = nomisPhoneId, mapping = null)
        mappingApiMock.stubGetByNomisAddressId(
          nomisAddressId = nomisAddressId,
          PersonAddressMappingDto(
            nomisId = nomisAddressId,
            dpsId = dpsContactAddressId.toString(),
            mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
          ),
        )
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withAddress(
              nomisAddressId,
              PersonPhoneNumber(
                phoneId = nomisPhoneId,
                number = "07973 555 555",
                type = CodeDescription("MOB", "Mobile"),
                extension = "x555",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactAddressPhone(contactAddressPhone().copy(contactAddressPhoneId = dpsContactAddressPhoneId))
        mappingApiMock.stubCreatePhoneMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PersonPhoneMappingDto(
                dpsId = dpsContactAddressPhoneId.toString(),
                nomisId = nomisPhoneId,
                dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
                mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
              ),
              existing = PersonPhoneMappingDto(
                dpsId = "9999",
                nomisId = nomisPhoneId,
                dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
                mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressPhoneEvent(
            eventType = "PHONES_PERSON-INSERTED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-contactperson-duplicate") }
      }

      @Test
      fun `will create the phone in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact-address-phone")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/phone"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsPhoneType", "ADDRESS")
            .withRequestBodyJsonPath("dpsId", dpsContactAddressPhoneId)
            .withRequestBodyJsonPath("nomisId", "$nomisPhoneId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["dpsContactAddressPhoneId"]).isEqualTo(dpsContactAddressPhoneId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-contactperson-duplicate"),
          check {
            assertThat(it["existingNomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["existingDpsContactPhoneId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["duplicateDpsContactPhoneId"]).isEqualTo(dpsContactAddressPhoneId.toString())
            assertThat(it["type"]).isEqualTo("PHONE")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(nomisPhoneId = nomisPhoneId, mapping = null)
        mappingApiMock.stubGetByNomisAddressId(
          nomisAddressId = nomisAddressId,
          PersonAddressMappingDto(
            nomisId = nomisAddressId,
            dpsId = dpsContactAddressId.toString(),
            mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
          ),
        )
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withAddress(
              nomisAddressId,
              PersonPhoneNumber(
                phoneId = nomisPhoneId,
                number = "07973 555 555",
                type = CodeDescription("MOB", "Mobile"),
                extension = "x555",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactAddressPhone(contactAddressPhone().copy(contactAddressPhoneId = dpsContactAddressPhoneId))
        mappingApiMock.stubCreatePhoneMappingFollowedBySuccess()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressPhoneEvent(
            eventType = "PHONES_PERSON-INSERTED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
          ),
        ).also { waitForAnyProcessingToComplete("contactperson-phone-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the phone in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact-address-phone")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/phone"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactAddressPhoneId)
            .withRequestBodyJsonPath("nomisId", "$nomisPhoneId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-created-success"),
          check {
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["dpsContactAddressPhoneId"]).isEqualTo(dpsContactAddressPhoneId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PHONES_PERSON-UPDATED - address")
  inner class PersonAddressPhoneUpdated {
    private val nomisPhoneId = 3456L
    private val nomisPersonId = 123456L
    private val nomisAddressId = 652882L
    private val dpsContactAddressPhoneId = 937373L

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressPhoneEvent(
            eventType = "PHONES_PERSON-UPDATED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create phone in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/contact-address-phone/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(
          nomisPhoneId = nomisPhoneId,
          mapping = PersonPhoneMappingDto(
            dpsId = dpsContactAddressPhoneId.toString(),
            nomisId = nomisPhoneId,
            dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
            mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withAddress(
              nomisAddressId,
              PersonPhoneNumber(
                phoneId = nomisPhoneId,
                number = "07973 555 555",
                type = CodeDescription("MOB", "Mobile"),
                extension = "x555",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                  modifyUserId = "T.SWIFT",
                  modifyDatetime = "2024-10-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubUpdateContactAddressPhone(dpsContactAddressPhoneId)
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personAddressPhoneEvent(
            eventType = "PHONES_PERSON-UPDATED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will update the address phone in DPS from the person`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/contact-address-phone/$dpsContactAddressPhoneId")))
        val request: SyncUpdateContactAddressPhoneRequest = ContactPersonDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact-address-phone/$dpsContactAddressPhoneId")))
        with(request) {
          assertThat(phoneType).isEqualTo("MOB")
          assertThat(phoneNumber).isEqualTo("07973 555 555")
          assertThat(extNumber).isEqualTo("x555")
          assertThat(updatedBy).isEqualTo("T.SWIFT")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-updated-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            assertThat(it["dpsContactAddressPhoneId"]).isEqualTo(dpsContactAddressPhoneId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PHONES_PERSON-DELETED - address")
  inner class PersonAddressPhoneDeleted {
    private val personId = 123456L
    private val phoneId = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personPhoneEvent(
          eventType = "PHONES_PERSON-DELETED",
          personId = personId,
          phoneId = phoneId,
          isAddress = true,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-address-phone-synchronisation-deleted-todo"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["phoneId"]).isEqualTo(phoneId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("INTERNET_ADDRESSES_PERSON-INSERTED")
  inner class PersonEmailAdded {
    private val nomisInternetAddressId = 3456L
    private val nomisPersonId = 123456L
    private val dpsContactEmailId = 937373L

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_PERSON-INSERTED",
            personId = nomisPersonId,
            internetAddressId = nomisInternetAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create email in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact-email")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-email-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisInternetAddressId = nomisInternetAddressId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withEmailAddress(
              PersonEmailAddress(
                emailAddressId = nomisInternetAddressId,
                email = "test@test.com",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactEmail(contactEmail().copy(contactEmailId = dpsContactEmailId))
        mappingApiMock.stubCreateEmailMapping()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_PERSON-INSERTED",
            personId = nomisPersonId,
            internetAddressId = nomisInternetAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/email/nomis-internet-address-id/$nomisInternetAddressId")))
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will create the email in DPS from the person`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/contact-email")))
        val request: SyncCreateContactEmailRequest = ContactPersonDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact-email")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(emailAddress).isEqualTo("test@test.com")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/email"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactEmailId)
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-email-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["dpsContactEmailId"]).isEqualTo(dpsContactEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisInternetAddressId = nomisInternetAddressId, mapping = PersonEmailMappingDto(dpsId = "$dpsContactEmailId", nomisId = nomisInternetAddressId, mappingType = PersonEmailMappingDto.MappingType.NOMIS_CREATED))
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_PERSON-INSERTED",
            personId = nomisPersonId,
            internetAddressId = nomisInternetAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create email in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact-email")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-email-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["dpsContactEmailId"]).isEqualTo(dpsContactEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisInternetAddressId = nomisInternetAddressId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withEmailAddress(
              PersonEmailAddress(
                emailAddressId = nomisInternetAddressId,
                email = "test@test.com",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactEmail(contactEmail().copy(contactEmailId = dpsContactEmailId))
        mappingApiMock.stubCreateEmailMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PersonEmailMappingDto(
                dpsId = dpsContactEmailId.toString(),
                nomisId = nomisInternetAddressId,
                mappingType = PersonEmailMappingDto.MappingType.NOMIS_CREATED,
              ),
              existing = PersonEmailMappingDto(
                dpsId = "9999",
                nomisId = nomisInternetAddressId,
                mappingType = PersonEmailMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_PERSON-INSERTED",
            personId = nomisPersonId,
            internetAddressId = nomisInternetAddressId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-contactperson-duplicate") }
      }

      @Test
      fun `will create the email in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact-email")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/email"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactEmailId)
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-email-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["dpsContactEmailId"]).isEqualTo(dpsContactEmailId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-contactperson-duplicate"),
          check {
            assertThat(it["existingNomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["existingDpsContactEmailId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["duplicateDpsContactEmailId"]).isEqualTo(dpsContactEmailId.toString())
            assertThat(it["type"]).isEqualTo("EMAIL")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisInternetAddressId = nomisInternetAddressId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withEmailAddress(
              PersonEmailAddress(
                emailAddressId = nomisInternetAddressId,
                email = "test@test.com",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactEmail(contactEmail().copy(contactEmailId = dpsContactEmailId))
        mappingApiMock.stubCreateEmailMappingFollowedBySuccess()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_PERSON-INSERTED",
            personId = nomisPersonId,
            internetAddressId = nomisInternetAddressId,
          ),
        ).also { waitForAnyProcessingToComplete("contactperson-email-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the email in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact-email")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/email"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactEmailId)
            .withRequestBodyJsonPath("nomisId", "$nomisInternetAddressId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-email-synchronisation-created-success"),
          check {
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["dpsContactEmailId"]).isEqualTo(dpsContactEmailId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("INTERNET_ADDRESSES_PERSON-UPDATED")
  inner class PersonEmailUpdated {
    private val nomisInternetAddressId = 3456L
    private val nomisPersonId = 123456L
    private val dpsContactEmailId = 937373L

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_PERSON-UPDATED",
            personId = nomisPersonId,
            internetAddressId = nomisInternetAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update email in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/contact-email/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-email-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(nomisInternetAddressId = nomisInternetAddressId, mapping = PersonEmailMappingDto(dpsId = "$dpsContactEmailId", nomisId = nomisInternetAddressId, mappingType = PersonEmailMappingDto.MappingType.NOMIS_CREATED))
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withEmailAddress(
              PersonEmailAddress(
                emailAddressId = nomisInternetAddressId,
                email = "test@test.com",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                  modifyUserId = "T.SWIFT",
                  modifyDatetime = "2024-10-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubUpdateContactEmail(dpsContactEmailId)
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_PERSON-UPDATED",
            personId = nomisPersonId,
            internetAddressId = nomisInternetAddressId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will update the email in DPS from the person`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/contact-email/$dpsContactEmailId")))
        val request: SyncUpdateContactEmailRequest = ContactPersonDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact-email/$dpsContactEmailId")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(emailAddress).isEqualTo("test@test.com")
          assertThat(updatedBy).isEqualTo("T.SWIFT")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-email-synchronisation-updated-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["dpsContactEmailId"]).isEqualTo(dpsContactEmailId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("INTERNET_ADDRESSES_PERSON-DELETED")
  inner class PersonEmailDeleted {
    private val personId = 123456L
    private val internetAddressId = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personInternetAddressEvent(
          eventType = "INTERNET_ADDRESSES_PERSON-DELETED",
          personId = personId,
          internetAddressId = internetAddressId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-email-synchronisation-deleted-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["internetAddressId"]).isEqualTo(internetAddressId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("PERSON_EMPLOYMENTS-INSERTED")
  inner class PersonEmploymentAdded {
    private val personId = 123456L
    private val employmentSequence = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personEmploymentEvent(
          eventType = "PERSON_EMPLOYMENTS-INSERTED",
          personId = personId,
          employmentSequence = employmentSequence,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-employment-synchronisation-created-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["employmentSequence"]).isEqualTo(employmentSequence.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("PERSON_EMPLOYMENTS-UPDATED")
  inner class PersonEmploymentUpdated {
    private val personId = 123456L
    private val employmentSequence = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personEmploymentEvent(
          eventType = "PERSON_EMPLOYMENTS-UPDATED",
          personId = personId,
          employmentSequence = employmentSequence,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-employment-synchronisation-updated-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["employmentSequence"]).isEqualTo(employmentSequence.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("PERSON_EMPLOYMENTS-DELETED")
  inner class PersonEmploymentDeleted {
    private val personId = 123456L
    private val employmentSequence = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personEmploymentEvent(
          eventType = "PERSON_EMPLOYMENTS-DELETED",
          personId = personId,
          employmentSequence = employmentSequence,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-employment-synchronisation-deleted-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["employmentSequence"]).isEqualTo(employmentSequence.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("PERSON_IDENTIFIERS-INSERTED")
  inner class PersonIdentifierAdded {
    private val nomisSequenceNumber = 4L
    private val nomisPersonId = 123456L
    private val dpsContactIdentityId = 937373L

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personIdentifierEvent(
            eventType = "PERSON_IDENTIFIERS-INSERTED",
            personId = nomisPersonId,
            identifierSequence = nomisSequenceNumber,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create identifier in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact-identity")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-identifier-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisIdentifierIdsOrNull(nomisPersonId = nomisPersonId, nomisSequenceNumber = nomisSequenceNumber, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withIdentifier(
              PersonIdentifier(
                sequence = nomisSequenceNumber,
                identifier = "SMITH777788",
                type = CodeDescription("DV", "Driving License"),
                issuedAuthority = "DVLA",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactIdentity(contactIdentity().copy(contactIdentityId = dpsContactIdentityId))
        mappingApiMock.stubCreateIdentifierMapping()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personIdentifierEvent(
            eventType = "PERSON_IDENTIFIERS-INSERTED",
            personId = nomisPersonId,
            identifierSequence = nomisSequenceNumber,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/identifier/nomis-person-id/$nomisPersonId/nomis-sequence-number/$nomisSequenceNumber")))
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will create the identifier in DPS from the person`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/contact-identity")))
        val request: SyncCreateContactIdentityRequest = ContactPersonDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact-identity")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(identityType).isEqualTo("DV")
          assertThat(identityValue).isEqualTo("SMITH777788")
          assertThat(issuingAuthority).isEqualTo("DVLA")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/identifier"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactIdentityId)
            .withRequestBodyJsonPath("nomisPersonId", "$nomisPersonId")
            .withRequestBodyJsonPath("nomisSequenceNumber", "$nomisSequenceNumber"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-identifier-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["dpsContactIdentityId"]).isEqualTo(dpsContactIdentityId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisIdentifierIdsOrNull(
          nomisPersonId = nomisPersonId,
          nomisSequenceNumber = nomisSequenceNumber,
          mapping = PersonIdentifierMappingDto(
            dpsId = "$dpsContactIdentityId",
            nomisSequenceNumber = nomisSequenceNumber,
            nomisPersonId = nomisPersonId,
            mappingType = PersonIdentifierMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personIdentifierEvent(
            eventType = "PERSON_IDENTIFIERS-INSERTED",
            personId = nomisPersonId,
            identifierSequence = nomisSequenceNumber,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create identifier in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact-identity")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-identifier-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["dpsContactIdentityId"]).isEqualTo(dpsContactIdentityId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisIdentifierIdsOrNull(nomisPersonId = nomisPersonId, nomisSequenceNumber = nomisSequenceNumber, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withIdentifier(
              PersonIdentifier(
                sequence = nomisSequenceNumber,
                identifier = "SMITH777788",
                type = CodeDescription("DV", "Driving License"),
                issuedAuthority = "DVLA",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactIdentity(contactIdentity().copy(contactIdentityId = dpsContactIdentityId))
        mappingApiMock.stubCreateIdentifierMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PersonIdentifierMappingDto(
                dpsId = dpsContactIdentityId.toString(),
                nomisSequenceNumber = nomisSequenceNumber,
                nomisPersonId = nomisPersonId,
                mappingType = PersonIdentifierMappingDto.MappingType.NOMIS_CREATED,
              ),
              existing = PersonIdentifierMappingDto(
                dpsId = "9999",
                nomisSequenceNumber = nomisSequenceNumber,
                nomisPersonId = nomisPersonId,
                mappingType = PersonIdentifierMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personIdentifierEvent(
            eventType = "PERSON_IDENTIFIERS-INSERTED",
            personId = nomisPersonId,
            identifierSequence = nomisSequenceNumber,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-contactperson-duplicate") }
      }

      @Test
      fun `will create the identifier in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact-identity")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/identifier"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactIdentityId)
            .withRequestBodyJsonPath("nomisSequenceNumber", "$nomisSequenceNumber")
            .withRequestBodyJsonPath("nomisPersonId", "$nomisPersonId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-identifier-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["dpsContactIdentityId"]).isEqualTo(dpsContactIdentityId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-contactperson-duplicate"),
          check {
            assertThat(it["existingNomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["existingNomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["existingDpsContactIdentityId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["duplicateNomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["duplicateDpsContactIdentityId"]).isEqualTo(dpsContactIdentityId.toString())
            assertThat(it["type"]).isEqualTo("IDENTIFIER")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisIdentifierIdsOrNull(nomisPersonId = nomisPersonId, nomisSequenceNumber = nomisSequenceNumber, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withIdentifier(
              PersonIdentifier(
                sequence = nomisSequenceNumber,
                identifier = "SMITH777788",
                type = CodeDescription("DV", "Driving License"),
                issuedAuthority = "DVLA",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactIdentity(contactIdentity().copy(contactIdentityId = dpsContactIdentityId))
        mappingApiMock.stubCreateIdentifierMappingFollowedBySuccess()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personIdentifierEvent(
            eventType = "PERSON_IDENTIFIERS-INSERTED",
            personId = nomisPersonId,
            identifierSequence = nomisSequenceNumber,
          ),
        ).also { waitForAnyProcessingToComplete("contactperson-identifier-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the identifier in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact-identity")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/identifier"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactIdentityId)
            .withRequestBodyJsonPath("nomisSequenceNumber", "$nomisSequenceNumber")
            .withRequestBodyJsonPath("nomisPersonId", "$nomisPersonId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-identifier-synchronisation-created-success"),
          check {
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactIdentityId"]).isEqualTo(dpsContactIdentityId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PERSON_IDENTIFIERS-UPDATED")
  inner class PersonIdentifierUpdated {
    private val personId = 123456L
    private val identifierSequence = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personIdentifierEvent(
          eventType = "PERSON_IDENTIFIERS-UPDATED",
          personId = personId,
          identifierSequence = identifierSequence,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-identifier-synchronisation-updated-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["identifierSequence"]).isEqualTo(identifierSequence.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("PERSON_IDENTIFIERS-DELETED")
  inner class PersonIdentifierDeleted {
    private val personId = 123456L
    private val identifierSequence = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personIdentifierEvent(
          eventType = "PERSON_IDENTIFIERS-DELETED",
          personId = personId,
          identifierSequence = identifierSequence,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-identifier-synchronisation-deleted-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["identifierSequence"]).isEqualTo(identifierSequence.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("VISITOR_RESTRICTION-UPSERTED")
  inner class PersonRestrictionUpserted {
    private val nomisPersonRestrictionId = 3456L
    private val nomisPersonId = 123456L
    private val dpsContactRestrictionId = 937373L

    @Nested
    inner class WhenCreatedOrUpdatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personRestrictionEvent(
            eventType = "VISITOR_RESTRICTION-UPSERTED",
            personId = nomisPersonId,
            restrictionId = nomisPersonRestrictionId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create restriction in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/contact-restriction")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-restriction-synchronisation-upserted-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPersonRestrictionId"]).isEqualTo(nomisPersonRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = nomisPersonRestrictionId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson()
            .withContactRestriction(
              ContactRestriction(
                id = nomisPersonRestrictionId,
                comment = "Banned for life",
                type = CodeDescription("BAN", "Banned"),
                effectiveDate = LocalDate.parse("2021-01-01"),
                expiryDate = LocalDate.parse("2025-01-01"),
                enteredStaff = ContactRestrictionEnteredStaff(
                  staffId = 123,
                  username = "J.SMITH",
                ),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactRestriction(contactRestriction().copy(contactRestrictionId = dpsContactRestrictionId))
        mappingApiMock.stubCreatePersonRestrictionMapping()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personRestrictionEvent(
            eventType = "VISITOR_RESTRICTION-UPSERTED",
            personId = nomisPersonId,
            restrictionId = nomisPersonRestrictionId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/person-restriction/nomis-person-restriction-id/$nomisPersonRestrictionId")))
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will create the restriction in DPS from the person`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/contact-restriction")))
        val request: SyncCreateContactRestrictionRequest = ContactPersonDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact-restriction")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(restrictionType).isEqualTo("BAN")
          assertThat(startDate).isEqualTo(LocalDate.parse("2021-01-01"))
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2025-01-01"))
          assertThat(comments).isEqualTo("Banned for life")
          assertThat(createdBy).isEqualTo("J.SMITH")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/person-restriction"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactRestrictionId)
            .withRequestBodyJsonPath("nomisId", "$nomisPersonRestrictionId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-restriction-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPersonRestrictionId"]).isEqualTo(nomisPersonRestrictionId.toString())
            assertThat(it["dpsContactRestrictionId"]).isEqualTo(dpsContactRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = nomisPersonRestrictionId, mapping = PersonRestrictionMappingDto(dpsId = "$dpsContactRestrictionId", nomisId = nomisPersonRestrictionId, mappingType = PersonRestrictionMappingDto.MappingType.NOMIS_CREATED))
        nomisApiMock.stubGetPerson(
          person = contactPerson()
            .withContactRestriction(
              ContactRestriction(
                id = nomisPersonRestrictionId,
                comment = "Banned for life",
                type = CodeDescription("BAN", "Banned"),
                effectiveDate = LocalDate.parse("2021-01-01"),
                expiryDate = LocalDate.parse("2025-01-01"),
                enteredStaff = ContactRestrictionEnteredStaff(
                  staffId = 123,
                  username = "J.SMITH",
                ),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                  modifyUserId = "J.SPEAK",
                  modifyDatetime = "2024-09-05T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubUpdateContactRestriction(dpsContactRestrictionId)
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personRestrictionEvent(
            eventType = "VISITOR_RESTRICTION-UPSERTED",
            personId = nomisPersonId,
            restrictionId = nomisPersonRestrictionId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will update the restriction in DPS`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/contact-restriction/$dpsContactRestrictionId")))
        val request: SyncUpdateContactRestrictionRequest = ContactPersonDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact-restriction/$dpsContactRestrictionId")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(restrictionType).isEqualTo("BAN")
          assertThat(startDate).isEqualTo(LocalDate.parse("2021-01-01"))
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2025-01-01"))
          assertThat(comments).isEqualTo("Banned for life")
          assertThat(updatedBy).isEqualTo("J.SMITH")
          assertThat(updatedTime).isEqualTo("2024-09-05T13:31")
        }
      }

      @Test
      fun `will track telemetry for update success`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-restriction-synchronisation-updated-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPersonRestrictionId"]).isEqualTo(nomisPersonRestrictionId.toString())
            assertThat(it["dpsContactRestrictionId"]).isEqualTo(dpsContactRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = nomisPersonRestrictionId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson()
            .withContactRestriction(
              ContactRestriction(
                id = nomisPersonRestrictionId,
                comment = "Banned for life",
                type = CodeDescription("BAN", "Banned"),
                effectiveDate = LocalDate.parse("2021-01-01"),
                expiryDate = LocalDate.parse("2025-01-01"),
                enteredStaff = ContactRestrictionEnteredStaff(
                  staffId = 123,
                  username = "J.SMITH",
                ),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactRestriction(contactRestriction().copy(contactRestrictionId = dpsContactRestrictionId))
        mappingApiMock.stubCreatePersonRestrictionMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PersonRestrictionMappingDto(
                dpsId = dpsContactRestrictionId.toString(),
                nomisId = nomisPersonRestrictionId,
                mappingType = PersonRestrictionMappingDto.MappingType.NOMIS_CREATED,
              ),
              existing = PersonRestrictionMappingDto(
                dpsId = "9999",
                nomisId = nomisPersonRestrictionId,
                mappingType = PersonRestrictionMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personRestrictionEvent(
            eventType = "VISITOR_RESTRICTION-UPSERTED",
            personId = nomisPersonId,
            restrictionId = nomisPersonRestrictionId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-contactperson-duplicate") }
      }

      @Test
      fun `will create the restriction in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact-restriction")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/person-restriction"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactRestrictionId)
            .withRequestBodyJsonPath("nomisId", "$nomisPersonRestrictionId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-restriction-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPersonRestrictionId"]).isEqualTo(nomisPersonRestrictionId.toString())
            assertThat(it["dpsContactRestrictionId"]).isEqualTo(dpsContactRestrictionId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-contactperson-duplicate"),
          check {
            assertThat(it["existingNomisPersonRestrictionId"]).isEqualTo(nomisPersonRestrictionId.toString())
            assertThat(it["existingDpsContactRestrictionId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisPersonRestrictionId"]).isEqualTo(nomisPersonRestrictionId.toString())
            assertThat(it["duplicateDpsContactRestrictionId"]).isEqualTo(dpsContactRestrictionId.toString())
            assertThat(it["type"]).isEqualTo("PERSON_RESTRICTION")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = nomisPersonRestrictionId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson()
            .withContactRestriction(
              ContactRestriction(
                id = nomisPersonRestrictionId,
                comment = "Banned for life",
                type = CodeDescription("BAN", "Banned"),
                effectiveDate = LocalDate.parse("2021-01-01"),
                expiryDate = LocalDate.parse("2025-01-01"),
                enteredStaff = ContactRestrictionEnteredStaff(
                  staffId = 123,
                  username = "J.SMITH",
                ),
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactRestriction(contactRestriction().copy(contactRestrictionId = dpsContactRestrictionId))
        mappingApiMock.stubCreatePersonRestrictionMappingFollowedBySuccess()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          personRestrictionEvent(
            eventType = "VISITOR_RESTRICTION-UPSERTED",
            personId = nomisPersonId,
            restrictionId = nomisPersonRestrictionId,
          ),
        ).also { waitForAnyProcessingToComplete("contactperson-person-restriction-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the restriction in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/contact-restriction")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/person-restriction"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactRestrictionId)
            .withRequestBodyJsonPath("nomisId", "$nomisPersonRestrictionId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-restriction-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonRestrictionId"]).isEqualTo(nomisPersonRestrictionId.toString())
            assertThat(it["dpsContactRestrictionId"]).isEqualTo(dpsContactRestrictionId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("VISITOR_RESTRICTION-DELETED")
  inner class PersonRestrictionDeleted {
    private val restrictionId = 9876L
    private val personId = 123456L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personRestrictionEvent(
          eventType = "VISITOR_RESTRICTION-DELETED",
          personId = personId,
          restrictionId = restrictionId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-restriction-synchronisation-deleted-success"),
        check {
          assertThat(it["personRestrictionId"]).isEqualTo(restrictionId.toString())
          assertThat(it["personId"]).isEqualTo(personId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("OFFENDER_CONTACT-INSERTED")
  inner class ContactAdded {
    private val nomisContactId = 3456L
    private val nomisPersonId = 123456L
    private val bookingId = 890L
    private val offenderNo = "A1234KT"
    private val dpsPrisonerContactId = 937373L

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          contactEvent(
            eventType = "OFFENDER_CONTACT-INSERTED",
            personId = nomisPersonId,
            contactId = nomisContactId,
            bookingId = bookingId,
            offenderNo = offenderNo,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create prisoner contact in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/prisoner-contact")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-synchronisation-created-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactId"]).isEqualTo(nomisContactId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactIdOrNull(nomisContactId = nomisContactId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withContact(
              PersonContact(
                id = nomisContactId,
                contactType = CodeDescription("S", "Social/Family"),
                relationshipType = CodeDescription("BRO", "Brother"),
                active = true,
                nextOfKin = true,
                approvedVisitor = true,
                emergencyContact = true,
                expiryDate = LocalDate.parse("2025-01-01"),
                comment = "Big brother",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
                prisoner = ContactForPrisoner(
                  bookingId = 76544,
                  offenderNo = offenderNo,
                  bookingSequence = 1,
                  firstName = "JOHN",
                  lastName = "SMITH",
                ),
                restrictions = emptyList(),
              ),
            ),
        )
        dpsApiMock.stubCreatePrisonerContact(prisonerContact().copy(id = dpsPrisonerContactId))
        mappingApiMock.stubCreateContactMapping()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          contactEvent(
            eventType = "OFFENDER_CONTACT-INSERTED",
            personId = nomisPersonId,
            contactId = nomisContactId,
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists for person`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/contact/nomis-contact-id/$nomisContactId")))
      }

      @Test
      fun `will retrieve the person details containing the contact from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will create the prisoner contact in DPS from the person`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/prisoner-contact")))
        val createPrisonerContactRequest: SyncCreatePrisonerContactRequest = ContactPersonDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/prisoner-contact")))
        with(createPrisonerContactRequest) {
          assertThat(prisonerNumber).isEqualTo(offenderNo)
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2025-01-01"))
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(approvedVisitor).isTrue()
          assertThat(emergencyContact).isTrue()
          assertThat(nextOfKin).isTrue()
          assertThat(active).isTrue()
          assertThat(currentTerm).isTrue()
          assertThat(contactType).isEqualTo("S")
          assertThat(relationshipType).isEqualTo("BRO")
          assertThat(comments).isEqualTo("Big brother")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/contact"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsPrisonerContactId)
            .withRequestBodyJsonPath("nomisId", "$nomisContactId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactId"]).isEqualTo(nomisContactId.toString())
            assertThat(it["dpsPrisonerContactId"]).isEqualTo(dpsPrisonerContactId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactIdOrNull(nomisContactId = nomisContactId, mapping = PersonContactMappingDto(dpsId = "$dpsPrisonerContactId", nomisId = nomisContactId, mappingType = PersonContactMappingDto.MappingType.NOMIS_CREATED))
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          contactEvent(
            eventType = "OFFENDER_CONTACT-INSERTED",
            personId = nomisPersonId,
            contactId = nomisContactId,
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create prisoner contact in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/prisoner-contact")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactId"]).isEqualTo(nomisContactId.toString())
            assertThat(it["dpsPrisonerContactId"]).isEqualTo(dpsPrisonerContactId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactIdOrNull(nomisContactId = nomisContactId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withContact(
              PersonContact(
                id = nomisContactId,
                contactType = CodeDescription("S", "Social/Family"),
                relationshipType = CodeDescription("BRO", "Brother"),
                active = true,
                nextOfKin = true,
                approvedVisitor = true,
                emergencyContact = true,
                expiryDate = LocalDate.parse("2025-01-01"),
                comment = "Big brother",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
                prisoner = ContactForPrisoner(
                  bookingId = 76544,
                  offenderNo = offenderNo,
                  bookingSequence = 1,
                  firstName = "JOHN",
                  lastName = "SMITH",
                ),
                restrictions = emptyList(),
              ),
            ),
        )
        dpsApiMock.stubCreatePrisonerContact(prisonerContact().copy(id = dpsPrisonerContactId))
        mappingApiMock.stubCreateContactMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PersonMappingDto(
                dpsId = dpsPrisonerContactId.toString(),
                nomisId = nomisContactId,
                mappingType = NOMIS_CREATED,
              ),
              existing = PersonMappingDto(
                dpsId = "9999",
                nomisId = nomisContactId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          contactEvent(
            eventType = "OFFENDER_CONTACT-INSERTED",
            personId = nomisPersonId,
            contactId = nomisContactId,
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-contactperson-duplicate") }
      }

      @Test
      fun `will create the prisoner contact in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/prisoner-contact")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/contact"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsPrisonerContactId)
            .withRequestBodyJsonPath("nomisId", "$nomisContactId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactId"]).isEqualTo(nomisContactId.toString())
            assertThat(it["dpsPrisonerContactId"]).isEqualTo(dpsPrisonerContactId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-contactperson-duplicate"),
          check {
            assertThat(it["existingNomisContactId"]).isEqualTo(nomisContactId.toString())
            assertThat(it["existingDpsPrisonerContactId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisContactId"]).isEqualTo(nomisContactId.toString())
            assertThat(it["duplicateDpsPrisonerContactId"]).isEqualTo(dpsPrisonerContactId.toString())
            assertThat(it["type"]).isEqualTo("CONTACT")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactIdOrNull(nomisContactId = nomisContactId, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withContact(
              PersonContact(
                id = nomisContactId,
                contactType = CodeDescription("S", "Social/Family"),
                relationshipType = CodeDescription("BRO", "Brother"),
                active = true,
                nextOfKin = true,
                approvedVisitor = true,
                emergencyContact = true,
                expiryDate = LocalDate.parse("2025-01-01"),
                comment = "Big brother",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                ),
                prisoner = ContactForPrisoner(
                  bookingId = 76544,
                  offenderNo = offenderNo,
                  bookingSequence = 1,
                  firstName = "JOHN",
                  lastName = "SMITH",
                ),
                restrictions = emptyList(),
              ),
            ),
        )
        dpsApiMock.stubCreatePrisonerContact(prisonerContact().copy(id = dpsPrisonerContactId))
        mappingApiMock.stubCreateContactMappingFollowedBySuccess()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          contactEvent(
            eventType = "OFFENDER_CONTACT-INSERTED",
            personId = nomisPersonId,
            contactId = nomisContactId,
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete("contactperson-contact-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the prisoner contact in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/prisoner-contact")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/contact"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsPrisonerContactId)
            .withRequestBodyJsonPath("nomisId", "$nomisContactId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-synchronisation-created-success"),
          check {
            assertThat(it["nomisContactId"]).isEqualTo(nomisContactId.toString())
            assertThat(it["dpsPrisonerContactId"]).isEqualTo(dpsPrisonerContactId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CONTACT-UPDATED")
  inner class ContactUpdated {
    private val nomisContactId = 3456L
    private val nomisPersonId = 123456L
    private val dpsPrisonerContactId = 87474L
    private val bookingId = 890L
    private val offenderNo = "A1234KT"

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          contactEvent(
            eventType = "OFFENDER_CONTACT-UPDATED",
            personId = nomisPersonId,
            contactId = nomisContactId,
            bookingId = bookingId,
            offenderNo = offenderNo,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not updated contact in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/prisoner-contact/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactId(
          nomisContactId = nomisContactId,
          mapping = PersonContactMappingDto(dpsId = dpsPrisonerContactId.toString(), nomisId = nomisContactId, mappingType = PersonContactMappingDto.MappingType.MIGRATED),
        )
        nomisApiMock.stubGetPerson(
          contactPerson(nomisPersonId)
            .withContact(
              PersonContact(
                id = nomisContactId,
                contactType = CodeDescription("S", "Social/Family"),
                relationshipType = CodeDescription("BRO", "Brother"),
                active = true,
                nextOfKin = true,
                approvedVisitor = true,
                emergencyContact = true,
                expiryDate = LocalDate.parse("2025-01-01"),
                comment = "Big brother",
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = "2024-09-01T13:31",
                  modifyUserId = "T.SMITH",
                  modifyDatetime = "2024-10-01T13:31",
                ),
                prisoner = ContactForPrisoner(
                  bookingId = 76544,
                  offenderNo = offenderNo,
                  bookingSequence = 1,
                  firstName = "JOHN",
                  lastName = "SMITH",
                ),
                restrictions = emptyList(),
              ),
            ),
        )
        dpsApiMock.stubUpdatePrisonerContact(prisonerContactId = dpsPrisonerContactId)

        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          contactEvent(
            eventType = "OFFENDER_CONTACT-UPDATED",
            personId = nomisPersonId,
            contactId = nomisContactId,
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-synchronisation-updated-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactId"]).isEqualTo(nomisContactId.toString())
            assertThat(it["dpsPrisonerContactId"]).isEqualTo(dpsPrisonerContactId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will update the prisoner contact in DPS from the NOMIS person`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/prisoner-contact/$dpsPrisonerContactId")))
        val updatePrisonerContactRequest: SyncUpdatePrisonerContactRequest = ContactPersonDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/prisoner-contact/$dpsPrisonerContactId")))
        with(updatePrisonerContactRequest) {
          assertThat(prisonerNumber).isEqualTo(offenderNo)
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2025-01-01"))
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(approvedVisitor).isTrue()
          assertThat(emergencyContact).isTrue()
          assertThat(nextOfKin).isTrue()
          assertThat(active).isTrue()
          assertThat(currentTerm).isTrue()
          assertThat(contactType).isEqualTo("S")
          assertThat(relationshipType).isEqualTo("BRO")
          assertThat(comments).isEqualTo("Big brother")
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo("2024-10-01T13:31")
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CONTACT-DELETED")
  inner class ContactDeleted {
    private val contactId = 3456L
    private val personId = 123456L
    private val bookingId = 890L
    private val offenderNo = "A1234KT"

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        contactEvent(
          eventType = "OFFENDER_CONTACT-DELETED",
          personId = personId,
          contactId = contactId,
          bookingId = bookingId,
          offenderNo = offenderNo,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-contact-synchronisation-deleted-success"),
        check {
          assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
          assertThat(it["contactId"]).isEqualTo(contactId.toString())
          assertThat(it["personId"]).isEqualTo(personId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("PERSON_RESTRICTION-UPSERTED")
  inner class ContactRestrictionUpserted {
    private val nomisContactRestrictionId = 3456L
    private val nomisPersonId = 123456L
    private val nomisContactId = 652882L
    private val dpsPrisonerContactId = 637373L
    private val dpsPrisonerContactRestrictionId = 937373L
    private val offenderNo = "A1234KT"

    @Nested
    inner class WhenCreatedOrUpdatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          contactRestrictionEvent(
            eventType = "PERSON_RESTRICTION-UPSERTED",
            personId = nomisPersonId,
            restrictionId = nomisContactRestrictionId,
            contactId = nomisContactId,
            offenderNo = offenderNo,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create restriction in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-restriction-synchronisation-upserted-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactId"]).isEqualTo(nomisContactId.toString())
            assertThat(it["nomisContactRestrictionId"]).isEqualTo(nomisContactRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactRestrictionIdOrNull(nomisContactRestrictionId = nomisContactRestrictionId, mapping = null)
        mappingApiMock.stubGetByNomisContactId(
          nomisContactId = nomisContactId,
          PersonContactMappingDto(
            nomisId = nomisContactId,
            dpsId = dpsPrisonerContactId.toString(),
            mappingType = PersonContactMappingDto.MappingType.MIGRATED,
          ),
        )
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withContact(
              contactId = nomisContactId,
              offenderNo = offenderNo,
              restriction = ContactRestriction(
                id = nomisContactRestrictionId,
                type = CodeDescription(code = "BAN", description = "Banned"),
                enteredStaff = ContactRestrictionEnteredStaff(staffId = 1, username = "Q1251T"),
                effectiveDate = LocalDate.parse("2020-01-01"),
                expiryDate = LocalDate.parse("2026-01-01"),
                comment = "Banned for life",
                audit = nomisAudit().copy(createDatetime = "2024-09-01T13:31"),
              ),
            ),
        )

        dpsApiMock.stubCreatePrisonerContactRestriction(prisonerContactRestriction().copy(prisonerContactRestrictionId = dpsPrisonerContactRestrictionId))
        mappingApiMock.stubCreateContactRestrictionMapping()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          contactRestrictionEvent(
            eventType = "PERSON_RESTRICTION-UPSERTED",
            personId = nomisPersonId,
            restrictionId = nomisContactRestrictionId,
            contactId = nomisContactId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/contact-restriction/nomis-contact-restriction-id/$nomisContactRestrictionId")))
      }

      @Test
      fun `will retrieve the contact mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/contact/nomis-contact-id/$nomisContactId")))
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will create the contact restriction DPS`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction")))
        val request: SyncCreatePrisonerContactRestrictionRequest = ContactPersonDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction")))
        with(request) {
          assertThat(prisonerContactId).isEqualTo(dpsPrisonerContactId)
          assertThat(restrictionType).isEqualTo("BAN")
          assertThat(comments).isEqualTo("Banned for life")
          assertThat(startDate).isEqualTo("2020-01-01")
          assertThat(expiryDate).isEqualTo("2026-01-01")
          // the entered staff username
          assertThat(createdBy).isEqualTo("Q1251T")
          assertThat(createdTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/contact-restriction"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsPrisonerContactRestrictionId)
            .withRequestBodyJsonPath("nomisId", "$nomisContactRestrictionId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-restriction-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactRestrictionId"]).isEqualTo(nomisContactRestrictionId.toString())
            assertThat(it["nomisContactId"]).isEqualTo(nomisContactId.toString())
            assertThat(it["dpsPrisonerContactId"]).isEqualTo(dpsPrisonerContactId.toString())
            assertThat(it["dpsPrisonerContactRestrictionId"]).isEqualTo(dpsPrisonerContactRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactRestrictionIdOrNull(
          nomisContactRestrictionId = nomisContactRestrictionId,
          mapping = PersonContactRestrictionMappingDto(
            dpsId = "$dpsPrisonerContactRestrictionId",
            nomisId = nomisContactRestrictionId,
            mappingType = PersonContactRestrictionMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        mappingApiMock.stubGetByNomisContactId(
          nomisContactId = nomisContactId,
          PersonContactMappingDto(
            nomisId = nomisContactId,
            dpsId = dpsPrisonerContactId.toString(),
            mappingType = PersonContactMappingDto.MappingType.MIGRATED,
          ),
        )
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withContact(
              contactId = nomisContactId,
              offenderNo = offenderNo,
              restriction = ContactRestriction(
                id = nomisContactRestrictionId,
                type = CodeDescription(code = "BAN", description = "Banned"),
                enteredStaff = ContactRestrictionEnteredStaff(staffId = 1, username = "J.SMITH"),
                effectiveDate = LocalDate.parse("2020-01-01"),
                expiryDate = LocalDate.parse("2026-01-01"),
                comment = "Banned for life",
                audit = nomisAudit().copy(modifyDatetime = "2024-09-01T13:31", modifyUserId = "J.SPEAK"),
              ),
            ),
        )
        dpsContactPersonServer.stubUpdatePrisonerContactRestriction(dpsPrisonerContactRestrictionId)
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          contactRestrictionEvent(
            eventType = "PERSON_RESTRICTION-UPSERTED",
            personId = nomisPersonId,
            restrictionId = nomisContactRestrictionId,
            contactId = nomisContactId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will update the restriction in DPS`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction/$dpsPrisonerContactRestrictionId")))
        val request: SyncUpdatePrisonerContactRestrictionRequest =
          ContactPersonDpsApiExtension.getRequestBody(putRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction/$dpsPrisonerContactRestrictionId")))
        with(request) {
          assertThat(restrictionType).isEqualTo("BAN")
          assertThat(startDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2026-01-01"))
          assertThat(comments).isEqualTo("Banned for life")
          assertThat(updatedBy).isEqualTo("J.SMITH")
          assertThat(updatedTime).isEqualTo("2024-09-01T13:31")
        }
      }

      @Test
      fun `will track telemetry for update success`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-restriction-synchronisation-updated-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactRestrictionId"]).isEqualTo(nomisContactRestrictionId.toString())
            assertThat(it["dpsPrisonerContactRestrictionId"]).isEqualTo(dpsPrisonerContactRestrictionId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactRestrictionIdOrNull(nomisContactRestrictionId = nomisContactRestrictionId, mapping = null)
        mappingApiMock.stubGetByNomisContactId(
          nomisContactId = nomisContactId,
          PersonContactMappingDto(
            nomisId = nomisContactId,
            dpsId = dpsPrisonerContactId.toString(),
            mappingType = PersonContactMappingDto.MappingType.MIGRATED,
          ),
        )
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withContact(
              contactId = nomisContactId,
              offenderNo = offenderNo,
              restriction = ContactRestriction(
                id = nomisContactRestrictionId,
                type = CodeDescription(code = "BAN", description = "Banned"),
                enteredStaff = ContactRestrictionEnteredStaff(staffId = 1, username = "Q1251T"),
                effectiveDate = LocalDate.parse("2020-01-01"),
                expiryDate = LocalDate.parse("2026-01-01"),
                comment = "Banned for life",
                audit = nomisAudit().copy(createDatetime = "2024-09-01T13:31"),
              ),
            ),
        )
        dpsApiMock.stubCreatePrisonerContactRestriction(prisonerContactRestriction().copy(prisonerContactRestrictionId = dpsPrisonerContactRestrictionId))
        mappingApiMock.stubCreateContactRestrictionMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PersonContactRestrictionMappingDto(
                dpsId = dpsPrisonerContactRestrictionId.toString(),
                nomisId = nomisContactRestrictionId,
                mappingType = PersonContactRestrictionMappingDto.MappingType.NOMIS_CREATED,
              ),
              existing = PersonContactRestrictionMappingDto(
                dpsId = "9999",
                nomisId = nomisContactRestrictionId,
                mappingType = PersonContactRestrictionMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          contactRestrictionEvent(
            eventType = "PERSON_RESTRICTION-UPSERTED",
            personId = nomisPersonId,
            restrictionId = nomisContactRestrictionId,
            contactId = nomisContactId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-contactperson-duplicate") }
      }

      @Test
      fun `will create the prisoner contact restriction in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/contact-restriction"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsPrisonerContactRestrictionId)
            .withRequestBodyJsonPath("nomisId", "$nomisContactRestrictionId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-restriction-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactRestrictionId"]).isEqualTo(nomisContactRestrictionId.toString())
            assertThat(it["dpsPrisonerContactRestrictionId"]).isEqualTo(dpsPrisonerContactRestrictionId.toString())
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-contactperson-duplicate"),
          check {
            assertThat(it["existingNomisContactRestrictionId"]).isEqualTo(nomisContactRestrictionId.toString())
            assertThat(it["existingDpsPrisonerContactRestrictionId"]).isEqualTo("9999")
            assertThat(it["duplicateNomisContactRestrictionId"]).isEqualTo(nomisContactRestrictionId.toString())
            assertThat(it["duplicateDpsPrisonerContactRestrictionId"]).isEqualTo(dpsPrisonerContactRestrictionId.toString())
            assertThat(it["type"]).isEqualTo("CONTACT_RESTRICTION")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactRestrictionIdOrNull(nomisContactRestrictionId = nomisContactRestrictionId, mapping = null)
        mappingApiMock.stubGetByNomisContactId(
          nomisContactId = nomisContactId,
          PersonContactMappingDto(
            nomisId = nomisContactId,
            dpsId = dpsPrisonerContactId.toString(),
            mappingType = PersonContactMappingDto.MappingType.MIGRATED,
          ),
        )
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withContact(
              contactId = nomisContactId,
              offenderNo = offenderNo,
              restriction = ContactRestriction(
                id = nomisContactRestrictionId,
                type = CodeDescription(code = "BAN", description = "Banned"),
                enteredStaff = ContactRestrictionEnteredStaff(staffId = 1, username = "Q1251T"),
                effectiveDate = LocalDate.parse("2020-01-01"),
                expiryDate = LocalDate.parse("2026-01-01"),
                comment = "Banned for life",
                audit = nomisAudit().copy(createDatetime = "2024-09-01T13:31"),
              ),
            ),
        )
        dpsApiMock.stubCreatePrisonerContactRestriction(prisonerContactRestriction().copy(prisonerContactRestrictionId = dpsPrisonerContactRestrictionId))
        mappingApiMock.stubCreateContactRestrictionMappingFollowedBySuccess()
        awsSqsContactPersonOffenderEventsClient.sendMessage(
          contactPersonQueueOffenderEventsUrl,
          contactRestrictionEvent(
            eventType = "PERSON_RESTRICTION-UPSERTED",
            personId = nomisPersonId,
            restrictionId = nomisContactRestrictionId,
            contactId = nomisContactId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete("contactperson-contact-restriction-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the prisoner contact restriction in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction")))
      }

      @Test
      fun `will eventual to create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/contact-restriction"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsPrisonerContactRestrictionId)
            .withRequestBodyJsonPath("nomisId", "$nomisContactRestrictionId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-restriction-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactRestrictionId"]).isEqualTo(nomisContactRestrictionId.toString())
            assertThat(it["dpsPrisonerContactRestrictionId"]).isEqualTo(dpsPrisonerContactRestrictionId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PERSON_RESTRICTION-DELETED")
  inner class ContactRestrictionDeleted {
    private val restrictionId = 9876L
    private val personId = 123456L
    private val contactId = 3456L
    private val offenderNo = "A1234KT"

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        contactRestrictionEvent(
          eventType = "PERSON_RESTRICTION-DELETED",
          personId = personId,
          restrictionId = restrictionId,
          contactId = contactId,
          offenderNo = offenderNo,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-contact-restriction-synchronisation-deleted-success"),
        check {
          assertThat(it["contactRestrictionId"]).isEqualTo(restrictionId.toString())
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["contactId"]).isEqualTo(contactId.toString())
          assertThat(it["offenderNo"]).isEqualTo(offenderNo)
        },
        isNull(),
      )
    }
  }
}

fun personEvent(
  eventType: String,
  personId: Long,
  auditModuleName: String = "OCUCNPER",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"personId\": \"$personId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun personAddressEvent(
  eventType: String,
  personId: Long,
  addressId: Long,
  auditModuleName: String = "OCDOAPOP",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"addressId\": \"$addressId\",\"personId\": \"$personId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun personPhoneEvent(
  eventType: String,
  personId: Long,
  phoneId: Long,
  auditModuleName: String = "OCDGNUMB",
  isAddress: Boolean = false,
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"phoneId\": \"$phoneId\",\"personId\": \"$personId\",\"isAddress\": \"$isAddress\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun personAddressPhoneEvent(
  eventType: String,
  personId: Long,
  phoneId: Long,
  addressId: Long,
  auditModuleName: String = "OCDGNUMB",
  isAddress: Boolean = true,
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"phoneId\": \"$phoneId\",\"personId\": \"$personId\",\"addressId\": \"$addressId\",\"isAddress\": \"$isAddress\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun personInternetAddressEvent(
  eventType: String,
  personId: Long,
  internetAddressId: Long,
  auditModuleName: String = "OCDGNUMB",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"internetAddressId\": \"$internetAddressId\",\"personId\": \"$personId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun personEmploymentEvent(
  eventType: String,
  personId: Long,
  employmentSequence: Long,
  auditModuleName: String = "OCDPERSO",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"employmentSequence\": \"$employmentSequence\",\"personId\": \"$personId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun personIdentifierEvent(
  eventType: String,
  personId: Long,
  identifierSequence: Long,
  auditModuleName: String = "OCDPERSO",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"identifierSequence\": \"$identifierSequence\",\"personId\": \"$personId\",\"auditModuleName\":\"$auditModuleName\",\"nomisEventType\":\"$eventType\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun personRestrictionEvent(
  eventType: String,
  restrictionId: Long,
  personId: Long,
  auditModuleName: String = "OMUVREST",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"personId\": \"$personId\",\"visitorRestrictionId\": \"$restrictionId\",\"auditModuleName\":\"$auditModuleName\",\"restrictionType\": \"BAN\",\"effectiveDate\": \"2021-10-15\",\"expiryDate\": \"2022-01-13\",\"enteredById\": \"485887\",\"nomisEventType\":\"VISITOR_RESTRICTS-UPDATED\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun contactEvent(
  eventType: String,
  contactId: Long,
  personId: Long,
  bookingId: Long,
  offenderNo: String,
  auditModuleName: String = "OCDPERSO",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"offenderIdDisplay\": \"$offenderNo\",\"personId\": \"$personId\",\"contactId\": \"$contactId\",\"auditModuleName\":\"$auditModuleName\",\"approvedVisitor\": \"false\",\"nomisEventType\":\"OFFENDER_CONTACT-INSERTED\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun contactRestrictionEvent(
  eventType: String,
  restrictionId: Long,
  personId: Long,
  contactId: Long,
  offenderNo: String,
  auditModuleName: String = "OIUOVRES",
) = // language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"personId\": \"$personId\",\"offenderIdDisplay\": \"$offenderNo\",\"contactPersonId\": \"$contactId\",\"offenderPersonRestrictionId\": \"$restrictionId\",\"auditModuleName\":\"$auditModuleName\",\"restrictionType\": \"BAN\",\"effectiveDate\": \"2021-10-15\",\"expiryDate\": \"2022-01-13\",\"enteredById\": \"485887\",\"nomisEventType\":\"OFF_PERS_RESTRICTS-UPDATED\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
