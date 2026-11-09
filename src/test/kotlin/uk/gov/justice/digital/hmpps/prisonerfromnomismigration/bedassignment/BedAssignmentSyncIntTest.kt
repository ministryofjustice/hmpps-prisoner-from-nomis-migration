package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.matches
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.BedAssignmentDpsApiExtension.Companion.bedAssignmentApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.countAllMessagesOnDLQQueue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.HmppsQueue

private const val SYNC_OFFENDER_NO = "A1234KT"
private const val SYNC_BOOKING_ID = 123456L
private const val SYNC_SEQUENCE = 1
private const val SYNC_DPS_ID = "e52d7268-6e10-41a8-a0b9-2319b32520d6"
private const val SYNC_DPS_ID_2 = "f3f31737-6ee3-4ec5-8a79-0ac110fe50e2"

class BedAssignmentSyncIntTest(
  @Autowired private val nomisMockServer: BedAssignmentNomisApiMockServer,
  @Autowired private val mappingMockServer: BedAssignmentMappingApiMockServer,
) : BedAssignmentIntegrationTestBase() {
  override fun getQueues(): List<HmppsQueue> = listOf(bedAssignmentEventQueue)

  @Nested
  @DisplayName("BED_ASSIGNMENT_HISTORY-INSERTED")
  inner class Inserted {
    private val eventType = "BED_ASSIGNMENT_HISTORY-INSERTED"

    @Nested
    inner class DpsOrigin {
      @BeforeEach
      fun setUp() {
        send(eventType, auditModuleName = "DPS_SYNCHRONISATION")
        waitForTelemetry("bedassignment-synchronisation-created-skipped")
      }

      @Test
      fun `skips events explicitly originating from DPS`() {
        verify(telemetryClient).trackEvent(
          eq("bedassignment-synchronisation-created-skipped"),
          check {
            assertThat(it).containsEntry("offenderNo", SYNC_OFFENDER_NO)
              .containsEntry("bookingId", SYNC_BOOKING_ID.toString())
              .containsEntry("sequence", SYNC_SEQUENCE.toString())
          },
          isNull(),
        )
        nomisApi.verify(0, getRequestedFor(urlPathMatching("/prisoners/.*")))
        bedAssignmentApi.verify(0, putRequestedFor(urlPathMatching("/nomis-sync/bed-assignments/sync/.*")))
      }
    }

    @Nested
    inner class WholeHistory {
      @BeforeEach
      fun setUp() {
        stubTwoRowHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(
            created = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, 1),
            updated = assignmentResult(SYNC_DPS_ID_2, 234567, 2),
            deleted = assignmentResult("8b07cbd9-0820-0a0f-c32f-a9429b618e0b", 345678, 3),
          ),
        )
        mappingMockServer.stubGetByNomisId(SYNC_BOOKING_ID, 1, HttpStatus.NOT_FOUND)
        mappingMockServer.stubCreateMapping()
        mappingMockServer.stubGetByNomisId(
          234567,
          2,
          bedAssignmentMappingJson(234567, 2, SYNC_DPS_ID_2, "NOMIS_CREATED"),
        )
        mappingMockServer.stubDeleteMapping()
        send(eventType)
        waitForTelemetry("bedassignment-synchronisation-created-success")
      }

      @Test
      fun `sends all bookings in the current history to DPS`() {
        bedAssignmentApi.verify(
          putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO"))
            .withRequestBodyJsonPath("$[0].bookingId", 123456)
            .withRequestBodyJsonPath("$[0].sequence", 1)
            .withRequestBodyJsonPath("$[0].livingUnitId", 987654)
            .withRequestBodyJsonPath("$[0].livingUnitDescription", "A-1-001")
            .withRequestBodyJsonPath("$[0].locationId", SYNC_LOCATION_ID)
            .withRequestBodyJsonPath("$[0].createdBy", "NOMIS.USER")
            .withRequestBodyJsonPath("$[1].bookingId", 234567)
            .withRequestBodyJsonPath("$[1].sequence", 2),
        )
      }

      @Test
      fun `creates missing, preserves matching and deletes mappings by DPS id`() {
        mappingApi.verify(
          postRequestedFor(urlEqualTo("/mapping/bed-assignments"))
            .withRequestBodyJsonPath("dpsBedAssignmentId", SYNC_DPS_ID)
            .withRequestBodyJsonPath("nomisBookingId", "123456")
            .withRequestBodyJsonPath("nomisSequence", "1")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
        mappingApi.verify(
          0,
          postRequestedFor(urlEqualTo("/mapping/bed-assignments"))
            .withRequestBodyJsonPath("dpsBedAssignmentId", SYNC_DPS_ID_2),
        )
        mappingApi.verify(
          deleteRequestedFor(urlEqualTo("/mapping/bed-assignments/dps-bed-assignment-id/8b07cbd9-0820-0a0f-c32f-a9429b618e0b")),
        )
      }

      @Test
      fun `records operation counts`() {
        verify(telemetryClient).trackEvent(
          eq("bedassignment-synchronisation-created-success"),
          check {
            assertThat(it).containsEntry("offenderNo", SYNC_OFFENDER_NO)
              .containsEntry("bookingId", SYNC_BOOKING_ID.toString())
              .containsEntry("sequence", SYNC_SEQUENCE.toString())
              .containsEntry("historyCount", "2")
              .containsEntry("createdCount", "1")
              .containsEntry("updatedCount", "1")
              .containsEntry("deletedCount", "1")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class RepeatedAndOutOfOrderEvents {
      @Test
      fun `each event re-reads current history instead of applying stale row deltas`() {
        stubTwoRowHistory()
        bedAssignmentApi.stubSync(SYNC_OFFENDER_NO)

        send("BED_ASSIGNMENT_HISTORY-UPDATED", sequence = 2)
        waitForTelemetry("bedassignment-synchronisation-updated-success")
        send("BED_ASSIGNMENT_HISTORY-DELETED", sequence = 1)
        waitForTelemetry("bedassignment-synchronisation-deleted-success")

        nomisApi.verify(2, getRequestedFor(urlEqualTo("/prisoners/$SYNC_OFFENDER_NO/bed-assignments")))
        bedAssignmentApi.verify(
          2,
          putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO"))
            .withRequestBodyJsonPath("$[0].bookingId", SYNC_BOOKING_ID)
            .withRequestBodyJsonPath("$[1].bookingId", 234567),
        )
      }
    }

    @Nested
    inner class EmptyHistory {
      @Test
      fun `sends an empty history to remove the final DPS row`() {
        stubEmptyHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(deleted = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE)),
        )
        mappingMockServer.stubDeleteMapping()

        send(eventType)
        waitForTelemetry("bedassignment-synchronisation-created-success")

        bedAssignmentApi.verify(
          putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO"))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.equalToJson("[]")),
        )
        mappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/bed-assignments/dps-bed-assignment-id/$SYNC_DPS_ID")))
      }
    }

    @Nested
    inner class MappingRecovery {
      @BeforeEach
      fun setUp() {
        stubSingleRowHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(created = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE)),
        )
        mappingMockServer.stubGetByNomisId(SYNC_BOOKING_ID, SYNC_SEQUENCE, HttpStatus.NOT_FOUND)
        mappingMockServer.stubCreateMappingFailureFollowedBySuccess()

        send(eventType)
        waitForTelemetry("bedassignment-synchronisation-created-success")
        waitForTelemetry("bedassignment-mapping-created-success")
      }

      @Test
      fun `mapping-only retry does not repeat the successful whole-history DPS sync`() {
        bedAssignmentApi.verify(1, putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO")))
        mappingApi.verify(2, postRequestedFor(urlEqualTo("/mapping/bed-assignments")))
      }

      @Test
      fun `reports the initial mapping failure separately from overall success`() {
        verify(telemetryClient).trackEvent(
          eq("bedassignment-synchronisation-created-success"),
          check { assertThat(it).containsEntry("mapping", "initial-failure") },
          isNull(),
        )
      }
    }

    @Nested
    inner class DuplicateMappings {
      @Test
      fun `reports and retries a conflicting mapping rather than overwriting it`() {
        stubSingleRowHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(created = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE)),
        )
        mappingMockServer.stubGetByNomisId(
          SYNC_BOOKING_ID,
          SYNC_SEQUENCE,
          bedAssignmentMappingJson(SYNC_BOOKING_ID, SYNC_SEQUENCE, SYNC_DPS_ID_2, "NOMIS_CREATED"),
        )

        send(eventType)
        waitForTelemetry("bedassignment-synchronisation-created-success")

        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 1 }
        verify(telemetryClient, times(3)).trackEvent(eq("bedassignment-from-nomis-sync-duplicate"), anyMap(), isNull())
        verify(telemetryClient).trackEvent(
          eq("bedassignment-synchronisation-created-success"),
          check { assertThat(it).containsEntry("mapping", "initial-failure") },
          isNull(),
        )
        mappingApi.verify(0, postRequestedFor(urlEqualTo("/mapping/bed-assignments")))
      }
    }

    @Nested
    inner class DownstreamFailures {
      @Test
      fun `retries and dead letters NOMIS history failures`() {
        nomisMockServer.stubGetBedAssignmentsError(SYNC_OFFENDER_NO)
        send(eventType)

        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 1 }
        verify(telemetryClient, times(2)).trackEvent(
          eq("bedassignment-synchronisation-created-error"),
          anyMap(),
          isNull(),
        )
        bedAssignmentApi.verify(0, putRequestedFor(urlPathMatching("/nomis-sync/bed-assignments/sync/.*")))
      }

      @Test
      fun `retries and dead letters DPS sync failures`() {
        stubSingleRowHistory()
        bedAssignmentApi.stubSyncError(SYNC_OFFENDER_NO, 400)
        send(eventType)

        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 1 }
        bedAssignmentApi.verify(2, putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO")))
        mappingApi.verify(0, postRequestedFor(urlEqualTo("/mapping/bed-assignments")))
      }

      @Test
      fun `retries and dead letters location lookup failures`() {
        nomisMockServer.stubGetBedAssignmentsForPrisoner(SYNC_OFFENDER_NO, bedAssignmentHistoryJson())
        stubLocationFailure()
        bedAssignmentApi.stubSync(SYNC_OFFENDER_NO)
        send(eventType)

        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 1 }
        bedAssignmentApi.verify(0, putRequestedFor(urlPathMatching("/nomis-sync/bed-assignments/sync/.*")))
      }
    }

    @Nested
    inner class InvalidEvents {
      @Test
      fun `dead letters events missing their required booking id or sequence`() {
        bedAssignmentEventClient.sendMessage(
          bedAssignmentEventQueueUrl,
          malformedBedAssignmentEvent(eventType, missingBookingId = true),
        )
        bedAssignmentEventClient.sendMessage(
          bedAssignmentEventQueueUrl,
          malformedBedAssignmentEvent(eventType, missingSequence = true),
        )

        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 2 }
      }
    }
  }

  @Nested
  @DisplayName("BED_ASSIGNMENT_HISTORY-UPDATED")
  inner class Updated {
    private val eventType = "BED_ASSIGNMENT_HISTORY-UPDATED"

    @Nested
    inner class DpsOrigin {
      @Test
      fun `skips an explicitly DPS-originated update`() {
        send(eventType, auditModuleName = "DPS_SYNCHRONISATION")
        waitForTelemetry("bedassignment-synchronisation-updated-skipped")

        nomisApi.verify(0, getRequestedFor(urlPathMatching("/prisoners/.*")))
        bedAssignmentApi.verify(0, putRequestedFor(urlPathMatching("/nomis-sync/bed-assignments/sync/.*")))
      }
    }

    @Nested
    inner class CurrentHistory {
      @Test
      fun `re-reads and sends the full cross-booking history for an update`() {
        stubTwoRowHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(updated = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE)),
        )
        mappingMockServer.stubGetByNomisId(SYNC_BOOKING_ID, SYNC_SEQUENCE, bedAssignmentMappingJson())

        send(eventType)
        waitForTelemetry("bedassignment-synchronisation-updated-success")

        nomisApi.verify(1, getRequestedFor(urlEqualTo("/prisoners/$SYNC_OFFENDER_NO/bed-assignments")))
        bedAssignmentApi.verify(
          putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO"))
            .withRequestBodyJsonPath("$[0].bookingId", 123456)
            .withRequestBodyJsonPath("$[1].bookingId", 234567),
        )
        mappingApi.verify(0, postRequestedFor(urlEqualTo("/mapping/bed-assignments")))
      }

      @Test
      fun `processes a no-op empty history`() {
        stubEmptyHistory()
        bedAssignmentApi.stubSync(SYNC_OFFENDER_NO)

        send(eventType)
        waitForTelemetry("bedassignment-synchronisation-updated-success")

        bedAssignmentApi.verify(
          putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO"))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.equalToJson("[]")),
        )
      }
    }

    @Nested
    inner class MappingReconciliation {
      @Test
      fun `creates a mapping when DPS reports an updated row whose mapping is missing`() {
        stubSingleRowHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(updated = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE)),
        )
        mappingMockServer.stubGetByNomisId(SYNC_BOOKING_ID, SYNC_SEQUENCE, HttpStatus.NOT_FOUND)
        mappingMockServer.stubCreateMapping()

        send(eventType)
        waitForTelemetry("bedassignment-synchronisation-updated-success")

        mappingApi.verify(
          postRequestedFor(urlEqualTo("/mapping/bed-assignments"))
            .withRequestBodyJsonPath("dpsBedAssignmentId", SYNC_DPS_ID)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `does not overwrite a conflicting mapping and schedules recovery`() {
        stubSingleRowHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(updated = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE)),
        )
        mappingMockServer.stubGetByNomisId(
          SYNC_BOOKING_ID,
          SYNC_SEQUENCE,
          bedAssignmentMappingJson(SYNC_BOOKING_ID, SYNC_SEQUENCE, SYNC_DPS_ID_2, "NOMIS_CREATED"),
        )

        send(eventType)
        waitForTelemetry("bedassignment-synchronisation-updated-success")

        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 1 }
        verify(telemetryClient, times(3)).trackEvent(eq("bedassignment-from-nomis-sync-duplicate"), anyMap(), isNull())
        mappingApi.verify(0, postRequestedFor(urlEqualTo("/mapping/bed-assignments")))
      }
    }

    @Nested
    inner class MappingFailures {
      @Test
      fun `recovers transient mapping creation without another DPS call`() {
        stubSingleRowHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(created = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE)),
        )
        mappingMockServer.stubGetByNomisId(SYNC_BOOKING_ID, SYNC_SEQUENCE, HttpStatus.NOT_FOUND)
        mappingMockServer.stubCreateMappingFailureFollowedBySuccess()

        send(eventType)
        waitForTelemetry("bedassignment-mapping-created-success")

        bedAssignmentApi.verify(1, putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO")))
        mappingApi.verify(2, postRequestedFor(urlEqualTo("/mapping/bed-assignments")))
      }

      @Test
      fun `dead letters permanently failing mapping retry messages`() {
        stubSingleRowHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(created = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE)),
        )
        mappingMockServer.stubGetByNomisId(SYNC_BOOKING_ID, SYNC_SEQUENCE, HttpStatus.NOT_FOUND)
        mappingApi.stubFor(
          com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/mapping/bed-assignments"))
            .willReturn(aResponse().withStatus(500)),
        )

        send(eventType)
        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 1 }

        bedAssignmentApi.verify(1, putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO")))
        verify(telemetryClient, times(2)).trackEvent(eq("bedassignment-mapping-created-failure"), anyMap(), isNull())
      }
    }

    @Nested
    inner class DownstreamFailures {
      @Test
      fun `retries and dead letters NOMIS history failures`() {
        nomisMockServer.stubGetBedAssignmentsError(SYNC_OFFENDER_NO)
        send(eventType)

        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 1 }
        verify(telemetryClient, times(2)).trackEvent(
          eq("bedassignment-synchronisation-updated-error"),
          anyMap(),
          isNull(),
        )
        bedAssignmentApi.verify(0, putRequestedFor(urlPathMatching("/nomis-sync/bed-assignments/sync/.*")))
      }

      @Test
      fun `retries and dead letters DPS sync failures`() {
        stubEmptyHistory()
        bedAssignmentApi.stubSyncError(SYNC_OFFENDER_NO, 400)
        send(eventType)

        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 1 }
        bedAssignmentApi.verify(2, putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO")))
      }
    }
  }

  @Nested
  @DisplayName("BED_ASSIGNMENT_HISTORY-DELETED")
  inner class Deleted {
    private val eventType = "BED_ASSIGNMENT_HISTORY-DELETED"

    @Nested
    inner class DpsOrigin {
      @Test
      fun `skips an explicitly DPS-originated deletion`() {
        send(eventType, auditModuleName = "DPS_SYNCHRONISATION")
        waitForTelemetry("bedassignment-synchronisation-deleted-skipped")

        nomisApi.verify(0, getRequestedFor(urlPathMatching("/prisoners/.*")))
        bedAssignmentApi.verify(0, putRequestedFor(urlPathMatching("/nomis-sync/bed-assignments/sync/.*")))
      }

      @Test
      fun `skips a deletion with no audit module`() {
        send(eventType, auditModuleName = null)
        waitForTelemetry("bedassignment-synchronisation-deleted-skipped")

        nomisApi.verify(0, getRequestedFor(urlPathMatching("/prisoners/.*")))
        bedAssignmentApi.verify(0, putRequestedFor(urlPathMatching("/nomis-sync/bed-assignments/sync/.*")))
      }
    }

    @Nested
    inner class CurrentHistory {
      @Test
      fun `re-reads the current history when the deleted row has already disappeared`() {
        stubTwoRowHistory()
        bedAssignmentApi.stubSync(SYNC_OFFENDER_NO)

        send(eventType, sequence = 3)
        waitForTelemetry("bedassignment-synchronisation-deleted-success")

        bedAssignmentApi.verify(
          putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO"))
            .withRequestBodyJsonPath("$[0].bookingId", 123456)
            .withRequestBodyJsonPath("$[1].bookingId", 234567),
        )
      }

      @Test
      fun `removes all rows for an empty history`() {
        stubEmptyHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(deleted = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE)),
        )
        mappingMockServer.stubDeleteMapping()

        send(eventType)
        waitForTelemetry("bedassignment-synchronisation-deleted-success")

        bedAssignmentApi.verify(
          putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO"))
            .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.equalToJson("[]")),
        )
        mappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/bed-assignments/dps-bed-assignment-id/$SYNC_DPS_ID")))
      }
    }

    @Nested
    inner class DeletionMapping {
      @Test
      fun `deletes each returned mapping by DPS id before recreating mappings`() {
        stubSingleRowHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(
            deleted = assignmentResult(SYNC_DPS_ID_2, 234567, 2),
            created = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE),
          ),
        )
        mappingMockServer.stubDeleteMapping()
        mappingMockServer.stubGetByNomisId(SYNC_BOOKING_ID, SYNC_SEQUENCE, HttpStatus.NOT_FOUND)
        mappingMockServer.stubCreateMapping()

        send(eventType)
        waitForTelemetry("bedassignment-synchronisation-deleted-success")

        mappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/bed-assignments/dps-bed-assignment-id/$SYNC_DPS_ID_2")))
        mappingApi.verify(postRequestedFor(urlEqualTo("/mapping/bed-assignments")))
      }

      @Test
      fun `keeps a matching mapping and does not create it again`() {
        stubSingleRowHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(updated = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE)),
        )
        mappingMockServer.stubGetByNomisId(SYNC_BOOKING_ID, SYNC_SEQUENCE, bedAssignmentMappingJson())

        send(eventType)
        waitForTelemetry("bedassignment-synchronisation-deleted-success")

        mappingApi.verify(0, postRequestedFor(urlEqualTo("/mapping/bed-assignments")))
      }
    }

    @Nested
    inner class MappingRetry {
      @Test
      fun `retries original deleted DPS ids without syncing DPS again`() {
        stubEmptyHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(deleted = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE)),
        )
        mappingMockServer.stubDeleteMappingFailureFollowedBySuccess(SYNC_DPS_ID)

        send(eventType)
        waitForTelemetry("bedassignment-mapping-created-success")

        bedAssignmentApi.verify(1, putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO")))
        mappingApi.verify(
          2,
          deleteRequestedFor(urlEqualTo("/mapping/bed-assignments/dps-bed-assignment-id/$SYNC_DPS_ID")),
        )
      }

      @Test
      fun `dead letters permanent mapping deletion failures`() {
        stubEmptyHistory()
        bedAssignmentApi.stubSync(
          SYNC_OFFENDER_NO,
          syncResponse(deleted = assignmentResult(SYNC_DPS_ID, SYNC_BOOKING_ID, SYNC_SEQUENCE)),
        )
        mappingApi.stubFor(
          com.github.tomakehurst.wiremock.client.WireMock.delete(urlEqualTo("/mapping/bed-assignments/dps-bed-assignment-id/$SYNC_DPS_ID"))
            .willReturn(aResponse().withStatus(500)),
        )

        send(eventType)
        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 1 }

        bedAssignmentApi.verify(1, putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO")))
        verify(telemetryClient, times(2)).trackEvent(eq("bedassignment-mapping-created-failure"), anyMap(), isNull())
      }
    }

    @Nested
    inner class DownstreamFailures {
      @Test
      fun `retries and dead letters NOMIS errors`() {
        nomisMockServer.stubGetBedAssignmentsError(SYNC_OFFENDER_NO)
        send(eventType)

        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 1 }
        verify(telemetryClient, times(2)).trackEvent(
          eq("bedassignment-synchronisation-deleted-error"),
          anyMap(),
          isNull(),
        )
      }

      @Test
      fun `retries and dead letters DPS errors`() {
        stubEmptyHistory()
        bedAssignmentApi.stubSyncError(SYNC_OFFENDER_NO, 400)
        send(eventType)

        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 1 }
        bedAssignmentApi.verify(2, putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$SYNC_OFFENDER_NO")))
      }

      @Test
      fun `retries and dead letters location lookup errors`() {
        nomisMockServer.stubGetBedAssignmentsForPrisoner(SYNC_OFFENDER_NO, bedAssignmentHistoryJson())
        stubLocationFailure()
        bedAssignmentApi.stubSync(SYNC_OFFENDER_NO)
        send(eventType)

        await untilCallTo { bedAssignmentEventQueue.countAllMessagesOnDLQQueue() } matches { it == 1 }
        bedAssignmentApi.verify(0, putRequestedFor(urlPathMatching("/nomis-sync/bed-assignments/sync/.*")))
      }
    }
  }

  private fun send(
    eventType: String,
    sequence: Int = SYNC_SEQUENCE,
    auditModuleName: String? = "OIDSTUFF",
    attributeEventType: String = eventType,
  ) {
    bedAssignmentEventClient.sendMessage(
      bedAssignmentEventQueueUrl,
      bedAssignmentEvent(eventType, sequence, auditModuleName, attributeEventType),
    )
  }

  private fun stubSingleRowHistory() {
    nomisMockServer.stubGetBedAssignmentsForPrisoner(SYNC_OFFENDER_NO, bedAssignmentHistoryJson())
    stubLocationMapping(987654, SYNC_LOCATION_ID)
  }

  private fun stubTwoRowHistory() {
    nomisMockServer.stubGetBedAssignmentsForPrisoner(
      SYNC_OFFENDER_NO,
      bedAssignmentHistoryWithRowsJson(
        bedAssignmentJson(bookingId = SYNC_BOOKING_ID, sequence = 1, livingUnitId = 987654),
        bedAssignmentJson(bookingId = 234567, sequence = 2, livingUnitId = 987654),
      ),
    )
    stubLocationMapping(987654, SYNC_LOCATION_ID)
  }

  private fun stubEmptyHistory() {
    nomisMockServer.stubGetBedAssignmentsForPrisoner(SYNC_OFFENDER_NO, """{"bedAssignments":[]}""")
  }

  private fun stubLocationMapping(nomisLocationId: Long, dpsLocationId: String) {
    mappingApi.stubFor(
      com.github.tomakehurst.wiremock.client.WireMock.get("/mapping/locations/nomis/$nomisLocationId")
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(200)
            .withBody("""{"dpsLocationId":"$dpsLocationId","nomisLocationId":$nomisLocationId,"mappingType":"MIGRATED"}"""),
        ),
    )
  }

  private fun stubLocationFailure() {
    mappingApi.stubFor(
      com.github.tomakehurst.wiremock.client.WireMock.get(urlPathMatching("/mapping/locations/nomis/.*"))
        .willReturn(aResponse().withStatus(500)),
    )
  }

  private fun waitForTelemetry(namePattern: String) {
    await untilAsserted {
      verify(telemetryClient).trackEvent(matches(namePattern), anyMap(), isNull())
    }
  }
}

