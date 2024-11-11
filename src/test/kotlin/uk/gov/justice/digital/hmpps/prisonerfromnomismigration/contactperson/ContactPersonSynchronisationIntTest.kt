package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.contact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.CreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate

class ContactPersonSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: ContactPersonNomisApiMockServer

  private val dpsApiMock = ContactPersonDpsApiExtension.dpsContactPersonServer

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
          personId = nomisPersonId,
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
            dateOfBirth = LocalDate.parse("1965-07-19"),
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
        val createContactRequest: CreateContactRequest = ContactPersonDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/sync/contact")))
        with(createContactRequest) {
          assertThat(title).isEqualTo("MR")
          assertThat(lastName).isEqualTo("SMITH")
          assertThat(firstName).isEqualTo("JOHN")
          assertThat(middleName).isEqualTo("BOB")
          assertThat(dateOfBirth).isEqualTo(LocalDate.parse("1965-07-19"))
          assertThat(estimatedIsOverEighteen).isNull()
          assertThat(relationship).isNull()
          // TODO - check why this is in the request
          assertThat(placeOfBirth).isNull()
          // TODO - check why this is in the request - currently setting to true
          assertThat(active).isTrue()
          // TODO - check why this is in the request - this is always flas in NOMIS
          assertThat(suspended).isFalse()
          assertThat(isStaff).isTrue()
          // TODO - is this a duplicate of the above
          assertThat(staff).isTrue()
          assertThat(remitter).isTrue()
          assertThat(deceasedFlag).isFalse()
          assertThat(deceasedDate).isNull()
          assertThat(coronerNumber).isNull()
          // TODO - not clear is this is a code
          assertThat(gender).isEqualTo("M")
          assertThat(domesticStatus).isEqualTo("MAR")
          assertThat(languageCode).isEqualTo("EN")
          // TODO - check why this is in the request
          assertThat(nationalityCode).isNull()
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
          personId = nomisPersonId,
          person = contactPerson(),
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
          eq("from-nomis-synch-contactperson-duplicate"),
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
          personId = nomisPersonId,
          person = contactPerson(),
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
    private val personId = 123456L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personEvent(
          eventType = "PERSON-UPDATED",
          personId = personId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-synchronisation-updated-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
        },
        isNull(),
      )
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
    private val personId = 123456L
    private val addressId = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personAddressEvent(
          eventType = "ADDRESSES_PERSON-INSERTED",
          personId = personId,
          addressId = addressId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-address-synchronisation-created-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["addressId"]).isEqualTo(addressId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("ADDRESSES_PERSON-UPDATED")
  inner class PersonAddressUpdated {
    private val personId = 123456L
    private val addressId = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personAddressEvent(
          eventType = "ADDRESSES_PERSON-UPDATED",
          personId = personId,
          addressId = addressId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-address-synchronisation-updated-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["addressId"]).isEqualTo(addressId.toString())
        },
        isNull(),
      )
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
    private val personId = 123456L
    private val phoneId = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personPhoneEvent(
          eventType = "PHONES_PERSON-INSERTED",
          personId = personId,
          phoneId = phoneId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-phone-synchronisation-created-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["phoneId"]).isEqualTo(phoneId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("PHONES_PERSON-UPDATED")
  inner class PersonPhoneUpdated {
    private val personId = 123456L
    private val phoneId = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personPhoneEvent(
          eventType = "PHONES_PERSON-UPDATED",
          personId = personId,
          phoneId = phoneId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-phone-synchronisation-updated-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["phoneId"]).isEqualTo(phoneId.toString())
        },
        isNull(),
      )
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
    private val personId = 123456L
    private val phoneId = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personPhoneEvent(
          eventType = "PHONES_PERSON-INSERTED",
          personId = personId,
          phoneId = phoneId,
          isAddress = true,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-address-phone-synchronisation-created-todo"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["phoneId"]).isEqualTo(phoneId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("PHONES_PERSON-UPDATED - address")
  inner class PersonAddressPhoneUpdated {
    private val personId = 123456L
    private val phoneId = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personPhoneEvent(
          eventType = "PHONES_PERSON-UPDATED",
          personId = personId,
          phoneId = phoneId,
          isAddress = true,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-address-phone-synchronisation-updated-todo"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["phoneId"]).isEqualTo(phoneId.toString())
        },
        isNull(),
      )
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
    private val personId = 123456L
    private val internetAddressId = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personInternetAddressEvent(
          eventType = "INTERNET_ADDRESSES_PERSON-INSERTED",
          personId = personId,
          internetAddressId = internetAddressId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-email-synchronisation-created-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["internetAddressId"]).isEqualTo(internetAddressId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("INTERNET_ADDRESSES_PERSON-UPDATED")
  inner class PersonEmailUpdated {
    private val personId = 123456L
    private val internetAddressId = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personInternetAddressEvent(
          eventType = "INTERNET_ADDRESSES_PERSON-UPDATED",
          personId = personId,
          internetAddressId = internetAddressId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-email-synchronisation-updated-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["internetAddressId"]).isEqualTo(internetAddressId.toString())
        },
        isNull(),
      )
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
    private val personId = 123456L
    private val identifierSequence = 76543L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personIdentifierEvent(
          eventType = "PERSON_IDENTIFIERS-INSERTED",
          personId = personId,
          identifierSequence = identifierSequence,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-identifier-synchronisation-created-success"),
        check {
          assertThat(it["personId"]).isEqualTo(personId.toString())
          assertThat(it["identifierSequence"]).isEqualTo(identifierSequence.toString())
        },
        isNull(),
      )
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
    private val restrictionId = 9876L
    private val personId = 123456L

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        personRestrictionEvent(
          eventType = "VISITOR_RESTRICTION-UPSERTED",
          personId = personId,
          restrictionId = restrictionId,
        ),
      ).also { waitForAnyProcessingToComplete() }
    }

    @Test
    fun `will track telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("contactperson-person-restriction-synchronisation-created-success"),
        check {
          assertThat(it["personRestrictionId"]).isEqualTo(restrictionId.toString())
          assertThat(it["personId"]).isEqualTo(personId.toString())
        },
        isNull(),
      )
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
    private val contactId = 3456L
    private val personId = 123456L
    private val bookingId = 890L
    private val offenderNo = "A1234KT"

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        contactEvent(
          eventType = "OFFENDER_CONTACT-INSERTED",
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
        eq("contactperson-contact-synchronisation-created-success"),
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
  @DisplayName("OFFENDER_CONTACT-UPDATED")
  inner class ContactUpdated {
    private val contactId = 3456L
    private val personId = 123456L
    private val bookingId = 890L
    private val offenderNo = "A1234KT"

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        contactEvent(
          eventType = "OFFENDER_CONTACT-UPDATED",
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
        eq("contactperson-contact-synchronisation-updated-success"),
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
    private val restrictionId = 9876L
    private val personId = 123456L
    private val contactId = 3456L
    private val offenderNo = "A1234KT"

    @BeforeEach
    fun setUp() {
      awsSqsContactPersonOffenderEventsClient.sendMessage(
        contactPersonQueueOffenderEventsUrl,
        contactRestrictionEvent(
          eventType = "PERSON_RESTRICTION-UPSERTED",
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
        eq("contactperson-contact-restriction-synchronisation-created-success"),
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
) =
  // language=JSON
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
) =
  // language=JSON
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
) =
  // language=JSON
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

fun personInternetAddressEvent(
  eventType: String,
  personId: Long,
  internetAddressId: Long,
  auditModuleName: String = "OCDGNUMB",
) =
  // language=JSON
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
) =
  // language=JSON
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
) =
  // language=JSON
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
) =
  // language=JSON
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
) =
  // language=JSON
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
) =
  // language=JSON
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
