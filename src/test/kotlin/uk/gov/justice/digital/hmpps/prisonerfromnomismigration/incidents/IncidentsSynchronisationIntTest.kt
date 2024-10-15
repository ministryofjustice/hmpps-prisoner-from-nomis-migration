package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsApiExtension.Companion.incidentsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsMappingApiMockServer.Companion.INCIDENTS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsMappingApiMockServer.Companion.INCIDENTS_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

private const val DPS_INCIDENT_ID = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42"
private const val NOMIS_INCIDENT_ID = 1234L
private const val NOMIS_API_URL = "/incidents/$NOMIS_INCIDENT_ID"
private const val NOMIS_MAPPING_API_URL = "$INCIDENTS_GET_MAPPING_URL/$NOMIS_INCIDENT_ID"

class IncidentsSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var incidentsNomisApi: IncidentsNomisApiMockServer

  @Autowired
  private lateinit var incidentsMappingApi: IncidentsMappingApiMockServer

  @Nested
  @DisplayName("INCIDENT-CREATED")
  inner class IncidentCreated {

    @Nested
    inner class WhenCreateByDPS {
      @BeforeEach
      fun setUp() {
        awsSqsIncidentsOffenderEventsClient.sendMessage(
          incidentsQueueOffenderEventsUrl,
          incidentEvent(eventType = "INCIDENT-INSERTED", auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("incidents-synchronisation-skipped"),
            check {
              assertThat(it["nomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
              assertThat(it["dpsIncidentId"]).isNull()
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
    @DisplayName("When there is a new Incident Inserted Event")
    inner class WhenNewIncident {

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          incidentsNomisApi.stubGetIncident()
          incidentsMappingApi.stubGetAnyIncidentNotFound()
          incidentsApi.stubIncidentUpsert()
          mappingApi.stubMappingCreate(INCIDENTS_CREATE_MAPPING_URL)

          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent(eventType = "INCIDENT-INSERTED"),
          )
          waitForAnyProcessingToComplete("incidents-synchronisation-created-success")
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
            incidentsApi.verify(postRequestedFor(urlPathEqualTo("/sync/upsert")))
          }
        }

        @Test
        fun `will create a mapping between the two records`() {
          await untilAsserted {
            mappingApi.verify(
              postRequestedFor(urlPathEqualTo("/mapping/incidents"))
                .withRequestBodyJsonPath("dpsIncidentId", DPS_INCIDENT_ID)
                .withRequestBodyJsonPath("nomisIncidentId", "$NOMIS_INCIDENT_ID"),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("incidents-synchronisation-created-success"),
              check {
                assertThat(it["dpsIncidentId"]).isEqualTo(DPS_INCIDENT_ID)
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
          incidentsNomisApi.stubGetIncidentNotFound()

          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent(eventType = "INCIDENT-INSERTED"),
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
      inner class WhenMappingAlreadyExists {

        @Test
        internal fun `telemetry added to track the failure`() {
          incidentsNomisApi.stubGetIncident()
          incidentsMappingApi.stubGetIncident()

          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent(eventType = "INCIDENT-INSERTED"),
          )

          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("incidents-synchronisation-created-ignored"),
              check {
                assertThat(it["nomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
                assertThat(it["migrationId"]).isNull()
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenDuplicateMapping {
        private val duplicateDPSIncidentId = "ddd596da-8eab-4d2a-a026-bc5afb8acda0"

        @Test
        internal fun `it will not retry after a 409 (duplicate incident written to Incident API)`() {
          incidentsNomisApi.stubGetIncident()
          incidentsMappingApi.stubGetAnyIncidentNotFound()
          incidentsApi.stubIncidentUpsert(duplicateDPSIncidentId)
          incidentsMappingApi.stubIncidentMappingCreateConflict()

          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent(eventType = "INCIDENT-INSERTED"),
          )

          // wait for all mappings to be created before verifying
          await untilCallTo { mappingApi.createMappingCount(INCIDENTS_CREATE_MAPPING_URL) } matches { it == 1 }

          // check that one incident is created
          assertThat(incidentsApi.createIncidentUpsertCount()).isEqualTo(1)

          // doesn't retry
          incidentsMappingApi.verifyCreateMappingIncidentId(dpsIncidentId = duplicateDPSIncidentId)

          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("incidents-synchronisation-nomis-duplicate"),
              check {
                assertThat(it["existingNomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
                assertThat(it["duplicateNomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
                assertThat(it["existingDPSIncidentId"]).isEqualTo(DPS_INCIDENT_ID)
                assertThat(it["duplicateDPSIncidentId"]).isEqualTo(duplicateDPSIncidentId)
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
  inner class IncidentUpdated {

    @Nested
    inner class WhenUpdateByDPS {
      @BeforeEach
      fun setUp() {
        awsSqsIncidentsOffenderEventsClient.sendMessage(
          incidentsQueueOffenderEventsUrl,
          incidentEvent(eventType = "INCIDENT-INSERTED", auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("incidents-synchronisation-skipped"),
            check {
              assertThat(it["nomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
              assertThat(it["dpsIncidentId"]).isNull()
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
    @DisplayName("When there is a new Update Incident event")
    inner class WhenUpdateByNomis {

      @Nested
      inner class WhenNomisHasNoIncident {
        @BeforeEach
        fun setUp() {
          incidentsNomisApi.stubGetIncidentNotFound()

          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent("INCIDENT-CHANGED-PARTIES"),
          )
          awsSqsIncidentsOffenderEventDlqClient.waitForMessageCountOnQueue(incidentsQueueOffenderEventsDlqUrl, 1)
        }

        @Test
        fun `will not create the incident in the incidents service`() {
          incidentsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
        }

        @Test
        fun `will not attempt to get mapping data`() {
          incidentsMappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
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
          incidentsNomisApi.stubGetIncident()
          incidentsMappingApi.stubGetAnyIncidentNotFound()
          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent(eventType = "INCIDENT-CHANGED-RESPONSES"),
          )
          awsSqsIncidentsOffenderEventDlqClient.waitForMessageCountOnQueue(incidentsQueueOffenderEventsDlqUrl, 1)
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("incidents-synchronisation-updated-failed"),
              check {
                assertThat(it["nomisIncidentId"]).isEqualTo("1234")
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsIncidentsOffenderEventDlqClient.countAllMessagesOnQueue(incidentsQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          incidentsNomisApi.stubGetIncident()
          incidentsMappingApi.stubGetIncident()
          incidentsApi.stubIncidentUpsert()

          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent("INCIDENT-CHANGED-PARTIES"),
          )
          waitForAnyProcessingToComplete("incidents-synchronisation-updated-success")
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
            incidentsApi.verify(
              postRequestedFor(urlPathEqualTo("/sync/upsert"))
                .withRequestBodyJsonPath("id", "fb4b2e91-91e7-457b-aa17-797f8c5c2f42"),
            )
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
              eq("incidents-synchronisation-updated-success"),
              check {
                assertThat(it["dpsIncidentId"]).isEqualTo(DPS_INCIDENT_ID)
                assertThat(it["nomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("INCIDENT-DELETED")
  inner class IncidentDeleted {

    @Nested
    inner class WhenDeleteByDPS {
      @BeforeEach
      fun setUp() {
        awsSqsIncidentsOffenderEventsClient.sendMessage(
          incidentsQueueOffenderEventsUrl,
          incidentEvent(
            eventType = "INCIDENT-DELETED-CASES",
            auditModuleName = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("incidents-synchronisation-deleted-skipped"),
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
        incidentsMappingApi.stubGetAnyIncidentNotFound()

        awsSqsIncidentsOffenderEventsClient.sendMessage(
          incidentsQueueOffenderEventsUrl,
          incidentEvent(
            eventType = "INCIDENT-DELETED-CASES",
            auditModuleName = "OIUDINCRS",
          ),
        )
        waitForAnyProcessingToComplete("incidents-synchronisation-deleted-ignored")
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
          eq("incidents-synchronisation-deleted-ignored"),
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
          incidentsMappingApi.stubGetIncident()
          incidentsApi.stubIncidentDelete(DPS_INCIDENT_ID)
          incidentsMappingApi.stubIncidentMappingDelete()

          awsSqsIncidentsOffenderEventsClient.sendMessage(
            incidentsQueueOffenderEventsUrl,
            incidentEvent(
              eventType = "INCIDENT-DELETED-CASES",
              auditModuleName = "OIUDINCRS",
            ),
          )
          waitForAnyProcessingToComplete("incidents-synchronisation-deleted-success")
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
            incidentsApi.verify(deleteRequestedFor(urlPathEqualTo("/incident-reports/$DPS_INCIDENT_ID")))
          }
        }

        @Test
        fun `will delete mapping`() {
          await untilAsserted {
            mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/incidents/dps-incident-id/$DPS_INCIDENT_ID")))
          }
        }

        @Test
        fun `will create telemetry tracking the delete`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("incidents-synchronisation-deleted-success"),
              check {
                assertThat(it["nomisIncidentId"]).isEqualTo("$NOMIS_INCIDENT_ID")
                assertThat(it["dpsIncidentId"]).isEqualTo(DPS_INCIDENT_ID)
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
  eventType: String,
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
