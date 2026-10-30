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
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiMockServer.Companion.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingIdMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationBookingIdMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationBooking
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeries
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeriesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersonLocationSyncBookingIntTest(
  @Autowired private val nomisApi: PersonLocationNomisApiMockServer,
  @Autowired private val mappingApi: PersonLocationMappingApiMockServer,
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
  @DisplayName("BOOKING_STATUS-INSERTED")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class BookingInserted {
    private val dpsCustodialSeriesId = UUID.randomUUID()

    @Nested
    @DisplayName("Happy path")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationBooking(
          offenderNo = "A1234BC",
          bookingId = 12345,
          response = personLocationBooking(bookingId = 12345, bookingBeginTime = LocalDateTime.parse("2025-09-01T10:00:00")),
        )
        dpsApi.stubSyncCustodialSeries("A1234BC", response = ReferenceId(dpsCustodialSeriesId))
        mappingApi.stubCreatePersonLocationBookingMapping()

        personLocationsOffenderEventsQueue.sendMessage(bookingStatusEvent("BOOKING_STATUS-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check booking mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/person-location/booking/nomis-booking-id/12345")))
      }

      @Test
      fun `should get NOMIS booking`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/movements/A1234BC/person-locations/booking/12345")))
      }

      @Test
      fun `should create DPS custodial series`() {
        getRequestBody<SyncCustodialSeriesRequest>(putRequestedFor(urlPathEqualTo("/sync/custodial-series/A1234BC"))).apply {
          assertThat(occurredAt).isEqualTo(LocalDateTime.parse("2025-09-01T10:00:00"))
          assertThat(syncUser.username).isEqualTo("SYS")
          with(custodialSeries) {
            assertThat(dpsId).isNull()
            assertThat(status).isEqualTo(SyncCustodialSeries.Status.OPEN)
            assertThat(isActive).isTrue()
            assertThat(openedAt).isEqualTo(LocalDateTime.parse("2025-09-01T10:00:00"))
            assertThat(closedAt).isNull()
            assertThat(legacyBookingId).isEqualTo(12345)
            assertThat(legacyBookingReference).isEqualTo("B12345")
          }
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/person-location/booking"))
            .withRequestBodyJsonPath("nomisBookingId", "12345")
            .withRequestBodyJsonPath("dpsCustodialSeriesId", "$dpsCustodialSeriesId")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-booking-inserted-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["bookingSeq"]).isEqualTo("1")
            assertThat(it["dpsCustodialSeriesId"]).isEqualTo("$dpsCustodialSeriesId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Ignored when raised by DPS")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenCreatedInDps {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        personLocationsOffenderEventsQueue.sendMessage(bookingStatusEvent("BOOKING_STATUS-INSERTED", auditModuleName = "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT call DPS or create mapping`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/custodial-series/A1234BC")))
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/booking")))
      }

      @Test
      fun `should publish ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-booking-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["bookingSeq"]).isEqualTo("1")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Already created")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenAlreadyCreated {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))

        personLocationsOffenderEventsQueue.sendMessage(bookingStatusEvent("BOOKING_STATUS-INSERTED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT call DPS or create mapping`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/custodial-series/A1234BC")))
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/booking")))
      }

      @Test
      fun `should publish ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-booking-inserted-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["dpsCustodialSeriesId"]).isEqualTo("$dpsCustodialSeriesId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("DPS rejects the request")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDpsRejectsRequest {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, status = NOT_FOUND)
        nomisApi.stubGetPersonLocationBooking(offenderNo = "A1234BC", bookingId = 12345)
        dpsApi.stubSyncCustodialSeries("A1234BC", status = BAD_REQUEST)

        personLocationsOffenderEventsQueue.sendMessage(bookingStatusEvent("BOOKING_STATUS-INSERTED"))
          .also { waitForAnyProcessingToComplete("person-location-sync-booking-inserted-error", times = 2) }
      }

      @Test
      fun `should NOT create mapping`() {
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/booking")))
      }

      @Test
      fun `should publish error telemetry`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("person-location-sync-booking-inserted-error"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["error"]).contains("400")
          },
          isNull(),
        )
      }
    }

    private fun duplicateMappingError() = DuplicateMappingErrorResponse(
      moreInfo = DuplicateErrorContentObject(
        existing = PersonLocationBookingIdMappingDto(
          nomisBookingId = 12345,
          dpsCustodialSeriesId = dpsCustodialSeriesId,
          mappingType = NOMIS_CREATED,
        ),
        duplicate = PersonLocationBookingIdMappingDto(
          nomisBookingId = 999,
          dpsCustodialSeriesId = dpsCustodialSeriesId,
          mappingType = NOMIS_CREATED,
        ),
      ),
      errorCode = 1409,
      status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
      userMessage = "Duplicate mapping",
    )

    private fun stubSuccessfulSyncBeforeMapping() {
      mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, status = NOT_FOUND)
      nomisApi.stubGetPersonLocationBooking(offenderNo = "A1234BC", bookingId = 12345)
      dpsApi.stubSyncCustodialSeries("A1234BC", response = ReferenceId(dpsCustodialSeriesId))
    }

    @Nested
    @DisplayName("Duplicate mapping")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDuplicateMapping {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        stubSuccessfulSyncBeforeMapping()
        mappingApi.stubCreatePersonLocationBookingMapping(status = CONFLICT, error = duplicateMappingError())

        personLocationsOffenderEventsQueue.sendMessage(bookingStatusEvent("BOOKING_STATUS-INSERTED"))
          .also { waitForAnyProcessingToComplete("person-location-sync-booking-inserted-duplicate") }
      }

      @Test
      fun `should create DPS custodial series`() {
        dpsApi.verify(putRequestedFor(urlPathEqualTo("/sync/custodial-series/A1234BC")))
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(eq("person-location-sync-booking-inserted-success"), any(), isNull())
      }

      @Test
      fun `should publish duplicate telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-booking-inserted-duplicate"),
          check {
            assertThat(it["existingBookingId"]).isEqualTo("12345")
            assertThat(it["existingDpsCustodialSeriesId"]).isEqualTo("$dpsCustodialSeriesId")
            assertThat(it["duplicateBookingId"]).isEqualTo("999")
            assertThat(it["duplicateDpsCustodialSeriesId"]).isEqualTo("$dpsCustodialSeriesId")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Mapping retry finds a duplicate")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenMappingRetryFindsDuplicate {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        stubSuccessfulSyncBeforeMapping()
        mappingApi.stubCreatePersonLocationBookingMappingFailureFollowedByDuplicate(duplicateMappingError())

        personLocationsOffenderEventsQueue.sendMessage(bookingStatusEvent("BOOKING_STATUS-INSERTED"))
          .also { waitForAnyProcessingToComplete("person-location-sync-booking-inserted-duplicate") }
      }

      @Test
      fun `should publish duplicate telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-booking-inserted-duplicate"),
          check { assertThat(it["duplicateBookingId"]).isEqualTo("999") },
          isNull(),
        )
      }

      @Test
      fun `should NOT publish mapping created telemetry`() {
        verify(telemetryClient, never()).trackEvent(eq("person-location-sync-booking-mapping-retry-created"), any(), isNull())
      }
    }

    @Nested
    @DisplayName("Mapping create fails once")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenMappingCreateFailsOnce {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        stubSuccessfulSyncBeforeMapping()
        mappingApi.stubCreatePersonLocationBookingMappingFailureFollowedBySuccess()

        personLocationsOffenderEventsQueue.sendMessage(bookingStatusEvent("BOOKING_STATUS-INSERTED"))
          .also { waitForAnyProcessingToComplete("person-location-sync-booking-mapping-retry-created") }
      }

      @Test
      fun `should create DPS custodial series once`() {
        dpsApi.verify(1, putRequestedFor(urlPathEqualTo("/sync/custodial-series/A1234BC")))
      }

      @Test
      fun `should create mapping on 2nd call`() {
        mappingApi.verify(
          2,
          postRequestedFor(urlPathEqualTo("/mapping/person-location/booking"))
            .withRequestBodyJsonPath("nomisBookingId", "12345")
            .withRequestBodyJsonPath("dpsCustodialSeriesId", "$dpsCustodialSeriesId")
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
        )
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-booking-inserted-success"),
          check { assertThat(it["dpsCustodialSeriesId"]).isEqualTo("$dpsCustodialSeriesId") },
          isNull(),
        )
      }

      @Test
      fun `should publish mapping created telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-booking-mapping-retry-created"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["dpsCustodialSeriesId"]).isEqualTo("$dpsCustodialSeriesId")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("BOOKING_STATUS-UPDATED")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class BookingUpdated {
    private val dpsCustodialSeriesId = UUID.randomUUID()

    @Nested
    @DisplayName("Happy path - booking closed")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPathBookingClosed {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        nomisApi.stubGetPersonLocationBooking(
          offenderNo = "A1234BC",
          bookingId = 12345,
          response = personLocationBooking(bookingId = 12345).copy(
            bookingStatus = PersonLocationBooking.BookingStatus.CLOSED,
            activeBooking = false,
            latestBooking = false,
            bookingEndTime = LocalDateTime.parse("2025-10-01T11:00:00"),
            audit = NomisAudit(
              createDatetime = LocalDateTime.parse("2025-09-01T10:00:00"),
              createUsername = "PRISONER_MANAGER_API",
              modifyDatetime = LocalDateTime.parse("2025-10-01T11:00:00"),
              modifyUserId = "SOME_USER",
            ),
          ),
        )
        dpsApi.stubSyncCustodialSeries("A1234BC", response = ReferenceId(dpsCustodialSeriesId))

        personLocationsOffenderEventsQueue.sendMessage(bookingStatusEvent("BOOKING_STATUS-UPDATED"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should check booking mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/person-location/booking/nomis-booking-id/12345")))
      }

      @Test
      fun `should update DPS custodial series using the mapped DPS id`() {
        getRequestBody<SyncCustodialSeriesRequest>(putRequestedFor(urlPathEqualTo("/sync/custodial-series/A1234BC"))).apply {
          assertThat(occurredAt).isEqualTo(LocalDateTime.parse("2025-10-01T11:00:00"))
          assertThat(syncUser.username).isEqualTo("SOME_USER")
          with(custodialSeries) {
            assertThat(dpsId).isEqualTo(dpsCustodialSeriesId)
            assertThat(status).isEqualTo(SyncCustodialSeries.Status.CLOSED)
            assertThat(isActive).isFalse()
            assertThat(closedAt).isEqualTo(LocalDateTime.parse("2025-10-01T11:00:00"))
            assertThat(legacyBookingId).isEqualTo(12345)
          }
        }
      }

      @Test
      fun `should NOT create a mapping`() {
        mappingApi.verify(0, postRequestedFor(urlPathEqualTo("/mapping/person-location/booking")))
      }

      @Test
      fun `should publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-booking-updated-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["bookingSeq"]).isEqualTo("1")
            assertThat(it["oldBookingSeq"]).isEqualTo("2")
            assertThat(it["dpsCustodialSeriesId"]).isEqualTo("$dpsCustodialSeriesId")
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

        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, status = NOT_FOUND)

        personLocationsOffenderEventsQueue.sendMessage(bookingStatusEvent("BOOKING_STATUS-UPDATED"))
          .also { waitForAnyProcessingToComplete("person-location-sync-booking-updated-error", times = 2) }
      }

      @Test
      fun `should NOT call DPS`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/custodial-series/A1234BC")))
      }

      @Test
      fun `should publish error telemetry`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("person-location-sync-booking-updated-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["error"]).contains("No person location booking mapping found for booking 12345")
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

        personLocationsOffenderEventsQueue.sendMessage(bookingStatusEvent("BOOKING_STATUS-UPDATED", auditModuleName = "DPS_SYNCHRONISATION"))
          .also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `should NOT call DPS`() {
        dpsApi.verify(0, putRequestedFor(urlPathEqualTo("/sync/custodial-series/A1234BC")))
      }

      @Test
      fun `should publish ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("person-location-sync-booking-updated-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["bookingSeq"]).isEqualTo("1")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("DPS rejects the request")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class WhenDpsRejectsRequest {
      @BeforeEach
      fun setUp() {
        setUpTestClass()

        mappingApi.stubGetPersonLocationBookingMapping(nomisBookingId = 12345, response = personLocationBookingMapping(nomisBookingId = 12345, dpsCustodialSeriesId = dpsCustodialSeriesId))
        nomisApi.stubGetPersonLocationBooking(offenderNo = "A1234BC", bookingId = 12345)
        dpsApi.stubSyncCustodialSeries("A1234BC", status = BAD_REQUEST)

        personLocationsOffenderEventsQueue.sendMessage(bookingStatusEvent("BOOKING_STATUS-UPDATED"))
          .also { waitForAnyProcessingToComplete("person-location-sync-booking-updated-error", times = 2) }
      }

      @Test
      fun `should publish error telemetry`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("person-location-sync-booking-updated-error"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12345")
            assertThat(it["dpsCustodialSeriesId"]).isEqualTo("$dpsCustodialSeriesId")
            assertThat(it["error"]).contains("400")
          },
          isNull(),
        )
      }
    }
  }

  private fun bookingStatusEvent(
    eventType: String,
    prisonerNumber: String = "A1234BC",
    auditModuleName: String = "OIDADMIS",
  ) = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "83354f3f-45cb-5e8e-9266-2e0fa1e91dcc",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-160f3055cc4e04c4105ee85f2ed1fccb",
         "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2025-09-01T10:00:01\",\"bookingId\":12345,\"offenderIdDisplay\":\"$prisonerNumber\",\"nomisEventType\":\"$eventType\",\"bookingSeq\":1,\"oldBookingSeq\":2,\"auditModuleName\":\"$auditModuleName\"}",
         "Timestamp" : "2025-09-01T09:00:02.004Z",
         "MessageAttributes" : {
           "eventType" : {"Type":"String","Value":"$eventType"}
         }
        }
    """.trimMargin()
}
