package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
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
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.dpsCourtSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiMockServer.Companion.referenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourtSchedulerSyncScheduleIntTest(
  @Autowired private val nomisApi: CourtSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: CourtSchedulerMappingApiMockServer,
  @Autowired private val sentencingMappingApi: CourtSentencingMappingApiMockServer,
) : SqsIntegrationTestBase() {

  private val dpsApi = dpsCourtSchedulerServer

  override fun resetTelemetryClient() {}

  private fun setUpTestClass() {
    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    CourtSchedulerDpsApiExtension.resetAndDisableResetBeforeEach()
    CourtSentencingDpsApiExtension.resetAndDisableResetBeforeEach()

    reset(telemetryClient)
  }

  @Nested
  @DisplayName("COURT_EVENTS-INSERTED")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class CourtScheduleCreated {

    private val yesterday: LocalDateTime = LocalDateTime.now().minusDays(1)

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()
      private val dpsSentencingCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, status = NOT_FOUND)
        nomisApi.stubGetCourtScheduleOut("A1234BC", 123L)
        sentencingMappingApi.stubGetCourtAppearanceByNomisId(123, "$dpsSentencingCourtAppearanceId")
        dpsApi.stubSyncCourtEvent("A1234BC", referenceId(dpsCourtAppearanceId))
        mappingApi.stubCreateCourtScheduleMapping()

        sendMessage(courtScheduleEvent("COURT_EVENTS-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/123")))
      }

      @Test
      fun `should get NOMIS court event`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/court/schedule/out/123")))
      }

      @Test
      fun `should NOT get court sentencing mapping`() {
        mappingApi.verify(
          count = 0,
          pattern = getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/123")),
        )
      }

      @Test
      fun `should create DPS scheduled movement`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEvent>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")),
        ).apply {
          assertThat(courtEvent.dpsId).isNull()
          assertThat(courtEvent.agyLocId).isEqualTo("LEEDMC")
          assertThat(courtEvent.eventDate).isEqualTo(yesterday.toLocalDate())
          assertThat(LocalDateTime.parse(courtEvent.startTime)).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
          assertThat(courtEvent.courtEventType).isEqualTo("CRT")
          assertThat(courtEvent.eventStatus).isEqualTo("SCH")
          assertThat(courtEvent.eventId).isEqualTo(123)
          assertThat(courtEvent.commentText).isEqualTo("court schedule comment")
          assertThat(courtEvent.externalReferenceUrn).isNull()
          assertThat(user.username).isEqualTo("USER")
          assertThat(user.activeCaseloadId).isEqualTo("MDI")
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisEventId", 123)
            .withRequestBodyJsonPath("dpsCourtAppearanceId", dpsCourtAppearanceId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo("$dpsCourtAppearanceId")
            assertThat(it["dpsSentencingCourtAppearanceId"]).isEqualTo("null")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathLinkedToCourtCase {
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()
      private val dpsSentencingCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, status = NOT_FOUND)
        nomisApi.stubGetCourtScheduleOut("A1234BC", 123L, courtCaseId = 1314L)
        sentencingMappingApi.stubGetCourtAppearanceByNomisId(123, "$dpsSentencingCourtAppearanceId")
        dpsApi.stubSyncCourtEvent("A1234BC", referenceId(dpsCourtAppearanceId))
        mappingApi.stubCreateCourtScheduleMapping()

        sendMessage(courtScheduleEvent("COURT_EVENTS-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get court sentencing mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/123")))
      }

      @Test
      fun `should create DPS scheduled movement`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEvent>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")),
        ).apply {
          assertThat(courtEvent.externalReferenceUrn).isEqualTo("$dpsSentencingCourtAppearanceId")
        }
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-inserted-success"),
          check {
            assertThat(it["dpsSentencingCourtAppearanceId"]).isEqualTo("$dpsSentencingCourtAppearanceId")
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

        sendMessage(courtScheduleEvent("COURT_EVENTS-INSERTED", auditModuleName = "DPS_SYNCHRONISATION_COURT_SCHEDULER"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT check mapping`() {
        mappingApi.verify(
          count = 0,
          pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/123")),
        )
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenWeReceiveCourtScheduleInEvent {

      @BeforeAll
      fun setUp(output: CapturedOutput) {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, status = NOT_FOUND)
        nomisApi.stubGetCourtScheduleOut(status = NOT_FOUND)

        sendMessage(courtScheduleEvent("COURT_EVENTS-INSERTED", directionCode = "IN"))
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenAlreadyCreated {

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L)

        sendMessage(courtScheduleEvent("COURT_EVENTS-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/123")))
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenSentencingCourtAppearanceNotCreatedYet {

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, status = NOT_FOUND)
        nomisApi.stubGetCourtScheduleOut("A1234BC", 123L, courtCaseId = 1314L)
        sentencingMappingApi.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)

        sendMessage(courtScheduleEvent("COURT_EVENTS-INSERTED"))
          .also {
            await untilAsserted {
              assertThat(awsSqsCourtMovementsOffenderEventsDlqClient.countAllMessagesOnQueue(courtMovementsQueueOffenderEventsDlqUrl).get())
                .isEqualTo(1)
            }
          }
      }

      @Test
      fun `should get court sentencing mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/123")))
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")),
        )
      }

      @Test
      fun `should create failure telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-inserted-awaiting-parent"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class DuplicateMapping {
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, status = NOT_FOUND)
        nomisApi.stubGetCourtScheduleOut("A1234BC", 123L)
        dpsApi.stubSyncCourtEvent("A1234BC", referenceId(dpsCourtAppearanceId))
        mappingApi.stubCreateCourtScheduleMappingConflict(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              existing = CourtScheduleMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345,
                nomisEventId = 123,
                dpsCourtAppearanceId = dpsCourtAppearanceId,
                mappingType = CourtScheduleMappingDto.MappingType.NOMIS_CREATED,
              ),
              duplicate = CourtScheduleMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345,
                nomisEventId = 999,
                dpsCourtAppearanceId = dpsCourtAppearanceId,
                mappingType = CourtScheduleMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        sendMessage(courtScheduleEvent("COURT_EVENTS-INSERTED"))
          .also { waitForAnyProcessingToComplete("court-scheduler-sync-schedule-inserted-duplicate") }
      }

      @Test
      fun `should create DPS court appearance a single time`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")))
      }

      @Test
      fun `should create mapping a single time`() {
        mappingApi.verify(postRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule")))
      }

      @Test
      fun `should create success telemetry telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-inserted-success"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `should create duplicate telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-inserted-duplicate"),
          check {
            assertThat(it["existingNomisEventId"]).isEqualTo("123")
            assertThat(it["duplicateNomisEventId"]).isEqualTo("999")
            assertThat(it["existingDpsCourtAppearanceId"]).isEqualTo("$dpsCourtAppearanceId")
            assertThat(it["duplicateDpsCourtAppearanceId"]).isEqualTo("$dpsCourtAppearanceId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class MappingRetry {
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()
      private val dpsSentencingCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, status = NOT_FOUND)
        nomisApi.stubGetCourtScheduleOut("A1234BC", 123L)
        sentencingMappingApi.stubGetCourtAppearanceByNomisId(123, "$dpsSentencingCourtAppearanceId")
        dpsApi.stubSyncCourtEvent("A1234BC", referenceId(dpsCourtAppearanceId))
        mappingApi.stubCreateCourtScheduleMappingFailureFollowedBySuccess()

        sendMessage(courtScheduleEvent("COURT_EVENTS-INSERTED"))
          .also { waitForAnyProcessingToComplete("court-scheduler-sync-schedule-mapping-retry-created") }
      }

      @Test
      fun `should create DPS scheduled movement`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")))
      }

      @Test
      fun `should create mapping on 2nd call`() {
        mappingApi.verify(
          count = 2,
          pattern = postRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule")),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-inserted-success"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `should publish mapping retry telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-mapping-retry-created"),
          check {
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo("$dpsCourtAppearanceId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @DisplayName("COURT_EVENTS-UPDATED")
  inner class CourtScheduleUpdated {

    private val yesterday: LocalDateTime = LocalDateTime.now().minusDays(1)

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, dpsCourtAppearanceId = dpsCourtAppearanceId)
        nomisApi.stubGetCourtScheduleOut("A1234BC", 123L)
        dpsApi.stubSyncCourtEvent("A1234BC", referenceId(dpsCourtAppearanceId))

        sendMessage(courtScheduleEvent("COURT_EVENTS-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/123")))
      }

      @Test
      fun `should get NOMIS court event`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/court/schedule/out/123")))
      }

      @Test
      fun `should NOT get court sentencing mapping`() {
        mappingApi.verify(
          count = 0,
          pattern = getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/123")),
        )
      }

      @Test
      fun `should update DPS court appearance`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEvent>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")),
        ).apply {
          assertThat(courtEvent.dpsId).isEqualTo(dpsCourtAppearanceId)
          assertThat(courtEvent.agyLocId).isEqualTo("LEEDMC")
          assertThat(courtEvent.eventDate).isEqualTo(yesterday.toLocalDate())
          assertThat(LocalDateTime.parse(courtEvent.startTime)).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
          assertThat(courtEvent.courtEventType).isEqualTo("CRT")
          assertThat(courtEvent.eventStatus).isEqualTo("SCH")
          assertThat(courtEvent.eventId).isEqualTo(123)
          assertThat(courtEvent.commentText).isEqualTo("court schedule comment")
          assertThat(courtEvent.externalReferenceUrn).isNull()
          assertThat(user.username).isEqualTo("USER")
          assertThat(user.activeCaseloadId).isEqualTo("MDI")
        }
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo("$dpsCourtAppearanceId")
            assertThat(it["dpsSentencingCourtAppearanceId"]).isEqualTo("null")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathLinkedToCourtCase {
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()
      private val dpsSentencingCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, dpsCourtAppearanceId = dpsCourtAppearanceId)
        nomisApi.stubGetCourtScheduleOut("A1234BC", 123L, courtCaseId = 1314L)
        sentencingMappingApi.stubGetCourtAppearanceByNomisId(123, "$dpsSentencingCourtAppearanceId")
        dpsApi.stubSyncCourtEvent("A1234BC", referenceId(dpsCourtAppearanceId))
        mappingApi.stubCreateCourtScheduleMapping()

        sendMessage(courtScheduleEvent("COURT_EVENTS-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get court sentencing mapping`() {
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/123")))
      }

      @Test
      fun `should update DPS court appearance`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEvent>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")),
        ).apply {
          assertThat(courtEvent.externalReferenceUrn).isEqualTo("$dpsSentencingCourtAppearanceId")
        }
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-updated-success"),
          check {
            assertThat(it["dpsSentencingCourtAppearanceId"]).isEqualTo("$dpsSentencingCourtAppearanceId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenWeReceiveCourtScheduleInEvent {
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp(output: CapturedOutput) {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, dpsCourtAppearanceId)
        nomisApi.stubGetCourtScheduleOut(NOT_FOUND)

        sendMessage(courtScheduleEvent("COURT_EVENTS-UPDATED", directionCode = "IN"))
      }

      @Test
      fun `should NOT update DPS court appearance`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenUpdatedInDps {

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        sendMessage(courtScheduleEvent("COURT_EVENTS-UPDATED", auditModuleName = "DPS_SYNCHRONISATION_COURT_SCHEDULER"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT check mapping`() {
        mappingApi.verify(
          count = 0,
          pattern = getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/123")),
        )
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")),
        )
      }

      @Test
      fun `should publish telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-updated-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDpsUpdateFails {
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, dpsCourtAppearanceId = dpsCourtAppearanceId)
        nomisApi.stubGetCourtScheduleOut("A1234BC", 123L)
        dpsApi.stubSyncCourtEventError("A1234BC", status = 500)

        sendMessage(courtScheduleEvent("COURT_EVENTS-UPDATED"))
          .also { waitForAnyProcessingToComplete("court-scheduler-sync-schedule-updated-error") }
      }

      @Test
      fun `should attempt to update DPS court appearance`() {
        CourtSchedulerDpsApiMockServer.getRequestBody<SyncCourtEvent>(
          putRequestedFor(urlPathEqualTo("/sync/court-appearances/A1234BC")),
        ).apply {
          assertThat(courtEvent.dpsId).isEqualTo(dpsCourtAppearanceId)
        }
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-updated-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo("$dpsCourtAppearanceId")
            assertThat(it["dpsSentencingCourtAppearanceId"]).isEqualTo("null")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @DisplayName("COURT_EVENTS-DELETED")
  inner class CourtScheduleDeleted {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, dpsCourtAppearanceId = dpsCourtAppearanceId)
        mappingApi.stubDeleteCourtScheduleMapping(nomisEventId = 123L)
        dpsApi.stubDeleteCourtEvent(dpsCourtAppearanceId)

        sendMessage(courtScheduleEvent("COURT_EVENTS-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/123")))
      }

      @Test
      fun `should delete mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/123")))
      }

      @Test
      fun `should delete DPS court appearance`() {
        dpsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/court-appearances/$dpsCourtAppearanceId")))
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo("$dpsCourtAppearanceId")
            assertThat(it["dpsSentencingCourtAppearanceId"]).isEqualTo(null)
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenMappingDoesNotExist {
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, NOT_FOUND)

        sendMessage(courtScheduleEvent("COURT_EVENTS-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT delete mapping`() {
        mappingApi.verify(
          0,
          deleteRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/123")),
        )
      }

      @Test
      fun `should NOT delete DPS court appearance`() {
        dpsApi.verify(
          0,
          deleteRequestedFor(urlPathEqualTo("/sync/court-appearances/$dpsCourtAppearanceId")),
        )
      }

      @Test
      fun `should publish ignored telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-deleted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
// TODO add this back in when direction is added to event
//            assertThat(it["directionCode"]).isEqualTo("OUT")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDpsDeleteFails {
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetCourtScheduleMapping(nomisEventId = 123L, dpsCourtAppearanceId = dpsCourtAppearanceId)
        dpsApi.stubDeleteCourtEventError(dpsCourtAppearanceId, 500)

        sendMessage(courtScheduleEvent("COURT_EVENTS-DELETED"))
          .also { waitForAnyProcessingToComplete("court-scheduler-sync-schedule-deleted-error") }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/123")))
      }

      @Test
      fun `should NOT delete mapping`() {
        mappingApi.verify(
          0,
          deleteRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/123")),
        )
      }

      @Test
      fun `should try to delete DPS court appearance`() {
        dpsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/court-appearances/$dpsCourtAppearanceId")))
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-sync-schedule-deleted-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo("$dpsCourtAppearanceId")
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

  // TODO still waiting for direction to be added to the message
  private fun courtScheduleEvent(
    eventType: String,
    auditModuleName: String = "OCDCCASE",
    nomisEventType: String = eventType.replace("COURT_EVENTS", "COURT_EVENT"),
    directionCode: String = "OUT",
    eventId: Long = 123,
  ) = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "57126174-e2d7-518f-914e-0056a63363b0",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
         "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2026-05-05T09:39:57\",\"nomisEventType\":\"$nomisEventType\",\"bookingId\":12345,\"offenderIdDisplay\":\"A1234BC\",\"auditModuleName\":\"$auditModuleName\",\"eventId\":$eventId,\"caseId\":101112,\"isBreachHearing\":false,\"directionCode\":\"$directionCode\"}",
         "Timestamp" : "2025-09-02T09:19:03.998Z",
         "SignatureVersion" : "1",
         "Signature" : "eePe/HtUdMyeFriH6GJe4FAJjYhQFjohJOu0+t8qULvpaw+qsGBfolKYa83fARpGDZJf9ceKd6kYGwF+OVeNViXluqPeUyoWbJ/lOjCs1tvlUuceCLy/7+eGGxkNASKJ1sWdwhO5J5I8WKUq5vfyYgL/Mygae6U71Bc0H9I2uVkw7tUYg0ZQBMSkA8HpuLLAN06qR5ahJnNDDxxoV07KY6E2dy8TheEo2Dhxq8hicl272LxWKMifM9VfR+D1i1eZNXDGsvvHmMCjumpxxYAJmrU+aqUzAU2KnhoZJTfeZT+RV+ZazjPLqX52zwA47ZFcqzCBnmrU6XwuHT4gKJcj1Q==",
         "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-6209c161c6221fdf56ec1eb5c821d112.pem",
         "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:3b68e1dd-c229-490f-bff9-05bd53595ddc",
         "MessageAttributes" : {
           "publishedAt" : {"Type":"String","Value":"2025-09-02T09:19:03.976312166+01:00"},
           "traceparent" : {"Type":"String","Value":"00-a0103c496069d331bd417cac78f4085c-0158c9f6485e8841-01"},
           "eventType" : {"Type":"String","Value":"$eventType"}
         }
       }
    """.trimMargin()
}
