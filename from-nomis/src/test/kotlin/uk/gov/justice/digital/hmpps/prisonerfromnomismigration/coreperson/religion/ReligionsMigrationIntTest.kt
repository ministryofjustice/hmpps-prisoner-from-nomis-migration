package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.beliefs
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.MigrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse.Status
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestsAsString
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.replacePrisonNumber
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.collections.forEach

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReligionsMigrationIntTest(
  @Autowired private val corePersonNomisApiMock: CorePersonNomisApiMockServer,
  @Autowired private val mappingApiMock: ReligionsMappingApiMockServer,
) : MigrationTestBase() {
  private val nomisApiMock = NomisApiExtension.nomisApi
  private val cprApiMock = CorePersonCprApiExtension.cprCorePersonServer

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  @Nested
  @DisplayName("POST /migrate/core-person/religion")
  inner class StartMigration {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/core-person/religion")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/core-person/religion")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/core-person/religion")
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
      private val cprReligionId: String = "abc-123456"
      private val nomisPrisonNumber = "A0000BC"
      private val nomisId = 2L

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, nomisPrisonNumber)
        mappingApiMock.stubGetReligionsByNomisPrisonNumberOrNull(nomisPrisonNumber = nomisPrisonNumber, mapping = null)
        corePersonNomisApiMock.stubGetOffenderReligions(
          prisonNumber = nomisPrisonNumber,
          religions = beliefs(),
        )
        cprApiMock.stubMigrateCorePersonReligion(
          nomisPrisonNumber = nomisPrisonNumber,
          nomisId,
          cprReligionId,
        )
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve religion details`() {
        corePersonNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/core-person/$nomisPrisonNumber/religions")))
      }

      @Test
      fun `will transform and migrate religions into CPR`() {
        val migrationRequest: PrisonReligionRequest = CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/religion/$nomisPrisonNumber")))

        assertThat(migrationRequest.religions).hasSize(1)
        assertThat(migrationRequest.religions[0].nomisReligionId).isEqualTo(nomisId.toString())
        assertThat(migrationRequest.religions[0].religionCode).isEqualTo("DRU")
        assertThat(migrationRequest.religions[0].startDate).isEqualTo(LocalDate.parse("2016-08-02"))
        assertThat(migrationRequest.religions[0].endDate).isNull()
        assertThat(migrationRequest.religions[0].changeReasonKnown).isEqualTo(true)
        assertThat(migrationRequest.religions[0].comments).isEqualTo("No longer believes in Zoroastrianism")
        assertThat(migrationRequest.religions[0].createUserId).isEqualTo("KOFEADDY")
        assertThat(migrationRequest.religions[0].createDateTime).isEqualTo(LocalDateTime.parse("2016-08-01T10:55:00"))
        assertThat(migrationRequest.religions[0].modifyUserId).isEqualTo("KOFE_MOD")
        assertThat(migrationRequest.religions[0].modifyDateTime).isEqualTo(LocalDateTime.parse("2017-08-01T10:55:00"))
      }

      @Test
      fun `will create mappings for religions`() {
        val mappingRequests: List<ReligionsMigrationMappingDto> = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person-religion")))

        assertThat(mappingRequests).hasSize(1)

        with(mappingRequests.first()) {
          assertThat(mappingType).isEqualTo(ReligionsMigrationMappingDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(cprId).isEqualTo(nomisPrisonNumber)
          assertThat(religions).hasSize(1)
          assertThat(religions[0].cprId).isEqualTo(cprReligionId)
          assertThat(religions[0].nomisId).isEqualTo(nomisId)
        }
      }

      @Test
      fun `will track telemetry for each prisoner migrated`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-religion-migration-entity-migrated"),
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
    inner class HappyPathNoReligions {
      private lateinit var migrationResult: MigrationResult
      private val nomisPrisonNumber = "A0000BC"

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, nomisPrisonNumber)
        mappingApiMock.stubGetReligionsByNomisPrisonNumberOrNull(nomisPrisonNumber = nomisPrisonNumber, mapping = null)
        corePersonNomisApiMock.stubGetOffenderReligions(
          prisonNumber = nomisPrisonNumber,
          // no religions found in nomis
          religions = emptyList(),
        )
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve religion details`() {
        corePersonNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/core-person/$nomisPrisonNumber/religions")))
      }

      @Test
      fun `will transform and migrate religions into CPR`() {
        val migrationRequests = CorePersonCprApiExtension.getRequestBodies<PrisonReligionRequest>(postRequestedFor(urlPathEqualTo("/syscon-sync/religion/$nomisPrisonNumber")))

        // no religions migrated as none found
        assertThat(migrationRequests).hasSize(0)
      }

      @Test
      fun `will create mappings for religions`() {
        val mappingRequests: List<ReligionsMigrationMappingDto> = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person-religion")))

        assertThat(mappingRequests).hasSize(1)

        with(mappingRequests.first()) {
          assertThat(mappingType).isEqualTo(ReligionsMigrationMappingDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(cprId).isEqualTo(nomisPrisonNumber)
          assertThat(religions).hasSize(0)
        }
      }

      @Test
      fun `will track telemetry for each prisoner migrated`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-religion-migration-entity-migrated"),
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
            mappingApiMock.stubGetReligionsByNomisPrisonNumberOrNull(nomisPrisonNumber = it, null)
            corePersonNomisApiMock.stubGetOffenderReligions(prisonNumber = it)
            cprApiMock.stubMigrateCorePersonReligion(nomisPrisonNumber = it, nomisId, cprReligionId)
          }

        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 81)
        // wait until all records have individually migrated since status check might finish just before some entities are still in flight due to the "big" numbers
        migrationResult = performMigration {
          verify(telemetryClient, times(80)).trackEvent(eq("coreperson-religion-migration-entity-migrated"), any(), isNull())
        }
      }

      @Test
      fun `will migrate 80 records exactly once`() {
        val migrationRequests = cprApiMock.getRequestsAsString(postRequestedFor(urlPathMatching("/syscon-sync/religion/.*")))

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
      val cprReligionId: UUID = UUID.fromString("e7c2a3cc-e5b2-48ff-9e8b-a5038355b36c")
      val nomisPrisonNumber = "D0000BC"

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, nomisPrisonNumber)
        mappingApiMock.stubGetReligionsByNomisPrisonNumberOrNull(
          nomisPrisonNumber = nomisPrisonNumber,
          mapping = null,
        )
        corePersonNomisApiMock.stubGetOffenderReligions(
          prisonNumber = nomisPrisonNumber,
          religions = listOf(
            OffenderBelief(
              beliefId = 2,
              belief = CodeDescription("DRU", "Druid"),
              startDate = LocalDate.parse("2016-08-02"),
              audit = NomisAudit(
                createDatetime = LocalDateTime.parse("2016-08-01T10:55:00"),
                createUsername = "KOFEADDY",
                createDisplayName = "KOFE ADDY",
              ),
              changeReason = true,
              comments = "No longer believes in Zoroastrianism",
            ),
          ),
        )
        cprApiMock.stubMigrateCorePersonReligion(
          nomisPrisonNumber = nomisPrisonNumber,
          nomisId,
          cprReligionId.toString(),
        )
        mappingApiMock.stubCreateMappingsForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will transform and migrate religions into CPR`() {
        val migrationRequest: PrisonReligionRequest =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/religion/$nomisPrisonNumber")))

        assertThat(migrationRequest.religions).hasSize(1)
        assertThat(migrationRequest.religions[0].religionCode).isEqualTo("DRU")
      }

      @Test
      fun `will eventually create mappings for religions`() {
        val mappingRequests: List<ReligionsMigrationMappingDto> = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person-religion")))

        await untilAsserted {
          assertThat(mappingRequests).hasSize(2)
        }

        mappingRequests.forEach {
          assertThat(it.nomisPrisonNumber).isEqualTo(nomisPrisonNumber)
          assertThat(it.cprId).isEqualTo(nomisPrisonNumber)
        }
      }

      @Test
      fun `will eventually track telemetry for each slot migrated`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("coreperson-religion-migration-entity-migrated"),
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
      val cprReligionId: UUID = UUID.fromString("e7c2a3cc-e5b2-48ff-9e8b-a5038355b36c")
      val nomisPrisonNumber = "D0000BC"
      private val nomisId = 2L

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetPrisonerIds(1, 1, nomisPrisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(0, 1, nomisPrisonNumber)
        mappingApiMock.stubGetReligionsByNomisPrisonNumberOrNull(
          nomisPrisonNumber = nomisPrisonNumber,
          mapping = null,
        )
        corePersonNomisApiMock.stubGetOffenderReligions(
          prisonNumber = nomisPrisonNumber,
          religions = beliefs(),
        )
        cprApiMock.stubMigrateCorePersonReligion(
          nomisPrisonNumber = nomisPrisonNumber,
          nomisId,
          cprReligionId.toString(),
        )
        mappingApiMock.stubCreateMappingsForMigration(
          DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = ReligionsMigrationMappingDto(
                cprId = cprReligionId.toString(),
                nomisPrisonNumber = nomisPrisonNumber,
                mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
                religions = emptyList(),
              ),
              existing = ReligionsMigrationMappingDto(
                cprId = "9999",
                nomisPrisonNumber = nomisPrisonNumber,
                religions = emptyList(),
                mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
              ),
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
        val migrationRequest: PrisonReligionRequest =
          CorePersonCprApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/religion/$nomisPrisonNumber")))

        assertThat(migrationRequest.religions).hasSize(1)
        assertThat(migrationRequest.religions[0].religionCode).isEqualTo("DRU")
      }

      @Test
      fun `will only try create mappings once`() {
        val mappingRequests: List<ReligionsMigrationMappingDto> = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/core-person-religion")))

        await untilAsserted {
          assertThat(mappingRequests).hasSize(1)
        }
      }

      @Test
      fun `will never track telemetry for each slot migrated`() {
        verify(telemetryClient, times(0)).trackEvent(
          eq("coreperson-religion-migration-entity-migrated"),
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
    waitUntilVerify: () -> Unit = {
      verify(telemetryClient).trackEvent(
        eq("coreperson-religion-migration-completed"),
        any(),
        isNull(),
      )
    },
  ): MigrationResult = webTestClient.post().uri("/migrate/core-person/religion")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted(waitUntilVerify)
    }

  private fun waitUntilCompleted(waitUntilVerify: () -> Unit) = await atMost Duration.ofSeconds(60) untilAsserted {
    waitUntilVerify()
  }
}
