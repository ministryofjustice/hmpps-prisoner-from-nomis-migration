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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension.Companion.csipApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

private const val NOMIS_CSIP_ID = 1234L

class CSIPFactorSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var csipNomisApi: CSIPNomisApiMockServer

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  @DisplayName("CSIP_FACTORS-INSERTED")
  inner class CSIPFactorCreated {

    @Nested
    inner class WhenCreateByDPS {
      private val nomisCSIPFactorId = 543L

      @BeforeEach
      fun setUp() {
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCSIPFactorId.toString(), auditModuleName = "DPS_SYNCHRONISATION"),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("csip-factor-synchronisation-skipped"),
            check {
              assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
              assertThat(it["nomisCSIPReportId"]).isEqualTo(NOMIS_CSIP_ID.toString())
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
      private val nomisCSIPFactorId = 654L
      private val nomisCSIPReportId = NOMIS_CSIP_ID
      val dpsCSIPReportId = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5"
      val dpsCSIPFactorId = "e07fdbee-1463-4c7e-a374-aae2445845be"

      @Nested
      @DisplayName("When Create CSIP Factor Happy Path")
      inner class WhenCreateByNomisSuccess {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIPFactor(nomisCSIPFactorId)
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPReportId) // Needed to ensure we have the uuid for dps
          csipMappingApi.stubGetFactorByNomisId(HttpStatus.NOT_FOUND)
          csipApi.stubCSIPFactorInsert(dpsCSIPReportId, dpsCSIPFactorId)
          mappingApi.stubMappingCreate("/mapping/csip/factors")

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCSIPFactorId.toString()),
          )
          waitForAnyProcessingToComplete("csip-factor-synchronisation-created-success")
        }

        @Test
        fun `will retrieve details about the csip from NOMIS`() {
          csipNomisApi.verify(getRequestedFor(urlEqualTo("/csip/factors/$nomisCSIPFactorId")))
        }

        @Test
        fun `will retrieve mapping to determine the associated csip report`() {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/csip/nomis-csip-id/$nomisCSIPReportId")))
        }

        @Test
        fun `will retrieve mapping to check if this is a new csip factor`() {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/csip/factors/nomis-csip-factor-id/$nomisCSIPFactorId")))
        }

        @Test
        fun `will create the csip factor in the csip service`() {
          csipApi.verify(
            postRequestedFor(urlPathEqualTo("/csip-records/$dpsCSIPReportId/referral/contributory-factors"))
              .withHeader("Username", equalTo("JSMITH")),
          )
        }

        @Test
        fun `will create a mapping between the two records`() {
          mappingApi.verify(
            postRequestedFor(urlPathEqualTo("/mapping/csip/factors"))
              .withRequestBody(matchingJsonPath("dpsCSIPFactorId", equalTo(dpsCSIPFactorId)))
              .withRequestBody(matchingJsonPath("nomisCSIPFactorId", equalTo(nomisCSIPFactorId.toString()))),
          )
        }

        @Test
        fun `will create telemetry tracking the create`() {
          verify(telemetryClient).trackEvent(
            eq("csip-factor-synchronisation-created-success"),
            check {
              assertThat(it["nomisCSIPReportId"]).isEqualTo(nomisCSIPReportId.toString())
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
              assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
              assertThat(it["dpsCSIPFactorId"]).isEqualTo(dpsCSIPFactorId)
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When Nomis has no CSIP Factor")
      inner class WhenNomisHasNoCSIPFactor {
        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIPFactor(HttpStatus.NOT_FOUND)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCSIPFactorId.toString()),
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
          csipNomisApi.stubGetCSIPFactor(nomisCSIPFactorId)
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPReportId) // Needed to ensure we have the uuid for dps
          csipMappingApi.stubGetFactorByNomisId(HttpStatus.NOT_FOUND)
          csipApi.stubCSIPFactorInsert(dpsCSIPReportId, duplicateDPSCSIPFactorId)
          csipMappingApi.stubCSIPFactorMappingCreateConflict(
            nomisCSIPFactorId = nomisCSIPFactorId,
            duplicateDPSCSIPFactorId = duplicateDPSCSIPFactorId,
            existingDPSCSIPFactorId = dpsCSIPFactorId,
          )

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCSIPFactorId.toString()),
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
                assertThat(it["existingNomisCSIPFactorId"]).isEqualTo("$nomisCSIPFactorId")
                assertThat(it["duplicateNomisCSIPFactorId"]).isEqualTo("$nomisCSIPFactorId")
                assertThat(it["existingDPSCSIPFactorId"]).isEqualTo(dpsCSIPFactorId)
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
          csipNomisApi.stubGetCSIPFactor(nomisCSIPFactorId)
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPReportId) // Needed to ensure we have the uuid for dps
          csipMappingApi.stubGetFactorByNomisId(nomisCSIPFactorId = nomisCSIPFactorId, dpsCSIPFactorId = dpsCSIPFactorId)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCSIPFactorId.toString()),
          )

          // check that no CSIPs are created
          assertThat(csipApi.createCSIPFactorSyncCount()).isEqualTo(0)
          csipApi.verify(0, postRequestedFor(anyUrl()))

          // doesn't try to create a new mapping
          csipMappingApi.verify(0, postRequestedFor(anyUrl()))

          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-factor-synchronisation-created-ignored"),
              check {
                assertThat(it["dpsCSIPFactorId"]).isEqualTo(dpsCSIPFactorId)
                assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
                assertThat(it["reason"]).isEqualTo("CSIP Factor already mapped")
                assertThat(it["migrationId"]).isNull()
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When no CSIP Report Mapping for the CSIP Factor has been created")
      inner class WhenAssociatedReportMappingDoesNotYetExist {

        @Test
        internal fun `it will put the message on the dlq`() {
          csipNomisApi.stubGetCSIPFactor(nomisCSIPFactorId)
          csipMappingApi.stubGetByNomisId(HttpStatus.NOT_FOUND)

          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCSIPFactorId.toString()),
          )

          awsSqsCSIPOffenderEventDlqClient.waitForMessageCountOnQueue(csipQueueOffenderEventsDlqUrl, 1)

          // check that no CSIPs are created and no posts of any kind are sent
          assertThat(csipApi.createCSIPFactorSyncCount()).isEqualTo(0)
          csipApi.verify(0, postRequestedFor(anyUrl()))

          // doesn't try to create a new mapping
          csipMappingApi.verify(0, postRequestedFor(anyUrl()))

          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("csip-factor-synchronisation-created-failed"),
              check {
                assertThat(it["nomisCSIPReportId"]).isEqualTo("$NOMIS_CSIP_ID")
                assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
                assertThat(it["reason"]).isEqualTo("CSIP Report for CSIP factor not mapped")
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
        private val dpsCSIPFactorId = "a04f7a8d-61aa-400c-9395-f4dc62f36a11"

        @BeforeEach
        fun setUp() {
          csipNomisApi.stubGetCSIPFactor(nomisCSIPFactorId)
          csipMappingApi.stubGetByNomisId(dpsCSIPId = dpsCSIPReportId) // Needed to ensure we have the uuid for dps
          csipMappingApi.stubGetFactorByNomisId(HttpStatus.NOT_FOUND)
          csipApi.stubCSIPFactorInsert(dpsCSIPReportId, dpsCSIPFactorId)
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/csip/factors")
            awsSqsCSIPOffenderEventsClient.sendMessage(
              csipQueueOffenderEventsUrl,
              csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCSIPFactorId.toString()),
            )
          }

          @Test
          fun `will create csip factor in DPS`() {
            await untilAsserted {
              csipApi.verify(
                postRequestedFor(urlPathEqualTo("/csip-records/$dpsCSIPReportId/referral/contributory-factors"))
                  .withRequestBody(matchingJsonPath("factorTypeCode", equalTo("BUL")))
                  .withRequestBody(matchingJsonPath("comment", equalTo("Offender causes trouble"))),
              )
            }
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            await untilAsserted {
              csipMappingApi.verify(
                exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/csip/factors"))
                  .withRequestBody(matchingJsonPath("dpsCSIPFactorId", equalTo(dpsCSIPFactorId)))
                  .withRequestBody(matchingJsonPath("nomisCSIPFactorId", equalTo(nomisCSIPFactorId.toString())))
                  .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
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
                eq("csip-factor-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                  assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
                  assertThat(it["nomisCSIPReportId"]).isEqualTo(nomisCSIPReportId.toString())
                  assertThat(it["dpsCSIPFactorId"]).isEqualTo(dpsCSIPFactorId)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-factor-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                  assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
                  assertThat(it["dpsCSIPFactorId"]).isEqualTo(dpsCSIPFactorId)
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
            csipMappingApi.stubCreateFactorMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
            awsSqsCSIPOffenderEventsClient.sendMessage(
              csipQueueOffenderEventsUrl,
              csipFactorEvent(eventType = "CSIP_FACTORS-INSERTED", csipFactorId = nomisCSIPFactorId.toString()),
            )
            await untilCallTo {
              awsSqsCSIPOffenderEventDlqClient.countAllMessagesOnQueue(csipQueueOffenderEventsDlqUrl).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create csip factor in DPS`() {
            await untilAsserted {
              csipApi.verify(
                1,
                postRequestedFor(urlPathEqualTo("/csip-records/$dpsCSIPReportId/referral/contributory-factors"))
                  .withRequestBody(matchingJsonPath("factorTypeCode", equalTo("BUL")))
                  .withRequestBody(matchingJsonPath("comment", equalTo("Offender causes trouble"))),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            // Once and then twice via RETRY_CSIP_FACTOR_SYNCHRONISATION_MAPPING message
            csipMappingApi.verify(
              exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/csip/factors")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-factor-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                  assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
                  assertThat(it["nomisCSIPReportId"]).isEqualTo(nomisCSIPReportId.toString())
                  assertThat(it["dpsCSIPFactorId"]).isEqualTo(dpsCSIPFactorId)
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
  @DisplayName("CSIP_FACTORS-UPDATED")
  inner class CSIPFactorUpdated {
    @Nested
    @DisplayName("When csip factor was updated in DPS")
    inner class DPSUpdated {

      private val nomisCSIPFactorId = 12345L

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIPFactor(nomisCSIPFactorId = nomisCSIPFactorId)
        awsSqsCSIPOffenderEventsClient.sendMessage(
          csipQueueOffenderEventsUrl,
          csipFactorEvent(
            eventType = "CSIP_FACTORS-UPDATED",
            auditModuleName = "DPS_SYNCHRONISATION",
            csipFactorId = nomisCSIPFactorId.toString(),
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("csip-factor-synchronisation-updated-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A1234BC")
              assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
              assertThat(it["nomisCSIPReportId"]).isEqualTo(NOMIS_CSIP_ID.toString())
            },
            isNull(),
          )
        }

        // will not bother getting mapping
        csipMappingApi.verify(
          count = 0,
          getRequestedFor(WireMock.urlPathMatching("/mapping/csip/factors/nomis-factor-id/\\d+")),
        )
        // will not update the csip factor  in DPS
        csipApi.verify(
          0,
          WireMock.putRequestedFor(anyUrl()),
        )
      }
    }

    @Nested
    @DisplayName("When csip factor was updated in NOMIS")
    inner class NomisUpdated {
      private val nomisCSIPFactorId = 12345L

      @BeforeEach
      fun setUp() {
        csipNomisApi.stubGetCSIPFactor(
          nomisCSIPFactorId = nomisCSIPFactorId,
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetFactorByNomisId(status = HttpStatus.NOT_FOUND)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(
              eventType = "CSIP_FACTORS-UPDATED",
              csipFactorId = nomisCSIPFactorId.toString(),
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("csip-factor-synchronisation-updated-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
                assertThat(it["nomisCSIPReportId"]).isEqualTo(NOMIS_CSIP_ID.toString())
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
      inner class HappyPath {
        private val dpsCSIPFactorId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetFactorByNomisId(
            nomisCSIPFactorId = nomisCSIPFactorId,
            dpsCSIPFactorId = dpsCSIPFactorId,
          )
          csipApi.stubCSIPFactorUpdate(dpsCSIPFactorId = dpsCSIPFactorId)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(
              eventType = "CSIP_FACTORS-UPDATED",
              csipFactorId = nomisCSIPFactorId.toString(),
            ),
          )
          waitForAnyProcessingToComplete("csip-factor-synchronisation-updated-success")
        }

        @Test
        fun `will update (patch) DPS with the changes`() {
          await untilAsserted {
            csipApi.verify(
              1,
              WireMock.patchRequestedFor(urlPathEqualTo("/csip-records/referral/contributory-factors/$dpsCSIPFactorId"))
                .withRequestBody(matchingJsonPath("factorTypeCode", equalTo("BUL")))
                .withRequestBody(matchingJsonPath("comment", equalTo("Offender causes trouble"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-factor-synchronisation-updated-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A1234BC")
                assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
                assertThat(it["nomisCSIPReportId"]).isEqualTo(NOMIS_CSIP_ID.toString())
                assertThat(it["dpsCSIPFactorId"]).isEqualTo(dpsCSIPFactorId)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("CSIP_FACTORS-DELETED")
  inner class CSIPFactorDeleted {
    private val nomisCSIPFactorId = 343L

    @Nested
    @DisplayName("When csip factor was deleted")
    inner class DeletedInEitherNOMISOrDPS {

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetFactorByNomisId(HttpStatus.NOT_FOUND)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-DELETED", csipFactorId = nomisCSIPFactorId.toString()),
          )
        }

        @Test
        fun `telemetry added to track that the delete was ignored`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("csip-factor-synchronisation-deleted-ignored"),
              check {
                assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("Happy Path")
      inner class HappyPath {
        private val dpsCSIPFactorId = "c4d6fb09-fd27-42bc-a33e-5ca74ac510be"
        private val nomisCSIPFactorId = 987L

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetFactorByNomisId(nomisCSIPFactorId = nomisCSIPFactorId, dpsCSIPFactorId = dpsCSIPFactorId)

          csipApi.stubCSIPFactorDelete(dpsCSIPFactorId = dpsCSIPFactorId)
          csipMappingApi.stubDeleteFactorMapping(dpsCSIPFactorId = dpsCSIPFactorId)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-DELETED", csipFactorId = nomisCSIPFactorId.toString()),
          )

          waitForAnyProcessingToComplete("csip-factor-synchronisation-deleted-success")
        }

        @Test
        fun `will delete CSIP in DPS`() {
          csipApi.verify(
            1,
            WireMock.deleteRequestedFor(urlPathEqualTo("/csip-records/referral/contributory-factors/$dpsCSIPFactorId")),
          )
        }

        @Test
        fun `will delete CSIP mapping`() {
          csipMappingApi.verify(
            1,
            WireMock.deleteRequestedFor(urlPathEqualTo("/mapping/csip/factors/dps-csip-factor-id/$dpsCSIPFactorId")),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          verify(telemetryClient).trackEvent(
            eq("csip-factor-synchronisation-deleted-success"),
            check {
              assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
              assertThat(it["dpsCSIPFactorId"]).isEqualTo(dpsCSIPFactorId)
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
        private val nomisCSIPFactorId = 121L
        private val dpsCSIPFactorId = "a4725216-892d-4325-bc18-f74d95f3bca2"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetFactorByNomisId(nomisCSIPFactorId = nomisCSIPFactorId, dpsCSIPFactorId = dpsCSIPFactorId)
          csipApi.stubCSIPFactorDelete(dpsCSIPFactorId = dpsCSIPFactorId)
          csipMappingApi.stubDeleteFactorMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
          awsSqsCSIPOffenderEventsClient.sendMessage(
            csipQueueOffenderEventsUrl,
            csipFactorEvent(eventType = "CSIP_FACTORS-DELETED", csipFactorId = nomisCSIPFactorId.toString()),
          )
          waitForAnyProcessingToComplete("csip-factor-mapping-deleted-failed")
        }

        @Test
        fun `will delete csip in DPS`() {
          csipApi.verify(1, WireMock.deleteRequestedFor(urlPathEqualTo("/csip-records/referral/contributory-factors/$dpsCSIPFactorId")))
        }

        @Test
        fun `will try to delete CSIP mapping once and record failure`() {
          verify(telemetryClient).trackEvent(
            eq("csip-factor-mapping-deleted-failed"),
            any(),
            isNull(),
          )

          csipMappingApi.verify(
            1,
            WireMock.deleteRequestedFor(urlPathEqualTo("/mapping/csip/factors/dps-csip-factor-id/$dpsCSIPFactorId")),
          )
        }

        @Test
        fun `will eventually track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-factor-synchronisation-deleted-success"),
              check {
                assertThat(it["nomisCSIPFactorId"]).isEqualTo(nomisCSIPFactorId.toString())
                assertThat(it["dpsCSIPFactorId"]).isEqualTo(dpsCSIPFactorId)
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
