package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock
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
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension.Companion.csipApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingApiMockServer.Companion.CSIP_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingApiMockServer.Companion.CSIP_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

private const val DPS_CSIP_ID = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5"
private const val NOMIS_CSIP_ID = 1234L
private const val NOMIS_API_URL = "/csip/$NOMIS_CSIP_ID"
private const val NOMIS_MAPPING_API_URL = "$CSIP_GET_MAPPING_URL/$NOMIS_CSIP_ID"

class CSIPSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var csipNomisApi: CSIPNomisApiMockServer

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  inner class CSIPCreated {

    @Nested
    inner class WhenCreateByDPS {
      @BeforeEach
      fun setUp() {
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipEvent(eventType = "CSIP-INSERTED", auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("csip-synchronisation-skipped"),
            check {
              assertThat(it["nomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
              assertThat(it["dpsCSIPId"]).isNull()
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
    @DisplayName("When there is a new CSIP Inserted Event")
    inner class WhenNewCSIP {

      @Nested
      inner class WhenCreateByNomisSuccess {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIP()
          csipMappingApi.stubGetByNomisIdWithError()
          csipApi.stubCSIPInsert()
          mappingApi.stubMappingCreate(CSIP_CREATE_MAPPING_URL)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP-INSERTED"),
          )
        }

        @Test
        fun `will retrieve details about the csip from NOMIS`() {
          await untilAsserted {
            csipNomisApi.verify(getRequestedFor(urlEqualTo(NOMIS_API_URL)))
          }
        }

        @Test
        fun `will retrieve mapping to check if this is a new csip`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
          }
        }

        @Test
        fun `will create the csip in the csip service`() {
          await untilAsserted {
            csipApi.verify(postRequestedFor(urlPathEqualTo("/csip")))
          }
        }

        @Test
        fun `will create a mapping between the two records`() {
          await untilAsserted {
            mappingApi.verify(
              postRequestedFor(urlPathEqualTo("/mapping/csip"))
                .withRequestBody(matchingJsonPath("dpsCSIPId", equalTo(DPS_CSIP_ID)))
                .withRequestBody(matchingJsonPath("nomisCSIPId", equalTo("$NOMIS_CSIP_ID"))),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-synchronisation-created-success"),
              check {
                assertThat(it["dpsCSIPId"]).isEqualTo(DPS_CSIP_ID)
                assertThat(it["nomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenNomisHasNoCSIP {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIPNotFound()

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP-INSERTED"),
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
        private val duplicateDPSCSIPId = "ddd596da-8eab-4d2a-a026-bc5afb8acda0"

        @Test
        internal fun `it will not retry after a 409 (duplicate csip written to CSIP API)`() {
          csipNomisApi.stubGetCSIP()
          csipMappingApi.stubGetByNomisIdWithError()
          csipApi.stubCSIPInsert(duplicateDPSCSIPId)
          csipMappingApi.stubCSIPMappingCreateConflict()

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP-INSERTED"),
          )

          // wait for all mappings to be created before verifying
          await untilCallTo { mappingApi.createMappingCount(CSIP_CREATE_MAPPING_URL) } matches { it == 1 }

          // check that one csip is created
          assertThat(csipApi.createCSIPSyncCount()).isEqualTo(1)

          // doesn't retry
          csipMappingApi.verifyCreateMappingCSIPId(dpsCSIPId = duplicateDPSCSIPId)

          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-synchronisation-from-nomis-duplicate"),
              check {
                assertThat(it["existingNomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
                assertThat(it["duplicateNomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
                assertThat(it["existingDPSCSIPId"]).isEqualTo(DPS_CSIP_ID)
                assertThat(it["duplicateDPSCSIPId"]).isEqualTo(duplicateDPSCSIPId)
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
          csipNomisApi.stubGetCSIP()
          csipMappingApi.stubGetByNomisId()

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP-INSERTED"),
          )

          // check that no CSIPs are created
          assertThat(csipApi.createCSIPSyncCount()).isEqualTo(0)
          csipApi.verify(0, postRequestedFor(anyUrl()))

          // doesn't try to create a new mapping
          mappingApi.verify(0, postRequestedFor(anyUrl()))

          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-synchronisation-created-ignored"),
              check {
                assertThat(it["dpsCSIPId"]).isEqualTo(DPS_CSIP_ID)
                assertThat(it["nomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
                assertThat(it["migrationId"]).isNull()
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  inner class CSIPDeleted {
    @Nested
    @DisplayName("When csip was deleted in either NOMIS or DPS")
    inner class DeletedInEitherNOMISOrDPS {

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisIdWithError()
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(
              eventType = "CSIP-DELETED",
            ),
          )
        }

        @Test
        fun `telemetry added to track that the delete was ignored`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("csip-synchronisation-deleted-ignored"),
              check {
                assertThat(it["nomisCSIPId"]).isEqualTo("1234")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping does exist")
      inner class MappingExists {
        private val dpsCSIPId = "c4d6fb09-fd27-42bc-a33e-5ca74ac510be"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPId)

          csipApi.stubCSIPDelete(dpsCSIPId = dpsCSIPId)
          csipMappingApi.stubDeleteMapping(dpsCSIPId = dpsCSIPId)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP-DELETED"),
          )
        }

        @Test
        fun `will delete CSIP in DPS`() {
          await untilAsserted {
            csipApi.verify(
              1,
              WireMock.deleteRequestedFor(urlPathEqualTo("/csip/$dpsCSIPId")),
            )
          }
        }

        @Test
        fun `will delete CSIP mapping`() {
          await untilAsserted {
            csipMappingApi.verify(
              1,
              WireMock.deleteRequestedFor(urlPathEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPId")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-synchronisation-deleted-success"),
              check {
                assertThat(it["nomisCSIPId"]).isEqualTo(NOMIS_CSIP_ID.toString())
                assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPId)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping fails to be deleted")
      inner class MappingDeleteFails {
        private val dpsCSIPId = "a4725216-892d-4325-bc18-f74d95f3bca2"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPId)
          csipApi.stubCSIPDelete(dpsCSIPId = dpsCSIPId)
          csipMappingApi.stubDeleteMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP-DELETED"),
          )
        }

        @Test
        fun `will delete csip in DPS`() {
          await untilAsserted {
            csipApi.verify(
              1,
              WireMock.deleteRequestedFor(urlPathEqualTo("/csip/$dpsCSIPId")),
            )
          }
        }

        @Test
        fun `will try to delete CSIP mapping once and record failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-mapping-deleted-failed"),
              any(),
              isNull(),
            )

            csipMappingApi.verify(
              1,
              WireMock.deleteRequestedFor(urlPathEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPId")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-synchronisation-deleted-success"),
              check {
                assertThat(it["nomisCSIPId"]).isEqualTo(NOMIS_CSIP_ID.toString())
                assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPId)
              },
              isNull(),
            )
          }
        }
      }
    }
  }
}

fun SqsAsyncClient.waitForMessageCountOnQueue(queueUrl: String, messageCount: Int) =
  await untilCallTo {
    countAllMessagesOnQueue(queueUrl)
      .get()
  } matches { it == messageCount }

fun csipEvent(
  eventType: String = "CSIP-INSERTED",
  nomisCSIPId: String = "$NOMIS_CSIP_ID",
  auditModuleName: String = "OIDIXXXX",
) = """
  {
    "Type" : "Notification",
    "MessageId" : "7bdec840-69e5-5163-8013-967eb63d3d26",
    "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
    "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-02-08T13:56:40\",\"nomisEventType\":\"CSIP-INSERTED\",\"nomisCSIPId\":\"$nomisCSIPId\",\"csipPartySeq\":0,\"auditModuleName\":\"$auditModuleName\"}",
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
