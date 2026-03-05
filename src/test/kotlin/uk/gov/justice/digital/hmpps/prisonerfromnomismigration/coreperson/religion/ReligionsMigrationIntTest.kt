package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.beliefs
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReligionsMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
  @Autowired private val corePersonNomisApiMock: CorePersonNomisApiMockServer,
  @Autowired private val mappingApiMock: ReligionsMappingApiMockServer,
) : SqsIntegrationTestBase() {
  private val nomisApiMock = NomisApiExtension.nomisApi
  private val cprApiMock = CorePersonCprApiExtension.cprCorePersonServer

  internal fun deleteHistoryRecords() = runBlocking {
    migrationHistoryRepository.deleteAll()
  }

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
    @TestInstance(Lifecycle.PER_CLASS)
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeAll
      fun setUp() {
        nomisApiMock.stubGetPrisonerIds(1, 1, "A0001BC")
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

      @AfterAll
      fun tearDown() = deleteHistoryRecords()

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
    @TestInstance(Lifecycle.PER_CLASS)
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult
      private val cprReligionId: String = "abc-123456"
      private val prisonNumber = "A0001BC"
      private val nomisId = 2L

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetPrisonerIds(1, 1, prisonNumber)
        nomisApiMock.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
        nomisApiMock.stubGetAllPrisonersInRange(1, 1)
        mappingApiMock.stubGetReligionsByNomisPrisonNumberOrNull(nomisPrisonNumber = prisonNumber, mapping = null)
        corePersonNomisApiMock.stubGetOffenderReligions(
          prisonNumber = prisonNumber,
          religions = beliefs(),
        )
        cprApiMock.stubMigrateCorePersonReligion(nomisPrisonNumber = prisonNumber, nomisId, cprReligionId)
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @AfterEach
      fun tearDown() = deleteHistoryRecords()

      @Test
      fun `will retrieve religion details`() {
        corePersonNomisApiMock.verify(getRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/religions")))
      }

      @Test
      fun `will transform and migrate religions into CPR`() {
        val migrationRequest: PrisonReligionRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/syscon-sync/religion/$prisonNumber")))

        assertThat(migrationRequest.religions).hasSize(1)
        assertThat(migrationRequest.religions[0].nomisReligionId).isEqualTo(nomisId.toString())
        assertThat(migrationRequest.religions[0].religionCode).isEqualTo("DRU")
        assertThat(migrationRequest.religions[0].startDate).isEqualTo(LocalDate.parse("2016-08-02"))
        assertThat(migrationRequest.religions[0].endDate).isNull()
        assertThat(migrationRequest.religions[0].verified).isEqualTo(true)
        assertThat(migrationRequest.religions[0].changeReasonKnown).isEqualTo(true)
        assertThat(migrationRequest.religions[0].comments).isEqualTo("No longer believes in Zoroastrianism")
        assertThat(migrationRequest.religions[0].modifyUserId).isEqualTo("KOFEADDY")
        assertThat(migrationRequest.religions[0].modifyDateTime).isEqualTo(LocalDateTime.parse("2016-08-01T10:55:00"))
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
          eq("core-person-religion-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonNumber"]).isEqualTo(prisonNumber)
            assertThat(it["cprId"]).isEqualTo(prisonNumber)
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
  }

  private fun performMigration(): MigrationResult = webTestClient.post().uri("/migrate/core-person/religion")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("core-person-religion-migration-completed"),
      any(),
      isNull(),
    )
  }
}
