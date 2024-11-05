package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.eq
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPDpsApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingApiMockServer.Companion.CSIP_CREATE_CHILD_MAPPINGS_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingApiMockServer.Companion.CSIP_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.UUID

private const val NOMIS_CSIP_ID = 1234L

class CSIPReviewSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var csipNomisApi: CSIPNomisApiMockServer

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  @DisplayName("CSIP_REVIEWS-INSERTED")
  inner class CSIPReviewCreated {

    @Nested
    inner class WhenCreateByDPS {
      private val nomisCSIPReviewId = 67L

      @BeforeEach
      fun setUp() {
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipReviewEvent(eventType = "CSIP_REVIEWS-INSERTED", csipReviewId = nomisCSIPReviewId.toString(), auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("csip-synchronisation-updated-skipped"),
            check {
              assertThat(it["nomisCSIPReviewId"]).isEqualTo(nomisCSIPReviewId.toString())
              assertThat(it["nomisCSIPId"]).isEqualTo(NOMIS_CSIP_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
              assertThat(it["dpsCSIPId"]).isNull()
            },
            isNull(),
          )
        }
        csipNomisApi.verify(exactly(0), getRequestedFor(anyUrl()))
        csipMappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
        csipDpsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When there is a new CSIP Review Inserted Event")
    inner class WhenNewCSIPReview {
      private val nomisCSIPReviewId = 67L
      private val nomisCSIPReportId = NOMIS_CSIP_ID
      val dpsCSIPReportId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5"
      val dpsCSIPReviewId = "e07fdbee-1463-4c7e-a374-aae2445845be"

      @Nested
      @DisplayName("When Create CSIP Review Happy Path")
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIP(nomisCSIPReportId)
          csipMappingApi.stubGetByNomisId(nomisCSIPId = nomisCSIPReportId, dpsCSIPId = dpsCSIPReportId)
          csipMappingApi.stubGetFullMappingByDpsReportId(dpsCSIPId = dpsCSIPReportId)
          csipDpsApi.stubSyncCSIPReportReviewMappingUpdate(
            dpsCSIPReviewId = dpsCSIPReviewId,
            nomisCSIPReviewId = nomisCSIPReviewId,
          )
          csipMappingApi.stubPostMapping(CSIP_CREATE_CHILD_MAPPINGS_URL)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipReviewEvent(eventType = "CSIP_REVIEWS-INSERTED", csipReviewId = nomisCSIPReviewId.toString()),
          )
          waitForAnyProcessingToComplete("csip-synchronisation-updated-success")
        }

        @Test
        fun `will retrieve details about the csip from NOMIS`() {
          csipNomisApi.verify(getRequestedFor(urlEqualTo("/csip/$nomisCSIPReportId")))
        }

        @Test
        fun `will retrieve mapping to determine the associated csip report`() {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/csip/nomis-csip-id/$nomisCSIPReportId")))
        }

        @Test
        fun `will request full csip mapping`() {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPReportId/all")))
        }

        @Test
        fun `will update DPS with the changes including CSIP Reviews`() {
          csipDpsApi.verify(
            putRequestedFor(urlEqualTo("/sync/csip-records"))
              .withRequestBody(matchingJsonPath("id", equalTo(dpsCSIPReportId)))
              .withRequestBody(matchingJsonPath("legacyId", equalTo("1234")))
              .withRequestBody(matchingJsonPath("logCode", equalTo("ASI-001")))
              .withRequestBody(matchingJsonPath("prisonNumber", equalTo("A1234BC")))
              .withRequestBody(matchingJsonPath("plan.reviews[0].legacyId", equalTo("67")))
              .withRequestBody(matchingJsonPath("actionedAt", equalTo("2024-04-01T10:00:00")))
              .withRequestBody(matchingJsonPath("actionedBy", equalTo("FJAMES"))),
          )
        }

        @Test
        fun `will update the mapping between the two records`() {
          csipMappingApi.verify(
            postRequestedFor(urlPathEqualTo(CSIP_CREATE_CHILD_MAPPINGS_URL))
              .withRequestBody(matchingJsonPath("dpsCSIPReportId", equalTo(dpsCSIPReportId)))
              .withRequestBody(matchingJsonPath("nomisCSIPReportId", equalTo(nomisCSIPReportId.toString())))
              .withRequestBody(matchingJsonPath("reviewMappings[0].dpsId", equalTo(dpsCSIPReviewId)))
              .withRequestBody(
                matchingJsonPath(
                  "reviewMappings[0].nomisId",
                  equalTo(nomisCSIPReviewId.toString()),
                ),
              ),
          )
        }

        @Test
        fun `will create telemetry tracking the update`() {
          verify(telemetryClient).trackEvent(
            eq("csip-synchronisation-updated-success"),
            check {
              assertThat(it["nomisCSIPId"]).isEqualTo(nomisCSIPReportId.toString())
              assertThat(it["nomisCSIPReviewId"]).isEqualTo(nomisCSIPReviewId.toString())
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
              assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPReportId)
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When Nomis has no CSIP Report")
      inner class WhenNomisHasNoCSIP {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIP(HttpStatus.NOT_FOUND)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipReviewEvent(eventType = "CSIP_REVIEWS-INSERTED", csipReviewId = nomisCSIPReviewId.toString()),
          )
          awsSqsCSIPOffenderEventDlqClient.waitForMessageCountOnQueue(csipQueueOffenderEventsDlqUrl, 1)
        }

        @Test
        fun `will not create the csip in the csip service`() {
          csipDpsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
        }

        @Test
        fun `will not attempt to get mapping data`() {
          mappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
        }

        @Test
        fun `will not create telemetry tracking`() {
          verify(telemetryClient, Times(0)).trackEvent(any(), any(), isNull())
        }
      }

      @Nested
      inner class WhenDuplicateMapping {
        private val dpsCsipReportId = UUID.randomUUID().toString()
        private val duplicateDPSCSIPId = "ddd596da-8eab-4d2a-a026-bc5afb8acda0"

        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIP()
          csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipReviewEvent(eventType = "CSIP_REVIEWS-INSERTED", csipReviewId = nomisCSIPReviewId.toString()),
          )
        }

        @Test
        internal fun `it will not retry after a 409 (duplicate csip written to CSIP API)`() {
          csipNomisApi.stubGetCSIP()
          csipMappingApi.stubGetByNomisId(HttpStatus.NOT_FOUND)
          csipDpsApi.stubSyncCSIPReport(duplicateDPSCSIPId)
          csipMappingApi.stubCSIPMappingCreateConflict(existingDPSCSIPId = dpsCsipReportId)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP_REPORTS-INSERTED"),
          )

          // wait for all mappings to be created before verifying
          await untilCallTo { mappingApi.createMappingCount(CSIP_CREATE_MAPPING_URL) } matches { it == 1 }

          // check that one csip is created
          assertThat(csipDpsApi.syncCSIPCount()).isEqualTo(1)

          // doesn't retry
          csipMappingApi.verifyCreateCSIPReportMapping(dpsCSIPId = duplicateDPSCSIPId)

          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-synchronisation-from-nomis-duplicate"),
              check {
                assertThat(it["existingNomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
                assertThat(it["duplicateNomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
                assertThat(it["existingDPSCSIPId"]).isEqualTo(dpsCsipReportId)
                assertThat(it["duplicateDPSCSIPId"]).isEqualTo(duplicateDPSCSIPId)
                assertThat(it["migrationId"]).isNull()
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIP()
          csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipReviewEvent(eventType = "CSIP_REVIEWS-INSERTED", csipReviewId = nomisCSIPReviewId.toString()),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("csip-synchronisation-updated-failed"),
              check {
                assertThat(it["nomisCSIPId"]).isEqualTo(NOMIS_CSIP_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo("A1234BC")
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(1)
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("CSIP_REVIEWS-UPDATED")
  inner class CSIPReviewUpdated {

    @Nested
    inner class WhenUpdateByDPS {
      private val nomisCSIPReviewId = 67L

      @BeforeEach
      fun setUp() {
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipReviewEvent(eventType = "CSIP_REVIEWS-UPDATED", csipReviewId = nomisCSIPReviewId.toString(), auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("csip-synchronisation-updated-skipped"),
            check {
              assertThat(it["nomisCSIPReviewId"]).isEqualTo(nomisCSIPReviewId.toString())
              assertThat(it["nomisCSIPId"]).isEqualTo(NOMIS_CSIP_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
              assertThat(it["dpsCSIPId"]).isNull()
            },
            isNull(),
          )
        }
        csipNomisApi.verify(exactly(0), getRequestedFor(anyUrl()))
        csipMappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
        csipDpsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When there is a new CSIP Review Updated Event")
    inner class WhenUpdateByNomis {
      private val nomisCSIPReviewId = 67L
      private val nomisCSIPReportId = NOMIS_CSIP_ID
      val dpsCSIPReportId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5"

      @Nested
      @DisplayName("When Update CSIP Review Happy Path")
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIP(nomisCSIPReportId)
          csipMappingApi.stubGetByNomisId(nomisCSIPId = nomisCSIPReportId, dpsCSIPId = dpsCSIPReportId)
          csipMappingApi.stubGetFullMappingByDpsReportId(dpsCSIPId = dpsCSIPReportId)
          csipDpsApi.stubSyncCSIPReportNoMappingUpdates()

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipReviewEvent(eventType = "CSIP_REVIEWS-UPDATED", csipReviewId = nomisCSIPReviewId.toString()),
          )
          waitForAnyProcessingToComplete("csip-synchronisation-updated-success")
        }

        @Test
        fun `will retrieve details about the csip from NOMIS`() {
          csipNomisApi.verify(getRequestedFor(urlEqualTo("/csip/$nomisCSIPReportId")))
        }

        @Test
        fun `will retrieve mapping to determine the associated csip report`() {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/csip/nomis-csip-id/$nomisCSIPReportId")))
        }

        @Test
        fun `will request full csip mapping`() {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPReportId/all")))
        }

        @Test
        fun `will update DPS with the changes including CSIP Reviews`() {
          csipDpsApi.verify(
            putRequestedFor(urlEqualTo("/sync/csip-records"))
              .withRequestBody(matchingJsonPath("id", equalTo(dpsCSIPReportId)))
              .withRequestBody(matchingJsonPath("legacyId", equalTo("1234")))
              .withRequestBody(matchingJsonPath("logCode", equalTo("ASI-001")))
              .withRequestBody(matchingJsonPath("prisonNumber", equalTo("A1234BC")))
              .withRequestBody(matchingJsonPath("plan.reviews[0].legacyId", equalTo("67")))
              .withRequestBody(matchingJsonPath("actionedAt", equalTo("2024-04-01T10:00:00")))
              .withRequestBody(matchingJsonPath("actionedBy", equalTo("FJAMES"))),
          )
        }

        @Test
        fun `will NOT update the mapping between the two records`() {
          mappingApi.verify(exactly(0), postRequestedFor(anyUrl()))
        }

        @Test
        fun `will create telemetry tracking the update`() {
          verify(telemetryClient).trackEvent(
            eq("csip-synchronisation-updated-success"),
            check {
              assertThat(it["nomisCSIPId"]).isEqualTo(nomisCSIPReportId.toString())
              assertThat(it["nomisCSIPReviewId"]).isEqualTo(nomisCSIPReviewId.toString())
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
              assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPReportId)
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When Nomis has no CSIP Report")
      inner class WhenNomisHasNoCSIP {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIP(HttpStatus.NOT_FOUND)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipReviewEvent(eventType = "CSIP_REVIEWS-UPDATED", csipReviewId = nomisCSIPReviewId.toString()),
          )
          awsSqsCSIPOffenderEventDlqClient.waitForMessageCountOnQueue(csipQueueOffenderEventsDlqUrl, 1)
        }

        @Test
        fun `will not create the csip in the csip service`() {
          csipDpsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
        }

        @Test
        fun `will not attempt to get mapping data`() {
          mappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
        }

        @Test
        fun `will not create telemetry tracking`() {
          verify(telemetryClient, Times(0)).trackEvent(any(), any(), isNull())
        }
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIP()
          csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipReviewEvent(eventType = "CSIP_REVIEWS-UPDATED", csipReviewId = nomisCSIPReviewId.toString()),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("csip-synchronisation-updated-failed"),
              check {
                assertThat(it["nomisCSIPId"]).isEqualTo(NOMIS_CSIP_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo("A1234BC")
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(1)
          }
        }
      }
    }
  }
}

fun csipReviewEvent(
  eventType: String,
  csipReviewId: String,
  csipReportId: String = "$NOMIS_CSIP_ID",
  auditModuleName: String = "OIDCSIPC",
) = """
  {
    "Type" : "Notification",
    "MessageId" : "7bdec840-69e5-5163-8013-967eb63d3d26",
    "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
    "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-06-11T10:39:17\",\"bookingId\":1215724,\"offenderIdDisplay\":\"A1234BC\",\"nomisEventType\":\"$eventType\",\"rootOffenderId\":2581911,\"csipReviewId\":\"$csipReviewId\",\"csipReportId\":\"$csipReportId\",\"auditModuleName\":\"$auditModuleName\"}",
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
