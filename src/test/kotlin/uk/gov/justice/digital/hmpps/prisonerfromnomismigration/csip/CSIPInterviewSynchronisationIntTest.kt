package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage

private const val NOMIS_CSIP_ID = 1234L

class CSIPInterviewSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var csipNomisApi: CSIPNomisApiMockServer

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  @DisplayName("CSIP_INTVW-DELETED")
  inner class CSIPInterviewDeleted {
    private val nomisCSIPInterviewId = 343L

    @Nested
    @DisplayName("When csip interview was deleted")
    inner class DeletedInEitherNOMISOrDPS {

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetInterviewByNomisId(HttpStatus.NOT_FOUND)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipInterviewEvent(eventType = "CSIP_INTVW-DELETED", csipInterviewId = nomisCSIPInterviewId.toString()),
          )
        }

        @Test
        fun `telemetry added to track that the delete was ignored`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("csip-interview-synchronisation-deleted-ignored"),
              check {
                assertThat(it["nomisCSIPInterviewId"]).isEqualTo(nomisCSIPInterviewId.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("Happy Path")
      inner class HappyPath {
        private val dpsCSIPInterviewId = "c4d6fb09-fd27-42bc-a33e-5ca74ac510be"
        private val nomisCSIPInterviewId = 987L
        private val dpsCSIPReportId = "c4d6fb09-fd27-42bc-a33e-5ca74ac510be"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetInterviewByNomisId(nomisCSIPInterviewId = nomisCSIPInterviewId, dpsCSIPInterviewId = dpsCSIPInterviewId, dpsCSIPReportId = dpsCSIPReportId)

          csipDpsApi.stubDeleteCSIPInterview(dpsCSIPInterviewId = dpsCSIPInterviewId)
          csipMappingApi.stubDeleteInterviewMapping(dpsCSIPInterviewId = dpsCSIPInterviewId)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipInterviewEvent(eventType = "CSIP_INTVW-DELETED", csipInterviewId = nomisCSIPInterviewId.toString()),
          )

          waitForAnyProcessingToComplete("csip-interview-synchronisation-deleted-success")
        }

        @Test
        fun `will delete CSIP in DPS`() {
          csipDpsApi.verify(
            1,
            WireMock.deleteRequestedFor(urlPathEqualTo("/csip-records/referral/investigation/interviews/$dpsCSIPInterviewId")),
          )
        }

        @Test
        fun `will delete CSIP Interview mapping`() {
          csipMappingApi.verify(
            1,
            WireMock.deleteRequestedFor(urlPathEqualTo("/mapping/csip/interviews/dps-csip-interview-id/$dpsCSIPInterviewId")),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("csip-interview-synchronisation-deleted-success"),
            check {
              assertThat(it["nomisCSIPInterviewId"]).isEqualTo(nomisCSIPInterviewId.toString())
              assertThat(it["dpsCSIPInterviewId"]).isEqualTo(dpsCSIPInterviewId)
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
              assertThat(it["nomisCSIPReportId"]).isEqualTo("$NOMIS_CSIP_ID")
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When mapping fails to be deleted")
      inner class MappingDeleteFails {
        private val nomisCSIPInterviewId = 121L
        private val dpsCSIPInterviewId = "a4725216-892d-4325-bc18-f74d95f3bca2"
        private val dpsCSIPReportId = "a4725216-892d-4325-bc18-f74d95f3bca2"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetInterviewByNomisId(nomisCSIPInterviewId = nomisCSIPInterviewId, dpsCSIPInterviewId = dpsCSIPInterviewId, dpsCSIPReportId = dpsCSIPReportId)
          csipApi.stubDeleteCSIPInterview(dpsCSIPInterviewId = dpsCSIPInterviewId)
          csipMappingApi.stubDeleteInterviewMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipInterviewEvent(eventType = "CSIP_INTVW-DELETED", csipInterviewId = nomisCSIPInterviewId.toString()),
          )
          waitForAnyProcessingToComplete("csip-interview-mapping-deleted-failed")
        }

        @Test
        fun `will delete csip in DPS`() {
          csipDpsApi.verify(1, WireMock.deleteRequestedFor(urlPathEqualTo("/csip-records/referral/investigation/interviews/$dpsCSIPInterviewId")))
        }

        @Test
        fun `will try to delete CSIP mapping once and record failure`() {
          verify(telemetryClient).trackEvent(
            eq("csip-interview-mapping-deleted-failed"),
            any(),
            isNull(),
          )

          csipMappingApi.verify(
            1,
            WireMock.deleteRequestedFor(urlPathEqualTo("/mapping/csip/interviews/dps-csip-interview-id/$dpsCSIPInterviewId")),
          )
        }

        @Test
        fun `will eventually track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-interview-synchronisation-deleted-success"),
              check {
                assertThat(it["nomisCSIPInterviewId"]).isEqualTo(nomisCSIPInterviewId.toString())
                assertThat(it["dpsCSIPInterviewId"]).isEqualTo(dpsCSIPInterviewId)
              },
              isNull(),
            )
          }
        }
      }
    }
  }
}

fun csipInterviewEvent(
  eventType: String = "CSIP_INTVW-INSERTED",
  csipInterviewId: String,
  csipReportId: String = "$NOMIS_CSIP_ID",
  auditModuleName: String = "OIDCSIPC",
) = """
  {
    "Type" : "Notification",
    "MessageId" : "7bdec840-69e5-5163-8013-967eb63d3d26",
    "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
    "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-06-11T10:39:17\",\"bookingId\":1215724,\"offenderIdDisplay\":\"A1234BC\",\"nomisEventType\":\"$eventType\",\"rootOffenderId\":2581911,\"csipInterviewId\":\"$csipInterviewId\",\"csipReportId\":\"$csipReportId\",\"auditModuleName\":\"$auditModuleName\"}",
    "Timestamp" : "2024-02-08T13:56:40.981Z",
    "SignatureVersion" : "1",
    "Signature" : "ZUU+9m0kLuVMVE0KCwk5LN1bhQQ6VTOP7djMUaJFYB/+s8kKpAh4Hm5XbIrqbAIoDJmf2MF+GxGRe1sypAn7z61GqqotcXI6r5CjiCvQVsrcwQqO0qoUkb5NoXWyBCG4MOaasFYfjleDnthQS/+rnNWT9Ndl09QtAhjfztHnD279GbrVhywj9O1xcDpnIkx/zGsZUbQsPZDOTOcfeV0M8mbrJhWMWefg9fZ05LeLljD4B8DjMfkmMAn3nBszWlZQcQPDReV7xoMPA+dXJpYXXx6PRLPRtfs7BFGA1hsuYI0mXZb3V3QBvG4Jt5IEYPkfKGZDEmf/hK9V7WkfBiDu2A==",
    "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-60eadc530605d63b8e62a523676ef735.pem",
    "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:902e0982-6d9a-4430-9aae-e055362dc824",
    "MessageAttributes" : {
      "publishedAt" : {"Type":"String","Value":"2024-02-08T13:56:40.96292265Z"},
      "eventType" : {"Type":"String","Value":"$eventType"}
    }
} 
""".trimIndent()
