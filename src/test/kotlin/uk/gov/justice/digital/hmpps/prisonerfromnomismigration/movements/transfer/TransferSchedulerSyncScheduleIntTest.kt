package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleDpsApiExtension.Companion.dpsTransferSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleNomisApiMockServer.Companion.transferScheduleWaitlistResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncTransferRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransferSchedulerSyncScheduleIntTest(
  @Autowired private val mappingApi: TransferScheduleMappingApiMockServer,
  @Autowired private val nomisApi: TransferScheduleNomisApiMockServer,
) : TransferSchedulerIntegrationTestBase() {

  private val dpsApi = dpsTransferSchedulerServer

  override fun resetTelemetryClient() {}

  private fun setUpTestClass() {
    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    TransferScheduleDpsApiExtension.resetAndDisableResetBeforeEach()

    reset(telemetryClient)
  }

  @Nested
  @DisplayName("SCHEDULED_EXT_MOVE-INSERTED")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class TransferScheduleCreated {
    private val dpsId = UUID.randomUUID()

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferScheduleMapping(nomisEventId = 123L, status = NOT_FOUND)
        nomisApi.stubGetTransferScheduleOut(offenderNo = "A1234BC", eventId = 123L)
        dpsApi.stubSyncTransferSchedule(personIdentifier = "A1234BC", response = ReferenceId(dpsId))
        mappingApi.stubCreateTransferScheduleMapping()

        sendMessage(transferScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/123")))
      }

      @Test
      fun `should get NOMIS court event`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/transfers/schedule/out/123")))
      }

      @Test
      fun `should create DPS scheduled movement`() {
        TransferScheduleDpsApiMockServer.getRequestBody<SyncTransferRequest>(
          putRequestedFor(urlPathEqualTo("/sync/transfers/A1234BC")),
        ).apply {
          assertThat(transfer.dpsId).isNull()
          assertThat(transfer.eventId).isEqualTo(123L)
          assertThat(transfer.schedule!!.start).isCloseTo(LocalDateTime.now().minusDays(1), within(5, ChronoUnit.MINUTES))
          assertThat(transfer.schedule.eventSubType).isEqualTo("TRN")
          assertThat(transfer.schedule.eventStatus).isEqualTo("SCH")
          assertThat(transfer.schedule.commentText).isEqualTo("transfer schedule comment")
          assertThat(transfer.schedule.hiddenCommentText).isEqualTo("hidden transfer schedule comment")
          assertThat(transfer.schedule.agyLocId).isEqualTo("BXI")
          assertThat(transfer.schedule.toAgyLocId).isEqualTo("LEI")
          assertThat(transfer.schedule.outcomeReasonCode).isEqualTo("ADMI")
          assertThat(transfer.schedule.escortCode).isEqualTo("U")
          assertThat(transfer.waitlist!!.requestDate).isEqualTo("${LocalDate.now().minusDays(1)}")
          assertThat(transfer.waitlist.waitListStatus).isEqualTo("PEND")
          assertThat(transfer.waitlist.statusDate).isEqualTo("${LocalDate.now().minusDays(1)}")
          assertThat(transfer.waitlist.transferPriority).isEqualTo("3")
          assertThat(transfer.waitlist.approved).isTrue
          assertThat(transfer.waitlist.approvedUsername).isEqualTo("A_USER")
          assertThat(transfer.waitlist.outcomeReasonCode?.value).isEqualTo("TRANS")
          assertThat(transfer.waitlist.commentText1).isEqualTo("some waitlist comment")
          assertThat(syncUser.username).isEqualTo("SYS")
          assertThat(syncUser.activeCaseloadId).isEqualTo("MDI")
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", 12345)
            .withRequestBodyJsonPath("nomisEventId", 123)
            .withRequestBodyJsonPath("dpsTransferScheduleId", dpsId)
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should raise telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-schedule-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["dpsTransferScheduleId"]).isEqualTo("$dpsId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathNoWaitlist {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferScheduleMapping(nomisEventId = 123L, status = NOT_FOUND)
        nomisApi.stubGetTransferScheduleOut(offenderNo = "A1234BC", eventId = 123L, waitlist = null)
        dpsApi.stubSyncTransferSchedule(personIdentifier = "A1234BC", response = ReferenceId(dpsId))
        mappingApi.stubCreateTransferScheduleMapping()

        sendMessage(transferScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should create DPS scheduled movement without waitlist`() {
        TransferScheduleDpsApiMockServer.getRequestBody<SyncTransferRequest>(
          putRequestedFor(urlPathEqualTo("/sync/transfers/A1234BC")),
        ).apply {
          assertThat(transfer.dpsId).isNull()
          assertThat(transfer.eventId).isEqualTo(123L)
          assertThat(transfer.schedule).isNotNull
          assertThat(transfer.waitlist).isNull()
        }
      }

      @Test
      fun `should raise telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-schedule-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["dpsTransferScheduleId"]).isEqualTo("$dpsId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenWaitlistCreatedMoreRecently {
      private val waitlistCreatedTime = LocalDateTime.now()
      private val scheduleCreatedTime = LocalDateTime.now().minusDays(1)

      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferScheduleMapping(nomisEventId = 123L, status = NOT_FOUND)
        nomisApi.stubGetTransferScheduleOut(
          offenderNo = "A1234BC",
          eventId = 123L,
          createUsername = "SCHEDULE_USER",
          createDateTime = scheduleCreatedTime,
          waitlist = transferScheduleWaitlistResponse(
            createUsername = "WAITLIST_USER",
            createDateTime = waitlistCreatedTime,
          ),
        )
        dpsApi.stubSyncTransferSchedule(personIdentifier = "A1234BC", response = ReferenceId(dpsId))
        mappingApi.stubCreateTransferScheduleMapping()

        sendMessage(transferScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should create DPS scheduled movement with audit from waitlist`() {
        TransferScheduleDpsApiMockServer.getRequestBody<SyncTransferRequest>(
          putRequestedFor(urlPathEqualTo("/sync/transfers/A1234BC")),
        ).apply {
          assertThat(transfer.dpsId).isNull()
          assertThat(transfer.eventId).isEqualTo(123L)
          assertThat(syncUser.username).isEqualTo("WAITLIST_USER")
          assertThat(occurredAt).isEqualTo(waitlistCreatedTime)
        }
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        sendMessage(transferScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT check mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/123")),
        )
      }

      @Test
      fun `should NOT create DPS scheduled transfer`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/transfers/A1234BC")),
        )
      }

      @Test
      fun `should raise telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-schedule-inserted-ignored"),
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
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferScheduleMapping(nomisEventId = 123L)

        sendMessage(transferScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/123")))
      }

      @Test
      fun `should NOT create DPS scheduled transfer`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/transfers/A1234BC")),
        )
      }

      @Test
      fun `should raise telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-schedule-inserted-ignored"),
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
    inner class DuplicateMapping {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferScheduleMapping(nomisEventId = 123L, status = NOT_FOUND)
        nomisApi.stubGetTransferScheduleOut(offenderNo = "A1234BC", eventId = 123L)
        dpsApi.stubSyncTransferSchedule(personIdentifier = "A1234BC", response = ReferenceId(dpsId))
        mappingApi.stubCreateTransferScheduleMappingConflict(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              existing = TransferScheduleMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345,
                nomisEventId = 123,
                dpsTransferScheduleId = dpsId,
                mappingType = TransferScheduleMappingDto.MappingType.NOMIS_CREATED,
              ),
              duplicate = TransferScheduleMappingDto(
                prisonerNumber = "A1234BC",
                bookingId = 12345,
                nomisEventId = 999,
                dpsTransferScheduleId = dpsId,
                mappingType = TransferScheduleMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        sendMessage(transferScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete("transfer-scheduler-sync-schedule-inserted-duplicate") }
      }

      @Test
      fun `should create DPS scheduled movement a single time`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/transfers/A1234BC")))
      }

      @Test
      fun `should create mapping a single time`() {
        mappingApi.verify(postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule")))
      }

      @Test
      fun `should raise success telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-schedule-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["dpsTransferScheduleId"]).isEqualTo("$dpsId")
          },
          isNull(),
        )
      }

      @Test
      fun `should raise duplicate telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-schedule-inserted-duplicate"),
          check {
            assertThat(it["existingOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["existingBookingId"]).isEqualTo("12345")
            assertThat(it["existingNomisEventId"]).isEqualTo("123")
            assertThat(it["existingDpsTransferScheduleId"]).isEqualTo("$dpsId")
            assertThat(it["duplicateOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["duplicateBookingId"]).isEqualTo("12345")
            assertThat(it["duplicateNomisEventId"]).isEqualTo("999")
            assertThat(it["duplicateDpsTransferScheduleId"]).isEqualTo("$dpsId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class MappingRetry {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferScheduleMapping(nomisEventId = 123L, status = NOT_FOUND)
        nomisApi.stubGetTransferScheduleOut(offenderNo = "A1234BC", eventId = 123L)
        dpsApi.stubSyncTransferSchedule(personIdentifier = "A1234BC", response = ReferenceId(dpsId))
        mappingApi.stubCreateTransferScheduleMappingFailureFollowedBySuccess()

        sendMessage(transferScheduleEvent("SCHEDULED_EXT_MOVE-INSERTED"))
          .also { waitForAnyProcessingToComplete("transfer-scheduler-sync-schedule-mapping-retry-created") }
      }

      @Test
      fun `should create DPS scheduled movement`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/transfers/A1234BC")))
      }

      @Test
      fun `should create mapping on 2nd call`() {
        mappingApi.verify(
          count = 2,
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule")),
        )
      }

      @Test
      fun `should raise telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-schedule-inserted-success"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `should raise mapping retry telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-schedule-mapping-retry-created"),
          check {
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["dpsTransferScheduleId"]).isEqualTo("$dpsId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("SCHEDULE_EXT_MOVE-UPDATED")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class TransferScheduleUpdated {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      val dpsId = UUID.randomUUID()

      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferScheduleMapping(nomisEventId = 123L, dpsTransferScheduleId = dpsId)
        nomisApi.stubGetTransferScheduleOut(offenderNo = "A1234BC", eventId = 123L)
        dpsApi.stubSyncTransferSchedule(personIdentifier = "A1234BC", response = ReferenceId(dpsId))

        sendMessage(transferScheduleEvent("SCHEDULED_EXT_MOVE-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/123")))
      }

      @Test
      fun `should get NOMIS court event`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/transfers/schedule/out/123")))
      }

      @Test
      fun `should update DPS scheduled movement passing DPS ID`() {
        TransferScheduleDpsApiMockServer.getRequestBody<SyncTransferRequest>(
          putRequestedFor(urlPathEqualTo("/sync/transfers/A1234BC")),
        ).apply {
          assertThat(transfer.dpsId).isEqualTo(dpsId)
          assertThat(transfer.eventId).isEqualTo(123L)
        }
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule")),
        )
      }

      @Test
      fun `should raise telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-schedule-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["dpsTransferScheduleId"]).isEqualTo("$dpsId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenUpdatedInDps {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        sendMessage(transferScheduleEvent("SCHEDULED_EXT_MOVE-UPDATED", "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT check mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/123")),
        )
      }

      @Test
      fun `should NOT update DPS scheduled transfer`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/transfers/A1234BC")),
        )
      }

      @Test
      fun `should raise telemetry`() = runTest {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-schedule-updated-ignored"),
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
    inner class WhenMappingDoesNotExist {
      val dpsId = UUID.randomUUID()

      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferScheduleMapping(nomisEventId = 123L, status = NOT_FOUND)

        sendMessage(transferScheduleEvent("SCHEDULED_EXT_MOVE-UPDATED"))
          .also { waitForAnyProcessingToComplete("transfer-scheduler-sync-schedule-updated-error", times = 2) }
      }

      @Test
      fun `should check mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/123")))
      }

      @Test
      fun `should NOT update DPS scheduled transfer`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/transfers/A1234BC")),
        )
      }

      @Test
      fun `should raise telemetry`() = runTest {
        verify(telemetryClient, times(2)).trackEvent(
          eq("transfer-scheduler-sync-schedule-updated-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["nomisEventId"]).isEqualTo("123")
          },
          isNull(),
        )
      }

      @Test
      fun `should send event to DLQ`() {
        assertThat(awsSqsTransferMovementsOffenderEventsDlqClient.countAllMessagesOnQueue(transferMovementsQueueOffenderEventsDlqUrl).get())
          .isEqualTo(1)
      }
    }
  }

  private fun sendMessage(event: String) = awsSqsTransferMovementsOffenderEventsClient.sendMessage(
    transferMovementsQueueOffenderEventsUrl,
    event,
  )

  private fun transferScheduleEvent(eventType: String, auditModuleName: String = "OCUCANTR", nomisEventType: String = "TRN", eventId: Long = 123) = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "57126174-e2d7-518f-914e-0056a63363b0",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
         "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2025-09-02T09:19:03\",\"nomisEventType\":\"$eventType\",\"bookingId\":12345,\"offenderIdDisplay\":\"A1234BC\",\"eventId\":$eventId,\"eventMovementType\":\"$nomisEventType\",\"auditModuleName\":\"$auditModuleName\",\"directionCode\":\"OUT\"}",
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
