package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.moreThan
import com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly
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
import org.mockito.Mockito.eq
import org.mockito.internal.verification.Times
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension.Companion.locationsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.LOCATIONS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.LOCATIONS_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

private const val DPS_LOCATION_ID = "abcdef12-1234-1234-1234-1234567890ab"
private const val NOMIS_LOCATION_ID = 12345L
private const val NOMIS_API_URL = "/locations/$NOMIS_LOCATION_ID"
private const val NOMIS_MAPPING_API_URL = "$LOCATIONS_GET_MAPPING_URL/$NOMIS_LOCATION_ID"
private const val DPS_PARENT_LOCATION_ID = "fedcba98-1234-1234-1234-1234567890ab"
private const val NOMIS_PARENT_LOCATION_ID = 45678L

class LocationsSynchronisationIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class WhenCreateByDPS {
    @BeforeEach
    fun setUp() {
      awsSqsLocationsOffenderEventsClient.sendMessage(
        locationsQueueOffenderEventsUrl,
        locationEvent(auditModuleName = "DPS_SYNCHRONISATION"),
      )
    }

    @Test
    fun `the event is ignored`() {
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("locations-synchronisation-skipped"),
          check {
            assertThat(it["nomisLocationId"]).isEqualTo("$NOMIS_LOCATION_ID")
            assertThat(it["dpsLocationId"]).isNull()
          },
          isNull(),
        )
      }
      nomisApi.verify(exactly(0), getRequestedFor(anyUrl()))
      mappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
      locationsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
    }
  }

  @Nested
  @DisplayName("AGENCY_INTERNAL_LOCATIONS-UPDATED")
  inner class LocationDetailUpserted {

    @Nested
    @DisplayName("When there is a new Location")
    inner class WhenNewLocation {

      @Nested
      inner class WhenCreateByNomisSuccess {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetLocation(NOMIS_LOCATION_ID, NOMIS_PARENT_LOCATION_ID)
          mappingApi.stubGetAnyLocationNotFound()
          mappingApi.stubGetLocation(DPS_PARENT_LOCATION_ID, NOMIS_PARENT_LOCATION_ID)
          locationsApi.stubUpsertLocationForSynchronisation(DPS_LOCATION_ID)
          mappingApi.stubMappingCreate(LOCATIONS_CREATE_MAPPING_URL)

          awsSqsLocationsOffenderEventsClient.sendMessage(
            locationsQueueOffenderEventsUrl,
            locationEvent(),
          )
        }

        @Test
        fun `will retrieve details about the location from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlEqualTo(NOMIS_API_URL)))
          }
        }

        @Test
        fun `will retrieve mapping to check if this is a new location`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
          }
        }

        @Test
        fun `will create the location in the locations service`() {
          await untilAsserted {
            locationsApi.verify(postRequestedFor(urlPathEqualTo("/sync/upsert")))
          }
        }

        @Test
        fun `will create a mapping between the two records`() {
          await untilAsserted {
            mappingApi.verify(
              postRequestedFor(urlPathEqualTo("/mapping/locations"))
                .withRequestBody(matchingJsonPath("dpsLocationId", equalTo(DPS_LOCATION_ID)))
                .withRequestBody(matchingJsonPath("nomisLocationId", equalTo(NOMIS_LOCATION_ID.toString()))),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("locations-created-synchronisation-success"),
              check {
                assertThat(it["dpsLocationId"]).isEqualTo(DPS_LOCATION_ID)
                assertThat(it["nomisLocationId"]).isEqualTo(NOMIS_LOCATION_ID.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenCreateByNomisWithMinimalDataSuccess {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetLocationWithMinimalData(NOMIS_LOCATION_ID)
          mappingApi.stubGetAnyLocationNotFound()
          locationsApi.stubUpsertLocationForSynchronisation(locationId = DPS_LOCATION_ID)
          mappingApi.stubMappingCreate(LOCATIONS_CREATE_MAPPING_URL)

          awsSqsLocationsOffenderEventsClient.sendMessage(
            locationsQueueOffenderEventsUrl,
            locationEvent(),
          )
        }

        @Test
        fun `will retrieve details about the location from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlEqualTo(NOMIS_API_URL)))
          }
        }

        @Test
        fun `will retrieve mapping to check if this is a new location`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
          }
        }

        @Test
        fun `will create the location in the locations service`() {
          await untilAsserted {
            locationsApi.verify(postRequestedFor(urlPathEqualTo("/sync/upsert")))
          }
        }

        @Test
        fun `will create a mapping between the two records`() {
          await untilAsserted {
            mappingApi.verify(
              postRequestedFor(urlPathEqualTo("/mapping/locations"))
                .withRequestBody(matchingJsonPath("dpsLocationId", equalTo(DPS_LOCATION_ID)))
                .withRequestBody(matchingJsonPath("nomisLocationId", equalTo(NOMIS_LOCATION_ID.toString()))),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("locations-created-synchronisation-success"),
              check {
                assertThat(it["dpsLocationId"]).isEqualTo(DPS_LOCATION_ID)
                assertThat(it["nomisLocationId"]).isEqualTo(NOMIS_LOCATION_ID.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenNomisHasNoLocation {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetLocationNotFound(NOMIS_LOCATION_ID)

          awsSqsLocationsOffenderEventsClient.sendMessage(
            locationsQueueOffenderEventsUrl,
            locationEvent(),
          )
          awsSqsLocationsOffenderEventDlqClient.waitForMessageCountOnQueue(locationsQueueOffenderEventsDlqUrl, 1)
        }

        @Test
        fun `will not create the location in the locations service`() {
          locationsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
        }

        @Test
        fun `will attempt to get mapping data`() {
          mappingApi.verify(moreThan(0), getRequestedFor(anyUrl()))
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
          nomisApi.stubGetLocation(NOMIS_LOCATION_ID, NOMIS_PARENT_LOCATION_ID)
          mappingApi.stubGetLocation(DPS_LOCATION_ID, NOMIS_LOCATION_ID)
          mappingApi.stubGetLocation(DPS_PARENT_LOCATION_ID, NOMIS_PARENT_LOCATION_ID)
          locationsApi.stubUpsertLocationForSynchronisation(DPS_LOCATION_ID)

          awsSqsLocationsOffenderEventsClient.sendMessage(
            locationsQueueOffenderEventsUrl,
            locationEvent(),
          )
        }

        @Test
        fun `will retrieve details about the location from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlEqualTo(NOMIS_API_URL)))
          }
        }

        @Test
        fun `will retrieve mapping to check if this is a new location`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
          }
        }

        @Test
        fun `will send the update to the location in the locations service`() {
          await untilAsserted {
            locationsApi.verify(postRequestedFor(urlPathEqualTo("/sync/upsert")))
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
              eq("locations-updated-synchronisation-success"),
              check {
                assertThat(it["dpsLocationId"]).isEqualTo(DPS_LOCATION_ID)
                assertThat(it["nomisLocationId"]).isEqualTo(NOMIS_LOCATION_ID.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenDuplicateMapping {

        private val duplicationLocationId = "12345678-1234-1234-1234-1234567890ab"

        @Test
        internal fun `it will not retry after a 409 (duplicate location written to Location API)`() {
          nomisApi.stubGetLocationWithMinimalData(NOMIS_LOCATION_ID)
          mappingApi.stubGetAnyLocationNotFound()
          locationsApi.stubUpsertLocationForSynchronisation(locationId = duplicationLocationId)
          mappingApi.stubLocationMappingCreateConflict(
            nomisLocationId = NOMIS_LOCATION_ID,
            existingLocationId = DPS_LOCATION_ID,
            duplicateLocationId = duplicationLocationId,
          )

          awsSqsLocationsOffenderEventsClient.sendMessage(
            locationsQueueOffenderEventsUrl,
            locationEvent(),
          )

          // wait for all mappings to be created before verifying
          await untilCallTo { mappingApi.createMappingCount(LOCATIONS_CREATE_MAPPING_URL) } matches { it == 1 }

          // check that one location is created
          assertThat(locationsApi.createLocationSynchronisationCount()).isEqualTo(1)

          // doesn't retry
          mappingApi.verifyCreateMappingLocationIds(arrayOf(duplicationLocationId), times = 1)

          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("from-nomis-synch-location-duplicate"),
              check {
                assertThat(it["duplicateDpsLocationId"]).isEqualTo(duplicationLocationId)
                assertThat(it["duplicateNomisLocationId"]).isEqualTo(NOMIS_LOCATION_ID.toString())
                assertThat(it["existingDpsLocationId"]).isEqualTo(DPS_LOCATION_ID)
                assertThat(it["existingNomisLocationId"]).isEqualTo(NOMIS_LOCATION_ID.toString())
                assertThat(it["migrationId"]).isNull()
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("When Location is deleted in Nomis")
    inner class WhenDeleted {
      @Nested
      inner class WhenDeleteByNomisSuccess {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetLocation(DPS_LOCATION_ID, NOMIS_LOCATION_ID)
          locationsApi.stubDeleteLocation(DPS_LOCATION_ID)
          mappingApi.stubLocationsDeleteMapping(DPS_LOCATION_ID)

          awsSqsLocationsOffenderEventsClient.sendMessage(
            locationsQueueOffenderEventsUrl,
            locationEvent(recordDeleted = true),
          )
        }

        @Test
        fun `will retrieve mapping`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
          }
        }

        @Test
        fun `will delete the location from the locations service`() {
          await untilAsserted {
            locationsApi.verify(deleteRequestedFor(urlPathEqualTo("/sync/delete/$DPS_LOCATION_ID")))
          }
        }

        @Test
        fun `will delete the mapping`() {
          await untilAsserted {
            mappingApi.verify(
              deleteRequestedFor(urlPathEqualTo("/mapping/locations/dps/$DPS_LOCATION_ID")),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the deletion`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("locations-deleted-synchronisation-success"),
              check {
                assertThat(it["dpsLocationId"]).isEqualTo(DPS_LOCATION_ID)
              },
              isNull(),
            )
            verify(telemetryClient).trackEvent(
              eq("locations-deleted-mapping-success"),
              check {
                assertThat(it["dpsLocationId"]).isEqualTo(DPS_LOCATION_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenMappingIsMissing {
        @BeforeEach
        fun setUp() {
          locationsApi.stubDeleteLocation(DPS_LOCATION_ID)
          mappingApi.stubLocationsDeleteMapping(DPS_LOCATION_ID)

          awsSqsLocationsOffenderEventsClient.sendMessage(
            locationsQueueOffenderEventsUrl,
            locationEvent(recordDeleted = true),
          )
        }

        @Test
        fun `will repeatedly try to retrieve mapping`() {
          await untilAsserted {
            mappingApi.verify(moreThanOrExactly(2), getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
          }
        }

        @Test
        fun `will not delete the location from the locations service`() {
          await untilAsserted {
            locationsApi.verify(
              exactly(0),
              deleteRequestedFor(urlPathEqualTo("/sync/delete/$DPS_LOCATION_ID")),
            )
          }
        }

        @Test
        fun `will not create telemetry tracking the deletion`() {
          await untilAsserted {
            verifyNoInteractions(telemetryClient)
          }
        }
      }

      @Nested
      inner class WhenDeleteLocationFails {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetLocation(DPS_LOCATION_ID, NOMIS_LOCATION_ID)
          locationsApi.stubDeleteLocationWithError(DPS_LOCATION_ID)

          awsSqsLocationsOffenderEventsClient.sendMessage(
            locationsQueueOffenderEventsUrl,
            locationEvent(recordDeleted = true),
          )
        }

        @Test
        fun `will retrieve mapping`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
          }
        }

        @Test
        fun `will repeatedly fail to delete the location from the locations service`() {
          await untilAsserted {
            locationsApi.verify(
              moreThanOrExactly(2),
              deleteRequestedFor(urlPathEqualTo("/sync/delete/$DPS_LOCATION_ID")),
            )
          }
        }

        @Test
        fun `will not delete the mapping`() {
          await untilAsserted {
            mappingApi.verify(
              exactly(0),
              deleteRequestedFor(urlPathEqualTo("/mapping/locations/dps/$DPS_LOCATION_ID")),
            )
          }
        }

        @Test
        fun `will not create telemetry`() {
          await untilAsserted {
            verifyNoInteractions(telemetryClient)
          }
        }
      }

      @Nested
      inner class WhenDeleteMappingFails {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetLocation(DPS_LOCATION_ID, NOMIS_LOCATION_ID)
          locationsApi.stubDeleteLocation(DPS_LOCATION_ID)
          mappingApi.stubLocationsDeleteMappingWithError(DPS_LOCATION_ID)

          awsSqsLocationsOffenderEventsClient.sendMessage(
            locationsQueueOffenderEventsUrl,
            locationEvent(recordDeleted = true),
          )
        }

        @Test
        fun `will retrieve mapping`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
          }
        }

        @Test
        fun `will delete the location from the locations service`() {
          await untilAsserted {
            locationsApi.verify(
              exactly(1),
              deleteRequestedFor(urlPathEqualTo("/sync/delete/$DPS_LOCATION_ID")),
            )
          }
        }

        @Test
        fun `will fail to delete the mapping once only`() {
          await untilAsserted {
            mappingApi.verify(
              exactly(1),
              deleteRequestedFor(urlPathEqualTo("/mapping/locations/dps/$DPS_LOCATION_ID")),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the deletion`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("locations-deleted-synchronisation-success"),
              check {
                assertThat(it["dpsLocationId"]).isEqualTo(DPS_LOCATION_ID)
              },
              isNull(),
            )
            verify(telemetryClient).trackEvent(
              eq("locations-deleted-mapping-failed"),
              check {
                assertThat(it["dpsLocationId"]).isEqualTo(DPS_LOCATION_ID)
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

fun locationEvent(
  eventType: String = "AGENCY_INTERNAL_LOCATIONS-UPDATED",
  locationId: Long = NOMIS_LOCATION_ID,
  auditModuleName: String = "OIMILOCA",
  recordDeleted: Boolean = false,
) = """{
    "Type" : "Notification",
    "MessageId" : "be8e7273-0446-5590-8c7f-2f24e966322e",
    "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
    "Message" : "{\"oldDescription\":\"HMI-D-1-007\",\"nomisEventType\":\"$eventType\",\"recordDeleted\":\"$recordDeleted\",\"eventDatetime\":\"2024-04-22T16:36:47.0000000Z\",\"prisonId\":\"HMI\",\"description\":\"HMI-D-1-007\",\"eventType\":\"$eventType\",\"auditModuleName\":\"$auditModuleName\",\"internalLocationId\":\"$locationId\"}",
    "Timestamp" : "2023-08-17T09:39:44.790Z",
    "SignatureVersion" : "1",
    "Signature" : "ppyNS9XAEwLaSdtXpVxZ+pYKT7g4uZLGGXUaquKKwtgpkcRCoTwG2Vcjbgh4HpqF0zNXTIQJHAckXBqXMXW6CeJuKcvndqOXO7yw+qzbL4iOkPecUkl4pJFWT0PJ4q6gptDOWf/nKP+Wd/ggozuGa27NJ5eEEGn/YbxnqH98h9C0pUjVPhaukoSp0fP6+2L8eyuFEPGgefT+reKZZ2E9VjUStaNNsNjdjVfkjrkHrVQwpey8PbucOQYLEwyo/WV6ho+gqjQYpM+WjghDWvGn6UNbnJKTQGxy3shInPsY2kfyCJAyUoOU0CJ6ALHKnlN7OMr1lbvmHMARgKNY6ELJoA==",
    "SigningCertURL" : "https://sns.eu-west-2.amazonaws.com/SimpleNotificationService-01d088a6f77103d0fe307c0069e40ed6.pem",
    "UnsubscribeURL" : "https://sns.eu-west-2.amazonaws.com/?Action=Unsubscribe&SubscriptionArn=arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7:bc997c88-9318-4ec0-b424-3f4579c9be6d",
    "MessageAttributes" : {
      "publishedAt" : {"Type":"String","Value":"2023-08-17T10:39:44.744191611+01:00"},
      "eventType" : {"Type": "String","Value": "$eventType"}
   }
}
""".trimIndent()
