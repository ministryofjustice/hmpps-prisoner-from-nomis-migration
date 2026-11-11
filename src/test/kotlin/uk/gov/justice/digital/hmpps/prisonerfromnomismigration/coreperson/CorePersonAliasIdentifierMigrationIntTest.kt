package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAlias
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAliasesAndIdentifiersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAliasMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconIdentifierMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse.Status
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto
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
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorePersonAliasIdentifierMigrationIntTest(
  @Autowired private val corePersonNomisApiMock: CorePersonNomisApiMockServer,
  @Autowired private val mappingApiMock: CorePersonAliasIdentifierMappingApiMockServer,
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
  @DisplayName("POST /migrate/core-person?migrationType=CORE_PERSON_ALIAS_IDENTIFIER")
  inner class StartMigration {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/core-person?migrationType=CORE_PERSON_ALIAS_IDENTIFIER")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/core-person?migrationType=CORE_PERSON_ALIAS_IDENTIFIER")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/core-person?migrationType=CORE_PERSON_ALIAS_IDENTIFIER")
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
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1, firstOffenderNo = "A0000BC")
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
      fun `will not bother retrieving any aliases`() {
        corePersonNomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/core-person/A0000BC/aliases-identifiers")))
      }

      @Test
      fun `will not migrate anything to CPR`() {
        cprApiMock.verify(0, postRequestedFor(anyUrl()))
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
      private val testData = testData(prisonerNumber = nomisPrisonNumber)

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1, firstOffenderNo = nomisPrisonNumber)
        mappingApiMock.stubGetCorePersonByNomisPrisonNumberOrNull(nomisPrisonNumber = nomisPrisonNumber, mapping = null)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(
          prisonNumber = nomisPrisonNumber,
          aliasesAndIdentifiers = testData.aliases,
        )
        cprApiMock.stubMigrateAliasesAndIdentifiers(
          nomisPrisonNumber = nomisPrisonNumber,
          aliasMappings = testData.aliasMappings,
          identifierMappings = testData.identifierMappings,
        )
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(count = 1, testData.corePersonMapping)
        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve aliases and identifiers`() {
        corePersonNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/core-person/$nomisPrisonNumber/aliases-identifiers")))
      }

      @Test
      fun `will transform and migrate aliases and identifiers into CPR`() {
        val migrationRequest: PrisonAliasesAndIdentifiersRequest =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/aliases-identifiers/$nomisPrisonNumber")))

        assertThat(migrationRequest.aliases).hasSize(2)
        with(migrationRequest.aliases.first()) {
          assertThat(nomisOffenderId).isEqualTo(10000L)
          assertThat(isPrimary).isTrue()
          assertThat(titleCode).isEqualTo(PrisonAlias.TitleCode.MR)
          assertThat(firstName).isEqualTo("JOHN")
          assertThat(middleNames).isEqualTo("FRED JAMES")
          assertThat(lastName).isEqualTo("SMITH")
          assertThat(dateOfBirth).isEqualTo(LocalDate.parse("1980-01-01"))
          assertThat(sexCode).isEqualTo(PrisonAlias.SexCode.M)
          assertThat(birthPlace).isEqualTo("LONDON")
          assertThat(birthCountry).isEqualTo(PrisonAlias.BirthCountry.IMN)
          assertThat(ethnicity).isEqualTo(PrisonAlias.Ethnicity.A1)
          assertThat(suffix).isEqualTo(PrisonAlias.Suffix.Junior)
          assertThat(nameType).isEqualTo(PrisonAlias.NameType.CN)
          assertThat(createDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T10:00:00"))
          assertThat(createUserId).isEqualTo("SYSTEM")
          assertThat(modifyDateTime).isEqualTo(LocalDateTime.parse("2021-01-01T10:00:00"))
          assertThat(modifyUserId).isEqualTo("JANE_ADM")

          assertThat(identifiers).hasSize(2)
          with(identifiers[0]) {
            assertThat(nomisIdentifierId).isEqualTo(NomisIdentifierId(nomisOffenderId = 10000L, nomisSequence = 1))
            assertThat(type).isEqualTo(PrisonIdentifier.Type.PNC)
            assertThat(value).isEqualTo("20/0071818T")
            assertThat(verified).isTrue()
            assertThat(comment).isEqualTo("Met Police")
            assertThat(issuedDate).isEqualTo(LocalDate.parse("2020-01-01"))
          }
          with(identifiers[1]) {
            assertThat(nomisIdentifierId).isEqualTo(NomisIdentifierId(nomisOffenderId = 10000L, nomisSequence = 2))
            assertThat(type).isEqualTo(PrisonIdentifier.Type.NATIONAL_INSURANCE_NUMBER)
            assertThat(value).isEqualTo("AA123456A")
          }
        }
        with(migrationRequest.aliases[1]) {
          assertThat(nomisOffenderId).isEqualTo(10001L)
          assertThat(isPrimary).isFalse()
          assertThat(titleCode).isNull()
          assertThat(middleNames).isNull()
          assertThat(sexCode).isNull()
          assertThat(birthCountry).isNull()
          assertThat(ethnicity).isNull()
          assertThat(suffix).isNull()
          assertThat(nameType).isNull()
          assertThat(identifiers).hasSize(1)
          assertThat(identifiers[0].type).isEqualTo(PrisonIdentifier.Type.DRIVER_LICENSE_NUMBER)
        }
      }

      @Test
      fun `will create mappings for aliases and identifiers`() {
        val mappingRequests: List<CorePersonAliasIdentifierMappingsDto> =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person/alias-identifier-migration")))

        assertThat(mappingRequests).hasSize(1)

        with(mappingRequests.first()) {
          assertThat(mappingType).isEqualTo(CorePersonAliasIdentifierMappingsDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(personMapping.cprId).isEqualTo(nomisPrisonNumber)
          assertThat(personMapping.nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(aliases).containsExactlyInAnyOrder(
            OffenderAliasMappingDto(
              cprId = "cpr-alias-10000",
              nomisOffenderId = 10000L,
              nomisPrisonNumber = nomisPrisonNumber,
              mappingType = OffenderAliasMappingDto.MappingType.MIGRATED,
              label = migrationResult.migrationId,
            ),
            OffenderAliasMappingDto(
              cprId = "cpr-alias-10001",
              nomisOffenderId = 10001L,
              nomisPrisonNumber = nomisPrisonNumber,
              mappingType = OffenderAliasMappingDto.MappingType.MIGRATED,
              label = migrationResult.migrationId,
            ),
          )
          assertThat(identifiers).hasSize(3)
          assertThat(identifiers).contains(
            OffenderIdentifierMappingDto(
              cprId = "cpr-identifier-10000-2",
              nomisOffenderId = 10000L,
              nomisIdentifierSequence = 2,
              nomisPrisonNumber = nomisPrisonNumber,
              mappingType = OffenderIdentifierMappingDto.MappingType.MIGRATED,
              label = migrationResult.migrationId,
            ),
          )
        }
      }

      @Test
      fun `will track telemetry for each prisoner migrated`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-alias-identifier-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo(nomisPrisonNumber)
            assertThat(it["cprId"]).isEqualTo(nomisPrisonNumber)
            assertThat(it["migrationId"]).isEqualTo(migrationResult.migrationId)
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
          .jsonPath("$.migrationType").isEqualTo("CORE_PERSON_ALIAS_IDENTIFIER")
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

        nomisApiMock.stubGetPrisonerIds(41, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 10, totalElements = 41, firstOffenderNo = nomisPrisonNumber)

        (0L..<41L)
          .map { nomisPrisonNumber.replacePrisonNumber(it) }
          .forEachIndexed { i, prisonNumber ->
            val testData = testData(prisonerNumber = prisonNumber, offenderId = 10000L + i * 2)
            mappingApiMock.stubGetCorePersonByNomisPrisonNumberOrNull(nomisPrisonNumber = prisonNumber, mapping = null)
            corePersonNomisApiMock.stubGetAliasesAndIdentifiers(
              prisonNumber = prisonNumber,
              aliasesAndIdentifiers = testData.aliases,
            )
            cprApiMock.stubMigrateAliasesAndIdentifiers(
              nomisPrisonNumber = prisonNumber,
              aliasMappings = testData.aliasMappings,
              identifierMappings = testData.identifierMappings,
            )
          }

        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(count = 41, testData().corePersonMapping)
        migrationResult = performMigration {
          verify(telemetryClient, times(41)).trackEvent(eq("coreperson-alias-identifier-migration-entity-migrated"), any(), isNull())
        }
      }

      @Test
      fun `will migrate 41 records exactly once`() {
        val migrationRequests =
          cprApiMock.getRequestsAsString(postRequestedFor(urlPathMatching("/syscon-sync/aliases-identifiers/.*")))

        assertThat(migrationRequests).hasSize(41)
        assertThat(migrationRequests).containsExactlyInAnyOrderElementsOf(
          (0L..<41L).map { "/syscon-sync/aliases-identifiers/${nomisPrisonNumber.replacePrisonNumber(it)}" },
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class FailureWithRecoverPath {
      private lateinit var migrationResult: MigrationResult
      private val nomisPrisonNumber = "D0000BC"
      private val testData = testData(prisonerNumber = nomisPrisonNumber)

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1, firstOffenderNo = nomisPrisonNumber)
        mappingApiMock.stubGetCorePersonByNomisPrisonNumberOrNull(nomisPrisonNumber = nomisPrisonNumber, mapping = null)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(
          prisonNumber = nomisPrisonNumber,
          aliasesAndIdentifiers = testData.aliases,
        )
        cprApiMock.stubMigrateAliasesAndIdentifiers(
          nomisPrisonNumber = nomisPrisonNumber,
          aliasMappings = testData.aliasMappings,
          identifierMappings = testData.identifierMappings,
        )
        mappingApiMock.stubCreateMappingsForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationCount(count = 1, corePersonMappingDto = testData.corePersonMapping)
        migrationResult = performMigration()
      }

      @Test
      fun `will only migrate aliases and identifiers into CPR once`() {
        val migrationRequests: List<PrisonAliasesAndIdentifiersRequest> =
          CorePersonCprApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/syscon-sync/aliases-identifiers/$nomisPrisonNumber")))

        assertThat(migrationRequests).hasSize(1)
        assertThat(migrationRequests.first().aliases).hasSize(2)
      }

      @Test
      fun `will eventually create mappings for aliases and identifiers`() {
        await untilAsserted {
          val mappingRequests: List<CorePersonAliasIdentifierMappingsDto> =
            MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person/alias-identifier-migration")))
          assertThat(mappingRequests).hasSize(2)
          mappingRequests.forEach {
            assertThat(it.personMapping.nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
            assertThat(it.personMapping.cprId).isEqualTo(nomisPrisonNumber)
            assertThat(it.aliases).hasSize(2)
            assertThat(it.identifiers).hasSize(3)
          }
        }
      }

      @Test
      fun `will eventually track telemetry for each prisoner migrated`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("coreperson-alias-identifier-migration-entity-migrated"),
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
      private val testData = testData(prisonerNumber = nomisPrisonNumber)

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1, firstOffenderNo = nomisPrisonNumber)
        mappingApiMock.stubGetCorePersonByNomisPrisonNumberOrNull(nomisPrisonNumber = nomisPrisonNumber, mapping = null)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(
          prisonNumber = nomisPrisonNumber,
          aliasesAndIdentifiers = testData.aliases,
        )
        cprApiMock.stubMigrateAliasesAndIdentifiers(
          nomisPrisonNumber = nomisPrisonNumber,
          aliasMappings = testData.aliasMappings,
          identifierMappings = testData.identifierMappings,
        )
        mappingApiMock.stubCreateMappingsForMigration(
          DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = CorePersonAliasIdentifierMappingsDto(
                mappingType = CorePersonAliasIdentifierMappingsDto.MappingType.MIGRATED,
                personMapping = CorePersonMappingIdDto(cprId = nomisPrisonNumber, nomisPrisonNumber = nomisPrisonNumber),
                aliases = emptyList(),
                identifiers = emptyList(),
              ),
              existing = CorePersonAliasIdentifierMappingsDto(
                mappingType = CorePersonAliasIdentifierMappingsDto.MappingType.MIGRATED,
                personMapping = CorePersonMappingIdDto(cprId = "EXISTING", nomisPrisonNumber = nomisPrisonNumber),
                aliases = emptyList(),
                identifiers = emptyList(),
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
      fun `will transform and migrate prisoner into CPR`() {
        val migrationRequest: PrisonAliasesAndIdentifiersRequest =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/aliases-identifiers/$nomisPrisonNumber")))

        assertThat(migrationRequest.aliases).hasSize(2)
      }

      @Test
      fun `will only try create mappings once`() {
        val mappingRequests: List<CorePersonAliasIdentifierMappingsDto> =
          MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person/alias-identifier-migration")))

        assertThat(mappingRequests).hasSize(1)
      }

      @Test
      fun `will track telemetry for the duplicate`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-alias-identifier-migration-duplicate"),
          check {
            assertThat(it["duplicateCprId"]).isEqualTo(nomisPrisonNumber)
            assertThat(it["duplicateNomisPrisonNumber"]).isEqualTo(nomisPrisonNumber)
            assertThat(it["existingCprId"]).isEqualTo("EXISTING")
            assertThat(it["existingNomisPrisonNumber"]).isEqualTo(nomisPrisonNumber)
            assertThat(it["migrationId"]).isEqualTo(migrationResult.migrationId)
          },
          isNull(),
        )
      }

      @Test
      fun `will never track telemetry for prisoner migrated`() {
        verify(telemetryClient, times(0)).trackEvent(
          eq("coreperson-alias-identifier-migration-entity-migrated"),
          any(),
          isNull(),
        )
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
    inner class FailureWithUnmappedCode {
      private lateinit var migrationResult: MigrationResult
      private val nomisPrisonNumber = "E0000BC"

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        val testData = testData(prisonerNumber = nomisPrisonNumber)
        val aliases = testData.aliases.mapIndexed { i, alias ->
          if (i == 0) {
            alias.copy(identifiers = alias.identifiers + alias.identifiers.first().copy(sequence = 3, type = CodeDescription("STAFF", "Staff Pass/ Identity Card")))
          } else {
            alias
          }
        }

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRangesAndInRange(pageSize = 1, totalElements = 1, firstOffenderNo = nomisPrisonNumber)
        mappingApiMock.stubGetCorePersonByNomisPrisonNumberOrNull(nomisPrisonNumber = nomisPrisonNumber, mapping = null)
        corePersonNomisApiMock.stubGetAliasesAndIdentifiers(
          prisonNumber = nomisPrisonNumber,
          aliasesAndIdentifiers = aliases,
        )
        cprApiMock.stubMigrateAliasesAndIdentifiers(nomisPrisonNumber = nomisPrisonNumber)
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will not migrate anything to CPR`() {
        cprApiMock.verify(0, postRequestedFor(anyUrl()))
      }

      @Test
      fun `will not create any mappings`() {
        MappingApiExtension.mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/core-person/alias-identifier-migration")))
      }

      @Test
      fun `will record the prisoner as failed`() {
        await untilAsserted {
          webTestClient.get().uri("/migrate/history/${migrationResult.migrationId}")
            .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
            .header("Content-Type", "application/json")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
            .jsonPath("$.status").isEqualTo("COMPLETED")
            .jsonPath("$.recordsFailed").isEqualTo("1")
        }
      }
    }
  }

  private fun performMigration(
    waitUntilVerify: () -> Unit = { },
  ): MigrationResult = webTestClient.post().uri("/migrate/core-person?migrationType=CORE_PERSON_ALIAS_IDENTIFIER")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted(waitUntilVerify)
    }

  private fun waitUntilCompleted(waitUntilVerify: () -> Unit) = await atMost Duration.ofSeconds(60) untilAsserted {
    waitUntilVerify()
    verify(telemetryClient).trackEvent(eq("coreperson-alias-identifier-migration-completed"), any(), isNull())
  }

  private data class TestData(
    val aliases: List<CoreOffender>,
    val aliasMappings: List<SysconAliasMapping>,
    val identifierMappings: List<SysconIdentifierMapping>,
    val corePersonMapping: List<CorePersonMappingDto>,
  )

  private fun testData(
    prisonerNumber: String = "A1234BC",
    offenderId: Long = 10000L,
  ): TestData {
    val aliasOffenderId = offenderId + 1
    return TestData(
      aliases = listOf(
        CoreOffender(
          offenderId = offenderId,
          title = CodeDescription(code = "MR", description = "Mr"),
          firstName = "JOHN",
          middleName1 = "FRED",
          middleName2 = "JAMES",
          lastName = "SMITH",
          suffix = CodeDescription(code = "JR", description = "Junior"),
          workingName = true,
          dateOfBirth = LocalDate.parse("1980-01-01"),
          birthPlace = "LONDON",
          birthCountry = CodeDescription(code = "IOM", description = "Isle of Man"),
          ethnicity = CodeDescription(code = "A1", description = "Asian/Asian British: Indian"),
          sex = CodeDescription(code = "M", description = "Male"),
          nameType = CodeDescription(code = "CN", description = "Current Name"),
          identifiers = listOf(
            identifier(offenderId = offenderId, sequence = 1, type = CodeDescription("PNC", "PNC Number"), value = "20/0071818T"),
            identifier(offenderId = offenderId, sequence = 2, type = CodeDescription("NINO", "National Insurance Number"), value = "AA123456A"),
          ),
          createdDateTime = LocalDateTime.parse("2020-01-01T10:00:00"),
          createdByUsername = "SYSTEM",
          lastUpdatedDateTime = LocalDateTime.parse("2021-01-01T10:00:00"),
          lastUpdatedByUsername = "JANE_ADM",
        ),
        CoreOffender(
          offenderId = aliasOffenderId,
          firstName = "JOHNNY",
          lastName = "SMITHE",
          workingName = false,
          identifiers = listOf(
            identifier(offenderId = aliasOffenderId, sequence = 1, type = CodeDescription("DL", "Driving Licence"), value = "SMITH801010J99AB"),
          ),
          createdDateTime = LocalDateTime.parse("2020-01-01T10:00:00"),
          createdByUsername = "SYSTEM",
          lastUpdatedDateTime = null,
          lastUpdatedByUsername = null,
        ),
      ),
      aliasMappings = listOf(
        SysconAliasMapping(nomisOffenderId = offenderId, cprAliasId = "cpr-alias-$offenderId"),
        SysconAliasMapping(nomisOffenderId = aliasOffenderId, cprAliasId = "cpr-alias-$aliasOffenderId"),
      ),
      identifierMappings = listOf(
        SysconIdentifierMapping(nomisIdentifierId = NomisIdentifierId(offenderId, 1), cprIdentifierId = "cpr-identifier-$offenderId-1"),
        SysconIdentifierMapping(nomisIdentifierId = NomisIdentifierId(offenderId, 2), cprIdentifierId = "cpr-identifier-$offenderId-2"),
        SysconIdentifierMapping(nomisIdentifierId = NomisIdentifierId(aliasOffenderId, 1), cprIdentifierId = UUID.randomUUID().toString()),
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

  private fun identifier(offenderId: Long, sequence: Long, type: CodeDescription, value: String) = Identifier(
    offenderId = offenderId,
    sequence = sequence,
    type = type,
    identifier = value,
    issuedAuthority = "Met Police",
    issuedDate = LocalDate.parse("2020-01-01"),
    verified = true,
    createdDateTime = LocalDateTime.parse("2020-01-01T10:00:00"),
    createdByUsername = "SYSTEM",
    lastUpdatedDateTime = null,
    lastUpdatedByUsername = null,
  )
}
