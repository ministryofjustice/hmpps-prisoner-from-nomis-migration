package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactRestrictionEnteredStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerRestrictionIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiMockServer.Companion.prisonerRestrictionResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContactPersonMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: ContactPersonNomisApiMockServer

  private val dpsApiMock = ContactPersonDpsApiExtension.dpsContactPersonServer

  @Autowired
  private lateinit var mappingApiMock: PrisonerRestrictionMappingApiMockServer

  @Nested
  @DisplayName("POST /migrate/contactperson")
  inner class MigrateContactPersons {
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
          .bodyValue(PrisonerRestrictionMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/contactperson")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerRestrictionMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/contactperson")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerRestrictionMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetPrisonerRestrictionIdsToMigrate(content = listOf(PrisonerRestrictionIdResponse(1000), PrisonerRestrictionIdResponse(2000)))
        mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(
          nomisPrisonerRestrictionId = 1000,
          mapping = PrisonerRestrictionMappingDto(
            dpsId = "10000",
            nomisId = 1000,
            offenderNo = "A1234KT",
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(
          nomisPrisonerRestrictionId = 2000,
          mapping = PrisonerRestrictionMappingDto(
            dpsId = "20000",
            nomisId = 2000,
            offenderNo = "A1234KT",
            mappingType = MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will not bother retrieving any restriction details`() {
        nomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/restrictions/1000")))
        nomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/restrictions/2000")))
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
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetPrisonerRestrictionIdsToMigrate(content = listOf(PrisonerRestrictionIdResponse(1000), PrisonerRestrictionIdResponse(2000)))
        mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId = 1000, mapping = null)
        mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId = 2000, mapping = null)
        nomisApiMock.stubGetPrisonerRestrictionById(1000, prisonerRestriction().copy(id = 1000, type = CodeDescription("BAN", "Banned")))
        nomisApiMock.stubGetPrisonerRestrictionById(2000, prisonerRestriction().copy(id = 2000, type = CodeDescription("CCTV", "CCTV")))
        dpsApiMock.stubMigratePrisonerRestriction(prisonerNumber = "A1234KT", prisonerRestrictionResponse().copy(prisonerRestrictionId = 10_000, prisonerNumber = "A1234KT"))
        dpsApiMock.stubMigratePrisonerRestriction(prisonerNumber = "A1234KT", prisonerRestrictionResponse().copy(prisonerRestrictionId = 20_000, prisonerNumber = "A1234KT"))
        mappingApiMock.stubCreateMappingForMigration()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 2)
        migrationResult = performMigration()
      }

      @Test
      fun `will get the count of the number prisoner restrictions to migrate`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/restrictions/ids")))
      }

      @Test
      fun `will get details for each restriction`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/restrictions/1000")))
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/prisoners/restrictions/2000")))
      }

      @Test
      @Disabled("fails until can fix prisoner number in restriction")
      fun `will create mapping for each restriction`() {
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("personMapping.dpsId", "10000")
            .withRequestBodyJsonPath("personMapping.nomisId", "1000"),
        )
        mappingApiMock.verify(
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("personMapping.dpsId", "20000")
            .withRequestBodyJsonPath("personMapping.nomisId", "2000"),
        )
      }

      @Test
      @Disabled("fails until can fix prisoner number in restriction")
      fun `will track telemetry for each restriction migrated`() {
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
      fun `will record the number of restriction migrated`() {
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
    inner class HappyPathNomisToDPSMapping {
      private lateinit var dpsRequests: List<uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigratePrisonerRestrictionRequest>
      private lateinit var mappingRequests: List<PrisonerRestrictionMappingDto>
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        stubMigratePrisonerRestrictions(
          prisonerRestriction().copy(
            id = 1000,
            bookingId = 456,
            bookingSequence = 1,
            type = CodeDescription("BAN", "Banned"),
            effectiveDate = LocalDate.parse("2024-01-01"),
            enteredStaff = ContactRestrictionEnteredStaff(1234, "T.SMITH"),
          ),
          prisonerRestriction().copy(
            id = 2000,
            bookingId = 457,
            bookingSequence = 2,
            type = CodeDescription("CCTV", "CCTV"),
            effectiveDate = LocalDate.parse("2024-07-01"),
            expiryDate = LocalDate.parse("2024-07-31"),
            enteredStaff = ContactRestrictionEnteredStaff(1234, "T.SMITH"),
            authorisedStaff = ContactRestrictionEnteredStaff(1233, "M.SMITH"),
            comment = "Needs to be watched",
            audit = nomisAudit().copy(
              createDatetime = LocalDateTime.parse("2022-01-02T10:23"),
              createUsername = "ADJUA.BEEK",
              modifyDatetime = LocalDateTime.parse("2024-01-02T10:23"),
              modifyUserId = "ADJUA.MENSAH",
            ),
          ),
        )
        migrationResult = performMigration()
        dpsRequests = getRequestBodies(postRequestedFor(urlPathEqualTo("/migrate/prisoner-restriction/A1234KT")))
        mappingRequests = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction")))
      }

      @Test
      fun `will send restriction date to DPS`() {
        with(dpsRequests.find { it.restrictionType == "BAN" } ?: throw AssertionError("Request not found")) {
          assertThat(restrictionType).isEqualTo("BAN")
          assertThat(currentTerm).isEqualTo(true)
        }
        with(dpsRequests.find { it.restrictionType == "CCTV" } ?: throw AssertionError("Request not found")) {
          assertThat(restrictionType).isEqualTo("CCTV")
          assertThat(effectiveDate).isEqualTo(LocalDate.parse("2024-07-01"))
          assertThat(expiryDate).isEqualTo(LocalDate.parse("2024-07-31"))
          assertThat(authorisedUsername).isEqualTo("M.SMITH")
          assertThat(currentTerm).isEqualTo(false)
          assertThat(commentText).isEqualTo("Needs to be watched")
          assertThat(createdBy).isEqualTo("ADJUA.BEEK")
          assertThat(createdTime).isEqualTo(LocalDateTime.parse("2022-01-02T10:23"))
          assertThat(updatedBy).isEqualTo("T.SMITH")
          assertThat(updatedTime).isEqualTo(LocalDateTime.parse("2024-01-02T10:23"))
        }
      }

      @Test
      @Disabled("fails until can fix prisoner number in restriction")
      fun `will create mappings for nomis person to dps contact`() {
        // mock will return a dpsId which is nomisId*10

        with(mappingRequests.find { it.nomisId == 1000L } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisId).isEqualTo(1000L)
          assertThat(dpsId).isEqualTo("10000")
        }
        with(mappingRequests.find { it.nomisId == 2000L } ?: throw AssertionError("Request not found")) {
          assertThat(mappingType).isEqualTo(MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisId).isEqualTo(2000L)
          assertThat(dpsId).isEqualTo("20000")
        }
      }
    }

    @Nested
    inner class MappingErrorRecovery {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetPrisonerRestrictionIdsToMigrate(content = listOf(PrisonerRestrictionIdResponse(1000)))
        mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId = 1000, mapping = null)
        nomisApiMock.stubGetPrisonerRestrictionById(1000, prisonerRestriction().copy(id = 1000))
        dpsApiMock.stubMigratePrisonerRestriction("A1234KT", prisonerRestrictionResponse().copy(prisonerRestrictionId = 10_000))
        mappingApiMock.stubCreateMappingForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will get details for restriction only once`() {
        nomisApiMock.verify(1, getRequestedFor(urlPathEqualTo("/prisoners/restrictions/1000")))
      }

      @Test
      fun `will attempt create mapping twice before succeeding`() {
        mappingApiMock.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "10000")
            .withRequestBodyJsonPath("nomisId", "1000"),
        )
      }

      @Test
      fun `will track telemetry for each restrictions migrated`() {
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
      fun `will record the number of restrictions migrated`() {
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
    inner class DuplicateMappingErrorHandling {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetPrisonerRestrictionIdsToMigrate(content = listOf(PrisonerRestrictionIdResponse(1000)))
        mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId = 1000, mapping = null)
        nomisApiMock.stubGetPrisonerRestrictionById(1000, prisonerRestriction().copy(id = 1000))
        dpsApiMock.stubMigratePrisonerRestriction("A1234KT", prisonerRestrictionResponse().copy(prisonerRestrictionId = 10_000))
        mappingApiMock.stubCreateMappingForMigration(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PrisonerRestrictionMappingDto(
                dpsId = "1000",
                nomisId = 100,
                offenderNo = "A1234KT",
                mappingType = MIGRATED,
              ),
              existing = PrisonerRestrictionMappingDto(
                dpsId = "999",
                nomisId = 100,
                offenderNo = "A1234KT",
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
      fun `will get details for restriction only once`() {
        nomisApiMock.verify(1, getRequestedFor(urlPathEqualTo("/prisoners/restrictions/1000")))
      }

      @Test
      fun `will attempt create mapping once before failing`() {
        mappingApiMock.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBodyJsonPath("dpsId", "10000")
            .withRequestBodyJsonPath("nomisId", "1000"),
        )
      }

      @Test
      fun `will track telemetry for each restriction migrated`() {
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
      fun `will record the number of restrictions migrated`() {
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

  private fun performMigration(body: PrisonerRestrictionMigrationFilter = PrisonerRestrictionMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/contactperson")
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

  private fun stubMigratePrisonerRestrictions(vararg nomisPrisonerRestrictions: PrisonerRestriction) {
    dpsApiMock.resetAll()
    mappingApiMock.resetAll()
    nomisApiMock.stubGetPrisonerRestrictionIdsToMigrate(content = nomisPrisonerRestrictions.map { PrisonerRestrictionIdResponse(it.id) })
    nomisPrisonerRestrictions.forEach {
      mappingApiMock.stubGetByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId = it.id, mapping = null)
      nomisApiMock.stubGetPrisonerRestrictionById(it.id, it)
      dpsApiMock.stubMigratePrisonerRestriction(prisonerNumber = "A1234KT", prisonerRestrictionResponse().copy(prisonerRestrictionId = it.id * 10))
    }
    mappingApiMock.stubCreateMappingForMigration()
    mappingApiMock.stubGetMigrationDetails(migrationId = ".*", count = nomisPrisonerRestrictions.size)
  }
}
