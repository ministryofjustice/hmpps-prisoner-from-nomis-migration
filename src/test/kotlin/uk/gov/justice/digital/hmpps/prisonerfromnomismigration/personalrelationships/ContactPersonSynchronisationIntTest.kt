package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
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
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.prisonerDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.prisonerReceivedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.countAllMessagesOnDLQQueue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonEmploymentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactForPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactForPrisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactRestrictionEnteredStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonEmployment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonEmploymentCorporate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.contact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.contactAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.contactAddressPhone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.contactEmail
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.contactEmployment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.contactIdentity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.contactPhone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.contactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.mergePrisonerContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.prisonerContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.prisonerContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.resetPrisonerContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MergePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.PrisonerContactAndRestrictionIds
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.PrisonerRelationshipIds
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.ResetPrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactIdentityRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreatePrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactIdentityRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime

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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
              createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val createContactRequest: SyncCreateContactRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact")))
        with(createContactRequest) {
          assertThat(title).isEqualTo("MR")
          assertThat(lastName).isEqualTo("SMITH")
          assertThat(firstName).isEqualTo("JOHN")
          assertThat(middleName).isEqualTo("BOB")
          assertThat(dateOfBirth).isEqualTo("2024-07-19")
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
          assertThat(createdTime).isEqualTo(LocalDateTime.parse("2024-09-01T13:31"))
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
        mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = nomisPersonId, mapping = PersonMappingDto(dpsId = "$dpsContactId", nomisId = nomisPersonId, mappingType = NOMIS_CREATED))
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
              createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
              modifyUserId = "T.SMITH",
              modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
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

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val updateContactRequest: SyncUpdateContactRequest = getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact/$dpsContactId")))
        with(updateContactRequest) {
          assertThat(title).isEqualTo("MR")
          assertThat(lastName).isEqualTo("SMITH")
          assertThat(firstName).isEqualTo("JOHN")
          assertThat(middleName).isEqualTo("BOB")
          assertThat(dateOfBirth).isEqualTo(LocalDate.parse("1965-07-19"))
          assertThat(isStaff).isTrue()
          assertThat(remitter).isTrue()
          assertThat(deceasedFlag).isFalse()
          assertThat(deceasedDate).isNull()
          assertThat(gender).isEqualTo("M")
          assertThat(domesticStatus).isEqualTo("MAR")
          assertThat(languageCode).isEqualTo("EN")
          assertThat(interpreterRequired).isTrue()
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo(LocalDateTime.parse("2024-10-01T13:31"))
        }
      }
    }
  }

  @Nested
  @DisplayName("PERSON-DELETED")
  inner class PersonDeleted {
    private val nomisPersonId = 123456L
    private val dpsContactId = 123456L

    @Nested
    inner class WhenMappingDoesExist {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonIdOrNull(
          nomisPersonId = nomisPersonId,
          mapping = PersonMappingDto(dpsId = dpsContactId.toString(), nomisId = nomisPersonId, mappingType = MIGRATED),
        )
        dpsApiMock.stubDeleteContact(contactId = dpsContactId)
        mappingApiMock.stubDeleteByNomisPersonId(nomisPersonId)

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personEvent(
            eventType = "PERSON-DELETED",
            personId = nomisPersonId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(dpsContactId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the contact from DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/contact/$dpsContactId")))
      }

      @Test
      fun `will delete the person mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/person/nomis-person-id/$nomisPersonId")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonIdOrNull(
          nomisPersonId = nomisPersonId,
          mapping = null,
        )

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personEvent(
            eventType = "PERSON-DELETED",
            personId = nomisPersonId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry for delete ignored`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
          },
          isNull(),
        )
      }
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactAddress(contactAddress().copy(contactAddressId = dpsContactAddressId))
        mappingApiMock.stubCreateAddressMapping()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val request: SyncCreateContactAddressRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact-address")))
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
          assertThat(createdTime).isEqualTo(LocalDateTime.parse("2024-09-01T13:31"))
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
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

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactAddress(contactAddress().copy(contactAddressId = dpsContactAddressId))
        mappingApiMock.stubCreateAddressMappingFollowedBySuccess()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
          eq("contactperson-address-synchronisation-updated-skipped"),
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                  modifyUserId = "T.SMITH",
                  modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubUpdateContactAddress(addressId = dpsContactAddressId)

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
          eq("contactperson-address-synchronisation-updated-success"),
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
        val request: SyncUpdateContactAddressRequest = getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact-address/$dpsContactAddressId")))
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
          assertThat(updatedTime).isEqualTo(LocalDateTime.parse("2024-10-01T13:31"))
        }
      }
    }
  }

  @Nested
  @DisplayName("ADDRESSES_PERSON-DELETED")
  inner class PersonAddressDeleted {

    private val nomisAddressId = 3456L
    private val nomisPersonId = 123456L
    private val dpsContactAddressId = 937373L

    @Nested
    inner class WhenMappingExists {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(
          nomisAddressId = nomisAddressId,
          mapping = PersonAddressMappingDto(
            dpsId = "$dpsContactAddressId",
            nomisId = nomisAddressId,
            mappingType = PersonAddressMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        dpsApiMock.stubDeleteContactAddress(dpsContactAddressId)
        mappingApiMock.stubDeleteByNomisAddressId(nomisAddressId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personAddressEvent(
            eventType = "ADDRESSES_PERSON-DELETED",
            personId = nomisPersonId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-address-synchronisation-deleted-success"),
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
      fun `will delete the address in DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/contact-address/$dpsContactAddressId")))
      }

      @Test
      fun `will delete the address mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/address/nomis-address-id/$nomisAddressId")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisAddressIdOrNull(
          nomisAddressId = nomisAddressId,
          mapping = null,
        )
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personAddressEvent(
            eventType = "ADDRESSES_PERSON-DELETED",
            personId = nomisPersonId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-address-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
          },
          isNull(),
        )
      }
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactPhone(contactPhone().copy(contactPhoneId = dpsContactPhoneId))
        mappingApiMock.stubCreatePhoneMapping()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val request: SyncCreateContactPhoneRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact-phone")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(phoneType).isEqualTo("MOB")
          assertThat(phoneNumber).isEqualTo("07973 555 555")
          assertThat(extNumber).isEqualTo("x555")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo(LocalDateTime.parse("2024-09-01T13:31"))
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
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

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactPhone(contactPhone().copy(contactPhoneId = dpsContactPhoneId))
        mappingApiMock.stubCreatePhoneMappingFollowedBySuccess()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                  modifyUserId = "T.SWIFT",
                  modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubUpdateContactPhone(dpsContactPhoneId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val request: SyncUpdateContactPhoneRequest = getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact-phone/$dpsContactPhoneId")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(phoneType).isEqualTo("MOB")
          assertThat(phoneNumber).isEqualTo("07973 555 555")
          assertThat(extNumber).isEqualTo("x555")
          assertThat(updatedBy).isEqualTo("T.SWIFT")
          assertThat(updatedTime).isEqualTo(LocalDateTime.parse("2024-10-01T13:31"))
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

    private val nomisPhoneId = 3456L
    private val nomisPersonId = 123456L
    private val dpsContactPhoneId = 937373L

    @Nested
    inner class WhenMappingExists {
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
        dpsApiMock.stubDeleteContactPhone(dpsContactPhoneId)
        mappingApiMock.stubDeleteByNomisPhoneId(nomisPhoneId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personPhoneEvent(
            eventType = "PHONES_PERSON-DELETED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["dpsContactPhoneId"]).isEqualTo(dpsContactPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the phone in DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/contact-phone/$dpsContactPhoneId")))
      }

      @Test
      fun `will delete the phone mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/phone/nomis-phone-id/$nomisPhoneId")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(
          nomisPhoneId = nomisPhoneId,
          mapping = null,
        )
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personPhoneEvent(
            eventType = "PHONES_PERSON-DELETED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
          },
          isNull(),
        )
      }
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactAddressPhone(contactAddressPhone().copy(contactAddressPhoneId = dpsContactAddressPhoneId))
        mappingApiMock.stubCreatePhoneMapping()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val request: SyncCreateContactAddressPhoneRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact-address-phone")))
        with(request) {
          assertThat(contactAddressId).isEqualTo(dpsContactAddressId)
          assertThat(phoneType).isEqualTo("MOB")
          assertThat(phoneNumber).isEqualTo("07973 555 555")
          assertThat(extNumber).isEqualTo("x555")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo(LocalDateTime.parse("2024-09-01T13:31"))
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
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

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactAddressPhone(contactAddressPhone().copy(contactAddressPhoneId = dpsContactAddressPhoneId))
        mappingApiMock.stubCreatePhoneMappingFollowedBySuccess()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                  modifyUserId = "T.SWIFT",
                  modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubUpdateContactAddressPhone(dpsContactAddressPhoneId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val request: SyncUpdateContactAddressPhoneRequest = getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact-address-phone/$dpsContactAddressPhoneId")))
        with(request) {
          assertThat(phoneType).isEqualTo("MOB")
          assertThat(phoneNumber).isEqualTo("07973 555 555")
          assertThat(extNumber).isEqualTo("x555")
          assertThat(updatedBy).isEqualTo("T.SWIFT")
          assertThat(updatedTime).isEqualTo(LocalDateTime.parse("2024-10-01T13:31"))
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

    private val nomisPhoneId = 3456L
    private val nomisPersonId = 123456L
    private val nomisAddressId = 652882L
    private val dpsContactAddressPhoneId = 937373L

    @Nested
    inner class WhenMappingExists {
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
        dpsApiMock.stubDeleteContactAddressPhone(dpsContactAddressPhoneId)
        mappingApiMock.stubDeleteByNomisPhoneId(nomisPhoneId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personAddressPhoneEvent(
            eventType = "PHONES_PERSON-DELETED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            assertThat(it["dpsContactAddressPhoneId"]).isEqualTo(dpsContactAddressPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the phone in DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/contact-address-phone/$dpsContactAddressPhoneId")))
      }

      @Test
      fun `will delete the phone mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/phone/nomis-phone-id/$nomisPhoneId")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPhoneIdOrNull(
          nomisPhoneId = nomisPhoneId,
          mapping = null,
        )
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personAddressPhoneEvent(
            eventType = "PHONES_PERSON-DELETED",
            personId = nomisPersonId,
            phoneId = nomisPhoneId,
            addressId = nomisAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-phone-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
          },
          isNull(),
        )
      }
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactEmail(contactEmail().copy(contactEmailId = dpsContactEmailId))
        mappingApiMock.stubCreateEmailMapping()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val request: SyncCreateContactEmailRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact-email")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(emailAddress).isEqualTo("test@test.com")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo(LocalDateTime.parse("2024-09-01T13:31"))
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
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

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactEmail(contactEmail().copy(contactEmailId = dpsContactEmailId))
        mappingApiMock.stubCreateEmailMappingFollowedBySuccess()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                  modifyUserId = "T.SWIFT",
                  modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubUpdateContactEmail(dpsContactEmailId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val request: SyncUpdateContactEmailRequest = getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact-email/$dpsContactEmailId")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(emailAddress).isEqualTo("test@test.com")
          assertThat(updatedBy).isEqualTo("T.SWIFT")
          assertThat(updatedTime).isEqualTo(LocalDateTime.parse("2024-10-01T13:31"))
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

    private val nomisInternetAddressId = 3456L
    private val nomisPersonId = 123456L
    private val dpsContactEmailId = 937373L

    @Nested
    inner class WhenMappingExists {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(
          nomisInternetAddressId = nomisInternetAddressId,
          mapping = PersonEmailMappingDto(
            dpsId = "$dpsContactEmailId",
            nomisId = nomisInternetAddressId,
            mappingType = PersonEmailMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        dpsApiMock.stubDeleteContactEmail(dpsContactEmailId)
        mappingApiMock.stubDeleteByNomisEmailId(nomisInternetAddressId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_PERSON-DELETED",
            personId = nomisPersonId,
            internetAddressId = nomisInternetAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-email-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
            assertThat(it["dpsContactEmailId"]).isEqualTo(dpsContactEmailId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the email in DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/contact-email/$dpsContactEmailId")))
      }

      @Test
      fun `will delete the email mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/email/nomis-internet-address-id/$nomisInternetAddressId")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmailIdOrNull(
          nomisInternetAddressId = nomisInternetAddressId,
          mapping = null,
        )
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personInternetAddressEvent(
            eventType = "INTERNET_ADDRESSES_PERSON-DELETED",
            personId = nomisPersonId,
            internetAddressId = nomisInternetAddressId,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-email-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisInternetAddressId"]).isEqualTo(nomisInternetAddressId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PERSON_EMPLOYMENTS-INSERTED")
  inner class PersonEmploymentAdded {
    private val nomisSequenceNumber = 4L
    private val nomisPersonId = 123456L
    private val dpsContactEmploymentId = 937373L

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personEmploymentEvent(
            eventType = "PERSON_EMPLOYMENTS-INSERTED",
            personId = nomisPersonId,
            employmentSequence = nomisSequenceNumber,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create employment in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/employment")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-employment-synchronisation-created-skipped"),
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
        mappingApiMock.stubGetByNomisEmploymentIdsOrNull(nomisPersonId = nomisPersonId, nomisSequenceNumber = nomisSequenceNumber, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withEmployment(
              PersonEmployment(
                sequence = nomisSequenceNumber,
                corporate = PersonEmploymentCorporate(
                  id = 54321,
                  name = "Police",
                ),
                active = true,
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactEmployment(contactEmployment().copy(employmentId = dpsContactEmploymentId))
        mappingApiMock.stubCreateEmploymentMapping()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personEmploymentEvent(
            eventType = "PERSON_EMPLOYMENTS-INSERTED",
            personId = nomisPersonId,
            employmentSequence = nomisSequenceNumber,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will check if mapping already exists`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/contact-person/employment/nomis-person-id/$nomisPersonId/nomis-sequence-number/$nomisSequenceNumber")))
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will create the employment in DPS from the person`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/employment")))
        val request: SyncCreateEmploymentRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/employment")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(organisationId).isEqualTo(54321L)
          assertThat(active).isEqualTo(true)
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo(LocalDateTime.parse("2024-09-01T13:31"))
        }
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/employment"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactEmploymentId)
            .withRequestBodyJsonPath("nomisPersonId", "$nomisPersonId")
            .withRequestBodyJsonPath("nomisSequenceNumber", "$nomisSequenceNumber"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-employment-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["dpsContactEmploymentId"]).isEqualTo(dpsContactEmploymentId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmploymentIdsOrNull(
          nomisPersonId = nomisPersonId,
          nomisSequenceNumber = nomisSequenceNumber,
          mapping = PersonEmploymentMappingDto(
            dpsId = "$dpsContactEmploymentId",
            nomisSequenceNumber = nomisSequenceNumber,
            nomisPersonId = nomisPersonId,
            mappingType = PersonEmploymentMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personEmploymentEvent(
            eventType = "PERSON_EMPLOYMENTS-INSERTED",
            personId = nomisPersonId,
            employmentSequence = nomisSequenceNumber,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not create employment in DPS`() {
        dpsApiMock.verify(0, postRequestedFor(urlPathEqualTo("/sync/employment")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-employment-synchronisation-created-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["dpsContactEmploymentId"]).isEqualTo(dpsContactEmploymentId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmploymentIdsOrNull(nomisPersonId = nomisPersonId, nomisSequenceNumber = nomisSequenceNumber, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withEmployment(
              PersonEmployment(
                sequence = nomisSequenceNumber,
                corporate = PersonEmploymentCorporate(
                  id = 54321,
                  name = "Police",
                ),
                active = true,
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactEmployment(contactEmployment().copy(employmentId = dpsContactEmploymentId))
        mappingApiMock.stubCreateEmploymentMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PersonEmploymentMappingDto(
                dpsId = dpsContactEmploymentId.toString(),
                nomisSequenceNumber = nomisSequenceNumber,
                nomisPersonId = nomisPersonId,
                mappingType = PersonEmploymentMappingDto.MappingType.NOMIS_CREATED,
              ),
              existing = PersonEmploymentMappingDto(
                dpsId = "9999",
                nomisSequenceNumber = nomisSequenceNumber,
                nomisPersonId = nomisPersonId,
                mappingType = PersonEmploymentMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personEmploymentEvent(
            eventType = "PERSON_EMPLOYMENTS-INSERTED",
            personId = nomisPersonId,
            employmentSequence = nomisSequenceNumber,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-sync-contactperson-duplicate") }
      }

      @Test
      fun `will create the employment in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/employment")))
      }

      @Test
      fun `will attempt to create a mapping between the DPS and NOMIS record once`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/employment"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactEmploymentId)
            .withRequestBodyJsonPath("nomisSequenceNumber", "$nomisSequenceNumber")
            .withRequestBodyJsonPath("nomisPersonId", "$nomisPersonId"),
        )
      }

      @Test
      fun `will track telemetry for both overall success and duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-employment-synchronisation-created-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["dpsContactEmploymentId"]).isEqualTo(dpsContactEmploymentId.toString())
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
            assertThat(it["duplicateDpsContactIdentityId"]).isEqualTo(dpsContactEmploymentId.toString())
            assertThat(it["type"]).isEqualTo("EMPLOYMENT")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmploymentIdsOrNull(nomisPersonId = nomisPersonId, nomisSequenceNumber = nomisSequenceNumber, mapping = null)
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withEmployment(
              PersonEmployment(
                sequence = nomisSequenceNumber,
                corporate = PersonEmploymentCorporate(
                  id = 54321,
                  name = "Police",
                ),
                active = true,
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactEmployment(contactEmployment().copy(employmentId = dpsContactEmploymentId))
        mappingApiMock.stubCreateEmploymentMappingFollowedBySuccess()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personEmploymentEvent(
            eventType = "PERSON_EMPLOYMENTS-INSERTED",
            personId = nomisPersonId,
            employmentSequence = nomisSequenceNumber,
          ),
        ).also { waitForAnyProcessingToComplete("contactperson-employment-mapping-synchronisation-created") }
      }

      @Test
      fun `will create the employment in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/employment")))
      }

      @Test
      fun `will create a mapping between the DPS and NOMIS record`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/employment"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("dpsId", dpsContactEmploymentId)
            .withRequestBodyJsonPath("nomisSequenceNumber", "$nomisSequenceNumber")
            .withRequestBodyJsonPath("nomisPersonId", "$nomisPersonId"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-employment-synchronisation-created-success"),
          check {
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactEmploymentId"]).isEqualTo(dpsContactEmploymentId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PERSON_EMPLOYMENTS-UPDATED")
  inner class PersonEmploymentUpdated {
    private val nomisSequenceNumber = 4L
    private val nomisPersonId = 123456L
    private val dpsContactEmploymentId = 937373L

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personEmploymentEvent(
            eventType = "PERSON_EMPLOYMENTS-UPDATED",
            personId = nomisPersonId,
            employmentSequence = nomisSequenceNumber,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update employment in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/employment/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-employment-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmploymentIds(
          nomisPersonId = nomisPersonId,
          nomisSequenceNumber = nomisSequenceNumber,
          mapping = PersonEmploymentMappingDto(
            dpsId = dpsContactEmploymentId.toString(),
            nomisSequenceNumber = nomisSequenceNumber,
            nomisPersonId = nomisPersonId,
            mappingType = PersonEmploymentMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        nomisApiMock.stubGetPerson(
          person = contactPerson(nomisPersonId)
            .withEmployment(
              PersonEmployment(
                sequence = nomisSequenceNumber,
                corporate = PersonEmploymentCorporate(
                  id = 54321,
                  name = "Police",
                ),
                active = false,
                audit = NomisAudit(
                  createUsername = "J.SPEAK",
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                  modifyUserId = "T.SMITH",
                  modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubUpdateContactEmployment(contactEmploymentId = dpsContactEmploymentId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personEmploymentEvent(
            eventType = "PERSON_EMPLOYMENTS-UPDATED",
            personId = nomisPersonId,
            employmentSequence = nomisSequenceNumber,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will update the employment in DPS from the person`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/employment/$dpsContactEmploymentId")))
        val request: SyncUpdateEmploymentRequest = getRequestBody(putRequestedFor(urlPathEqualTo("/sync/employment/$dpsContactEmploymentId")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(organisationId).isEqualTo(54321)
          assertThat(active).isEqualTo(false)
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo(LocalDateTime.parse("2024-10-01T13:31"))
        }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-employment-synchronisation-updated-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["dpsContactEmploymentId"]).isEqualTo(dpsContactEmploymentId.toString())
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("PERSON_EMPLOYMENTS-DELETED")
  inner class PersonEmploymentDeleted {
    private val nomisSequenceNumber = 4L
    private val nomisPersonId = 123456L
    private val dpsContactEmploymentId = 937373L

    @Nested
    inner class WhenMappingExists {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmploymentIds(
          nomisPersonId = nomisPersonId,
          nomisSequenceNumber = nomisSequenceNumber,
          mapping = PersonEmploymentMappingDto(
            dpsId = dpsContactEmploymentId.toString(),
            nomisSequenceNumber = nomisSequenceNumber,
            nomisPersonId = nomisPersonId,
            mappingType = PersonEmploymentMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        mappingApiMock.stubDeleteByNomisEmploymentIds(
          nomisPersonId = nomisPersonId,
          nomisSequenceNumber = nomisSequenceNumber,
        )

        dpsApiMock.stubDeleteContactEmployment(contactEmploymentId = dpsContactEmploymentId)

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personEmploymentEvent(
            eventType = "PERSON_EMPLOYMENTS-DELETED",
            personId = nomisPersonId,
            employmentSequence = nomisSequenceNumber,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will delete employment in DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/employment/$dpsContactEmploymentId")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-employment-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["dpsContactEmploymentId"]).isEqualTo(dpsContactEmploymentId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the employment mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/employment/nomis-person-id/$nomisPersonId/nomis-sequence-number/$nomisSequenceNumber")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisEmploymentIdsOrNull(nomisPersonId = nomisPersonId, nomisSequenceNumber = nomisSequenceNumber, mapping = null)
        mappingApiMock.stubDeleteByNomisEmploymentIds(nomisPersonId = nomisPersonId, nomisSequenceNumber = nomisSequenceNumber)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personEmploymentEvent(
            eventType = "PERSON_EMPLOYMENTS-DELETED",
            personId = nomisPersonId,
            employmentSequence = nomisSequenceNumber,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-employment-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
          },
          isNull(),
        )
      }
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactIdentity(contactIdentity().copy(contactIdentityId = dpsContactIdentityId))
        mappingApiMock.stubCreateIdentifierMapping()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val request: SyncCreateContactIdentityRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact-identity")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(identityType).isEqualTo("DV")
          assertThat(identityValue).isEqualTo("SMITH777788")
          assertThat(issuingAuthority).isEqualTo("DVLA")
          assertThat(createdBy).isEqualTo("J.SPEAK")
          assertThat(createdTime).isEqualTo(LocalDateTime.parse("2024-09-01T13:31"))
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
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

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactIdentity(contactIdentity().copy(contactIdentityId = dpsContactIdentityId))
        mappingApiMock.stubCreateIdentifierMappingFollowedBySuccess()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
    private val nomisSequenceNumber = 4L
    private val nomisPersonId = 123456L
    private val dpsContactIdentityId = 937373L

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personIdentifierEvent(
            eventType = "PERSON_IDENTIFIERS-UPDATED",
            personId = nomisPersonId,
            identifierSequence = nomisSequenceNumber,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will not update identifier in DPS`() {
        dpsApiMock.verify(0, putRequestedFor(urlPathMatching("/sync/contact-identity/.*")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-identifier-synchronisation-updated-skipped"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisIdentifierIds(
          nomisPersonId = nomisPersonId,
          nomisSequenceNumber = nomisSequenceNumber,
          mapping = PersonIdentifierMappingDto(
            dpsId = dpsContactIdentityId.toString(),
            nomisSequenceNumber = nomisSequenceNumber,
            nomisPersonId = nomisPersonId,
            mappingType = PersonIdentifierMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                  modifyUserId = "T.SMITH",
                  modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubUpdateContactIdentity(contactIdentityId = dpsContactIdentityId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personIdentifierEvent(
            eventType = "PERSON_IDENTIFIERS-UPDATED",
            personId = nomisPersonId,
            identifierSequence = nomisSequenceNumber,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will retrieve the details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/$nomisPersonId")))
      }

      @Test
      fun `will update the identifier in DPS from the person`() {
        dpsApiMock.verify(putRequestedFor(urlPathEqualTo("/sync/contact-identity/$dpsContactIdentityId")))
        val request: SyncUpdateContactIdentityRequest = getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact-identity/$dpsContactIdentityId")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(identityType).isEqualTo("DV")
          assertThat(identityValue).isEqualTo("SMITH777788")
          assertThat(issuingAuthority).isEqualTo("DVLA")
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo(LocalDateTime.parse("2024-10-01T13:31"))
        }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-identifier-synchronisation-updated-success"),
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
  }

  @Nested
  @DisplayName("PERSON_IDENTIFIERS-DELETED")
  inner class PersonIdentifierDeleted {
    private val nomisSequenceNumber = 4L
    private val nomisPersonId = 123456L
    private val dpsContactIdentityId = 937373L

    @Nested
    inner class WhenMappingExists {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisIdentifierIds(
          nomisPersonId = nomisPersonId,
          nomisSequenceNumber = nomisSequenceNumber,
          mapping = PersonIdentifierMappingDto(
            dpsId = dpsContactIdentityId.toString(),
            nomisSequenceNumber = nomisSequenceNumber,
            nomisPersonId = nomisPersonId,
            mappingType = PersonIdentifierMappingDto.MappingType.NOMIS_CREATED,
          ),
        )
        mappingApiMock.stubDeleteByNomisIdentifierIds(
          nomisPersonId = nomisPersonId,
          nomisSequenceNumber = nomisSequenceNumber,
        )

        dpsApiMock.stubDeleteContactIdentity(contactIdentityId = dpsContactIdentityId)

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personIdentifierEvent(
            eventType = "PERSON_IDENTIFIERS-DELETED",
            personId = nomisPersonId,
            identifierSequence = nomisSequenceNumber,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will delete identifier in DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/contact-identity/$dpsContactIdentityId")))
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-identifier-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
            assertThat(it["dpsContactIdentityId"]).isEqualTo(dpsContactIdentityId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will delete the identifier mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/identifier/nomis-person-id/$nomisPersonId/nomis-sequence-number/$nomisSequenceNumber")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisIdentifierIdsOrNull(nomisPersonId = nomisPersonId, nomisSequenceNumber = nomisSequenceNumber, mapping = null)
        mappingApiMock.stubDeleteByNomisIdentifierIds(nomisPersonId = nomisPersonId, nomisSequenceNumber = nomisSequenceNumber)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personIdentifierEvent(
            eventType = "PERSON_IDENTIFIERS-DELETED",
            personId = nomisPersonId,
            identifierSequence = nomisSequenceNumber,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-identifier-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisSequenceNumber"]).isEqualTo(nomisSequenceNumber.toString())
          },
          isNull(),
        )
      }
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactRestriction(contactRestriction().copy(contactRestrictionId = dpsContactRestrictionId))
        mappingApiMock.stubCreatePersonRestrictionMapping()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val request: SyncCreateContactRestrictionRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact-restriction")))
        with(request) {
          // DPS and NOMIS contact/person id are the same
          assertThat(contactId).isEqualTo(nomisPersonId)
          assertThat(restrictionType).isEqualTo("BAN")
          assertThat(startDate).isEqualTo(LocalDate.parse("2021-01-01"))
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2025-01-01"))
          assertThat(comments).isEqualTo("Banned for life")
          assertThat(createdBy).isEqualTo("J.SMITH")
          assertThat(createdTime).isEqualTo(LocalDateTime.parse("2024-09-01T13:31"))
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                  modifyUserId = "J.SPEAK",
                  modifyDatetime = LocalDateTime.parse("2024-09-05T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubUpdateContactRestriction(dpsContactRestrictionId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val request: SyncUpdateContactRestrictionRequest = getRequestBody(putRequestedFor(urlPathEqualTo("/sync/contact-restriction/$dpsContactRestrictionId")))
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
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

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                  createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
                ),
              ),
            ),
        )
        dpsApiMock.stubCreateContactRestriction(contactRestriction().copy(contactRestrictionId = dpsContactRestrictionId))
        mappingApiMock.stubCreatePersonRestrictionMappingFollowedBySuccess()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
    private val nomisPersonRestrictionId = 3456L
    private val nomisPersonId = 123456L
    private val dpsContactRestrictionId = 937373L

    @Nested
    inner class WhenMappingDoesExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = nomisPersonRestrictionId, mapping = PersonRestrictionMappingDto(dpsId = "$dpsContactRestrictionId", nomisId = nomisPersonRestrictionId, mappingType = PersonRestrictionMappingDto.MappingType.NOMIS_CREATED))
        dpsApiMock.stubDeleteContactRestriction(dpsContactRestrictionId)
        mappingApiMock.stubDeleteByNomisPersonRestrictionId(nomisPersonRestrictionId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personRestrictionEvent(
            eventType = "VISITOR_RESTRICTION-DELETED",
            personId = nomisPersonId,
            restrictionId = nomisPersonRestrictionId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will delete the restriction from DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/contact-restriction/$dpsContactRestrictionId")))
      }

      @Test
      fun `will delete the restriction mapping`() {
        mappingApiMock.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/person-restriction/nomis-person-restriction-id/$nomisPersonRestrictionId")))
      }

      @Test
      fun `will track telemetry for delete success`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-restriction-synchronisation-deleted-success"),
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
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = nomisPersonRestrictionId, mapping = null)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          personRestrictionEvent(
            eventType = "VISITOR_RESTRICTION-DELETED",
            personId = nomisPersonId,
            restrictionId = nomisPersonRestrictionId,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry ignoring the delete`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-person-restriction-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisPersonRestrictionId"]).isEqualTo(nomisPersonRestrictionId.toString())
          },
          isNull(),
        )
      }
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        nomisApiMock.stubGetContact(
          nomisContactId,
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
              createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
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
        )
        dpsApiMock.stubCreatePrisonerContact(prisonerContact().copy(id = dpsPrisonerContactId))
        mappingApiMock.stubCreateContactMapping()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
      fun `will retrieve the contact details from NOMIS`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/contact/$nomisContactId")))
      }

      @Test
      fun `will create the prisoner contact in DPS from the person`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/prisoner-contact")))
        val createPrisonerContactRequest: SyncCreatePrisonerContactRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/prisoner-contact")))
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
          assertThat(createdTime).isEqualTo(LocalDateTime.parse("2024-09-01T13:31"))
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        nomisApiMock.stubGetContact(
          nomisContactId,
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
              createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
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

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
    inner class WhenDuplicateContactInDpsMapping {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactIdOrNull(nomisContactId = nomisContactId, mapping = null)
        nomisApiMock.stubGetContact(
          nomisContactId,
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
              createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
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
        )
        dpsApiMock.stubCreatePrisonerContact(HttpStatus.CONFLICT)

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
      fun `will try create the prisoner contact in DPS once`() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/prisoner-contact")))
      }

      @Test
      fun `will not attempt to create a mapping between the DPS and NOMIS`() {
        mappingApiMock.verify(
          0,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/contact")),
        )
      }

      @Test
      fun `will track telemetry for duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-sync-contactperson-duplicate"),
          check {
            assertThat(it["existingNomisContactId"]).isEqualTo(nomisContactId.toString())
            assertThat(it["type"]).isEqualTo("DPS_CONTACT")
          },
          isNull(),
        )
      }

      @Test
      fun `message will not be sent to DLQ`() {
        assertThat(personalRelationshipsOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(0)
      }
    }

    @Nested
    inner class MappingCreateFails {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactIdOrNull(nomisContactId = nomisContactId, mapping = null)
        nomisApiMock.stubGetContact(
          nomisContactId,
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
              createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
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
        )
        dpsApiMock.stubCreatePrisonerContact(prisonerContact().copy(id = dpsPrisonerContactId))
        mappingApiMock.stubCreateContactMappingFollowedBySuccess()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        nomisApiMock.stubGetContact(
          nomisContactId,
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
              createDatetime = LocalDateTime.parse("2024-09-01T13:31"),
              modifyUserId = "T.SMITH",
              modifyDatetime = LocalDateTime.parse("2024-10-01T13:31"),
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
        )
        dpsApiMock.stubUpdatePrisonerContact(prisonerContactId = dpsPrisonerContactId)

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val updatePrisonerContactRequest: SyncUpdatePrisonerContactRequest = getRequestBody(putRequestedFor(urlPathEqualTo("/sync/prisoner-contact/$dpsPrisonerContactId")))
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
          assertThat(updatedTime).isEqualTo(LocalDateTime.parse("2024-10-01T13:31"))
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CONTACT-DELETED")
  inner class ContactDeleted {
    private val nomisContactId = 3456L
    private val nomisPersonId = 123456L
    private val dpsPrisonerContactId = 87474L
    private val bookingId = 890L
    private val offenderNo = "A1234KT"

    @Nested
    inner class WhenMappingExists {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactId(
          nomisContactId = nomisContactId,
          mapping = PersonContactMappingDto(dpsId = dpsPrisonerContactId.toString(), nomisId = nomisContactId, mappingType = PersonContactMappingDto.MappingType.MIGRATED),
        )
        dpsApiMock.stubDeletePrisonerContact(prisonerContactId = dpsPrisonerContactId)
        mappingApiMock.stubDeleteByNomisContactId(nomisContactId)

        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          contactEvent(
            eventType = "OFFENDER_CONTACT-DELETED",
            personId = nomisPersonId,
            contactId = nomisContactId,
            bookingId = bookingId,
            offenderNo = offenderNo,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-synchronisation-deleted-success"),
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
      fun `will delete the prisoner contact from DPS`() {
        dpsApiMock.verify(deleteRequestedFor(urlPathEqualTo("/sync/prisoner-contact/$dpsPrisonerContactId")))
      }

      @Test
      fun `will delete the contact mapping`() {
        mappingApiMock.verify(deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/contact/nomis-contact-id/$nomisContactId")))
      }
    }

    @Nested
    inner class WhenMappingDoesNotExists {

      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactIdOrNull(
          nomisContactId = nomisContactId,
          mapping = null,
        )
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          contactEvent(
            eventType = "OFFENDER_CONTACT-DELETED",
            personId = nomisPersonId,
            contactId = nomisContactId,
            bookingId = bookingId,
            offenderNo = offenderNo,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactId"]).isEqualTo(nomisContactId.toString())
          },
          isNull(),
        )
      }
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                audit = nomisAudit().copy(createDatetime = LocalDateTime.parse("2024-09-01T13:31")),
              ),
            ),
        )

        dpsApiMock.stubCreatePrisonerContactRestriction(prisonerContactRestriction().copy(prisonerContactRestrictionId = dpsPrisonerContactRestrictionId))
        mappingApiMock.stubCreateContactRestrictionMapping()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
        val request: SyncCreatePrisonerContactRestrictionRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction")))
        with(request) {
          assertThat(prisonerContactId).isEqualTo(dpsPrisonerContactId)
          assertThat(restrictionType).isEqualTo("BAN")
          assertThat(comments).isEqualTo("Banned for life")
          assertThat(startDate).isEqualTo("2020-01-01")
          assertThat(expiryDate).isEqualTo("2026-01-01")
          // the entered staff username
          assertThat(createdBy).isEqualTo("Q1251T")
          assertThat(createdTime).isEqualTo(LocalDateTime.parse("2024-09-01T13:31"))
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
                audit = nomisAudit().copy(modifyDatetime = LocalDateTime.parse("2024-09-01T13:31"), modifyUserId = "J.SPEAK"),
              ),
            ),
        )
        dpsContactPersonServer.stubUpdatePrisonerContactRestriction(dpsPrisonerContactRestrictionId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
          getRequestBody(putRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction/$dpsPrisonerContactRestrictionId")))
        with(request) {
          assertThat(restrictionType).isEqualTo("BAN")
          assertThat(startDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2026-01-01"))
          assertThat(comments).isEqualTo("Banned for life")
          assertThat(updatedBy).isEqualTo("J.SMITH")
          assertThat(updatedTime).isEqualTo(LocalDateTime.parse("2024-09-01T13:31"))
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
                audit = nomisAudit().copy(createDatetime = LocalDateTime.parse("2024-09-01T13:31")),
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
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
                audit = nomisAudit().copy(createDatetime = LocalDateTime.parse("2024-09-01T13:31")),
              ),
            ),
        )
        dpsApiMock.stubCreatePrisonerContactRestriction(prisonerContactRestriction().copy(prisonerContactRestrictionId = dpsPrisonerContactRestrictionId))
        mappingApiMock.stubCreateContactRestrictionMappingFollowedBySuccess()
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
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
    private val nomisContactRestrictionId = 3456L
    private val nomisPersonId = 123456L
    private val nomisContactId = 652882L
    private val dpsPrisonerContactRestrictionId = 937373L
    private val offenderNo = "A1234KT"

    @Nested
    inner class WhenMappingExists {
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
        dpsApiMock.stubDeletePrisonerContactRestriction(dpsPrisonerContactRestrictionId)
        mappingApiMock.stubDeleteByNomisContactRestrictionId(nomisContactRestrictionId)
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          contactRestrictionEvent(
            eventType = "PERSON_RESTRICTION-DELETED",
            personId = nomisPersonId,
            restrictionId = nomisContactRestrictionId,
            contactId = nomisContactId,
            offenderNo = offenderNo,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-restriction-synchronisation-deleted-success"),
          check {
            assertThat(it["nomisContactRestrictionId"]).isEqualTo(nomisContactRestrictionId.toString())
            assertThat(it["dpsPrisonerContactRestrictionId"]).isEqualTo(dpsPrisonerContactRestrictionId.toString())
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactId"]).isEqualTo(nomisContactId.toString())
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingDoesNotExists {
      @BeforeEach
      fun setUp() {
        mappingApiMock.stubGetByNomisContactRestrictionIdOrNull(
          nomisContactRestrictionId = nomisContactRestrictionId,
          mapping = null,
        )
        awsSqsPersonalRelationshipsOffenderEventsClient.sendMessage(
          personalRelationshipsQueueOffenderEventsUrl,
          contactRestrictionEvent(
            eventType = "PERSON_RESTRICTION-DELETED",
            personId = nomisPersonId,
            restrictionId = nomisContactRestrictionId,
            contactId = nomisContactId,
            offenderNo = offenderNo,
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry for delete gnore`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-contact-restriction-synchronisation-deleted-ignored"),
          check {
            assertThat(it["nomisContactRestrictionId"]).isEqualTo(nomisContactRestrictionId.toString())
            assertThat(it["nomisPersonId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(nomisPersonId.toString())
            assertThat(it["nomisContactId"]).isEqualTo(nomisContactId.toString())
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prison-offender-events.prisoner.merged")
  inner class PrisonerMerged {
    val offenderNumberRetained = "A1234KT"
    val offenderNumberRemoved = "A1000KT"
    val nomisContactId1 = 123L
    val dpsPrisonerContactId1 = 1123L
    val nomisContactId1RestrictionId1 = 1231L
    val nomisContactId1RestrictionId2 = 1232L
    val dpsPrisonerContactId1RestrictionId1 = 11231L
    val dpsPrisonerContactId1RestrictionId2 = 11232L
    val nomisContactId2 = 234L
    val dpsPrisonerContactId2 = 1234L
    val nomisContactId2RestrictionId1 = 2341L
    val dpsPrisonerContactId2RestrictionId1 = 12341L

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubContactsForPrisoner(
          offenderNo = offenderNumberRetained,
          contacts = prisonerWithContacts().copy(
            contacts = listOf(
              prisonerWithContact().copy(
                id = nomisContactId1,
                relationshipType = CodeDescription(code = "BOF", description = "Boyfriend"),
                contactType = CodeDescription(code = "S", description = "Social/ Family"),
                active = true,
                emergencyContact = true,
                nextOfKin = false,
                approvedVisitor = false,
                bookingSequence = 1,
                person = ContactForPerson(
                  personId = 4321,
                  lastName = "BRIGHT",
                  firstName = "JANE",
                ),
                restrictions = listOf(
                  prisonerWithContactRestriction().copy(
                    id = nomisContactId1RestrictionId1,
                    type = CodeDescription(code = "BAN", description = "Banned"),
                  ),
                  prisonerWithContactRestriction().copy(id = nomisContactId1RestrictionId2),
                ),
              ),
              prisonerWithContact().copy(
                id = nomisContactId2,
                bookingSequence = 2,
                restrictions = listOf(
                  prisonerWithContactRestriction().copy(id = nomisContactId2RestrictionId1),
                ),
              ),
            ),
          ),
        )
        dpsApiMock.stubReplaceMergedPrisonerContacts(
          mergePrisonerContactResponse().copy(
            relationshipsCreated = listOf(
              PrisonerContactAndRestrictionIds(
                contactId = 123,
                relationship = IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT, nomisId = nomisContactId1, dpsId = dpsPrisonerContactId1),
                restrictions = listOf(
                  IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT_RESTRICTION, nomisId = nomisContactId1RestrictionId1, dpsId = dpsPrisonerContactId1RestrictionId1),
                  IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT_RESTRICTION, nomisId = nomisContactId1RestrictionId2, dpsId = dpsPrisonerContactId1RestrictionId2),
                ),
              ),
              PrisonerContactAndRestrictionIds(
                contactId = 123,
                relationship = IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT, nomisId = nomisContactId2, dpsId = dpsPrisonerContactId2),
                restrictions = listOf(
                  IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT_RESTRICTION, nomisId = nomisContactId2RestrictionId1, dpsId = dpsPrisonerContactId2RestrictionId1),
                ),
              ),

            ),
            relationshipsRemoved = listOf(
              PrisonerRelationshipIds(
                prisonerNumber = offenderNumberRemoved,
                contactId = 123,
                prisonerContactId = 10,
                prisonerContactRestrictionIds = listOf(1),
              ),
              PrisonerRelationshipIds(
                prisonerNumber = offenderNumberRemoved,
                contactId = 123,
                prisonerContactId = 20,
                prisonerContactRestrictionIds = listOf(2),
              ),
            ),
          ),
        )

        mappingApiMock.stubReplaceMappingsForPrisoner(offenderNumberRetained)

        personContactsDomainEventsQueue.sendMessage(
          mergeDomainEvent(
            bookingId = 1234,
            offenderNo = offenderNumberRetained,
            removedOffenderNo = offenderNumberRemoved,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will retrieve all contacts for the retained prisoner number`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/$offenderNumberRetained/contacts")))
      }

      @Test
      fun `will replace the contacts in DPS`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/admin/merge")))
      }

      @Test
      fun `all contacts will be sent to DPS along with the prisoner numbers merged`() {
        val request: MergePrisonerContactRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/admin/merge")))
        assertThat(request.removedPrisonerNumber).isEqualTo(offenderNumberRemoved)
        assertThat(request.retainedPrisonerNumber).isEqualTo(offenderNumberRetained)
        assertThat(request.prisonerContacts).hasSize(2)
        with(request.prisonerContacts[0]) {
          assertThat(id).isEqualTo(nomisContactId1)
          assertThat(restrictions).hasSize(2)
          assertThat(restrictions[0].id).isEqualTo(nomisContactId1RestrictionId1)
          assertThat(restrictions[1].id).isEqualTo(nomisContactId1RestrictionId2)
        }
        with(request.prisonerContacts[1]) {
          assertThat(id).isEqualTo(nomisContactId2)
          assertThat(restrictions).hasSize(1)
          assertThat(restrictions[0].id).isEqualTo(nomisContactId2RestrictionId1)
        }
      }

      @Test
      fun `will replace mappings for prisoner`() {
        mappingApiMock.verify(postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$offenderNumberRetained")))
      }

      @Test
      fun `will send mappings to be created`() {
        val request: ContactPersonPrisonerMappingsDto = ContactPersonMappingApiMockServer.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$offenderNumberRetained")))

        assertThat(request.mappingType).isEqualTo(ContactPersonPrisonerMappingsDto.MappingType.NOMIS_CREATED)
        assertThat(request.personContactMapping).hasSize(2)
        with(request.personContactMapping[0]) {
          assertThat(dpsId).isEqualTo("$dpsPrisonerContactId1")
          assertThat(nomisId).isEqualTo(nomisContactId1)
        }
        with(request.personContactMapping[1]) {
          assertThat(dpsId).isEqualTo("$dpsPrisonerContactId2")
          assertThat(nomisId).isEqualTo(nomisContactId2)
        }
        assertThat(request.personContactRestrictionMapping).hasSize(3)
        with(request.personContactRestrictionMapping[0]) {
          assertThat(dpsId).isEqualTo("$dpsPrisonerContactId1RestrictionId1")
          assertThat(nomisId).isEqualTo(nomisContactId1RestrictionId1)
        }
        with(request.personContactRestrictionMapping[1]) {
          assertThat(dpsId).isEqualTo("$dpsPrisonerContactId1RestrictionId2")
          assertThat(nomisId).isEqualTo(nomisContactId1RestrictionId2)
        }
        with(request.personContactRestrictionMapping[2]) {
          assertThat(dpsId).isEqualTo("$dpsPrisonerContactId2RestrictionId1")
          assertThat(nomisId).isEqualTo(nomisContactId2RestrictionId1)
        }
      }

      @Test
      fun `will send mappings to be deleted`() {
        val request: ContactPersonPrisonerMappingsDto = ContactPersonMappingApiMockServer.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$offenderNumberRetained")))

        assertThat(request.personContactMappingsToRemoveByDpsId).containsExactlyInAnyOrder("10", "20")
        assertThat(request.personContactRestrictionMappingsToRemoveByDpsId).containsExactlyInAnyOrder("1", "2")
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-contactperson-merge"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNumberRetained)
            assertThat(it["removedOffenderNo"]).isEqualTo(offenderNumberRemoved)
            assertThat(it["contactsCount"]).isEqualTo("2")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingFailure {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubContactsForPrisoner(offenderNo = offenderNumberRetained)
        dpsApiMock.stubReplaceMergedPrisonerContacts()
        mappingApiMock.stubReplaceMappingsForPrisonerFailureFollowedBySuccess(offenderNumberRetained)

        personContactsDomainEventsQueue.sendMessage(
          mergeDomainEvent(
            bookingId = 1234,
            offenderNo = offenderNumberRetained,
            removedOffenderNo = offenderNumberRemoved,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-synch-contactperson-merge") }
      }

      @Test
      fun `will retrieve all contacts for the retained prisoner number once`() {
        nomisApiMock.verify(1, getRequestedFor(urlPathEqualTo("/prisoners/$offenderNumberRetained/contacts")))
      }

      @Test
      fun `will replace the contacts in DPS once `() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/admin/merge")))
      }

      @Test
      fun `will try replace mappings for prisoner until succeeds `() {
        mappingApiMock.verify(2, postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$offenderNumberRetained")))
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient, times(1)).trackEvent(
          eq("from-nomis-synch-contactperson-merge"),
          any(),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prisoner-offender-search.prisoner.received")
  inner class PrisonerReceived {
    val offenderNumber = "A1234KT"
    val nomisContactId1 = 123L
    val dpsPrisonerContactId1 = 1123L
    val nomisContactId1RestrictionId1 = 1231L
    val nomisContactId1RestrictionId2 = 1232L
    val dpsPrisonerContactId1RestrictionId1 = 11231L
    val dpsPrisonerContactId1RestrictionId2 = 11232L
    val nomisContactId2 = 234L
    val dpsPrisonerContactId2 = 1234L
    val nomisContactId2RestrictionId1 = 2341L
    val dpsPrisonerContactId2RestrictionId1 = 12341L

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubContactsForPrisoner(
          offenderNo = offenderNumber,
          contacts = prisonerWithContacts().copy(
            contacts = listOf(
              prisonerWithContact().copy(
                id = nomisContactId1,
                relationshipType = CodeDescription(code = "BOF", description = "Boyfriend"),
                contactType = CodeDescription(code = "S", description = "Social/ Family"),
                active = true,
                emergencyContact = true,
                nextOfKin = false,
                approvedVisitor = false,
                bookingSequence = 1,
                person = ContactForPerson(
                  personId = 4321,
                  lastName = "BRIGHT",
                  firstName = "JANE",
                ),
                restrictions = listOf(
                  prisonerWithContactRestriction().copy(
                    id = nomisContactId1RestrictionId1,
                    type = CodeDescription(code = "BAN", description = "Banned"),
                  ),
                  prisonerWithContactRestriction().copy(id = nomisContactId1RestrictionId2),
                ),
              ),
              prisonerWithContact().copy(
                id = nomisContactId2,
                bookingSequence = 2,
                restrictions = listOf(
                  prisonerWithContactRestriction().copy(id = nomisContactId2RestrictionId1),
                ),
              ),
            ),
          ),
        )
        dpsApiMock.stubResetPrisonerContacts(
          resetPrisonerContactResponse().copy(
            relationshipsCreated = listOf(
              PrisonerContactAndRestrictionIds(
                contactId = 123,
                relationship = IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT, nomisId = nomisContactId1, dpsId = dpsPrisonerContactId1),
                restrictions = listOf(
                  IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT_RESTRICTION, nomisId = nomisContactId1RestrictionId1, dpsId = dpsPrisonerContactId1RestrictionId1),
                  IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT_RESTRICTION, nomisId = nomisContactId1RestrictionId2, dpsId = dpsPrisonerContactId1RestrictionId2),
                ),
              ),
              PrisonerContactAndRestrictionIds(
                contactId = 123,
                relationship = IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT, nomisId = nomisContactId2, dpsId = dpsPrisonerContactId2),
                restrictions = listOf(
                  IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT_RESTRICTION, nomisId = nomisContactId2RestrictionId1, dpsId = dpsPrisonerContactId2RestrictionId1),
                ),
              ),

            ),
            relationshipsRemoved = listOf(
              PrisonerRelationshipIds(
                prisonerNumber = offenderNumber,
                contactId = 123,
                prisonerContactId = 10,
                prisonerContactRestrictionIds = listOf(1),
              ),
              PrisonerRelationshipIds(
                prisonerNumber = offenderNumber,
                contactId = 123,
                prisonerContactId = 20,
                prisonerContactRestrictionIds = listOf(2),
              ),
            ),
          ),
        )

        mappingApiMock.stubReplaceMappingsForPrisoner(offenderNumber)
      }

      @Nested
      inner class NewBooking {

        @BeforeEach
        fun setUp() {
          personContactsDomainEventsQueue.sendMessage(
            prisonerReceivedDomainEvent(
              offenderNo = offenderNumber,
              reason = "NEW_ADMISSION",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will retrieve all contacts for the prisoner`() {
          nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/$offenderNumber/contacts")))
        }

        @Test
        fun `will reset the contacts in DPS`() {
          dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/admin/reset")))
        }

        @Test
        fun `all contacts will be sent to DPS along`() {
          val request: ResetPrisonerContactRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/admin/reset")))
          assertThat(request.prisonerNumber).isEqualTo(offenderNumber)
          assertThat(request.prisonerContacts).hasSize(2)
          with(request.prisonerContacts[0]) {
            assertThat(id).isEqualTo(nomisContactId1)
            assertThat(restrictions).hasSize(2)
            assertThat(restrictions[0].id).isEqualTo(nomisContactId1RestrictionId1)
            assertThat(restrictions[1].id).isEqualTo(nomisContactId1RestrictionId2)
          }
          with(request.prisonerContacts[1]) {
            assertThat(id).isEqualTo(nomisContactId2)
            assertThat(restrictions).hasSize(1)
            assertThat(restrictions[0].id).isEqualTo(nomisContactId2RestrictionId1)
          }
        }

        @Test
        fun `will replace mappings for prisoner`() {
          mappingApiMock.verify(postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$offenderNumber")))
        }

        @Test
        fun `will send mappings to be created`() {
          val request: ContactPersonPrisonerMappingsDto = ContactPersonMappingApiMockServer.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$offenderNumber")))

          assertThat(request.mappingType).isEqualTo(ContactPersonPrisonerMappingsDto.MappingType.NOMIS_CREATED)
          assertThat(request.personContactMapping).hasSize(2)
          with(request.personContactMapping[0]) {
            assertThat(dpsId).isEqualTo("$dpsPrisonerContactId1")
            assertThat(nomisId).isEqualTo(nomisContactId1)
          }
          with(request.personContactMapping[1]) {
            assertThat(dpsId).isEqualTo("$dpsPrisonerContactId2")
            assertThat(nomisId).isEqualTo(nomisContactId2)
          }
          assertThat(request.personContactRestrictionMapping).hasSize(3)
          with(request.personContactRestrictionMapping[0]) {
            assertThat(dpsId).isEqualTo("$dpsPrisonerContactId1RestrictionId1")
            assertThat(nomisId).isEqualTo(nomisContactId1RestrictionId1)
          }
          with(request.personContactRestrictionMapping[1]) {
            assertThat(dpsId).isEqualTo("$dpsPrisonerContactId1RestrictionId2")
            assertThat(nomisId).isEqualTo(nomisContactId1RestrictionId2)
          }
          with(request.personContactRestrictionMapping[2]) {
            assertThat(dpsId).isEqualTo("$dpsPrisonerContactId2RestrictionId1")
            assertThat(nomisId).isEqualTo(nomisContactId2RestrictionId1)
          }
        }

        @Test
        fun `will send mappings to be deleted`() {
          val request: ContactPersonPrisonerMappingsDto = ContactPersonMappingApiMockServer.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$offenderNumber")))

          assertThat(request.personContactMappingsToRemoveByDpsId).containsExactlyInAnyOrder("10", "20")
          assertThat(request.personContactRestrictionMappingsToRemoveByDpsId).containsExactlyInAnyOrder("1", "2")
        }

        @Test
        fun `will track telemetry for the merge`() {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-contactperson-booking-changed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNumber)
              assertThat(it["contactsCount"]).isEqualTo("2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class SwitchBooking {

        @BeforeEach
        fun setUp() {
          personContactsDomainEventsQueue.sendMessage(
            prisonerReceivedDomainEvent(
              offenderNo = offenderNumber,
              reason = "READMISSION_SWITCH_BOOKING",
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will reset the contacts in DPS`() {
          dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/admin/reset")))
        }

        @Test
        fun `will replace mappings for prisoner`() {
          mappingApiMock.verify(postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$offenderNumber")))
        }

        @Test
        fun `will track telemetry for the merge`() {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-contactperson-booking-changed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNumber)
              assertThat(it["contactsCount"]).isEqualTo("2")
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class MappingFailure {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubContactsForPrisoner(offenderNo = offenderNumber)
        dpsApiMock.stubResetPrisonerContacts()
        mappingApiMock.stubReplaceMappingsForPrisonerFailureFollowedBySuccess(offenderNumber)

        personContactsDomainEventsQueue.sendMessage(
          prisonerReceivedDomainEvent(
            offenderNo = offenderNumber,
            reason = "NEW_ADMISSION",
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-synch-contactperson-booking-changed") }
      }

      @Test
      fun `will retrieve all contacts for the retained prisoner number once`() {
        nomisApiMock.verify(1, getRequestedFor(urlPathEqualTo("/prisoners/$offenderNumber/contacts")))
      }

      @Test
      fun `will replace the contacts in DPS once `() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/admin/reset")))
      }

      @Test
      fun `will try replace mappings for prisoner until succeeds `() {
        mappingApiMock.verify(2, postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$offenderNumber")))
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient, times(1)).trackEvent(
          eq("from-nomis-synch-contactperson-booking-changed"),
          any(),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("prison-offender-events.prisoner.booking.moved")
  inner class BookingMoved {
    val fromOffenderNumber = "A1234KT"
    val toOffenderNumber = "A1000KT"
    val bookingId = 98776L
    val nomisContactId1 = 123L
    val dpsPrisonerContactId1 = 1123L
    val nomisContactId1RestrictionId1 = 1231L
    val nomisContactId1RestrictionId2 = 1232L
    val dpsPrisonerContactId1RestrictionId1 = 11231L
    val dpsPrisonerContactId1RestrictionId2 = 11232L
    val nomisContactId2 = 234L
    val dpsPrisonerContactId2 = 1234L
    val nomisContactId2RestrictionId1 = 2341L
    val dpsPrisonerContactId2RestrictionId1 = 12341L

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubContactsForPrisoner(
          offenderNo = fromOffenderNumber,
          contacts = prisonerWithContacts().copy(
            contacts = listOf(
              prisonerWithContact().copy(
                id = nomisContactId1,
                relationshipType = CodeDescription(code = "BOF", description = "Boyfriend"),
                contactType = CodeDescription(code = "S", description = "Social/ Family"),
                active = true,
                emergencyContact = true,
                nextOfKin = false,
                approvedVisitor = false,
                bookingSequence = 1,
                person = ContactForPerson(
                  personId = 4321,
                  lastName = "BRIGHT",
                  firstName = "JANE",
                ),
                restrictions = listOf(
                  prisonerWithContactRestriction().copy(
                    id = nomisContactId1RestrictionId1,
                    type = CodeDescription(code = "BAN", description = "Banned"),
                  ),
                  prisonerWithContactRestriction().copy(id = nomisContactId1RestrictionId2),
                ),
              ),
              prisonerWithContact().copy(
                id = nomisContactId2,
                bookingSequence = 2,
                restrictions = listOf(
                  prisonerWithContactRestriction().copy(id = nomisContactId2RestrictionId1),
                ),
              ),
            ),
          ),
        )
        dpsApiMock.stubResetPrisonerContacts(
          resetPrisonerContactResponse().copy(
            relationshipsCreated = listOf(
              PrisonerContactAndRestrictionIds(
                contactId = 123,
                relationship = IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT, nomisId = nomisContactId1, dpsId = dpsPrisonerContactId1),
                restrictions = listOf(
                  IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT_RESTRICTION, nomisId = nomisContactId1RestrictionId1, dpsId = dpsPrisonerContactId1RestrictionId1),
                  IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT_RESTRICTION, nomisId = nomisContactId1RestrictionId2, dpsId = dpsPrisonerContactId1RestrictionId2),
                ),
              ),
              PrisonerContactAndRestrictionIds(
                contactId = 123,
                relationship = IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT, nomisId = nomisContactId2, dpsId = dpsPrisonerContactId2),
                restrictions = listOf(
                  IdPair(elementType = IdPair.ElementType.PRISONER_CONTACT_RESTRICTION, nomisId = nomisContactId2RestrictionId1, dpsId = dpsPrisonerContactId2RestrictionId1),
                ),
              ),

            ),
            relationshipsRemoved = listOf(
              PrisonerRelationshipIds(
                prisonerNumber = fromOffenderNumber,
                contactId = 123,
                prisonerContactId = 10,
                prisonerContactRestrictionIds = listOf(1),
              ),
              PrisonerRelationshipIds(
                prisonerNumber = fromOffenderNumber,
                contactId = 123,
                prisonerContactId = 20,
                prisonerContactRestrictionIds = listOf(2),
              ),
            ),
          ),
        )

        mappingApiMock.stubReplaceMappingsForPrisoner(fromOffenderNumber)
      }

      @Nested
      inner class ToPrisonerActive {

        @BeforeEach
        fun setUp() {
          nomisApiMock.stubGetPrisonerDetails(offenderNo = toOffenderNumber, prisonerDetails().copy(location = "MDI", active = true))

          personContactsDomainEventsQueue.sendMessage(
            bookingMovedDomainEvent(
              bookingId = bookingId,
              movedFromNomsNumber = fromOffenderNumber,
              movedToNomsNumber = toOffenderNumber,
            ),
          ).also {
            waitForAnyProcessingToComplete("from-nomis-synch-contactperson-booking-moved", "from-nomis-synch-contactperson-booking-moved-ignored")
          }
        }

        @Test
        fun `will retrieve all contacts for the prisoner`() {
          nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/$fromOffenderNumber/contacts")))
        }

        @Test
        fun `will reset the contacts in DPS`() {
          dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/sync/admin/reset")))
        }

        @Test
        fun `all contacts will be sent to DPS along`() {
          val request: ResetPrisonerContactRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/sync/admin/reset")))
          assertThat(request.prisonerNumber).isEqualTo(fromOffenderNumber)
          assertThat(request.prisonerContacts).hasSize(2)
          with(request.prisonerContacts[0]) {
            assertThat(id).isEqualTo(nomisContactId1)
            assertThat(restrictions).hasSize(2)
            assertThat(restrictions[0].id).isEqualTo(nomisContactId1RestrictionId1)
            assertThat(restrictions[1].id).isEqualTo(nomisContactId1RestrictionId2)
          }
          with(request.prisonerContacts[1]) {
            assertThat(id).isEqualTo(nomisContactId2)
            assertThat(restrictions).hasSize(1)
            assertThat(restrictions[0].id).isEqualTo(nomisContactId2RestrictionId1)
          }
        }

        @Test
        fun `will replace mappings for prisoner`() {
          mappingApiMock.verify(postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$fromOffenderNumber")))
        }

        @Test
        fun `will send mappings to be created`() {
          val request: ContactPersonPrisonerMappingsDto = ContactPersonMappingApiMockServer.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$fromOffenderNumber")))

          assertThat(request.mappingType).isEqualTo(ContactPersonPrisonerMappingsDto.MappingType.NOMIS_CREATED)
          assertThat(request.personContactMapping).hasSize(2)
          with(request.personContactMapping[0]) {
            assertThat(dpsId).isEqualTo("$dpsPrisonerContactId1")
            assertThat(nomisId).isEqualTo(nomisContactId1)
          }
          with(request.personContactMapping[1]) {
            assertThat(dpsId).isEqualTo("$dpsPrisonerContactId2")
            assertThat(nomisId).isEqualTo(nomisContactId2)
          }
          assertThat(request.personContactRestrictionMapping).hasSize(3)
          with(request.personContactRestrictionMapping[0]) {
            assertThat(dpsId).isEqualTo("$dpsPrisonerContactId1RestrictionId1")
            assertThat(nomisId).isEqualTo(nomisContactId1RestrictionId1)
          }
          with(request.personContactRestrictionMapping[1]) {
            assertThat(dpsId).isEqualTo("$dpsPrisonerContactId1RestrictionId2")
            assertThat(nomisId).isEqualTo(nomisContactId1RestrictionId2)
          }
          with(request.personContactRestrictionMapping[2]) {
            assertThat(dpsId).isEqualTo("$dpsPrisonerContactId2RestrictionId1")
            assertThat(nomisId).isEqualTo(nomisContactId2RestrictionId1)
          }
        }

        @Test
        fun `will send mappings to be deleted`() {
          val request: ContactPersonPrisonerMappingsDto = ContactPersonMappingApiMockServer.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$fromOffenderNumber")))

          assertThat(request.personContactMappingsToRemoveByDpsId).containsExactlyInAnyOrder("10", "20")
          assertThat(request.personContactRestrictionMappingsToRemoveByDpsId).containsExactlyInAnyOrder("1", "2")
        }

        @Test
        fun `will track telemetry for the merge`() {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-contactperson-booking-moved"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(fromOffenderNumber)
              assertThat(it["whichPrisoner"]).isEqualTo("FROM")
              assertThat(it["bookingId"]).isEqualTo("$bookingId")
              assertThat(it["contactsCount"]).isEqualTo("2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class ToPrisonerInActive {

        @BeforeEach
        fun setUp() {
          nomisApiMock.stubContactsForPrisoner(offenderNo = toOffenderNumber)
          nomisApiMock.stubGetPrisonerDetails(offenderNo = toOffenderNumber, prisonerDetails().copy(location = "OUT", active = false))
          mappingApiMock.stubReplaceMappingsForPrisoner(toOffenderNumber)

          personContactsDomainEventsQueue.sendMessage(
            bookingMovedDomainEvent(
              bookingId = bookingId,
              movedFromNomsNumber = fromOffenderNumber,
              movedToNomsNumber = toOffenderNumber,
            ),
          ).also {
            waitForAnyProcessingToComplete("from-nomis-synch-contactperson-booking-moved", times = 2)
          }
        }

        @Test
        fun `will retrieve all contacts for both prisoners`() {
          nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/$fromOffenderNumber/contacts")))
          nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/$toOffenderNumber/contacts")))
        }

        @Test
        fun `will reset the contacts in DPS for both prisoner`() {
          dpsApiMock.verify(
            postRequestedFor(urlPathEqualTo("/sync/admin/reset"))
              .withRequestBodyJsonPath("prisonerNumber", fromOffenderNumber),
          )
          dpsApiMock.verify(
            postRequestedFor(urlPathEqualTo("/sync/admin/reset"))
              .withRequestBodyJsonPath("prisonerNumber", toOffenderNumber),
          )
        }

        @Test
        fun `will replace mappings for both prisoners`() {
          mappingApiMock.verify(postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$fromOffenderNumber")))
          mappingApiMock.verify(postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$toOffenderNumber")))
        }

        @Test
        fun `will track telemetry for the move`() {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-contactperson-booking-moved"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(fromOffenderNumber)
              assertThat(it["whichPrisoner"]).isEqualTo("FROM")
              assertThat(it["bookingId"]).isEqualTo("$bookingId")
              assertThat(it["contactsCount"]).isEqualTo("2")
            },
            isNull(),
          )
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-contactperson-booking-moved"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(toOffenderNumber)
              assertThat(it["whichPrisoner"]).isEqualTo("TO")
              assertThat(it["bookingId"]).isEqualTo("$bookingId")
              assertThat(it["contactsCount"]).isEqualTo("1")
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class MappingFailure {
      @BeforeEach
      fun setUp() {
        nomisApiMock.stubContactsForPrisoner(offenderNo = fromOffenderNumber)
        dpsApiMock.stubResetPrisonerContacts()
        mappingApiMock.stubReplaceMappingsForPrisonerFailureFollowedBySuccess(fromOffenderNumber)

        nomisApiMock.stubGetPrisonerDetails(offenderNo = toOffenderNumber, prisonerDetails().copy(location = "MDI", active = true))

        personContactsDomainEventsQueue.sendMessage(
          bookingMovedDomainEvent(
            bookingId = bookingId,
            movedFromNomsNumber = fromOffenderNumber,
            movedToNomsNumber = toOffenderNumber,
          ),
        )
          .also {
            waitForAnyProcessingToComplete("from-nomis-synch-contactperson-booking-moved", "from-nomis-synch-contactperson-booking-moved-ignored")
          }
      }

      @Test
      fun `will retrieve all contacts for the retained prisoner number once`() {
        nomisApiMock.verify(1, getRequestedFor(urlPathEqualTo("/prisoners/$fromOffenderNumber/contacts")))
      }

      @Test
      fun `will replace the contacts in DPS once `() {
        dpsApiMock.verify(1, postRequestedFor(urlPathEqualTo("/sync/admin/reset")))
      }

      @Test
      fun `will try replace mappings for prisoner until succeeds `() {
        mappingApiMock.verify(2, postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/$fromOffenderNumber")))
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient, times(1)).trackEvent(
          eq("from-nomis-synch-contactperson-booking-moved"),
          any(),
          isNull(),
        )
      }
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
