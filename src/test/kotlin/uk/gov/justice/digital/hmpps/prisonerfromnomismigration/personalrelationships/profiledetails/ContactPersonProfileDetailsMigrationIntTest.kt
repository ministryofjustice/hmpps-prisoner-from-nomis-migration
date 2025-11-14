package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ProfileDetailsNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.booking
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.profileDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.profileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate

class ContactPersonProfileDetailsMigrationIntTest(
  @Autowired private val nomisProfileDetailsApi: ProfileDetailsNomisApiMockServer,
  @Autowired private val mappingApi: ContactPersonProfileDetailsMappingApiMockServer,
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiMockServer,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {

  @BeforeEach
  fun deleteHistoryRecords() = runTest {
    migrationHistoryRepository.deleteAll()
  }

  @Nested
  inner class Migrate {
    private lateinit var migrationId: String

    private fun stubMigrationDependencies(entities: Int = 2) {
      nomisApi.stubGetPrisonerIds(totalElements = entities.toLong(), pageSize = 10, firstOffenderNo = "A0001KT")
      (1L..entities)
        .map { "A000${it}KT" }
        .forEachIndexed { index, offenderNo ->
          nomisProfileDetailsApi.stubGetProfileDetails(
            offenderNo = offenderNo,
            bookingId = null,
            response = profileDetailsResponse(
              offenderNo,
              listOf(
                booking(
                  bookingId = index.toLong(),
                  latestBooking = true,
                  listOf(
                    profileDetails("MARITAL", "${10 * index + 1}"),
                    profileDetails("CHILD", "${10 * index + 2}"),
                  ),
                ),
                booking(
                  bookingId = 10 + index.toLong(),
                  latestBooking = false,
                  listOf(
                    profileDetails("MARITAL", "${10 * index + 3}"),
                    profileDetails("CHILD", "${10 * index + 4}"),
                  ),
                ),
              ),
            ),
          )
          dpsApi.stubMigrateDomesticStatus(migrateDomesticStatusResponse(offenderNo))
          dpsApi.stubMigrateNumberOfChildren(migrateNumberOfChildrenResponse(offenderNo))
          mappingApi.stubPutMapping()
        }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/contact-person-profile-details")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(ContactPersonProfileDetailsMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/contact-person-profile-details")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(ContactPersonProfileDetailsMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/contact-person-profile-details")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(ContactPersonProfileDetailsMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() = runTest {
        stubMigrationDependencies()
        migrationId = performMigration()
      }

      @Test
      fun `will request all prisoner ids`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/prisoners/ids/all")))
      }

      @Test
      fun `will migrate domestic status to DPS`() {
        dpsApi.verify(
          postRequestedFor(urlEqualTo("/migrate/domestic-status"))
            .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
            .withRequestBodyJsonPath("current.domesticStatusCode", "1")
            .withRequestBodyJsonPath("current.createdBy", "ANOTHER_USER")
            .withRequestBody(matchingJsonPath("current.createdTime", containing("${LocalDate.now()}")))
            .withRequestBodyJsonPath("history[0].domesticStatusCode", "3")
            .withRequestBodyJsonPath("history[0].createdBy", "ANOTHER_USER")
            .withRequestBody(matchingJsonPath("history[0].createdTime", containing("${LocalDate.now()}"))),
        )
        dpsApi.verify(
          postRequestedFor(urlEqualTo("/migrate/domestic-status"))
            .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A0002KT")))
            .withRequestBody(matchingJsonPath("current.domesticStatusCode", equalTo("11")))
            .withRequestBody(matchingJsonPath("history[0].domesticStatusCode", equalTo("13"))),
        )
      }

      @Test
      fun `will migrate number of children to DPS`() {
        dpsApi.verify(
          postRequestedFor(urlEqualTo("/migrate/number-of-children"))
            .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
            .withRequestBodyJsonPath("current.numberOfChildren", "2")
            .withRequestBodyJsonPath("history[0].numberOfChildren", "4"),
        )
        dpsApi.verify(
          postRequestedFor(urlEqualTo("/migrate/number-of-children"))
            .withRequestBodyJsonPath("prisonerNumber", "A0002KT")
            .withRequestBodyJsonPath("current.numberOfChildren", "12")
            .withRequestBodyJsonPath("history[0].numberOfChildren", "14"),
        )
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-profiledetails-migration-entity-migrated"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0001KT",
                "migrationId" to migrationId,
                "domesticStatusDpsIds" to "1, 2, 3",
                "numberOfChildrenDpsIds" to "4, 5, 6",
                "migrationType" to "CONTACTPERSON_PROFILEDETAILS",
              ),
            )
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("contactperson-profiledetails-migration-entity-migrated"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0002KT",
                "migrationId" to migrationId,
                "domesticStatusDpsIds" to "1, 2, 3",
                "numberOfChildrenDpsIds" to "4, 5, 6",
                "migrationType" to "CONTACTPERSON_PROFILEDETAILS",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will create mappings`() {
        mappingApi.verify(
          putRequestedFor(urlEqualTo("/mapping/contact-person/profile-details/migration"))
            .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
            .withRequestBodyJsonPath("migrationId", migrationId)
            .withRequestBodyJsonPath("domesticStatusDpsIds", "1, 2, 3")
            .withRequestBodyJsonPath("numberOfChildrenDpsIds", "4, 5, 6"),
        )
        mappingApi.verify(
          putRequestedFor(urlEqualTo("/mapping/contact-person/profile-details/migration"))
            .withRequestBodyJsonPath("prisonerNumber", "A0002KT")
            .withRequestBodyJsonPath("migrationId", migrationId)
            .withRequestBodyJsonPath("domesticStatusDpsIds", "1, 2, 3")
            .withRequestBodyJsonPath("numberOfChildrenDpsIds", "4, 5, 6"),
        )
      }
    }

    @Nested
    inner class SinglePrisoner {
      @BeforeEach
      fun setUp() = runTest {
        stubMigrationDependencies(1)
        migrationId = performMigration(prisonerNumber = "A0001KT")
      }

      @Test
      fun `will NOT request all prisoner ids`() {
        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/ids/all")))
      }

      @Test
      fun `will migrate domestic status to DPS`() {
        dpsApi.verify(
          postRequestedFor(urlEqualTo("/migrate/domestic-status"))
            .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
            .withRequestBodyJsonPath("current.domesticStatusCode", "1")
            .withRequestBodyJsonPath("history[0].domesticStatusCode", "3"),
        )
      }

      @Test
      fun `will migrate number of children to DPS`() {
        dpsApi.verify(
          postRequestedFor(urlEqualTo("/migrate/number-of-children"))
            .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
            .withRequestBodyJsonPath("current.numberOfChildren", "2")
            .withRequestBodyJsonPath("history[0].numberOfChildren", "4"),
        )
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-profiledetails-migration-entity-migrated"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0001KT",
                "migrationId" to migrationId,
                "domesticStatusDpsIds" to "1, 2, 3",
                "numberOfChildrenDpsIds" to "4, 5, 6",
                "migrationType" to "CONTACTPERSON_PROFILEDETAILS",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will create mappings`() {
        mappingApi.verify(
          putRequestedFor(urlEqualTo("/mapping/contact-person/profile-details/migration"))
            .withRequestBodyJsonPath("prisonerNumber", "A0001KT")
            .withRequestBodyJsonPath("migrationId", migrationId)
            .withRequestBodyJsonPath("domesticStatusDpsIds", "1, 2, 3")
            .withRequestBodyJsonPath("numberOfChildrenDpsIds", "4, 5, 6"),
        )
      }
    }

    @Nested
    inner class Ignored {
      val offenderNo = "A0001KT"

      @BeforeEach
      fun setUp() = runTest {
        nomisApi.stubGetPrisonerIds(totalElements = 1.toLong(), pageSize = 10, firstOffenderNo = offenderNo)
        nomisProfileDetailsApi.stubGetProfileDetails(
          offenderNo = offenderNo,
          bookingId = null,
          response = profileDetailsResponse(offenderNo, listOf()),
        )
        dpsApi.stubMigrateDomesticStatus(migrateDomesticStatusResponse(offenderNo))
        dpsApi.stubMigrateNumberOfChildren(migrateNumberOfChildrenResponse(offenderNo))
        mappingApi.stubPutMapping()
        migrationId = performMigration(prisonerNumber = offenderNo)
      }

      @Test
      fun `will request all prisoner ids`() {
        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/ids/all")))
      }

      @Test
      fun `will NOT call NOMIS or DPS APIs`() {
        nomisApi.verify(0, getRequestedFor(urlEqualTo("/prisoners/$offenderNo/profile-details")))
        dpsApi.verify(0, postRequestedFor(urlEqualTo("/migrate/domestic-status")))
        dpsApi.verify(0, postRequestedFor(urlEqualTo("/migrate/number-of-children")))
      }

      @Test
      fun `will NOT create mappings`() {
        mappingApi.verify(0, putRequestedFor(urlEqualTo("/mapping/contact-person/profile-details/migration")))
      }

      @Test
      fun `will publish ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-profiledetails-migration-entity-ignored"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0001KT",
                "migrationId" to migrationId,
                "reason" to "Prisoner has no latest booking",
              ),
            )
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingsErrorRecovery {
      val offenderNo = "A1234AA"

      @BeforeEach
      fun setUp() = runTest {
        nomisApi.stubGetPrisonerIds(totalElements = 1, pageSize = 10, firstOffenderNo = offenderNo)
        nomisProfileDetailsApi.stubGetProfileDetails(
          offenderNo = offenderNo,
          bookingId = null,
          response = profileDetailsResponse(offenderNo),
        )
        dpsApi.stubMigrateDomesticStatus(migrateDomesticStatusResponse(offenderNo))
        dpsApi.stubMigrateNumberOfChildren(migrateNumberOfChildrenResponse(offenderNo))
        // The mappings will succeed after a retry
        mappingApi.stubPutMappingFailureFollowedBySuccess()

        migrationId = performMigration()
      }

      @Test
      fun `will migrate domestic status to DPS`() {
        dpsApi.verify(
          postRequestedFor(urlEqualTo("/migrate/domestic-status"))
            .withRequestBodyJsonPath("prisonerNumber", offenderNo),
        )
      }

      @Test
      fun `will migrate number of children to DPS`() {
        dpsApi.verify(
          postRequestedFor(urlEqualTo("/migrate/number-of-children"))
            .withRequestBodyJsonPath("prisonerNumber", offenderNo),
        )
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("contactperson-profiledetails-migration-entity-migrated"),
          check {
            assertThat(it).containsAllEntriesOf(
              mapOf(
                "offenderNo" to offenderNo,
                "migrationId" to migrationId,
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will create mappings after a retry`() {
        mappingApi.verify(
          2,
          putRequestedFor(urlEqualTo("/mapping/contact-person/profile-details/migration"))
            .withRequestBodyJsonPath("prisonerNumber", offenderNo),
        )
      }
    }

    @Nested
    inner class FailureTelemetry {
      @Test
      fun `will publish failure telemetry where NOMIS get fails`() {
        stubMigrationDependencies(1)
        nomisProfileDetailsApi.stubGetProfileDetails("A0001KT", NOT_FOUND)
        migrationId = performMigration()

        verify(telemetryClient).trackEvent(
          eq("contactperson-profiledetails-migration-entity-failed"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0001KT",
                "migrationId" to migrationId,
                "reason" to "404 Not Found from GET http://localhost:8081/prisoners/A0001KT/profile-details",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will publish failure telemetry where DPS update fails`() {
        stubMigrationDependencies(1)
        dpsApi.stubMigrateDomesticStatus(BAD_REQUEST)
        migrationId = performMigration()

        verify(telemetryClient).trackEvent(
          eq("contactperson-profiledetails-migration-entity-failed"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0001KT",
                "migrationId" to migrationId,
                "reason" to "400 Bad Request from POST http://localhost:8097/migrate/domestic-status",
              ),
            )
          },
          isNull(),
        )
      }
    }
  }

  private fun performMigration(prisonerNumber: String? = null): String = webTestClient.post()
    .uri("/migrate/contact-person-profile-details")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .apply { prisonerNumber?.let { bodyValue("""{"prisonerNumber":"$prisonerNumber"}""") } ?: bodyValue("{}") }
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .migrationId
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("contactperson-profiledetails-migration-completed"),
      any(),
      isNull(),
    )
  }
}
