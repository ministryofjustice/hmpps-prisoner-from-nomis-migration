package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiMockServer.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.MovementLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement
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
  }

  private fun getRequest() = getRequestBody<SyncExternalMovementRequest>(putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC")))

  private fun transferMovementOut(scheduleId: Long?) = PersonLocationMovement(
    bookingId = 12345,
    sequence = 3,
    movementType = PersonLocationMovement.MovementType.TRN,
    directionCode = PersonLocationMovement.DirectionCode.OUT,
    movementTime = LocalDateTime.parse("2025-09-02T13:23:00"),
    movementReason = CodeDescription(code = "NOTR", description = "Normal Transfer"),
    audit = NomisAudit(createDatetime = LocalDateTime.parse("2025-09-02T13:24:00"), createUsername = "SOME_USER"),
    from = MovementLocation(type = MovementLocation.Type.PRISON, description = "Brixton (HMP)", code = "BXI"),
    to = MovementLocation(type = MovementLocation.Type.PRISON, description = "Leeds (HMP)", code = "LEI"),
    scheduleId = scheduleId,
    commentText = "some transfer movement comment",
  )

  private fun transferMovementEvent(prisonerNumber: String = "A1234BC") = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "83354f3f-45cb-5e8e-9266-2e0fa1e91dcc",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-160f3055cc4e04c4105ee85f2ed1fccb",
         "Message" : "{\"eventType\":\"EXTERNAL_MOVEMENT-CHANGED\",\"eventDatetime\":\"2025-09-02T13:24:01\",\"bookingId\":12345,\"offenderIdDisplay\":\"$prisonerNumber\",\"nomisEventType\":\"EXTERNAL_MOVEMENT-CHANGED\",\"movementSeq\":3,\"movementDateTime\":\"2025-09-02T13:23:00\",\"movementType\":\"TRN\",\"movementReasonCode\":\"NOTR\",\"directionCode\":\"OUT\",\"fromAgencyLocationId\":\"BXI\",\"recordInserted\":true,\"recordDeleted\":false,\"auditModuleName\":\"OCUCANTR\"}",
         "Timestamp" : "2025-09-02T12:24:02.004Z",
         "MessageAttributes" : {
           "eventType" : {"Type":"String","Value":"EXTERNAL_MOVEMENT-CHANGED"}
         }
        }
    """.trimMargin()
}
