package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiExtension.Companion.dpsExtMovementsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsDpsApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.SyncWriteTapOccurrence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

class TapScheduleIntTest(
  @Autowired private val nomisApi: TapNomisApiMockServer,
  @Autowired private val mappingApi: ExternalMovementsMappingApiMockServer,
) : SqsIntegrationTestBase() {

  private val dpsApi = dpsExtMovementsServer

  private val now = LocalDateTime.now()
  private val today = now.toLocalDate()
  private val yesterday = now.minusDays(1)
  private val tomorrow = now.plusDays(1)

  @Nested
  @DisplayName("SCHEDULED_EXT_MOVE-INSERTED")
  inner class TapScheduleCreated {
    private val dpsAuthorisationId: UUID = UUID.randomUUID()
    private val dpsOccurrenceId: UUID = UUID.randomUUID()
    private val eventTime = now

    @Nested
    inner class HappyPathOutbound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsApplicationId = dpsAuthorisationId)
        nomisApi.stubGetTapScheduleOut(eventId = 45678, eventTime = eventTime)
        dpsApi.stubSyncTapOccurrence(authorisationId = dpsAuthorisationId, response = SyncResponse(dpsOccurrenceId))
        mappingApi.stubCreateScheduledMovementMapping()

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should check parent mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should get NOMIS scheduled movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/schedule/out/45678")))
      }

      @Test
      fun `should create DPS scheduled movement`() {
        ExternalMovementsDpsApiMockServer.getRequestBody<SyncWriteTapOccurrence>(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences")),
        ).apply {
          assertThat(id).isNull()
          assertThat(start).isCloseTo(now, within(1, ChronoUnit.MINUTES))
          assertThat(end).isCloseTo(tomorrow, within(1, ChronoUnit.MINUTES))
          assertThat(location.description).isEqualTo("some description")
          assertThat(location.address).isEqualTo("to full address")
          assertThat(location.postcode).isEqualTo("S1 1AB")
          assertThat(absenceTypeCode).isEqualTo("RDR")
          assertThat(absenceSubTypeCode).isEqualTo("RR")
          assertThat(absenceReasonCode).isEqualTo("C5")
          assertThat(transportCode).isEqualTo("VAN")
          assertThat(comments).isEqualTo("scheduled absence comment")
          assertThat(contactInformation).isEqualTo("Derek")
          assertThat(created.by).isEqualTo("USER")
          assertThat(updated).isNull()
          assertThat(isCancelled).isFalse
          assertThat(legacyId).isEqualTo(45678)
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisEventId", 45678)
            .withRequestBodyJsonPath("dpsOccurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("nomisAddressId", 321)
            .withRequestBodyJsonPath("nomisAddressOwnerClass", "OFF")
            .withRequestBodyJsonPath("dpsAddressText", "to full address")
            .withRequestBodyJsonPath("eventTime", containing("$today")),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenNotTapOutMovement {
      @BeforeEach
      fun setUp(output: CapturedOutput) {
        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED", nomisEventType = "TAP", direction = "IN"))
          .also { await untilCallTo { output.out } matches { it!!.contains("Ignoring") } }
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement")),
        )
      }
    }

    @Nested
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678)

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId)

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenParentApplicationNotCreatedYet {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)
        nomisApi.stubGetTapScheduleOut(eventId = 45678)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, NOT_FOUND)

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-scheduled-movement-inserted-awaiting-parent") }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should check parent mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/nomis-application-id/111")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-awaiting-parent"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["error"]).isEqualTo("Expected parent entity not found, retrying")
          },
          isNull(),
        )
      }

      // Note we only send to the DLQ to assert the message is rejected in tests - in real life we'll retry 3 times as per redrive policy (1 of which should succeed after the parent is created)
      @Test
      fun `should send message to DLQ`() {
        await untilAsserted {
          assertThat(awsSqsExternalMovementsOffenderEventsDlqClient.countAllMessagesOnQueue(externalMovementsQueueOffenderEventsDlqUrl).get())
            .isEqualTo(1)
        }
      }
    }

    @Nested
    inner class WhenDuplicateMapping {
      private val eventTime = now

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsAuthorisationId)
        nomisApi.stubGetTapScheduleOut(eventId = 45678)
        dpsApi.stubSyncTapOccurrence(authorisationId = dpsAuthorisationId, response = SyncResponse(dpsOccurrenceId))
        mappingApi.stubCreateScheduledMovementMappingConflict(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              existing = ScheduledMovementSyncMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345L,
                nomisEventId = 222L,
                dpsOccurrenceId = dpsOccurrenceId,
                mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                nomisAddressId = 321,
                nomisAddressOwnerClass = "OFF",
                dpsAddressText = "to full address",
                eventTime = "$eventTime",
              ),
              duplicate = ScheduledMovementSyncMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345L,
                nomisEventId = 45678L,
                dpsOccurrenceId = dpsOccurrenceId,
                mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                nomisAddressId = 321,
                nomisAddressOwnerClass = "OFF",
                dpsAddressText = "to full address",
                eventTime = "$eventTime",
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-scheduled-movement-inserted-duplicate") }
      }

      @Test
      fun `should create DPS scheduled movement only once`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences")))
      }

      @Test
      fun `should create mapping only once`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisEventId", 45678)
            .withRequestBodyJsonPath("dpsOccurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("nomisAddressId", 321)
            .withRequestBodyJsonPath("nomisAddressOwnerClass", "OFF")
            .withRequestBodyJsonPath("dpsAddressText", "to full address"),
        )
      }

      @Test
      fun `should create success telemetry and duplicate telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-duplicate"),
          check {
            assertThat(it["duplicateOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["existingOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["duplicateBookingId"]).isEqualTo("12345")
            assertThat(it["existingBookingId"]).isEqualTo("12345")
            assertThat(it["duplicateNomisEventId"]).isEqualTo("45678")
            assertThat(it["existingNomisEventId"]).isEqualTo("222")
            assertThat(it["duplicateDpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
            assertThat(it["existingDpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingCreateFailsOnce {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsApplicationId = dpsAuthorisationId)
        nomisApi.stubGetTapScheduleOut(eventId = 45678)
        dpsApi.stubSyncTapOccurrence(authorisationId = dpsAuthorisationId, response = SyncResponse(dpsOccurrenceId))
        mappingApi.stubCreateScheduledMovementMappingFailureFollowedBySuccess()

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-scheduled-movement-mapping-retry-created") }
      }

      @Test
      fun `should create DPS scheduled movement`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences")))
      }

      @Test
      fun `should create mapping on 2nd call`() {
        mappingApi.verify(
          count = 2,
          postRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisEventId", 45678)
            .withRequestBodyJsonPath("dpsOccurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
          },
          isNull(),
        )
      }

      @Test
      fun `should publish mapping created telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-mapping-retry-created"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("SCHEDULED_EXT_MOVE-UPDATED")
  inner class TapScheduleUpdated {
    private val dpsAuthorisationId: UUID = UUID.randomUUID()
    private val dpsOccurrenceId: UUID = UUID.randomUUID()
    private val eventTime = yesterday

    @Nested
    inner class HappyPathOutbound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId, eventTime)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsAuthorisationId)
        nomisApi.stubGetTapScheduleOut(eventId = 45678, eventTime = eventTime)
        dpsApi.stubSyncTapOccurrence(authorisationId = dpsAuthorisationId, response = SyncResponse(dpsOccurrenceId))
        mappingApi.stubUpdateScheduledMovementMapping()

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should get NOMIS scheduled movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/schedule/out/45678")))
      }

      @Test
      fun `should update DPS scheduled movement`() {
        ExternalMovementsDpsApiMockServer.getRequestBody<SyncWriteTapOccurrence>(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences")),
        ).apply {
          assertThat(id).isEqualTo(dpsOccurrenceId)
          assertThat(start).isCloseTo(yesterday, within(1, ChronoUnit.MINUTES))
          assertThat(end).isCloseTo(tomorrow, within(1, ChronoUnit.MINUTES))
          assertThat(location.description).isEqualTo("some description")
          assertThat(location.address).isEqualTo("to full address")
          assertThat(location.postcode).isEqualTo("S1 1AB")
          assertThat(absenceTypeCode).isEqualTo("RDR")
          assertThat(absenceSubTypeCode).isEqualTo("RR")
          assertThat(absenceReasonCode).isEqualTo("C5")
          assertThat(transportCode).isEqualTo("VAN")
          assertThat(comments).isEqualTo("scheduled absence comment")
          assertThat(created.by).isEqualTo("USER")
          assertThat(updated).isNull()
          assertThat(isCancelled).isFalse
          assertThat(legacyId).isEqualTo(45678)
        }
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingHasDpsUprn {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId, eventTime, dpsUprn = 987L)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsAuthorisationId)
        nomisApi.stubGetTapScheduleOut(eventId = 45678, eventTime = eventTime)
        dpsApi.stubSyncTapOccurrence(authorisationId = dpsAuthorisationId, response = SyncResponse(dpsOccurrenceId))
        mappingApi.stubUpdateScheduledMovementMapping()

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should send UPRN to DPS`() {
        ExternalMovementsDpsApiMockServer.getRequestBody<SyncWriteTapOccurrence>(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences")),
        ).apply {
          assertThat(id).isEqualTo(dpsOccurrenceId)
          assertThat(location.uprn).isEqualTo(987L)
          assertThat(location.description).isEqualTo("some description")
          assertThat(location.address).isEqualTo("to full address")
          assertThat(location.postcode).isEqualTo("S1 1AB")
        }
      }
    }

    @Nested
    inner class WhenMappingDetailsChange {
      val newEventTime = tomorrow

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId, eventTime)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsAuthorisationId)
        nomisApi.stubGetTapScheduleOut(eventId = 45678, eventTime = newEventTime)
        mappingApi.stubUpdateScheduledMovementMapping()
        dpsApi.stubSyncTapOccurrence(authorisationId = dpsAuthorisationId, response = SyncResponse(dpsOccurrenceId))

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should get NOMIS scheduled movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/schedule/out/45678")))
      }

      @Test
      fun `should update DPS scheduled movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences"))
            .withRequestBodyJsonPath("id", "$dpsOccurrenceId")
            .withRequestBodyJsonPath("location.address", "to full address"),
        )
      }

      @Test
      fun `should update mapping`() {
        mappingApi.verify(
          putRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement"))
            .withRequestBodyJsonPath("dpsOccurrenceId", "$dpsOccurrenceId")
            .withRequestBodyJsonPath("nomisEventId", "45678")
            .withRequestBodyJsonPath("eventTime", "$newEventTime"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenAddressChanges {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId, eventTime)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsAuthorisationId)
        nomisApi.stubGetTapScheduleOut(eventId = 45678, toAddress = "new address", toAddressId = 654)
        mappingApi.stubUpdateScheduledMovementMapping()
        dpsApi.stubSyncTapOccurrence(authorisationId = dpsAuthorisationId, response = SyncResponse(dpsOccurrenceId))

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should update DPS scheduled movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences"))
            .withRequestBodyJsonPath("id", "$dpsOccurrenceId")
            .withRequestBodyJsonPath("location.address", "new address"),
        )
      }

      @Test
      fun `should update mapping`() {
        mappingApi.verify(
          putRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement"))
            .withRequestBodyJsonPath("dpsAddressText", "new address")
            .withRequestBodyJsonPath("nomisAddressId", 654)
            .withRequestBodyJsonPath("dpsUprn", absent()),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-updated-success"),
          check {
            assertThat(it["nomisAddressId"]).isEqualTo("654")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingUpdateFailsOnce {
      val newEventTime = tomorrow

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId, eventTime)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsAuthorisationId)
        nomisApi.stubGetTapScheduleOut(eventId = 45678, eventTime = newEventTime)
        mappingApi.stubUpdateScheduledMovementMappingFailureFollowedBySuccess()
        dpsApi.stubSyncTapOccurrence(authorisationId = dpsAuthorisationId, response = SyncResponse(dpsOccurrenceId))

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-UPDATED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-scheduled-movement-mapping-retry-updated") }
      }

      @Test
      fun `should update DPS scheduled movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences"))
            .withRequestBodyJsonPath("id", "$dpsOccurrenceId")
            .withRequestBodyJsonPath("location.address", "to full address"),
        )
      }

      @Test
      fun `should update mapping on 2nd call`() {
        mappingApi.verify(
          count = 2,
          putRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement"))
            .withRequestBodyJsonPath("nomisEventId", 45678)
            .withRequestBodyJsonPath("dpsOccurrenceId", dpsOccurrenceId)
            .withRequestBodyJsonPath("eventTime", "$newEventTime"),
        )
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["directionCode"]).isEqualTo("OUT")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
            assertThat(it["nomisAddressId"]).isEqualTo("321")
            assertThat(it["nomisAddressOwnerClass"]).isEqualTo("OFF")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-UPDATED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS scheduled movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences")),
        )
      }

      @Test
      fun `should NOT get mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-updated-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["directionCode"]).isEqualTo("OUT")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDpsUpdateFails {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId = dpsOccurrenceId)
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(111, dpsAuthorisationId)
        nomisApi.stubGetTapScheduleOut(eventId = 45678)
        dpsApi.stubSyncTapOccurrenceError(authorisationId = dpsAuthorisationId, status = 500)

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-UPDATED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-scheduled-movement-updated-error") }
      }

      @Test
      fun `should try to update DPS scheduled movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/temporary-absence-authorisations/$dpsAuthorisationId/occurrences"))
            .withRequestBodyJsonPath("id", "$dpsOccurrenceId"),
        )
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-updated-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["nomisApplicationId"]).isEqualTo("111")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("SCHEDULED_EXT_MOVE-DELETED")
  inner class TapScheduleDeleted {
    private val dpsOccurrenceId = UUID.randomUUID()

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId)
        mappingApi.stubDeleteScheduledMovementMapping(nomisEventId = 45678)
        dpsApi.stubDeleteTapOccurrence(dpsOccurrenceId)

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should delete mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")))
      }

      @Test
      fun `should delete DPS scheduled movement`() {
        dpsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/temporary-absence-occurrences/$dpsOccurrenceId")))
      }

      @Test
      fun `should create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, NOT_FOUND)

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-DELETED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT delete DPS scheduled movement`() {
        dpsApi.verify(0, deleteRequestedFor(urlPathMatching("/sync/temporary-absence-occurrences/.*")))
      }

      @Test
      fun `should get mapping`() {
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")),
        )
      }

      @Test
      fun `should create telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-deleted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenDpsDeleteFails {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetScheduledMovementMapping(45678, dpsOccurrenceId)
        dpsApi.stubDeleteTapOccurrenceError(dpsOccurrenceId, status = 500)

        sendMessage(tapScheduleEvent("SCHEDULED_EXT_MOVE-DELETED"))
          .also { waitForAnyProcessingToComplete("temporary-absence-sync-scheduled-movement-deleted-error") }
      }

      @Test
      fun `should try to delete DPS scheduled movement`() {
        dpsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/temporary-absence-occurrences/$dpsOccurrenceId")))
      }

      @Test
      fun `should create error telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-sync-scheduled-movement-deleted-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("45678")
            assertThat(it["dpsOccurrenceId"]).isEqualTo("$dpsOccurrenceId")
          },
          isNull(),
        )
      }

      @Test
      fun `should not delete mapping`() {
        mappingApi.verify(
          count = 0,
          deleteRequestedFor(urlPathEqualTo("/mapping/temporary-absence/scheduled-movement/nomis-event-id/45678")),
        )
      }
    }
  }

  private fun sendMessage(event: String) = awsSqsExternalMovementsOffenderEventsClient.sendMessage(
    externalMovementsQueueOffenderEventsUrl,
    event,
  )

  private fun tapScheduleEvent(eventType: String, auditModuleName: String = "OCUCANTR", nomisEventType: String = "TAP", direction: String = "OUT", eventId: Long = 45678) = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "57126174-e2d7-518f-914e-0056a63363b0",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
         "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2025-09-02T09:19:03\",\"nomisEventType\":\"$eventType\",\"bookingId\":12345,\"offenderIdDisplay\":\"A1234BC\",\"eventId\":$eventId,\"eventMovementType\":\"$nomisEventType\",\"auditModuleName\":\"$auditModuleName\",\"directionCode\":\"$direction\"}",
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
