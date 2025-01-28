package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPDpsApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingApiMockServer.Companion.CSIP_CREATE_CHILD_MAPPINGS_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingApiMockServer.Companion.CSIP_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingApiMockServer.Companion.CSIP_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.AbstractMap.SimpleEntry

private const val DPS_CSIP_ID = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5"
private const val NOMIS_CSIP_ID = 1234L
private const val NOMIS_API_URL = "/csip/$NOMIS_CSIP_ID"
private const val NOMIS_MAPPING_API_URL = "$CSIP_GET_MAPPING_URL/$NOMIS_CSIP_ID"
private const val BOOKING_ID = 2345L

class CSIPSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var csipNomisApi: CSIPNomisApiMockServer

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  @DisplayName("CSIP_REPORTS-INSERTED")
  inner class CSIPReportCreated {

    @Nested
    inner class WhenCreateByDPS {
      @BeforeEach
      fun setUp() {
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipEvent(eventType = "CSIP_REPORTS-INSERTED", auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("csip-synchronisation-created-skipped"),
            check {
              assertThat(it["nomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
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
    @DisplayName("CSIP_REPORTS-INSERTED - When there is a new CSIP Inserted Event")
    inner class WhenNewCSIP {

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIP()
          csipMappingApi.stubGetByNomisId(NOT_FOUND)
          csipDpsApi.stubSyncCSIPReportWithFactor()
          mappingApi.stubMappingCreate(CSIP_CREATE_MAPPING_URL)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP_REPORTS-INSERTED"),
          )
          waitForAnyProcessingToComplete("csip-synchronisation-created-success")
        }

        @Test
        fun `will retrieve details about the csip from NOMIS`() {
          csipNomisApi.verify(getRequestedFor(urlEqualTo(NOMIS_API_URL)))
        }

        @Test
        fun `will retrieve mapping to check if this is a new csip`() {
          mappingApi.verify(getRequestedFor(urlPathEqualTo(NOMIS_MAPPING_API_URL)))
        }

        @Test
        fun `will create the csip in the csip service`() {
          csipDpsApi.verify(
            putRequestedFor(urlPathEqualTo("/sync/csip-records"))
              .withRequestBodyJsonPath("prisonNumber", "A1234BC")
              .withRequestBodyJsonPath("referral.incidentDate", "2024-06-12")
              .withRequestBodyJsonPath("referral.incidentTime", "10:32:12"),
          )
        }

        @Test
        fun `will create a mapping between the two records`() {
          mappingApi.verify(
            postRequestedFor(urlPathEqualTo(CSIP_CREATE_MAPPING_URL))
              .withRequestBodyJsonPath("dpsCSIPReportId", DPS_CSIP_ID)
              .withRequestBodyJsonPath("nomisCSIPReportId", "$NOMIS_CSIP_ID")
              .withRequestBodyJsonPath("factorMappings[0].dpsId", "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e6"),
          )
        }

        @Test
        fun `will create telemetry tracking the create`() {
          verify(telemetryClient).trackEvent(
            eq("csip-synchronisation-created-success"),
            check {
              assertThat(it["dpsCSIPId"]).isEqualTo(DPS_CSIP_ID)
              assertThat(it["nomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
              assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenCreateByNomisSuccessWithMinimalData {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIPWithMinimalData()
          csipMappingApi.stubGetByNomisId(NOT_FOUND)
          csipDpsApi.stubSyncCSIPReportWithFactor()
          mappingApi.stubMappingCreate(CSIP_CREATE_MAPPING_URL)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP_REPORTS-INSERTED"),
          )
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-synchronisation-created-success"),
              check {
                assertThat(it["dpsCSIPId"]).isEqualTo(DPS_CSIP_ID)
                assertThat(it["nomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
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
          csipNomisApi.stubGetCSIP(NOT_FOUND)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(),
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
        private val duplicateDPSCSIPId = "ddd596da-8eab-4d2a-a026-bc5afb8acda0"

        @Test
        internal fun `it will not retry after a 409 (duplicate csip written to CSIP API)`() {
          csipNomisApi.stubGetCSIP()
          csipMappingApi.stubGetByNomisId(NOT_FOUND)
          csipDpsApi.stubSyncCSIPReport(duplicateDPSCSIPId)
          csipMappingApi.stubCSIPMappingCreateConflict()

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
            csipEvent(eventType = "CSIP_REPORTS-INSERTED"),
          )

          // check that no CSIPs are created
          assertThat(csipDpsApi.syncCSIPCount()).isEqualTo(0)
          csipDpsApi.verify(0, putRequestedFor(anyUrl()))

          // doesn't try to create a new mapping
          mappingApi.verify(0, postRequestedFor(anyUrl()))

          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-synchronisation-created-ignored"),
              check {
                assertThat(it["nomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
                assertThat(it["dpsCSIPId"]).isEqualTo(DPS_CSIP_ID)
                assertThat(it["migrationId"]).isNull()
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        private val dpsCSIPReportId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
        private val nomisCSIPReportId = 876L

        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIP(nomisCSIPId = nomisCSIPReportId)
          csipMappingApi.stubGetByNomisId(NOT_FOUND)
          csipDpsApi.stubSyncCSIPReport(dpsCSIPId = dpsCSIPReportId)
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            csipMappingApi.stubPostMappingFailureFollowedBySuccess()
            awsSqsCSIPOffenderEventsClient.sendMessage(
              csipQueueOffenderEventsUrl,
              csipEvent(eventType = "CSIP_REPORTS-INSERTED", csipReportId = nomisCSIPReportId.toString()),
            )
          }

          @Test
          fun `will create csip in DPS`() {
            await untilAsserted {
              csipDpsApi.verify(putRequestedFor(urlEqualTo("/sync/csip-records")))
            }
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            await untilAsserted {
              csipMappingApi.verify(
                exactly(2),
                postRequestedFor(urlPathEqualTo(CSIP_CREATE_MAPPING_URL))
                  .withRequestBodyJsonPath("dpsCSIPReportId", dpsCSIPReportId)
                  .withRequestBodyJsonPath("nomisCSIPReportId", nomisCSIPReportId.toString())
                  .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
              )
            }

            assertThat(
              awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(0)
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-synchronisation-created-success"),
                check {
                  assertThat(it["nomisCSIPId"]).isEqualTo(nomisCSIPReportId.toString())
                  assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                  assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPReportId)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["nomisCSIPId"]).isEqualTo(nomisCSIPReportId.toString())
                  assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                  assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPReportId)
                },
                isNull(),
              )
            }
          }
        }

        @Nested
        @DisplayName("Fails constantly")
        inner class FailsConstantly {
          @BeforeEach
          fun setUp() {
            csipMappingApi.stubPostMapping(status = INTERNAL_SERVER_ERROR)
            awsSqsCSIPOffenderEventsClient.sendMessage(
              csipQueueOffenderEventsUrl,
              csipEvent(eventType = "CSIP_REPORTS-INSERTED", csipReportId = nomisCSIPReportId.toString()),
            )
            await untilCallTo {
              awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create csip in DPS`() {
            await untilAsserted {
              csipDpsApi.verify(
                1,
                putRequestedFor(urlPathEqualTo("/sync/csip-records")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            csipMappingApi.verify(
              exactly(3),
              postRequestedFor(urlPathEqualTo(CSIP_CREATE_MAPPING_URL)),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                  assertThat(it["nomisCSIPId"]).isEqualTo(nomisCSIPReportId.toString())
                  assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPReportId)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("CSIP_REPORTS-UPDATED")
  inner class CSIPReportUpdated {

    @Nested
    @DisplayName("CSIP_REPORTS-UPDATED - When csip was updated in DPS")
    inner class CSIPDPSUpdated {

      @BeforeEach
      fun setUp() {
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipEvent(eventType = "CSIP_REPORTS-UPDATED", auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("csip-synchronisation-updated-skipped"),
            check {
              assertThat(it["nomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
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
    @DisplayName("CSIP_REPORTS-UPDATED - When csip was updated in Nomis")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIP()
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(status = NOT_FOUND)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP_REPORTS-UPDATED", auditModuleName = "OIDCSIPN"),
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

      @Nested
      @DisplayName("Happy Path")
      inner class HappyPath {
        private val dpsCSIPId = "a4725216-892d-4325-bc18-f74d95f3bca2"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPId)
          csipMappingApi.stubGetFullMappingByDpsReportId(dpsCSIPId = dpsCSIPId)
          csipDpsApi.stubSyncCSIPReportNoMappingUpdates()
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP_REPORTS-UPDATED", auditModuleName = "OIDCSIPN"),
          )
          waitForAnyProcessingToComplete("csip-synchronisation-updated-success")
        }

        @Test
        fun `will retrieve details about the csip from NOMIS`() {
          csipNomisApi.verify(getRequestedFor(urlEqualTo(NOMIS_API_URL)))
        }

        @Test
        fun `will check the csip report exists`() {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/csip/nomis-csip-id/$NOMIS_CSIP_ID")))
        }

        @Test
        fun `will request full csip mapping`() {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPId/all")))
        }

        @Test
        fun `will update DPS with the changes specific to the OIDCSIPN screen`() {
          csipDpsApi.verify(
            putRequestedFor(urlEqualTo("/sync/csip-records"))
              .withRequestBodyJsonPath("id", dpsCSIPId)
              .withRequestBodyJsonPath("legacyId", "1234")
              .withRequestBodyJsonPath("logCode", "ASI-001")
              .withRequestBodyJsonPath("prisonNumber", "A1234BC")
              .withRequestBodyJsonPath("actionedAt", "2024-04-01T10:32:12.867081")
              .withRequestBodyJsonPath("actionedBy", "JSMITH"),
          )
        }

        @Test
        fun `will not create a mapping between the two records or its children`() {
          mappingApi.verify(0, postRequestedFor(urlPathEqualTo(CSIP_CREATE_CHILD_MAPPINGS_URL)))
        }

        @Test
        fun `will create telemetry tracking the update`() {
          verify(telemetryClient).trackEvent(
            eq("csip-synchronisation-updated-success"),
            check {
              assertThat(it["nomisCSIPId"]).isEqualTo("$NOMIS_CSIP_ID")
              assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPId)
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
              assertThat(it).doesNotContainKey("mapping")
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        private val dpsCSIPReportId = "a04f7a8d-61aa-400c-9395-f4dc62f36aff"
        private val nomisCSIPReportId = 878L

        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIP(nomisCSIPId = nomisCSIPReportId)
          csipMappingApi.stubGetByNomisId(nomisCSIPId = nomisCSIPReportId, dpsCSIPId = dpsCSIPReportId)
          csipMappingApi.stubGetFullMappingByDpsReportId(nomisCSIPId = nomisCSIPReportId, dpsCSIPId = dpsCSIPReportId)
          csipDpsApi.stubSyncCSIPReport(dpsCSIPId = dpsCSIPReportId)
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            csipMappingApi.stubPostMappingFailureFollowedBySuccess(CSIP_CREATE_CHILD_MAPPINGS_URL)
            awsSqsCSIPOffenderEventsClient.sendMessage(
              csipQueueOffenderEventsUrl,
              csipEvent(eventType = "CSIP_REPORTS-UPDATED", csipReportId = nomisCSIPReportId.toString()),
            )
          }

          @Test
          fun `will create csip in DPS`() {
            await untilAsserted {
              csipDpsApi.verify(putRequestedFor(urlEqualTo("/sync/csip-records")))
            }
          }

          @Test
          fun `will attempt to create child mapping two times and succeed`() {
            await untilAsserted {
              csipMappingApi.verify(
                exactly(2),
                postRequestedFor(urlPathEqualTo(CSIP_CREATE_CHILD_MAPPINGS_URL))
                  .withRequestBodyJsonPath("dpsCSIPReportId", dpsCSIPReportId)
                  .withRequestBodyJsonPath("nomisCSIPReportId", nomisCSIPReportId)
                  .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
              )
            }

            assertThat(
              awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(0)
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-synchronisation-updated-success"),
                check {
                  assertThat(it["nomisCSIPId"]).isEqualTo(nomisCSIPReportId.toString())
                  assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                  assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPReportId)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-mapping-updated-synchronisation-success"),
                check {
                  assertThat(it["nomisCSIPId"]).isEqualTo(nomisCSIPReportId.toString())
                  assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                  assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPReportId)
                },
                isNull(),
              )
            }
          }
        }

        @Nested
        @DisplayName("Fails constantly")
        inner class FailsConstantly {
          @BeforeEach
          fun setUp() {
            csipMappingApi.stubPostMapping(status = INTERNAL_SERVER_ERROR, url = CSIP_CREATE_CHILD_MAPPINGS_URL)
            awsSqsCSIPOffenderEventsClient.sendMessage(
              csipQueueOffenderEventsUrl,
              csipEvent(eventType = "CSIP_REPORTS-UPDATED", csipReportId = nomisCSIPReportId.toString()),
            )
            await untilCallTo {
              awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create csip in DPS`() {
            await untilAsserted {
              csipDpsApi.verify(
                1,
                putRequestedFor(urlPathEqualTo("/sync/csip-records")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            csipMappingApi.verify(
              exactly(3),
              postRequestedFor(urlPathEqualTo(CSIP_CREATE_CHILD_MAPPINGS_URL)),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-synchronisation-updated-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                  assertThat(it["nomisCSIPId"]).isEqualTo(nomisCSIPReportId.toString())
                  assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPReportId)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }
          }
        }
      }
    }
  }

  @Nested
  inner class BookingMoved {
    @Nested
    inner class HappyPath {
      val bookingId = BOOKING_ID
      private val movedToNomsNumber = "A1234KT"
      private val movedFromNomsNumber = "A1000KT"
      private val dpsCSIPId1 = "956d4326-b0c3-47ac-ab12-f0165109a6c5"
      private val dpsCSIPId2 = "f612a10f-4827-4022-be96-d882193dfabd"

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIPIdsForBooking(bookingId = bookingId)
        csipMappingApi.stubGetByMappingsNomisId(dpsCSIPId1 = dpsCSIPId1, dpsCSIPId2 = dpsCSIPId2)
        csipDpsApi.stubMoveOffenderForCSIP()

        awsSqsSentencingOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          bookingMovedDomainEvent(
            bookingId = bookingId,
            movedFromNomsNumber = movedFromNomsNumber,
            movedToNomsNumber = movedToNomsNumber,
          ),
        )
        waitForAnyProcessingToComplete("csip-booking-moved-success")
      }

      @Test
      fun `will retrieve csip ids for the bookings that has changed`() {
        csipNomisApi.verify(getRequestedFor(urlPathEqualTo("/csip/booking/$bookingId")))
      }

      @Test
      fun `will request dps mappings for NOMIS csip mappings`() {
        csipMappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/csip/nomis-csip-id"))
            .withQueryParam("nomisCSIPId", equalTo("1234"))
            .withQueryParam("nomisCSIPId", equalTo("5678")),
        )
      }

      @Test
      fun `will send a csip move to DPS`() {
        csipDpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/csip-records/move"))
            .withRequestBodyJsonPath("fromPrisonNumber", movedFromNomsNumber)
            .withRequestBodyJsonPath("toPrisonNumber", movedToNomsNumber)
            .withRequestBodyJsonPath("recordUuids[0]", dpsCSIPId1)
            .withRequestBodyJsonPath("recordUuids[1]", dpsCSIPId2),
        )
      }

      @Test
      fun `will track telemetry for the booking move`() {
        verify(telemetryClient).trackEvent(
          eq("csip-booking-moved-success"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["movedToNomsNumber"]).isEqualTo(movedToNomsNumber)
            assertThat(it["movedFromNomsNumber"]).isEqualTo(movedFromNomsNumber)
            assertThat(it["count"]).isEqualTo("2")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathWithNoCSIPs {
      private val bookingId = BOOKING_ID
      private val movedToNomsNumber = "A1234KT"
      private val movedFromNomsNumber = "A1000KT"

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIPIdsForBookingNoCsips(bookingId = bookingId)

        awsSqsSentencingOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          bookingMovedDomainEvent(
            bookingId = bookingId,
            movedFromNomsNumber = movedFromNomsNumber,
            movedToNomsNumber = movedToNomsNumber,
          ),
        )
        waitForAnyProcessingToComplete("csip-booking-moved-ignored")
      }

      @Test
      fun `will retrieve csip ids for the bookings that has changed`() {
        csipNomisApi.verify(getRequestedFor(urlPathEqualTo("/csip/booking/$bookingId")))
      }

      @Test
      fun `will not have any interaction with dps`() {
        csipDpsApi.verify(0, putRequestedFor(anyUrl()))
      }

      @Test
      fun `will not have any interaction with the mapping service`() {
        csipMappingApi.verify(0, getRequestedFor(anyUrl()))
      }

      @Test
      fun `will track telemetry to show moved ignored`() {
        verify(telemetryClient).trackEvent(
          eq("csip-booking-moved-ignored"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["movedToNomsNumber"]).isEqualTo(movedToNomsNumber)
            assertThat(it["movedFromNomsNumber"]).isEqualTo(movedFromNomsNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class BookingNotFoundInNomis {
      val bookingId = BOOKING_ID
      private val movedToNomsNumber = "A1234KT"
      private val movedFromNomsNumber = "A1000KT"

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIPIdsForBooking(NOT_FOUND)

        awsSqsSentencingOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          bookingMovedDomainEvent(
            bookingId = bookingId,
            movedFromNomsNumber = movedFromNomsNumber,
            movedToNomsNumber = movedToNomsNumber,
          ),
        )
        await untilCallTo {
          awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get()
        } matches { it == 1 }
      }

      @Test
      fun `will attempt to retrieve csip ids for the booking that has changed`() {
        csipNomisApi.verify(getRequestedFor(urlPathEqualTo("/csip/booking/$bookingId")))
      }

      @Test
      fun `will not have any interaction with the mapping service`() {
        csipMappingApi.verify(0, getRequestedFor(anyUrl()))
      }

      @Test
      fun `will not have any interaction with dps`() {
        csipDpsApi.verify(0, putRequestedFor(anyUrl()))
      }

      @Test
      fun `will track telemetry to show moved failed - twice allowing for retries`() {
        verify(telemetryClient, Times(2)).trackEvent(
          eq("csip-booking-moved-failed"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["movedToNomsNumber"]).isEqualTo(movedToNomsNumber)
            assertThat(it["movedFromNomsNumber"]).isEqualTo(movedFromNomsNumber)
            assertThat(it["error"]).contains("Not Found")
          },
          isNull(),
        )
      }

      @Test
      fun `will put the message back on the queue`() {
        assertThat(
          awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get(),
        ).isEqualTo(1)
      }
    }

    @Nested
    inner class MappingsNotFound {
      val bookingId = BOOKING_ID
      private val movedToNomsNumber = "A1234KT"
      private val movedFromNomsNumber = "A1000KT"

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIPIdsForBooking(bookingId = bookingId)
        csipMappingApi.stubGetByMappingsNomisId(NOT_FOUND)

        awsSqsSentencingOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          bookingMovedDomainEvent(
            bookingId = bookingId,
            movedFromNomsNumber = movedFromNomsNumber,
            movedToNomsNumber = movedToNomsNumber,
          ),
        )
        await untilCallTo {
          awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get()
        } matches { it == 1 }
      }

      @Test
      fun `will retrieve csip ids for the bookings that has changed`() {
        csipNomisApi.verify(getRequestedFor(urlPathEqualTo("/csip/booking/$bookingId")))
      }

      @Test
      fun `will request dps mappings for NOMIS csip mappings`() {
        csipMappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/csip/nomis-csip-id"))
            .withQueryParam("nomisCSIPId", equalTo("1234"))
            .withQueryParam("nomisCSIPId", equalTo("5678")),
        )
      }

      @Test
      fun `will not have any interaction with dps`() {
        csipDpsApi.verify(0, putRequestedFor(anyUrl()))
      }

      @Test
      fun `will track telemetry to show moved failed - twice allowing for retries`() {
        verify(telemetryClient, Times(2)).trackEvent(
          eq("csip-booking-moved-failed"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["movedToNomsNumber"]).isEqualTo(movedToNomsNumber)
            assertThat(it["movedFromNomsNumber"]).isEqualTo(movedFromNomsNumber)
            assertThat(it["error"]).contains("Not Found")
          },
          isNull(),
        )
      }

      @Test
      fun `will put the message back on the queue`() {
        assertThat(
          awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get(),
        ).isEqualTo(1)
      }
    }

    @Nested
    inner class DPSUpdateFailed {
      val bookingId = BOOKING_ID
      private val movedToNomsNumber = "A1234KT"
      private val movedFromNomsNumber = "A1000KT"
      private val dpsCSIPId1 = "956d4326-b0c3-47ac-ab12-f0165109a6cc"
      private val dpsCSIPId2 = "f612a10f-4827-4022-be96-d882193dfadd"

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIPIdsForBooking(bookingId = bookingId)
        csipMappingApi.stubGetByMappingsNomisId(dpsCSIPId1 = dpsCSIPId1, dpsCSIPId2 = dpsCSIPId2)
        csipDpsApi.stubMoveOffenderForCSIP(INTERNAL_SERVER_ERROR)

        awsSqsSentencingOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          bookingMovedDomainEvent(
            bookingId = bookingId,
            movedFromNomsNumber = movedFromNomsNumber,
            movedToNomsNumber = movedToNomsNumber,
          ),
        )
        await untilCallTo {
          awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get()
        } matches { it == 1 }
      }

      @Test
      fun `will retrieve csip ids for the bookings that has changed`() {
        csipNomisApi.verify(getRequestedFor(urlPathEqualTo("/csip/booking/$bookingId")))
      }

      @Test
      fun `will request dps mappings for NOMIS csip mappings`() {
        csipMappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/csip/nomis-csip-id"))
            .withQueryParam("nomisCSIPId", equalTo("1234"))
            .withQueryParam("nomisCSIPId", equalTo("5678")),
        )
      }

      @Test
      fun `will send a csip move to DPS`() {
        csipDpsApi.verify(
          putRequestedFor(urlPathEqualTo("/sync/csip-records/move"))
            .withRequestBodyJsonPath("fromPrisonNumber", movedFromNomsNumber)
            .withRequestBodyJsonPath("toPrisonNumber", movedToNomsNumber)
            .withRequestBodyJsonPath("recordUuids[0]", dpsCSIPId1)
            .withRequestBodyJsonPath("recordUuids[1]", dpsCSIPId2),
        )
      }

      @Test
      fun `will track telemetry to show moved failed - twice allowing for retries`() {
        verify(telemetryClient, Times(2)).trackEvent(
          eq("csip-booking-moved-failed"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["movedToNomsNumber"]).isEqualTo(movedToNomsNumber)
            assertThat(it["movedFromNomsNumber"]).isEqualTo(movedFromNomsNumber)
            assertThat(it["error"]).contains("500 Internal Server")
          },
          isNull(),
        )
      }

      @Test
      fun `will put the message back on the queue`() {
        assertThat(
          awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get(),
        ).isEqualTo(1)
      }
    }
  }

  @Nested
  @DisplayName("CSIP_REPORTS-DELETED")
  inner class CSIPReportDeleted {
    @Nested
    @DisplayName("CSIP_REPORTS-DELETED - When csip was deleted in either NOMIS or DPS")
    inner class DeletedInEitherNOMISOrDPS {

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(NOT_FOUND)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP_REPORTS-DELETED"),
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

          csipDpsApi.stubCSIPDelete(dpsCSIPId = dpsCSIPId)
          csipMappingApi.stubDeleteCSIPReportMapping(dpsCSIPId = dpsCSIPId)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP_REPORTS-DELETED"),
          )
        }

        @Test
        fun `will delete CSIP in DPS`() {
          await untilAsserted {
            csipDpsApi.verify(
              1,
              WireMock.deleteRequestedFor(urlPathEqualTo("/sync/csip-records/$dpsCSIPId"))
                .withRequestBodyJsonPath("actionedAt", "2024-06-11T10:39:17"),
            )
          }
        }

        @Test
        fun `will delete CSIP mapping`() {
          await untilAsserted {
            csipMappingApi.verify(
              1,
              WireMock.deleteRequestedFor(urlPathEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPId/all")),
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
          csipDpsApi.stubCSIPDelete(dpsCSIPId = dpsCSIPId)
          csipMappingApi.stubDeleteCSIPReportMapping(status = INTERNAL_SERVER_ERROR)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP_REPORTS-DELETED"),
          )
        }

        @Test
        fun `will delete csip in DPS`() {
          await untilAsserted {
            csipDpsApi.verify(
              1,
              WireMock.deleteRequestedFor(urlPathEqualTo("/sync/csip-records/$dpsCSIPId")),
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
              WireMock.deleteRequestedFor(urlPathEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPId/all")),
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

fun SqsAsyncClient.waitForMessageCountOnQueue(queueUrl: String, messageCount: Int) = await untilCallTo {
  countAllMessagesOnQueue(queueUrl)
    .get()
} matches { it == messageCount }

fun csipEvent(
  eventType: String = "CSIP_REPORTS-INSERTED",
  csipReportId: String = "$NOMIS_CSIP_ID",
  auditModuleName: String = "OIDCSIPN",
) = """
  {
    "Type" : "Notification",
    "MessageId" : "7bdec840-69e5-5163-8013-967eb63d3d26",
    "TopicArn" : "arn:aws:sns:eu-west-2:754256621582:cloud-platform-Digital-Prison-Services-f221e27fcfcf78f6ab4f4c3cc165eee7",
    "Message" : "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-06-11T10:39:17\",\"bookingId\":1215724,\"offenderIdDisplay\":\"A1234BC\",\"nomisEventType\":\"CSIP_REPORTS-INSERTED\",\"rootOffenderId\":2581911,\"csipReportId\":\"$csipReportId\",\"auditModuleName\":\"$auditModuleName\"}",
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
