package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiMockServer.Companion.migrateCorePersonResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonPhoneMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddressUsage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderDisability
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderInterestToImmigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderNationality
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderNationalityDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderSexualOrientation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class CorePersonMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisCorePersonApiMock: CorePersonNomisApiMockServer

  private val cprApiMock = CorePersonCprApiExtension.cprCorePersonServer

  @Autowired
  private lateinit var mappingApiMock: CorePersonMappingApiMockServer

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
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
      }
    }

    @Nested
    @Disabled
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
              corePerson().offenders!![0].copy(
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
              corePerson().offenders!![0].copy(
                firstName = "ADDO",
                lastName = "ABOAGYE",
              ),
            ),
          ),
        )
        cprApiMock.stubMigrateCorePerson(
          nomisPrisonNumber = "A0001BC",
          migrateCorePersonResponse(),
        )
        cprApiMock.stubMigrateCorePerson(
          nomisPrisonNumber = "A0002BC",
          migrateCorePersonResponse(),
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
            .withRequestBodyJsonPath("personMapping.cprId", "A0001BC")
            .withRequestBodyJsonPath("personMapping.nomisPrisonNumber", "A0001BC"),
        )
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/core-person/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("personMapping.cprId", "A0002BC")
            .withRequestBodyJsonPath("personMapping.nomisPrisonNumber", "A0002BC"),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo("A0001BC")
            assertThat(it["cprId"]).isEqualTo("A0001BC")
          },
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("coreperson-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo("A0002BC")
            assertThat(it["cprId"]).isEqualTo("A0002BC")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of prisoners migrated`() {
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
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
    @Disabled
    inner class HappyPathNomisToCPRMapping {
      private lateinit var cprRequests: List<Prisoner>
      private lateinit var cprRequests2: List<Prisoner>
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
                createDate = LocalDate.parse("2004-03-04"),
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
                flat = "Flat 1B",
                premise = "Pudding Court",
                street = "High Mound",
                locality = "Broomhill",
                postcode = "S1 5GG",
                city = CodeDescription("25343", "Sheffield"),
                county = CodeDescription("S.YORKSHIRE", "South Yorkshire"),
                country = CodeDescription("ENG", "England"),
                noFixedAddress = false,
                comment = "Use this address",
                startDate = LocalDate.parse("1987-01-01"),
                endDate = LocalDate.parse("2024-02-01"),
                validatedPAF = true,
                primaryAddress = true,
                mailAddress = true,
                usages = listOf(OffenderAddressUsage(addressId = 201, usage = CodeDescription("HOME", "Home"), active = true)),
                phoneNumbers = listOf(
                  OffenderPhoneNumber(
                    phoneId = 101,
                    number = "0113 555 5555",
                    type = CodeDescription("WORK", "Work"),
                    extension = "ext 5555",
                  ),
                ),
              ),
              OffenderAddress(
                addressId = 102,
                validatedPAF = false,
                primaryAddress = false,
                mailAddress = false,
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
                startDateTime = LocalDateTime.parse("2016-08-18T19:58:23"),
                latestBooking = true,
              ),
              OffenderNationality(
                bookingId = 914459,
                nationality = CodeDescription("MG", "Malagasy"),
                startDateTime = LocalDateTime.parse("2012-01-11T16:45:02"),
                endDateTime = LocalDateTime.parse("2014-09-05T10:55:00"),
                latestBooking = false,
              ),
            ),
            nationalityDetails = listOf(
              OffenderNationalityDetails(
                bookingId = 1125444,
                details = "ROTL 23/01/2023",
                startDateTime = LocalDateTime.parse("2016-08-19T19:58:23"),
                latestBooking = true,
              ),
              OffenderNationalityDetails(
                bookingId = 914459,
                details = "Claims to be from Madagascar",
                startDateTime = LocalDateTime.parse("2012-01-12T16:45:02"),
                endDateTime = LocalDateTime.parse("2014-09-05T10:55:00"),
                latestBooking = false,
              ),
            ),
            sexualOrientations = listOf(
              OffenderSexualOrientation(
                bookingId = 1125444,
                sexualOrientation = CodeDescription("HET", "Heterosexual"),
                startDateTime = LocalDateTime.parse("2016-08-19T19:58:23"),
                latestBooking = true,
              ),
              OffenderSexualOrientation(
                bookingId = 914459,
                sexualOrientation = CodeDescription("ND", "Not disclosed"),
                startDateTime = LocalDateTime.parse("2012-01-12T16:45:02"),
                endDateTime = LocalDateTime.parse("2014-09-05T10:55:00"),
                latestBooking = false,
              ),
            ),
            disabilities = listOf(
              OffenderDisability(
                bookingId = 1125444,
                disability = true,
                startDateTime = LocalDateTime.parse("2016-08-19T19:58:23"),
                latestBooking = true,
              ),
              OffenderDisability(
                bookingId = 914459,
                disability = false,
                startDateTime = LocalDateTime.parse("2012-01-12T16:45:02"),
                endDateTime = LocalDateTime.parse("2014-09-05T10:55:00"),
                latestBooking = false,
              ),
            ),
            interestsToImmigration = listOf(
              OffenderInterestToImmigration(
                bookingId = 1125444,
                startDateTime = LocalDateTime.parse("2016-08-19T19:58:23"),
                interestToImmigration = true,
                latestBooking = true,
              ),
              OffenderInterestToImmigration(
                bookingId = 914459,
                startDateTime = LocalDateTime.parse("2012-01-12T16:45:02"),
                endDateTime = LocalDateTime.parse("2014-09-05T10:55:00"),
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
                  createDatetime = LocalDateTime.parse("2016-08-01T10:55:00"),
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
                  createDatetime = LocalDateTime.parse("2016-08-01T10:55:00"),
                  createUsername = "KOFEADDY",
                  createDisplayName = "KOFE ADDY",
                  modifyUserId = "JIMADM",
                  modifyDisplayName = "Jimmy Admin",
                  modifyDatetime = LocalDateTime.parse("2016-08-02T10:55:00"),
                ),
                changeReason = true,
                comments = "New believer",
              ),
            ),
          ),

          CorePerson(
            prisonNumber = "A0002BC",
            activeFlag = false,
            offenders = listOf(
              CoreOffender(
                offenderId = 3,
                firstName = "KWAME",
                lastName = "KOBE",
                workingName = true,
                identifiers = emptyList(),
              ),
            ),
          ),
        )
        migrationResult = performMigration()
        cprRequests =
          CorePersonCprApiExtension.getRequestBodies(putRequestedFor(urlPathMatching("/syscon-sync/A0001BC")))
        cprRequests2 =
          CorePersonCprApiExtension.getRequestBodies(putRequestedFor(urlPathMatching("/syscon-sync/A0002BC")))
        mappingRequests =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person/migrate")))
      }

      @Test
      fun `will send optional core person data to CPR`() {
        with(cprRequests[0]) {
          assertThat(sentences).hasSize(1)
          assertThat(sentences[0].sentenceDate).isEqualTo(LocalDate.parse("1980-01-01"))
        }
      }

      @Test
      fun `will send mandatory core person data to CPR`() {
        with(cprRequests2[0]) {
          assertThat(aliases.size).isEqualTo(1)
          with(aliases[0]) {
            assertThat(firstName).isEqualTo("KWAME")
            assertThat(middleNames).isNull()
            assertThat(lastName).isEqualTo("KOBE")
          }
          assertThat(addresses).isEmpty()
          assertThat(sentences).isEmpty()
        }
      }

      @Test
      fun `will send offender details to CPR`() {
        with(cprRequests[0]) {
          with(name) {
            assertThat(titleCode).isEqualTo("MR")
            assertThat(firstName).isEqualTo("JOHN")
            assertThat(middleNames).isEqualTo("FRED JAMES")
            assertThat(lastName).isEqualTo("SMITH")
          }
          assertThat(aliases).hasSize(1)
          with(aliases[1]) {
            assertThat(titleCode).isNull()
            assertThat(firstName).isEqualTo("JIM")
            assertThat(middleNames).isNull()
            assertThat(lastName).isEqualTo("SMITH")
            assertThat(dateOfBirth).isNull()
            assertThat(sexCode).isNull()
          }
        }
      }

      @Test
      fun `will send offender identifier details to CPR`() {
        with(cprRequests[0]) {
          assertThat(identifiers).hasSize(2)
          with(identifiers[0]) {
            assertThat(type?.value).isEqualTo("PNC")
            assertThat(value).isEqualTo("20/0071818T")
          }
          with(identifiers[1]) {
            assertThat(type?.value).isEqualTo("CID")
            assertThat(value).isEqualTo("ABWERJKL")
          }
        }
      }
//
//      @Test
//      fun `will send phone numbers to CPR`() {
//        with(cprRequests[0]) {
//          assertThat(phoneNumbers).hasSize(2)
//          with(phoneNumbers[0]) {
//            assertThat(phoneId).isEqualTo(10)
//            assertThat(phoneNumber).isEqualTo("0114 555 5555")
//            assertThat(phoneType).isEqualTo(PhoneNumber.PhoneType.MOBILE)
//            assertThat(phoneExtension).isEqualTo("ext 5555")
//          }
//          with(phoneNumbers[1]) {
//            assertThat(phoneId).isEqualTo(11)
//            assertThat(phoneNumber).isEqualTo("0114 1111 1111111")
//            // FAX is not mapped to a phone type
//            assertThat(phoneType).isEqualTo(PhoneNumber.PhoneType.HOME)
//            assertThat(phoneExtension).isNull()
//          }
//        }
//      }

//      @Test
//      fun `will send addresses to CPR`() {
//        val corePerson = cprRequests[0]
//        assertThat(corePerson.addresses).hasSize(2)
//        with(corePerson.addresses[0]) {
//          assertThat(isPrimary).isTrue()
//          assertThat(type).isNull()
//          assertThat(flat).isEqualTo("Flat 1B")
//          assertThat(premise).isEqualTo("Pudding Court")
//          assertThat(street).isEqualTo("High Mound")
//          assertThat(locality).isEqualTo("Broomhill")
//          assertThat(townCode).isEqualTo("25343")
//          assertThat(countyCode).isEqualTo("S.YORKSHIRE")
//          assertThat(countryCode).isEqualTo("ENG")
//          assertThat(postcode).isEqualTo("S1 5GG")
//          assertThat(noFixedAddress).isFalse
//          assertThat(isMail).isTrue()
//          assertThat(comment).isEqualTo("Use this address")
//          assertThat(startDate).isEqualTo(LocalDate.parse("1987-01-01"))
//          assertThat(endDate).isEqualTo(LocalDate.parse("2024-02-01").toString())
//        }
//        with(corePerson.addresses[1]) {
//          assertThat(isPrimary).isFalse()
//          assertThat(type).isNull()
//          assertThat(flat).isNull()
//          assertThat(premise).isNull()
//          assertThat(street).isNull()
//          assertThat(locality).isNull()
//          assertThat(townCode).isNull()
//          assertThat(countyCode).isNull()
//          assertThat(countryCode).isNull()
//          assertThat(postcode).isNull()
//          assertThat(noFixedAddress).isNull()
//          assertThat(isMail).isFalse()
//          assertThat(comment).isNull()
//          assertThat(startDate).isNull()
//          assertThat(endDate).isNull()
//        }
//      }

//
//      @Test
//      fun `will send email addresses to CPR`() {
//        val corePerson = cprRequests[0]
//        assertThat(corePerson.emails).hasSize(1)
//        with(corePerson.emails[0]) {
//          assertThat(id).isEqualTo(130)
//          assertThat(emailAddress).isEqualTo("test@test.justice.gov.uk")
//        }
//      }

      @Test
      fun `will send latest demographic data to CPR`() {
        with(cprRequests[0].demographicAttributes) {
          assertThat(dateOfBirth).isEqualTo(LocalDate.parse("1980-01-01"))
          assertThat(birthPlace).isEqualTo("LONDON")
          assertThat(birthCountryCode).isEqualTo("ENG")
          assertThat(ethnicityCode).isEqualTo("BLACK")
          assertThat(sexCode).isEqualTo("MALE")
          assertThat(sexualOrientation).isEqualTo("HET")
          assertThat(disability).isTrue
          assertThat(religionCode).isEqualTo("DRU")
          assertThat(nationalityCode).isEqualTo("BRIT")
          assertThat(nationalityNote).isEqualTo("ROTL 23/01/2023")
          assertThat(interestToImmigration).isTrue
        }
      }

      @Test
      fun `will create mappings for nomis person to cpr core person`() {
        with(mappingRequests.find { it.personMapping.nomisPrisonNumber == "A0001BC" } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(CorePersonMappingsDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(personMapping.nomisPrisonNumber).isEqualTo("A0001BC")
          assertThat(personMapping.cprId).isEqualTo("A0001BC")
        }
        with(mappingRequests.find { it.personMapping.nomisPrisonNumber == "A0002BC" } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(CorePersonMappingsDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(personMapping.nomisPrisonNumber).isEqualTo("A0002BC")
          assertThat(personMapping.cprId).isEqualTo("A0002BC")
        }
      }

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
    }

    @Nested
    @Disabled
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
              corePerson().offenders!![0].copy(
                firstName = "JOHN",
                lastName = "SMITH",
              ),
            ),
          ),
        )
        cprApiMock.stubMigrateCorePerson(
          nomisPrisonNumber = "A0001BC",
          migrateCorePersonResponse(),
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
            .withRequestBodyJsonPath("personMapping.cprId", "A0001BC")
            .withRequestBodyJsonPath("personMapping.nomisPrisonNumber", "A0001BC"),
        )
      }

      @Test
      fun `will track telemetry for each person migrated`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo("A0001BC")
            assertThat(it["cprId"]).isEqualTo("A0001BC")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of core persons migrated`() {
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
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
    @Disabled
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
              corePerson().offenders!![0].copy(
                firstName = "JOHN",
                lastName = "SMITH",
              ),
            ),
          ),
        )
        cprApiMock.stubMigrateCorePerson(
          nomisPrisonNumber = "A0001BC",
          migrateCorePersonResponse(),
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
            .withRequestBodyJsonPath("personMapping.cprId", "A0001BC")
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
        webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
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

  private fun performMigration(body: CorePersonMigrationFilter = CorePersonMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/core-person")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
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
      cprApiMock.stubMigrateCorePerson(nomisPrisonNumber = it.prisonNumber, migrateCorePersonResponse(it.toCprPrisoner()))
    }
    mappingApiMock.stubCreateMappingsForMigration()
    mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = nomisPersonCores.size)
  }
}
