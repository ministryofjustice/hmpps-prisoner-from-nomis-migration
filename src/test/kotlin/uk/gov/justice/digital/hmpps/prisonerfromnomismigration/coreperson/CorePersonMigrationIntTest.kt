package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiMockServer.Companion.migrateCorePersonResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonPhoneMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderAddressUsage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderDisability
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderInterestToImmigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderNationality
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderNationalityDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderSexualOrientation
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
class CorePersonMigrationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisCorePersonApiMock: CorePersonNomisApiMockServer

  private val cprApiMock = CorePersonCprApiExtension.cprCorePersonServer

  @Autowired
  private lateinit var mappingApiMock: CorePersonMappingApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/core-person")
  inner class MigrateCorePerson {
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
        webTestClient.post().uri("/migrate/core-person")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CorePersonMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/core-person")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CorePersonMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/core-person")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CorePersonMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonerIds(totalElements = 2, pageSize = 10, firstOffenderNo = "A0001BC")
        mappingApiMock.stubGetByNomisPrisonNumberOrNull(
          nomisPrisonNumber = "A0001BC",
          mapping = CorePersonMappingDto(
            cprId = "10000",
            nomisPrisonNumber = "A0001BC",
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetByNomisPrisonNumberOrNull(
          nomisPrisonNumber = "A0002BC",
          mapping = CorePersonMappingDto(
            cprId = "20000",
            nomisPrisonNumber = "A0002BC",
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will not bother retrieving any core person details`() {
        nomisCorePersonApiMock.verify(0, getRequestedFor(urlPathEqualTo("/core-person/A0001BC")))
        nomisCorePersonApiMock.verify(0, getRequestedFor(urlPathEqualTo("/core-person/B0002BC")))
      }

      @Test
      fun `will mark migration as complete`() {
        webTestClient.get().uri("/migrate/core-person/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
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
        nomisApi.stubGetPrisonerIds(totalElements = 2, pageSize = 10, firstOffenderNo = "A0001BC")
        mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A0001BC", mapping = null)
        mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A0002BC", mapping = null)

        nomisCorePersonApiMock.stubGetCorePerson(
          prisonNumber = "A0001BC",
          corePerson(prisonNumber = "A0001BC").copy(
            offenders = listOf(
              corePerson().offenders[0].copy(
                firstName = "JOHN",
                lastName = "SMITH",
              ),
            ),
          ),
        )
        nomisCorePersonApiMock.stubGetCorePerson(
          prisonNumber = "A0002BC",
          corePerson(prisonNumber = "A0002BC").copy(
            offenders = listOf(
              corePerson().offenders[0].copy(
                firstName = "ADDO",
                lastName = "ABOAGYE",
              ),
            ),
          ),
        )
        cprApiMock.stubMigrateCorePerson(
          nomisPrisonNumber = "A0001BC",
          migrateCorePersonResponse().copy(nomisPrisonNumber = "A0001BC", cprId = "CPR-A0001BC"),
        )
        cprApiMock.stubMigrateCorePerson(
          nomisPrisonNumber = "A0002BC",
          migrateCorePersonResponse().copy(nomisPrisonNumber = "A0002BC", cprId = "CPR-A0002BC"),
        )
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 2)
        migrationResult = performMigration()
      }

      @Test
      fun `will get the count of the number offenders to migrate`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/ids/all")))
      }

      @Test
      fun `will get core person details for each offender`() {
        nomisCorePersonApiMock.verify(getRequestedFor(urlPathEqualTo("/core-person/A0001BC")))
        nomisCorePersonApiMock.verify(getRequestedFor(urlPathEqualTo("/core-person/A0002BC")))
      }

      @Test
      fun `will create mapping for each person and children`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/core-person/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("personMapping.cprId", "CPR-A0001BC")
            .withRequestBodyJsonPath("personMapping.nomisPrisonNumber", "A0001BC"),
        )
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/core-person/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("personMapping.cprId", "CPR-A0002BC")
            .withRequestBodyJsonPath("personMapping.nomisPrisonNumber", "A0002BC"),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo("A0001BC")
            assertThat(it["cprId"]).isEqualTo("CPR-A0001BC")
          },
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("coreperson-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo("A0002BC")
            assertThat(it["cprId"]).isEqualTo("CPR-A0002BC")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of prisoners migrated`() {
        webTestClient.get().uri("/migrate/core-person/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
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
    inner class HappyPathNomisToCPRMapping {
      private lateinit var cprRequests: List<MigrateCorePersonRequest>
      private lateinit var mappingRequests: List<CorePersonMappingsDto>
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        stubMigrateCorePersons(
          CorePerson(
            prisonNumber = "A0001BC",
            activeFlag = true,
            inOutStatus = "IN",
            offenders = listOf(
              CoreOffender(
                offenderId = 1,
                title = CodeDescription(code = "MR", description = "Mr"),
                firstName = "JOHN",
                middleName1 = "FRED",
                middleName2 = "JAMES",
                lastName = "SMITH",
                workingName = true,
                dateOfBirth = LocalDate.parse("1980-01-01"),
                birthPlace = "LONDON",
                birthCountry = CodeDescription(code = "ENG", description = "England"),
                ethnicity = CodeDescription(code = "BLACK", description = "Black"),
                sex = CodeDescription(code = "M", description = "Male"),
                nameType = CodeDescription(code = "MAID", description = "Maiden"),
                identifiers = listOf(
                  Identifier(
                    sequence = 1,
                    type = CodeDescription("PNC", "PNC Number"),
                    identifier = "20/0071818T",
                    issuedAuthority = "Met Police",
                    issuedDate = LocalDate.parse("2020-01-01"),
                    verified = true,
                  ),
                  Identifier(
                    sequence = 2,
                    type = CodeDescription("CID", "CID Number"),
                    identifier = "ABWERJKL",
                    verified = false,
                  ),
                ),
              ),
              CoreOffender(
                offenderId = 2,
                firstName = "JIM",
                lastName = "SMITH",
                workingName = false,
                identifiers = emptyList(),
              ),
            ),
            sentenceStartDates = listOf(LocalDate.parse("1980-01-01")),
            phoneNumbers = listOf(
              OffenderPhoneNumber(
                phoneId = 10,
                number = "0114 555 5555",
                type = CodeDescription("MOB", "Mobile"),
                extension = "ext 5555",
              ),
              OffenderPhoneNumber(
                phoneId = 11,
                number = "0114 1111 1111111",
                type = CodeDescription("FAX", "Fax"),
              ),
            ),
            addresses = listOf(
              OffenderAddress(
                addressId = 101,
                phoneNumbers = listOf(
                  OffenderPhoneNumber(
                    phoneId = 101,
                    number = "0113 555 5555",
                    type = CodeDescription("HOM", "Home"),
                    extension = "ext 5555",
                  ),
                ),
                validatedPAF = true,
                primaryAddress = true,
                mailAddress = true,
                usages = listOf(OffenderAddressUsage(addressId = 201, usage = CodeDescription("HOME", "Home"), active = true)),
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
              OffenderAddress(
                addressId = 102,
                validatedPAF = false,
                primaryAddress = false,
                mailAddress = false,
                phoneNumbers = emptyList(),
                usages = emptyList(),
              ),
            ),
            emailAddresses = listOf(
              OffenderEmailAddress(
                emailAddressId = 130,
                email = "test@test.justice.gov.uk",
              ),
            ),

            nationalities = listOf(
              OffenderNationality(
                bookingId = 1125444,
                nationality = CodeDescription("BRIT", "British"),
                startDateTime = "2016-08-18T19:58:23",
                latestBooking = true,
              ),
              OffenderNationality(
                bookingId = 914459,
                nationality = CodeDescription("MG", "Malagasy"),
                startDateTime = "2012-01-11T16:45:02",
                endDateTime = "2014-09-05T10:55:00",
                latestBooking = false,
              ),
            ),
            nationalityDetails = listOf(
              OffenderNationalityDetails(
                bookingId = 1125444,
                details = "ROTL 23/01/2023",
                startDateTime = "2016-08-19T19:58:23",
                latestBooking = true,
              ),
              OffenderNationalityDetails(
                bookingId = 914459,
                details = "Claims to be from Madagascar",
                startDateTime = "2012-01-12T16:45:02",
                endDateTime = "2014-09-05T10:55:00",
                latestBooking = false,
              ),
            ),
            sexualOrientations = listOf(
              OffenderSexualOrientation(
                bookingId = 1125444,
                sexualOrientation = CodeDescription("HET", "Heterosexual"),
                startDateTime = "2016-08-19T19:58:23",
                latestBooking = true,
              ),
              OffenderSexualOrientation(
                bookingId = 914459,
                sexualOrientation = CodeDescription("ND", "Not disclosed"),
                startDateTime = "2012-01-12T16:45:02",
                endDateTime = "2014-09-05T10:55:00",
                latestBooking = false,
              ),
            ),
            disabilities = listOf(
              OffenderDisability(
                bookingId = 1125444,
                disability = true,
                startDateTime = "2016-08-19T19:58:23",
                latestBooking = true,
              ),
              OffenderDisability(
                bookingId = 914459,
                disability = false,
                startDateTime = "2012-01-12T16:45:02",
                endDateTime = "2014-09-05T10:55:00",
                latestBooking = false,
              ),
            ),
            interestsToImmigration = listOf(
              OffenderInterestToImmigration(
                bookingId = 1125444,
                startDateTime = "2016-08-19T19:58:23",
                interestToImmigration = true,
                latestBooking = true,
              ),
              OffenderInterestToImmigration(
                bookingId = 914459,
                startDateTime = "2012-01-12T16:45:02",
                endDateTime = "2014-09-05T10:55:00",
                interestToImmigration = false,
                latestBooking = false,

              ),
            ),
            beliefs = listOf(
              OffenderBelief(
                beliefId = 2,
                belief = CodeDescription("DRU", "Druid"),
                startDate = LocalDate.parse("2016-08-02"),
                verified = true,
                audit = NomisAudit(
                  createDatetime = "2016-08-01",
                  createUsername = "KOFEADDY",
                  createDisplayName = "KOFE ADDY",
                ),
                changeReason = true,
                comments = "No longer believes in Zoroastrianism",
              ),
              OffenderBelief(
                beliefId = 1,
                belief = CodeDescription("ZORO", "Zoroastrian"),
                startDate = LocalDate.parse("2016-06-01"),
                endDate = LocalDate.parse("2016-08-02"),
                verified = true,
                audit = NomisAudit(
                  createDatetime = "2016-08-01",
                  createUsername = "KOFEADDY",
                  createDisplayName = "KOFE ADDY",
                  modifyUserId = "JIMADM`",
                  modifyDisplayName = "Jimmy Admin",
                  modifyDatetime = "2016-08-02T10:55:00",
                ),
                changeReason = true,
                comments = "New believer",
              ),
            ),
          ),

          CorePerson(
            prisonNumber = "A0002BC",
            activeFlag = false,
            sentenceStartDates = listOf(LocalDate.parse("1981-02-02")),
            nationalities = emptyList(),
            nationalityDetails = emptyList(),
            sexualOrientations = emptyList(),
            disabilities = emptyList(),
            interestsToImmigration = emptyList(),
            beliefs = emptyList(),
            offenders = listOf(
              CoreOffender(
                offenderId = 3,
                firstName = "KWAME",
                lastName = "KOBE",
                workingName = true,
                identifiers = emptyList(),
              ),
            ),
            phoneNumbers = emptyList(),
            addresses = emptyList(),
            emailAddresses = emptyList(),
          ),
        )
        migrationResult = performMigration()
        cprRequests =
          CorePersonCprApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/syscon-sync")))
        mappingRequests =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person/migrate")))
      }

      @Test
      fun `will send optional core person data to CPR`() {
        with(cprRequests.find { it.nomisPrisonNumber == "A0001BC" } ?: throw AssertionError("Request not found")) {
          assertThat(nomisPrisonNumber).isEqualTo("A0001BC")
          assertThat(activeFlag).isEqualTo(true)
          assertThat(inOutStatus).isEqualTo("IN")
          // TODO Add more fields as CPR request is updated
        }
      }

      @Test
      fun `will send mandatory core person data to CPR`() {
        with(cprRequests.find { it.nomisPrisonNumber == "A0002BC" } ?: throw AssertionError("Request not found")) {
          assertThat(nomisPrisonNumber).isEqualTo("A0002BC")
          assertThat(activeFlag).isFalse()
          assertThat(inOutStatus).isNull()

          assertThat(offenders.size).isEqualTo(1)
          assertThat(offenders[0].firstName).isEqualTo("KWAME")
          assertThat(offenders[0].middleName1).isNull()
          assertThat(offenders[0].middleName2).isNull()
          assertThat(offenders[0].lastName).isEqualTo("KOBE")
          assertThat(phoneNumbers).isEmpty()
          assertThat(addresses).isEmpty()
          assertThat(emailAddresses).isEmpty()
          // TODO Add more fields as CPR request is updated
        }
      }

      @Test
      fun `will send offender details to CPR`() {
        with(cprRequests.find { it.nomisPrisonNumber == "A0001BC" } ?: throw AssertionError("Request not found")) {
          assertThat(offenders).hasSize(2)
          with(offenders[0]) {
            assertThat(nomisOffenderId).isEqualTo(1)
            assertThat(title).isEqualTo("Mr")
            assertThat(firstName).isEqualTo("JOHN")
            assertThat(middleName1).isEqualTo("FRED")
            assertThat(middleName2).isEqualTo("JAMES")
            assertThat(lastName).isEqualTo("SMITH")
            assertThat(dateOfBirth).isEqualTo(LocalDate.parse("1980-01-01"))
            assertThat(birthPlace).isEqualTo("LONDON")
            assertThat(birthCountry).isEqualTo("ENG")
            assertThat(race).isEqualTo("BLACK")
            assertThat(sex).isEqualTo("M")
            assertThat(nameType).isEqualTo("MAID")
            assertThat(workingName).isTrue()
          }
          with(offenders[1]) {
            assertThat(nomisOffenderId).isEqualTo(2)
            assertThat(title).isNull()
            assertThat(firstName).isEqualTo("JIM")
            assertThat(middleName1).isNull()
            assertThat(middleName2).isNull()
            assertThat(lastName).isEqualTo("SMITH")
            assertThat(workingName).isFalse()
            assertThat(dateOfBirth).isNull()
            assertThat(birthPlace).isNull()
            assertThat(birthCountry).isNull()
            assertThat(race).isNull()
            assertThat(sex).isNull()
            assertThat(nameType).isNull()
            assertThat(workingName).isFalse()
          }
        }
      }

      @Test
      fun `will send offender identifier details to CPR`() {
        with(cprRequests.find { it.nomisPrisonNumber == "A0001BC" } ?: throw AssertionError("Request not found")) {
          assertThat(offenders).hasSize(2)
          with(offenders[0]) {
            assertThat(identifiers).hasSize(2)
            with(this.identifiers[0]) {
              assertThat(nomisSequence).isEqualTo(1)
              assertThat(type).isEqualTo("PNC")
              assertThat(identifier).isEqualTo("20/0071818T")
              assertThat(issuedBy).isEqualTo("Met Police")
              assertThat(issuedDate).isEqualTo(LocalDate.parse("2020-01-01"))
              assertThat(verified).isTrue()
            }
            with(this.identifiers[1]) {
              assertThat(nomisSequence).isEqualTo(2)
              assertThat(type).isEqualTo("CID")
              assertThat(identifier).isEqualTo("ABWERJKL")
              assertThat(issuedBy).isNull()
              assertThat(issuedDate).isNull()
              assertThat(verified).isFalse()
            }
          }
          assertThat(offenders[1].identifiers).hasSize(0)
        }
      }

      @Test
      fun `will send phone numbers to CPR`() {
        with(cprRequests.find { it.nomisPrisonNumber == "A0001BC" } ?: throw AssertionError("Request not found")) {
          assertThat(phoneNumbers).hasSize(2)
          with(phoneNumbers[0]) {
            assertThat(nomisPhoneId).isEqualTo(10)
            assertThat(phoneNumber).isEqualTo("0114 555 5555")
            assertThat(phoneType).isEqualTo("MOB")
            assertThat(phoneExtension).isEqualTo("ext 5555")
          }
          with(phoneNumbers[1]) {
            assertThat(nomisPhoneId).isEqualTo(11)
            assertThat(phoneNumber).isEqualTo("0114 1111 1111111")
            assertThat(phoneType).isEqualTo("FAX")
            assertThat(phoneExtension).isNull()
          }
        }
      }

      @Test
      fun `will send addresses to CPR`() {
        val corePerson = cprRequests.find { it.nomisPrisonNumber == "A0001BC" } ?: throw AssertionError("Request not found")
        assertThat(corePerson.addresses).hasSize(2)
        with(corePerson.addresses[0]) {
          assertThat(nomisAddressId).isEqualTo(101)
          assertThat(isPrimary).isTrue()
          assertThat(usages[0].usage).isEqualTo("HOME")
          assertThat(flat).isEqualTo("Flat 1B")
          assertThat(premise).isEqualTo("Pudding Court")
          assertThat(street).isEqualTo("High Mound")
          assertThat(locality).isEqualTo("Broomhill")
          assertThat(town).isEqualTo("Sheffield")
          assertThat(county).isEqualTo("S.YORKSHIRE")
          assertThat(country).isEqualTo("ENG")
          assertThat(postcode).isEqualTo("S1 5GG")
          assertThat(noFixedAddress).isTrue()
          assertThat(mail).isTrue()
          assertThat(comment).isEqualTo("Use this address")
          assertThat(startDate).isEqualTo(LocalDate.parse("1987-01-01"))
          assertThat(endDate).isEqualTo(LocalDate.parse("2024-02-01"))
        }
        with(corePerson.addresses[1]) {
          assertThat(nomisAddressId).isEqualTo(102)
          assertThat(isPrimary).isFalse()
          assertThat(usages).isEmpty()
          assertThat(flat).isNull()
          assertThat(premise).isNull()
          assertThat(street).isNull()
          assertThat(locality).isNull()
          assertThat(town).isNull()
          assertThat(county).isNull()
          assertThat(country).isNull()
          assertThat(postcode).isNull()
          assertThat(noFixedAddress).isFalse()
          assertThat(mail).isFalse()
          assertThat(comment).isNull()
          assertThat(startDate).isNull()
          assertThat(endDate).isNull()
        }
      }

      @Test
      fun `will send email addresses to CPR`() {
        val corePerson = cprRequests.find { it.nomisPrisonNumber == "A0001BC" } ?: throw AssertionError("Request not found")
        assertThat(corePerson.emailAddresses).hasSize(1)
        with(corePerson.emailAddresses[0]) {
          assertThat(nomisEmailAddressId).isEqualTo(130)
          assertThat(emailAddress).isEqualTo("test@test.justice.gov.uk")
        }
      }

      @Test
      fun `will send religion details to CPR`() {
        val corePerson =
          cprRequests.find { it.nomisPrisonNumber == "A0001BC" } ?: throw AssertionError("Request not found")
        assertThat(corePerson.religions).hasSize(2)
        with(corePerson.religions[0]) {
          assertThat(nomisBeliefId).isEqualTo(2)
          assertThat(religion).isEqualTo("Druid")
          assertThat(startDate).isEqualTo("2016-08-02")
          assertThat(endDate).isNull()
          assertThat(changeReason).isTrue()
          assertThat(comment).isEqualTo("No longer believes in Zoroastrianism")
          assertThat(createdByDisplayName).isEqualTo("KOFE ADDY")
          assertThat(updatedDisplayName).isNull()
        }
        with(corePerson.religions[1]) {
          assertThat(nomisBeliefId).isEqualTo(1)
          assertThat(religion).isEqualTo("Zoroastrian")
          assertThat(startDate).isEqualTo("2016-06-01")
          assertThat(endDate).isEqualTo("2016-08-02")
          assertThat(changeReason).isTrue()
          assertThat(comment).isEqualTo("New believer")
          assertThat(createdByDisplayName).isEqualTo("KOFE ADDY")
          assertThat(updatedDisplayName).isEqualTo("Jimmy Admin")
        }
      }

      // TODO Add tests for other children added to CPR request

      @Test
      fun `will create mappings for nomis person to cpr core person`() {
        // mock will return a cprId which is CPR-A0001BC

        with(mappingRequests.find { it.personMapping.nomisPrisonNumber == "A0001BC" } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(CorePersonMappingsDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(personMapping.nomisPrisonNumber).isEqualTo("A0001BC")
          assertThat(personMapping.cprId).isEqualTo("CPR-A0001BC")
        }
        with(mappingRequests.find { it.personMapping.nomisPrisonNumber == "A0002BC" } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(CorePersonMappingsDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(personMapping.nomisPrisonNumber).isEqualTo("A0002BC")
          assertThat(personMapping.cprId).isEqualTo("CPR-A0002BC")
        }
      }

      // TODO Add tests for Offender Mappings
      // TODO Add tests for Religion/Belief Mappings

      @Test
      fun `will create mappings for nomis addresses to cpr address`() {
        with(mappingRequests.find { it.personMapping.nomisPrisonNumber == "A0001BC" }?.addressMappings ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(2)
          assertThat(this[0].nomisId).isEqualTo(101L)
          assertThat(this[0].cprId).isEqualTo("CPR-101")
          assertThat(this[1].nomisId).isEqualTo(102L)
          assertThat(this[1].cprId).isEqualTo("CPR-102")
        }
        with(mappingRequests.find { it.personMapping.nomisPrisonNumber == "A0002BC" }?.addressMappings ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }

      @Test
      fun `will create mappings for nomis phones to cpr phones including from the addresses`() {
        with(mappingRequests.find { it.personMapping.nomisPrisonNumber == "A0001BC" }?.phoneMappings ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(2)
          assertThat(this[0].nomisId).isEqualTo(10L)
          assertThat(this[0].cprId).isEqualTo("CPR-10")
          assertThat(this[0].cprPhoneType).isEqualTo(CorePersonPhoneMappingIdDto.CprPhoneType.CORE_PERSON)
          assertThat(this[1].nomisId).isEqualTo(11L)
          assertThat(this[1].cprId).isEqualTo("CPR-11")
          assertThat(this[1].cprPhoneType).isEqualTo(CorePersonPhoneMappingIdDto.CprPhoneType.CORE_PERSON)
        }
        with(mappingRequests.find { it.personMapping.nomisPrisonNumber == "A0002BC" }?.phoneMappings ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }

      /* TODO Add in
      @Test
      fun `will create mappings for nomis emails to cpr emails`() {
        with(mappingRequests.find { it.personMapping.nomisPrisonNumber == "A0001BC" }?.emailMappings ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(1)
          assertThat(this[0].nomisId).isEqualTo(130L)
          assertThat(this[0].cprId).isEqualTo("CPR-130")
        }
        with(mappingRequests.find { it.personMapping.nomisPrisonNumber == "A0002BC" }?.emailMappings ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }
       */
    }

    @Nested
    inner class MappingErrorRecovery {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonerIds(totalElements = 1, pageSize = 10, firstOffenderNo = "A0001BC")
        mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A0001BC", mapping = null)
        nomisCorePersonApiMock.stubGetCorePerson(
          prisonNumber = "A0001BC",
          corePerson(prisonNumber = "A0001BC").copy(
            offenders = listOf(
              corePerson().offenders[0].copy(
                firstName = "JOHN",
                lastName = "SMITH",
              ),
            ),
          ),
        )
        cprApiMock.stubMigrateCorePerson(
          nomisPrisonNumber = "A0001BC",
          migrateCorePersonResponse().copy(nomisPrisonNumber = "A0001BC", cprId = "CPR-A0001BC"),
        )
        mappingApiMock.stubCreateMappingsForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will get details for person only once`() {
        nomisCorePersonApiMock.verify(1, getRequestedFor(urlPathEqualTo("/core-person/A0001BC")))
      }

      @Test
      fun `will attempt create mapping twice before succeeding`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/core-person/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("personMapping.cprId", "CPR-A0001BC")
            .withRequestBodyJsonPath("personMapping.nomisPrisonNumber", "A0001BC"),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo("A0001BC")
            assertThat(it["cprId"]).isEqualTo("CPR-A0001BC")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of core persons migrated`() {
        webTestClient.get().uri("/migrate/core-person/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
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
        nomisApi.stubGetPrisonerIds(totalElements = 1, pageSize = 10, firstOffenderNo = "A0001BC")
        mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A0001BC", mapping = null)
        nomisCorePersonApiMock.stubGetCorePerson(
          prisonNumber = "A0001BC",
          corePerson(prisonNumber = "A0001BC").copy(
            offenders = listOf(
              corePerson().offenders[0].copy(
                firstName = "JOHN",
                lastName = "SMITH",
              ),
            ),
          ),
        )
        cprApiMock.stubMigrateCorePerson(
          nomisPrisonNumber = "A0001BC",
          migrateCorePersonResponse().copy(nomisPrisonNumber = "A0001BC", cprId = "CPR-A0001BC"),
        )
        mappingApiMock.stubCreateMappingsForMigration(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = CorePersonMappingDto(
                cprId = "CPR-A0001BC",
                nomisPrisonNumber = "A0001BC",
                mappingType = MIGRATED,
              ),
              existing = CorePersonMappingDto(
                cprId = "CPR-A0001XX",
                nomisPrisonNumber = "A0001BC",
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
        nomisCorePersonApiMock.verify(1, getRequestedFor(urlPathEqualTo("/core-person/A0001BC")))
      }

      @Test
      fun `will attempt create mapping once before failing`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/core-person/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("personMapping.cprId", "CPR-A0001BC")
            .withRequestBodyJsonPath("personMapping.nomisPrisonNumber", "A0001BC"),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("nomis-migration-coreperson-duplicate"),
          check {
            assertThat(it["duplicateNomisPrisonNumber"]).isEqualTo("A0001BC")
            assertThat(it["duplicateCprId"]).isEqualTo("CPR-A0001BC")
            assertThat(it["existingNomisPrisonNumber"]).isEqualTo("A0001BC")
            assertThat(it["existingCprId"]).isEqualTo("CPR-A0001XX")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of core persons (offenders) migrated`() {
        webTestClient.get().uri("/migrate/core-person/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
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
  @DisplayName("GET /migrate/core-person/history")
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
            migrationType = MigrationType.CORE_PERSON,
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
            migrationType = MigrationType.CORE_PERSON,
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
            migrationType = MigrationType.CORE_PERSON,
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
            migrationType = MigrationType.CORE_PERSON,
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
        webTestClient.get().uri("/migrate/core-person/history")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/core-person/history")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/core-person/history")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `can read all records`() {
      webTestClient.get().uri("/migrate/core-person/history")
        .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
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
      webTestClient.get().uri("/migrate/core-person/history")
        .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  @DisplayName("GET /migrate/core-person/history/{migrationId}")
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
            migrationType = MigrationType.CORE_PERSON,
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
        webTestClient.get().uri("/migrate/core-person/history/2020-01-01T00:00:00")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/core-person/history/2020-01-01T00:00:00")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/core-person/history/2020-01-01T00:00:00")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `can read record`() {
      webTestClient.get().uri("/migrate/core-person/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.status").isEqualTo("COMPLETED")
    }

    @Test
    fun `can also use the syscon generic role`() {
      webTestClient.get().uri("/migrate/core-person/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  @DisplayName("GET /migrate/core-person/active-migration")
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
            migrationType = MigrationType.CORE_PERSON,
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
            migrationType = MigrationType.CORE_PERSON,
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
        webTestClient.get().uri("/migrate/core-person/active-migration")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/core-person/active-migration")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/core-person/active-migration")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/core-person/active-migration")
        .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
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
      webTestClient.get().uri("/migrate/core-person/active-migration")
        .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
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
        .jsonPath("$.migrationType").isEqualTo("CORE_PERSON")
    }

    @Test
    fun `can also use the syscon generic role`() {
      mappingApiMock.stubGetMigrationDetails(migrationId = "2020-01-01T00%3A00%3A00", count = 123456)
      webTestClient.get().uri("/migrate/core-person/active-migration")
        .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  @DisplayName("POST /migrate/core-person/{migrationId}/cancel")
  inner class TerminateMigrationCorePerson {
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
        webTestClient.post().uri("/migrate/core-person/{migrationId}/cancel", "some id")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/core-person/{migrationId}/cancel", "some id")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/core-person/{migrationId}/cancel", "some id")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/core-person/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    internal fun `will terminate a running migration`() {
      // slow the API calls so there is time to cancel before it completes
      nomisApi.setGlobalFixedDelay(1000)
      nomisApi.stubGetPrisonerIds(totalElements = 2, pageSize = 10, firstOffenderNo = "A0001BC")
      mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A0001BC", mapping = null)
      mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A0002BC", mapping = null)
      nomisCorePersonApiMock.stubGetCorePerson(
        prisonNumber = "A0001BC",
        corePerson(prisonNumber = "A0001BC").copy(
          offenders = listOf(
            corePerson().offenders[0].copy(
              firstName = "JOHN",
              lastName = "SMITH",
            ),
          ),
        ),
      )
      nomisCorePersonApiMock.stubGetCorePerson(
        prisonNumber = "A0002BC",
        corePerson(prisonNumber = "A0002BC").copy(
          offenders = listOf(
            corePerson().offenders[0].copy(
              firstName = "ADDO",
              lastName = "ABOAGYE",
            ),
          ),
        ),
      )

      cprApiMock.stubMigrateCorePerson()
      mappingApiMock.stubCreateMappingsForMigration()
      mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 2)

      val migrationId = performMigration().migrationId

      webTestClient.post().uri("/migrate/core-person/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/core-person/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/core-person/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationId)
          .jsonPath("$.status").isEqualTo("CANCELLED")
      }
    }
  }

  private fun performMigration(body: CorePersonMigrationFilter = CorePersonMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/core-person")
    .headers(setAuthorisation(roles = listOf("MIGRATE_CORE_PERSON")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(body)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("coreperson-migration-completed"),
      any(),
      isNull(),
    )
  }

  private fun stubMigrateCorePersons(vararg nomisPersonCores: CorePerson) {
    nomisApi.resetAll()
    cprApiMock.resetAll()
    mappingApiMock.resetAll()
    nomisApi.stubGetPrisonerIds(totalElements = 2, pageSize = 10, firstOffenderNo = "A0001BC")
    nomisPersonCores.forEach {
      nomisCorePersonApiMock.stubGetCorePerson(it.prisonNumber, it)
      mappingApiMock.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = it.prisonNumber, mapping = null)
      cprApiMock.stubMigrateCorePerson(nomisPrisonNumber = it.prisonNumber, migrateCorePersonResponse(it.toMigrateCorePersonRequest()))
    }
    mappingApiMock.stubCreateMappingsForMigration()
    mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = nomisPersonCores.size)
  }
}
