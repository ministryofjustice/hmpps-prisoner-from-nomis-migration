package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
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
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEventMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.dpsCourtSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiMockServer.Companion.referenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourtSchedulerSyncMovementIntTest(
  @Autowired private val nomisApi: CourtSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: CourtSchedulerMappingApiMockServer,
) : SqsIntegrationTestBase() {

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
    inner class HappyPath {
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
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court/movement/nomis-id/12345/3")))
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
          assertThat(movement.movementDate).isEqualTo(yesterday.toLocalDate())
          assertThat(LocalDateTime.parse(movement.movementTime)).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
          assertThat(movement.movementReasonCode).isEqualTo("CRT")
          assertThat(movement.directionCode).isEqualTo("OUT")
          assertThat(movement.fromAgencyId).isEqualTo("BXI")
          assertThat(movement.toAgencyId).isEqualTo("LEEDMC")
          assertThat(user.username).isEqualTo("USER")
          assertThat(user.activeCaseloadId).isEqualTo("MDI")
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/court/movement"))
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
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (updated)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class CourtMovementUpdated {
    @BeforeAll
    fun setUpTestClass() {
      reset(telemetryClient)
      reset(courtSchedulerSyncMovementService)
    }

    @Test
    fun `will call service`() = runTest {
      sendMessage(courtMovementEvent(inserted = false, deleted = false))
        .also { waitForAnyProcessingToComplete() }

      verify(courtSchedulerSyncMovementService).courtMovementUpdated(any())
    }
  }

  @Nested
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (deleted)")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class CourtMovementDeleted {
    @BeforeAll
    fun setUpTestClass() {
      reset(telemetryClient)
      reset(courtSchedulerSyncMovementService)
    }

    @Test
    fun `will call service`() = runTest {
      sendMessage(courtMovementEvent(deleted = true))
        .also { waitForAnyProcessingToComplete() }

      verify(courtSchedulerSyncMovementService).courtMovementDeleted(any())
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @DisplayName("EXTERNAL_MOVEMENT-CHANGED (IN, inserted)")
  inner class CourtMovementInCreated {

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
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
        mappingApi.verify(pattern = getRequestedFor(urlPathEqualTo("/mapping/court/movement/nomis-id/12345/3")))
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
          assertThat(movement.movementDate).isEqualTo(yesterday.toLocalDate())
          assertThat(LocalDateTime.parse(movement.movementTime)).isCloseTo(yesterday, within(5, ChronoUnit.MINUTES))
          assertThat(movement.movementReasonCode).isEqualTo("CRT")
          assertThat(movement.directionCode).isEqualTo("IN")
          assertThat(movement.fromAgencyId).isEqualTo("LEEDMC")
          assertThat(movement.toAgencyId).isEqualTo("BXI")
          assertThat(user.username).isEqualTo("USER")
          assertThat(user.activeCaseloadId).isEqualTo("MDI")
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/court/movement"))
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
