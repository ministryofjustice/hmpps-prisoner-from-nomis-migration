package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
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
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest.MigrationType.PHYSICAL_ATTRIBUTES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonMigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PhysicalAttributesMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var physicalAttributesNomisApi: PhysicalAttributesNomisApiMockServer

  @Autowired
  private lateinit var prisonPersonMappingApi: PrisonPersonMappingApiMockServer

  @Autowired
  private lateinit var dpsApi: PhysicalAttributesDpsApiMockServer

  @Nested
  inner class MigratePhysicalAttributes {
    private lateinit var migrationResult: MigrationResult

    private fun stubMigrationDependencies(entities: Int = 2) {
      nomisApi.stubGetPrisonIds(totalElements = entities.toLong(), pageSize = 10, firstOffenderNo = "A0001KT")
      (1L..entities)
        .map { "A000${it}KT" }
        .forEachIndexed { index, offenderNo ->
          physicalAttributesNomisApi.stubGetPhysicalAttributes(offenderNo)
          dpsApi.stubMigratePhysicalAttributes(offenderNo, PhysicalAttributesMigrationResponse(listOf(index + 1.toLong())))
          prisonPersonMappingApi.stubPostMapping()
        }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        stubMigrationDependencies(entities = 2)
        migrationResult = webTestClient.performMigration()
      }

      @Test
      fun `will migrate physical attributes`() {
        dpsApi.verify(
          putRequestedFor(urlMatching("/migration/prisoners/A0001KT/physical-attributes"))
            .withRequestBodyJsonPath("$[0].height", 180)
            .withRequestBodyJsonPath("$[0].weight", 80)
            .withRequestBodyJsonPath("$[0].appliesFrom", "2024-02-03T12:34:56Z[Europe/London]")
            .withRequestBodyJsonPath("$[0].appliesTo", "2024-10-21T12:34:56+01:00[Europe/London]")
            .withRequestBodyJsonPath("$[0].createdBy", "ANOTHER_USER")
            .withRequestBodyJsonPath("$[0].latestBooking", "true"),
        )
        dpsApi.verify(
          putRequestedFor(urlMatching("/migration/prisoners/A0002KT/physical-attributes")),
        )
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-entity-migrated"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0001KT",
                "migrationId" to migrationResult.migrationId,
                "dpsIds" to "[1]",
                "migrationType" to "PRISON_PERSON",
                "prisonPersonMigrationType" to "PHYSICAL_ATTRIBUTES",
              ),
            )
          },
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-entity-migrated"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0002KT",
                "migrationId" to migrationResult.migrationId,
                "dpsIds" to "[2]",
                "migrationType" to "PRISON_PERSON",
                "prisonPersonMigrationType" to "PHYSICAL_ATTRIBUTES",
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
            .withRequestBodyJsonPath("migrationType", "PHYSICAL_ATTRIBUTES")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBody(matchingJsonPath("dpsIds[?(@ == 1)]")),
        )
        mappingApi.verify(
          postRequestedFor(urlEqualTo("/mapping/prisonperson/migration"))
            .withRequestBodyJsonPath("nomisPrisonerNumber", "A0002KT")
            .withRequestBodyJsonPath("migrationType", "PHYSICAL_ATTRIBUTES")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBody(matchingJsonPath("dpsIds[?(@ == 2)]")),
        )
      }
    }

    @Nested
    inner class MultipleEntities {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonIds(totalElements = 1, pageSize = 10, firstOffenderNo = "A0001KT")
        dpsApi.stubMigratePhysicalAttributes("A0001KT", PhysicalAttributesMigrationResponse(listOf(1, 2, 3, 4)))
        physicalAttributesNomisApi.stubGetPhysicalAttributes("A0001KT", multiBookingMultiPhysicalAttributes("A0001KT"))
        prisonPersonMappingApi.stubPostMapping()

        migrationResult = webTestClient.performMigration()
      }

      @Test
      fun `will migrate physical attributes`() {
        dpsApi.verify(
          putRequestedFor(urlMatching("/migration/prisoners/A0001KT/physical-attributes"))
            .withRequestBodyJsonPath("$[0].height", 180)
            .withRequestBodyJsonPath("$[0].weight", 80)
            .withRequestBodyJsonPath("$[0].appliesFrom", "2024-02-03T12:34:56Z[Europe/London]")
            .withRequestBodyJsonPath("$[0].appliesTo", "2024-03-21T12:34:56Z[Europe/London]")
            .withRequestBodyJsonPath("$[0].createdBy", "ANOTHER_USER")
            .withRequestBodyJsonPath("$[1].height", 182)
            .withRequestBodyJsonPath("$[1].weight", 82)
            .withRequestBodyJsonPath("$[1].appliesFrom", "2024-02-03T12:34:56Z[Europe/London]")
            .withRequestBodyJsonPath("$[1].appliesTo", "2024-03-21T12:34:56Z[Europe/London]")
            .withRequestBodyJsonPath("$[1].createdBy", "ANOTHER_USER2")
            .withRequestBodyJsonPath("$[2].height", 184)
            .withRequestBodyJsonPath("$[2].weight", 84)
            .withRequestBodyJsonPath("$[2].appliesFrom", "2024-04-03T12:34:56+01:00[Europe/London]")
            .withRequestBodyJsonPath("$[2].appliesTo", "2024-10-21T12:34:56+01:00[Europe/London]")
            .withRequestBodyJsonPath("$[2].createdBy", "ANOTHER_USER3")
            .withRequestBodyJsonPath("$[3].height", 186)
            .withRequestBodyJsonPath("$[3].weight", 86)
            .withRequestBodyJsonPath("$[3].appliesFrom", "2024-04-03T12:34:56+01:00[Europe/London]")
            .withRequestBodyJsonPath("$[3].appliesTo", "2024-10-21T12:34:56+01:00[Europe/London]")
            .withRequestBodyJsonPath("$[3].createdBy", "ANOTHER_USER4"),
        )
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-entity-migrated"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0001KT",
                "migrationId" to migrationResult.migrationId,
                "dpsIds" to "[1, 2, 3, 4]",
                "migrationType" to "PRISON_PERSON",
                "prisonPersonMigrationType" to "PHYSICAL_ATTRIBUTES",
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
            .withRequestBodyJsonPath("migrationType", "PHYSICAL_ATTRIBUTES")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBody(matchingJsonPath("dpsIds[?(@ == 1)]"))
            .withRequestBody(matchingJsonPath("dpsIds[?(@ == 2)]"))
            .withRequestBody(matchingJsonPath("dpsIds[?(@ == 3)]"))
            .withRequestBody(matchingJsonPath("dpsIds[?(@ == 4)]")),

        )
      }
    }

    private fun WebTestClient.performMigration(offenderNo: String? = null) =
      post().uri("/migrate/prisonperson")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .bodyValue(PrisonPersonMigrationFilter(prisonerNumber = offenderNo, migrationType = PHYSICAL_ATTRIBUTES))
        .exchange()
        .expectStatus().isAccepted
        .returnResult<MigrationResult>().responseBody.blockFirst()!!
        .also {
          waitUntilCompleted()
        }

    private fun waitUntilCompleted() =
      await atMost Duration.ofSeconds(60) untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-completed"),
          any(),
          isNull(),
        )
      }

    private fun multiBookingMultiPhysicalAttributes(offenderNo: String) = PrisonerPhysicalAttributesResponse(
      offenderNo = offenderNo,
      bookings = listOf(
        BookingPhysicalAttributesResponse(
          bookingId = 1,
          startDateTime = "2024-02-03T12:34:56",
          endDateTime = "2024-03-21T12:34:56",
          latestBooking = true,
          physicalAttributes = listOf(
            PhysicalAttributesResponse(
              attributeSequence = 1,
              heightCentimetres = 180,
              weightKilograms = 80,
              createdBy = "A_USER",
              createDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
              modifiedBy = "ANOTHER_USER",
              modifiedDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
              auditModuleName = "MODULE",
            ),
            PhysicalAttributesResponse(
              attributeSequence = 2,
              heightCentimetres = 182,
              weightKilograms = 82,
              createdBy = "A_USER",
              createDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
              modifiedBy = "ANOTHER_USER2",
              modifiedDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
              auditModuleName = "MODULE",
            ),
          ),
        ),
        BookingPhysicalAttributesResponse(
          bookingId = 2,
          startDateTime = "2024-04-03T12:34:56",
          endDateTime = "2024-10-21T12:34:56",
          latestBooking = true,
          physicalAttributes = listOf(
            PhysicalAttributesResponse(
              attributeSequence = 1,
              heightCentimetres = 184,
              weightKilograms = 84,
              createdBy = "A_USER",
              createDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
              modifiedBy = "ANOTHER_USER3",
              modifiedDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
              auditModuleName = "MODULE",
            ),
            PhysicalAttributesResponse(
              attributeSequence = 2,
              heightCentimetres = 186,
              weightKilograms = 86,
              createdBy = "A_USER",
              createDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
              modifiedBy = "ANOTHER_USER4",
              modifiedDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
              auditModuleName = "MODULE",
            ),
          ),
        ),
      ),
    )
  }
}
