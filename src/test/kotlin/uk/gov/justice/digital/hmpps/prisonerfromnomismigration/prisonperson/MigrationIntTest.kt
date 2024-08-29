package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import kotlinx.coroutines.runBlocking
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsMigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension.Companion.dpsPrisonPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MigrationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var prisonPersonNomisApi: PrisonPersonNomisApiMockServer

  @Autowired
  private lateinit var prisonPersonMappingApi: PrisonPersonMappingApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("/migrate/prisonperson/physical-attributes")
  inner class MigratePhysicalAttributes {
    private lateinit var migrationResult: MigrationResult

    private fun stubMigrationDependencies(entities: Int = 2) {
      nomisApi.stubGetPrisonIds(totalElements = entities.toLong(), pageSize = 10, offenderNo = "A0001KT")
      (1L..entities)
        .map { "A000${it}KT" }
        .forEachIndexed { index, offenderNo ->
          prisonPersonNomisApi.stubGetPhysicalAttributes(offenderNo)
          dpsPrisonPersonServer.stubMigratePhysicalAttributes(offenderNo, PhysicalAttributesMigrationResponse(listOf(index + 1.toLong())))
          prisonPersonMappingApi.stubPostMapping()
        }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/prisonperson/physical-attributes")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(AlertsMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/prisonperson/physical-attributes")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(AlertsMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/prisonperson/physical-attributes")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(AlertsMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
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
        dpsPrisonPersonServer.verify(
          putRequestedFor(urlMatching("/migration/prisoners/A0001KT/physical-attributes"))
            .withRequestBodyJsonPath("$[0].height", 180)
            .withRequestBodyJsonPath("$[0].weight", 80)
            .withRequestBodyJsonPath("$[0].appliesFrom", "2024-02-03T12:34:56Z[Europe/London]")
            .withRequestBodyJsonPath("$[0].appliesTo", "2024-10-21T12:34:56+01:00[Europe/London]")
            .withRequestBodyJsonPath("$[0].createdBy", "ANOTHER_USER"),
        )
        dpsPrisonPersonServer.verify(
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
        nomisApi.stubGetPrisonIds(totalElements = 1, pageSize = 10, offenderNo = "A0001KT")
        dpsPrisonPersonServer.stubMigratePhysicalAttributes("A0001KT", PhysicalAttributesMigrationResponse(listOf(1, 2, 3, 4)))
        prisonPersonNomisApi.stubGetPhysicalAttributes("A0001KT", multiBookingMultiPhysicalAttributes("A0001KT"))
        prisonPersonMappingApi.stubPostMapping()

        migrationResult = webTestClient.performMigration()
      }

      @Test
      fun `will migrate physical attributes`() {
        dpsPrisonPersonServer.verify(
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

    @Nested
    inner class Errors {
      @Test
      fun `will put message on DLQ if call to NOMIS fails`() {
        nomisApi.stubGetPrisonIds(totalElements = 1, pageSize = 10, offenderNo = "A0001KT")
        prisonPersonNomisApi.stubGetPhysicalAttributes(INTERNAL_SERVER_ERROR)

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
        nomisApi.stubGetPrisonIds(totalElements = 1, pageSize = 10, offenderNo = "A0001KT")
        prisonPersonNomisApi.stubGetPhysicalAttributes("A0001KT")
        dpsPrisonPersonServer.stubMigratePhysicalAttributes(HttpStatus.BAD_REQUEST)

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
        nomisApi.stubGetPrisonIds(totalElements = 1, pageSize = 10, offenderNo = "A0001KT")
        prisonPersonNomisApi.stubGetPhysicalAttributes("A0001KT")
        dpsPrisonPersonServer.stubMigratePhysicalAttributes("A0001KT", PhysicalAttributesMigrationResponse(listOf(1L)))
        prisonPersonMappingApi.stubPostMappingFailureFollowedBySuccess()

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

      @Test
      fun `will not retry if mapping is a duplicate`() {
        nomisApi.stubGetPrisonIds(totalElements = 1, pageSize = 10, offenderNo = "A0001KT")
        prisonPersonNomisApi.stubGetPhysicalAttributes("A0001KT")
        dpsPrisonPersonServer.stubMigratePhysicalAttributes("A0001KT", PhysicalAttributesMigrationResponse(listOf(1L)))
        prisonPersonMappingApi.stubPostMappingDuplicate(
          DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PrisonPersonMigrationMappingRequest(
                nomisPrisonerNumber = "A0001KT",
                dpsIds = listOf(1L),
                migrationType = PrisonPersonMigrationMappingRequest.MigrationType.PHYSICAL_ATTRIBUTES,
                label = "label",
              ),
              existing = PrisonPersonMigrationMappingRequest(
                nomisPrisonerNumber = "A0001KT",
                dpsIds = listOf(1L),
                migrationType = PrisonPersonMigrationMappingRequest.MigrationType.PHYSICAL_ATTRIBUTES,
                label = "label",
              ),
            ),
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            errorCode = 1409,
            userMessage = "duplicate",
          ),
        )

        migrationResult = webTestClient.performMigration()

        verify(telemetryClient).trackEvent(
          eq("prisonperson-nomis-migration-duplicate"),
          check {
            assertThat(it).containsExactlyInAnyOrderEntriesOf(
              mapOf(
                "existingNomisPrisonerNumber" to "A0001KT",
                "existingDpsIds" to "[1]",
                "duplicateNomisPrisonerNumber" to "A0001KT",
                "duplicateDpsIds" to "[1]",
                "migrationId" to migrationResult.migrationId,
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
        prisonPersonNomisApi.stubGetPhysicalAttributes(offenderNo)
        dpsPrisonPersonServer.stubMigratePhysicalAttributes(
          offenderNo,
          PhysicalAttributesMigrationResponse(listOf(1.toLong())),
        )
        prisonPersonMappingApi.stubPostMapping()

        migrationResult = webTestClient.performMigration(offenderNo)
      }

      @Test
      fun `will migrate physical attributes`() {
        dpsPrisonPersonServer.verify(
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
          postRequestedFor(urlEqualTo("/mapping/prisonperson/migration"))
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
        prisonPersonNomisApi.stubGetPhysicalAttributes(NOT_FOUND)

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
      post().uri("/migrate/prisonperson/physical-attributes")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .bodyValue(MigrationFilter(prisonerNumber = offenderNo))
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

  @Nested
  @DisplayName("/migrate/prisonperson/history")
  inner class GetAllHistory {
    @BeforeEach
    fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.PRISONPERSON,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.PRISONPERSON,
          ),
        )
      }
    }

    @AfterEach
    fun tearDown() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/migrate/prisonperson/history")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.get().uri("/migrate/prisonperson/history")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.get().uri("/migrate/prisonperson/history")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `can read all records`() {
      webTestClient.get().uri("/migrate/prisonperson/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(2)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-02T00:00:00")
    }
  }

  @Nested
  @DisplayName("/migrate/prisonperson/history/{migrationid}")
  inner class GetSingleHistory {
    @BeforeEach
    internal fun createHistoryRecords() = runTest {
      migrationHistoryRepository.save(
        MigrationHistory(
          migrationId = "2020-01-01T00:00:00",
          whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
          whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
          status = MigrationStatus.COMPLETED,
          estimatedRecordCount = 123_567,
          filter = "",
          recordsMigrated = 123_560,
          recordsFailed = 7,
          migrationType = MigrationType.PRISONPERSON,
        ),
      )
    }

    @AfterEach
    fun tearDown() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/migrate/prisonperson/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.get().uri("/migrate/prisonperson/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.get().uri("/migrate/prisonperson/history/2020-01-01T00:00:00")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun ` not found`() {
      webTestClient.get().uri("/migrate/prisonperson/history/UNKNOWN")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    internal fun `can get a migration record`() {
      webTestClient.get().uri("/migrate/prisonperson/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .consumeWith { println(it) }
        .jsonPath("migrationId").isEqualTo("2020-01-01T00:00:00")
    }
  }

  @Nested
  @DisplayName("/migrate/prisonperson/active-migration")
  inner class GetActiveMigration {
    @BeforeEach
    internal fun createHistoryRecords() = runTest {
      migrationHistoryRepository.save(
        MigrationHistory(
          migrationId = "2020-01-01T00:00:00",
          whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
          whenEnded = null,
          status = MigrationStatus.STARTED,
          estimatedRecordCount = 123_567,
          filter = "",
          recordsMigrated = 123_560,
          recordsFailed = 7,
          migrationType = MigrationType.PRISONPERSON,
        ),
      )
    }

    @AfterEach
    fun tearDown() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/migrate/prisonperson/active-migration")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.get().uri("/migrate/prisonperson/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.get().uri("/migrate/prisonperson/active-migration")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `should get the active migration record`() {
      webTestClient.get().uri("/migrate/prisonperson/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("migrationId").isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    internal fun `should return nothing if no active migration`() = runTest {
      migrationHistoryRepository.deleteAll()

      webTestClient.get().uri("/migrate/prisonperson/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("migrationId").doesNotExist()
    }
  }
}
