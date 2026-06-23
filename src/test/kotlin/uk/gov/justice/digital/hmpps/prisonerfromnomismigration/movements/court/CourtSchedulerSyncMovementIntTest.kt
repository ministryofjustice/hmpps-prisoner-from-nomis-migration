package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEventMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.dpsCourtSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiMockServer.Companion.referenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourtSchedulerSyncMovementIntTest(
  @Autowired private val nomisApi: CourtSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: CourtSchedulerMappingApiMockServer,
) : CourtSchedulerIntegrationTestBase() {

  private val dpsApi = dpsCourtSchedulerServer

  private val yesterday = LocalDateTime.now().minusDays(1)

  override fun resetTelemetryClient() {}

  private fun setUpTestClass() {
    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    CourtSchedulerDpsApiExtension.resetAndDisableResetBeforeEach()

    reset(telemetryClient)
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (OUT, inserted)")
  inner class CourtMovementOutCreated {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathNoSchedule {
      private val dpsCourtMovementId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetCourtMovementOut("A1234BC", 12345L, 3)
        dpsApi.stubSyncCourtMovement("A1234BC", referenceId(dpsCourtMovementId))
        mappingApi.stubCreateCourtMovementMapping()

        sendMessage(courtMovementEvent(direction = "OUT", inserted = true))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get court movement mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should get NOMIS court movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/court/movement/out/12345/3")))
      }

      @Test
      fun `should create DPS court appearance movement`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEventMovement>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        ).apply {
          assertThat(movement.dpsId).isNull()
          assertThat(movement.dpsCourtAppearanceScheduleId).isNull()
          assertThat(movement.offenderBookId).isEqualTo(12345)
          assertThat(movement.movementSeq).isEqualTo(3)
          assertThat(movement.occurredAt).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
          assertThat(movement.movementReasonCode).isEqualTo("CRT")
          assertThat(movement.directionCode).isEqualTo("OUT")
          assertThat(movement.fromAgencyId).isEqualTo("BXI")
          assertThat(movement.toAgencyId).isEqualTo("LEEDMC")
          assertThat(user.username).isEqualTo("SYS")
          assertThat(user.activeCaseloadId).isEqualTo("MDI")
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("nomisBookingId", "12345")
            .withRequestBodyJsonPath("nomisMovementSeq", "3")
            .withRequestBodyJsonPath("dpsCourtMovementId", dpsCourtMovementId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisScheduledEventId"]).isNull()
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
            assertThat(it["directionCode"]).isEqualTo("OUT")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathWithSchedule {
      private val dpsCourtMovementId: UUID = UUID.randomUUID()
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetCourtMovementOut(
          offenderNo = "A1234BC",
          bookingId = 12345L,
          movementSeq = 3,
          courtScheduleOutId = 567L,
        )
        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 567L, dpsCourtAppearanceId = dpsCourtAppearanceId)
        dpsApi.stubSyncCourtMovement("A1234BC", referenceId(dpsCourtMovementId))
        mappingApi.stubCreateCourtMovementMapping()

        sendMessage(courtMovementEvent(direction = "OUT", inserted = true))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get court schedule mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/567")))
      }

      @Test
      fun `should create DPS court appearance movement with DPS court appearance ID`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEventMovement>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        ).apply {
          assertThat(movement.dpsId).isNull()
          assertThat(movement.dpsCourtAppearanceScheduleId).isEqualTo(dpsCourtAppearanceId)
          assertThat(movement.offenderBookId).isEqualTo(12345)
        }
      }

      @Test
      fun `should publish success telemetry with DPS court appearance ID`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-inserted-success"),
          check {
            assertThat(it["nomisEventId"]).isEqualTo("567")
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo("$dpsCourtAppearanceId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class MovementWithMissingSchedule {

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetCourtMovementOut(
          offenderNo = "A1234BC",
          bookingId = 12345L,
          movementSeq = 3,
          courtScheduleOutId = 567L,
        )
        // There is no mapping for the court schedule OUT so we should fail to sync the movement to DPS
        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 567L, NOT_FOUND)

        sendMessage(courtMovementEvent(direction = "OUT", inserted = true))
          .also { waitForAnyProcessingToComplete("court-scheduler-sync-movement-inserted-awaiting-parent") }
      }

      @Test
      fun `should try to get court schedule mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/567")))
      }

      @Test
      fun `should NOT create DPS court movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        )
      }

      @Test
      fun `should publish failure telemetry with DPS court appearance ID`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-inserted-awaiting-parent"),
          check {
            assertThat(it["nomisEventId"]).isEqualTo("567")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenCreatedInDps {

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        sendMessage(courtMovementEvent(direction = "OUT", inserted = true, auditModuleName = COURT_SCHEDULER_SYNC_AUDIT_MODULE))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS court appearance movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          0,
          postRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement")),
        )
      }

      @Test
      fun `should publish ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-inserted-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["directionCode"]).isEqualTo("OUT")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenAlreadyCreated {
      private val dpsCourtMovementId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, dpsCourtMovementId = dpsCourtMovementId)

        sendMessage(courtMovementEvent(direction = "OUT", inserted = true))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get court movement mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should NOT create DPS court appearance movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          0,
          postRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement")),
        )
      }

      @Test
      fun `should publish ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDuplicateMapping {
      private val dpsCourtMovementId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetCourtMovementOut("A1234BC", 12345L, 3)
        dpsApi.stubSyncCourtMovement("A1234BC", referenceId(dpsCourtMovementId))
        mappingApi.stubCreateCourtMovementMappingConflict(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              existing = CourtMovementMappingDto(
                prisonerNumber = "A1234BC",
                nomisBookingId = 12345,
                nomisMovementSeq = 3,
                dpsCourtMovementId = dpsCourtMovementId,
                mappingType = CourtMovementMappingDto.MappingType.NOMIS_CREATED,
              ),
              duplicate = CourtMovementMappingDto(
                prisonerNumber = "A1234BC",
                nomisBookingId = 12345,
                nomisMovementSeq = 999,
                dpsCourtMovementId = dpsCourtMovementId,
                mappingType = CourtMovementMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        sendMessage(courtMovementEvent(direction = "OUT", inserted = true))
          .also { waitForAnyProcessingToComplete("court-scheduler-sync-movement-inserted-duplicate") }
      }

      @Test
      fun `should create DPS court appearance movement`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEventMovement>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        ).apply {
          assertThat(movement.offenderBookId).isEqualTo(12345)
          assertThat(movement.movementSeq).isEqualTo(3)
        }
      }

      @Test
      fun `should try to create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement")),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
          },
          isNull(),
        )
      }

      @Test
      fun `should publish duplicate telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-inserted-duplicate"),
          check {
            assertThat(it["existingOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["existingBookingId"]).isEqualTo("12345")
            assertThat(it["existingMovementSeq"]).isEqualTo("3")
            assertThat(it["existingDpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
            assertThat(it["duplicateOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["duplicateBookingId"]).isEqualTo("12345")
            assertThat(it["duplicateMovementSeq"]).isEqualTo("999")
            assertThat(it["duplicateDpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenMappingCreateFailsOnce {
      private val dpsCourtMovementId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetCourtMovementOut("A1234BC", 12345L, 3)
        dpsApi.stubSyncCourtMovement("A1234BC", referenceId(dpsCourtMovementId))
        mappingApi.stubCreateCourtMovementMappingFailureFollowedBySuccess()

        sendMessage(courtMovementEvent(direction = "OUT", inserted = true))
          .also { waitForAnyProcessingToComplete("court-scheduler-sync-movement-mapping-retry-created") }
      }

      @Test
      fun `should create DPS court appearance movement`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEventMovement>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        ).apply {
          assertThat(movement.offenderBookId).isEqualTo(12345)
          assertThat(movement.movementSeq).isEqualTo(3)
        }
      }

      @Test
      fun `should create mapping on 2nd call`() {
        mappingApi.verify(
          count = 2,
          postRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("nomisBookingId", "12345")
            .withRequestBodyJsonPath("nomisMovementSeq", "3")
            .withRequestBodyJsonPath("dpsCourtMovementId", dpsCourtMovementId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
          },
          isNull(),
        )
      }

      @Test
      fun `should publish mapping created telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-mapping-retry-created"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
          },
          isNull(),
        )
      }
    }
  }

  /*
   * Note that the edge cases are covered by CourtMovementOutInserted which largely follows the same execution path
   */
  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (OUT, updated)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class CourtMovementOutUpdated {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathNoSchedule {
      private val dpsCourtMovementId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, dpsCourtMovementId = dpsCourtMovementId)
        nomisApi.stubGetCourtMovementOut("A1234BC", 12345L, 3)
        dpsApi.stubSyncCourtMovement("A1234BC", referenceId(dpsCourtMovementId))

        sendMessage(courtMovementEvent(direction = "OUT", inserted = false))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get court movement mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should get NOMIS court movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/court/movement/out/12345/3")))
      }

      @Test
      fun `should update DPS court appearance movement`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEventMovement>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        ).apply {
          assertThat(movement.dpsId).isEqualTo(dpsCourtMovementId)
          assertThat(movement.dpsCourtAppearanceScheduleId).isNull()
          assertThat(movement.offenderBookId).isEqualTo(12345)
          assertThat(movement.movementSeq).isEqualTo(3)
          assertThat(movement.occurredAt).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
          assertThat(movement.movementReasonCode).isEqualTo("CRT")
          assertThat(movement.directionCode).isEqualTo("OUT")
          assertThat(movement.fromAgencyId).isEqualTo("BXI")
          assertThat(movement.toAgencyId).isEqualTo("LEEDMC")
          assertThat(user.username).isEqualTo("SYS")
          assertThat(user.activeCaseloadId).isEqualTo("MDI")
        }
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisScheduledEventId"]).isNull()
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
            assertThat(it["directionCode"]).isEqualTo("OUT")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathWithSchedule {
      private val dpsCourtMovementId: UUID = UUID.randomUUID()
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, dpsCourtMovementId = dpsCourtMovementId)
        nomisApi.stubGetCourtMovementOut("A1234BC", 12345L, 3, courtScheduleOutId = 567L)
        mappingApi.stubGetCourtScheduleMapping(567L, dpsCourtAppearanceId)
        dpsApi.stubSyncCourtMovement("A1234BC", referenceId(dpsCourtMovementId))

        sendMessage(courtMovementEvent(direction = "OUT", inserted = false))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get court schedule mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/567")))
      }

      @Test
      fun `should update DPS court appearance movement`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEventMovement>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        ).apply {
          assertThat(movement.dpsId).isEqualTo(dpsCourtMovementId)
          assertThat(movement.dpsCourtAppearanceScheduleId).isEqualTo(dpsCourtAppearanceId)
        }
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-updated-success"),
          check {
            assertThat(it["nomisEventId"]).isEqualTo("567")
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo("$dpsCourtAppearanceId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ScheduleDoesNotExist {
      private val dpsCourtMovementId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, dpsCourtMovementId = dpsCourtMovementId)
        nomisApi.stubGetCourtMovementOut("A1234BC", 12345L, 3, courtScheduleOutId = 567L)
        mappingApi.stubGetCourtScheduleMapping(567L, NOT_FOUND)

        sendMessage(courtMovementEvent(direction = "OUT", inserted = false))
          .also { waitForAnyProcessingToComplete("court-scheduler-sync-movement-updated-awaiting-parent") }
      }

      @Test
      fun `should get court schedule mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/567")))
      }

      @Test
      fun `should NOT update DPS court appearance movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        )
      }

      @Test
      fun `should publish failure telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-updated-awaiting-parent"),
          check {
            assertThat(it["nomisEventId"]).isEqualTo("567")
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (OUT, deleted)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class CourtMovementOutDeleted {
    private val dpsCourtMovementId = UUID.randomUUID()

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(12345, 3, dpsCourtMovementId)
        mappingApi.stubDeleteCourtMovementMapping(12345, 3)
        dpsApi.stubDeleteCourtMovement(dpsCourtMovementId)

        sendMessage(courtMovementEvent(deleted = true, direction = "OUT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should delete mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should delete DPS external movement`() {
        dpsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/$dpsCourtMovementId")))
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenMappingDoesNotExist {
      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(12345, 3, NOT_FOUND)

        sendMessage(courtMovementEvent(deleted = true, direction = "OUT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should NOT delete mapping`() {
        mappingApi.verify(
          0,
          deleteRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")),
        )
      }

      @Test
      fun `should NOT delete DPS external movement`() {
        dpsApi.verify(
          0,
          deleteRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/$dpsCourtMovementId")),
        )
      }

      @Test
      fun `should create ignored telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-deleted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["directionCode"]).isEqualTo("OUT")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDpsDeleteFails {
      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(12345, 3, dpsCourtMovementId)
        mappingApi.stubDeleteCourtMovementMapping(12345, 3)
        dpsApi.stubDeleteCourtMovementError(dpsCourtMovementId, 400)

        sendMessage(courtMovementEvent(deleted = true, direction = "OUT"))
          .also { waitForAnyProcessingToComplete("court-scheduler-sync-movement-deleted-error") }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should try to delete DPS external movement`() {
        dpsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/$dpsCourtMovementId")))
      }

      @Test
      fun `should NOT delete mapping`() {
        mappingApi.verify(
          0,
          deleteRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")),
        )
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-deleted-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
          },
          isNull(),
        )
      }
    }
  }

  /*
   * Note that movement IN mainly has the same execution path as a movement OUT so we don't need to test all edge cases
   */
  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (IN, inserted)")
  inner class CourtMovementInCreated {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathNoSchedule {
      private val dpsCourtMovementId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetCourtMovementIn("A1234BC", 12345L, 3)
        dpsApi.stubSyncCourtMovement("A1234BC", referenceId(dpsCourtMovementId))
        mappingApi.stubCreateCourtMovementMapping()

        sendMessage(courtMovementEvent(direction = "IN", inserted = true))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get court movement mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should get NOMIS court movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/court/movement/in/12345/3")))
      }

      @Test
      fun `should create DPS court appearance movement`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEventMovement>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        ).apply {
          assertThat(movement.dpsId).isNull()
          assertThat(movement.dpsCourtAppearanceScheduleId).isNull()
          assertThat(movement.offenderBookId).isEqualTo(12345)
          assertThat(movement.movementSeq).isEqualTo(3)
          assertThat(movement.occurredAt).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
          assertThat(movement.movementReasonCode).isEqualTo("CRT")
          assertThat(movement.directionCode).isEqualTo("IN")
          assertThat(movement.fromAgencyId).isEqualTo("LEEDMC")
          assertThat(movement.toAgencyId).isEqualTo("BXI")
          assertThat(user.username).isEqualTo("SYS")
          assertThat(user.activeCaseloadId).isEqualTo("MDI")
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("nomisBookingId", "12345")
            .withRequestBodyJsonPath("nomisMovementSeq", "3")
            .withRequestBodyJsonPath("dpsCourtMovementId", dpsCourtMovementId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisScheduledEventId"]).isNull()
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
            assertThat(it["directionCode"]).isEqualTo("IN")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathWithSchedule {
      private val dpsCourtMovementId: UUID = UUID.randomUUID()
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetCourtMovementIn(
          offenderNo = "A1234BC",
          bookingId = 12345L,
          movementSeq = 3,
          courtScheduleOutId = 567L,
        )
        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 567L, dpsCourtAppearanceId = dpsCourtAppearanceId)
        dpsApi.stubSyncCourtMovement("A1234BC", referenceId(dpsCourtMovementId))
        mappingApi.stubCreateCourtMovementMapping()

        sendMessage(courtMovementEvent(direction = "IN", inserted = true))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get court schedule mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/567")))
      }

      @Test
      fun `should create DPS court appearance movement with court appearance ID`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEventMovement>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        ).apply {
          assertThat(movement.dpsId).isNull()
          assertThat(movement.dpsCourtAppearanceScheduleId).isEqualTo(dpsCourtAppearanceId)
        }
      }

      @Test
      fun `should publish success telemetry with court appearance ID`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-inserted-success"),
          check {
            assertThat(it["nomisEventId"]).isEqualTo("567")
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo("$dpsCourtAppearanceId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class MovementWithMissingSchedule {

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetCourtMovementIn(
          offenderNo = "A1234BC",
          bookingId = 12345L,
          movementSeq = 3,
          courtScheduleOutId = 567L,
        )
        // There is no mapping for the court schedule OUT so we should fail to sync the movement to DPS
        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 567L, status = NOT_FOUND)

        sendMessage(courtMovementEvent(direction = "IN", inserted = true))
          .also { waitForAnyProcessingToComplete("court-scheduler-sync-movement-inserted-awaiting-parent") }
      }

      @Test
      fun `should try to get court schedule mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/567")))
      }

      @Test
      fun `should NOT create DPS court movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        )
      }

      @Test
      fun `should publish success telemetry with court appearance ID`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-inserted-awaiting-parent"),
          check {
            assertThat(it["nomisEventId"]).isEqualTo("567")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (IN, updated)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class CourtMovementInUpdated {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathNoSchedule {
      private val dpsCourtMovementId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, dpsCourtMovementId = dpsCourtMovementId)
        nomisApi.stubGetCourtMovementIn("A1234BC", 12345L, 3)
        dpsApi.stubSyncCourtMovement("A1234BC", referenceId(dpsCourtMovementId))

        sendMessage(courtMovementEvent(direction = "IN", inserted = false))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get court movement mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should get NOMIS court movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/court/movement/in/12345/3")))
      }

      @Test
      fun `should update DPS court appearance movement`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEventMovement>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        ).apply {
          assertThat(movement.dpsId).isEqualTo(dpsCourtMovementId)
          assertThat(movement.dpsCourtAppearanceScheduleId).isNull()
          assertThat(movement.offenderBookId).isEqualTo(12345)
          assertThat(movement.movementSeq).isEqualTo(3)
          assertThat(movement.occurredAt).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
          assertThat(movement.movementReasonCode).isEqualTo("CRT")
          assertThat(movement.directionCode).isEqualTo("IN")
          assertThat(movement.fromAgencyId).isEqualTo("LEEDMC")
          assertThat(movement.toAgencyId).isEqualTo("BXI")
          assertThat(user.username).isEqualTo("SYS")
          assertThat(user.activeCaseloadId).isEqualTo("MDI")
        }
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisScheduledEventId"]).isNull()
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
            assertThat(it["directionCode"]).isEqualTo("IN")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathWithSchedule {
      private val dpsCourtMovementId: UUID = UUID.randomUUID()
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(nomisBookingId = 12345L, nomisMovementSeq = 3, dpsCourtMovementId = dpsCourtMovementId)
        nomisApi.stubGetCourtMovementIn("A1234BC", 12345L, 3, courtScheduleOutId = 567L)
        mappingApi.stubGetCourtScheduleMapping(567L, dpsCourtAppearanceId)
        dpsApi.stubSyncCourtMovement("A1234BC", referenceId(dpsCourtMovementId))

        sendMessage(courtMovementEvent(direction = "IN", inserted = false))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get court schedule mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/567")))
      }

      @Test
      fun `should update DPS court appearance movement`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEventMovement>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/A1234BC")),
        ).apply {
          assertThat(movement.dpsId).isEqualTo(dpsCourtMovementId)
          assertThat(movement.dpsCourtAppearanceScheduleId).isEqualTo(dpsCourtAppearanceId)
          assertThat(movement.directionCode).isEqualTo("IN")
        }
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-updated-success"),
          check {
            assertThat(it["nomisEventId"]).isEqualTo("567")
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo("$dpsCourtAppearanceId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (IN, deleted)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class CourtMovementInDeleted {
    private val dpsCourtMovementId = UUID.randomUUID()

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtMovementMapping(12345, 3, dpsCourtMovementId)
        mappingApi.stubDeleteCourtMovementMapping(12345, 3)
        dpsApi.stubDeleteCourtMovement(dpsCourtMovementId)

        sendMessage(courtMovementEvent(deleted = true, direction = "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should delete mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/court-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should delete DPS external movement`() {
        dpsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/court-appearance-movements/$dpsCourtMovementId")))
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-movement-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["directionCode"]).isEqualTo("IN")
            assertThat(it["dpsCourtMovementId"]).isEqualTo("$dpsCourtMovementId")
          },
          isNull(),
        )
      }
    }
  }

  private fun sendMessage(event: String) = awsSqsCourtMovementsOffenderEventsClient.sendMessage(
    courtMovementsQueueOffenderEventsUrl,
    event,
  )

  private fun courtMovementEvent(
    auditModuleName: String = "OCUCANTR",
    movementType: String = "CRT",
    direction: String = "OUT",
    inserted: Boolean = false,
    deleted: Boolean = false,
  ) = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "83354f3f-45cb-5e8e-9266-2e0fa1e91dcc",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-160f3055cc4e04c4105ee85f2ed1fccb",
         "Message" : "{\"eventType\":\"EXTERNAL_MOVEMENT-CHANGED\",\"eventDatetime\":\"2025-09-02T13:24:01\",\"bookingId\":12345,\"offenderIdDisplay\":\"A1234BC\",\"nomisEventType\":\"EXTERNAL_MOVEMENT-CHANGED\",\"movementSeq\":3,\"movementDateTime\":\"2025-09-02T13:23:00\",\"movementType\":\"$movementType\",\"movementReasonCode\":\"OPA\",\"directionCode\":\"$direction\",\"fromAgencyLocationId\":\"NWI\",\"recordInserted\":$inserted,\"recordDeleted\":$deleted,\"auditModuleName\":\"$auditModuleName\"}",
         "Timestamp" : "2025-09-02T12:24:02.004Z",
         "SignatureVersion" : "1",
         "Signature" : "HDyAhgG0o4XV4eJjuLODqeyBfZfsUxLcqVyiwQQIvegES5QnWmfgKwzb+D3az1QgiJBaknq/NIR+C/71O0AFFTSRN3RFOQyLrPZBeynGIyBNzGgeJjPGrZrSBqYegtJKJPDQEQLNepk2Jgqjiu3NgKT0gq5z5mU7G45wqkC81F3/DJUAHb98BmLbWK/cibnaHrvgXW493IbWPLXQENzJ9rDJKekz6sdY6+qHcOg57xdho/Xlb6VFo28/9qoVqA+A2MUBlHBRI1BSK0QVu8duri5DHjE0I2/UG7emlt9vZ6KtxyXz/ZmFVC/nY2OD0OgFJvP7DaAJbgMo/rbGe1JlYQ==",
         "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-6209c161c6221fdf56ec1eb5c821d112.pem",
         "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-160f3055cc4e04c4105ee85f2ed1fccb:217952f4-706e-4451-84d7-e349633331e0",
         "MessageAttributes" : {
           "code" : {"Type":"String","Value":"$movementType-$direction"},
           "publishedAt" : {"Type":"String","Value":"2025-09-02T13:24:02.000596721+01:00"},
           "traceparent" : {"Type":"String","Value":"00-b525e7a9b05de2c11a64ff93a0ef292b-f8f18e48747b11fd-01"},
           "eventType" : {"Type":"String","Value":"EXTERNAL_MOVEMENT-CHANGED"}
         }
        }
    """.trimMargin()
}
