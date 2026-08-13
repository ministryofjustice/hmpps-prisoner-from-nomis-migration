package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgencyRegistersMigrationIntTest(
  @Autowired private val nomisApiMock: AgencyNomisApiMockServer,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : AgencyRegistersIntegrationTestBase() {
  private val dpsApiMock = AgencyRegistersDpsApiExtension.agencyRegistersApi

  override fun resetTelemetryClient() {}

  internal fun setupMigrationTest() = runBlocking {
    migrationHistoryRepository.deleteAll()

    NomisApiExtension.resetAndDisableResetBeforeEach()
    AgencyRegistersDpsApiExtension.resetAndDisableResetBeforeEach()

    tearDownTelemetryClient()
  }

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  @Nested
  @DisplayName("POST /migrate/agency-registers")
  inner class StartMigration {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/agency-registers")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/agency-registers")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/agency-registers")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult
      private val nomisAgencyId = "SHEFCC"

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetAgencyIds()
        nomisApiMock.stubGetAgency(agencyId = nomisAgencyId)
        dpsApiMock.stubMigrateAgency(agencyId = nomisAgencyId)

        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve all agency ids to migrate`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/agency/ids/all")))
      }

      @Test
      fun `will retrieve agency details`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/agency/$nomisAgencyId")))
      }

      @Test
      fun `will migrate agency into DPS`() {
        dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/legacy/migrate/agency/id/$nomisAgencyId")))
      }

      @Test
      fun `will transform the agency data correctly`() {
        dpsApiMock.verify(
          postRequestedFor(urlPathEqualTo("/legacy/migrate/agency/id/$nomisAgencyId"))
            .withRequestBody(matchingJsonPath("$.agencyType", equalTo("COURT")))
            .withRequestBody(matchingJsonPath("$.name", equalTo("Sheffield Crown Court")))
            .withRequestBody(matchingJsonPath("$.active", equalTo("true")))
            .withRequestBody(matchingJsonPath("$.description", equalTo("Sheffield Crown Court")))
            .withRequestBody(matchingJsonPath("$.cjitCode", equalTo("C00SH00")))
            .withRequestBody(matchingJsonPath("$.areaCode", equalTo("52")))
            .withRequestBody(matchingJsonPath("$.regionCode", equalTo("YOHUM")))
            .withRequestBody(matchingJsonPath("$.courtTypeCode", equalTo("CC")))
            .withRequestBody(matchingJsonPath("$.addresses[0].addressLine1", equalTo("Sheffield Combined Crt Centre")))
            .withRequestBody(matchingJsonPath("$.addresses[0].addressLine2", equalTo("The Law Courts")))
            .withRequestBody(matchingJsonPath("$.addresses[0].town", equalTo("Sheffield")))
            .withRequestBody(matchingJsonPath("$.addresses[0].county", equalTo("South Yorkshire")))
            .withRequestBody(matchingJsonPath("$.addresses[0].postcode", equalTo("S3 8PH")))
            .withRequestBody(matchingJsonPath("$.addresses[0].country", equalTo("England")))
            .withRequestBody(
              matchingJsonPath(
                "$.emailAddresses[0].address",
                equalTo("sheffield.crown.court@test.com"),
              ),
            ),
        )
      }

      @Test
      fun `will track telemetry for the migration completing`() {
        verify(telemetryClient).trackEvent(
          eq("agencyregisters-migration-completed"),
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
    inner class HappyPathMultipleAgencies {
      private lateinit var migrationResult: MigrationResult
      private val nomisAgencyIds = (1..10).map { "AGY%02d".format(it) }

      @BeforeAll
      fun setUp() {
        setupMigrationTest()

        nomisApiMock.stubGetAgencyIds(
          response = AgencyIdsResponse(agencyIds = nomisAgencyIds.map { AgencyId(agencyId = it) }),
        )
        nomisAgencyIds.forEach { agencyId ->
          nomisApiMock.stubGetAgency(
            agencyId = agencyId,
            response = AgencyNomisApiMockServer.agencyResponse().copy(agencyId = agencyId),
          )
          dpsApiMock.stubMigrateAgency(agencyId = agencyId)
        }

        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve all agency ids to migrate`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/agency/ids/all")))
      }

      @Test
      fun `will retrieve details for each agency`() {
        nomisAgencyIds.forEach { agencyId ->
          nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/agency/$agencyId")))
        }
      }

      @Test
      fun `will migrate each agency into DPS`() {
        nomisAgencyIds.forEach { agencyId ->
          dpsApiMock.verify(postRequestedFor(urlPathEqualTo("/legacy/migrate/agency/id/$agencyId")))
        }
      }

      @Test
      fun `will track telemetry for the migration completing`() {
        verify(telemetryClient).trackEvent(
          eq("agencyregisters-migration-completed"),
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
  }

  private fun performMigration(
    waitUntilVerify: () -> Unit = {
      verify(telemetryClient).trackEvent(
        eq("agencyregisters-migration-completed"),
        any(),
        isNull(),
      )
    },
  ): MigrationResult = webTestClient.post().uri("/migrate/agency-registers")
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
