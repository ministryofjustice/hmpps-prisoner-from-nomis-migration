package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
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
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.verification.VerificationMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension.Companion.dpsCourtSentencingServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseIdentifierResponse
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime
import java.util.AbstractMap.SimpleEntry
import java.util.UUID

private const val NOMIS_COURT_CASE_ID = 1234L
private const val NOMIS_COURT_APPEARANCE_ID = 5555L
private const val NOMIS_CASE_IDENTIFIER = "GH123"
private const val NOMIS_CASE_IDENTIFIER_TYPE = "CASE/INFO#"
private const val DPS_COURT_CASE_ID = "cc1"
private const val DPS_COURT_APPEARANCE_ID = "6f35a357-f458-40b9-b824-de729ffeb459"
private const val EXISTING_DPS_COURT_CASE_ID = "cc2"
private const val EXISTING_DPS_COURT_APPEARANCE_ID = "9d99a357-f458-40b9-b824-de729ffeb459"
private const val EXISTING_DPS_CHARGE_ID = "88d8a357-f458-40b9-b824-de729ffeb459"
private const val OFFENDER_ID_DISPLAY = "A3864DZ"
private const val NOMIS_BOOKING_ID = 12344321L
private const val NOMIS_OFFENDER_CHARGE_ID = 7777L
private const val DPS_CHARGE_ID = "5b35a357-f458-40b9-b824-de729ffeb488"

class CourtSentencingSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var courtSentencingNomisApiMockServer: CourtSentencingNomisApiMockServer

  @Autowired
  private lateinit var courtSentencingMappingApiMockServer: CourtSentencingMappingApiMockServer

  @Nested
  @DisplayName("OFFENDER_CASES-INSERTED")
  inner class CourtCaseInserted {

    @Nested
    @DisplayName("When court sentencing was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCase(
          courtCaseId = NOMIS_COURT_CASE_ID,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtCaseEvent(
            eventType = "OFFENDER_CASES-INSERTED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        ).also {
          waitForTelemetry()
        }
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            },
            isNull(),
          )
        }

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-cases/nomis-court-case-id/\\d+")),
        )
        // will not create an court case in DPS
        dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court case was created in NOMIS")
    inner class NomisCreated {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCase(
          bookingId = NOMIS_BOOKING_ID,
          courtCaseId = NOMIS_COURT_CASE_ID,
        )
      }

      @Nested
      @DisplayName("When mapping does not exist yet")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          dpsCourtSentencingServer.stubPostCourtCaseForCreate(courtCaseId = DPS_COURT_CASE_ID)
          courtSentencingMappingApiMockServer.stubPostMapping()
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtCaseEvent(
              eventType = "OFFENDER_CASES-INSERTED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will create a court case in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              postRequestedFor(urlPathEqualTo("/legacy/court-case"))
                .withRequestBody(matchingJsonPath("prisonerId", equalTo(OFFENDER_ID_DISPLAY))),
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases"))
                .withRequestBody(matchingJsonPath("dpsCourtCaseId", equalTo(DPS_COURT_CASE_ID)))
                .withRequestBody(matchingJsonPath("nomisCourtCaseId", equalTo(NOMIS_COURT_CASE_ID.toString())))
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )
          }
        }

        @Test
        fun `will retrieve the court case from nomis`() {
          await untilAsserted {
            courtSentencingNomisApiMockServer.verify(
              getRequestedFor(urlPathEqualTo("/prisoners/$OFFENDER_ID_DISPLAY/sentencing/court-cases/$NOMIS_COURT_CASE_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-case-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
            mapping = CourtCaseMappingDto(
              nomisCourtCaseId = NOMIS_COURT_CASE_ID,
              dpsCourtCaseId = DPS_COURT_CASE_ID,
            ),
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtCaseEvent(
              eventType = "OFFENDER_CASES-INSERTED",
              bookingId = NOMIS_BOOKING_ID,
              caseId = NOMIS_COURT_CASE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-case-synchronisation-created-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              },
              isNull(),
            )
          }
          // will not create a court case in DPS
          dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          dpsCourtSentencingServer.stubPostCourtCaseForCreate(courtCaseId = DPS_COURT_CASE_ID)
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            courtSentencingMappingApiMockServer.stubPostMappingFailureFollowedBySuccess()
            awsSqsCourtSentencingOffenderEventsClient.sendMessage(
              courtSentencingQueueOffenderEventsUrl,
              courtCaseEvent(
                eventType = "OFFENDER_CASES-INSERTED",
              ),
            ).also {
              waitForTelemetry()
            }
          }

          @Test
          fun `will create a court case in DPS`() {
            await untilAsserted {
              dpsCourtSentencingServer.verify(
                postRequestedFor(urlPathEqualTo("/legacy/court-case")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            await untilAsserted {
              courtSentencingMappingApiMockServer.verify(
                WireMock.exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases"))
                  .withRequestBody(matchingJsonPath("dpsCourtCaseId", equalTo(DPS_COURT_CASE_ID)))
                  .withRequestBody(matchingJsonPath("nomisCourtCaseId", equalTo(NOMIS_COURT_CASE_ID.toString())))
                  .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
              )
            }

            assertThat(
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get(),
            ).isEqualTo(0)
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("court-case-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                  assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("court-case-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                  assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
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
            courtSentencingMappingApiMockServer.stubPostMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
            awsSqsCourtSentencingOffenderEventsClient.sendMessage(
              courtSentencingQueueOffenderEventsUrl,
              courtCaseEvent(
                eventType = "OFFENDER_CASES-INSERTED",
              ),
            )
            await untilCallTo {
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create a court case in DPS`() {
            await untilAsserted {
              dpsCourtSentencingServer.verify(
                1,
                postRequestedFor(urlPathEqualTo("/legacy/court-case")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            courtSentencingMappingApiMockServer.verify(
              WireMock.exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("court-case-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                  assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }
          }
        }
      }
    }

    @Nested
    @DisplayName("duplicate mapping - two messages received at the same time")
    inner class WhenDuplicate {

      @Test
      internal fun `it will not retry after a 409 (duplicate court case written to Sentencing API)`() {
        // in the case of multiple events received at the same time - mapping doesn't exist
        courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)

        courtSentencingNomisApiMockServer.stubGetCourtCase(
          bookingId = NOMIS_BOOKING_ID,
          courtCaseId = NOMIS_COURT_CASE_ID,
        )
        dpsCourtSentencingServer.stubPostCourtCaseForCreate(courtCaseId = DPS_COURT_CASE_ID)

        courtSentencingMappingApiMockServer.stubCourtCaseMappingCreateConflict(
          existingDpsCourtCaseId = EXISTING_DPS_COURT_CASE_ID,
          duplicateDpsCourtCaseId = DPS_COURT_CASE_ID,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
        )

        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtCaseEvent(
            eventType = "OFFENDER_CASES-INSERTED",
            caseId = NOMIS_COURT_CASE_ID,
            bookingId = NOMIS_BOOKING_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
          ),
        )

        // wait for mapping calls before verifying
        await untilAsserted {
          courtSentencingMappingApiMockServer.verify(
            1,
            postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases")),
          )
        }

        // doesn't retry
        dpsCourtSentencingServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/legacy/court-case")),
        )

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            org.mockito.kotlin.eq("from-nomis-sync-court-case-duplicate"),
            check {
              assertThat(it["migrationId"]).isNull()
              assertThat(it["existingDpsCourtCaseId"]).isEqualTo(EXISTING_DPS_COURT_CASE_ID)
              assertThat(it["duplicateDpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              assertThat(it["existingNomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["duplicateNomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CASES-DELETED")
  inner class CourtCaseDeleted {

    @Nested
    @DisplayName("When court case was deleted in NOMIS")
    inner class NomisDeleted {

      @Nested
      @DisplayName("When mapping does not exist")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtCaseEvent(
              eventType = "OFFENDER_CASES-DELETED",
            ),
          )
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-case-synchronisation-deleted-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              },
              isNull(),
            )
          }
          // will not delete a court case in DPS
          dpsCourtSentencingServer.verify(0, deleteRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
            mapping = CourtCaseMappingDto(
              nomisCourtCaseId = NOMIS_COURT_CASE_ID,
              dpsCourtCaseId = DPS_COURT_CASE_ID,
            ),
          )
          courtSentencingMappingApiMockServer.stubDeleteCourtCaseMapping(dpsCourtCaseId = DPS_COURT_CASE_ID)
          dpsCourtSentencingServer.stubDeleteCourtCase(DPS_COURT_CASE_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtCaseEvent(
              eventType = "OFFENDER_CASES-DELETED",
              bookingId = NOMIS_BOOKING_ID,
              caseId = NOMIS_COURT_CASE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will delete a court case in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/legacy/court-case/$DPS_COURT_CASE_ID")),
              // TODO DPS to implement this endpoint
            )
          }
        }

        @Test
        fun `will delete mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases/dps-court-case-id/$DPS_COURT_CASE_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-case-synchronisation-deleted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CASES-UPDATED")
  inner class CourtCaseUpdated {
    @Nested
    @DisplayName("When court case was updated in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtCaseEvent(
            eventType = "OFFENDER_CASES-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-updated-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
            },
            isNull(),
          )
        }

        // will not bother getting the court case or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/prisoners/\\d+/sentencing/court-cases/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-cases/nomis-court-case-id/\\d+")),
        )
        // will not create a court case in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court case was updated in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCase(
          courtCaseId = NOMIS_COURT_CASE_ID,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtCaseEvent(
              eventType = "OFFENDER_CASES-UPDATED",
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("court-case-synchronisation-updated-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )

          dpsCourtSentencingServer.stubPutCourtCaseForUpdate(courtCaseId = DPS_COURT_CASE_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtCaseEvent(
              eventType = "OFFENDER_CASES-UPDATED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will update DPS with the changes`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              1,
              putRequestedFor(urlPathEqualTo("/legacy/court-case/$DPS_COURT_CASE_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-case-synchronisation-updated-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CASE_IDENTIFIERS")
  inner class CaseIdentifiersUpdated {
    @Nested
    @DisplayName("When court case was updated in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          caseIdentifiersEvent(
            eventType = "OFFENDER_CASE_IDENTIFIERS-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("case-identifiers-synchronisation-skipped"),
            check {
              assertThat(it["nomisIdentifiersNo"]).isEqualTo(NOMIS_CASE_IDENTIFIER)
              assertThat(it["nomisIdentifiersType"]).isEqualTo(NOMIS_CASE_IDENTIFIER_TYPE)
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["eventType"]).isEqualTo("OFFENDER_CASE_IDENTIFIERS-UPDATED")
            },
            isNull(),
          )
        }

        // will not bother getting the court case or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/prisoners/\\d+/sentencing/court-cases/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-cases/nomis-court-case-id/\\d+")),
        )
        // will not attempt to refresh case references in DPS
        dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When case identifier was updated in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCaseForMigration(
          caseId = NOMIS_COURT_CASE_ID,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          caseIndentifiers = listOf(
            CaseIdentifierResponse(
              reference = NOMIS_CASE_IDENTIFIER,
              createDateTime = LocalDateTime.now().toString(),
              type = NOMIS_CASE_IDENTIFIER_TYPE,
            ),
            CaseIdentifierResponse(
              reference = "ref2",
              createDateTime = LocalDateTime.now().plusHours(1).toString(),
              type = NOMIS_CASE_IDENTIFIER_TYPE,
            ),
            CaseIdentifierResponse(
              reference = "ref2",
              createDateTime = LocalDateTime.now().plusHours(1).toString(),
              type = "NOT_OF_INTEREST_TO_DPS",
            ),
          ),
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            caseIdentifiersEvent(
              eventType = "OFFENDER_CASE_IDENTIFIERS-UPDATED",
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("case-identifiers-synchronisation-failed"),
              check {
                assertThat(it["nomisIdentifiersNo"]).isEqualTo(NOMIS_CASE_IDENTIFIER)
                assertThat(it["nomisIdentifiersType"]).isEqualTo(NOMIS_CASE_IDENTIFIER_TYPE)
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["eventType"]).isEqualTo("OFFENDER_CASE_IDENTIFIERS-UPDATED")
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )

          dpsCourtSentencingServer.stubPutCaseIdentifierRefresh(courtCaseId = DPS_COURT_CASE_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            caseIdentifiersEvent(
              eventType = "OFFENDER_CASE_IDENTIFIERS-UPDATED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will update DPS with the changes`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              1,
              putRequestedFor(urlPathEqualTo("/court-case/$DPS_COURT_CASE_ID/case-references/refresh"))
                .withRequestBody(matchingJsonPath("caseReferences.size()", equalTo("2")))
                .withRequestBody(
                  matchingJsonPath(
                    "caseReferences[0].offenderCaseReference",
                    equalTo(NOMIS_CASE_IDENTIFIER),
                  ),
                )
                .withRequestBody(matchingJsonPath("caseReferences[1].offenderCaseReference", equalTo("ref2"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("case-identifiers-synchronisation-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisIdentifiersNo"]).isEqualTo(NOMIS_CASE_IDENTIFIER)
                assertThat(it["nomisIdentifiersType"]).isEqualTo(NOMIS_CASE_IDENTIFIER_TYPE)
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["eventType"]).isEqualTo("OFFENDER_CASE_IDENTIFIERS-UPDATED")
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class HandlesDeletedEvent {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )

          dpsCourtSentencingServer.stubPutCaseIdentifierRefresh(courtCaseId = DPS_COURT_CASE_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            caseIdentifiersEvent(
              eventType = "OFFENDER_CASE_IDENTIFIERS-DELETED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("case-identifiers-synchronisation-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisIdentifiersNo"]).isEqualTo(NOMIS_CASE_IDENTIFIER)
                assertThat(it["nomisIdentifiersType"]).isEqualTo(NOMIS_CASE_IDENTIFIER_TYPE)
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["eventType"]).isEqualTo("OFFENDER_CASE_IDENTIFIERS-DELETED")
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class HandlesInsertedEvent {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )

          dpsCourtSentencingServer.stubPutCaseIdentifierRefresh(courtCaseId = DPS_COURT_CASE_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            caseIdentifiersEvent(
              eventType = "OFFENDER_CASE_IDENTIFIERS-INSERTED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("case-identifiers-synchronisation-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisIdentifiersNo"]).isEqualTo(NOMIS_CASE_IDENTIFIER)
                assertThat(it["nomisIdentifiersType"]).isEqualTo(NOMIS_CASE_IDENTIFIER_TYPE)
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["eventType"]).isEqualTo("OFFENDER_CASE_IDENTIFIERS-INSERTED")
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("COURT_EVENTS-INSERTED")
  inner class CourtAppearanceInserted {

    @Nested
    @DisplayName("When court appearance was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtAppearance(
          courtCaseId = NOMIS_COURT_CASE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtAppearanceEvent(
            eventType = "COURT_EVENTS-INSERTED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            },
            isNull(),
          )
        }

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/\\d+")),
        )
        // will not create an court case in DPS
        dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court appearance was created in NOMIS")
    inner class NomisCreated {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtAppearance(
          courtCaseId = NOMIS_COURT_CASE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          eventDateTime = "2020-01-02T00:00:00",
          courtId = "MDI",
        )
      }

      @Nested
      @DisplayName("When mapping does not exist yet")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )
          dpsCourtSentencingServer.stubPostCourtAppearanceForCreate(
            courtAppearanceId = UUID.fromString(
              DPS_COURT_APPEARANCE_ID,
            ),
            courtCaseId = DPS_COURT_CASE_ID,
          )
          courtSentencingMappingApiMockServer.stubPostCourtAppearanceMapping()
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-INSERTED",
            ),
          )
        }

        @Test
        fun `will create a court appearance in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              postRequestedFor(urlPathEqualTo("/legacy/court-appearance"))
                .withRequestBody(matchingJsonPath("legacyData.eventId", equalTo(NOMIS_COURT_APPEARANCE_ID.toString())))
                .withRequestBody(matchingJsonPath("legacyData.nomisOutcomeCode", equalTo("4506")))
                .withRequestBody(matchingJsonPath("legacyData.caseId", equalTo(NOMIS_COURT_CASE_ID.toString())))
                .withRequestBody(matchingJsonPath("legacyData.outcomeDescription", equalTo("Adjournment")))
                .withRequestBody(matchingJsonPath("legacyData.postedDate", WireMock.not(WireMock.absent())))
                .withRequestBody(matchingJsonPath("courtCode", equalTo("MDI")))
                .withRequestBody(matchingJsonPath("courtCaseUuid", equalTo(DPS_COURT_CASE_ID)))
                .withRequestBody(matchingJsonPath("appearanceDate", equalTo("2020-01-02")))
                .withRequestBody(
                  matchingJsonPath(
                    "appearanceTypeUuid",
                    equalTo(COURT_APPEARANCE_DPS_APPEARANCE_TYPE_UUID),
                  ),
                ),
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances"))
                .withRequestBody(matchingJsonPath("dpsCourtAppearanceId", equalTo(DPS_COURT_APPEARANCE_ID)))
                .withRequestBody(
                  matchingJsonPath(
                    "nomisCourtAppearanceId",
                    equalTo(NOMIS_COURT_APPEARANCE_ID.toString()),
                  ),
                )
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-appearance-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("Ignore appearances unrelated to a court case")
      inner class NoAssociatedCourtCase {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetCourtAppearance(
            courtCaseId = null,
            offenderNo = OFFENDER_ID_DISPLAY,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-INSERTED",
            ),
          )
        }

        @Test
        fun `will track a telemetry event for ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-appearance-synchronisation-created-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isNull()
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["reason"]).contains("appearance not associated with a court case")
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("Ignore appearances without a court case mapping")
      inner class CourtCaseNotMapped {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetCourtAppearance(
            courtCaseId = NOMIS_COURT_CASE_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-INSERTED",
            ),
          )
        }

        @Test
        fun `will track a telemetry event for failed`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("court-appearance-synchronisation-created-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["reason"]).isEqualTo("associated court case is not mapped")
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
            mapping = CourtCaseMappingDto(
              nomisCourtCaseId = NOMIS_COURT_CASE_ID,
              dpsCourtCaseId = DPS_COURT_CASE_ID,
            ),
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-INSERTED",
              bookingId = NOMIS_BOOKING_ID,
              courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-appearance-synchronisation-created-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              },
              isNull(),
            )
          }
          // will not create a court case in DPS
          dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)
          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
            dpsCourtCaseId = DPS_COURT_CASE_ID,
          )
          dpsCourtSentencingServer.stubPostCourtAppearanceForCreate(
            courtAppearanceId = UUID.fromString(
              DPS_COURT_APPEARANCE_ID,
            ),
          )
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            courtSentencingMappingApiMockServer.stubPostCourtAppearanceMappingFailureFollowedBySuccess()
            awsSqsCourtSentencingOffenderEventsClient.sendMessage(
              courtSentencingQueueOffenderEventsUrl,
              courtAppearanceEvent(
                eventType = "COURT_EVENTS-INSERTED",
              ),
            ).also {
              waitForTelemetry()
            }
          }

          @Test
          fun `will create a court case in DPS`() {
            await untilAsserted {
              dpsCourtSentencingServer.verify(
                postRequestedFor(urlPathEqualTo("/legacy/court-appearance")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            await untilAsserted {
              courtSentencingMappingApiMockServer.verify(
                WireMock.exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances"))
                  .withRequestBody(matchingJsonPath("dpsCourtAppearanceId", equalTo(DPS_COURT_APPEARANCE_ID)))
                  .withRequestBody(
                    matchingJsonPath(
                      "nomisCourtAppearanceId",
                      equalTo(NOMIS_COURT_APPEARANCE_ID.toString()),
                    ),
                  )
                  .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
              )
            }

            assertThat(
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get(),
            ).isEqualTo(0)
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("court-appearance-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                  assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
                  assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                  assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("court-appearance-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                  assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
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
            courtSentencingMappingApiMockServer.stubPostCourtAppearanceMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
            awsSqsCourtSentencingOffenderEventsClient.sendMessage(
              courtSentencingQueueOffenderEventsUrl,
              courtAppearanceEvent(
                eventType = "COURT_EVENTS-INSERTED",
              ),
            )
            await untilCallTo {
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create a court case in DPS`() {
            await untilAsserted {
              dpsCourtSentencingServer.verify(
                1,
                postRequestedFor(urlPathEqualTo("/legacy/court-appearance")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            courtSentencingMappingApiMockServer.verify(
              WireMock.exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("court-appearance-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                  assertThat(it["dpsCourtCaseId"]).isEqualTo(DPS_COURT_CASE_ID)
                  assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                  assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }
          }
        }
      }
    }

    @Nested
    @DisplayName("duplicate mapping - two messages received at the same time")
    inner class WhenDuplicate {

      @Test
      internal fun `it will not retry after a 409 (duplicate court appearance written to Sentencing API)`() {
        // in the case of multiple events received at the same time - mapping doesn't exist
        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)

        courtSentencingMappingApiMockServer.stubGetByNomisId(
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
          dpsCourtCaseId = DPS_COURT_CASE_ID,
        )

        courtSentencingNomisApiMockServer.stubGetCourtAppearance(
          offenderNo = OFFENDER_ID_DISPLAY,
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          courtCaseId = NOMIS_COURT_CASE_ID,
        )
        dpsCourtSentencingServer.stubPostCourtAppearanceForCreate(
          courtAppearanceId = UUID.fromString(
            DPS_COURT_APPEARANCE_ID,
          ),
        )

        courtSentencingMappingApiMockServer.stubCourtAppearanceMappingCreateConflict(
          existingDpsCourtAppearanceId = EXISTING_DPS_COURT_APPEARANCE_ID,
          duplicateDpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtAppearanceEvent(
            eventType = "COURT_EVENTS-INSERTED",
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            bookingId = NOMIS_BOOKING_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
          ),
        )

        // wait for mapping calls before verifying
        await untilAsserted {
          courtSentencingMappingApiMockServer.verify(
            1,
            postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances")),
          )
        }

        // doesn't retry
        dpsCourtSentencingServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/legacy/court-appearance")),
        )

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            org.mockito.kotlin.eq("from-nomis-sync-court-appearance-duplicate"),
            check {
              assertThat(it["migrationId"]).isNull()
              assertThat(it["existingDpsCourtAppearanceId"]).isEqualTo(EXISTING_DPS_COURT_APPEARANCE_ID)
              assertThat(it["duplicateDpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              assertThat(it["existingNomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["duplicateNomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("COURT_EVENTS-UPDATED")
  inner class CourtAppearanceUpdated {
    @Nested
    @DisplayName("When court appearance was updated in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtAppearanceEvent(
            eventType = "COURT_EVENTS-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-synchronisation-updated-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            },
            isNull(),
          )
        }

        // will not bother getting the court appearance or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/prisoners/\\d+/sentencing/court-appearances/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/\\d+")),
        )
        // will not update a court appearance in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court appearance was updated in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtAppearance(
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          courtCaseId = NOMIS_COURT_CASE_ID,
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-UPDATED",
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("court-appearance-synchronisation-updated-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetByNomisId(
            nomisCourtCaseId = NOMIS_COURT_CASE_ID,
          )

          dpsCourtSentencingServer.stubPutCourtAppearanceForUpdate(
            courtAppearanceId = UUID.fromString(
              DPS_COURT_APPEARANCE_ID,
            ),
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-UPDATED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will update DPS with the changes`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              1,
              putRequestedFor(urlPathEqualTo("/legacy/court-appearance/$DPS_COURT_APPEARANCE_ID"))
                .withRequestBody(
                  matchingJsonPath(
                    "appearanceTypeUuid",
                    equalTo(COURT_APPEARANCE_DPS_APPEARANCE_TYPE_UUID),
                  ),
                ),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-appearance-synchronisation-updated-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("Ignore appearances unrelated to a court case")
      inner class NoAssociatedCourtCase {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )
          courtSentencingNomisApiMockServer.stubGetCourtAppearance(
            courtCaseId = null,
            offenderNo = OFFENDER_ID_DISPLAY,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-UPDATED",
            ),
          )
        }

        @Test
        fun `will track a telemetry event for ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-appearance-synchronisation-updated-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isNull()
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["reason"]).contains("appearance not associated with a court case")
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("appearances without a court case mapping fail")
      inner class CourtCaseNotMapped {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )
          courtSentencingNomisApiMockServer.stubGetCourtAppearance(
            courtCaseId = NOMIS_COURT_CASE_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-UPDATED",
            ),
          )
        }

        @Test
        fun `will track a telemetry event for failed`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("court-appearance-synchronisation-updated-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["reason"]).isEqualTo("associated court case is not mapped")
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("COURT_EVENTS-DELETED")
  inner class CourtAppearanceDeleted {

    @Nested
    @DisplayName("When court appearance was deleted in NOMIS")
    inner class NomisDeleted {

      @Nested
      @DisplayName("When mapping does not exist")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-DELETED",
            ),
          )
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-appearance-synchronisation-deleted-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              },
              isNull(),
            )
          }
          // will not delete a court appearance in DPS
          dpsCourtSentencingServer.verify(0, deleteRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            mapping = CourtAppearanceMappingDto(
              nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            ),
          )
          courtSentencingMappingApiMockServer.stubDeleteCourtAppearanceMappingByDpsId(dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID)
          dpsCourtSentencingServer.stubDeleteCourtAppearance(DPS_COURT_APPEARANCE_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-DELETED",
              bookingId = NOMIS_BOOKING_ID,
              courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will delete a court appearance in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/legacy/court-appearance/$DPS_COURT_APPEARANCE_ID")),
            )
          }
        }

        @Test
        fun `will delete mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/dps-court-appearance-id/$DPS_COURT_APPEARANCE_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-appearance-synchronisation-deleted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("COURT_EVENT_CHARGES-INSERTED")
  inner class CourtEventChargeInserted {

    @Nested
    @DisplayName("When court event charge was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtEventChargeEvent(
            eventType = "COURT_EVENT_CHARGES-INSERTED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
            },
            isNull(),
          )
        }

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-charges/nomis-court-charge-id/\\d+")),
        )

        // will not call the DPS service
        dpsCourtSentencingServer.verify(0, anyRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When court event charge was created in NOMIS")
    inner class NomisCreated {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetOffenderCharge(
          offenderNo = OFFENDER_ID_DISPLAY,
          offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
        )

        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
        )
      }

      @Nested
      @DisplayName("When charge mapping does not exist yet")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)

          dpsCourtSentencingServer.stubPostCourtChargeForCreate(
            courtChargeId = DPS_CHARGE_ID,
            courtCaseId = DPS_COURT_CASE_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
          )
          courtSentencingMappingApiMockServer.stubPostCourtChargeMapping()
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-INSERTED",
            ),
          )
        }

        @Test
        fun `will create a court charge and associate with an appearance in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              postRequestedFor(urlPathEqualTo("/legacy/charge")),
              // TODO assert once DPS team have defined their dto
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-charges"))
                .withRequestBody(matchingJsonPath("dpsCourtChargeId", equalTo(DPS_CHARGE_ID)))
                .withRequestBody(
                  matchingJsonPath(
                    "nomisCourtChargeId",
                    equalTo(NOMIS_OFFENDER_CHARGE_ID.toString()),
                  ),
                )
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-charge-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                assertThat(it["existingDpsCharge"]).isEqualTo("false")
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When court charge mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )
          dpsCourtSentencingServer.stubPutCourtChargeForAddExistingChargeToAppearance(
            courtChargeId = DPS_CHARGE_ID,
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )

          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-INSERTED",
            ),
          )
        }

        @Test
        fun `the existing offender charge is added to the appearance on DPS rather than created`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              putRequestedFor(urlPathEqualTo("/legacy/court-appearance/${DPS_COURT_APPEARANCE_ID}/charge/$DPS_CHARGE_ID")),
              // TODO assert once DPS team have defined their dto
            )
          }
        }

        @Test
        fun `will not try to create a mapping `() {
          courtSentencingMappingApiMockServer.verify(
            0,
            anyRequestedFor(anyUrl()),
          )
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-charge-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                assertThat(it["existingDpsCharge"]).isEqualTo("true")
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
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
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)

          dpsCourtSentencingServer.stubPostCourtChargeForCreate(
            courtChargeId = DPS_CHARGE_ID,
            courtCaseId = DPS_COURT_CASE_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
          )
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            courtSentencingMappingApiMockServer.stubPostCourtChargeMappingFailureFollowedBySuccess()
            awsSqsCourtSentencingOffenderEventsClient.sendMessage(
              courtSentencingQueueOffenderEventsUrl,
              courtEventChargeEvent(
                eventType = "COURT_EVENT_CHARGES-INSERTED",
              ),
            )
          }

          @Test
          fun `will create a court case in DPS`() {
            await untilAsserted {
              dpsCourtSentencingServer.verify(
                postRequestedFor(urlPathEqualTo("/legacy/charge")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            await untilAsserted {
              courtSentencingMappingApiMockServer.verify(
                WireMock.exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-charges"))
                  .withRequestBody(matchingJsonPath("dpsCourtChargeId", equalTo(DPS_CHARGE_ID)))
                  .withRequestBody(
                    matchingJsonPath(
                      "nomisCourtChargeId",
                      equalTo(NOMIS_OFFENDER_CHARGE_ID.toString()),
                    ),
                  )
                  .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
              )
            }

            assertThat(
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get(),
            ).isEqualTo(0)
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("court-charge-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
                  assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                  assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
                  assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("court-charge-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
                  assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
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
            courtSentencingMappingApiMockServer.stubPostCourtChargeMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)
            awsSqsCourtSentencingOffenderEventsClient.sendMessage(
              courtSentencingQueueOffenderEventsUrl,
              courtEventChargeEvent(
                eventType = "COURT_EVENT_CHARGES-INSERTED",
              ),
            )
            await untilCallTo {
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create and associate a charge in DPS`() {
            await untilAsserted {
              dpsCourtSentencingServer.verify(
                postRequestedFor(urlPathEqualTo("/legacy/charge")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            courtSentencingMappingApiMockServer.verify(
              WireMock.exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-charges")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("court-charge-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
                  assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                  assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
                  assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }
          }
        }
      }
    }

    @Nested
    @DisplayName("duplicate mapping - two messages received at the same time")
    inner class WhenDuplicate {

      @Test
      internal fun `it will not retry after a 409 (duplicate charge written to Sentencing API)`() {
        // in the case of multiple events received at the same time - mapping doesn't exist
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)

        courtSentencingNomisApiMockServer.stubGetOffenderCharge(
          offenderNo = OFFENDER_ID_DISPLAY,
          offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
        )

        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
        )

        dpsCourtSentencingServer.stubPostCourtChargeForCreate(
          courtChargeId = DPS_CHARGE_ID,
          courtCaseId = DPS_COURT_CASE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )

        courtSentencingMappingApiMockServer.stubCourtChargeMappingCreateConflict(
          existingDpsCourtChargeId = EXISTING_DPS_CHARGE_ID,
          duplicateDpsCourtChargeId = DPS_CHARGE_ID,
          nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
        )

        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtEventChargeEvent(
            eventType = "COURT_EVENT_CHARGES-INSERTED",
          ),
        )

        // wait for mapping calls before verifying
        await untilAsserted {
          courtSentencingMappingApiMockServer.verify(
            1,
            postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-charges")),
          )
        }

        // doesn't retry
        dpsCourtSentencingServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/legacy/charge")),
        )

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            org.mockito.kotlin.eq("from-nomis-sync-charge-duplicate"),
            check {
              assertThat(it["migrationId"]).isNull()
              assertThat(it["existingDpsCourtChargeId"]).isEqualTo(EXISTING_DPS_CHARGE_ID)
              assertThat(it["duplicateDpsCourtChargeId"]).isEqualTo(DPS_CHARGE_ID)
              assertThat(it["existingNomisCourtChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["duplicateNomisCourtChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("COURT_EVENT_CHARGES-DELETED")
  inner class CourtEventChargeDeleted {

    @Nested
    @DisplayName("When court charge was deleted in NOMIS")
    inner class NomisDeleted {

      @Nested
      @DisplayName("When charge mapping does not exist")
      inner class NoChargeMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            mapping = CourtAppearanceMappingDto(
              nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            ),
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-DELETED",
            ),
          )
          waitForTelemetry(times(2))
        }

        @Test
        fun `the event failed and is retried`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("court-charge-synchronisation-deleted-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              },
              isNull(),
            )
          }
          // will not delete a court charge in DPS
          dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When appearance mapping does not exist")
      inner class NoAppearanceMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-DELETED",
            ),
          )
          waitForTelemetry()
        }

        @Test
        fun `the failure event is recorded and retried`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("court-charge-synchronisation-deleted-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              },
              isNull(),
            )
          }
          // will not delete a court charge in DPS
          dpsCourtSentencingServer.verify(0, deleteRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mappings exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            mapping = CourtAppearanceMappingDto(
              nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            ),
          )

          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )

          dpsCourtSentencingServer.stubRemoveCourtCharge(
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            chargeId = DPS_CHARGE_ID,
          )

          courtSentencingNomisApiMockServer.stubGetOffenderCharge(status = NOT_FOUND)

          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-DELETED",
            ),
          )
          waitForTelemetry()
        }

        @Test
        fun `will delete a court appearance in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/legacy/court-appearance/$DPS_COURT_APPEARANCE_ID/charge/$DPS_CHARGE_ID")),
              // TODO DPS to implement this endpoint
            )
          }
        }

        @Test
        fun `will remove the mapping if nomis charge has been deleted`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.stubDeleteCourtChargeMapping(NOMIS_OFFENDER_CHARGE_ID)
            courtSentencingMappingApiMockServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/court-charges/nomis-court-charge-id/$NOMIS_OFFENDER_CHARGE_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-charge-synchronisation-deleted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
                assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mappings exists, nomis has not deleted the offender charge")
      inner class MappingExistsNomisHasNotDeletedCharge {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            mapping = CourtAppearanceMappingDto(
              nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
              dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
            ),
          )

          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )

          dpsCourtSentencingServer.stubRemoveCourtCharge(
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            chargeId = DPS_CHARGE_ID,
          )

          courtSentencingNomisApiMockServer.stubGetOffenderCharge(
            offenderNo = OFFENDER_ID_DISPLAY,
            offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
          )

          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-DELETED",
            ),
          )
          waitForTelemetry()
        }

        @Test
        fun `will not delete the charge mapping if nomis charge has not been deleted`() {
          courtSentencingMappingApiMockServer.verify(
            0,
            deleteRequestedFor(urlPathEqualTo("/court-charges/nomis-court-charge-id/$NOMIS_OFFENDER_CHARGE_ID")),
          )
        }
      }
    }
  }

  private fun waitForTelemetry(times: VerificationMode = atLeastOnce()) {
    await untilAsserted {
      verify(telemetryClient, times).trackEvent(
        any(),
        any(),
        isNull(),
      )
    }
  }

  @Nested
  @DisplayName("COURT_EVENT_CHARGES-UPDATED")
  inner class CourtEventChargeUpdated {
    @Nested
    @DisplayName("When court charge was updated in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtEventChargeEvent(
            eventType = "COURT_EVENT_CHARGES-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-updated-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
            },
            isNull(),
          )
        }

        // will not bother getting the court charge or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/prisoners/\\d+/sentencing/offender-charges/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-charges/nomis-court-charge-id/\\d+")),
        )
        // will not update a court charge in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When offender charge was updated in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtEventCharge(
          offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )
      }

      @Nested
      @DisplayName("When charge mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-UPDATED",
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("court-charge-synchronisation-updated-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["reason"]).isEqualTo("charge is not mapped")
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When appearance mapping doesn't exist")
      inner class AppearanceMappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            offenderChargeEvent(
              eventType = "COURT_EVENT_CHARGES-UPDATED",
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("court-charge-synchronisation-updated-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["reason"]).isEqualTo("associated court appearance is not mapped")
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_COURT_APPEARANCE_ID,
          )

          dpsCourtSentencingServer.stubPutAppearanceChargeForUpdate(
            chargeId = DPS_CHARGE_ID,
            appearanceId = DPS_COURT_APPEARANCE_ID,
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtEventChargeEvent(
              eventType = "COURT_EVENT_CHARGES-UPDATED",
            ),
          )
        }

        @Test
        fun `will update DPS with the changes`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              1,
              putRequestedFor(urlPathEqualTo("/legacy/charge/$DPS_CHARGE_ID/appearance/$DPS_COURT_APPEARANCE_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-charge-synchronisation-updated-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
                assertThat(it["dpsChargeId"]).isEqualTo(DPS_CHARGE_ID)
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("When court event charge was updated in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtEventChargeEvent(
            eventType = "COURT_EVENT_CHARGES-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-updated-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
            },
            isNull(),
          )
        }

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-charges/nomis-court-charge-id/\\d+")),
        )

        // will not call the DPS service
        dpsCourtSentencingServer.verify(0, anyRequestedFor(anyUrl()))
      }
    }
  }
}

fun courtCaseEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  caseId: Long = NOMIS_COURT_CASE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"caseId\": \"$caseId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun caseIdentifiersEvent(
  eventType: String,
  reference: String = NOMIS_CASE_IDENTIFIER,
  identifierType: String = NOMIS_CASE_IDENTIFIER_TYPE,
  caseId: Long = NOMIS_COURT_CASE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"identifierNo\": \"$reference\",\"identifierType\": \"$identifierType\",\"caseId\": \"$caseId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun courtAppearanceEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  courtAppearanceId: Long = NOMIS_COURT_APPEARANCE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"$courtAppearanceId\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun courtEventChargeEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  eventId: Long = NOMIS_COURT_APPEARANCE_ID,
  chargeId: Long = NOMIS_OFFENDER_CHARGE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "NOMIS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"eventId\": \"$eventId\",\"chargeId\": \"$chargeId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun offenderChargeEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  chargeId: Long = NOMIS_OFFENDER_CHARGE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"chargeId\": \"$chargeId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()
