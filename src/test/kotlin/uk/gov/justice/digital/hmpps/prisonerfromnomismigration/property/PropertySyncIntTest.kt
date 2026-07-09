package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.PropertyApiExtension.Companion.propertyDpsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.model.SyncPropertyContainerResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.UUID

private const val DPS_ID = "e52d7268-6e10-41a8-a0b9-2319b32520d6"
private const val DPS_LOCATION_ID = "edcd118c-41ba-42ea-b5c4-404b453a0000"
private const val NOMIS_ID = 9876543210L
private const val BOOKING_ID = 1234567L
private const val OFFENDER_ID_DISPLAY = "A1234AA"

class PropertySyncIntTest(
  @Autowired private val propertyNomisApiMockServer: PropertyNomisApiMockServer,
  @Autowired private val propertyMappingApiMockServer: PropertyMappingApiMockServer,
) : PropertyIntegrationTestBase() {

  @Nested
  @DisplayName("PRISONER_PROPERTY-INSERTED")
  inner class Created {
    @Nested
    @DisplayName("When property was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() = runTest {
        propertyNomisApiMockServer.stubGetProperty(
          containerId = NOMIS_ID,
          bookingId = BOOKING_ID,
          propertyResponse = propertyContainerGetResponse(NOMIS_ID, BOOKING_ID, OFFENDER_ID_DISPLAY),
        )
        awsSqsPropertyEventDlqClient.sendMessage(
          propertyEventQueueUrl,
          propertyEvent(eventType = "PRISONER_PROPERTY-INSERTED", auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("property-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["nomisPropertyContainerId"]).isEqualTo(NOMIS_ID.toString())
            },
            isNull(),
          )
        }

        // will not bother getting mapping
        propertyMappingApiMockServer.verify(
          count = 0,
          getRequestedFor(urlPathMatching("/mapping/property/nomis-id/\\d+")),
        )
        // will not create property in DPS
        propertyDpsApi.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When property was created in NOMIS")
    inner class NomisCreated {
      @BeforeEach
      fun setUp() {
        propertyNomisApiMockServer.stubGetProperty(
          NOMIS_ID,
          BOOKING_ID,
          propertyResponse = propertyContainerGetResponse(NOMIS_ID, BOOKING_ID, OFFENDER_ID_DISPLAY),
        )
        mappingApi.stubAllMappingsNotFound("/mapping/property/nomis-id")
        mappingApi.stubMappingCreate("/mapping/property")
        mappingApi.stubGetApiLocationNomis(123456, DPS_LOCATION_ID)
      }

      @Nested
      @DisplayName("Happy path")
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          propertyDpsApi.stubUpsert(
            SyncPropertyContainerResponse(
              dpsId = UUID.fromString(DPS_ID),
              nomisPropertyContainerId = NOMIS_ID,
              mappingType = SyncPropertyContainerResponse.MappingType.CREATED,
            ),
          )
          propertyMappingApiMockServer.stubPostMapping()

          awsSqsPropertyEventDlqClient.sendMessage(
            propertyEventQueueUrl,
            propertyEvent(
              eventType = "PRISONER_PROPERTY-INSERTED",
              bookingId = BOOKING_ID,
              nomisId = NOMIS_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
        }

        @Test
        fun `will create property in DPS`() {
          await untilAsserted {
            propertyDpsApi.verify(
              1,
              postRequestedFor(urlPathEqualTo("/sync/property-containers/upsert"))
                .withRequestBodyJsonPath("nomisPropertyContainerId", equalTo(NOMIS_ID.toString()))
                .withRequestBodyJsonPath("prisonerNumber", equalTo(OFFENDER_ID_DISPLAY))
                .withRequestBodyJsonPath("internalLocationId", equalTo(DPS_LOCATION_ID))
                .withRequestBodyJsonPath("prisonId", equalTo("SYI"))
                .withRequestBodyJsonPath("containerCode", equalTo("Bulk"))
                .withRequestBodyJsonPath("sealMark", equalTo("SEAL1234"))
                .withRequestBodyJsonPath("active", equalTo("true"))
                .withRequestBodyJsonPath("proposedDisposalDate", equalTo("2035-05-14"))
                .withRequestBodyJsonPath("expiryDate", equalTo("2035-05-13"))
                .withRequestBodyJsonPath("createDateTime", equalTo("2025-05-01T12:34:56"))
                .withRequestBodyJsonPath("createUsername", equalTo("ME"))
                .withRequestBodyJsonPath("modifyDateTime", equalTo("2025-05-02T12:34:56"))
                .withRequestBodyJsonPath("modifyUsername", equalTo("SOMEONEELSE")),
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            propertyMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/property"))
                .withRequestBody(matchingJsonPath("dpsPropertyContainerId", equalTo(DPS_ID)))
                .withRequestBody(matchingJsonPath("nomisPropertyContainerId", equalTo(NOMIS_ID.toString())))
                .withRequestBody(matchingJsonPath("bookingId", equalTo(BOOKING_ID.toString())))
                .withRequestBody(matchingJsonPath("offenderNo", equalTo(OFFENDER_ID_DISPLAY)))
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("property-synchronisation-created-success"),
              check {
                assertThat(it["dpsPropertyContainerId"]).isEqualTo(DPS_ID)
                assertThat(it["nomisPropertyContainerId"]).isEqualTo(NOMIS_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["mapping"]).isEqualTo("success")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        @BeforeEach
        fun setUp() {
          propertyMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          propertyDpsApi.stubUpsert(
            SyncPropertyContainerResponse(
              dpsId = UUID.fromString(DPS_ID),
              nomisPropertyContainerId = NOMIS_ID,
              mappingType = SyncPropertyContainerResponse.MappingType.CREATED,
            ),
          )
        }

        @Nested
        @DisplayName("Mapping fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            propertyMappingApiMockServer.stubPostMappingFailureFollowedBySuccess()

            awsSqsPropertyEventDlqClient.sendMessage(
              propertyEventQueueUrl,
              propertyEvent(
                eventType = "PRISONER_PROPERTY-INSERTED",
                bookingId = BOOKING_ID,
                nomisId = NOMIS_ID,
                offenderNo = OFFENDER_ID_DISPLAY,
              ),
            )
          }

          @Test
          fun `will create property in DPS, mapping and telemetry`() {
            await untilAsserted {
              propertyDpsApi.verify(
                postRequestedFor(urlPathEqualTo("/sync/property-containers/upsert")),
              )
            }

            await untilAsserted {
              propertyMappingApiMockServer.verify(
                exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/property"))
                  .withRequestBody(matchingJsonPath("dpsPropertyContainerId", equalTo(DPS_ID)))
                  .withRequestBody(matchingJsonPath("nomisPropertyContainerId", equalTo(NOMIS_ID.toString())))
                  .withRequestBody(matchingJsonPath("bookingId", equalTo(BOOKING_ID.toString())))
                  .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("property-synchronisation-created-success"),
                check {
                  assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                  assertThat(it["nomisPropertyContainerId"]).isEqualTo(NOMIS_ID.toString())
                  assertThat(it["dpsPropertyContainerId"]).isEqualTo(DPS_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("property-synchronisation-mapping-created"),
                check {
                  assertThat(it["dpsPropertyContainerId"]).isEqualTo(DPS_ID)
                  assertThat(it["nomisPropertyContainerId"]).isEqualTo(NOMIS_ID.toString())
                  assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                },
                isNull(),
              )
            }

            assertThat(
              awsSqsPropertyEventDlqClient.countAllMessagesOnQueue(propertyEventDlqUrl).get(),
            ).isEqualTo(0)
          }
        }

        @Nested
        @DisplayName("Fails constantly")
        inner class FailsConstantly {
          @BeforeEach
          fun setUp() {
            propertyMappingApiMockServer.stubPostMapping(status = INTERNAL_SERVER_ERROR)
            awsSqsPropertyEventClient.sendMessage(
              propertyEventQueueUrl,
              propertyEvent(
                eventType = "PRISONER_PROPERTY-INSERTED",
                bookingId = BOOKING_ID,
                nomisId = NOMIS_ID,
                offenderNo = OFFENDER_ID_DISPLAY,
              ),
            )
            await untilCallTo {
              awsSqsPropertyEventDlqClient.countAllMessagesOnQueue(propertyEventDlqUrl).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create property in DPS`() {
            await untilAsserted {
              propertyDpsApi.verify(
                1,
                postRequestedFor(urlPathEqualTo("/sync/property-containers/upsert")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            propertyMappingApiMockServer.verify(
              exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/property")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("property-synchronisation-created-success"),
                check {
                  assertThat(it["dpsPropertyContainerId"]).isEqualTo(DPS_ID)
                  assertThat(it["nomisPropertyContainerId"]).isEqualTo(NOMIS_ID.toString())
                  assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }
          }
        }
      }

      @Nested
      @DisplayName("When property api POST fails")
      inner class DPSApiFail {
        @BeforeEach
        fun setUp() {
          propertyDpsApi.stubUpsertFailure()

          awsSqsPropertyEventDlqClient.sendMessage(
            propertyEventQueueUrl,
            propertyEvent(
              eventType = "PRISONER_PROPERTY-INSERTED",
              bookingId = BOOKING_ID,
              nomisId = NOMIS_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
        }

        @Test
        fun `will not attempt to create mapping and will track a telemetry event for failure`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("property-synchronisation-created-error"),
              check {
                assertThat(it["nomisPropertyContainerId"]).isEqualTo(NOMIS_ID.toString())
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["error"]).isEqualTo("500 Internal Server Error from POST http://localhost:8107/sync/property-containers/upsert")
              },
              isNull(),
            )
          }
          propertyMappingApiMockServer.verify(
            0,
            postRequestedFor(urlPathEqualTo("/mapping/property")),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("PRISONER_PROPERTY-UPDATED")
  inner class Updated {
    @Nested
    @DisplayName("When property was updated in DPS")
    inner class DPSUpdated {

      @BeforeEach
      fun setUp() = runTest {
        propertyNomisApiMockServer.stubGetProperty(
          containerId = NOMIS_ID,
          bookingId = BOOKING_ID,
          propertyResponse = propertyContainerGetResponse(NOMIS_ID, BOOKING_ID, OFFENDER_ID_DISPLAY),
        )
        awsSqsPropertyEventDlqClient.sendMessage(
          propertyEventQueueUrl,
          propertyEvent(eventType = "PRISONER_PROPERTY-UPDATED", auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("property-synchronisation-updated-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["nomisPropertyContainerId"]).isEqualTo(NOMIS_ID.toString())
            },
            isNull(),
          )
        }

        // will not bother getting mapping
        propertyMappingApiMockServer.verify(
          count = 0,
          getRequestedFor(urlPathMatching("/mapping/property/nomis-id/\\d+")),
        )
        // will not create property in DPS
        propertyDpsApi.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When property was updated in NOMIS")
    inner class NomisUpdated {
      @BeforeEach
      fun setUp() {
        propertyNomisApiMockServer.stubGetProperty(
          propertyResponse = propertyContainerGetResponse(NOMIS_ID, BOOKING_ID, OFFENDER_ID_DISPLAY),
        )
        propertyMappingApiMockServer.stubGetByNomisId(
          PropertyContainerMappingDto(
            dpsPropertyContainerId = DPS_ID,
            nomisPropertyContainerId = NOMIS_ID,
            bookingId = BOOKING_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
            mappingType = PropertyContainerMappingDto.MappingType.MIGRATED,
          ),
        )
        mappingApi.stubGetApiLocationNomis(123456, DPS_LOCATION_ID)
      }

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          propertyDpsApi.stubUpsert(
            SyncPropertyContainerResponse(
              dpsId = UUID.fromString(DPS_ID),
              nomisPropertyContainerId = NOMIS_ID,
              mappingType = SyncPropertyContainerResponse.MappingType.UPDATED,
            ),
          )

          awsSqsPropertyEventDlqClient.sendMessage(
            propertyEventQueueUrl,
            propertyEvent(
              eventType = "PRISONER_PROPERTY-UPDATED",
              bookingId = BOOKING_ID,
              nomisId = NOMIS_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
        }

        @Test
        fun `will update property in DPS`() {
          await untilAsserted {
            propertyDpsApi.verify(
              postRequestedFor(urlPathEqualTo("/sync/property-containers/upsert"))
                .withRequestBodyJsonPath("dpsId", equalTo(DPS_ID))
                .withRequestBodyJsonPath("nomisPropertyContainerId", equalTo(NOMIS_ID.toString()))
                .withRequestBodyJsonPath("prisonerNumber", equalTo(OFFENDER_ID_DISPLAY)),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("property-synchronisation-updated-success"),
              check {
                assertThat(it["dpsPropertyContainerId"]).isEqualTo(DPS_ID)
                assertThat(it["nomisPropertyContainerId"]).isEqualTo(NOMIS_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              },
              isNull(),
            )
          }
        }
      }
    }
  }
}

fun propertyEvent(
  eventType: String,
  bookingId: Long = BOOKING_ID,
  nomisId: Long = NOMIS_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModuleName: String? = "OIDSTUFF",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-07-10T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"$eventType\",\"propertyContainerId\":$nomisId,\"auditModuleName\":\"$auditModuleName\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

/*private fun dpsProperty() = SyncPropertyContainerRequest(
  nomisPropertyContainerId = NOMIS_ID,
  prisonerNumber = OFFENDER_ID_DISPLAY,
  internalLocationId = nomisProperty.toDpsLocation(),
  prisonId = nomisProperty.prisonId,
  containerCode = nomisProperty.toDpsContainerCode(),
  sealMark = nomisProperty.sealMark,
  active = nomisProperty.active,
  proposedDisposalDate = nomisProperty.proposedDisposalDate,
  expiryDate = nomisProperty.expiryDate,
  createDateTime = nomisProperty.createdDateTime,
  createUsername = nomisProperty.createdBy,
  modifyDateTime = nomisProperty.updatedDateTime,
  modifyUsername = nomisProperty.updatedBy,
)*/