private const val SYNC_LOCATION_ID = "abcdef12-1234-5678-9012-abcdef123456"

private fun assignmentResult(id: String, bookingId: Long, sequence: Int) = """{"id":"$id","bookingId":$bookingId,"sequence":$sequence}"""

private fun syncResponse(
  created: String? = null,
  updated: String? = null,
  deleted: String? = null,
) = """{"created":[${created.orEmpty()}],"updated":[${updated.orEmpty()}],"deleted":[${deleted.orEmpty()}]}"""

private fun bedAssignmentEvent(
  payloadEventType: String,
  sequence: Int,
  auditModuleName: String?,
  attributeEventType: String = payloadEventType,
) = """
{
  "MessageId":"ae06c49e-1f41-4b9f-b2f2-dcca610d02cd",
  "Type":"Notification",
  "Timestamp":"2019-10-21T14:01:18.500Z",
  "Message":"{\"eventType\":\"$payloadEventType\",\"eventDatetime\":\"2024-07-10T15:00:25.489964\",\"bookingId\":$SYNC_BOOKING_ID,\"offenderIdDisplay\":\"$SYNC_OFFENDER_NO\",\"bedAssignmentSeq\":$sequence${auditModuleName?.let { ",\\\"auditModuleName\\\":\\\"$it\\\"" } ?: ""}}",
  "TopicArn":"arn:aws:sns:eu-west-1:000000000000:offender_events",
  "MessageAttributes":{
    "eventType":{"Type":"String","Value":"$attributeEventType"},
    "id":{"Type":"String","Value":"8b07cbd9-0820-0a0f-c32f-a9429b618e0b"},
    "contentType":{"Type":"String","Value":"text/plain;charset=UTF-8"},
    "timestamp":{"Type":"Number.java.lang.Long","Value":"1571666478344"}
  }
}
""".trimIndent()

private fun malformedBedAssignmentEvent(
  eventType: String,
  missingBookingId: Boolean = false,
  missingSequence: Boolean = false,
  offenderNo: String = SYNC_OFFENDER_NO,
) = """
{
  "MessageId":"ae06c49e-1f41-4b9f-b2f2-dcca610d02cd",
  "Type":"Notification",
  "Message":"{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-07-10T15:00:25.489964\",\"offenderIdDisplay\":\"$offenderNo\"${if (missingBookingId) "" else ",\\\"bookingId\\\":123456"}${if (missingSequence) "" else ",\\\"bedAssignmentSeq\\\":1"}}",
  "MessageAttributes":{"eventType":{"Type":"String","Value":"$eventType"}}
}
""".trimIndent()
