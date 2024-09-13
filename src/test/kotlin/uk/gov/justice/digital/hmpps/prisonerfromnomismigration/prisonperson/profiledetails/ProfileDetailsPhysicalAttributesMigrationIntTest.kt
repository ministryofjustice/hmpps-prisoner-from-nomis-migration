package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest.MigrationType.PROFILE_DETAILS_PHYSICAL_ATTRIBUTES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonMigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.ProfileDetailsPhysicalAttributesMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.test.kotlin.auth.WithMockAuthUser
import java.time.Duration
import java.time.LocalDate

// TODO SDIT-2019 can remove this when calling the API to start a migration
@WithMockAuthUser
class ProfileDetailsPhysicalAttributesMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var profileDetailsNomisApi: ProfileDetailsNomisApiMockServer

  @Autowired
  private lateinit var prisonPersonMappingApi: PrisonPersonMappingApiMockServer

  @Autowired
  private lateinit var dpsApi: ProfileDetailsPhysicalAttributesDpsApiMockServer

  @Autowired
  private lateinit var migrationService: PrisonPersonMigrationService

  @Nested
  @DisplayName("/migrate/prisonperson/profile-detail-physical-attributes")
  inner class MigrateProfileDetailPhysicalAttributes {
    private lateinit var migrationId: String
    private fun stubMigrationDependencies(entities: Int = 2) {
      nomisApi.stubGetPrisonIds(totalElements = entities.toLong(), pageSize = 10, offenderNo = "A0001KT")
      (1L..entities)
        .map { "A000${it}KT" }
        .forEachIndexed { index, offenderNo ->
          profileDetailsNomisApi.stubGetProfileDetails(offenderNo)
          dpsApi.stubMigrateProfileDetailsPhysicalAttributes(offenderNo, ProfileDetailsPhysicalAttributesMigrationResponse(listOf(index + 1.toLong())))
          prisonPersonMappingApi.stubPostMapping()
        }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() = runTest {
        stubMigrationDependencies(entities = 2)
        // TODO SDIT-2019 replace with a call to the start migration endpoint when it's done
        migrationId = migrationService.startMigration(PrisonPersonMigrationFilter(migrationType = PROFILE_DETAILS_PHYSICAL_ATTRIBUTES)).migrationId
          .also { waitUntilCompleted() }
      }

      @Test
      fun `will migrate profile detail physical attributes`() {
        dpsApi.verify(
          putRequestedFor(urlMatching("/migration/prisoners/A0001KT/profile-details-physical-attributes"))
            .withRequestBodyJsonPath("$[0].appliesFrom", "2024-02-03T12:34:56Z[Europe/London]")
            .withRequestBodyJsonPath("$[0].appliesTo", "2024-10-21T12:34:56+01:00[Europe/London]")
            .withRequestBodyJsonPath("$[0].build.value", "SLIM")
            .withRequestBodyJsonPath("$[0].build.lastModifiedAt", containing(LocalDate.now().toString()))
            .withRequestBodyJsonPath("$[0].build.lastModifiedBy", "ANOTHER_USER")
            .withRequestBodyJsonPath("$[0].shoeSize.value", "8.5")
            .withRequestBodyJsonPath("$[0].shoeSize.lastModifiedAt", containing(LocalDate.now().toString()))
            .withRequestBodyJsonPath("$[0].shoeSize.lastModifiedBy", "ANOTHER_USER")
            .withRequestBodyJsonPath("$[0].facialHair", absent()),
        )
        dpsApi.verify(
          putRequestedFor(urlMatching("/migration/prisoners/A0002KT/profile-details-physical-attributes")),
        )
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-entity-migrated"),
          org.mockito.kotlin.check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0001KT",
                "migrationId" to migrationId,
                "dpsIds" to "[1]",
                "migrationType" to "PRISON_PERSON",
                "prisonPersonMigrationType" to "PROFILE_DETAILS_PHYSICAL_ATTRIBUTES",
              ),
            )
          },
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-entity-migrated"),
          org.mockito.kotlin.check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0002KT",
                "migrationId" to migrationId,
                "dpsIds" to "[2]",
                "migrationType" to "PRISON_PERSON",
                "prisonPersonMigrationType" to "PROFILE_DETAILS_PHYSICAL_ATTRIBUTES",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will create mappings`() {
        mappingApi.verify(
          postRequestedFor(urlEqualTo("/mapping/prisonperson/migration"))
            .withRequestBodyJsonPath("nomisPrisonerNumber", "A0001KT")
            .withRequestBodyJsonPath("migrationType", "PROFILE_DETAILS_PHYSICAL_ATTRIBUTES")
            .withRequestBodyJsonPath("label", migrationId)
            .withRequestBody(matchingJsonPath("dpsIds[?(@ == 1)]")),
        )
        mappingApi.verify(
          postRequestedFor(urlEqualTo("/mapping/prisonperson/migration"))
            .withRequestBodyJsonPath("nomisPrisonerNumber", "A0002KT")
            .withRequestBodyJsonPath("migrationType", "PROFILE_DETAILS_PHYSICAL_ATTRIBUTES")
            .withRequestBodyJsonPath("label", migrationId)
            .withRequestBody(matchingJsonPath("dpsIds[?(@ == 2)]")),
        )
      }
    }

    private fun waitUntilCompleted() =
      await atMost Duration.ofSeconds(60) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-completed"),
          any(),
          isNull(),
        )
      }
  }
}
