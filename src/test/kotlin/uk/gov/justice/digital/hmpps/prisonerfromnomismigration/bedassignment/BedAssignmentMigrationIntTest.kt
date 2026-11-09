package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.BedAssignmentDpsApiExtension.Companion.bedAssignmentApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.Duration

private const val MIGRATION_OFFENDER_1 = "A0001KT"
private const val MIGRATION_OFFENDER_2 = "A0002KT"
private const val MIGRATION_DPS_ID_1 = "00000000-0000-0000-0000-000000000001"
private const val MIGRATION_DPS_ID_2 = "00000000-0000-0000-0000-000000000002"
private const val MIGRATION_LOCATION_ID = "abcdef12-1234-5678-9012-abcdef123456"

@TestPropertySource(properties = ["bedassignment.page.size=2"])
class BedAssignmentMigrationIntTest(
  @Autowired private val nomisMockServer: BedAssignmentNomisApiMockServer,
  @Autowired private val mappingMockServer: BedAssignmentMappingApiMockServer,
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : BedAssignmentIntegrationTestBase() {
  override fun getQueues(): List<HmppsQueue> = listOf(bedAssignmentMigrationQueue)

  @BeforeEach
  fun clearMigrationHistory() = runBlocking {
    migrationHistoryRepository.deleteAll()
  }

  @Nested
  @DisplayName("POST /migrate/bed-assignments")
  inner class StartMigration {
    @Nested
    inner class Security {
      @Test
      fun `returns forbidden when no role is present`() {
        webTestClient.post().uri("/migrate/bed-assignments")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `returns forbidden for the wrong role`() {
        webTestClient.post().uri("/migrate/bed-assignments")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `returns unauthorized without a token`() {
        webTestClient.post().uri("/migrate/bed-assignments")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `rejects an empty explicit prisoner list`() {
        startRequest("""{"offenderNos":[]}""")
          .expectStatus().isBadRequest
      }

      @Test
      fun `rejects an invalid prisoner number`() {
        startRequest("""{"offenderNos":["not-a-prisoner"]}""")
          .expectStatus().isBadRequest
      }
    }

    @Nested
    inner class Selection {
      @Test
      fun `migrates prisoners from NOMIS when no explicit filter is supplied`() {
        nomisApi.stubGetPrisonerIds(totalElements = 1, pageSize = 1, firstOffenderNo = MIGRATION_OFFENDER_1)
        stubHistory(MIGRATION_OFFENDER_1, bedAssignmentJson())
        bedAssignmentApi.stubMigrate(
          MIGRATION_OFFENDER_1,
          """[{"id":"$MIGRATION_DPS_ID_1","bookingId":123456,"sequence":1}]""",
        )
        mappingMockServer.stubCreateMigrationMappings(MIGRATION_OFFENDER_1)

        performMigration(PrisonerMigrationFilter())

        nomisApi.verify(2, getRequestedFor(urlPathEqualTo("/prisoners/ids/all")))
        bedAssignmentApi.verify(1, postRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_1")))
      }
    }

    @Nested
    inner class HappyPath {
      private lateinit var migration: MigrationResult

      @BeforeEach
      fun setUp() {
        stubLocations()
        stubHistory(MIGRATION_OFFENDER_1, bedAssignmentJson(bookingId = 123456, sequence = 1))
        stubHistory(
          MIGRATION_OFFENDER_2,
          bedAssignmentJson(bookingId = 234567, sequence = 2),
          bedAssignmentJson(bookingId = 234567, sequence = 3, livingUnitId = 987655),
        )
        bedAssignmentApi.stubMigrate(
          MIGRATION_OFFENDER_1,
          """[{"id":"$MIGRATION_DPS_ID_1","bookingId":123456,"sequence":1}]""",
        )
        bedAssignmentApi.stubMigrate(
          MIGRATION_OFFENDER_2,
          """[{"id":"$MIGRATION_DPS_ID_2","bookingId":234567,"sequence":2},{"id":"$MIGRATION_DPS_ID_1","bookingId":234567,"sequence":3}]""",
        )
        mappingMockServer.stubCreateMigrationMappings(MIGRATION_OFFENDER_1)
        mappingMockServer.stubCreateMigrationMappings(MIGRATION_OFFENDER_2)
        migration = performMigration(PrisonerMigrationFilter(listOf(MIGRATION_OFFENDER_1, MIGRATION_OFFENDER_2)))
      }

      @Test
      fun `migrates each explicit prisoner once`() {
        bedAssignmentApi.verify(1, postRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_1")))
        bedAssignmentApi.verify(1, postRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_2")))
        nomisApi.verify(0, getRequestedFor(urlPathEqualTo("/prisoners/ids/all")))
      }

      @Test
      fun `sends complete history fields including optional historical values to DPS`() {
        bedAssignmentApi.verify(
          postRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_1"))
            .withRequestBodyJsonPath("$[0].bookingId", 123456)
            .withRequestBodyJsonPath("$[0].sequence", 1)
            .withRequestBodyJsonPath("$[0].livingUnitId", 987654)
            .withRequestBodyJsonPath("$[0].livingUnitDescription", "A-1-001")
            .withRequestBodyJsonPath("$[0].locationId", MIGRATION_LOCATION_ID)
            .withRequestBodyJsonPath("$[0].assignmentDateTime", "2024-01-02T03:04:05")
            .withRequestBodyJsonPath("$[0].assignmentReasonCode", "TRANSFER")
            .withRequestBodyJsonPath("$[0].createdBy", "NOMIS.USER"),
        )
        bedAssignmentApi.verify(
          postRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_2"))
            .withRequestBodyJsonPath("$[1].bookingId", 234567)
            .withRequestBodyJsonPath("$[1].sequence", 3)
            .withRequestBodyJsonPath("$[1].livingUnitId", 987655),
        )
      }

      @Test
      fun `persists migration ids and the migration label for each prisoner`() {
        mappingApi.verify(
          postRequestedFor(urlEqualTo("/mapping/bed-assignments/$MIGRATION_OFFENDER_1/all"))
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migration.migrationId)
            .withRequestBodyJsonPath("mappings[0].dpsBedAssignmentId", MIGRATION_DPS_ID_1)
            .withRequestBodyJsonPath("mappings[0].nomisBookingId", 123456)
            .withRequestBodyJsonPath("mappings[0].nomisSequence", 1),
        )
        mappingApi.verify(
          postRequestedFor(urlEqualTo("/mapping/bed-assignments/$MIGRATION_OFFENDER_2/all"))
            .withRequestBodyJsonPath("label", migration.migrationId)
            .withRequestBodyJsonPath("mappings[1].nomisSequence", 3),
        )
      }

      @Test
      fun `returns a completed migration status`() {
        webTestClient.get().uri("/migrate/history/${migration.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migration.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo(0)
      }
    }

    @Nested
    inner class EmptyHistory {
      @Test
      fun `migrates an empty history with an empty DPS and mapping list`() {
        stubHistory(MIGRATION_OFFENDER_1)
        bedAssignmentApi.stubMigrate(MIGRATION_OFFENDER_1, "[]")
        mappingMockServer.stubCreateMigrationMappings(MIGRATION_OFFENDER_1)
        val migration = performMigration(PrisonerMigrationFilter(listOf(MIGRATION_OFFENDER_1)))

        bedAssignmentApi.verify(
          postRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_1"))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.equalToJson("[]")),
        )
        mappingApi.verify(
          postRequestedFor(urlEqualTo("/mapping/bed-assignments/$MIGRATION_OFFENDER_1/all"))
            .withRequestBodyJsonPath("$.mappings.size()", 0)
            .withRequestBodyJsonPath("mappingType", "MIGRATED")
            .withRequestBodyJsonPath("label", migration.migrationId),
        )
        assertThat(migration.migrationId).isNotBlank()
      }
    }

    @Nested
    inner class MappingRecovery {
      @Test
      fun `retries mapping persistence without repeating the DPS migration`() {
        stubHistory(MIGRATION_OFFENDER_1, bedAssignmentJson())
        bedAssignmentApi.stubMigrate(
          MIGRATION_OFFENDER_1,
          """[{"id":"$MIGRATION_DPS_ID_1","bookingId":123456,"sequence":1}]""",
        )
        mappingMockServer.stubCreateMigrationMappingsFailureFollowedBySuccess(MIGRATION_OFFENDER_1)
        val migration = performMigration(PrisonerMigrationFilter(listOf(MIGRATION_OFFENDER_1)))

        bedAssignmentApi.verify(1, postRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_1")))
        mappingApi.verify(2, postRequestedFor(urlEqualTo("/mapping/bed-assignments/$MIGRATION_OFFENDER_1/all")))
        verify(telemetryClient).trackEvent(eq("bedassignment-migration-entity-mapping-pending"), any(), isNull())
      }

      @Test
      fun `normalizes repeated prisoner selections and counts the prisoner once`() {
        stubHistory(MIGRATION_OFFENDER_1, bedAssignmentJson())
        bedAssignmentApi.stubMigrate(
          MIGRATION_OFFENDER_1,
          """[{"id":"$MIGRATION_DPS_ID_1","bookingId":123456,"sequence":1}]""",
        )
        mappingMockServer.stubCreateMigrationMappings(MIGRATION_OFFENDER_1)
        val migration = performMigration(PrisonerMigrationFilter(listOf(MIGRATION_OFFENDER_1, MIGRATION_OFFENDER_1)))

        bedAssignmentApi.verify(1, postRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_1")))
        mappingApi.verify(1, postRequestedFor(urlEqualTo("/mapping/bed-assignments/$MIGRATION_OFFENDER_1/all")))
      }

      @Test
      fun `reconciles an identical mapping conflict before recording completion`() {
        stubHistory(MIGRATION_OFFENDER_1, bedAssignmentJson())
        bedAssignmentApi.stubMigrate(
          MIGRATION_OFFENDER_1,
          """[{"id":"$MIGRATION_DPS_ID_1","bookingId":123456,"sequence":1}]""",
        )
        mappingMockServer.stubMigrationMappingsDuplicate(MIGRATION_OFFENDER_1, MIGRATION_DPS_ID_1, MIGRATION_DPS_ID_1)
        mappingMockServer.stubGetByNomisId(
          123456,
          1,
          bedAssignmentMappingJson(123456, 1, MIGRATION_DPS_ID_1, "MIGRATED"),
        )

        val migration = performMigration(PrisonerMigrationFilter(listOf(MIGRATION_OFFENDER_1)))

        verify(telemetryClient).trackEvent(eq("bedassignment-migration-entity-duplicate"), any(), isNull())
      }

      @Test
      fun `does not overwrite a conflicting mapping and dead letters its mapping retry`() {
        stubHistory(MIGRATION_OFFENDER_1, bedAssignmentJson())
        bedAssignmentApi.stubMigrate(
          MIGRATION_OFFENDER_1,
          """[{"id":"$MIGRATION_DPS_ID_1","bookingId":123456,"sequence":1}]""",
        )
        mappingMockServer.stubMigrationMappingsDuplicate(
          MIGRATION_OFFENDER_1,
          MIGRATION_DPS_ID_1,
          MIGRATION_DPS_ID_2,
        )
        mappingMockServer.stubGetByNomisId(
          123456,
          1,
          bedAssignmentMappingJson(123456, 1, MIGRATION_DPS_ID_2, "MIGRATED"),
        )

        val migration = performMigration(PrisonerMigrationFilter(listOf(MIGRATION_OFFENDER_1)))

        await untilCallTo {
          bedAssignmentMigrationQueue.sqsDlqClient!!.countMessagesOnQueue(bedAssignmentMigrationDlqUrl).get()
        } matches { it == 1 }
        bedAssignmentApi.verify(1, postRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_1")))
        mappingApi.verify(0, postRequestedFor(urlEqualTo("/mapping/bed-assignments")))
        assertMigrationCount(migration.migrationId, 0)
        verify(telemetryClient, atLeast(2)).trackEvent(eq("bedassignment-migration-entity-duplicate"), any(), isNull())
      }
    }

    @Nested
    inner class DownstreamFailures {
      @Test
      fun `dead letters a persistent NOMIS history failure`() {
        nomisMockServer.stubGetBedAssignmentsError(MIGRATION_OFFENDER_1)

        performMigration(PrisonerMigrationFilter(listOf(MIGRATION_OFFENDER_1)))

        await untilCallTo { bedAssignmentMigrationQueue.sqsDlqClient!!.countMessagesOnQueue(bedAssignmentMigrationDlqUrl).get() } matches { it == 1 }
        bedAssignmentApi.verify(0, postRequestedFor(urlPathEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_1")))
        verify(telemetryClient, times(2)).trackEvent(eq("bedassignment-migration-entity-failed"), any(), isNull())
      }

      @Test
      fun `dead letters a persistent DPS migration failure`() {
        stubHistory(MIGRATION_OFFENDER_1, bedAssignmentJson())
        bedAssignmentApi.stubMigrateError(MIGRATION_OFFENDER_1, 400)

        performMigration(PrisonerMigrationFilter(listOf(MIGRATION_OFFENDER_1)))

        await untilCallTo { bedAssignmentMigrationQueue.sqsDlqClient!!.countMessagesOnQueue(bedAssignmentMigrationDlqUrl).get() } matches { it == 1 }
        bedAssignmentApi.verify(2, postRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_1")))
        mappingApi.verify(0, postRequestedFor(urlEqualTo("/mapping/bed-assignments/$MIGRATION_OFFENDER_1/all")))
      }

      @Test
      fun `dead letters a location lookup failure rather than dropping the assignment`() {
        stubHistory(MIGRATION_OFFENDER_1, bedAssignmentJson())
        mappingApi.stubFor(
          com.github.tomakehurst.wiremock.client.WireMock.get(urlPathMatching("/mapping/locations/nomis/.*"))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(500)),
        )

        performMigration(PrisonerMigrationFilter(listOf(MIGRATION_OFFENDER_1)))

        await untilCallTo { bedAssignmentMigrationQueue.sqsDlqClient!!.countMessagesOnQueue(bedAssignmentMigrationDlqUrl).get() } matches { it == 1 }
        bedAssignmentApi.verify(0, postRequestedFor(urlPathEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_1")))
      }
    }

    @Nested
    inner class PersistentMappingFailure {
      @Test
      fun `dead letters mapping-only retries without repeating the DPS migration`() {
        stubHistory(MIGRATION_OFFENDER_1, bedAssignmentJson())
        bedAssignmentApi.stubMigrate(
          MIGRATION_OFFENDER_1,
          """[{"id":"$MIGRATION_DPS_ID_1","bookingId":123456,"sequence":1}]""",
        )
        mappingApi.stubFor(
          com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/mapping/bed-assignments/$MIGRATION_OFFENDER_1/all"))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(500)),
        )

        val migration = performMigration(PrisonerMigrationFilter(listOf(MIGRATION_OFFENDER_1)))

        await untilCallTo { bedAssignmentMigrationQueue.sqsDlqClient!!.countMessagesOnQueue(bedAssignmentMigrationDlqUrl).get() } matches { it == 1 }
        bedAssignmentApi.verify(1, postRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/migrate/$MIGRATION_OFFENDER_1")))
        assertMigrationCount(migration.migrationId, 0)
      }
    }
  }

  private fun startRequest(body: String) = webTestClient.post().uri("/migrate/bed-assignments")
    .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(body)
    .exchange()

  private fun performMigration(filter: PrisonerMigrationFilter): MigrationResult {
    val result = webTestClient.post().uri("/migrate/bed-assignments")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(filter)
      .exchange()
      .expectStatus().isAccepted
      .returnResult<MigrationResult>()
      .responseBody.blockFirst()!!
    await atMost Duration.ofSeconds(60) untilAsserted {
      verify(telemetryClient).trackEvent(
        eq("bedassignment-migration-completed"),
        check { assertThat(it).containsEntry("migrationId", result.migrationId) },
        isNull(),
      )
    }
    return result
  }

  private fun assertMigrationCount(migrationId: String, expectedCount: Int) {
    webTestClient.get().uri("/migrate/history/$migrationId")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.recordsMigrated").isEqualTo(expectedCount)
  }

  private fun stubHistory(offenderNo: String, vararg rows: String) {
    nomisMockServer.stubGetBedAssignmentsForPrisoner(offenderNo, bedAssignmentHistoryWithRowsJson(*rows))
  }

  private fun stubLocations() {
    mappingApi.stubFor(
      com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/mapping/locations/nomis/987654"))
        .willReturn(
          com.github.tomakehurst.wiremock.client.WireMock.okJson(
            """{"dpsLocationId":"$MIGRATION_LOCATION_ID","nomisLocationId":987654,"mappingType":"MIGRATED"}""",
          ),
        ),
    )
    mappingApi.stubFor(
      com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/mapping/locations/nomis/987655"))
        .willReturn(
          com.github.tomakehurst.wiremock.client.WireMock.okJson(
            """{"dpsLocationId":"$MIGRATION_LOCATION_ID","nomisLocationId":987655,"mappingType":"MIGRATED"}""",
          ),
        ),
    )
  }
}
