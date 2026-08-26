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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.NomisIdentifierId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAliasesAndIdentifiersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAliasMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconIdentifierMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse.Status
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestsAsString
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.replacePrisonNumber
import java.time.Duration
import java.time.LocalDate
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
        nomisApiMock.stubGetAllPrisonersInRange(0, 1)
        mappingApiMock.stubGetReligionsByNomisPrisonNumberOrNull(
          nomisPrisonNumber = "A0000BC",
          mapping = ReligionsMappingDto(
            cprId = "10000",
            nomisPrisonNumber = "A0000BC",
            mappingType = ReligionsMappingDto.MappingType.MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetReligionsByNomisPrisonNumberOrNull(
          nomisPrisonNumber = "A0001BC",
          mapping = ReligionsMappingDto(
            cprId = "10000",
            nomisPrisonNumber = "A0001BC",
            mappingType = ReligionsMappingDto.MappingType.MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will not bother retrieving any religion details`() {
        corePersonNomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/core-person/A0000BC/religions")))
        corePersonNomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/core-person/A0001BC/religions")))
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

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, nomisPrisonNumber)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(
          prisonNumber = nomisPrisonNumber,
          aliasesAndIdentifiers = listOf(
            CoreOffender(
              offenderId = 10000L,
              firstName = "first",
              lastName = "last",
              workingName = true,
              identifiers = listOf(
                Identifier(
                  offenderId = 10000L,
                  sequence = 1,
                  type = CodeDescription("PNC", "PNC Number"),
                  identifier = "20/0071818T",
                  verified = true,
                  issuedAuthority = "DVLA",
                  issuedDate = LocalDate.of(2001, 1, 1),
                ),
              ),
            ),
          ),
        )
        cprApiMock.stubMigrateAliasesAndIdentifiers(
          nomisPrisonNumber = nomisPrisonNumber,
          aliasMappings = listOf(
            SysconAliasMapping(
              nomisOffenderId = 10000L,
              cprAliasId = "dfc4ce90-aaeb-427b-9607-5fbd49ae4c40",
            ),
          ),
          identifierMappings = listOf(
            SysconIdentifierMapping(
              nomisIdentifierId = NomisIdentifierId(
                nomisOffenderId = 10000L,
                nomisSequence = 1,
              ),
              cprIdentifierId = "dfc4ce90-aaeb-427b-9607-5fbd49ae4c40",
            ),
          ),
        )
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve aliases and identifiers`() {
        corePersonNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/core-person/$nomisPrisonNumber")))
      }

      @Test
      fun `will transform and migrate aliases and identifiers into CPR`() {
        val migrationRequest: PrisonAliasesAndIdentifiersRequest =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/aliases-identifiers/$nomisPrisonNumber")))

        assertThat(migrationRequest.aliases).hasSize(1)
        // TODO all fields
        assertThat(migrationRequest.aliases[0].nomisOffenderId).isEqualTo(10000L)
        assertThat(migrationRequest.aliases[0].firstName).isEqualTo("first")
        assertThat(migrationRequest.aliases[0].lastName).isEqualTo("last")
        assertThat(migrationRequest.aliases[0].isPrimary).isEqualTo(true)
        assertThat(migrationRequest.identifiers[0].nomisIdentifierId.nomisOffenderId).isEqualTo(10000L)
        assertThat(migrationRequest.identifiers[0].nomisIdentifierId.nomisSequence).isEqualTo(1)
        assertThat(migrationRequest.identifiers[0].verified).isEqualTo(true)
        assertThat(migrationRequest.identifiers[0].type).isEqualTo(PrisonIdentifier.Type.PNC)
        assertThat(migrationRequest.identifiers[0].value).isEqualTo("20/0071818T")
        assertThat(migrationRequest.identifiers[0].issuedAuthority).isEqualTo("DVLA")
        assertThat(migrationRequest.identifiers[0].issuedDate).isEqualTo(LocalDate.of(2001, 1, 1))
      }

      @Test
      fun `will create mappings for alias and identifiers`() {
        val mappingRequests: List<CorePersonMappingsDto> =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person")))

        assertThat(mappingRequests).hasSize(1)

        with(mappingRequests.first()) {

          assertThat(mappingType).isEqualTo(CorePersonMappingsDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(personMapping.nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(aliases).hasSize(1)
          assertThat(aliases[0].nomisOffenderId).isEqualTo(10000L)
          assertThat(aliases[0].nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(aliases[0].cprId).isEqualTo("dfc4ce90-aaeb-427b-9607-5fbd49ae4c40")
          assertThat(aliases[0].mappingType).isEqualTo(OffenderAliasMappingDto.MappingType.MIGRATED)
          assertThat(aliases[0].label).isEqualTo(migrationResult.migrationId)
          assertThat(identifiers).hasSize(1)
          assertThat(identifiers[0].nomisOffenderId).isEqualTo(10000L)
          assertThat(identifiers[0].nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(identifiers[0].nomisIdentifierSequence).isEqualTo(1)
          assertThat(identifiers[0].cprId).isEqualTo("dfc4ce90-aaeb-427b-9607-5fbd49ae4c40")
          assertThat(identifiers[0].mappingType).isEqualTo(OffenderIdentifierMappingDto.MappingType.MIGRATED)
          assertThat(identifiers[0].label).isEqualTo(migrationResult.migrationId)
        }
      }

      @Test
      fun `will track telemetry for each prisoner migrated`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-migration-entity-migrated"),
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

    // TODO we need a test for no aliases
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathNoIdentifiers {
      private lateinit var migrationResult: MigrationResult
      private val nomisPrisonNumber = "A0000BC"

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, nomisPrisonNumber)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(
          prisonNumber = nomisPrisonNumber,
          aliasesAndIdentifiers = listOf(
            CoreOffender(
              offenderId = 10000L,
              firstName = "first",
              lastName = "last",
              workingName = true,
              identifiers = emptyList(), // No identifiers for these tests
            ),
          ),
        )
        cprApiMock.stubMigrateAliasesAndIdentifiers(
          nomisPrisonNumber = nomisPrisonNumber,
          aliasMappings = listOf(
            SysconAliasMapping(
              nomisOffenderId = 10000L,
              cprAliasId = "dfc4ce90-aaeb-427b-9607-5fbd49ae4c40",
            ),
          ),
        )
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve aliases and identifiers details`() {
        corePersonNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/core-person/$nomisPrisonNumber")))
      }

      @Test
      fun `will transform and migrate the alias CPR`() {
        val migrationRequests = CorePersonCprApiExtension.getRequestBodies<PrisonAliasesAndIdentifiersRequest>(
          postRequestedFor(
            urlPathEqualTo("/syscon-sync/aliases-identifiers/$nomisPrisonNumber"),
          ),
        )

        // a migration request has been made
        assertThat(migrationRequests).hasSize(1)
      }

      @Test
      fun `will create mappings for aliases and identifiers`() {
        val mappingRequests: List<CorePersonMappingsDto> =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person")))

        assertThat(mappingRequests).hasSize(1)

        with(mappingRequests.first()) {
          assertThat(mappingType).isEqualTo(CorePersonMappingsDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(personMapping.nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(aliases).hasSize(1)
          assertThat(identifiers).hasSize(0)
        }
      }

      @Test
      fun `will track telemetry for each prisoner migrated`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-migration-entity-migrated"),
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
      private val cprReligionId: String = "abc-123456"
      private val nomisId = 1L

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
          .forEach {
            corePersonNomisApiMock.stubGetAliasesAndIdentifiers(
              prisonNumber = nomisPrisonNumber,
              aliasesAndIdentifiers = listOf(
                CoreOffender(
                  offenderId = 10000L,
                  firstName = "first",
                  lastName = "last",
                  workingName = true,
                  identifiers = listOf(
                    Identifier(
                      offenderId = 10000L,
                      sequence = 1,
                      type = CodeDescription("PNC", "PNC Number"),
                      identifier = "20/0071818T",
                      verified = true,
                      issuedAuthority = "DVLA",
                      issuedDate = LocalDate.of(2001, 1, 1),
                    ),
                  ),
                ),
              ),
            )
            cprApiMock.stubMigrateAliasesAndIdentifiers(
              nomisPrisonNumber = nomisPrisonNumber,
              aliasMappings = listOf(
                SysconAliasMapping(
                  nomisOffenderId = 10000L,
                  cprAliasId = "dfc4ce90-aaeb-427b-9607-5fbd49ae4c40",
                ),
              ),
              identifierMappings = listOf(
                SysconIdentifierMapping(
                  nomisIdentifierId = NomisIdentifierId(
                    nomisOffenderId = 10000L,
                    nomisSequence = 1,
                  ),
                  cprIdentifierId = "dfc4ce90-aaeb-427b-9607-5fbd49ae4c40",
                ),
              ),
            )
          }

        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 81)
        // wait until all records have individually migrated since status check might finish just before some entities are still in flight due to the "big" numbers
        migrationResult = performMigration {
          verify(telemetryClient, times(80)).trackEvent(eq("coreperson-migration-entity-migrated"), any(), isNull())
        }
      }

      @Test
      fun `will migrate 80 records exactly once`() {
        val migrationRequests =
          cprApiMock.getRequestsAsString(postRequestedFor(urlPathMatching("/syscon-sync/religion/.*")))

        assertThat(migrationRequests).hasSize(80)
        assertThat(migrationRequests).containsExactlyInAnyOrderElementsOf(
          (0L..<80L).map { "/syscon-sync/religion/${nomisPrisonNumber.replacePrisonNumber(it)}" },
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class FailureWithRecoverPath {
      private lateinit var migrationResult: MigrationResult
      val nomisId = 2L
      val nomisPrisonNumber = "D0000BC"

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, nomisPrisonNumber)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(
          prisonNumber = nomisPrisonNumber,
          aliasesAndIdentifiers = listOf(
            CoreOffender(
              offenderId = 10000L,
              firstName = "first",
              lastName = "last",
              workingName = true,
              identifiers = listOf(
                Identifier(
                  offenderId = 10000L,
                  sequence = 1,
                  type = CodeDescription("PNC", "PNC Number"),
                  identifier = "20/0071818T",
                  verified = true,
                  issuedAuthority = "DVLA",
                  issuedDate = LocalDate.of(2001, 1, 1),
                ),
              ),
            ),
          ),
        )
        cprApiMock.stubMigrateAliasesAndIdentifiers(
          nomisPrisonNumber = nomisPrisonNumber,
          aliasMappings = listOf(
            SysconAliasMapping(
              nomisOffenderId = 10000L,
              cprAliasId = "dfc4ce90-aaeb-427b-9607-5fbd49ae4c40",
            ),
          ),
          identifierMappings = listOf(
            SysconIdentifierMapping(
              nomisIdentifierId = NomisIdentifierId(
                nomisOffenderId = 10000L,
                nomisSequence = 1,
              ),
              cprIdentifierId = "dfc4ce90-aaeb-427b-9607-5fbd49ae4c40",
            ),
          ),
        )
        mappingApiMock.stubCreateMappingsForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will transform and migrate religions into CPR`() {
        val migrationRequest: PrisonAliasesAndIdentifiersRequest =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/aliases-identifiers/$nomisPrisonNumber")))

        assertThat(migrationRequest.aliases).hasSize(1)
        assertThat(migrationRequest.identifiers).hasSize(1)
      }

      @Test
      fun `will eventually create mappings for religions`() {
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
            eq("coreperson-migration-entity-migrated"),
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
      val nomisPrisonNumber = "D0000BC"

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, nomisPrisonNumber)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(
          prisonNumber = nomisPrisonNumber,
          aliasesAndIdentifiers = listOf(
            CoreOffender(
              offenderId = 10000L,
              firstName = "first",
              lastName = "last",
              workingName = true,
              identifiers = listOf(
                Identifier(
                  offenderId = 10000L,
                  sequence = 1,
                  type = CodeDescription("PNC", "PNC Number"),
                  identifier = "20/0071818T",
                  verified = true,
                  issuedAuthority = "DVLA",
                  issuedDate = LocalDate.of(2001, 1, 1),
                ),
              ),
            ),
          ),
        )
        cprApiMock.stubMigrateAliasesAndIdentifiers(
          nomisPrisonNumber = nomisPrisonNumber,
          aliasMappings = listOf(
            SysconAliasMapping(
              nomisOffenderId = 10000L,
              cprAliasId = "dfc4ce90-aaeb-427b-9607-5fbd49ae4c40",
            ),
          ),
          identifierMappings = listOf(
            SysconIdentifierMapping(
              nomisIdentifierId = NomisIdentifierId(
                nomisOffenderId = 10000L,
                nomisSequence = 1,
              ),
              cprIdentifierId = "dfc4ce90-aaeb-427b-9607-5fbd49ae4c40",
            ),
          ),
        )
        mappingApiMock.stubCreateMappingsForMigration(
          DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = CorePersonMappingsDto(
                mappingType = CorePersonMappingsDto.MappingType.MIGRATED,
                personMapping = CorePersonMappingIdDto(
                  cprId = nomisPrisonNumber,
                  nomisPrisonNumber = nomisPrisonNumber
                ),
                aliases = emptyList(),
                identifiers = emptyList(),
              ),
              existing = CorePersonMappingsDto(
                mappingType = CorePersonMappingsDto.MappingType.MIGRATED,
                personMapping = CorePersonMappingIdDto(
                  cprId = nomisPrisonNumber,
                  nomisPrisonNumber = nomisPrisonNumber
                ),
                aliases = emptyList(),
                identifiers = emptyList(),
              )
            ),
            status = Status._409_CONFLICT,
            errorCode = 1409,
            userMessage = "Duplicate",
          ),
        )
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will transform and migrate prisoners into CPR`() {
        val migrationRequest: PrisonAliasesAndIdentifiersRequest =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/aliases-identifiers/$nomisPrisonNumber")))

        assertThat(migrationRequest.identifiers).hasSize(1)
        assertThat(migrationRequest.aliases).hasSize(1)
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
          eq("coreperson-migration-entity-migrated"),
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
    verify(telemetryClient).trackEvent(eq("coreperson-migration-completed"), any(), isNull())
  }
}
