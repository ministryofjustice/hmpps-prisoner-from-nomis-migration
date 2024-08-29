package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
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
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingApiMockServer.Companion.CSIP_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingApiMockServer.Companion.CSIP_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.AbstractMap.SimpleEntry

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
  @DisplayName("CSIP_REPORTS-INSERTED")
  inner class CSIPCreated {

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
      inner class WhenCreateByNomisSuccess {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIP()
          csipMappingApi.stubGetByNomisId(HttpStatus.NOT_FOUND)
          csipDpsApi.stubInsertCSIPReport()
          mappingApi.stubMappingCreate(CSIP_CREATE_MAPPING_URL)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP_REPORTS-INSERTED"),
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
            csipDpsApi.verify(
              postRequestedFor(urlPathEqualTo("/prisoners/A1234BC/csip-records"))
                .withHeader("Username", equalTo("JSMITH"))
                .withRequestBody(matchingJsonPath("referral.incidentDate", equalTo("2024-06-12")))
                .withRequestBody(matchingJsonPath("referral.incidentTime", equalTo("10:32:12"))),
            )
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
          csipNomisApi.stubGetCSIP(HttpStatus.NOT_FOUND)

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
          csipMappingApi.stubGetByNomisId(HttpStatus.NOT_FOUND)
          csipDpsApi.stubInsertCSIPReport(duplicateDPSCSIPId)
          csipMappingApi.stubCSIPMappingCreateConflict()

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipEvent(eventType = "CSIP_REPORTS-INSERTED"),
          )

          // wait for all mappings to be created before verifying
          await untilCallTo { mappingApi.createMappingCount(CSIP_CREATE_MAPPING_URL) } matches { it == 1 }

          // check that one csip is created
          assertThat(csipDpsApi.createCSIPSyncCount()).isEqualTo(1)

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
          assertThat(csipDpsApi.createCSIPSyncCount()).isEqualTo(0)
          csipDpsApi.verify(0, postRequestedFor(anyUrl()))

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
  @DisplayName("CSIP_REPORTS-DELETED")
  inner class CSIPDeleted {
    @Nested
    @DisplayName("CSIP_REPORTS-DELETED - When csip was deleted in either NOMIS or DPS")
    inner class DeletedInEitherNOMISOrDPS {

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(HttpStatus.NOT_FOUND)
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
              WireMock.deleteRequestedFor(urlPathEqualTo("/csip-records/$dpsCSIPId")),
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
          csipDpsApi.stubCSIPDelete(dpsCSIPId = dpsCSIPId)
          csipMappingApi.stubDeleteCSIPReportMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
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
              WireMock.deleteRequestedFor(urlPathEqualTo("/csip-records/$dpsCSIPId")),
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
      // TODO Add this in once coding is done
      /*
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
       */

      csipNomisApi.verify(exactly(0), getRequestedFor(anyUrl()))
      csipMappingApi.verify(exactly(0), getRequestedFor(anyUrl()))
      csipDpsApi.verify(exactly(0), anyRequestedFor(anyUrl()))
    }
  }

  @Nested
  @DisplayName("CSIP_REPORTS-UPDATED - Safer Custody Screening (OIDCSIPS)")
  inner class CSIPSCSUpdated {

    @Nested
    @DisplayName("When CSIP SCS was update in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIP()
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipEvent(eventType = "CSIP_REPORTS-UPDATED", auditModuleName = "OIDCSIPS"),
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("csip-scs-synchronisation-created-failed"),
              check {
                assertThat(it["nomisCSIPId"]).isEqualTo(NOMIS_CSIP_ID.toString())
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
      @DisplayName("When mapping does exist")
      inner class MappingExists {
        private val dpsCSIPId = "a4725216-892d-4325-bc18-f74d95f3bcaa"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPId)
          csipDpsApi.stubInsertCSIPSCS(dpsCSIPId = dpsCSIPId)
        }

        @Test
        fun `will update DPS with the changes`() {
          await untilAsserted {
            csipDpsApi.verify(
              1,
              postRequestedFor(urlPathEqualTo("/csip-records/$dpsCSIPId/referral/safer-custody-screening"))
                .withHeader("Username", equalTo("FRED_ADM"))
                .withRequestBody(matchingJsonPath("outcomeTypeCode", equalTo("CUR")))
                .withRequestBody(matchingJsonPath("date", equalTo("2024-04-08")))
                .withRequestBody(matchingJsonPath("reasonForDecision", equalTo("There is a reason for the decision - it goes here")))
                .withRequestBody(matchingJsonPath("recordedBy", equalTo("FRED_ADM")))
                .withRequestBody(matchingJsonPath("recordedByDisplayName", equalTo("Fred Admin"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-scs-synchronisation-created-success"),
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

  @Nested
  @DisplayName("CSIP_REPORTS-UPDATED - Initial CSIP Record Screen (OIDCSIPN)")
  inner class CSIPInitialScreenUpdated {

    @Nested
    @DisplayName("When CSIP Initial screen was update in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIP()
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipEvent(eventType = "CSIP_REPORTS-UPDATED", auditModuleName = "OIDCSIPN"),
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)
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
      @DisplayName("When mapping does exist")
      inner class MappingExists {
        private val dpsCSIPId = "a4725216-892d-4325-bc18-f74d95f3bca2"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPId)
          csipDpsApi.stubUpdateCSIPReport(dpsCSIPId = dpsCSIPId)
          waitForAnyProcessingToComplete("csip-synchronisation-updated-success")
        }

        @Test
        fun `will update DPS with the changes specific to the OIDCSIPN screen`() {
          csipDpsApi.verify(
            1,
            patchRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral"))
              .withHeader("Username", equalTo("JSMITH"))
              .withRequestBody(matchingJsonPath("logCode", equalTo("ASI-001")))
              .withRequestBody(matchingJsonPath("referral.incidentDate", equalTo("2024-06-12")))
              .withRequestBody(matchingJsonPath("referral.incidentTypeCode", equalTo("INT")))
              .withRequestBody(matchingJsonPath("referral.incidentLocationCode", equalTo("LIB")))
              .withRequestBody(matchingJsonPath("referral.referredBy", equalTo("JIM_ADM")))
              .withRequestBody(matchingJsonPath("referral.refererAreaCode", equalTo("EDU")))
              .withRequestBody(matchingJsonPath("referral.incidentTime", equalTo("10:32:12")))
              .withRequestBody(matchingJsonPath("referral.isProactiveReferral", equalTo("true")))
              .withRequestBody(matchingJsonPath("referral.isStaffAssaulted", equalTo("true")))
              .withRequestBody(matchingJsonPath("referral.assaultedStaffName", equalTo("Fred Jones"))),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("csip-synchronisation-updated-success"),
            check {
              assertThat(it["nomisCSIPId"]).isEqualTo(NOMIS_CSIP_ID.toString())
              assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPId)
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("CSIP_REPORTS-UPDATED - Referral Continue CSIP Record Screen (OIDCSIPC)")
  inner class CSIPReferralContScreenUpdated {

    @Nested
    @DisplayName("When CSIP Referral Continue screen was update in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIP()
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipEvent(eventType = "CSIP_REPORTS-UPDATED", auditModuleName = "OIDCSIPC"),
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)
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
      @DisplayName("When mapping does exist")
      inner class MappingExists {
        private val dpsCSIPId = "a4725216-892d-4325-bc18-f74d95f3bca2"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPId)
          csipDpsApi.stubUpdateCSIPReport(dpsCSIPId = dpsCSIPId)
          waitForAnyProcessingToComplete("csip-synchronisation-updated-success")
        }

        @Test
        fun `will update DPS with the changes specific to the OIDCSIPC screen`() {
          csipDpsApi.verify(
            1,
            patchRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral"))
              .withHeader("Username", equalTo("JSMITH"))
              // Whilst not part of the Referral Continue screen, these are mandatory to the update
              .withRequestBody(matchingJsonPath("referral.incidentDate", equalTo("2024-06-12")))
              .withRequestBody(matchingJsonPath("referral.incidentTypeCode", equalTo("INT")))
              .withRequestBody(matchingJsonPath("referral.incidentLocationCode", equalTo("LIB")))
              .withRequestBody(matchingJsonPath("referral.referredBy", equalTo("JIM_ADM")))
              .withRequestBody(matchingJsonPath("referral.refererAreaCode", equalTo("EDU")))
              .withRequestBody(matchingJsonPath("referral.incidentInvolvementCode", equalTo("PER")))
              .withRequestBody(matchingJsonPath("referral.descriptionOfConcern", equalTo("There was a worry about the offender")))
              .withRequestBody(matchingJsonPath("referral.knownReasons", equalTo("known reasons details go in here")))
              .withRequestBody(matchingJsonPath("referral.otherInformation", equalTo("other information goes in here")))
              .withRequestBody(matchingJsonPath("referral.isSaferCustodyTeamInformed", equalTo("NO")))
              .withRequestBody(matchingJsonPath("referral.isReferralComplete", equalTo("true"))),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("csip-synchronisation-updated-success"),
            check {
              assertThat(it["nomisCSIPId"]).isEqualTo(NOMIS_CSIP_ID.toString())
              assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPId)
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("CSIP_REPORTS-UPDATED - Investigation Screen (OIDCSIPI)")
  inner class CSIPInvestigationUpdated {

    @Nested
    @DisplayName("When CSIP investigation was update in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIP()
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipEvent(eventType = "CSIP_REPORTS-UPDATED", auditModuleName = "OIDCSIPI"),
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)
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
      @DisplayName("When mapping does exist")
      inner class MappingExists {
        private val dpsCSIPId = "a4725216-892d-4325-bc18-f74d95f3bcff"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPId)
          csipDpsApi.stubUpdateCSIPInvestigation(dpsCSIPId = dpsCSIPId)
          waitForAnyProcessingToComplete("csip-investigation-synchronisation-updated-success")
        }

        @Test
        fun `will update DPS with the changes specific to the OIDCSIPI screen`() {
          csipDpsApi.verify(
            1,
            putRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral/investigation"))
              .withHeader("Username", equalTo("JSMITH"))
              .withHeader("Source", equalTo("NOMIS"))
              .withRequestBody(matchingJsonPath("staffInvolved", equalTo("some people")))
              .withRequestBody(matchingJsonPath("evidenceSecured", equalTo("A piece of pipe")))
              .withRequestBody(matchingJsonPath("occurrenceReason", equalTo("bad behaviour")))
              .withRequestBody(matchingJsonPath("personsUsualBehaviour", equalTo("Good person")))
              .withRequestBody(matchingJsonPath("personsTrigger", equalTo("missed meal")))
              .withRequestBody(matchingJsonPath("protectiveFactors", equalTo("ensure taken to canteen"))),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("csip-investigation-synchronisation-updated-success"),
            check {
              assertThat(it["nomisCSIPId"]).isEqualTo(NOMIS_CSIP_ID.toString())
              assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPId)
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("CSIP_REPORTS-UPDATED - Decision Screen (OIDCSIPD)")
  inner class CSIPDecisionUpdated {

    @Nested
    @DisplayName("When CSIP Decision was update in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIP()
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipEvent(eventType = "CSIP_REPORTS-UPDATED", auditModuleName = "OIDCSIPD"),
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)
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
      @DisplayName("When mapping does exist")
      inner class MappingExists {
        private val dpsCSIPId = "a4725216-892d-4325-bc18-f74d95f3bccc"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPId)
          csipDpsApi.stubUpdateCSIPDecision(dpsCSIPId = dpsCSIPId)
          waitForAnyProcessingToComplete("csip-decision-synchronisation-updated-success")
        }

        @Test
        fun `will update DPS with the changes specific to the OIDCSIPD screen`() {
          csipDpsApi.verify(
            1,
            putRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/referral/decision-and-actions"))
              .withHeader("Username", equalTo("JSMITH"))
              .withHeader("Source", equalTo("NOMIS"))
              .withRequestBody(matchingJsonPath("outcomeTypeCode", equalTo("OPE")))
              .withRequestBody(matchingJsonPath("conclusion", equalTo("Offender needs help")))
              .withRequestBody(matchingJsonPath("signedOffByRoleCode", equalTo("CUSTMAN")))
              .withRequestBody(matchingJsonPath("recordedBy", equalTo("FRED_ADM")))
              .withRequestBody(matchingJsonPath("recordedByDisplayName", equalTo("Fred Admin")))
              .withRequestBody(matchingJsonPath("date", equalTo("2024-04-08")))
              .withRequestBody(matchingJsonPath("actions[0]", equalTo("NON_ASSOCIATIONS_UPDATED")))
              .withRequestBody(matchingJsonPath("actions[1]", equalTo("OBSERVATION_BOOK")))
              .withRequestBody(matchingJsonPath("actions[2]", equalTo("SERVICE_REFERRAL")))
              .withRequestBody(matchingJsonPath("actionOther", equalTo("Some other info here"))),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("csip-decision-synchronisation-updated-success"),
            check {
              assertThat(it["nomisCSIPId"]).isEqualTo(NOMIS_CSIP_ID.toString())
              assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPId)
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("CSIP_REPORTS-UPDATED - Plan Screen (OIDCSIPP)")
  inner class CSIPPlanUpdated {

    @Nested
    @DisplayName("When CSIP Plan was update in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIP()
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipEvent(eventType = "CSIP_REPORTS-UPDATED", auditModuleName = "OIDCSIPP"),
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(status = HttpStatus.NOT_FOUND)
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
      @DisplayName("When mapping does exist")
      inner class MappingExists {
        private val dpsCSIPId = "a4725216-892d-4325-bc18-f74d95f3bccc"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPId)
          csipDpsApi.stubUpdateCSIPPlan(dpsCSIPId = dpsCSIPId)
          waitForAnyProcessingToComplete("csip-plan-synchronisation-updated-success")
        }

        @Test
        fun `will update DPS with the changes specific to the OIDCSIPP screen`() {
          csipDpsApi.verify(
            1,
            putRequestedFor(urlEqualTo("/csip-records/$dpsCSIPId/plan"))
              .withHeader("Username", equalTo("JSMITH"))
              .withHeader("Source", equalTo("NOMIS"))
              .withRequestBody(matchingJsonPath("caseManager", equalTo("C Jones")))
              .withRequestBody(matchingJsonPath("reasonForPlan", equalTo("helper")))
              .withRequestBody(matchingJsonPath("firstCaseReviewDate", equalTo("2024-04-15"))),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("csip-plan-synchronisation-updated-success"),
            check {
              assertThat(it["nomisCSIPId"]).isEqualTo(NOMIS_CSIP_ID.toString())
              assertThat(it["dpsCSIPId"]).isEqualTo(dpsCSIPId)
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
            },
            isNull(),
          )
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
