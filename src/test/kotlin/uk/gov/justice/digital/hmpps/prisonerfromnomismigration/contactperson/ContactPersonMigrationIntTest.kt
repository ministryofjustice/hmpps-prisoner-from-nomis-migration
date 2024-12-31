package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
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
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiExtension.Companion.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiMockServer.Companion.migrateContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonPhoneMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactForPrisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactRestrictionEnteredStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonEmployment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonEmploymentCorporate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContactPersonMigrationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: ContactPersonNomisApiMockServer

  private val dpsApiMock = ContactPersonDpsApiExtension.dpsContactPersonServer

  @Autowired
  private lateinit var mappingApiMock: ContactPersonMappingApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/contactperson")
  inner class MigrateAlerts {
    @BeforeEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/contactperson")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(ContactPersonMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/contactperson")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(ContactPersonMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/contactperson")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(ContactPersonMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetPersonIdsToMigrate(content = listOf(PersonIdResponse(1000), PersonIdResponse(2000)))
        mappingApiMock.stubGetByNomisPersonIdOrNull(
          nomisPersonId = 1000,
          mapping = PersonMappingDto(
            dpsId = "10000",
            nomisId = 1000,
            mappingType = PersonMappingDto.MappingType.MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetByNomisPersonIdOrNull(
          nomisPersonId = 2000,
          mapping = PersonMappingDto(
            dpsId = "20000",
            nomisId = 2000,
            mappingType = PersonMappingDto.MappingType.MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will not bother retrieving any person details`() {
        nomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/persons/1000")))
        nomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/persons/2000")))
      }

      @Test
      fun `will mark migration as complete`() {
        webTestClient.get().uri("/migrate/contactperson/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
      }
    }

    @Nested
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetPersonIdsToMigrate(content = listOf(PersonIdResponse(1000), PersonIdResponse(2000)))
        mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = 1000, mapping = null)
        mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = 2000, mapping = null)
        nomisApiMock.stubGetPerson(1000, contactPerson().copy(personId = 1000, firstName = "JOHN", lastName = "SMITH"))
        nomisApiMock.stubGetPerson(2000, contactPerson().copy(personId = 2000, firstName = "ADDO", lastName = "ABOAGYE"))
        dpsApiMock.stubMigrateContact(nomisPersonId = 1000L, migrateContactResponse().copy(contact = IdPair(nomisId = 1000, dpsId = 10_000, elementType = IdPair.ElementType.CONTACT)))
        dpsApiMock.stubMigrateContact(nomisPersonId = 2000L, migrateContactResponse().copy(contact = IdPair(nomisId = 2000, dpsId = 20_000, elementType = IdPair.ElementType.CONTACT)))
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 2)
        migrationResult = performMigration()
      }

      @Test
      fun `will get the count of the number person contacts to migrate`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/ids")))
      }

      @Test
      fun `will get details for each person`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/1000")))
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/persons/2000")))
      }

      @Test
      fun `will create mapping for each person and children`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("personMapping.dpsId", "10000")
            .withRequestBodyJsonPath("personMapping.nomisId", "1000"),
        )
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("personMapping.dpsId", "20000")
            .withRequestBodyJsonPath("personMapping.nomisId", "2000"),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-migration-entity-migrated"),
          check {
            assertThat(it["nomisId"]).isEqualTo("1000")
            assertThat(it["dpsId"]).isEqualTo("10000")
          },
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("contactperson-migration-entity-migrated"),
          check {
            assertThat(it["nomisId"]).isEqualTo("2000")
            assertThat(it["dpsId"]).isEqualTo("20000")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of prisoners migrated`() {
        webTestClient.get().uri("/migrate/contactperson/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("2")
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathNomisToDPSMapping {
      private lateinit var dpsRequests: List<MigrateContactRequest>
      private lateinit var mappingRequests: List<ContactPersonMappingsDto>
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        stubMigratePersons(
          ContactPerson(
            personId = 1000,
            firstName = "JOHN",
            lastName = "SMITH",
            middleName = "MIKE",
            dateOfBirth = LocalDate.parse("1965-07-19"),
            gender = CodeDescription("M", "Male"),
            title = CodeDescription("MR", "Mr"),
            language = CodeDescription("VIE", "Vietnamese"),
            interpreterRequired = true,
            domesticStatus = CodeDescription("M", "Married or in civil partnership"),
            deceasedDate = LocalDate.parse("2020-01-23"),
            isStaff = true,
            audit = NomisAudit(
              modifyUserId = "ADJUA.MENSAH",
              modifyDatetime = "2024-01-02T10:23",
              createUsername = "ADJUA.BEEK",
              createDatetime = "2022-01-02T10:23",
            ),
            phoneNumbers = listOf(
              PersonPhoneNumber(
                phoneId = 10,
                number = "0114 555 5555",
                type = CodeDescription("MOB", "Mobile"),
                audit = NomisAudit(
                  modifyUserId = "ADJUA.MENSAH",
                  modifyDatetime = "2024-02-02T10:23",
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),
                extension = "ext 5555",
              ),
              PersonPhoneNumber(
                phoneId = 11,
                number = "0114 1111 1111111",
                type = CodeDescription("FAX", "Fax"),
                audit = NomisAudit(
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),
              ),
            ),
            addresses = listOf(
              PersonAddress(
                addressId = 101,
                phoneNumbers = listOf(
                  PersonPhoneNumber(
                    phoneId = 101,
                    number = "0113 555 5555",
                    type = CodeDescription("HOM", "Home"),
                    audit = NomisAudit(
                      modifyUserId = "ADJUA.MENSAH",
                      modifyDatetime = "2024-04-02T10:23",
                      createUsername = "ADJUA.BEEK",
                      createDatetime = "2022-04-02T10:23",
                    ),
                    extension = "ext 5555",
                  ),
                ),
                validatedPAF = true,
                primaryAddress = true,
                mailAddress = true,
                audit = NomisAudit(
                  modifyUserId = "ADJUA.MENSAH",
                  modifyDatetime = "2024-03-02T10:23",
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-03-02T10:23",
                ),
                type = CodeDescription("HOME", "Home"),
                flat = "Flat 1B",
                premise = "Pudding Court",
                street = "High Mound",
                locality = "Broomhill",
                postcode = "S1 5GG",
                city = CodeDescription("25343", "Sheffield"),
                county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
                country = CodeDescription("ENG", "England"),
                noFixedAddress = true,
                comment = "Use this address",
                startDate = LocalDate.parse("1987-01-01"),
                endDate = LocalDate.parse("2024-02-01"),
              ),
              PersonAddress(
                addressId = 102,
                phoneNumbers = emptyList(),
                validatedPAF = false,
                primaryAddress = false,
                mailAddress = false,
                audit = NomisAudit(
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-03-02T10:23",
                ),
              ),
            ),
            emailAddresses = listOf(
              PersonEmailAddress(
                emailAddressId = 130,
                email = "test@test.justice.gov.uk",
                audit = NomisAudit(
                  modifyUserId = "ADJUA.MENSAH",
                  modifyDatetime = "2024-02-02T10:23",
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),
              ),
            ),
            employments = listOf(
              PersonEmployment(
                sequence = 1,
                active = true,
                audit = NomisAudit(
                  modifyUserId = "ADJUA.MENSAH",
                  modifyDatetime = "2024-02-02T10:23",
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),
                corporate = PersonEmploymentCorporate(id = 120, name = "Police"),
              ),
            ),
            identifiers = listOf(
              PersonIdentifier(
                sequence = 1,
                type = CodeDescription("PNC", "PNC Number"),
                identifier = "2024/00037373A",
                issuedAuthority = "Police",
                audit = NomisAudit(
                  modifyUserId = "ADJUA.MENSAH",
                  modifyDatetime = "2024-02-02T10:23",
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),

              ),
              PersonIdentifier(
                sequence = 2,
                type = CodeDescription("STAFF", "Staff Pass/ Identity Card"),
                identifier = "6363688",
                audit = NomisAudit(
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),
              ),
            ),
            restrictions = listOf(
              ContactRestriction(
                id = 150,
                type = CodeDescription("BAN", "Banned"),
                effectiveDate = LocalDate.parse("2023-01-01"),
                expiryDate = LocalDate.parse("2026-01-01"),
                enteredStaff = ContactRestrictionEnteredStaff(
                  staffId = 87675,
                  username = "ADJUA.SMITH",
                ),
                comment = "Banned for life!",
                audit = NomisAudit(
                  modifyUserId = "ADJUA.MENSAH",
                  modifyDatetime = "2024-02-02T10:23",
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),

              ),
              ContactRestriction(
                id = 151,
                type = CodeDescription("CCTV", "CCTV"),
                effectiveDate = LocalDate.parse("2023-01-01"),
                enteredStaff = ContactRestrictionEnteredStaff(
                  staffId = 87675,
                  username = "ADJUA.SMITH",
                ),
                audit = NomisAudit(
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),
              ),
            ),
            contacts = listOf(
              PersonContact(
                id = 190,
                contactType = CodeDescription("S", "Social/Family"),
                relationshipType = CodeDescription("BRO", "Brother"),
                active = true,
                approvedVisitor = true,
                nextOfKin = true,
                emergencyContact = true,
                prisoner = ContactForPrisoner(
                  bookingId = 123456,
                  bookingSequence = 1,
                  offenderNo = "A1234KT",
                  lastName = "SMITH",
                  firstName = "JOHN",
                ),
                restrictions = listOf(
                  ContactRestriction(
                    id = 160,
                    type = CodeDescription("BAN", "Banned"),
                    effectiveDate = LocalDate.parse("2023-01-01"),
                    expiryDate = LocalDate.parse("2026-01-01"),
                    enteredStaff = ContactRestrictionEnteredStaff(
                      staffId = 87675,
                      username = "ADJUA.SMITH",
                    ),
                    comment = "Banned for life!",
                    audit = NomisAudit(
                      modifyUserId = "ADJUA.MENSAH",
                      modifyDatetime = "2024-02-02T10:23",
                      createUsername = "ADJUA.BEEK",
                      createDatetime = "2022-02-02T10:23",
                    ),
                  ),
                  ContactRestriction(
                    id = 161,
                    type = CodeDescription("CCTV", "CCTV"),
                    effectiveDate = LocalDate.parse("2023-01-01"),
                    expiryDate = LocalDate.parse("2026-01-01"),
                    enteredStaff = ContactRestrictionEnteredStaff(
                      staffId = 87675,
                      username = "ADJUA.SMITH",
                    ),
                    comment = "Banned for life!",
                    audit = NomisAudit(
                      modifyUserId = null,
                      modifyDatetime = null,
                      createUsername = "ADJUA.BEEK",
                      createDatetime = "2022-02-04T10:23",
                    ),
                  ),
                ),
                audit = NomisAudit(
                  modifyUserId = "ADJUA.MENSAH",
                  modifyDatetime = "2024-02-02T10:23",
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),
                expiryDate = LocalDate.parse("2030-01-01"),
                comment = "Banned",
              ),
              PersonContact(
                id = 191,
                contactType = CodeDescription("S", "Social/Family"),
                relationshipType = CodeDescription("BRO", "Brother"),
                active = false,
                approvedVisitor = false,
                nextOfKin = false,
                emergencyContact = false,
                prisoner = ContactForPrisoner(
                  bookingId = 98,
                  bookingSequence = 2,
                  offenderNo = "A1234KT",
                  lastName = "SMITH",
                  firstName = "JOHN",
                ),
                restrictions = emptyList(),
                audit = NomisAudit(
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),
              ),
            ),
          ),
          ContactPerson(
            personId = 2000,
            firstName = "KWAME",
            lastName = "KOBE",
            interpreterRequired = false,
            audit = NomisAudit(
              createUsername = "ADJUA.BEEK",
              createDatetime = "2022-01-02T10:23",
            ),
            phoneNumbers = emptyList(),
            addresses = emptyList(),
            emailAddresses = emptyList(),
            employments = emptyList(),
            identifiers = emptyList(),
            contacts = emptyList(),
            restrictions = emptyList(),
          ),
        )
        migrationResult = performMigration()
        dpsRequests = getRequestBodies(postRequestedFor(urlPathEqualTo("/migrate/contact")))
        mappingRequests = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/contact-person/migrate")))
      }

      @Test
      fun `will send optional core person data to DPS`() {
        with(dpsRequests.find { it.personId == 1000L } ?: throw AssertionError("Request not found")) {
          assertThat(personId).isEqualTo(1000L)
          assertThat(firstName).isEqualTo("JOHN")
          assertThat(lastName).isEqualTo("SMITH")
          assertThat(dateOfBirth).isEqualTo(LocalDate.parse("1965-07-19"))
          assertThat(gender?.code).isEqualTo("M")
          assertThat(title?.code).isEqualTo("MR")
          assertThat(language?.code).isEqualTo("VIE")
          assertThat(interpreterRequired).isTrue()
          assertThat(domesticStatus?.code).isEqualTo("M")
          assertThat(deceasedDate).isEqualTo(LocalDate.parse("2020-01-23"))
          assertThat(staff).isTrue()
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-01-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-01-02T10:23"))
        }
      }

      @Test
      fun `will send mandatory core person data to DPS`() {
        with(dpsRequests.find { it.personId == 2000L } ?: throw AssertionError("Request not found")) {
          assertThat(personId).isEqualTo(2000L)
          assertThat(firstName).isEqualTo("KWAME")
          assertThat(lastName).isEqualTo("KOBE")
          assertThat(dateOfBirth).isNull()
          assertThat(gender).isNull()
          assertThat(title).isNull()
          assertThat(language).isNull()
          assertThat(interpreterRequired).isFalse()
          assertThat(domesticStatus).isNull()
          assertThat(deceasedDate).isNull()
          assertThat(staff).isFalse()
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-01-02T10:23"))
          assertThat(modifyUsername).isNull()
          assertThat(modifyDateTime).isNull()
          assertThat(phoneNumbers).isEmpty()
          assertThat(addresses).isEmpty()
          assertThat(employments).isEmpty()
          assertThat(emailAddresses).isEmpty()
          assertThat(identifiers).isEmpty()
          assertThat(restrictions).isEmpty()
          assertThat(contacts).isEmpty()
        }
      }

      @Test
      fun `will send global phone numbers to DPS`() {
        with(dpsRequests.find { it.personId == 1000L } ?: throw AssertionError("Request not found")) {
          assertThat(phoneNumbers).hasSize(2)
          with(phoneNumbers[0]) {
            assertThat(phoneId).isEqualTo(10)
            assertThat(number).isEqualTo("0114 555 5555")
            assertThat(type.code).isEqualTo("MOB")
            assertThat(createUsername).isEqualTo("ADJUA.BEEK")
            assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
            assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
            assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-02-02T10:23"))
            assertThat(extension).isEqualTo("ext 5555")
          }
          with(phoneNumbers[1]) {
            assertThat(phoneId).isEqualTo(11)
            assertThat(number).isEqualTo("0114 1111 1111111")
            assertThat(type.code).isEqualTo("FAX")
            assertThat(createUsername).isEqualTo("ADJUA.BEEK")
            assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
            assertThat(modifyUsername).isNull()
            assertThat(modifyDateTime).isNull()
            assertThat(extension).isNull()
          }
        }
      }

      @Test
      fun `will send addresses to DPS`() {
        val person = dpsRequests.find { it.personId == 1000L } ?: throw AssertionError("Request not found")
        assertThat(person.addresses).hasSize(2)
        with(person.addresses[0]) {
          assertThat(addressId).isEqualTo(101)
          assertThat(type?.code).isEqualTo("HOME")
          assertThat(flat).isEqualTo("Flat 1B")
          assertThat(premise).isEqualTo("Pudding Court")
          assertThat(street).isEqualTo("High Mound")
          assertThat(locality).isEqualTo("Broomhill")
          assertThat(county?.code).isEqualTo("S.YORKSHIRE")
          assertThat(country?.code).isEqualTo("ENG")
          assertThat(validatedPAF).isTrue()
          assertThat(noFixedAddress).isTrue()
          assertThat(mailAddress).isTrue()
          assertThat(comment).isEqualTo("Use this address")
          assertThat(startDate).isEqualTo(LocalDate.parse("1987-01-01"))
          assertThat(endDate).isEqualTo(LocalDate.parse("2024-02-01"))
          assertThat(postCode).isEqualTo("S1 5GG")
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-03-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-03-02T10:23"))
        }
        with(person.addresses[1]) {
          assertThat(addressId).isEqualTo(102)
          assertThat(type).isNull()
          assertThat(flat).isNull()
          assertThat(premise).isNull()
          assertThat(street).isNull()
          assertThat(locality).isNull()
          assertThat(county).isNull()
          assertThat(country).isNull()
          assertThat(validatedPAF).isFalse()
          assertThat(noFixedAddress).isNull()
          assertThat(mailAddress).isFalse()
          assertThat(comment).isNull()
          assertThat(startDate).isNull()
          assertThat(endDate).isNull()
          assertThat(postCode).isNull()
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-03-02T10:23"))
          assertThat(modifyUsername).isNull()
          assertThat(modifyDateTime).isNull()
          assertThat(phoneNumbers).isEmpty()
        }
      }

      @Test
      fun `will send address phone numbers to DPS`() {
        val person = dpsRequests.find { it.personId == 1000L } ?: throw AssertionError("Request not found")
        val firstAddress = person.addresses[0]
        assertThat(firstAddress.phoneNumbers).hasSize(1)
        with(firstAddress.phoneNumbers[0]) {
          assertThat(phoneId).isEqualTo(101)
          assertThat(number).isEqualTo("0113 555 5555")
          assertThat(type.code).isEqualTo("HOM")
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-04-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-04-02T10:23"))
          assertThat(extension).isEqualTo("ext 5555")
        }
      }

      @Test
      fun `will send employments to DPS`() {
        val person = dpsRequests.find { it.personId == 1000L } ?: throw AssertionError("Request not found")
        assertThat(person.employments).hasSize(1)
        with(person.employments[0]) {
          assertThat(sequence).isEqualTo(1)
          assertThat(corporate?.id).isEqualTo(120)
          assertThat(corporate?.name).isEqualTo("Police")
          assertThat(active).isTrue()
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-02-02T10:23"))
        }
      }

      @Test
      fun `will send identifiers to DPS`() {
        val person = dpsRequests.find { it.personId == 1000L } ?: throw AssertionError("Request not found")
        assertThat(person.identifiers).hasSize(2)
        with(person.identifiers[0]) {
          assertThat(sequence).isEqualTo(1)
          assertThat(type.code).isEqualTo("PNC")
          assertThat(identifier).isEqualTo("2024/00037373A")
          assertThat(issuedAuthority).isEqualTo("Police")
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-02-02T10:23"))
        }
        with(person.identifiers[1]) {
          assertThat(sequence).isEqualTo(2)
          assertThat(type.code).isEqualTo("STAFF")
          assertThat(identifier).isEqualTo("6363688")
          assertThat(issuedAuthority).isNull()
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
          assertThat(modifyUsername).isNull()
          assertThat(modifyDateTime).isNull()
        }
      }

      @Test
      fun `will send email addresses to DPS`() {
        val person = dpsRequests.find { it.personId == 1000L } ?: throw AssertionError("Request not found")
        assertThat(person.emailAddresses).hasSize(1)
        with(person.emailAddresses[0]) {
          assertThat(emailAddressId).isEqualTo(130)
          assertThat(email).isEqualTo("test@test.justice.gov.uk")
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-02-02T10:23"))
        }
      }

      @Test
      fun `will send restrictions to DPS`() {
        val person = dpsRequests.find { it.personId == 1000L } ?: throw AssertionError("Request not found")
        assertThat(person.restrictions).hasSize(2)
        with(person.restrictions[0]) {
          assertThat(id).isEqualTo(150)
          assertThat(type.code).isEqualTo("BAN")
          assertThat(comment).isEqualTo("Banned for life!")
          assertThat(effectiveDate).isEqualTo(LocalDate.parse("2023-01-01"))
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2026-01-01"))
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
          // will use entered by for modified since entered by will be last real person who modified
          assertThat(modifyUsername).isEqualTo("ADJUA.SMITH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-02-02T10:23"))
        }
        with(person.restrictions[1]) {
          assertThat(id).isEqualTo(151)
          assertThat(type.code).isEqualTo("CCTV")
          assertThat(comment).isNull()
          assertThat(effectiveDate).isEqualTo(LocalDate.parse("2023-01-01"))
          assertThat(expiryDate).isNull()
          // will use entered by for created since there has been no modifications so entered by will be the real person who created restriction
          assertThat(createUsername).isEqualTo("ADJUA.SMITH")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
          assertThat(modifyUsername).isNull()
          assertThat(modifyDateTime).isNull()
        }
      }

      @Test
      fun `will send contacts to DPS`() {
        val person = dpsRequests.find { it.personId == 1000L } ?: throw AssertionError("Request not found")
        assertThat(person.contacts).hasSize(2)
        with(person.contacts[0]) {
          assertThat(id).isEqualTo(190)
          assertThat(contactType.code).isEqualTo("S")
          assertThat(relationshipType.code).isEqualTo("BRO")
          assertThat(currentTerm).isTrue()
          assertThat(active).isTrue()
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2030-01-01"))
          assertThat(approvedVisitor).isTrue()
          assertThat(nextOfKin).isTrue()
          assertThat(emergencyContact).isTrue()
          assertThat(comment).isEqualTo("Banned")
          assertThat(prisonerNumber).isEqualTo("A1234KT")
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-02-02T10:23"))
        }
        with(person.contacts[1]) {
          assertThat(id).isEqualTo(191)
          assertThat(contactType.code).isEqualTo("S")
          assertThat(relationshipType.code).isEqualTo("BRO")
          assertThat(currentTerm).isFalse()
          assertThat(active).isFalse()
          assertThat(expiryDate).isNull()
          assertThat(approvedVisitor).isFalse()
          assertThat(nextOfKin).isFalse()
          assertThat(emergencyContact).isFalse()
          assertThat(comment).isNull()
          assertThat(prisonerNumber).isEqualTo("A1234KT")
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
          assertThat(modifyUsername).isNull()
          assertThat(modifyDateTime).isNull()
          assertThat(restrictions).isEmpty()
        }
      }

      @Test
      fun `will send contact restrictions to DPS`() {
        val person = dpsRequests.find { it.personId == 1000L } ?: throw AssertionError("Request not found")
        assertThat(person.contacts).hasSize(2)
        val contact = person.contacts[0]
        assertThat(contact.restrictions).hasSize(2)
        with(contact.restrictions[0]) {
          assertThat(id).isEqualTo(160)
          assertThat(restrictionType.code).isEqualTo("BAN")
          assertThat(comment).isEqualTo("Banned for life!")
          assertThat(startDate).isEqualTo(LocalDate.parse("2023-01-01"))
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2026-01-01"))
          // will use entered by for modified since entered by will be last real person who modified
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.SMITH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-02-02T10:23"))
        }
        with(contact.restrictions[1]) {
          // will use entered by for created since there has been no modifications so entered by will be the real person who created restriction
          assertThat(createUsername).isEqualTo("ADJUA.SMITH")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-04T10:23"))
          assertThat(modifyUsername).isNull()
          assertThat(modifyDateTime).isNull()
        }
      }

      @Test
      fun `will create mappings for nomis person to dps contact`() {
        // mock will return a dpsId which is nomisId*10

        with(mappingRequests.find { it.personMapping.nomisId == 1000L } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(ContactPersonMappingsDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(personMapping.nomisId).isEqualTo(1000L)
          assertThat(personMapping.dpsId).isEqualTo("10000")
        }
        with(mappingRequests.find { it.personMapping.nomisId == 2000L } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(ContactPersonMappingsDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(personMapping.nomisId).isEqualTo(2000L)
          assertThat(personMapping.dpsId).isEqualTo("20000")
        }
      }

      @Test
      fun `will create mappings for nomis addresses to dps address`() {
        with(mappingRequests.find { it.personMapping.nomisId == 1000L }?.personAddressMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(2)
          assertThat(this[0].nomisId).isEqualTo(101L)
          assertThat(this[0].dpsId).isEqualTo("1010")
          assertThat(this[1].nomisId).isEqualTo(102L)
          assertThat(this[1].dpsId).isEqualTo("1020")
        }
        with(mappingRequests.find { it.personMapping.nomisId == 2000L }?.personAddressMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }

      @Test
      fun `will create mappings for nomis phones to dps phones including from the addresses`() {
        with(mappingRequests.find { it.personMapping.nomisId == 1000L }?.personPhoneMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(3)
          assertThat(this[0].nomisId).isEqualTo(10L)
          assertThat(this[0].dpsId).isEqualTo("100")
          assertThat(this[0].dpsPhoneType).isEqualTo(ContactPersonPhoneMappingIdDto.DpsPhoneType.PERSON)
          assertThat(this[1].nomisId).isEqualTo(11L)
          assertThat(this[1].dpsId).isEqualTo("110")
          assertThat(this[1].dpsPhoneType).isEqualTo(ContactPersonPhoneMappingIdDto.DpsPhoneType.PERSON)
          assertThat(this[2].nomisId).isEqualTo(101L)
          assertThat(this[2].dpsId).isEqualTo("1010")
          assertThat(this[2].dpsPhoneType).isEqualTo(ContactPersonPhoneMappingIdDto.DpsPhoneType.ADDRESS)
        }
        with(mappingRequests.find { it.personMapping.nomisId == 2000L }?.personPhoneMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }

      @Test
      fun `will create mappings for nomis emails to dps emails`() {
        with(mappingRequests.find { it.personMapping.nomisId == 1000L }?.personEmailMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(1)
          assertThat(this[0].nomisId).isEqualTo(130L)
          assertThat(this[0].dpsId).isEqualTo("1300")
        }
        with(mappingRequests.find { it.personMapping.nomisId == 2000L }?.personEmailMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }

      @Test
      fun `will create mappings for nomis employments to dps employments`() {
        with(mappingRequests.find { it.personMapping.nomisId == 1000L }?.personEmploymentMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(1)
          assertThat(this[0].nomisSequenceNumber).isEqualTo(1L)
          assertThat(this[0].nomisPersonId).isEqualTo(1000L)
          assertThat(this[0].dpsId).isEqualTo("10")
        }
        with(mappingRequests.find { it.personMapping.nomisId == 2000L }?.personEmploymentMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }

      @Test
      fun `will create mappings for nomis identifiers to dps identifiers`() {
        with(mappingRequests.find { it.personMapping.nomisId == 1000L }?.personIdentifierMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(2)
          assertThat(this[0].nomisSequenceNumber).isEqualTo(1L)
          assertThat(this[0].nomisPersonId).isEqualTo(1000L)
          assertThat(this[0].dpsId).isEqualTo("10")
          assertThat(this[1].nomisSequenceNumber).isEqualTo(2L)
          assertThat(this[1].nomisPersonId).isEqualTo(1000L)
          assertThat(this[1].dpsId).isEqualTo("20")
        }
        with(mappingRequests.find { it.personMapping.nomisId == 2000L }?.personIdentifierMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }

      @Test
      fun `will create mappings for nomis person restrictions to dps global restrictions`() {
        with(mappingRequests.find { it.personMapping.nomisId == 1000L }?.personRestrictionMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(2)
          assertThat(this[0].nomisId).isEqualTo(150L)
          assertThat(this[0].dpsId).isEqualTo("1500")
          assertThat(this[1].nomisId).isEqualTo(151L)
          assertThat(this[1].dpsId).isEqualTo("1510")
        }
        with(mappingRequests.find { it.personMapping.nomisId == 2000L }?.personRestrictionMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }

      @Test
      fun `will create mappings for nomis contact to dps contact prisoner relationship`() {
        with(mappingRequests.find { it.personMapping.nomisId == 1000L }?.personContactMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(2)
          assertThat(this[0].nomisId).isEqualTo(190L)
          assertThat(this[0].dpsId).isEqualTo("1900")
          assertThat(this[1].nomisId).isEqualTo(191L)
          assertThat(this[1].dpsId).isEqualTo("1910")
        }
        with(mappingRequests.find { it.personMapping.nomisId == 2000L }?.personContactMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }

      @Test
      fun `will create mappings for nomis contact restrictions to dps contact prisoner restrictions`() {
        with(mappingRequests.find { it.personMapping.nomisId == 1000L }?.personContactRestrictionMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(2)
          assertThat(this[0].nomisId).isEqualTo(160L)
          assertThat(this[0].dpsId).isEqualTo("1600")
          assertThat(this[1].nomisId).isEqualTo(161L)
          assertThat(this[1].dpsId).isEqualTo("1610")
        }
        with(mappingRequests.find { it.personMapping.nomisId == 2000L }?.personContactRestrictionMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }
    }

    @Nested
    inner class MappingErrorRecovery {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetPersonIdsToMigrate(content = listOf(PersonIdResponse(1000)))
        mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = 1000, mapping = null)
        nomisApiMock.stubGetPerson(1000, contactPerson().copy(personId = 1000, firstName = "JOHN", lastName = "SMITH"))
        dpsApiMock.stubMigrateContact(migrateContactResponse().copy(contact = IdPair(nomisId = 1000, dpsId = 10_000, elementType = IdPair.ElementType.CONTACT)))
        mappingApiMock.stubCreateMappingsForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will get details for person only once`() {
        nomisApiMock.verify(1, getRequestedFor(urlPathEqualTo("/persons/1000")))
      }

      @Test
      fun `will attempt create mapping twice before succeeding`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("personMapping.dpsId", "10000")
            .withRequestBodyJsonPath("personMapping.nomisId", "1000"),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-migration-entity-migrated"),
          check {
            assertThat(it["nomisId"]).isEqualTo("1000")
            assertThat(it["dpsId"]).isEqualTo("10000")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of prisoners migrated`() {
        webTestClient.get().uri("/migrate/contactperson/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("1")
      }
    }

    @Nested
    inner class DuplicateMappingErrorHandling {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetPersonIdsToMigrate(content = listOf(PersonIdResponse(1000)))
        mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = 1000, mapping = null)
        nomisApiMock.stubGetPerson(1000, contactPerson().copy(personId = 1000, firstName = "JOHN", lastName = "SMITH"))
        dpsApiMock.stubMigrateContact(migrateContactResponse().copy(contact = IdPair(nomisId = 1000, dpsId = 10_000, elementType = IdPair.ElementType.CONTACT)))
        mappingApiMock.stubCreateMappingsForMigration(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PersonMappingDto(
                dpsId = "1000",
                nomisId = 100,
                mappingType = MIGRATED,
              ),
              existing = PersonMappingDto(
                dpsId = "999",
                nomisId = 100,
                mappingType = MIGRATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will get details for person only once`() {
        nomisApiMock.verify(1, getRequestedFor(urlPathEqualTo("/persons/1000")))
      }

      @Test
      fun `will attempt create mapping once before failing`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("personMapping.dpsId", "10000")
            .withRequestBodyJsonPath("personMapping.nomisId", "1000"),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("nomis-migration-contactperson-duplicate"),
          check {
            assertThat(it["duplicateNomisId"]).isEqualTo("100")
            assertThat(it["duplicateDpsId"]).isEqualTo("1000")
            assertThat(it["existingNomisId"]).isEqualTo("100")
            assertThat(it["existingDpsId"]).isEqualTo("999")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of prisoners migrated`() {
        webTestClient.get().uri("/migrate/contactperson/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("0")
      }
    }
  }

  @Nested
  @DisplayName("GET /migrate/contactperson/history")
  inner class GetAll {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.CONTACTPERSON,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.CONTACTPERSON,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T03:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.CONTACTPERSON,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-03T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-03T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-03T03:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.CONTACTPERSON,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/contactperson/history")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/contactperson/history")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/contactperson/history")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `can read all records`() {
      webTestClient.get().uri("/migrate/contactperson/history")
        .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(4)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-02T02:00:00")
        .jsonPath("$[2].migrationId").isEqualTo("2020-01-02T00:00:00")
        .jsonPath("$[3].migrationId").isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    fun `can also use the syscon generic role`() {
      webTestClient.get().uri("/migrate/contactperson/history")
        .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  @DisplayName("GET /migrate/contactperson/history/{migrationId}")
  inner class Get {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.CONTACTPERSON,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/contactperson/history/2020-01-01T00:00:00")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/contactperson/history/2020-01-01T00:00:00")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/contactperson/history/2020-01-01T00:00:00")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `can read record`() {
      webTestClient.get().uri("/migrate/contactperson/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.status").isEqualTo("COMPLETED")
    }

    @Test
    fun `can also use the syscon generic role`() {
      webTestClient.get().uri("/migrate/contactperson/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  @DisplayName("GET /migrate/contactperson/active-migration")
  inner class GetActiveMigration {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.STARTED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.CONTACTPERSON,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2019-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2019-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2019-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.CONTACTPERSON,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/contactperson/active-migration")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/contactperson/active-migration")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/contactperson/active-migration")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/contactperson/active-migration")
        .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").doesNotExist()
        .jsonPath("$.whenStarted").doesNotExist()
        .jsonPath("$.recordsMigrated").doesNotExist()
        .jsonPath("$.estimatedRecordCount").doesNotExist()
        .jsonPath("$.status").doesNotExist()
        .jsonPath("$.migrationType").doesNotExist()
    }

    @Test
    internal fun `can read active migration data`() {
      mappingApiMock.stubGetMigrationDetails(migrationId = "2020-01-01T00%3A00%3A00", count = 123456)
      webTestClient.get().uri("/migrate/contactperson/active-migration")
        .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.whenStarted").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.recordsMigrated").isEqualTo(123456)
        .jsonPath("$.toBeProcessedCount").isEqualTo(0)
        .jsonPath("$.beingProcessedCount").isEqualTo(0)
        .jsonPath("$.recordsFailed").isEqualTo(0)
        .jsonPath("$.estimatedRecordCount").isEqualTo(123567)
        .jsonPath("$.status").isEqualTo("STARTED")
        .jsonPath("$.migrationType").isEqualTo("CONTACTPERSON")
    }

    @Test
    fun `can also use the syscon generic role`() {
      mappingApiMock.stubGetMigrationDetails(migrationId = "2020-01-01T00%3A00%3A00", count = 123456)
      webTestClient.get().uri("/migrate/contactperson/active-migration")
        .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  @DisplayName("POST /migrate/contactperson/{migrationId}/cancel/")
  inner class TerminateMigrationAlerts {
    @BeforeEach
    internal fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/contactperson/{migrationId}/cancel", "some id")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/contactperson/{migrationId}/cancel", "some id")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/contactperson/{migrationId}/cancel", "some id")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/contactperson/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    internal fun `will terminate a running migration`() {
      // slow the API calls so there is time to cancel before it completes
      nomisApi.setGlobalFixedDelay(1000)
      nomisApiMock.stubGetPersonIdsToMigrate(content = listOf(PersonIdResponse(1000), PersonIdResponse(2000)))
      mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = 1000, mapping = null)
      mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = 2000, mapping = null)
      nomisApiMock.stubGetPerson(1000, contactPerson().copy(personId = 1000, firstName = "JOHN", lastName = "SMITH"))
      nomisApiMock.stubGetPerson(2000, contactPerson().copy(personId = 2000, firstName = "ADDO", lastName = "ABOAGYE"))
      dpsApiMock.stubMigrateContact()
      mappingApiMock.stubCreateMappingsForMigration()
      mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 2)

      val migrationId = performMigration().migrationId

      webTestClient.post().uri("/migrate/contactperson/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/contactperson/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/contactperson/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationId)
          .jsonPath("$.status").isEqualTo("CANCELLED")
      }
    }
  }

  private fun performMigration(body: ContactPersonMigrationFilter = ContactPersonMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/contactperson")
    .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(body)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("contactperson-migration-completed"),
      any(),
      isNull(),
    )
  }

  private fun stubMigratePersons(vararg nomisPersonContacts: ContactPerson) {
    dpsApiMock.resetAll()
    mappingApiMock.resetAll()
    nomisApiMock.stubGetPersonIdsToMigrate(content = nomisPersonContacts.map { PersonIdResponse(it.personId) })
    nomisPersonContacts.forEach {
      mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = it.personId, mapping = null)
      nomisApiMock.stubGetPerson(it.personId, it)
      dpsApiMock.stubMigrateContact(nomisPersonId = it.personId, migrateContactResponse(it.toDpsMigrateContactRequest()))
    }
    mappingApiMock.stubCreateMappingsForMigration()
    mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = nomisPersonContacts.size)
  }
}
