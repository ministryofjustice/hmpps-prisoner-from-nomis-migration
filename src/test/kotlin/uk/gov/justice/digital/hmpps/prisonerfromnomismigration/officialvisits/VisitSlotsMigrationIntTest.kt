package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
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
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.PrisonerRestrictionMigrationFilter
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VisitSlotsMigrationIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMock: VisitSlotsNomisApiMockServer

  @Autowired
  private lateinit var mappingApiMock: VisitSlotsMappingApiMockServer

  @Nested
  @DisplayName("POST /migrate/visitslots")
  inner class MigrateContactPersons {
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
          .bodyValue(PrisonerRestrictionMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/visitslots")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerRestrictionMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/visitslots")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(PrisonerRestrictionMigrationFilter())
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
              dayOfWeek = VisitTimeSlotIdResponse.DayOfWeek.MONDAY,
              timeSlotSequence = 2,
            ),
          ),
        )
        mappingApiMock.stubGetByNomisIdsOrNull(
          nomisPrisonId = "WWI",
          nomisDayOfWeek = VisitTimeSlotMappingDto.NomisDayOfWeek.MONDAY,
          nomisSlotSequence = 2,
          mapping = VisitTimeSlotMappingDto(
            dpsId = "10000",
            nomisPrisonId = "WWI",
            nomisDayOfWeek = VisitTimeSlotMappingDto.NomisDayOfWeek.MONDAY,
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
  }

  private fun performMigration(body: PrisonerRestrictionMigrationFilter = PrisonerRestrictionMigrationFilter()): MigrationResult = webTestClient.post().uri("/migrate/visitslots")
    .headers(setAuthorisation(roles = listOf("PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(body)
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
