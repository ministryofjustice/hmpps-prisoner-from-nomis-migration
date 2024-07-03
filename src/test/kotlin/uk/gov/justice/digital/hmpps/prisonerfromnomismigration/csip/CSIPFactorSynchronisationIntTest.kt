package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension.Companion.csipApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

private const val NOMIS_CSIP_ID = 1234L

class CSIPFactorSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var csipNomisApi: CSIPNomisApiMockServer

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  inner class CSIPFactorCreated {

    @Nested
    inner class WhenCreateByDPS {
      private val nomisCsipFactorId = 543L

      @BeforeEach
      fun setUp() {
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCsipFactorId.toString(), auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("csip-factor-synchronisation-skipped"),
            check {
              assertThat(it["nomisCsipFactorId"]).isEqualTo(nomisCsipFactorId.toString())
              assertThat(it["nomisCsipReportId"]).isEqualTo(NOMIS_CSIP_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
              assertThat(it["dpsCSIPFactorId"]).isNull()
            },
            isNull(),
          )
        }
        csipNomisApi.verify(exactly(0), getRequestedFor(anyUrl()))
        csipMappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
        csipApi.verify(exactly(0), anyRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When there is a new CSIP Factor Inserted Event")
    inner class WhenNewCSIPFactor {
      private val nomisCsipFactorId = 654L
      private val nomisCsipReportId = NOMIS_CSIP_ID
      val dpsCsipReportId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5"
      val dpsCsipFactorId = "e07fdbee-1463-4c7e-a374-aae2445845be"

      @Nested
      inner class WhenCreateByNomisSuccess {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIPFactor(nomisCsipFactorId)
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCsipReportId) // Needed to ensure we have the uuid for dps
          csipMappingApi.stubGetFactorByNomisId(HttpStatus.NOT_FOUND)
          csipApi.stubCSIPFactorInsert(dpsCsipReportId, dpsCsipFactorId)
          mappingApi.stubMappingCreate("/mapping/csip/factors")

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCsipFactorId.toString()),
          )
          waitForAnyProcessingToComplete("csip-factor-synchronisation-created-success")
        }

        @Test
        fun `will retrieve details about the csip from NOMIS`() {
          await untilAsserted {
            csipNomisApi.verify(getRequestedFor(urlEqualTo("/csip/factors/$nomisCsipFactorId")))
          }
        }

        @Test
        fun `will retrieve mapping to determine the associated csip report`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/csip/nomis-csip-id/$nomisCsipReportId")))
          }
        }

        @Test
        fun `will retrieve mapping to check if this is a new csip factor`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/csip/factors/nomis-csip-factor-id/$nomisCsipFactorId")))
          }
        }

        @Test
        fun `will create the csip factor in the csip service`() {
          await untilAsserted {
            csipApi.verify(
              postRequestedFor(urlPathEqualTo("/csip-records/$dpsCsipReportId/referral/contributory-factors"))
                .withHeader("Username", equalTo("JSMITH")),
            )
          }
        }

        @Test
        fun `will create a mapping between the two records`() {
          await untilAsserted {
            mappingApi.verify(
              postRequestedFor(urlPathEqualTo("/mapping/csip/factors"))
                .withRequestBody(matchingJsonPath("dpsCSIPFactorId", equalTo(dpsCsipFactorId)))
                .withRequestBody(matchingJsonPath("nomisCSIPFactorId", equalTo(nomisCsipFactorId.toString()))),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-factor-synchronisation-created-success"),
              check {
                assertThat(it["nomisCsipReportId"]).isEqualTo(nomisCsipReportId.toString())
                assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                assertThat(it["nomisCsipFactorId"]).isEqualTo(nomisCsipFactorId.toString())
                assertThat(it["dpsCsipFactorId"]).isEqualTo(dpsCsipFactorId)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenNomisHasNoCSIPFactor {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIPFactor(HttpStatus.NOT_FOUND)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCsipFactorId.toString()),
          )
          awsSqsCSIPOffenderEventDlqClient.waitForMessageCountOnQueue(csipQueueOffenderEventsDlqUrl, 1)
        }

        @Test
        fun `will not create the csip in the csip service`() {
          csipApi.verify(exactly(0), anyRequestedFor(anyUrl()))
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
        private val duplicateDPSCSIPFactorId = "ddd5e842-e9f9-4200-9024-1e4a02ec46ae"

        @Test
        internal fun `it will not retry after a 409 (duplicate csip written to CSIP API)`() {
          csipNomisApi.stubGetCSIPFactor(nomisCsipFactorId)
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCsipReportId) // Needed to ensure we have the uuid for dps
          csipMappingApi.stubGetFactorByNomisId(HttpStatus.NOT_FOUND)
          csipApi.stubCSIPFactorInsert(dpsCsipReportId, duplicateDPSCSIPFactorId)
          csipMappingApi.stubCSIPFactorMappingCreateConflict(
            nomisCSIPFactorId = nomisCsipFactorId,
            duplicateDPSCSIPFactorId = duplicateDPSCSIPFactorId,
            existingDPSCSIPFactorId = dpsCsipFactorId,
          )

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCsipFactorId.toString()),
          )

          // wait for all mappings to be created before verifying
          await untilCallTo { mappingApi.createMappingCount("/mapping/csip/factors") } matches { it == 1 }

          // check that one csip is created
          assertThat(csipApi.createCSIPFactorSyncCount()).isEqualTo(1)

          // doesn't retry
          csipMappingApi.verifyCreateCSIPFactorMapping(dpsCSIPFactorId = duplicateDPSCSIPFactorId)

          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-factor-synchronisation-from-nomis-duplicate"),
              check {
                assertThat(it["existingNomisCSIPFactorId"]).isEqualTo("$nomisCsipFactorId")
                assertThat(it["duplicateNomisCSIPFactorId"]).isEqualTo("$nomisCsipFactorId")
                assertThat(it["existingDPSCSIPFactorId"]).isEqualTo(dpsCsipFactorId)
                assertThat(it["duplicateDPSCSIPFactorId"]).isEqualTo(duplicateDPSCSIPFactorId)
                assertThat(it["migrationId"]).isNull()
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenMappingAlreadyExists {

        @Test
        internal fun `it will not retry after a 409 (duplicate csip written to CSIP API)`() {
          csipNomisApi.stubGetCSIPFactor(nomisCsipFactorId)
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCsipReportId) // Needed to ensure we have the uuid for dps
          csipMappingApi.stubGetFactorByNomisId(nomisCSIPFactorId = nomisCsipFactorId, dpsCSIPFactorId = dpsCsipFactorId)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCsipFactorId.toString()),
          )

          // check that no CSIPs are created
          assertThat(csipApi.createCSIPFactorSyncCount()).isEqualTo(0)
          csipApi.verify(0, postRequestedFor(anyUrl()))

          // doesn't try to create a new mapping
          mappingApi.verify(0, postRequestedFor(anyUrl()))

          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-factor-synchronisation-created-ignored"),
              check {
                assertThat(it["dpsCsipFactorId"]).isEqualTo(dpsCsipFactorId)
                assertThat(it["nomisCsipFactorId"]).isEqualTo(nomisCsipFactorId.toString())
                assertThat(it["reason"]).isEqualTo("CSIP Factor already mapped")
                assertThat(it["migrationId"]).isNull()
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenAssociatedReportMappingDoesNotYetExist {

        @Test
        internal fun `it will put the message on the dlq`() {
          csipNomisApi.stubGetCSIPFactor(nomisCsipFactorId)
          csipMappingApi.stubGetByNomisId(HttpStatus.NOT_FOUND)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCsipFactorId.toString()),
          )

          awsSqsCSIPOffenderEventDlqClient.waitForMessageCountOnQueue(csipQueueOffenderEventsDlqUrl, 1)

          // check that no CSIPs are created and no posts of any kind are sent
          assertThat(csipApi.createCSIPFactorSyncCount()).isEqualTo(0)
          csipApi.verify(0, postRequestedFor(anyUrl()))

          // doesn't try to create a new mapping
          mappingApi.verify(0, postRequestedFor(anyUrl()))

          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("csip-factor-synchronisation-created-failed"),
              check {
                assertThat(it["nomisCsipReportId"]).isEqualTo("$NOMIS_CSIP_ID")
                assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                assertThat(it["nomisCsipFactorId"]).isEqualTo(nomisCsipFactorId.toString())
                assertThat(it["reason"]).isEqualTo("CSIP Report for CSIP factor not mapped")
                assertThat(it["migrationId"]).isNull()
              },
              isNull(),
            )
          }
        }
      }
    }
  }
}

fun csipFactorEvent(
  eventType: String = "CSIP_FACTORS-INSERTED",
  csipFactorId: String,
  csipReportId: String = "$NOMIS_CSIP_ID",
  auditModuleName: String = "OIDCSIPC",
) = """
  {
    "Type" : "Notification",
    "MessageId" : "7bdec840-69e5-5163-8013-967eb63d3d26",
    "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
    "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-06-11T10:39:17\",\"bookingId\":1215724,\"offenderIdDisplay\":\"A1234BC\",\"nomisEventType\":\"$eventType\",\"rootOffenderId\":2581911,\"csipFactorId\":\"$csipFactorId\",\"csipReportId\":\"$csipReportId\",\"auditModuleName\":\"$auditModuleName\"}",
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
