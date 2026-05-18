package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncCourtEvents
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.MigrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.dpsCourtSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiMockServer.Companion.resyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapMigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourtSchedulerMigrationIntTest(
  @Autowired private val nomisApi: CourtSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: CourtSchedulerMappingApiMockServer,
  @Autowired private val sentencingMappingApi: CourtSentencingMappingApiMockServer,
) : MigrationTestBase() {

  private val dpsApi = dpsCourtSchedulerServer

  private val dpsCourtScheduleId = UUID.randomUUID()
  private val dpsScheduledMovementOutId = UUID.randomUUID()
  private val dpsScheduledMovementInId = UUID.randomUUID()
  private val dpsUnscheduledMovementOutId = UUID.randomUUID()
  private val dpsUnscheduledMovementInId = UUID.randomUUID()
  private val dpsSentencingCourtAppearanceId = UUID.randomUUID()
  private lateinit var migrationId: String

  @AfterAll
  fun tearDownTelemetryClient() = reset(telemetryClient)

  private fun stubMigrationDependencies(entities: Int = 2) {
    NomisApiExtension.nomisApi.stubGetPrisonerIds(totalElements = entities.toLong(), pageSize = 10, firstOffenderNo = "A0001KT")
    mappingApi.stubCreateCourtSchedulerPrisonerMappings()
    (1..entities)
      .map { index -> "A%04dKT".format(index) }
      .forEach { prisonerNumber ->
        mappingApi.stubGetCourtSchedulerPrisonerMappingIds(prisonerNumber, 12345L, 1, dpsCourtScheduleId, 3, dpsScheduledMovementOutId, 4, dpsScheduledMovementInId, 1, dpsUnscheduledMovementOutId, 2, dpsUnscheduledMovementInId)
        nomisApi.stubGetOffenderCourtMovements(prisonerNumber)
        sentencingMappingApi.stubGetAllCourtAppearanceByNomisIds(
          mappings = listOf(CourtAppearanceMappingDto(1, dpsSentencingCourtAppearanceId.toString(), "any", CourtAppearanceMappingDto.MappingType.MIGRATED)),
        )
        dpsApi.stubResyncPrisonerCourtAppearances(
          personIdentifier = prisonerNumber,
          response = resyncResponse(dpsCourtScheduleId, dpsScheduledMovementOutId, dpsScheduledMovementInId, dpsUnscheduledMovementOutId, dpsUnscheduledMovementInId),
        )
      }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MigrateEntity {
    @BeforeAll
    fun setUp() = runTest {
      setupMigrationTest()

      stubMigrationDependencies(entities = 1)
      migrationId = performMigration()
    }

    @Test
    fun `should check for existing mappings`() {
      mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court/A0001KT/ids")))
    }

    @Test
    fun `should get NOMIS court movement details`() {
      nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A0001KT/court")))
    }

    @Test
    fun `should get RaS court sentencing mappings`() {
      sentencingMappingApi.verify(
        postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-ids/get-list")),
      )
    }

    @Test
    fun `should call DPS resync API`() {
      dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")))
    }

    @Test
    fun `should populate DPS court appearance`() {
      CourtSchedulerDpsApiMockServer.getRequestBody<ResyncCourtEvents>(
        putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")),
      ).apply {
        with(courtEvents[0].courtEvent) {
          assertThat(dpsId).isEqualTo(dpsCourtScheduleId)
          assertThat(eventId).isEqualTo(1)
          assertThat(prisonCodeAtTimeOfScheduling).isEqualTo("BXI")
          assertThat(eventDate).isEqualTo(LocalDate.now().minusDays(1))
          assertThat(LocalDateTime.parse(startTime)).isCloseTo(LocalDateTime.now().minusDays(1), within(5, ChronoUnit.MINUTES))
          assertThat(courtEventType).isEqualTo("CRT")
          assertThat(eventStatus).isEqualTo("COMP")
          assertThat(commentText).isEqualTo("Some schedule comment")
          assertThat(externalReferenceUrn).isEqualTo("$EXTERNAL_REF_PREFIX$dpsSentencingCourtAppearanceId")
        }
        with(courtEvents[0].created) {
          assertThat(by).isEqualTo("USER")
          assertThat(at).isCloseTo(LocalDateTime.now().minusDays(1), within(5, ChronoUnit.MINUTES))
        }
        assertThat(courtEvents[0].modified).isNull()
      }
    }

    @Test
    fun `should populate DPS court movement`() {
      CourtSchedulerDpsApiMockServer.getRequestBody<ResyncCourtEvents>(
        putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")),
      ).apply {
        with(courtEvents[0].movements[0].movement) {
          assertThat(dpsId).isEqualTo(dpsScheduledMovementOutId)
          assertThat(offenderBookId).isEqualTo(12345)
          assertThat(movementSeq).isEqualTo(3)
          assertThat(movementDate).isEqualTo(LocalDate.now().minusDays(1))
          assertThat(LocalDateTime.parse(movementTime)).isCloseTo(LocalDateTime.now().minusDays(1), within(5, ChronoUnit.MINUTES))
          assertThat(movementReasonCode).isEqualTo("CRT")
          assertThat(fromAgencyId).isEqualTo("BXI")
          assertThat(toAgencyId).isEqualTo("LEEDMC")
          assertThat(commentText).isEqualTo("Some movement out comment")
        }
        with(courtEvents[0].movements[0].created) {
          assertThat(by).isEqualTo("USER")
          assertThat(at).isCloseTo(LocalDateTime.now().minusDays(1), within(5, ChronoUnit.MINUTES))
        }
        assertThat(courtEvents[0].movements[0].modified).isNull()
      }
    }

    @Test
    fun `should send all movements`() {
      CourtSchedulerDpsApiMockServer.getRequestBody<ResyncCourtEvents>(
        putRequestedFor(urlPathEqualTo("/resync/court-appearances/A0001KT")),
      ).apply {
        assertThat(courtEvents[0].movements.size).isEqualTo(2)
        assertThat(courtEvents[0].movements[0].movement.movementSeq).isEqualTo(3)
        assertThat(courtEvents[0].movements[1].movement.movementSeq).isEqualTo(4)
        assertThat(unscheduledMovements.size).isEqualTo(2)
        assertThat(unscheduledMovements[0].movement.movementSeq).isEqualTo(1)
        assertThat(unscheduledMovements[1].movement.movementSeq).isEqualTo(2)
      }
    }

    @Test
    fun `should update mappings`() {
      CourtSchedulerMappingApiMockServer.getRequestBody<CourtSchedulerPrisonerMappingsDto>(
        putRequestedFor(urlPathEqualTo("/mapping/court/migrate")),
      ).apply {
        assertThat(offenderNo).isEqualTo("A0001KT")
        with(bookings[0]) {
          assertThat(bookingId).isEqualTo(12345)
          with(courtSchedules[0]) {
            assertThat(nomisEventId).isEqualTo(1)
            assertThat(dpsCourtAppearanceId).isEqualTo(dpsCourtScheduleId)
            assertThat(movements[0].nomisMovementSeq).isEqualTo(3)
            assertThat(movements[0].dpsCourtMovementId).isEqualTo(dpsScheduledMovementOutId)
            assertThat(movements[1].nomisMovementSeq).isEqualTo(4)
            assertThat(movements[1].dpsCourtMovementId).isEqualTo(dpsScheduledMovementInId)
          }
          assertThat(unscheduledMovements[0].nomisMovementSeq).isEqualTo(1)
          assertThat(unscheduledMovements[0].dpsCourtMovementId).isEqualTo(dpsUnscheduledMovementOutId)
          assertThat(unscheduledMovements[1].nomisMovementSeq).isEqualTo(2)
          assertThat(unscheduledMovements[1].dpsCourtMovementId).isEqualTo(dpsUnscheduledMovementInId)
        }
      }
    }

    @Test
    fun `will publish telemetry`() {
      verify(telemetryClient).trackEvent(
        eq("court-movements-migration-entity-migrated"),
        check {
          assertThat(it["offenderNo"]).isEqualTo("A0001KT")
          assertThat(it["migrationId"]).isEqualTo(migrationId)
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class Security {
    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/migrate/court")
        .headers(setAuthorisation(roles = listOf()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(TapMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.post().uri("/migrate/court")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(TapMigrationFilter())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.post().uri("/migrate/court")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(TapMigrationFilter())
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  private fun performMigration(prisonerNumber: String? = null): String = webTestClient.post()
    .uri("/migrate/court")
    .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
    .contentType(MediaType.APPLICATION_JSON)
    .apply { prisonerNumber?.let { bodyValue("""{"prisonerNumber":"$prisonerNumber"}""") } ?: bodyValue("{}") }
    .exchange()
    .expectStatus().isAccepted
    .returnResult<MigrationResult>().responseBody.blockFirst()!!
    .migrationId
    .also {
      waitUntilCompleted()
    }

  private fun waitUntilCompleted() = await atMost Duration.ofSeconds(60) untilAsserted {
    verify(telemetryClient).trackEvent(
      eq("court-pmovements-migration-completed"),
      any(),
      isNull(),
    )
  }
}
