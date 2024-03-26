package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension.Companion.dpsCourtSentencingServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import java.util.AbstractMap.SimpleEntry

private const val NOMIS_COURT_CASE_ID = 1234L
private const val DPS_COURT_CASE_ID = "cc1"
private const val OFFENDER_ID_DISPLAY = "A3864DZ"
private const val NOMIS_BOOKING_ID = 12344321L

class CourtSentencingSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var courtSentencingNomisApiMockServer: CourtSentencingNomisApiMockServer

  @Autowired
  private lateinit var courtSentencingMappingApiMockServer: CourtSentencingMappingApiMockServer

  @Nested
  @DisplayName("OFFENDER_CASES-INSERTED")
  inner class CourtCaseInserted {

    @Nested
    @DisplayName("When court sentencing was created in DPS")
    inner class DPSCreated {
      // private val nomisCourtCaseId = 12345L

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCase(
          courtCaseId = NOMIS_COURT_CASE_ID,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtCaseEvent(
            eventType = "OFFENDER_CASES-INSERTED",
            bookingId = NOMIS_BOOKING_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
            courtCaseId = NOMIS_COURT_CASE_ID,
            auditModule = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["bookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            },
            isNull(),
          )
        }

        dpsCourtSentencingServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-cases/nomis-court-case-id/\\d+")),
        )
        // will not create an court case in DPS
        dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court case was created in NOMIS")
    inner class NomisCreated {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCase(
          bookingId = NOMIS_BOOKING_ID,
          courtCaseId = NOMIS_COURT_CASE_ID,
        )
      }

      @Nested
      @DisplayName("When mapping does not exist yet")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          dpsCourtSentencingServer.stubPostCourtCaseForCreate(courtCaseId = DPS_COURT_CASE_ID)
          courtSentencingMappingApiMockServer.stubPostMapping()
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtCaseEvent(
              eventType = "OFFENDER_CASES-INSERTED",
            ),
          )
        }

        @Test
        fun `will create a court case in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              postRequestedFor(urlPathEqualTo("/court-case")),
              // TODO assert once DPS team have defined their dto
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases"))
                .withRequestBody(matchingJsonPath("dpsCourtCaseId", equalTo(DPS_COURT_CASE_ID)))
                .withRequestBody(matchingJsonPath("nomisCourtCaseId", equalTo(NOMIS_COURT_CASE_ID.toString())))
                .withRequestBody(matchingJsonPath("mappingType", equalTo("DPS_CREATED"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-case-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["bookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
            mapping = CourtCaseMappingDto(
              nomisCourtCaseId = NOMIS_COURT_CASE_ID,
              dpsCourtCaseId = DPS_COURT_CASE_ID,
            ),
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtCaseEvent(
              eventType = "OFFENDER_CASES-INSERTED",
              bookingId = NOMIS_BOOKING_ID,
              courtCaseId = NOMIS_COURT_CASE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-case-synchronisation-created-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["bookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              },
              isNull(),
            )
          }
          // will not create a court case in DPS
          dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
        }
      }
    }
  }
}

fun courtCaseEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  courtCaseId: Long = NOMIS_COURT_CASE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"courtCaseId\": \"$courtCaseId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()
