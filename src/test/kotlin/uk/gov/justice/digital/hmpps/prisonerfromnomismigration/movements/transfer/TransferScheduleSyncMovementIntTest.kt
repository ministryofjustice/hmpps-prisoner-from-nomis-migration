package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferMovementMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncMovementRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransferScheduleSyncMovementIntTest(
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
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (inserted)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class TransferMovementCreated {
    private val dpsTransferMovementId = UUID.randomUUID()
    private val dpsTransferScheduleId = UUID.randomUUID()

    @Nested
    @DisplayName("Happy path - scheduled transfer movement")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathOutboundWithSchedule {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferMovementMapping(NOT_FOUND)
        nomisApi.stubGetTransferMovementOut(escort = null)
        mappingApi.stubGetTransferScheduleMapping(123, dpsTransferScheduleId)
        mappingApi.stubCreateTransferMovementMapping()
        dpsApi.stubSyncTransferMovement("A1234BC", response = ReferenceId(dpsTransferMovementId))

        sendMessage(transferMovementEvent(inserted = true))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check movement mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should check schedule mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/123")))
      }

      @Test
      fun `should get NOMIS transfer movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/transfer/movement/out/12345/3")))
      }

      @Test
      fun `should create DPS transfer movement`() {
        TransferScheduleDpsApiMockServer.getRequestBody<SyncMovementRequest>(
          putRequestedFor(urlPathEqualTo("/sync/transfer-movements/A1234BC")),
        ).apply {
          with(movement) {
            assertThat(dpsId).isNull()
            assertThat(dpsTransferId).isEqualTo(dpsTransferScheduleId)
            assertThat(offenderBookId).isEqualTo(12345)
            assertThat(movementSeq).isEqualTo(3)
            assertThat(occurredAt).isCloseTo(LocalDateTime.now(), within(Duration.ofMinutes(5)))
            assertThat(movementReasonCode).isEqualTo("28")
            assertThat(escortCode).isEqualTo("NOT_PROVIDED")
            assertThat(fromAgyLocId).isEqualTo("BXI")
            assertThat(toAgyLocId).isEqualTo("LEI")
            assertThat(active).isTrue
            assertThat(commentText).isEqualTo("some transfer movement comment")
          }
          with(syncUser) {
            assertThat(username).isEqualTo("SYS")
            assertThat(activeCaseloadId).isEqualTo("MDI")
          }
          assertThat(occurredAt).isCloseTo(LocalDateTime.now().minusDays(1), within(Duration.ofMinutes(5)))
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("nomisBookingId", "12345")
            .withRequestBodyJsonPath("nomisMovementSeq", "3")
            .withRequestBodyJsonPath("dpsTransferMovementId", "$dpsTransferMovementId")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["dpsTransferMovementId"]).isEqualTo("$dpsTransferMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Happy path - unscheduled transfer movement")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathOutboundWithoutSchedule {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferMovementMapping(NOT_FOUND)
        // The schedule ID is null
        nomisApi.stubGetTransferMovementOut(eventId = null)
        mappingApi.stubCreateTransferMovementMapping()
        dpsApi.stubSyncTransferMovement("A1234BC", response = ReferenceId(dpsTransferMovementId))

        sendMessage(transferMovementEvent(inserted = true))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT check schedule mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/123")),
        )
      }

      @Test
      fun `should create DPS transfer movement`() {
        TransferScheduleDpsApiMockServer.getRequestBody<SyncMovementRequest>(
          putRequestedFor(urlPathEqualTo("/sync/transfer-movements/A1234BC")),
        ).apply {
          with(movement) {
            assertThat(dpsId).isNull()
            assertThat(dpsTransferId).isNull()
          }
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement")),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisEventId"]).isEqualTo("null")
            assertThat(it["dpsTransferMovementId"]).isEqualTo("$dpsTransferMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        sendMessage(transferMovementEvent(inserted = true, auditModuleName = "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS transfer movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/transfer-movements/A1234BC")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement")),
        )
      }

      @Test
      fun `should publish ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-movement-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
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

        mappingApi.stubGetTransferMovementMapping()

        sendMessage(transferMovementEvent(inserted = true))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT create DPS transfer movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/transfer-movements/A1234BC")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement")),
        )
      }

      @Test
      fun `should publish ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-movement-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenParentScheduleNotFound {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferMovementMapping(NOT_FOUND)
        nomisApi.stubGetTransferMovementOut()
        mappingApi.stubGetTransferScheduleMapping(123, status = NOT_FOUND)

        sendMessage(transferMovementEvent(inserted = true))
          .also { waitForAnyProcessingToComplete("transfer-scheduler-sync-movement-inserted-awaiting-parent", times = 2) }
      }

      @Test
      fun `should check schedule mapping`() {
        mappingApi.verify(
          count = 2,
          getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/123")),
        )
      }

      @Test
      fun `should NOT create DPS transfer movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/transfer-movements/A1234BC")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement")),
        )
      }

      @Test
      fun `should publish failure telemetry`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("transfer-scheduler-sync-movement-inserted-awaiting-parent"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["error"]).isEqualTo("Expected parent entity not found, retrying")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferMovementMapping(NOT_FOUND)
        nomisApi.stubGetTransferMovementOut(escort = null)
        mappingApi.stubGetTransferScheduleMapping(123, dpsTransferScheduleId)
        dpsApi.stubSyncTransferMovement("A1234BC", response = ReferenceId(dpsTransferMovementId))
        mappingApi.stubCreateTransferMovementMappingConflict(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              existing = TransferMovementMappingDto(
                prisonerNumber = "A1234BC",
                nomisBookingId = 12345,
                nomisMovementSeq = 3,
                dpsTransferMovementId = dpsTransferMovementId,
                mappingType = NOMIS_CREATED,
              ),
              duplicate = TransferMovementMappingDto(
                prisonerNumber = "A1234BC",
                nomisBookingId = 12345,
                nomisMovementSeq = 999,
                dpsTransferMovementId = dpsTransferMovementId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        sendMessage(transferMovementEvent(inserted = true))
          .also { waitForAnyProcessingToComplete("transfer-scheduler-sync-movement-inserted-duplicate") }
      }

      @Test
      fun `should create DPS transfer movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/transfer-movements/A1234BC")),
        )
      }

      @Test
      fun `should try to create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement")),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-movement-inserted-success"),
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
          eq("transfer-scheduler-sync-movement-inserted-duplicate"),
          check {
            assertThat(it["existingOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["existingBookingId"]).isEqualTo("12345")
            assertThat(it["existingMovementSeq"]).isEqualTo("3")
            assertThat(it["existingDpsTransferMovementId"]).isEqualTo("$dpsTransferMovementId")
            assertThat(it["duplicateOffenderNo"]).isEqualTo("A1234BC")
            assertThat(it["duplicateBookingId"]).isEqualTo("12345")
            assertThat(it["duplicateMovementSeq"]).isEqualTo("999")
            assertThat(it["duplicateDpsTransferMovementId"]).isEqualTo("$dpsTransferMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenMappingCreateFailsOnce {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferMovementMapping(NOT_FOUND)
        nomisApi.stubGetTransferMovementOut(escort = null)
        mappingApi.stubGetTransferScheduleMapping(123, dpsTransferScheduleId)
        dpsApi.stubSyncTransferMovement("A1234BC", response = ReferenceId(dpsTransferMovementId))
        mappingApi.stubCreateTransferMovementMappingFailureFollowedBySuccess()

        sendMessage(transferMovementEvent(inserted = true))
          .also { waitForAnyProcessingToComplete("transfer-scheduler-sync-movement-mapping-retry-created") }
      }

      @Test
      fun `should create DPS transfer movement`() {
        dpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/transfer-movements/A1234BC")),
        )
      }

      @Test
      fun `should create mapping on 2nd call`() {
        mappingApi.verify(
          count = 2,
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement"))
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("nomisBookingId", "12345")
            .withRequestBodyJsonPath("nomisMovementSeq", "3")
            .withRequestBodyJsonPath("dpsTransferMovementId", "$dpsTransferMovementId")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["dpsTransferMovementId"]).isEqualTo("$dpsTransferMovementId")
          },
          isNull(),
        )
      }

      @Test
      fun `should publish mapping created telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-movement-mapping-retry-created"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["dpsTransferMovementId"]).isEqualTo("$dpsTransferMovementId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (updated)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class TransferMovementUpdated {
    private val dpsTransferMovementId = UUID.randomUUID()
    private val dpsTransferScheduleId = UUID.randomUUID()

    @Nested
    @DisplayName("Happy path - update scheduled transfer movement")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathOutboundWithSchedule {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferMovementMapping(12345L, 3, dpsTransferMovementId)
        nomisApi.stubGetTransferMovementOut()
        mappingApi.stubGetTransferScheduleMapping(123, dpsTransferScheduleId)
        dpsApi.stubSyncTransferMovement("A1234BC", response = ReferenceId(dpsTransferMovementId))

        sendMessage(transferMovementEvent(inserted = false))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check movement mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement/nomis-id/12345/3")))
      }

      @Test
      fun `should check schedule mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/123")))
      }

      @Test
      fun `should get NOMIS transfer movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/transfer/movement/out/12345/3")))
      }

      @Test
      fun `should update DPS transfer movement passing DPS ID`() {
        TransferScheduleDpsApiMockServer.getRequestBody<SyncMovementRequest>(
          putRequestedFor(urlPathEqualTo("/sync/transfer-movements/A1234BC")),
        ).apply {
          with(movement) {
            assertThat(dpsId).isEqualTo(dpsTransferMovementId)
            assertThat(dpsTransferId).isEqualTo(dpsTransferScheduleId)
            assertThat(offenderBookId).isEqualTo(12345)
            assertThat(movementSeq).isEqualTo(3)
          }
        }
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement")),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["dpsTransferMovementId"]).isEqualTo("$dpsTransferMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Happy path - update unscheduled transfer movement")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathOutboundWithoutSchedule {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferMovementMapping(12345L, 3, dpsTransferMovementId)
        nomisApi.stubGetTransferMovementOut(eventId = null)
        dpsApi.stubSyncTransferMovement("A1234BC", response = ReferenceId(dpsTransferMovementId))

        sendMessage(transferMovementEvent(inserted = false))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT check schedule mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/123")),
        )
      }

      @Test
      fun `should create DPS transfer movement passing DPS ID`() {
        TransferScheduleDpsApiMockServer.getRequestBody<SyncMovementRequest>(
          putRequestedFor(urlPathEqualTo("/sync/transfer-movements/A1234BC")),
        ).apply {
          with(movement) {
            assertThat(dpsId).isEqualTo(dpsTransferMovementId)
            assertThat(dpsTransferId).isNull()
            assertThat(offenderBookId).isEqualTo(12345)
            assertThat(movementSeq).isEqualTo(3)
          }
        }
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement")),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-sync-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisEventId"]).isEqualTo("null")
            assertThat(it["dpsTransferMovementId"]).isEqualTo("$dpsTransferMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenScheduleDoesNotExist {

      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetTransferMovementMapping(12345L, 3, dpsTransferMovementId)
        nomisApi.stubGetTransferMovementOut()
        mappingApi.stubGetTransferScheduleMapping(123, status = NOT_FOUND)

        sendMessage(transferMovementEvent(inserted = false))
          .also { waitForAnyProcessingToComplete("transfer-scheduler-sync-movement-updated-awaiting-parent", times = 2) }
      }

      @Test
      fun `should check schedule mapping`() {
        mappingApi.verify(
          count = 2,
          getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/123")),
        )
      }

      @Test
      fun `should NOT create DPS transfer movement`() {
        dpsApi.verify(
          0,
          putRequestedFor(urlPathEqualTo("/sync/transfer-movements/A1234BC")),
        )
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/movement")),
        )
      }

      @Test
      fun `should publish failure telemetry`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("transfer-scheduler-sync-movement-updated-awaiting-parent"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisEventId"]).isEqualTo("123")
            assertThat(it["error"]).isEqualTo("Expected parent entity not found, retrying")
          },
          isNull(),
        )
      }
    }
  }

  private fun sendMessage(event: String) = awsSqsTransferMovementsOffenderEventsClient.sendMessage(
    transferMovementsQueueOffenderEventsUrl,
    event,
  )

  private fun transferMovementEvent(
    auditModuleName: String = "OCUCANTR",
    movementType: String = "TRN",
    inserted: Boolean = false,
    deleted: Boolean = false,
  ) = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "83354f3f-45cb-5e8e-9266-2e0fa1e91dcc",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-160f3055cc4e04c4105ee85f2ed1fccb",
         "Message" : "{\"eventType\":\"EXTERNAL_MOVEMENT-CHANGED\",\"eventDatetime\":\"2025-09-02T13:24:01\",\"bookingId\":12345,\"offenderIdDisplay\":\"A1234BC\",\"nomisEventType\":\"EXTERNAL_MOVEMENT-CHANGED\",\"movementSeq\":3,\"movementDateTime\":\"2025-09-02T13:23:00\",\"movementType\":\"$movementType\",\"movementReasonCode\":\"OPA\",\"directionCode\":\"OUT\",\"fromAgencyLocationId\":\"NWI\",\"recordInserted\":$inserted,\"recordDeleted\":$deleted,\"auditModuleName\":\"$auditModuleName\"}",
         "Timestamp" : "2025-09-02T12:24:02.004Z",
         "SignatureVersion" : "1",
         "Signature" : "HDyAhgG0o4XV4eJjuLODqeyBfZfsUxLcqVyiwQQIvegES5QnWmfgKwzb+D3az1QgiJBaknq/NIR+C/71O0AFFTSRN3RFOQyLrPZBeynGIyBNzGgeJjPGrZrSBqYegtJKJPDQEQLNepk2Jgqjiu3NgKT0gq5z5mU7G45wqkC81F3/DJUAHb98BmLbWK/cibnaHrvgXW493IbWPLXQENzJ9rDJKekz6sdY6+qHcOg57xdho/Xlb6VFo28/9qoVqA+A2MUBlHBRI1BSK0QVu8duri5DHjE0I2/UG7emlt9vZ6KtxyXz/ZmFVC/nY2OD0OgFJvP7DaAJbgMo/rbGe1JlYQ==",
         "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-6209c161c6221fdf56ec1eb5c821d112.pem",
         "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-160f3055cc4e04c4105ee85f2ed1fccb:217952f4-706e-4451-84d7-e349633331e0",
         "MessageAttributes" : {
           "code" : {"Type":"String","Value":"$movementType-OUT"},
           "publishedAt" : {"Type":"String","Value":"2025-09-02T13:24:02.000596721+01:00"},
           "traceparent" : {"Type":"String","Value":"00-b525e7a9b05de2c11a64ff93a0ef292b-f8f18e48747b11fd-01"},
           "eventType" : {"Type":"String","Value":"EXTERNAL_MOVEMENT-CHANGED"}
         }
        }
    """.trimMargin()
}
