package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse.Status
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitInternalLocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.migrateVisitConfigResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.VisitSlotsNomisApiMockServer.Companion.visitSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.VisitSlotsNomisApiMockServer.Companion.visitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.IdPair.ElementType.PRISON_VISIT_SLOT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.model.MigrateVisitConfigRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VisitSlotsMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: VisitSlotsNomisApiMockServer
  private val dpsApiMock = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @Autowired
  private lateinit var mappingApiMock: VisitSlotsMappingApiMockServer

  @Nested
  @DisplayName("POST /migrate/visitslots")
  inner class StartMigration {
    @BeforeEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/visitslots")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/visitslots")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/visitslots")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class EverythingAlreadyMigrated {
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetVisitTimeSlotIds(
          content = listOf(
            VisitTimeSlotIdResponse(
              prisonId = "WWI",
              dayOfWeek = VisitTimeSlotIdResponse.DayOfWeek.MON,
              timeSlotSequence = 2,
            ),
          ),
        )
        mappingApiMock.stubGetTimeSlotByNomisIdsOrNull(
          nomisPrisonId = "WWI",
          nomisDayOfWeek = "MON",
          nomisSlotSequence = 2,
          mapping = VisitTimeSlotMappingDto(
            dpsId = "10000",
            nomisPrisonId = "WWI",
            nomisDayOfWeek = "MON",
            nomisSlotSequence = 2,
            mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
            label = "2020-01-01T00:00:00",
          ),
        )
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will not bother retrieving any visit time slot details`() {
        nomisApiMock.verify(0, getRequestedFor(urlPathEqualTo("/visits/configuration/time-slots/prison-id/WW1/day-of-week/MONDAY/time-slot-sequence/2")))
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
    inner class HappyPath {
      private lateinit var migrationResult: MigrationResult
      val nomisLocationId = 12345L
      val dpsLocationId: UUID = UUID.fromString("e7c2a3cc-e5b2-48ff-9e8b-a5038355b36c")
      val nomisPrisonId = "WWI"
      val nomisSlotSequence = 2
      val dpsTimeSlotId = 54321L
      val dpsVisitSlotId = 45678L
      val nomisVisitSlotId = 876544L

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetVisitTimeSlotIds(
          content = listOf(
            VisitTimeSlotIdResponse(
              prisonId = nomisPrisonId,
              dayOfWeek = VisitTimeSlotIdResponse.DayOfWeek.MON,
              timeSlotSequence = nomisSlotSequence,
            ),
          ),
        )
        mappingApiMock.stubGetTimeSlotByNomisIdsOrNull(
          nomisPrisonId = nomisPrisonId,
          nomisDayOfWeek = "MON",
          nomisSlotSequence = nomisSlotSequence,
          mapping = null,
        )
        nomisApiMock.stubGetVisitTimeSlot(
          prisonId = nomisPrisonId,
          dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON,
          timeSlotSequence = nomisSlotSequence,
          response = visitTimeSlotResponse().copy(
            visitSlots = listOf(
              visitSlotResponse().copy(
                id = 9876,
                internalLocation = VisitInternalLocationResponse(
                  id = nomisLocationId,
                  code = "WWI-VISITS-ROOM-1",
                ),
                maxGroups = 19,
                maxAdults = 20,
                audit = NomisAudit(
                  createDatetime = LocalDateTime.parse("2020-01-01T00:00:00"),
                  createUsername = "JILL.BEAK",
                ),
              ),
            ),
            prisonId = nomisPrisonId,
            dayOfWeek = VisitTimeSlotResponse.DayOfWeek.MON,
            timeSlotSequence = nomisSlotSequence,
            startTime = "10:00",
            endTime = "11:15",
            effectiveDate = LocalDate.parse("2020-01-01"),
            expiryDate = LocalDate.parse("2030-01-01"),
            audit = NomisAudit(
              createDatetime = LocalDateTime.parse("2020-01-03T00:00:00"),
              createUsername = "JILL.WIK",
              modifyDatetime = LocalDateTime.parse("2020-01-04T00:00:00"),
              modifyUserId = "BOB.SMITH",
            ),
          ),
        )
        mappingApiMock.stubGetInternalLocationByNomisId(
          nomisLocationId = nomisLocationId,
          mapping = LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )
        dpsApiMock.stubMigrateVisitConfiguration(
          response = migrateVisitConfigResponse().copy(
            dpsTimeSlotId = dpsTimeSlotId,
            visitSlots = listOf(
              IdPair(elementType = PRISON_VISIT_SLOT, nomisId = nomisVisitSlotId, dpsId = dpsVisitSlotId),
            ),
          ),
        )
        mappingApiMock.stubCreateMappingsForMigration()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will retrieve visit time slot details`() {
        nomisApiMock.verify(getRequestedFor(urlPathEqualTo("/visits/configuration/time-slots/prison-id/$nomisPrisonId/day-of-week/MON/time-slot-sequence/$nomisSlotSequence")))
      }

      @Test
      fun `will retrieve DPS visit room location id`() {
        mappingApiMock.verify(getRequestedFor(urlPathEqualTo("/mapping/locations/nomis/$nomisLocationId")))
      }

      @Test
      fun `will transform and migrate time and visit slots into DPS`() {
        val migrationRequest: MigrateVisitConfigRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/migrate/visit-configuration")))

        assertThat(migrationRequest.effectiveDate).isEqualTo(LocalDate.parse("2020-01-01"))
        assertThat(migrationRequest.expiryDate).isEqualTo(LocalDate.parse("2030-01-01"))
        assertThat(migrationRequest.startTime).isEqualTo("10:00")
        assertThat(migrationRequest.endTime).isEqualTo("11:15")
        assertThat(migrationRequest.dayCode).isEqualTo("MON")
        assertThat(migrationRequest.createUsername).isEqualTo("JILL.WIK")
        assertThat(migrationRequest.createDateTime).isEqualTo(LocalDateTime.parse("2020-01-03T00:00:00"))
        assertThat(migrationRequest.modifyUsername).isEqualTo("BOB.SMITH")
        assertThat(migrationRequest.modifyDateTime).isEqualTo(LocalDateTime.parse("2020-01-04T00:00:00"))
        assertThat(migrationRequest.visitSlots).hasSize(1)
        assertThat(migrationRequest.visitSlots[0].maxAdults).isEqualTo(20)
        assertThat(migrationRequest.visitSlots[0].maxGroups).isEqualTo(19)
        assertThat(migrationRequest.visitSlots[0].dpsLocationId).isEqualTo(dpsLocationId)
        assertThat(migrationRequest.visitSlots[0].createUsername).isEqualTo("JILL.BEAK")
        assertThat(migrationRequest.visitSlots[0].createDateTime).isEqualTo(LocalDateTime.parse("2020-01-01T00:00:00"))
        assertThat(migrationRequest.visitSlots[0].modifyUsername).isNull()
        assertThat(migrationRequest.visitSlots[0].modifyDateTime).isNull()
      }

      @Test
      fun `will create mappings for time and visit slot`() {
        val mappingRequests: List<VisitTimeSlotMigrationMappingDto> = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/visit-slots")))

        assertThat(mappingRequests).hasSize(1)

        with(mappingRequests.first()) {
          assertThat(mappingType).isEqualTo(VisitTimeSlotMigrationMappingDto.MappingType.MIGRATED)
          assertThat(label).isEqualTo(migrationResult.migrationId)
          assertThat(nomisPrisonId).isEqualTo(nomisPrisonId)
          assertThat(nomisDayOfWeek).isEqualTo("MON")
          assertThat(nomisSlotSequence).isEqualTo(nomisSlotSequence)
          assertThat(dpsId).isEqualTo(dpsTimeSlotId.toString())
          assertThat(visitSlots).hasSize(1)
          assertThat(visitSlots[0].dpsId).isEqualTo(dpsVisitSlotId.toString())
          assertThat(visitSlots[0].nomisId).isEqualTo(nomisVisitSlotId)
        }
      }

      @Test
      fun `will track telemetry for each slot migrated`() {
        verify(telemetryClient).trackEvent(
          eq("visitslots-migration-entity-migrated"),
          check {
            assertThat(it["nomisPrisonId"]).isEqualTo(nomisPrisonId)
            assertThat(it["nomisDayOfWeek"]).isEqualTo("MON")
            assertThat(it["nomisSlotSequence"]).isEqualTo(nomisSlotSequence.toString())
            assertThat(it["dpsId"]).isEqualTo(dpsTimeSlotId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will record the number of visit time slots migrated`() {
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

    @Nested
    inner class FailureWithRecoverPath {
      private lateinit var migrationResult: MigrationResult
      val nomisLocationId = 12345L
      val dpsLocationId: UUID = UUID.fromString("e7c2a3cc-e5b2-48ff-9e8b-a5038355b36c")
      val nomisPrisonId = "WWI"
      val nomisSlotSequence = 2
      val dpsTimeSlotId = 54321L
      val dpsVisitSlotId = 45678L
      val nomisVisitSlotId = 876544L

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetVisitTimeSlotIds(
          content = listOf(
            VisitTimeSlotIdResponse(
              prisonId = nomisPrisonId,
              dayOfWeek = VisitTimeSlotIdResponse.DayOfWeek.MON,
              timeSlotSequence = nomisSlotSequence,
            ),
          ),
        )
        mappingApiMock.stubGetTimeSlotByNomisIdsOrNull(
          nomisPrisonId = nomisPrisonId,
          nomisDayOfWeek = "MON",
          nomisSlotSequence = nomisSlotSequence,
          mapping = null,
        )
        nomisApiMock.stubGetVisitTimeSlot(
          prisonId = nomisPrisonId,
          dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON,
          timeSlotSequence = nomisSlotSequence,
          response = visitTimeSlotResponse().copy(
            visitSlots = listOf(
              visitSlotResponse().copy(
                id = 9876,
                internalLocation = VisitInternalLocationResponse(
                  id = nomisLocationId,
                  code = "WWI-VISITS-ROOM-1",
                ),
              ),
            ),
            prisonId = nomisPrisonId,
            dayOfWeek = VisitTimeSlotResponse.DayOfWeek.MON,
            timeSlotSequence = nomisSlotSequence,
          ),
        )
        mappingApiMock.stubGetInternalLocationByNomisId(
          nomisLocationId = nomisLocationId,
          mapping = LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )
        dpsApiMock.stubMigrateVisitConfiguration(
          response = migrateVisitConfigResponse().copy(
            dpsTimeSlotId = dpsTimeSlotId,
            visitSlots = listOf(
              IdPair(elementType = PRISON_VISIT_SLOT, nomisId = nomisVisitSlotId, dpsId = dpsVisitSlotId),
            ),
          ),
        )
        mappingApiMock.stubCreateMappingsForMigrationFailureFollowedBySuccess()
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 1)
        migrationResult = performMigration()
      }

      @Test
      fun `will transform and migrate time and visit slots into DPS`() {
        val migrationRequest: MigrateVisitConfigRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/migrate/visit-configuration")))

        assertThat(migrationRequest.dayCode).isEqualTo("MON")
        assertThat(migrationRequest.visitSlots).hasSize(1)
      }

      @Test
      fun `will eventually create mappings for time and visit slot`() {
        val mappingRequests: List<VisitTimeSlotMigrationMappingDto> = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/visit-slots")))

        await untilAsserted {
          assertThat(mappingRequests).hasSize(2)
        }

        mappingRequests.forEach {
          assertThat(it.dpsId).isEqualTo(dpsTimeSlotId.toString())
        }
      }

      @Test
      fun `will eventually track telemetry for each slot migrated`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("visitslots-migration-entity-migrated"),
            check {
              assertThat(it["nomisPrisonId"]).isEqualTo(nomisPrisonId)
              assertThat(it["nomisDayOfWeek"]).isEqualTo("MON")
              assertThat(it["nomisSlotSequence"]).isEqualTo(nomisSlotSequence.toString())
              assertThat(it["dpsId"]).isEqualTo(dpsTimeSlotId.toString())
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will record the number of visit time slots migrated`() {
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

    @Nested
    inner class FailureWithDuplicate {
      private lateinit var migrationResult: MigrationResult
      val nomisLocationId = 12345L
      val dpsLocationId: UUID = UUID.fromString("e7c2a3cc-e5b2-48ff-9e8b-a5038355b36c")
      val nomisPrisonId = "WWI"
      val nomisSlotSequence = 2
      val dpsTimeSlotId = 54321L
      val dpsVisitSlotId = 45678L
      val nomisVisitSlotId = 876544L

      @BeforeEach
      fun setUp() {
        nomisApiMock.stubGetVisitTimeSlotIds(
          content = listOf(
            VisitTimeSlotIdResponse(
              prisonId = nomisPrisonId,
              dayOfWeek = VisitTimeSlotIdResponse.DayOfWeek.MON,
              timeSlotSequence = nomisSlotSequence,
            ),
          ),
        )
        mappingApiMock.stubGetTimeSlotByNomisIdsOrNull(
          nomisPrisonId = nomisPrisonId,
          nomisDayOfWeek = "MON",
          nomisSlotSequence = nomisSlotSequence,
          mapping = null,
        )
        nomisApiMock.stubGetVisitTimeSlot(
          prisonId = nomisPrisonId,
          dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON,
          timeSlotSequence = nomisSlotSequence,
          response = visitTimeSlotResponse().copy(
            visitSlots = listOf(
              visitSlotResponse().copy(
                id = 9876,
                internalLocation = VisitInternalLocationResponse(
                  id = nomisLocationId,
                  code = "WWI-VISITS-ROOM-1",
                ),
              ),
            ),
            prisonId = nomisPrisonId,
            dayOfWeek = VisitTimeSlotResponse.DayOfWeek.MON,
            timeSlotSequence = nomisSlotSequence,
          ),
        )
        mappingApiMock.stubGetInternalLocationByNomisId(
          nomisLocationId = nomisLocationId,
          mapping = LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )
        dpsApiMock.stubMigrateVisitConfiguration(
          response = migrateVisitConfigResponse().copy(
            dpsTimeSlotId = dpsTimeSlotId,
            visitSlots = listOf(
              IdPair(elementType = PRISON_VISIT_SLOT, nomisId = nomisVisitSlotId, dpsId = dpsVisitSlotId),
            ),
          ),
        )
        mappingApiMock.stubCreateMappingsForMigration(
          DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = VisitTimeSlotMigrationMappingDto(
                dpsId = dpsTimeSlotId.toString(),
                nomisPrisonId = nomisPrisonId,
                nomisDayOfWeek = "MON",
                nomisSlotSequence = 2,
                visitSlots = emptyList(),
                mappingType = VisitTimeSlotMigrationMappingDto.MappingType.MIGRATED,
              ),
              existing = VisitTimeSlotMigrationMappingDto(
                dpsId = "9999",
                nomisPrisonId = nomisPrisonId,
                nomisDayOfWeek = "MON",
                nomisSlotSequence = 2,
                visitSlots = emptyList(),
                mappingType = VisitTimeSlotMigrationMappingDto.MappingType.MIGRATED,

              ),
            ),
            status = Status._409_CONFLICT,
            errorCode = 1409,
            userMessage = "Duplicate",
          ),
        )
        mappingApiMock.stubGetMigrationCount(migrationId = ".*", count = 0)
        migrationResult = performMigration()
      }

      @Test
      fun `will transform and migrate time and visit slots into DPS`() {
        val migrationRequest: MigrateVisitConfigRequest = getRequestBody(postRequestedFor(urlPathEqualTo("/migrate/visit-configuration")))

        assertThat(migrationRequest.dayCode).isEqualTo("MON")
        assertThat(migrationRequest.visitSlots).hasSize(1)
      }

      @Test
      fun `will only try create mappings once`() {
        val mappingRequests: List<VisitTimeSlotMigrationMappingDto> = MappingApiExtension.getRequestBodies(postRequestedFor(urlPathEqualTo("/mapping/visit-slots")))

        await untilAsserted {
          assertThat(mappingRequests).hasSize(1)
        }
      }

      @Test
      fun `will never track telemetry for each slot migrated`() {
        verify(telemetryClient, times(0)).trackEvent(
          eq("visitslots-migration-entity-migrated"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will record the number of visit time slots migrated`() {
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

  private fun performMigration(): MigrationResult = webTestClient.post().uri("/migrate/visitslots")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .exchange()
    .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("visitslots-migration-completed"),
      any(),
      isNull(),
    )
  }
}
