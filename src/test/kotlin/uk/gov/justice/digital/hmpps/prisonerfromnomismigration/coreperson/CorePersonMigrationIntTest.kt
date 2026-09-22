package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
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
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressesAndContactsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAddressUsageMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconContactMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse.Status
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderAddressUsage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestsAsString
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.replacePrisonNumber
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.collections.forEach

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorePersonMigrationIntTest(
  @Autowired private val corePersonNomisApiMock: CorePersonNomisApiMockServer,
  @Autowired private val mappingApiMock: CorePersonMappingApiMockServer,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : CorePersonIntegrationTestBase() {
  private val nomisApiMock = NomisApiExtension.nomisApi
  private val cprApiMock = CorePersonCprApiExtension.cprCorePersonServer

  override fun resetTelemetryClient() {}

  internal fun setupMigrationTest() = runBlocking {
    migrationHistoryRepository.deleteAll()

    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    CorePersonCprApiExtension.resetAndDisableResetBeforeEach()
    tearDownTelemetryClient()
  }

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  @Nested
  @DisplayName("POST /migrate/core-person")
  inner class StartMigration {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/core-person")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/core-person")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/core-person")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, "A0000BC")
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, "A0000BC")
        mappingApiMock.stubGetCorePersonByNomisPrisonNumberOrNull(
          nomisPrisonNumber = "A0000BC",
          mapping = CorePersonMappingDto(
            cprId = "A0000BC",
            nomisPrisonNumber = "A0000BC",
            mappingType = CorePersonMappingDto.MappingType.MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationCount(count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will not bother retrieving any core person records`() {
        corePersonNomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/core-person/A0000BC")))
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
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult
      private val nomisPrisonNumber = "A0000BC"
      private val testData = testData()

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, nomisPrisonNumber)
        corePersonNomisApiMock.stubGetAddressesAndContacts(
          prisonNumber = nomisPrisonNumber,
          addressesAndContacts = testData.addressesAndContacts,
        )
        cprApiMock.stubMigrateAddressesAndContacts(
          nomisPrisonNumber = nomisPrisonNumber,
          addressMappings = testData.addressesMapping,
          contactMappings = testData.contactsMapping,
        )
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(count = 1, testData.corePersonMapping)
        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve addresses and contacts`() {
        corePersonNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/core-person/$nomisPrisonNumber/addresses-contacts")))
      }

      @Test
      fun `will transform and migrate addresses and contacts into CPR`() {
        val migrationRequest: PrisonAddressesAndContactsRequest =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/addresses-contacts/$nomisPrisonNumber")))

        assertThat(migrationRequest.addresses).hasSize(1)
        with(migrationRequest.addresses!!.first()) {
          assertThat(nomisAddressId).isEqualTo(10000L)
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
          assertThat(buildingNumber).isEqualTo("The Priory")
          assertThat(thoroughfareName).isEqualTo("Main Street")
          assertThat(dependentLocality).isEqualTo("Sheffield")
          assertThat(postcode).isEqualTo("S1 1AA")
        }
        assertThat(migrationRequest.contacts).hasSize(2)
        assertThat(migrationRequest.contacts!![0].nomisContactId).isEqualTo(30000L)
        assertThat(migrationRequest.contacts[0].type).isEqualTo(PrisonContact.Type.MOBILE)
        assertThat(migrationRequest.contacts[0].value).isEqualTo("07700 900123")
        assertThat(migrationRequest.contacts[1].nomisContactId).isEqualTo(40000L)
        assertThat(migrationRequest.contacts[1].type).isEqualTo(PrisonContact.Type.EMAIL)
        assertThat(migrationRequest.contacts[1].value).isEqualTo("test@example.com")
      }

      @Test
      fun `will create mappings for address and contacts`() {
        val mappingRequests: List<CorePersonMappingsDto> =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person")))

        assertThat(mappingRequests).hasSize(1)

        with(mappingRequests.first()) {
          assertThat(mappingType).isEqualTo(CorePersonMappingsDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(personMapping.nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(addresses).hasSize(1)
          assertThat(addressUsages).hasSize(1)
          assertThat(phoneNumbers).hasSize(1)
          assertThat(emailAddresses).hasSize(1)
        }
      }

      @Test
      fun `will track telemetry for each prisoner migrated`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-address-contact-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo(nomisPrisonNumber)
            assertThat(it["cprId"]).isEqualTo(nomisPrisonNumber)
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
          .jsonPath("$.recordsMigrated").isEqualTo("1")
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathNoContacts {
      private lateinit var migrationResult: MigrationResult
      private val nomisPrisonNumber = "A0000BC"
      private val testData = testDataNoContact()

      @BeforeAll
      fun setUp() {
        setupMigrationTest()
        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, nomisPrisonNumber)
        corePersonNomisApiMock.stubGetAddressesAndContacts(
          prisonNumber = nomisPrisonNumber,
          addressesAndContacts = testData.addressesAndContacts,
        )
        cprApiMock.stubMigrateAddressesAndContacts(
          nomisPrisonNumber = nomisPrisonNumber,
          addressMappings = testData.addressesMapping,
        )
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(count = 1, testData.corePersonMapping)
        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve addresses and contacts details`() {
        corePersonNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/core-person/$nomisPrisonNumber/addresses-contacts")))
      }

      @Test
      fun `will transform and migrate the address CPR`() {
        val migrationRequests = CorePersonCprApiExtension.getRequestBodies<PrisonAddressesAndContactsRequest>(
          postRequestedFor(
            urlPathEqualTo("/syscon-sync/addresses-contacts/$nomisPrisonNumber"),
          ),
        )

        // a migration request has been made
        assertThat(migrationRequests).hasSize(1)
      }

      @Test
      fun `will create mappings for addresses and contacts`() {
        val mappingRequests: List<CorePersonMappingsDto> =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person")))

        assertThat(mappingRequests).hasSize(1)

        with(mappingRequests.first()) {
          assertThat(mappingType).isEqualTo(CorePersonMappingsDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(personMapping.nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(addresses).hasSize(1)
          assertThat(phoneNumbers).hasSize(0)
          assertThat(emailAddresses).hasSize(0)
          assertThat(addressUsages).hasSize(0)
        }
      }

      @Test
      fun `will track telemetry for each prisoner migrated`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-address-contact-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo(nomisPrisonNumber)
            assertThat(it["cprId"]).isEqualTo(nomisPrisonNumber)
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
          .jsonPath("$.recordsMigrated").isEqualTo("1")
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathLargeNumberOfPrisoners {
      private lateinit var migrationResult: MigrationResult
      private val nomisPrisonNumber = "A0001KT"

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        // estimated count
        nomisApiMock.stubGetPrisonerIds(81, 1, nomisPrisonNumber)

        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 10, totalElements = 81)
        nomisApiMock.stubGetAllPrisonersInRange(0, 10, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersInRange(10, 20, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersInRange(20, 30, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersInRange(30, 40, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersInRange(40, 50, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersInRange(50, 60, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersInRange(60, 70, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersInRange(70, 80, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersInRange(80, 81, nomisPrisonNumber)

        (0L..<81L)
          .map { nomisPrisonNumber.replacePrisonNumber(it) }
          .forEachIndexed { i, prisonNumber ->
            val testData = testData(
              offenderId = 10000L + i,
              cprAddressId = UUID.randomUUID().toString(),
              cprContactId = UUID.randomUUID().toString(),
            )
            corePersonNomisApiMock.stubGetAddressesAndContacts(
              prisonNumber = prisonNumber,
              addressesAndContacts = testData.addressesAndContacts,
            )
            cprApiMock.stubMigrateAddressesAndContacts(
              nomisPrisonNumber = prisonNumber,
              addressMappings = testData.addressesMapping,
              contactMappings = testData.contactsMapping,
            )
          }

        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(count = 81, testData().corePersonMapping)
        // wait until all records have individually migrated since status check might finish just before some entities are still in flight due to the "big" numbers
        migrationResult = performMigration {
          verify(telemetryClient, times(80)).trackEvent(eq("coreperson-address-contact-migration-entity-migrated"), any(), isNull())
        }
      }

      @Test
      fun `will migrate 80 records exactly once`() {
        val migrationRequests =
          cprApiMock.getRequestsAsString(postRequestedFor(urlPathMatching("/syscon-sync/addresses-contacts/.*")))

        assertThat(migrationRequests).hasSize(80)
        assertThat(migrationRequests).containsExactlyInAnyOrderElementsOf(
          (0L..<80L).map { "/syscon-sync/addresses-contacts/${nomisPrisonNumber.replacePrisonNumber(it)}" },
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class FailureWithRecoverPath {
      private lateinit var migrationResult: MigrationResult
      private val nomisPrisonNumber = "D0000BC"
      private val testData = testData()

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, nomisPrisonNumber)
        corePersonNomisApiMock.stubGetAddressesAndContacts(
          prisonNumber = nomisPrisonNumber,
          addressesAndContacts = testData.addressesAndContacts,
        )
        cprApiMock.stubMigrateAddressesAndContacts(
          nomisPrisonNumber = nomisPrisonNumber,
          addressMappings = testData.addressesMapping,
          contactMappings = testData.contactsMapping,
        )
        mappingApiMock.stubCreateMappingsForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationCount(count = 1, corePersonMappingDto = testData.corePersonMapping)
        migrationResult = performMigration()
      }

      @Test
      fun `will transform and migrate addresses and contacts into CPR`() {
        val migrationRequest: PrisonAddressesAndContactsRequest =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/addresses-contacts/$nomisPrisonNumber")))

        assertThat(migrationRequest.addresses).hasSize(1)
        assertThat(migrationRequest.contacts).hasSize(2)
      }

      @Test
      fun `will eventually create mappings for addresses and contacts`() {
        val mappingRequests: List<CorePersonMappingsDto> =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person")))

        await untilAsserted {
          assertThat(mappingRequests).hasSize(2)
        }

        mappingRequests.forEach {
          assertThat(it.personMapping.nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(it.personMapping.cprId).isEqualTo(nomisPrisonNumber)
        }
      }

      @Test
      fun `will eventually track telemetry for each slot migrated`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("coreperson-address-contact-migration-entity-migrated"),
            check {
              assertThat(it["nomisPrisonNumber"]).isEqualTo(nomisPrisonNumber)
              assertThat(it["cprId"]).isEqualTo(nomisPrisonNumber)
            },
            isNull(),
          )
        }
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
          .jsonPath("$.recordsMigrated").isEqualTo("1")
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class FailureWithDuplicate {
      private lateinit var migrationResult: MigrationResult
      private val nomisPrisonNumber = "D0000BC"
      private val testData = testData()

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, nomisPrisonNumber)
        corePersonNomisApiMock.stubGetAddressesAndContacts(
          prisonNumber = nomisPrisonNumber,
          addressesAndContacts = testData.addressesAndContacts,
        )
        cprApiMock.stubMigrateAddressesAndContacts(
          nomisPrisonNumber = nomisPrisonNumber,
          addressMappings = testData.addressesMapping,
          contactMappings = testData.contactsMapping,
        )
        mappingApiMock.stubCreateMappingsForMigration(
          DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = CorePersonMappingsDto(
                mappingType = CorePersonMappingsDto.MappingType.MIGRATED,
                personMapping = CorePersonMappingIdDto(
                  cprId = nomisPrisonNumber,
                  nomisPrisonNumber = nomisPrisonNumber,
                ),
                addresses = emptyList(),
                phoneNumbers = emptyList(),
                emailAddresses = emptyList(),
                addressUsages = emptyList(),
              ),
              existing = CorePersonMappingsDto(
                mappingType = CorePersonMappingsDto.MappingType.MIGRATED,
                personMapping = CorePersonMappingIdDto(
                  cprId = nomisPrisonNumber,
                  nomisPrisonNumber = nomisPrisonNumber,
                ),
                addresses = emptyList(),
                phoneNumbers = emptyList(),
                emailAddresses = emptyList(),
                addressUsages = emptyList(),
              ),
            ),
            status = Status._409_CONFLICT,
            errorCode = 1409,
            userMessage = "Duplicate",
          ),
        )
        mappingApiMock.stubGetMigrationCount(count = 0, testData.corePersonMapping)
        migrationResult = performMigration()
      }

      @Test
      fun `will transform and migrate prisoners into CPR`() {
        val migrationRequest: PrisonAddressesAndContactsRequest =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/addresses-contacts/$nomisPrisonNumber")))

        assertThat(migrationRequest.contacts).hasSize(2)
        assertThat(migrationRequest.addresses).hasSize(1)
      }

      @Test
      fun `will only try create mappings once`() {
        val mappingRequests: List<CorePersonMappingsDto> =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person")))

        await untilAsserted {
          assertThat(mappingRequests).hasSize(1)
        }
      }

      @Test
      fun `will never track telemetry for each slot migrated`() {
        verify(telemetryClient, times(0)).trackEvent(
          eq("coreperson-address-contact-migration-entity-migrated"),
          any(),
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
      }
    }
  }

  private fun performMigration(
    waitUntilVerify: () -> Unit = { },
  ): MigrationResult = webTestClient.post().uri("/migrate/core-person")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted(waitUntilVerify)
    }

  private fun waitUntilCompleted(waitUntilVerify: () -> Unit) = await atMost Duration.ofSeconds(60) untilAsserted {
    waitUntilVerify()
    verify(telemetryClient).trackEvent(eq("coreperson-address-contact-migration-completed"), any(), isNull())
  }

  private data class TestData(
    val addressesAndContacts: CorePersonAddressContact,
    val addressesMapping: List<SysconAddressMapping>,
    val contactsMapping: List<SysconContactMapping>,
    val corePersonMapping: List<CorePersonMappingDto>,
  )

  private fun testDataNoContact(
    prisonerNumber: String = "A1234BC",
    offenderId: Long = 10000L,
    cprAddressId: String = "dfc4ce90-aaeb-427b-9607-5fbd49ae4c40",
  ) = TestData(
    addressesAndContacts = CorePersonAddressContact(
      addresses = listOf(
        OffenderAddress(
          addressId = offenderId,
          primaryAddress = true,
          mailAddress = true,
          createdDateTime = LocalDateTime.parse("2000-02-02T00:00:00"),
          createdByUsername = "SYSTEM",
          lastUpdatedDateTime = null,
          lastUpdatedByUsername = null,
        ),
      ),
    ),
    addressesMapping = listOf(
      SysconAddressMapping(
        nomisAddressId = offenderId,
        cprAddressId = cprAddressId,
        addressUsageMappings = emptyList(),
        contactMappings = emptyList(),
      ),
    ),
    contactsMapping = emptyList(),
    corePersonMapping = listOf(
      CorePersonMappingDto(
        cprId = prisonerNumber,
        label = LocalDateTime.now().toString(),
        whenCreated = LocalDateTime.now().toString(),
        nomisPrisonNumber = prisonerNumber,
        mappingType = CorePersonMappingDto.MappingType.MIGRATED,
      ),
    ),
  )

  private fun testData(
    prisonerNumber: String = "A1234BC",
    offenderId: Long = 10000L,
    cprAddressId: String = "dfc4ce90-aaeb-427b-9607-5fbd49ae4c40",
    cprContactId: String = "dfc4ce90-aaeb-427b-9607-5fbd49ae4c40",
  ) = TestData(
    addressesAndContacts = CorePersonAddressContact(
      addresses = listOf(
        OffenderAddress(
          addressId = offenderId,
          primaryAddress = true,
          mailAddress = true,
          createdDateTime = LocalDateTime.parse("2000-02-02T00:00:00"),
          createdByUsername = "SYSTEM",
          lastUpdatedDateTime = null,
          lastUpdatedByUsername = null,
          flat = "Flat 2",
          premise = "The Priory",
          street = "Main Street",
          locality = "Sheffield",
          postcode = "S1 1AA",
          phoneNumbers = listOf(
            OffenderPhoneNumber(
              phoneId = 20000L,
              number = "0114 123 4567",
              type = CodeDescription("HOME", "Home"),
              createdDateTime = LocalDateTime.parse("2000-02-02T00:00:00"),
              createdByUsername = "SYSTEM",
              lastUpdatedDateTime = null,
              lastUpdatedByUsername = null,
            ),
          ),
          usages = listOf(
            OffenderAddressUsage(
              addressId = offenderId,
              usage = CodeDescription("HOME", "Home"),
              active = true,
              createdDateTime = LocalDateTime.parse("2000-02-02T00:00:00"),
              createdByUsername = "SYSTEM",
              lastUpdatedDateTime = null,
              lastUpdatedByUsername = null,
            ),
          ),
        ),
      ),
      phoneNumbers = listOf(
        OffenderPhoneNumber(
          phoneId = 30000L,
          number = "07700 900123",
          type = CodeDescription("MOBILE", "Mobile"),
          createdDateTime = LocalDateTime.parse("2000-02-02T00:00:00"),
          createdByUsername = "SYSTEM",
          lastUpdatedDateTime = null,
          lastUpdatedByUsername = null,
        ),
      ),
      emailAddresses = listOf(
        OffenderEmailAddress(
          emailAddressId = 40000L,
          email = "test@example.com",
          createdDateTime = LocalDateTime.parse("2000-02-02T00:00:00"),
          createdByUsername = "SYSTEM",
          lastUpdatedDateTime = null,
          lastUpdatedByUsername = null,
        ),
      ),
    ),
    addressesMapping = listOf(
      SysconAddressMapping(
        nomisAddressId = offenderId,
        cprAddressId = cprAddressId,
        addressUsageMappings = listOf(
          SysconAddressUsageMapping(
            nomisAddressUsageId = offenderId,
            nomisAddressUsageCode = SysconAddressUsageMapping.NomisAddressUsageCode.HOME,
            cprAddressUsageId = "d50f33bf-8c98-4c55-a4a3-b4d8d1e73732",
          ),
        ),
        contactMappings = emptyList(),
      ),
    ),
    contactsMapping = listOf(
      SysconContactMapping(
        nomisContactId = 30000L,
        nomisContactType = SysconContactMapping.NomisContactType.MOBILE,
        cprContactId = cprContactId,
      ),
      SysconContactMapping(
        nomisContactId = 40000L,
        nomisContactType = SysconContactMapping.NomisContactType.EMAIL,
        cprContactId = "f48fc155-0cfe-4c3f-94b0-869bab03645c",
      ),
    ),
    corePersonMapping = listOf(
      CorePersonMappingDto(
        cprId = prisonerNumber,
        label = LocalDateTime.now().toString(),
        whenCreated = LocalDateTime.now().toString(),
        nomisPrisonNumber = prisonerNumber,
        mappingType = CorePersonMappingDto.MappingType.MIGRATED,
      ),
    ),
  )
}
