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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
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
      private lateinit var requests: List<MigrateContactRequest>

      @BeforeAll
      fun setUp() {
        stubMigratePersons(
          contactPerson().copy(
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
            audit = contactPerson().audit.copy(
              modifyUserId = "ADJUA.MENSAH",
              modifyDatetime = "2024-01-02T10:23",
              createUsername = "ADJUA.BEEK",
              createDatetime = "2022-01-02T10:23",
            ),
          ),
          ContactPerson(
            personId = 2000,
            firstName = "KWAME",
            lastName = "KOBE",
            interpreterRequired = false,
            keepBiometrics = false,
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
        performMigration()
        requests = getRequestBodies(postRequestedFor(urlPathEqualTo("/migrate/contact")))
      }

      @Test
      fun `will send optional core person data to DPS`() {
        val request = requests.find { it.personId == 1000L } ?: throw AssertionError("Request not found")
        assertThat(request.personId).isEqualTo(1000L)
        assertThat(request.firstName).isEqualTo("JOHN")
        assertThat(request.lastName).isEqualTo("SMITH")
        assertThat(request.dateOfBirth).isEqualTo(LocalDate.parse("1965-07-19"))
        assertThat(request.gender?.code).isEqualTo("M")
        assertThat(request.title?.code).isEqualTo("MR")
        assertThat(request.language?.code).isEqualTo("VIE")
        assertThat(request.interpreterRequired).isTrue()
        assertThat(request.domesticStatus?.code).isEqualTo("M")
        assertThat(request.deceasedDate).isEqualTo(LocalDate.parse("2020-01-23"))
        assertThat(request.staff).isTrue()
        assertThat(request.createUsername).isEqualTo("ADJUA.BEEK")
        assertThat(request.createDateTime).isEqualTo(LocalDateTime.parse("2022-01-02T10:23"))
        assertThat(request.modifyUsername).isEqualTo("ADJUA.MENSAH")
        assertThat(request.modifyDateTime).isEqualTo(LocalDateTime.parse("2024-01-02T10:23"))
      }

      @Test
      fun `will send mandatory core person data to DPS`() {
        val request = requests.find { it.personId == 2000L } ?: throw AssertionError("Request not found")
        assertThat(request.personId).isEqualTo(2000L)
        assertThat(request.firstName).isEqualTo("KWAME")
        assertThat(request.lastName).isEqualTo("KOBE")
        assertThat(request.dateOfBirth).isNull()
        assertThat(request.gender).isNull()
        assertThat(request.title).isNull()
        assertThat(request.language).isNull()
        assertThat(request.interpreterRequired).isFalse()
        assertThat(request.domesticStatus).isNull()
        assertThat(request.deceasedDate).isNull()
        assertThat(request.staff).isFalse()
        assertThat(request.createUsername).isEqualTo("ADJUA.BEEK")
        assertThat(request.createDateTime).isEqualTo(LocalDateTime.parse("2022-01-02T10:23"))
        assertThat(request.modifyUsername).isNull()
        assertThat(request.modifyDateTime).isNull()
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

  private fun performMigration(body: ContactPersonMigrationFilter = ContactPersonMigrationFilter()): MigrationResult =
    webTestClient.post().uri("/migrate/contactperson")
      .headers(setAuthorisation(roles = listOf("MIGRATE_CONTACTPERSON")))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(body)
      .exchange()
      .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
      .also {
        waitUntilCompleted()
      }

  private fun waitUntilCompleted() =
    await atMost Duration.ofSeconds(60) untilAsserted {
      verify(telemetryClient).trackEvent(
        eq("contactperson-migration-completed"),
        any(),
        isNull(),
      )
    }

  private fun stubMigratePersons(vararg nomisPersonContacts: ContactPerson) {
    nomisApiMock.stubGetPersonIdsToMigrate(content = nomisPersonContacts.map { PersonIdResponse(it.personId) })
    nomisPersonContacts.forEach {
      mappingApiMock.stubGetByNomisPersonIdOrNull(nomisPersonId = it.personId, mapping = null)
      nomisApiMock.stubGetPerson(it.personId, it)
      dpsApiMock.stubMigrateContact(nomisPersonId = it.personId, migrateContactResponse().copy(contact = IdPair(nomisId = it.personId, dpsId = it.personId * 10, elementType = IdPair.ElementType.CONTACT)))
    }
    mappingApiMock.stubCreateMappingsForMigration()
    mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = nomisPersonContacts.size)
  }
}
