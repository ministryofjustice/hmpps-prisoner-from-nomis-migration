package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
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
import org.mockito.Mockito.eq
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.IncidentsApiExtension.Companion.incidentsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.INCIDENTS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.INCIDENTS_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

private const val INCIDENT_ID = "4321"
private const val NOMIS_INCIDENT_ID = 1234L
private const val NOMIS_API_URL = "/incidents/$NOMIS_INCIDENT_ID"
private const val NOMIS_MAPPING_API_URL = "$INCIDENTS_GET_MAPPING_URL/$NOMIS_INCIDENT_ID"

class IncidentsSynchronisationIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class IncidentUpdated {

    @Nested
    inner class WhenCreateByDPS {
      @BeforeEach
      fun setUp() {
        awsSqsIncidentsOffenderEventsClient.sendMessage(
          incidentsQueueOffenderEventsUrl,
          incidentEvent(auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("incident-synchronisation-skipped"),
            check {
              assertThat(it["nomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
              assertThat(it["incidentId"]).isNull()
            },
            isNull(),
          )
        }
        nomisApi.verify(exactly(0), getRequestedFor(anyUrl()))
        mappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
        incidentsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When there is a new Incident")
    inner class WhenNewIncident {

      @Nested
      inner class WhenCreateByNomisSuccess {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetIncident()
          mappingApi.stubGetAnyIncidentNotFound()
          incidentsApi.stubIncidentForSync()
          mappingApi.stubMappingCreate(INCIDENTS_CREATE_MAPPING_URL)

          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent(),
          )
        }

        @Test
        fun `will retrieve details about the incident from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlEqualTo(NOMIS_API_URL)))
          }
        }

        @Test
        fun `will retrieve mapping to check if this is a new incident`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
          }
        }

        @Test
        fun `will create the incident in the incidents service`() {
          await untilAsserted {
            incidentsApi.verify(putRequestedFor(urlPathEqualTo("/incidents/sync")))
          }
        }

        @Test
        fun `will create a mapping between the two records`() {
          await untilAsserted {
            mappingApi.verify(
              postRequestedFor(urlPathEqualTo("/mapping/incidents"))
                .withRequestBody(matchingJsonPath("incidentId", equalTo(INCIDENT_ID)))
                .withRequestBody(matchingJsonPath("nomisIncidentId", equalTo("$NOMIS_INCIDENT_ID"))),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("incident-created-synchronisation-success"),
              check {
                assertThat(it["incidentId"]).isEqualTo(INCIDENT_ID)
                assertThat(it["nomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenNomisHasNoIncident {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetIncidentNotFound()

          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent(),
          )
          awsSqsIncidentsOffenderEventDlqClient.waitForMessageCountOnQueue(incidentsQueueOffenderEventsDlqUrl, 1)
        }

        @Test
        fun `will not create the incident in the incidents service`() {
          incidentsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
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
      inner class WhenUpdateByNomisSuccess {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetIncident()
          mappingApi.stubGetIncident()
          incidentsApi.stubIncidentForSync()

          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent(),
          )
        }

        @Test
        fun `will retrieve details about the incident from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlEqualTo(NOMIS_API_URL)))
          }
        }

