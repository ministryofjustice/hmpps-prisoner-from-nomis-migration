package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyNomisApiMockServer.Companion.agencyAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyNomisApiMockServer.Companion.agencyPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.Duration
import java.time.LocalDate

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
        nomisApiMock.stubGetAgency(
          agencyId = nomisAgencyId,
          response = AgencyNomisApiMockServer.agencyResponse().copy(
            agencyId = nomisAgencyId,
            type = CodeDescription(code = "CRT", description = "Court"),
            description = "Sheffield Crown Crt",
            longDescription = "Sheffield Crown Court",
            active = false,
            deactivationDate = LocalDate.parse("2020-01-01"),
            cjitCode = "C00SH00",
            updateAllowed = false,
            localAuthorities = listOf(
              CodeDescription(code = "00CF", description = "Rotherham Borough Council"),
            ),
            courtType = CodeDescription(code = "CC", description = "Crown Court"),
            disabilityAccessCode = "BA",
            area = CodeDescription(code = "52", description = "South Yorkshire"),
            subArea = CodeDescription(code = "ROTH", description = "Rotherham"),
            region = CodeDescription(code = "52", description = "South Yorkshire"),
            nomsRegion = CodeDescription(code = "YOHUM", description = "Yorkshire & Humberside"),
            payrollRegion = CodeDescription(code = "YP", description = "Young People"),
            contactName = "JANE SMITH",
            emailAddresses = listOf(
              AgencyNomisApiMockServer.agencyEmailAddress().copy(
                emailAddress = "sheffield.crown.court@test.com",
              ),
            ),
            phones = listOf(
              agencyPhoneNumber().copy(
                number = "0114 555 9898",
                type = CodeDescription(code = "BUS", description = "Business"),
              ),
              agencyPhoneNumber().copy(
                number = "0114 555 9898",
                type = CodeDescription(code = "BUS", description = "Business"),
              ),
              agencyPhoneNumber().copy(
                number = "0114 555 9999",
                type = CodeDescription(code = "FAX", description = "Fax"),
              ),
            ),
            addresses = listOf(
              agencyAddress().copy(
                type = CodeDescription(code = "BUS", description = "Business Address"),
                flat = null,
                premise = "Sheffield Combined Crt Centre",
                street = "The Law Courts",
                locality = "50 West Bar",
                postcode = "S3 8PH",
                city = CodeDescription(code = "SHEFF", description = "Sheffield"),
                county = CodeDescription(code = "S.YORKSHIRE", description = "South Yorkshire"),
                country = CodeDescription(code = "ENG", description = "England"),
                phoneNumbers = emptyList(),
              ),
              agencyAddress().copy(
                type = CodeDescription(code = "BUS", description = "Business Address"),
                flat = "Top floor",
                premise = null,
                street = "The Law Courts",
                locality = "50 West Bar",
                postcode = "S3 8PH",
                city = CodeDescription(code = "SHEFF", description = "Sheffield"),
                county = CodeDescription(code = "S.YORKSHIRE", description = "South Yorkshire"),
                country = CodeDescription(code = "ENG", description = "England"),
                phoneNumbers = listOf(
                  agencyPhoneNumber().copy(
                    number = "0114 555 8888",
                    type = CodeDescription(code = "BUS", description = "Business"),
                  ),
                ),
              ),
            ),
          ),
        )
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
        val request: LegacyAgencyDto = AgencyRegistersDpsApiExtension.getRequestBody(postRequestedFor(urlPathEqualTo("/legacy/migrate/agency/id/$nomisAgencyId")))
        with(request) {
          assertThat(agencyType).isEqualTo(LegacyAgencyType.COURT)
          assertThat(name).isEqualTo("Sheffield Crown Crt")
          assertThat(description).isEqualTo("Sheffield Crown Court")
          assertThat(active).isFalse
          assertThat(inactiveDate).isEqualTo(LocalDate.parse("2020-01-01"))
          assertThat(cjitCode).isEqualTo("C00SH00")
          assertThat(localAuthorityCode).isEqualTo("00CF")
          assertThat(courtTypeCode).isEqualTo("CC")
          assertThat(accessibleAccess).isEqualTo(LegacyAgencyDto.AccessibleAccess.BY_ARRANGEMENT_ONLY)
          assertThat(areaCode).isEqualTo("52")
          assertThat(subareaCode).isEqualTo("ROTH")
          assertThat(regionCode).isEqualTo("YOHUM")
          assertThat(geographicalAreaCode).isEqualTo("52")
          assertThat(payrollRegionCode).isEqualTo("YP")
          assertThat(contact).isEqualTo("JANE SMITH")
          assertThat(emailAddresses).hasSize(1)
          assertThat(emailAddresses[0].address).isEqualTo("sheffield.crown.court@test.com")
          assertThat(phoneNumbers).hasSize(3)
          assertThat(phoneNumbers.map { it.number }).containsExactlyInAnyOrder("0114 555 9898", "0114 555 9999", "0114 555 8888")
          assertThat(addresses).hasSize(2)
          with(addresses[0]) {
            assertThat(addressLine1).isEqualTo("Sheffield Combined Crt Centre, The Law Courts")
            assertThat(addressLine2).isEqualTo("50 West Bar")
            assertThat(town).isEqualTo("Sheffield")
            assertThat(county).isEqualTo("South Yorkshire")
            assertThat(postcode).isEqualTo("S3 8PH")
            assertThat(country).isEqualTo("England")
          }
          with(addresses[1]) {
            assertThat(addressLine1).isEqualTo("Top floor, The Law Courts")
            assertThat(addressLine2).isEqualTo("50 West Bar")
            assertThat(town).isEqualTo("Sheffield")
            assertThat(county).isEqualTo("South Yorkshire")
            assertThat(postcode).isEqualTo("S3 8PH")
            assertThat(country).isEqualTo("England")
          }
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
