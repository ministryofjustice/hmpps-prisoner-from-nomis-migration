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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension.Companion.dpsCourtSchedulerServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiMockServer.Companion.referenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CourtSchedulerScheduleIntTest(
  @Autowired private val nomisApi: CourtSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: CourtSchedulerMappingApiMockServer,
  @Autowired private val sentencingMappingApi: CourtSentencingMappingApiMockServer,
) : SqsIntegrationTestBase() {

  private val dpsApi = dpsCourtSchedulerServer

  override fun resetTelemetryClient() {}

  @Nested
  @DisplayName("COURT_EVENTS-INSERTED")
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  inner class CourtScheduleCreated {

    @BeforeAll
    fun setUpTestClass() {
      NomisApiExtension.resetAndDisableResetBeforeEach()
      MappingApiExtension.resetAndDisableResetBeforeEach()
      CourtSchedulerDpsApiExtension.resetAndDisableResetBeforeEach()
      CourtSentencingDpsApiExtension.resetAndDisableResetBeforeEach()

      reset(telemetryClient)
    }

    private val yesterday: LocalDateTime = LocalDateTime.now().minusDays(1)

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class HappyPath {
      private val dpsCourtAppearanceId: UUID = UUID.randomUUID()
      private val dpsSentencingCourtAppearanceId: UUID = UUID.randomUUID()

      @BeforeAll
      fun setUp() {
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
        mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/court/schedule/nomis-id/123")))
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
        }
      }

      @Test
      fun `should create mapping`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/court/schedule"))
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
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @DisplayName("COURT_EVENTS-UPDATED")
  inner class CourtScheduleUpdated {

    @BeforeAll
    fun setUpTestClass() {
      reset(telemetryClient)
      reset(courtSchedulerSyncScheduleService)
    }

    @Test
    fun `will call service`() = runTest {
      sendMessage(courtScheduleEvent("COURT_EVENTS-UPDATED"))
        .also { waitForAnyProcessingToComplete() }

      verify(courtSchedulerSyncScheduleService).courtScheduleUpdated(any())
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @DisplayName("COURT_EVENTS-DELETED")
  inner class CourtScheduleDeleted {

    @BeforeAll
    fun setUpTestClass() {
      reset(telemetryClient)
      reset(courtSchedulerSyncScheduleService)
    }

    @Test
    fun `will call service`() = runTest {
      sendMessage(courtScheduleEvent("COURT_EVENTS-DELETED"))
        .also { waitForAnyProcessingToComplete() }

      verify(courtSchedulerSyncScheduleService).courtScheduleDeleted(any())
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
    direction: String = "OUT",
    eventId: Long = 123,
  ) = // language=JSON
    """{
         "Type" : "Notification",
         "MessageId" : "57126174-e2d7-518f-914e-0056a63363b0",
         "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
         "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2026-05-05T09:39:57\",\"nomisEventType\":\"$nomisEventType\",\"bookingId\":12345,\"offenderIdDisplay\":\"A1234BC\",\"auditModuleName\":\"$auditModuleName\",\"eventId\":$eventId,\"caseId\":101112,\"isBreachHearing\":false}",
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
