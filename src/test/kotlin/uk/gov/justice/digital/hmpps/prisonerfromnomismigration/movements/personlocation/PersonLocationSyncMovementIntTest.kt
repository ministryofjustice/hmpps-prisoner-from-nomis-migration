package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.countAllMessagesOnDLQQueue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiMockServer.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementIdMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementIdMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.DirectionCode.IN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.DirectionCode.OUT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.ADM
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.CRT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.TAP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.TRN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.CodedReason
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.Prison
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalJourney
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovementRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime
import java.util.UUID

private const val NOMIS_SCHEDULE_ID = 123L

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonLocationSyncMovementIntTest(
  @Autowired private val nomisApi: PersonLocationNomisApiMockServer,
  @Autowired private val mappingApi: PersonLocationMappingApiMockServer,
  @Autowired private val transferScheduleMappingApi: TransferScheduleMappingApiMockServer,
  @Autowired private val courtSchedulerMappingApi: CourtSchedulerMappingApiMockServer,
  @Autowired private val courtSentencingMappingApi: CourtSentencingMappingApiMockServer,
  @Autowired private val tapMappingApi: TapMappingApiMockServer,
) : PersonLocationIntegrationTestBase() {

  private val dpsApi = dpsPersonLocationServer

  override fun resetTelemetryClient() {}

  private fun setUpTestClass() {
    NomisApiExtension.resetAndDisableResetBeforeEach()
    MappingApiExtension.resetAndDisableResetBeforeEach()
    PersonLocationDpsApiExtension.resetAndDisableResetBeforeEach()

    reset(telemetryClient)
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (inserted)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MovementCreated {
    private val dpsExternalMovementId = UUID.randomUUID()
    private val dpsCustodialSeriesId = UUID.randomUUID()
    private val dpsTransferScheduleId = UUID.randomUUID()
    private val dpsCourtAppearanceId = UUID.randomUUID()
    private val dpsSentencingAppearanceId = UUID.randomUUID()
    private val dpsTapOccurrenceId = UUID.randomUUID()

    @Nested
    @DisplayName("Happy path - scheduled transfer movement")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathOutboundTransferWithSchedule {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = transferMovementOut(scheduleId = NOMIS_SCHEDULE_ID))
        transferScheduleMappingApi.stubGetTransferScheduleMapping(NOMIS_SCHEDULE_ID, dpsTransferScheduleId)
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", response = ReferenceId(dpsExternalMovementId))
        mappingApi.stubCreatePersonLocationMovementMapping()

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent())
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check movement mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/person-location/movement/nomis-booking-id/12345/nomis-movement-seq/3")))
      }

      @Test
      fun `should get NOMIS movement`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/person-locations/movement/12345/3")))
      }

      @Test
      fun `should get the parent transfer schedule mapping`() {
        transferScheduleMappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/$NOMIS_SCHEDULE_ID")))
      }

      @Test
      fun `should get the booking mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/person-location/booking/nomis-booking-id/12345")))
      }

      @Test
      fun `should create DPS external movement`() {
        getRequest().apply {
          assertThat(occurredAt).isEqualTo(LocalDateTime.parse("2025-09-02T13:24:00"))
          assertThat(syncUser.username).isEqualTo("SOME_USER")
          with(movement) {
            assertThat(dpsId).isNull()
            assertThat(dpsCustodySeriesId).isEqualTo(dpsCustodialSeriesId)
            assertThat(legacyBookingId).isEqualTo(12345)
            assertThat(legacySequenceNumber).isEqualTo(3)
            assertThat(type).isEqualTo(SyncExternalMovement.Type.DEPARTURE)
            assertThat(occurredAt).isEqualTo(LocalDateTime.parse("2025-09-02T13:23:00"))
            assertThat((reason as CodedReason).code).isEqualTo("NOTR")
            assertThat(reason.description).isEqualTo("Normal Transfer")
            assertThat((from as Prison).code).isEqualTo("BXI")
            assertThat((to as Prison).code).isEqualTo("LEI")
            assertThat(notes).isEqualTo("some transfer movement comment")
            assertThat(journey.type).isEqualTo(SyncExternalJourney.Type.TRANSFER)
            assertThat(journey.scheduleReference).isEqualTo("urn:transfer-scheduler:transfer:$dpsTransferScheduleId")
          }
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/person-location/movement"))
            .withRequestBodyJsonPath("nomisBookingId", "12345")
            .withRequestBodyJsonPath("nomisMovementSeq", "3")
            .withRequestBodyJsonPath("dpsExternalMovementId", "$dpsExternalMovementId")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisEventId"]).isEqualTo("$NOMIS_SCHEDULE_ID")
            assertThat(it["dpsExternalMovementId"]).isEqualTo("$dpsExternalMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Happy path - unscheduled transfer movement")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathOutboundTransferWithoutSchedule {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        // The schedule ID is null
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = transferMovementOut(scheduleId = null))
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", response = ReferenceId(dpsExternalMovementId))
        mappingApi.stubCreatePersonLocationMovementMapping()

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent())
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT check schedule mapping`() {
        transferScheduleMappingApi.verify(
          count = 0,
          getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/$NOMIS_SCHEDULE_ID")),
        )
      }

      @Test
      fun `should create DPS external movement without a schedule reference`() {
        getRequest().apply {
          with(movement) {
            assertThat(dpsId).isNull()
            assertThat(dpsCustodySeriesId).isEqualTo(dpsCustodialSeriesId)
            assertThat(journey.type).isEqualTo(SyncExternalJourney.Type.TRANSFER)
            assertThat(journey.scheduleReference).isNull()
          }
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-movement-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisEventId"]).isEqualTo("null")
            assertThat(it["dpsExternalMovementId"]).isEqualTo("$dpsExternalMovementId")
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

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent(auditModuleName = "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT call DPS or create mapping`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }

      @Test
      fun `should publish ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-movement-inserted-ignored"),
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

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3)

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent())
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT call DPS or create mapping`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }

      @Test
      fun `should publish ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-movement-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["dpsExternalMovementId"]).isNotNull
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

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = transferMovementOut(scheduleId = NOMIS_SCHEDULE_ID))
        transferScheduleMappingApi.stubGetTransferScheduleMapping(NOMIS_SCHEDULE_ID, status = NOT_FOUND)

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent())
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-inserted-awaiting-parent", times = 2) }
      }

      @Test
      fun `should retry the schedule mapping lookup`() {
        transferScheduleMappingApi.verify(2, getRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/nomis-id/$NOMIS_SCHEDULE_ID")))
      }

      @Test
      fun `should NOT call DPS or create mapping`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }

      @Test
      fun `should publish awaiting parent telemetry`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("person-location-sync-movement-inserted-awaiting-parent"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisEventId"]).isEqualTo("$NOMIS_SCHEDULE_ID")
            assertThat(it["error"]).isEqualTo("Expected parent entity not found, retrying")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenParentBookingNotFound {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = transferMovementOut(scheduleId = null))
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, status = NOT_FOUND)

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent())
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-inserted-awaiting-parent", times = 2) }
      }

      @Test
      fun `should retry the booking mapping lookup`() {
        mappingApi.verify(2, getRequestedFor(urlPathEqualTo("/mapping/person-location/booking/nomis-booking-id/12345")))
      }

      @Test
      fun `should NOT call DPS or create mapping`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDpsRejectsRequest {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = transferMovementOut(scheduleId = null))
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", status = BAD_REQUEST)

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent())
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-inserted-error", times = 2) }
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }

      @Test
      fun `should publish error telemetry`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("person-location-sync-movement-inserted-error"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["error"]).contains("400")
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

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = transferMovementOut(scheduleId = null))
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", response = ReferenceId(dpsExternalMovementId))
        mappingApi.stubCreatePersonLocationMovementMapping(
          status = CONFLICT,
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              existing = PersonLocationMovementIdMappingDto(
                nomisBookingId = 12345,
                nomisMovementSeq = 3,
                dpsExternalMovementId = dpsExternalMovementId,
                mappingType = NOMIS_CREATED,
              ),
              duplicate = PersonLocationMovementIdMappingDto(
                nomisBookingId = 12345,
                nomisMovementSeq = 999,
                dpsExternalMovementId = dpsExternalMovementId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent())
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-inserted-duplicate") }
      }

      @Test
      fun `should create DPS external movement`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))
      }

      @Test
      fun `should try to create mapping`() {
        mappingApi.verify(postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(eq("person-location-sync-movement-inserted-success"), any(), isNull())
      }

      @Test
      fun `should publish duplicate telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-movement-inserted-duplicate"),
          check {
            assertThat(it["existingBookingId"]).isEqualTo("12345")
            assertThat(it["existingMovementSeq"]).isEqualTo("3")
            assertThat(it["existingDpsExternalMovementId"]).isEqualTo("$dpsExternalMovementId")
            assertThat(it["duplicateBookingId"]).isEqualTo("12345")
            assertThat(it["duplicateMovementSeq"]).isEqualTo("999")
            assertThat(it["duplicateDpsExternalMovementId"]).isEqualTo("$dpsExternalMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenMappingRetryFindsDuplicate {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = transferMovementOut(scheduleId = null))
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", response = ReferenceId(dpsExternalMovementId))
        mappingApi.stubCreatePersonLocationMovementMappingFailureFollowedByDuplicate(
          DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              existing = PersonLocationMovementIdMappingDto(
                nomisBookingId = 12345,
                nomisMovementSeq = 3,
                dpsExternalMovementId = dpsExternalMovementId,
                mappingType = NOMIS_CREATED,
              ),
              duplicate = PersonLocationMovementIdMappingDto(
                nomisBookingId = 12345,
                nomisMovementSeq = 999,
                dpsExternalMovementId = dpsExternalMovementId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent())
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-inserted-duplicate") }
      }

      @Test
      fun `should publish duplicate telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-movement-inserted-duplicate"),
          check {
            assertThat(it["existingBookingId"]).isEqualTo("12345")
            assertThat(it["duplicateMovementSeq"]).isEqualTo("999")
          },
          isNull(),
        )
      }

      @Test
      fun `should NOT publish mapping created telemetry`() {
        verify(telemetryClient, never()).trackEvent(eq("person-location-sync-movement-mapping-retry-created"), any(), isNull())
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenMappingCreateFailsOnce {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = transferMovementOut(scheduleId = null))
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", response = ReferenceId(dpsExternalMovementId))
        mappingApi.stubCreatePersonLocationMovementMappingFailureFollowedBySuccess()

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent())
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-mapping-retry-created") }
      }

      @Test
      fun `should create DPS external movement once`() {
        dpsApi.verify(1, putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))
      }

      @Test
      fun `should create mapping on 2nd call`() {
        mappingApi.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/person-location/movement"))
            .withRequestBodyJsonPath("nomisBookingId", "12345")
            .withRequestBodyJsonPath("nomisMovementSeq", "3")
            .withRequestBodyJsonPath("dpsExternalMovementId", "$dpsExternalMovementId")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-movement-inserted-success"),
          check { assertThat(it["dpsExternalMovementId"]).isEqualTo("$dpsExternalMovementId") },
          isNull(),
        )
      }

      @Test
      fun `should publish mapping created telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-movement-mapping-retry-created"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["dpsExternalMovementId"]).isEqualTo("$dpsExternalMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Happy path - court movement with a court scheduler schedule")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathCourtScheduleFromScheduler {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = movementOut(CRT, scheduleId = NOMIS_SCHEDULE_ID))
        courtSchedulerMappingApi.stubGetCourtScheduleMapping(NOMIS_SCHEDULE_ID, dpsCourtAppearanceId)
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", response = ReferenceId(dpsExternalMovementId))
        mappingApi.stubCreatePersonLocationMovementMapping()

        personLocationsOffenderEventsQueue.sendMessage(movementEvent("CRT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should create DPS external movement with the schedule reference`() {
        getRequest().movement.apply {
          assertThat(journey.type).isEqualTo(SyncExternalJourney.Type.COURT_APPEARANCE)
          assertThat(journey.scheduleReference).isEqualTo("urn:court-appearance-scheduler:court-appearance:$dpsCourtAppearanceId")
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }
    }

    @Nested
    @DisplayName("Happy path - court movement with a court sentencing schedule")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathCourtScheduleFromSentencing {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = movementOut(CRT, scheduleId = NOMIS_SCHEDULE_ID))
        courtSchedulerMappingApi.stubGetCourtScheduleMapping(NOMIS_SCHEDULE_ID, status = NOT_FOUND)
        courtSentencingMappingApi.stubGetCourtAppearanceByNomisId(NOMIS_SCHEDULE_ID, "$dpsSentencingAppearanceId")
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", response = ReferenceId(dpsExternalMovementId))
        mappingApi.stubCreatePersonLocationMovementMapping()

        personLocationsOffenderEventsQueue.sendMessage(movementEvent("CRT"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should create DPS external movement with the schedule reference`() {
        getRequest().movement.apply {
          assertThat(journey.type).isEqualTo(SyncExternalJourney.Type.COURT_APPEARANCE)
          assertThat(journey.scheduleReference).isEqualTo("urn:remand-and-sentencing:court-appearance:$dpsSentencingAppearanceId")
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }
    }

    @Nested
    @DisplayName("Happy path - TAP movement with a schedule")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathTapSchedule {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = movementOut(TAP, scheduleId = NOMIS_SCHEDULE_ID))
        tapMappingApi.stubGetTapScheduleMapping(NOMIS_SCHEDULE_ID, dpsTapOccurrenceId)
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", response = ReferenceId(dpsExternalMovementId))
        mappingApi.stubCreatePersonLocationMovementMapping()

        personLocationsOffenderEventsQueue.sendMessage(movementEvent("TAP"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should create DPS external movement with the schedule reference`() {
        getRequest().movement.apply {
          assertThat(journey.type).isEqualTo(SyncExternalJourney.Type.TEMPORARY_ABSENCE)
          assertThat(journey.scheduleReference).isEqualTo("urn:temporary-absence-scheduler:temporary-absence:$dpsTapOccurrenceId")
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }
    }

    @Nested
    @DisplayName("Happy path - admission has no schedule")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathAdmission {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = movementOut(ADM, scheduleId = null, directionCode = IN))
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", response = ReferenceId(dpsExternalMovementId))
        mappingApi.stubCreatePersonLocationMovementMapping()

        personLocationsOffenderEventsQueue.sendMessage(movementEvent("ADM", "IN"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should create DPS arrival without a schedule reference`() {
        getRequest().movement.apply {
          assertThat(type).isEqualTo(SyncExternalMovement.Type.ARRIVAL)
          assertThat(journey.type).isEqualTo(SyncExternalJourney.Type.ADMISSION)
          assertThat(journey.scheduleReference).isNull()
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenCourtScheduleNotFound {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = movementOut(CRT, scheduleId = NOMIS_SCHEDULE_ID))
        courtSchedulerMappingApi.stubGetCourtScheduleMapping(NOMIS_SCHEDULE_ID, status = NOT_FOUND)
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))

        personLocationsOffenderEventsQueue.sendMessage(movementEvent("CRT"))
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-inserted-awaiting-parent", times = 2) }
      }

      @Test
      fun `should retry the schedule mapping lookup`() {
        courtSchedulerMappingApi.verify(2, getRequestedFor(urlPathEqualTo("/mapping/court-scheduler/schedule/nomis-id/$NOMIS_SCHEDULE_ID")))
        courtSentencingMappingApi.verify(2, getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/$NOMIS_SCHEDULE_ID")))
      }

      @Test
      fun `should NOT call DPS or create mapping`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenTapScheduleNotFound {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = movementOut(TAP, scheduleId = NOMIS_SCHEDULE_ID))
        tapMappingApi.stubGetTapScheduleMapping(NOMIS_SCHEDULE_ID, status = NOT_FOUND)
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))

        personLocationsOffenderEventsQueue.sendMessage(movementEvent("TAP"))
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-inserted-awaiting-parent", times = 2) }
      }

      @Test
      fun `should retry the schedule mapping lookup`() {
        tapMappingApi.verify(2, getRequestedFor(urlPathEqualTo("/mapping/taps/schedule/nomis-id/$NOMIS_SCHEDULE_ID")))
      }

      @Test
      fun `should NOT call DPS or create mapping`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }
    }
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (updated)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MovementUpdated {
    private val dpsExternalMovementId = UUID.randomUUID()
    private val dpsCustodialSeriesId = UUID.randomUUID()
    private val dpsTransferScheduleId = UUID.randomUUID()

    @Nested
    @DisplayName("Happy path - scheduled transfer movement")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathWithSchedule {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, response = personLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, dpsExternalMovementId = dpsExternalMovementId))
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = transferMovementOut(scheduleId = NOMIS_SCHEDULE_ID))
        transferScheduleMappingApi.stubGetTransferScheduleMapping(NOMIS_SCHEDULE_ID, dpsTransferScheduleId)
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", response = ReferenceId(dpsExternalMovementId))

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent(recordInserted = false))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should update DPS external movement using the mapped DPS id`() {
        getRequest().apply {
          assertThat(syncUser.username).isEqualTo("SOME_USER")
          with(movement) {
            assertThat(dpsId).isEqualTo(dpsExternalMovementId)
            assertThat(dpsCustodySeriesId).isEqualTo(dpsCustodialSeriesId)
            assertThat(legacyBookingId).isEqualTo(12345)
            assertThat(legacySequenceNumber).isEqualTo(3)
            assertThat(journey.scheduleReference).isEqualTo("urn:transfer-scheduler:transfer:$dpsTransferScheduleId")
          }
        }
      }

      @Test
      fun `should NOT create a mapping`() {
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/movement")))
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-movement-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["nomisEventId"]).isEqualTo("$NOMIS_SCHEDULE_ID")
            assertThat(it["dpsExternalMovementId"]).isEqualTo("$dpsExternalMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Happy path - admission has no schedule")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathWithoutSchedule {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, response = personLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, dpsExternalMovementId = dpsExternalMovementId))
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = movementOut(ADM, scheduleId = null, directionCode = IN))
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", response = ReferenceId(dpsExternalMovementId))

        personLocationsOffenderEventsQueue.sendMessage(movementEvent("ADM", "IN", recordInserted = false))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should update DPS arrival without a schedule reference`() {
        getRequest().movement.apply {
          assertThat(dpsId).isEqualTo(dpsExternalMovementId)
          assertThat(type).isEqualTo(SyncExternalMovement.Type.ARRIVAL)
          assertThat(journey.scheduleReference).isNull()
        }
      }
    }

    @Nested
    @DisplayName("Ignored when raised by DPS")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDpsSyncAuditModule {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent(auditModuleName = "DPS_SYNCHRONISATION", recordInserted = false))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT call DPS`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))
      }

      @Test
      fun `should publish ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-movement-updated-ignored"),
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
    @DisplayName("Update overtakes the create")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenNoMapping {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent(recordInserted = false))
          .also { await untilAsserted { assertThat(personLocationsOffenderEventsQueue.countAllMessagesOnDLQQueue()).isEqualTo(1) } }
      }

      @Test
      fun `should NOT call DPS`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenScheduleNotFound {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, response = personLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, dpsExternalMovementId = dpsExternalMovementId))
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = movementOut(TAP, scheduleId = NOMIS_SCHEDULE_ID))
        tapMappingApi.stubGetTapScheduleMapping(NOMIS_SCHEDULE_ID, status = NOT_FOUND)
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))

        personLocationsOffenderEventsQueue.sendMessage(movementEvent("TAP", recordInserted = false))
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-updated-awaiting-parent", times = 2) }
      }

      @Test
      fun `should NOT call DPS`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))
      }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDpsRejectsRequest {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, response = personLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, dpsExternalMovementId = dpsExternalMovementId))
        nomisApi.stubGetPersonLocationMovement(bookingId = 12345, movementSeq = 3, response = transferMovementOut(scheduleId = null))
        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        dpsApi.stubSyncExternalMovement("A1234BC", status = BAD_REQUEST)

        personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent(recordInserted = false))
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-updated-error", times = 2) }
      }

      @Test
      fun `should publish error telemetry`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("person-location-sync-movement-updated-error"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["dpsExternalMovementId"]).isEqualTo("$dpsExternalMovementId")
            assertThat(it["error"]).contains("400")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (deleted)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MovementDeleted {
    private val dpsExternalMovementId = UUID.randomUUID()

    private fun deletedEvent(auditModuleName: String = "OCUCANTR") = transferMovementEvent(auditModuleName = auditModuleName, recordInserted = false, recordDeleted = true)

    @Nested
    @DisplayName("Happy path")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, response = personLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, dpsExternalMovementId = dpsExternalMovementId))
        dpsApi.stubDeleteExternalMovement(dpsExternalMovementId)
        mappingApi.stubDeletePersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3)

        personLocationsOffenderEventsQueue.sendMessage(deletedEvent())
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should delete the DPS external movement`() {
        dpsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/external-movements/$dpsExternalMovementId")))
      }

      @Test
      fun `should delete the mapping`() {
        mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/person-location/movement/nomis-booking-id/12345/nomis-movement-seq/3")))
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-movement-deleted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["movementSeq"]).isEqualTo("3")
            assertThat(it["dpsExternalMovementId"]).isEqualTo("$dpsExternalMovementId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Ignored when raised by DPS")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDpsSyncAuditModule {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        personLocationsOffenderEventsQueue.sendMessage(deletedEvent(auditModuleName = "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-deleted-ignored") }
      }

      @Test
      fun `should NOT call DPS or delete mapping`() {
        dpsApi.verify(0, deleteRequestedFor(anyUrl()))
        mappingApi.verify(0, deleteRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("No mapping")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenNoMapping {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, status = NOT_FOUND)

        personLocationsOffenderEventsQueue.sendMessage(deletedEvent())
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-deleted-ignored") }
      }

      @Test
      fun `should NOT call DPS or delete mapping`() {
        dpsApi.verify(0, deleteRequestedFor(anyUrl()))
        mappingApi.verify(0, deleteRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("DPS delete fails")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDpsFails {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, response = personLocationMovementMapping(nomisBookingId = 12345, nomisMovementSeq = 3, dpsExternalMovementId = dpsExternalMovementId))
        dpsApi.stubDeleteExternalMovement(dpsExternalMovementId, status = INTERNAL_SERVER_ERROR)

        personLocationsOffenderEventsQueue.sendMessage(deletedEvent())
          .also { waitForAnyProcessingToComplete("person-location-sync-movement-deleted-error", times = 2) }
      }

      @Test
      fun `should leave the mapping in place`() {
        mappingApi.verify(0, deleteRequestedFor(anyUrl()))
      }

      @Test
      fun `should publish error telemetry`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("person-location-sync-movement-deleted-error"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["dpsExternalMovementId"]).isEqualTo("$dpsExternalMovementId")
            assertThat(it["error"]).contains("500")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (edited in NOMIS prisoner movements screen)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class MovementsEdited {
    @BeforeEach
    fun setUp() {
      setUpTestClass()

      nomisApi.stubGetOffenderPersonLocations("A1234BC")
      transferScheduleMappingApi.stubGetTransferSchedulerPrisonerMappingIds(prisonerNumber = "A1234BC", nomisEventId = 54321L, dpsTransferScheduleId = UUID.randomUUID())
      mappingApi.stubGetPersonLocationMappings()
      dpsApi.stubResyncPrisoner(personIdentifier = "A1234BC")
      mappingApi.stubCreatePersonLocationMappings()

      personLocationsOffenderEventsQueue.sendMessage(transferMovementEvent(auditModuleName = "OUMEEMOV", recordInserted = false))
        .also { waitForAnyProcessingToComplete("person-location-migration-entity-migrated") }
    }

    @Test
    fun `should resync the person in DPS rather than sync the movement`() {
      dpsApi.verify(putRequestedFor(urlPathEqualTo("/resync/external-movements/A1234BC")))
      dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))
    }

    @Test
    fun `should update mappings`() {
      mappingApi.verify(putRequestedFor(urlPathEqualTo("/mapping/person-location/migrate")))
    }
  }

  private fun getRequest() = getRequestBody<SyncExternalMovementRequest>(putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))

  private fun transferMovementOut(scheduleId: Long?) = movementOut(TRN, scheduleId)

  private fun movementOut(
    movementType: PersonLocationMovement.MovementType,
    scheduleId: Long?,
    directionCode: PersonLocationMovement.DirectionCode = OUT,
  ) = PersonLocationMovement(
    bookingId = 12345,
    sequence = 3,
    movementType = movementType,
    directionCode = directionCode,
    movementTime = LocalDateTime.parse("2025-09-02T13:23:00"),
    movementReason = CodeDescription(code = "NOTR", description = "Normal Transfer"),
    audit = NomisAudit(createDatetime = LocalDateTime.parse("2025-09-02T13:24:00"), createUsername = "SOME_USER"),
    from = MovementLocation(type = MovementLocation.Type.PRISON, description = "Brixton (HMP)", code = "BXI").takeIf { directionCode == OUT },
    to = MovementLocation(type = MovementLocation.Type.PRISON, description = "Leeds (HMP)", code = "LEI"),
    scheduleId = scheduleId,
    commentText = "some transfer movement comment",
  )

  private fun movementEvent(movementType: String, directionCode: String = "OUT", recordInserted: Boolean = true, recordDeleted: Boolean = false) = transferMovementEvent(movementType = movementType, directionCode = directionCode, recordInserted = recordInserted, recordDeleted = recordDeleted)

  private fun transferMovementEvent(prisonerNumber: String = "A1234BC", auditModuleName: String = "OCUCANTR", movementType: String = "TRN", directionCode: String = "OUT", recordInserted: Boolean = true, recordDeleted: Boolean = false) = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "83354f3f-45cb-5e8e-9266-2e0fa1e91dcc",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-160f3055cc4e04c4105ee85f2ed1fccb",
         "Message" : "{\"eventType\":\"EXTERNAL_MOVEMENT-CHANGED\",\"eventDatetime\":\"2025-09-02T13:24:01\",\"bookingId\":12345,\"offenderIdDisplay\":\"$prisonerNumber\",\"nomisEventType\":\"EXTERNAL_MOVEMENT-CHANGED\",\"movementSeq\":3,\"movementDateTime\":\"2025-09-02T13:23:00\",\"movementType\":\"$movementType\",\"movementReasonCode\":\"NOTR\",\"directionCode\":\"$directionCode\",\"fromAgencyLocationId\":\"BXI\",\"recordInserted\":$recordInserted,\"recordDeleted\":$recordDeleted,\"auditModuleName\":\"$auditModuleName\"}",
         "Timestamp" : "2025-09-02T12:24:02.004Z",
         "MessageAttributes" : {
           "eventType" : {"Type":"String","Value":"EXTERNAL_MOVEMENT-CHANGED"}
         }
        }
    """.trimMargin()
}
