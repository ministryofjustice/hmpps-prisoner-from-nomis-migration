package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest.MigrationType.PHYSICAL_ATTRIBUTES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.PhysicalAttributesDpsApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.PhysicalAttributesNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.Duration

/*
 * These tests cover generic scenarios that apply to all prison person migration types, so they only need testing for one type.
 *
 * This means we don't need to duplicate these tests in each domain package.
 */
class PrisonPersonMigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var physicalAttributesNomisApi: PhysicalAttributesNomisApiMockServer

  @Autowired
  private lateinit var prisonPersonMappingApi: PrisonPersonMappingApiMockServer

  @Autowired
  private lateinit var dpsApi: PhysicalAttributesDpsApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("/migrate/prisonperson")
  inner class MigratePhysicalAttributes {
    private lateinit var migrationResult: MigrationResult

    private fun stubMigrationDependencies(entities: Int = 2) {
      nomisApi.stubGetPrisonIds(totalElements = entities.toLong(), pageSize = 10, firstOffenderNo = "A0001KT")
      (1L..entities)
        .map { "A000${it}KT" }
        .forEachIndexed { index, offenderNo ->
          physicalAttributesNomisApi.stubGetPhysicalAttributes(offenderNo)
          dpsApi.stubMigratePhysicalAttributes(offenderNo, PhysicalAttributesMigrationResponse(listOf(index + 1.toLong())))
          prisonPersonMappingApi.stubPutMapping()
        }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/prisonperson")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("{}")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/prisonperson")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("{}")
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/prisonperson")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("{}")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class Errors {
      @Test
      fun `will put message on DLQ if call to NOMIS fails`() {
        nomisApi.stubGetPrisonIds(totalElements = 1, pageSize = 10, firstOffenderNo = "A0001KT")
        physicalAttributesNomisApi.stubGetPhysicalAttributes("A0001KT", INTERNAL_SERVER_ERROR)

        migrationResult = webTestClient.performMigration()

        await untilAsserted {
          assertThat(prisonPersonMigrationDlqClient.countAllMessagesOnQueue(prisonPersonMigrationDlqUrl).get())
            .isEqualTo(1)
        }

        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-entity-failed"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0001KT",
                "migrationId" to migrationResult.migrationId,
                "migrationType" to "PRISON_PERSON",
                "prisonPersonMigrationType" to "PHYSICAL_ATTRIBUTES",
                "error" to "500 Internal Server Error from GET http://localhost:8081/prisoners/A0001KT/physical-attributes",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will put message on DLQ if call to DPS fails`() {
        nomisApi.stubGetPrisonIds(totalElements = 1, pageSize = 10, firstOffenderNo = "A0001KT")
        physicalAttributesNomisApi.stubGetPhysicalAttributes("A0001KT")
        dpsApi.stubMigratePhysicalAttributes(HttpStatus.BAD_REQUEST)

        migrationResult = webTestClient.performMigration()

        await untilAsserted {
          assertThat(prisonPersonMigrationDlqClient.countAllMessagesOnQueue(prisonPersonMigrationDlqUrl).get())
            .isEqualTo(1)
        }

        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-entity-failed"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0001KT",
                "migrationId" to migrationResult.migrationId,
                "migrationType" to "PRISON_PERSON",
                "prisonPersonMigrationType" to "PHYSICAL_ATTRIBUTES",
                "error" to "400 Bad Request from PUT http://localhost:8095/migration/prisoners/A0001KT/physical-attributes",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will retry if call to mapping service fails`() {
        nomisApi.stubGetPrisonIds(totalElements = 1, pageSize = 10, firstOffenderNo = "A0001KT")
        physicalAttributesNomisApi.stubGetPhysicalAttributes("A0001KT")
        dpsApi.stubMigratePhysicalAttributes("A0001KT", PhysicalAttributesMigrationResponse(listOf(1L)))
        prisonPersonMappingApi.stubPutMappingFailureFollowedBySuccess()

        migrationResult = webTestClient.performMigration()

        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-entity-migrated"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to "A0001KT",
                "migrationId" to migrationResult.migrationId,
                "migrationType" to "PRISON_PERSON",
                "prisonPersonMigrationType" to "PHYSICAL_ATTRIBUTES",
                "dpsIds" to "[1]",
              ),
            )
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class SinglePrisoner {
      private val offenderNo = "A1234AB"

      @BeforeEach
      fun setUp() {
        physicalAttributesNomisApi.stubGetPhysicalAttributes(offenderNo)
        dpsApi.stubMigratePhysicalAttributes(
          offenderNo,
          PhysicalAttributesMigrationResponse(listOf(1.toLong())),
        )
        prisonPersonMappingApi.stubPutMapping()

        migrationResult = webTestClient.performMigration(offenderNo)
      }

      @Test
      fun `will migrate physical attributes`() {
        dpsApi.verify(
          putRequestedFor(urlMatching("/migration/prisoners/$offenderNo/physical-attributes"))
            .withRequestBodyJsonPath("$[0].height", 180)
            .withRequestBodyJsonPath("$[0].weight", 80)
            .withRequestBodyJsonPath("$[0].appliesFrom", "2024-02-03T12:34:56Z[Europe/London]")
            .withRequestBodyJsonPath("$[0].appliesTo", "2024-10-21T12:34:56+01:00[Europe/London]")
            .withRequestBodyJsonPath("$[0].createdBy", "ANOTHER_USER"),
        )
      }

      @Test
      fun `will publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-entity-migrated"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to offenderNo,
                "migrationId" to migrationResult.migrationId,
                "dpsIds" to "[1]",
                "migrationType" to "PRISON_PERSON",
                "prisonPersonMigrationType" to "PHYSICAL_ATTRIBUTES",
              ),
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will create mapping`() {
        mappingApi.verify(
          putRequestedFor(urlEqualTo("/mapping/prisonperson/migration"))
            .withRequestBodyJsonPath("nomisPrisonerNumber", offenderNo)
            .withRequestBodyJsonPath("migrationType", "PHYSICAL_ATTRIBUTES")
            .withRequestBodyJsonPath("label", migrationResult.migrationId)
            .withRequestBody(matchingJsonPath("dpsIds[?(@ == 1)]")),
        )
      }
    }

    @Nested
    inner class SinglePrisonerNotFound {
      private val offenderNo = "A1234AB"

      @Test
      fun `will put message on DLQ if prisoner doesn't exist in NOMIS`() {
        physicalAttributesNomisApi.stubGetPhysicalAttributes(offenderNo, NOT_FOUND)

        migrationResult = webTestClient.performMigration(offenderNo)

        await untilAsserted {
          assertThat(prisonPersonMigrationDlqClient.countAllMessagesOnQueue(prisonPersonMigrationDlqUrl).get())
            .isEqualTo(1)
        }

        verify(telemetryClient).trackEvent(
          eq("prisonperson-migration-entity-failed"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "offenderNo" to offenderNo,
                "migrationId" to migrationResult.migrationId,
                "migrationType" to "PRISON_PERSON",
                "prisonPersonMigrationType" to "PHYSICAL_ATTRIBUTES",
                "error" to "404 Not Found from GET http://localhost:8081/prisoners/$offenderNo/physical-attributes",
              ),
            )
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("/migrate/prisonperson/{migrationid}/cancel")
    inner class CancelMigration {
      @BeforeEach
      internal fun createHistoryRecords() = runTest {
        migrationHistoryRepository.deleteAll()
      }

      @AfterEach
      fun tearDown() = runTest {
        migrationHistoryRepository.deleteAll()
      }

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/prisonperson/2020-01-01T00:00:00/cancel")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/prisonperson/2020-01-01T00:00:00/cancel")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/prisonperson/2020-01-01T00:00:00/cancel")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }

      @Test
      fun ` not found`() {
        webTestClient.post().uri("/migrate/prisonperson/2020-01-01T00:00:00/cancel")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isNotFound
      }

      @Test
      internal fun `should cancel a migration`() {
        stubMigrationDependencies(entities = 2)
        migrationResult = webTestClient.performMigration()

        webTestClient.post().uri("/migrate/prisonperson/${migrationResult.migrationId}/cancel")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isAccepted

        webTestClient.get().uri("/migrate/prisonperson/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

        await atMost (Duration.ofSeconds(60)) untilAsserted {
          webTestClient.get().uri("/migrate/prisonperson/history/${migrationResult.migrationId}")
            .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
            .header("Content-Type", "application/json")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
            .jsonPath("$.status").isEqualTo("CANCELLED")
        }
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
  }
}
