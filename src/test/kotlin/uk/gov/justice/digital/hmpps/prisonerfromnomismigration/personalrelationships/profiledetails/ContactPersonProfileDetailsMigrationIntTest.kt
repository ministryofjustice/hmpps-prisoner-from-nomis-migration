package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.Duration
import java.time.LocalDate

@WithMockAuthUser
class ContactPersonProfileDetailsMigrationIntTest(
  @Autowired private val nomisProfileDetailsApi: ContactPersonProfileDetailsNomisApiMockServer,
  @Autowired private val mappingApi: ContactPersonProfileDetailsMappingApiMockServer,
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiMockServer,
  @Autowired private val migrationService: ContactPersonProfileDetailsMigrationService,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {

  @Nested
  inner class Migrate {
    private lateinit var migrationId: String

    @BeforeEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

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
    inner class HappyPath {
      @BeforeEach
      fun setUp() = runTest {
        stubMigrationDependencies()
        migrationService.startMigration(ContactPersonProfileDetailsMigrationFilter())
          .also {
            migrationId = it.migrationId
            waitUntilCompleted()
          }
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

    private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
      verify(telemetryClient).trackEvent(
        eq("contactperson-profiledetails-migration-completed"),
        any(),
        isNull(),
      )
    }
  }
}