        @Test
        fun `will retrieve mapping to check if this is a new incident`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
          }
        }

        @Test
        fun `will send the update to the incident in the incidents service`() {
          await untilAsserted {
            incidentsApi.verify(putRequestedFor(urlPathEqualTo("/incidents/sync")))
          }
        }

        @Test
        fun `will not add a new mapping between the two records`() {
          await untilAsserted {
            verify(telemetryClient, Times(1)).trackEvent(any(), any(), isNull())
            mappingApi.verify(exactly(0), postRequestedFor(anyUrl()))
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("incident-updated-synchronisation-success"),
              check {
                assertThat(it["incidentId"]).isEqualTo(INCIDENT_ID)
                assertThat(it["nomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenDuplicateMapping {
        private val duplicateIncidentId = "9876"

        @Test
        internal fun `it will not retry after a 409 (duplicate incident written to Incident API)`() {
          nomisApi.stubGetIncident()
          mappingApi.stubGetAnyIncidentNotFound()
          incidentsApi.stubIncidentForSync(duplicateIncidentId)
          mappingApi.stubIncidentMappingCreateConflict()

          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent(),
          )

          // wait for all mappings to be created before verifying
          await untilCallTo { mappingApi.createMappingCount(INCIDENTS_CREATE_MAPPING_URL) } matches { it == 1 }

          // check that one incident is created
          assertThat(incidentsApi.createIncidentSynchronisationCount()).isEqualTo(1)

          // doesn't retry
          mappingApi.verifyCreateMappingIncidentIds(arrayOf(duplicateIncidentId.toLong()), times = 1)

          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("from-nomis-synch-incident-duplicate"),
              check {
                assertThat(it["existingNomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
                assertThat(it["duplicateNomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
                assertThat(it["existingIncidentId"]).isEqualTo(INCIDENT_ID)
                assertThat(it["duplicateIncidentId"]).isEqualTo(duplicateIncidentId)
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
  @DisplayName("INCIDENT-DELETED-*")
  inner class IncidentDeleted {

    @Nested
    inner class WhenDeleteByDPS {
      @BeforeEach
      fun setUp() {
        awsSqsIncidentsOffenderEventsClient.sendMessage(
          incidentsQueueOffenderEventsUrl,
          incidentEvent(
            eventType = "INCIDENT-DELETED-PARTIES",
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("incident-delete-synchronisation-skipped"),
            check {
              assertThat(it["nomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
            },
            isNull(),
          )
        }
        nomisApi.verify(exactly(0), anyRequestedFor(anyUrl()))
        mappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
        incidentsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
      }
    }

    @Nested
    inner class WhenDeleteByNomisWithNoMapping {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetAnyIncidentNotFound()

        awsSqsIncidentsOffenderEventsClient.sendMessage(
          incidentsQueueOffenderEventsUrl,
          incidentEvent(
            eventType = "INCIDENT-DELETED-PARTIES",
            auditModuleName = "OIUDINCRS",
          ),
        )
        await untilAsserted {
          verify(telemetryClient, Times(1)).trackEvent(any(), any(), isNull())
        }
      }

      @Test
      fun `will attempt to retrieve mapping`() {
        mappingApi.verify(getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
      }

      @Test
      fun `will not delete the incident in the incidents service`() {
        incidentsApi.verify(exactly(0), deleteRequestedFor(anyUrl()))
      }

      @Test
      fun `will not attempt to delete a mapping`() {
        mappingApi.verify(exactly(0), deleteRequestedFor(anyUrl()))
      }

      @Test
      fun `will create telemetry tracking the ignored delete`() {
        verify(telemetryClient).trackEvent(
          eq("incident-delete-synchronisation-ignored"),
          check {
            assertThat(it["nomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("When there is a delete incident")
    inner class WhenDeleteByNomis {

      @Nested
      inner class WhenDeleteByNomisSuccess {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetIncident()
          incidentsApi.stubIncidentForSyncDelete(INCIDENT_ID)
          mappingApi.stubIncidentMappingDelete()

          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent(
              eventType = "INCIDENT-DELETED-PARTIES",
              auditModuleName = "OIUDINCRS",
            ),
          )
        }

        @Test
        fun `will retrieve mapping`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
          }
        }

        @Test
        fun `will delete the incident in the incidents service`() {
          await untilAsserted {
            incidentsApi.verify(deleteRequestedFor(urlPathEqualTo("/incidents/sync/$INCIDENT_ID")))
          }
        }

        @Test
        fun `will delete mapping`() {
          await untilAsserted {
            mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/incidents/incident-id/$INCIDENT_ID")))
          }
        }

        @Test
        fun `will create telemetry tracking the delete`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("incident-delete-synchronisation-success"),
              check {
                //   assertThat(it["nomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
                //  assertThat(it["incidentId"]).isEqualTo(INCIDENT_ID)
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

fun incidentEvent(
  eventType: String = "INCIDENT-CHANGED-PARTIES",
  nomisIncidentId: String = "$NOMIS_INCIDENT_ID",
  auditModuleName: String = "OIDINCRS",
) = """
  {
    "Type" : "Notification",
    "MessageId" : "7bdec840-69e5-5163-8013-967eb63d3d26",
    "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
    "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-02-08T13:56:40\",\"nomisEventType\":\"INCIDENT-UPDATED\",\"incidentCaseId\":\"$nomisIncidentId\",\"incidentPartySeq\":0,\"auditModuleName\":\"$auditModuleName\"}",
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
