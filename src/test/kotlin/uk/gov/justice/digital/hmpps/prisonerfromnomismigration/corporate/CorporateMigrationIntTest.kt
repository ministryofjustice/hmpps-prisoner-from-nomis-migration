package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.corporate

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
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonDpsApiExtension.Companion.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.corporate.CorporateDpsApiMockServer.Companion.migrateOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateInternetAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporatePhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorporateMigrationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: CorporateNomisApiMockServer

  @Autowired
  private lateinit var mappingApiMock: CorporateMappingApiMockServer

  @Autowired
  private lateinit var dpsApiMock: CorporateDpsApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("POST /migrate/corporate")
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
        webTestClient.post().uri("/migrate/corporate")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CorporateMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/corporate")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CorporateMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/corporate")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CorporateMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetCorporateOrganisationIdsToMigrate(content = listOf(CorporateOrganisationIdResponse(1000), CorporateOrganisationIdResponse(2000)))
        mappingApiMock.stubGetByNomisCorporateIdOrNull(
          nomisCorporateId = 1000,
          mapping = CorporateMappingDto(
            dpsId = "10000",
            nomisId = 1000,
            mappingType = CorporateMappingDto.MappingType.MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetByNomisCorporateIdOrNull(
          nomisCorporateId = 2000,
          mapping = CorporateMappingDto(
            dpsId = "20000",
            nomisId = 2000,
            mappingType = CorporateMappingDto.MappingType.MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will not bother retrieving any corporate details`() {
        nomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/corporates/1000")))
        nomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/corporates/2000")))
      }

      @Test
      fun `will mark migration as complete`() {
        webTestClient.get().uri("/migrate/corporate/history/${migrationResult.migrationId}")
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
        nomisApiMock.stubGetCorporateOrganisationIdsToMigrate(content = listOf(CorporateOrganisationIdResponse(1000), CorporateOrganisationIdResponse(2000)))
        mappingApiMock.stubGetByNomisCorporateIdOrNull(
          nomisCorporateId = 1000,
          mapping = null,
        )
        mappingApiMock.stubGetByNomisCorporateIdOrNull(
          nomisCorporateId = 2000,
          mapping = null,
        )
        nomisApiMock.stubGetCorporateOrganisation(1000, corporateOrganisation().copy(id = 1000, name = "Boots"))
        nomisApiMock.stubGetCorporateOrganisation(2000, corporateOrganisation().copy(id = 2000, name = "Police"))
        dpsApiMock.stubMigrateOrganisation(nomisCorporateId = 1000L, migrateOrganisationResponse().copy(organisation = IdPair(nomisId = 1000, dpsId = 10_000, elementType = IdPair.ElementType.ORGANISATION)))
        dpsApiMock.stubMigrateOrganisation(nomisCorporateId = 2000L, migrateOrganisationResponse().copy(organisation = IdPair(nomisId = 2000, dpsId = 20_000, elementType = IdPair.ElementType.ORGANISATION)))
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 2)
        migrationResult = performMigration()
      }

      @Test
      fun `will get the count of the number corporates to migrate`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/corporates/ids")))
      }

      @Test
      fun `will get details for each corporate`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/corporates/1000")))
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/corporates/2000")))
      }

      @Test
      fun `will create mapping for each corporate and children`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("corporateMapping.dpsId", "10000")
            .withRequestBodyJsonPath("corporateMapping.nomisId", "1000"),
        )
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/corporate/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("corporateMapping.dpsId", "20000")
            .withRequestBodyJsonPath("corporateMapping.nomisId", "2000"),
        )
      }

      @Test
      fun `will track telemetry for each corporate migrated`() {
        verify(telemetryClient).trackEvent(
          eq("corporate-migration-entity-migrated"),
          org.mockito.kotlin.check {
            assertThat(it["nomisId"]).isEqualTo("1000")
            assertThat(it["dpsId"]).isEqualTo("10000")
          },
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("corporate-migration-entity-migrated"),
          org.mockito.kotlin.check {
            assertThat(it["nomisId"]).isEqualTo("2000")
            assertThat(it["dpsId"]).isEqualTo("20000")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of corporates migrated`() {
        webTestClient.get().uri("/migrate/corporate/history/${migrationResult.migrationId}")
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
      private lateinit var dpsRequests: List<MigrateOrganisationRequest>
      private lateinit var mappingRequests: List<CorporateMappingsDto>
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        stubMigrateCorporates(
          CorporateOrganisation(
            id = 1000,
            name = "BOOTS",
            active = true,
            caseload = CodeDescription("LEI", "Leeds"),
            vatNumber = "G1234",
            programmeNumber = "1",
            comment = "Nice place to work",
            types = listOf(
              CorporateOrganisationType(
                type = CodeDescription("DOCTOR", "Doctor"),
                audit = NomisAudit(
                  modifyUserId = "ADJUA.MENSAH",
                  modifyDatetime = "2024-02-02T10:23",
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),
              ),
            ),
            addresses = listOf(
              CorporateAddress(
                id = 101,
                phoneNumbers = listOf(
                  CorporatePhoneNumber(
                    id = 101,
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
                isServices = true,
                contactPersonName = "MICKY MIKES",
                businessHours = "10-5pm daily",
              ),
              CorporateAddress(
                id = 102,
                phoneNumbers = emptyList(),
                validatedPAF = false,
                primaryAddress = false,
                mailAddress = false,
                isServices = false,
                audit = NomisAudit(
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-03-02T10:23",
                ),
              ),
            ),
            phoneNumbers = listOf(
              CorporatePhoneNumber(
                id = 10,
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
              CorporatePhoneNumber(
                id = 11,
                number = "0114 1111 1111111",
                type = CodeDescription("FAX", "Fax"),
                audit = NomisAudit(
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),
              ),
            ),
            internetAddresses = listOf(
              CorporateInternetAddress(
                id = 130,
                internetAddress = "test@test.justice.gov.uk",
                type = "EMAIL",
                audit = NomisAudit(
                  modifyUserId = "ADJUA.MENSAH",
                  modifyDatetime = "2024-02-02T10:23",
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),
              ),
              CorporateInternetAddress(
                id = 230,
                internetAddress = "www.place.justice.gov.uk",
                type = "WEB",
                audit = NomisAudit(
                  modifyUserId = "ADJUA.MENSAH",
                  modifyDatetime = "2024-02-02T10:23",
                  createUsername = "ADJUA.BEEK",
                  createDatetime = "2022-02-02T10:23",
                ),
              ),
            ),
            audit = NomisAudit(
              modifyUserId = "ADJUA.MENSAH",
              modifyDatetime = "2024-01-02T10:23",
              createUsername = "ADJUA.BEEK",
              createDatetime = "2022-01-02T10:23",
            ),
          ),
          CorporateOrganisation(
            id = 2000,
            name = "POLICE",
            active = true,
            types = emptyList(),
            addresses = emptyList(),
            phoneNumbers = emptyList(),
            internetAddresses = emptyList(),
            audit = NomisAudit(
              modifyUserId = "ADJUA.MENSAH",
              modifyDatetime = "2024-01-02T10:23",
              createUsername = "ADJUA.BEEK",
              createDatetime = "2022-01-02T10:23",
            ),
          ),
        )
        migrationResult = performMigration()
        dpsRequests = getRequestBodies(postRequestedFor(urlPathEqualTo("/migrate/organisation")))
        mappingRequests = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/corporate/migrate")))
      }

      @Test
      fun `will send optional core corporate data to DPS`() {
        with(dpsRequests.find { it.nomisCorporateId == 1000L } ?: throw AssertionError("Request not found")) {
          assertThat(nomisCorporateId).isEqualTo(1000L)
          assertThat(active).isTrue()
          assertThat(organisationName).isEqualTo("BOOTS")
          assertThat(programmeNumber).isEqualTo("1")
          assertThat(caseloadId).isEqualTo("LEI")
          assertThat(vatNumber).isEqualTo("G1234")
          assertThat(comments).isEqualTo("Nice place to work")
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-01-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-01-02T10:23"))
        }
      }

      @Test
      fun `will send mandatory core corporate data to DPS`() {
        with(dpsRequests.find { it.nomisCorporateId == 2000L } ?: throw AssertionError("Request not found")) {
          assertThat(nomisCorporateId).isEqualTo(2000L)
          assertThat(organisationName).isEqualTo("POLICE")
          assertThat(active).isTrue()
          assertThat(caseloadId).isNull()
          assertThat(vatNumber).isNull()
          assertThat(comments).isNull()
          assertThat(programmeNumber).isNull()
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-01-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-01-02T10:23"))
        }
      }

      @Test
      fun `will send global phone numbers to DPS`() {
        with(dpsRequests.find { it.nomisCorporateId == 1000L } ?: throw AssertionError("Request not found")) {
          assertThat(phoneNumbers).hasSize(2)
          with(phoneNumbers[0]) {
            assertThat(nomisPhoneId).isEqualTo(10)
            assertThat(number).isEqualTo("0114 555 5555")
            assertThat(type).isEqualTo("MOB")
            assertThat(createUsername).isEqualTo("ADJUA.BEEK")
            assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
            assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
            assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-02-02T10:23"))
            assertThat(extension).isEqualTo("ext 5555")
          }
          with(phoneNumbers[1]) {
            assertThat(nomisPhoneId).isEqualTo(11)
            assertThat(number).isEqualTo("0114 1111 1111111")
            assertThat(type).isEqualTo("FAX")
            assertThat(createUsername).isEqualTo("ADJUA.BEEK")
            assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
            assertThat(modifyUsername).isNull()
            assertThat(modifyDateTime).isNull()
            assertThat(extension).isNull()
          }
        }
      }

      @Test
      fun `will create mappings for nomis global phone numbers`() {
        with(mappingRequests.find { it.corporateMapping.nomisId == 1000L }?.corporatePhoneMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(2)
          assertThat(this[0].nomisId).isEqualTo(10L)
          assertThat(this[0].dpsId).isEqualTo("100")
          assertThat(this[1].nomisId).isEqualTo(11L)
          assertThat(this[1].dpsId).isEqualTo("110")
        }
        with(mappingRequests.find { it.corporateMapping.nomisId == 2000L }?.corporatePhoneMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }

      @Test
      fun `will send addresses to DPS`() {
        val corporateOrganisation = dpsRequests.find { it.nomisCorporateId == 1000L } ?: throw AssertionError("Request not found")
        assertThat(corporateOrganisation.addresses).hasSize(2)
        with(corporateOrganisation.addresses[0]) {
          assertThat(nomisAddressId).isEqualTo(101)
          assertThat(type).isEqualTo("HOME")
          assertThat(flat).isEqualTo("Flat 1B")
          assertThat(premise).isEqualTo("Pudding Court")
          assertThat(street).isEqualTo("High Mound")
          assertThat(locality).isEqualTo("Broomhill")
          assertThat(county).isEqualTo("S.YORKSHIRE")
          assertThat(country).isEqualTo("ENG")
          assertThat(noFixedAddress).isTrue()
          assertThat(mailAddress).isTrue()
          assertThat(comment).isEqualTo("Use this address")
          assertThat(startDate).isEqualTo(LocalDate.parse("1987-01-01"))
          assertThat(endDate).isEqualTo(LocalDate.parse("2024-02-01"))
          assertThat(postCode).isEqualTo("S1 5GG")
          assertThat(serviceAddress).isTrue()
          assertThat(contactPersonName).isEqualTo("MICKY MIKES")
          assertThat(businessHours).isEqualTo("10-5pm daily")
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-03-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-03-02T10:23"))
        }
        with(corporateOrganisation.addresses[1]) {
          assertThat(nomisAddressId).isEqualTo(102)
          assertThat(type).isNull()
          assertThat(flat).isNull()
          assertThat(premise).isNull()
          assertThat(street).isNull()
          assertThat(locality).isNull()
          assertThat(county).isNull()
          assertThat(country).isNull()
          assertThat(noFixedAddress).isFalse()
          assertThat(mailAddress).isFalse()
          assertThat(comment).isNull()
          assertThat(startDate).isNull()
          assertThat(endDate).isNull()
          assertThat(postCode).isNull()
          assertThat(serviceAddress).isFalse()
          assertThat(contactPersonName).isNull()
          assertThat(businessHours).isNull()
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-03-02T10:23"))
          assertThat(modifyUsername).isNull()
          assertThat(modifyDateTime).isNull()
          assertThat(phoneNumbers).isEmpty()
        }
      }

      @Test
      fun `will create mappings for nomis addresses to dps address`() {
        with(mappingRequests.find { it.corporateMapping.nomisId == 1000L }?.corporateAddressMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(2)
          assertThat(this[0].nomisId).isEqualTo(101L)
          assertThat(this[0].dpsId).isEqualTo("1010")
          assertThat(this[1].nomisId).isEqualTo(102L)
          assertThat(this[1].dpsId).isEqualTo("1020")
        }
        with(mappingRequests.find { it.corporateMapping.nomisId == 2000L }?.corporateAddressMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }

      @Test
      fun `will send address phone numbers to DPS`() {
        val person = dpsRequests.find { it.nomisCorporateId == 1000L } ?: throw AssertionError("Request not found")
        val firstAddress = person.addresses[0]
        assertThat(firstAddress.phoneNumbers).hasSize(1)
        with(firstAddress.phoneNumbers[0]) {
          assertThat(nomisPhoneId).isEqualTo(101)
          assertThat(number).isEqualTo("0113 555 5555")
          assertThat(type).isEqualTo("HOM")
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-04-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-04-02T10:23"))
          assertThat(extension).isEqualTo("ext 5555")
        }
      }

      @Test
      fun `will create mappings for nomis address phone numbers`() {
        with(mappingRequests.find { it.corporateMapping.nomisId == 1000L }?.corporateAddressPhoneMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).hasSize(1)
          assertThat(this[0].nomisId).isEqualTo(101L)
          assertThat(this[0].dpsId).isEqualTo("1010")
        }
        with(mappingRequests.find { it.corporateMapping.nomisId == 2000L }?.corporateAddressPhoneMapping ?: throw AssertionError("Request not found")) {
          assertThat(this).isEmpty()
        }
      }

      @Test
      fun `will send email addresses to DPS`() {
        val corporateOrganisation = dpsRequests.find { it.nomisCorporateId == 1000L } ?: throw AssertionError("Request not found")
        assertThat(corporateOrganisation.emailAddresses).hasSize(1)
        with(corporateOrganisation.emailAddresses[0]) {
          assertThat(nomisEmailAddressId).isEqualTo(130)
          assertThat(email).isEqualTo("test@test.justice.gov.uk")
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-02-02T10:23"))
        }
      }

      @Test
      fun `will send web addresses to DPS`() {
        val corporateOrganisation = dpsRequests.find { it.nomisCorporateId == 1000L } ?: throw AssertionError("Request not found")
        assertThat(corporateOrganisation.webAddresses).hasSize(1)
        with(corporateOrganisation.webAddresses[0]) {
          assertThat(nomisWebAddressId).isEqualTo(230)
          assertThat(webAddress).isEqualTo("www.place.justice.gov.uk")
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-02-02T10:23"))
        }
      }

      @Test
      fun `will send organisation types to DPS`() {
        val corporateOrganisation = dpsRequests.find { it.nomisCorporateId == 1000L } ?: throw AssertionError("Request not found")
        assertThat(corporateOrganisation.organisationTypes).hasSize(1)
        with(corporateOrganisation.organisationTypes[0]) {
          assertThat(type).isEqualTo("DOCTOR")
          assertThat(createUsername).isEqualTo("ADJUA.BEEK")
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2022-02-02T10:23"))
          assertThat(modifyUsername).isEqualTo("ADJUA.MENSAH")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2024-02-02T10:23"))
        }
      }
    }

    @Nested
    inner class MappingErrorRecovery {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetCorporateOrganisationIdsToMigrate(content = listOf(CorporateOrganisationIdResponse(1000)))
        mappingApiMock.stubGetByNomisCorporateIdOrNull(nomisCorporateId = 1000, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(1000, corporateOrganisation().copy(id = 1000))
        dpsApiMock.stubMigrateOrganisation(migrateOrganisationResponse().copy(organisation = IdPair(nomisId = 1000, dpsId = 10_000, elementType = IdPair.ElementType.ORGANISATION)))
        mappingApiMock.stubCreateMappingsForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will get details for corporate only once`() {
        nomisApiMock.verify(1, getRequestedFor(urlPathEqualTo("/corporates/1000")))
      }

      @Test
      fun `will attempt create mapping twice before succeeding`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("corporateMapping.dpsId", "10000")
            .withRequestBodyJsonPath("corporateMapping.nomisId", "1000"),
        )
      }

      @Test
      fun `will track telemetry for each corporate migrated`() {
        verify(telemetryClient).trackEvent(
          eq("corporate-migration-entity-migrated"),
          org.mockito.kotlin.check {
            assertThat(it["nomisId"]).isEqualTo("1000")
            assertThat(it["dpsId"]).isEqualTo("10000")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of corporates migrated`() {
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
        nomisApiMock.stubGetCorporateOrganisationIdsToMigrate(content = listOf(CorporateOrganisationIdResponse(1000)))
        mappingApiMock.stubGetByNomisCorporateIdOrNull(nomisCorporateId = 1000, mapping = null)
        nomisApiMock.stubGetCorporateOrganisation(1000, corporateOrganisation().copy(id = 1000))
        dpsApiMock.stubMigrateOrganisation(migrateOrganisationResponse().copy(organisation = IdPair(nomisId = 1000, dpsId = 10_000, elementType = IdPair.ElementType.ORGANISATION)))
        mappingApiMock.stubCreateMappingsForMigration(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = CorporateMappingDto(
                dpsId = "1000",
                nomisId = 100,
                mappingType = CorporateMappingDto.MappingType.MIGRATED,
              ),
              existing = CorporateMappingDto(
                dpsId = "999",
                nomisId = 100,
                mappingType = CorporateMappingDto.MappingType.MIGRATED,
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
      fun `will get details for corporate only once`() {
        nomisApiMock.verify(1, getRequestedFor(urlPathEqualTo("/corporates/1000")))
      }

      @Test
      fun `will attempt create mapping once before failing`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/corporate/migrate"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("corporateMapping.dpsId", "10000")
            .withRequestBodyJsonPath("corporateMapping.nomisId", "1000"),
        )
      }

      @Test
      fun `will track telemetry for each corporate migrated`() {
        verify(telemetryClient).trackEvent(
          eq("nomis-migration-corporate-duplicate"),
          org.mockito.kotlin.check {
            assertThat(it["duplicateNomisId"]).isEqualTo("100")
            assertThat(it["duplicateDpsId"]).isEqualTo("1000")
            assertThat(it["existingNomisId"]).isEqualTo("100")
            assertThat(it["existingDpsId"]).isEqualTo("999")
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of corporates migrated`() {
        webTestClient.get().uri("/migrate/corporate/history/${migrationResult.migrationId}")
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
  @DisplayName("GET /migrate/corporate/history")
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
            migrationType = MigrationType.CORPORATE,
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
            migrationType = MigrationType.CORPORATE,
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
            migrationType = MigrationType.CORPORATE,
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
            migrationType = MigrationType.CORPORATE,
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
        webTestClient.get().uri("/migrate/corporate/history")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/corporate/history")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/corporate/history")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `can read all records`() {
      webTestClient.get().uri("/migrate/corporate/history")
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
      webTestClient.get().uri("/migrate/corporate/history")
        .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  @DisplayName("GET /migrate/corporate/history/{migrationId}")
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
            migrationType = MigrationType.CORPORATE,
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
        webTestClient.get().uri("/migrate/corporate/history/2020-01-01T00:00:00")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/corporate/history/2020-01-01T00:00:00")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/corporate/history/2020-01-01T00:00:00")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `can read record`() {
      webTestClient.get().uri("/migrate/corporate/history/2020-01-01T00:00:00")
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
      webTestClient.get().uri("/migrate/corporate/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  @DisplayName("GET /migrate/corporate/active-migration")
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
            migrationType = MigrationType.CORPORATE,
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
            migrationType = MigrationType.CORPORATE,
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
        webTestClient.get().uri("/migrate/corporate/active-migration")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/corporate/active-migration")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/corporate/active-migration")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/corporate/active-migration")
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
      webTestClient.get().uri("/migrate/corporate/active-migration")
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
        .jsonPath("$.migrationType").isEqualTo("CORPORATE")
    }

    @Test
    fun `can also use the syscon generic role`() {
      mappingApiMock.stubGetMigrationDetails(migrationId = "2020-01-01T00%3A00%3A00", count = 123456)
      webTestClient.get().uri("/migrate/corporate/active-migration")
        .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
    }
  }

  @Nested
  @DisplayName("POST /migrate/corporate/{migrationId}/cancel/")
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
        webTestClient.post().uri("/migrate/corporate/{migrationId}/cancel", "some id")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/corporate/{migrationId}/cancel", "some id")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/corporate/{migrationId}/cancel", "some id")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/corporate/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }
  }

  private fun performMigration(body: CorporateMigrationFilter = CorporateMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/corporate")
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
      eq("corporate-migration-completed"),
      any(),
      isNull(),
    )
  }

  private fun stubMigrateCorporates(vararg nomisCorporateOrganisations: CorporateOrganisation) {
    dpsApiMock.resetAll()
    mappingApiMock.resetAll()
    nomisApiMock.stubGetCorporateOrganisationIdsToMigrate(content = nomisCorporateOrganisations.map { CorporateOrganisationIdResponse(it.id) })
    nomisCorporateOrganisations.forEach {
      mappingApiMock.stubGetByNomisCorporateIdOrNull(nomisCorporateId = it.id, mapping = null)
      nomisApiMock.stubGetCorporateOrganisation(it.id, it)
      dpsApiMock.stubMigrateOrganisation(nomisCorporateId = it.id, migrateOrganisationResponse(it.toDpsMigrateOrganisationRequest()))
    }
    mappingApiMock.stubCreateMappingsForMigration()
    mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = nomisCorporateOrganisations.size)
  }
}
