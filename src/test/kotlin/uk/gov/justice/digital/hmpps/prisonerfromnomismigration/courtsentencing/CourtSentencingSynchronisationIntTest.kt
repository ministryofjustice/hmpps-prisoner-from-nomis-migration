package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.not
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateSentence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationSentenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.NomisPeriodLengthId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RecallCustodyDate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate
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
  @DisplayName("OFFENDER_CASES-LINKED")
  inner class CourtCaseLinked {
    val combinedCaseId = 5432L

    @Nested
    @DisplayName("When court case was linked in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtCaseLinkingEvent(
            eventType = "OFFENDER_CASES-LINKED",
            auditModule = "DPS_SYNCHRONISATION",
            combinedCaseId = combinedCaseId,
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-link-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
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
    @DisplayName("When court case was linked in NOMIS")
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
            courtCaseLinkingEvent(
              eventType = "OFFENDER_CASES-LINKED",
              combinedCaseId = combinedCaseId,
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("court-case-synchronisation-link-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
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

          // TODO - stub DPS link update
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtCaseLinkingEvent(
              eventType = "OFFENDER_CASES-LINKED",
              combinedCaseId = combinedCaseId,
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will update DPS with the changes`() {
          // TODO assert on DPS update
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-case-synchronisation-link-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
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
  @DisplayName("OFFENDER_CASES-UNLINKED")
  inner class CourtCaseUnlinked {
    val combinedCaseId = 5432L

    @Nested
    @DisplayName("When court case was unlinked in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtCaseLinkingEvent(
            eventType = "OFFENDER_CASES-UNLINKED",
            auditModule = "DPS_SYNCHRONISATION",
            combinedCaseId = combinedCaseId,
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-case-synchronisation-unlink-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
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
    @DisplayName("When court case was unlinked in NOMIS")
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
            courtCaseLinkingEvent(
              eventType = "OFFENDER_CASES-UNLINKED",
              combinedCaseId = combinedCaseId,
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("court-case-synchronisation-unlink-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
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

          // TODO - stub DPS link update
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtCaseLinkingEvent(
              eventType = "OFFENDER_CASES-UNLINKED",
              combinedCaseId = combinedCaseId,
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will update DPS with the changes`() {
          // TODO assert on DPS update
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-case-synchronisation-unlink-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
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
        courtSentencingNomisApiMockServer.stubGetCourtCaseNoOffenderVersion(
          caseId = NOMIS_COURT_CASE_ID,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          caseIndentifiers = listOf(
            CaseIdentifierResponse(
              reference = NOMIS_CASE_IDENTIFIER,
              createDateTime = LocalDateTime.now(),
              type = NOMIS_CASE_IDENTIFIER_TYPE,
            ),
            CaseIdentifierResponse(
              reference = "ref2",
              createDateTime = LocalDateTime.now().plusHours(1),
              type = NOMIS_CASE_IDENTIFIER_TYPE,
            ),
            CaseIdentifierResponse(
              reference = "ref2",
              createDateTime = LocalDateTime.now().plusHours(1),
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
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExistForDeletedEvent {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            caseIdentifiersEvent(
              eventType = "OFFENDER_CASE_IDENTIFIERS-DELETED",
            ),
          )
        }

        @Test
        fun `telemetry added to track the skipping of the event`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("case-identifiers-synchronisation-skipped"),
              check {
                assertThat(it["nomisIdentifiersNo"]).isEqualTo(NOMIS_CASE_IDENTIFIER)
                assertThat(it["isDelete"]).isEqualTo("true")
                assertThat(it["nomisIdentifiersType"]).isEqualTo(NOMIS_CASE_IDENTIFIER_TYPE)
                assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
                assertThat(it["eventType"]).isEqualTo("OFFENDER_CASE_IDENTIFIERS-DELETED")
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is NOT placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get(),
            ).isEqualTo(0)
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
      @DisplayName("Identifier deleted")
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
          eventDateTime = LocalDateTime.parse("2020-01-02T09:00:00"),
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
                .withRequestBody(matchingJsonPath("legacyData.nomisOutcomeCode", equalTo("4506")))
                .withRequestBody(matchingJsonPath("legacyData.outcomeConvictionFlag", equalTo("false")))
                .withRequestBody(matchingJsonPath("legacyData.outcomeDispositionCode", equalTo("I")))
                .withRequestBody(matchingJsonPath("legacyData.outcomeDescription", equalTo("Adjournment")))
                .withRequestBody(matchingJsonPath("legacyData.postedDate", not(WireMock.absent())))
                .withRequestBody(matchingJsonPath("legacyData.appearanceTime", equalTo("09:00")))
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
              courtCaseId = null,
            ),
          )
        }

        @Test
        fun `will not retrieve the court appearance from nomis`() {
          courtSentencingNomisApiMockServer.verify(
            0,
            getRequestedFor(anyUrl()),
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
          eventDateTime = LocalDateTime.parse("2020-01-02T09:00:00"),
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
                )
                .withRequestBody(
                  matchingJsonPath(
                    "legacyData.appearanceTime",
                    equalTo("09:00"),
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
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtAppearanceEvent(
              eventType = "COURT_EVENTS-UPDATED",
              courtCaseId = null,
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
          courtSentencingNomisApiMockServer.stubGetOffenderCharge(
            offenderNo = OFFENDER_ID_DISPLAY,
            offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
          )

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
              postRequestedFor(urlPathEqualTo("/legacy/charge"))
                .withRequestBody(matchingJsonPath("appearanceLifetimeUuid", equalTo(DPS_COURT_APPEARANCE_ID)))
                .withRequestBody(matchingJsonPath("offenceCode", equalTo("RI64006")))
                .withRequestBody(matchingJsonPath("offenceStartDate", equalTo("2024-04-04")))
                .withRequestBody(matchingJsonPath("legacyData.nomisOutcomeCode", equalTo("1002")))
                .withRequestBody(matchingJsonPath("legacyData.outcomeDispositionCode", equalTo("F"))),
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
          courtSentencingNomisApiMockServer.stubGetCourtEventCharge(
            offenderNo = OFFENDER_ID_DISPLAY,
            offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
            courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          )

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
              putRequestedFor(urlPathEqualTo("/legacy/court-appearance/${DPS_COURT_APPEARANCE_ID}/charge/$DPS_CHARGE_ID"))
                .withRequestBody(matchingJsonPath("offenceStartDate", equalTo("2024-03-03")))
                .withRequestBody(matchingJsonPath("legacyData.nomisOutcomeCode", equalTo("1002")))
                .withRequestBody(matchingJsonPath("legacyData.outcomeDispositionCode", equalTo("F"))),
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
          courtSentencingNomisApiMockServer.stubGetOffenderCharge(
            offenderNo = OFFENDER_ID_DISPLAY,
            offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
          )

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
          waitForTelemetry(times(1))
        }

        @Test
        fun `the event is skipped`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-charge-synchronisation-deleted-skipped"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
                assertThat(it["reason"]).isEqualTo("charge is not mapped")
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
        fun `the skipped event is recorded `() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-charge-synchronisation-deleted-skipped"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
                assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
                assertThat(it["reason"]).isEqualTo("court appearance is not mapped")
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
              putRequestedFor(urlPathEqualTo("/legacy/charge/$DPS_CHARGE_ID/appearance/$DPS_COURT_APPEARANCE_ID"))
                .withRequestBody(matchingJsonPath("legacyData.outcomeDispositionCode", equalTo("F")))
                .withRequestBody(matchingJsonPath("legacyData.outcomeDescription", equalTo("Imprisonment"))),
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

  @Nested
  @DisplayName("COURT_EVENT_CHARGES-LINKED")
  inner class CourtEventChargeLinked {
    val combinedCaseId = 65432L

    @Nested
    @DisplayName("When court charge was linked in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtEventChargeLinkingEvent(
            eventType = "COURT_EVENT_CHARGES-LINKED",
            auditModule = "DPS_SYNCHRONISATION",
            combinedCaseId = combinedCaseId,
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-link-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
              assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
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
    @DisplayName("When offender charge was linked in NOMIS")
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
            courtEventChargeLinkingEvent(
              eventType = "COURT_EVENT_CHARGES-LINKED",
              combinedCaseId = combinedCaseId,
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("court-charge-synchronisation-link-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
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
            courtEventChargeLinkingEvent(
              eventType = "COURT_EVENT_CHARGES-LINKED",
              combinedCaseId = combinedCaseId,
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.atLeastOnce()).trackEvent(
              eq("court-charge-synchronisation-link-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
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

          // TODO mock DPS update

          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            courtEventChargeLinkingEvent(
              eventType = "COURT_EVENT_CHARGES-LINKED",
              combinedCaseId = combinedCaseId,
            ),
          )
        }

        @Test
        fun `will update DPS with the changes`() {
          // TODO verify DPS link
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-charge-synchronisation-link-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
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
    @DisplayName("When court event charge was linked in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtEventChargeLinkingEvent(
            eventType = "COURT_EVENT_CHARGES-LINKED",
            auditModule = "DPS_SYNCHRONISATION",
            combinedCaseId = combinedCaseId,
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-charge-synchronisation-link-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
              assertThat(it["nomisCombinedCourtCaseId"]).isEqualTo(combinedCaseId.toString())
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

  @Nested
  @DisplayName("OFFENDER_CHARGES-UPDATED")
  inner class OffenderChargeUpdated {
    @Nested
    @DisplayName("When offender charge was updated in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          courtEventChargeEvent(
            eventType = "OFFENDER_CHARGES-UPDATED",
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
    @DisplayName("When offender charge was updated in NOMIS without an offence code change")
    inner class NomisUpdatedNoOffenceChange {
      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          offenderChargeEvent(
            eventType = "OFFENDER_CHARGES-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
            offenceCodeChange = "false",
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
              assertThat(it["reason"]).isEqualTo("OFFENDER_CHARGES-UPDATED change is not an offence code change")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisOffenderChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE_ID.toString())
            },
            isNull(),
          )
        }

        // will not bother getting the court charge or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(anyUrl()),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(anyUrl()),
        )
        // will not update a court charge in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When offender charge was updated in NOMIS with an offence code change")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetOffenderCharge(
          offenderChargeId = NOMIS_OFFENDER_CHARGE_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          offence = OffenceResponse(
            offenceCode = "TTT4006",
            statuteCode = "TI64",
            description = "Offender description",
          ),
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
            offenderChargeEvent(
              eventType = "OFFENDER_CHARGES-UPDATED",
              offenceCodeChange = "true",
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
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = NOMIS_OFFENDER_CHARGE_ID,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )

          dpsCourtSentencingServer.stubPutChargeForUpdate(
            chargeId = UUID.fromString(DPS_CHARGE_ID),
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            offenderChargeEvent(
              eventType = "OFFENDER_CHARGES-UPDATED",
              offenceCodeChange = "true",
            ),
          )
        }

        @Test
        fun `will update DPS with the changes`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              1,
              putRequestedFor(urlPathEqualTo("/legacy/charge/$DPS_CHARGE_ID")).withRequestBody(
                matchingJsonPath(
                  "offenceCode",
                  equalTo("TTT4006"),
                ),
              ),
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
  }

  @Nested
  @DisplayName("prison-offender-events.prisoner.merged")
  inner class PrisonerMerged {
    val offenderNumberRetained = "A1234KT"
    val offenderNumberRemoved = "A1000KT"

    @Nested
    inner class HappyPathWhenNoCasesChangedByMerge {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCasesChangedByMerge(offenderNo = offenderNumberRetained, courtCasesCreated = emptyList(), courtCasesDeactivated = emptyList())
        courtSentencingOffenderEventsQueue.sendMessage(
          mergeDomainEvent(
            bookingId = 1234,
            offenderNo = offenderNumberRetained,
            removedOffenderNo = offenderNumberRemoved,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will callback to NOMIS to find the cases changed by the merge`() {
        courtSentencingNomisApiMockServer.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/$offenderNumberRetained/sentencing/court-cases/post-merge")),
        )
      }

      @Test
      fun `will not call DPS to synchronise any cases`() {
        dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
      }

      @Test
      fun `will not call mapping service to synchronise any case mappings`() {
        courtSentencingMappingApiMockServer.verify(0, postRequestedFor(anyUrl()))
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-court-case-merge"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNumberRetained)
            assertThat(it["removedOffenderNo"]).isEqualTo(offenderNumberRemoved)
            assertThat(it["courtCasesCreatedCount"]).isEqualTo("0")
            assertThat(it["courtCasesDeactivatedCount"]).isEqualTo("0")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathWhenCasesCreatedAndDeactivatedByMerge {
      val dpsCourtCaseIdFor10001 = "3051ba19-d125-4f5f-8feb-3cf26802f488"
      val dpsCourtCaseIdFor10002 = "ef38466a-1886-477f-8e72-2e059670f5f8"
      val dpsSentenceIdForSequence1 = "0087ee53-0529-423a-a14d-f340ede43922"
      val dpsSentenceIdForSequence2 = "7b70c22c-a05b-4c78-8da3-c0d01f99400b"
      val dpsSentenceIdForSequence3 = "84430291-a381-4c99-acdb-102299324f2a"
      val dpsSentenceIdForSequence4 = "c0ba2706-6a89-4e64-ae84-a434472ca2ad"
      val dpsSentenceTermIdForSequence1Term1 = "4487ee53-0529-423a-a14d-f340ede43922"
      val dpsSentenceTermIdForSequence1Term2 = "2b70c22c-a05b-4c78-8da3-c0d01f99400b"
      val dpsSentenceTermIdForSequence2Term1 = "23430291-a381-4c99-acdb-102299324f2a"
      val dpsCourtAppearanceFor401 = "b8beca56-f155-4d35-82af-a727d16fb171"
      val dpsCourtAppearanceFor402 = "5c615d4e-891c-44c5-b3eb-349e8dd13da5"
      val dpsChargeIdFor501 = "b8a59d39-9cea-4fcd-a55b-9b53f07b089f"
      val dpsChargeIdFor502 = "50998ebf-bbbe-4bb8-971e-764fab595f80"
      val dpsChargeIdFor503 = "a580cc27-22c5-4d7d-ac16-e637e781d984"
      val dpsChargeIdFor504 = "4f21fb87-45b2-4a38-9ad8-bf2b40291adc"

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetCourtCasesChangedByMerge(
          offenderNo = offenderNumberRetained,
          courtCasesCreated = listOf(
            courtCaseResponse().copy(
              id = 20001,
              courtEvents = listOf(
                courtEventResponse(eventId = 402).copy(
                  courtEventCharges = listOf(
                    courtEventChargeResponse(eventId = 402, offenderChargeId = 503),
                    courtEventChargeResponse(eventId = 402, offenderChargeId = 504),
                  ),
                ),
              ),
              sentences = listOf(
                sentenceResponse(bookingId = 301, sentenceSequence = 1),
                sentenceResponse(bookingId = 301, sentenceSequence = 2),
              ),
            ),
            courtCaseResponse().copy(
              id = 20002,
              courtEvents = listOf(
                courtEventResponse(eventId = 401).copy(
                  courtEventCharges = listOf(
                    courtEventChargeResponse(eventId = 401, offenderChargeId = 501),
                    courtEventChargeResponse(eventId = 401, offenderChargeId = 502),
                  ),
                ),
              ),
              sentences = listOf(
                sentenceResponse(bookingId = 301, sentenceSequence = 3),
                sentenceResponse(bookingId = 301, sentenceSequence = 4),
              ),
            ),
          ),
          courtCasesDeactivated = listOf(
            courtCaseResponse().copy(
              id = 10001,
              courtEvents = listOf(
                courtEventResponse(eventId = 201).copy(
                  courtEventCharges = listOf(
                    courtEventChargeResponse(eventId = 201, offenderChargeId = 301),
                    courtEventChargeResponse(eventId = 201, offenderChargeId = 302),
                  ),
                ),
              ),
              sentences = listOf(
                sentenceResponse(bookingId = 201, sentenceSequence = 1).copy(offenderCharges = listOf(offenderChargeResponse(301))),
                sentenceResponse(bookingId = 201, sentenceSequence = 2).copy(offenderCharges = listOf(offenderChargeResponse(302))),
              ),
            ),
            courtCaseResponse().copy(
              id = 10002,
              courtEvents = listOf(
                courtEventResponse(eventId = 202).copy(
                  courtEventCharges = listOf(
                    courtEventChargeResponse(eventId = 202, offenderChargeId = 303),
                    courtEventChargeResponse(eventId = 202, offenderChargeId = 304),
                  ),
                ),
              ),
              sentences = listOf(
                sentenceResponse(bookingId = 201, sentenceSequence = 3).copy(offenderCharges = listOf(offenderChargeResponse(303))),
                sentenceResponse(bookingId = 201, sentenceSequence = 4).copy(offenderCharges = listOf(offenderChargeResponse(304))),
              ),
            ),
          ),
        )
        courtSentencingMappingApiMockServer.stubGetByNomisId(
          nomisCourtCaseId = 10001,
          dpsCourtCaseId = dpsCourtCaseIdFor10001,
        )
        courtSentencingMappingApiMockServer.stubGetByNomisId(
          nomisCourtCaseId = 10002,
          dpsCourtCaseId = dpsCourtCaseIdFor10002,
        )
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
          nomisBookingId = 201,
          nomisSentenceSequence = 1,
          dpsSentenceId = dpsSentenceIdForSequence1,
        )
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
          nomisBookingId = 201,
          nomisSentenceSequence = 2,
          dpsSentenceId = dpsSentenceIdForSequence2,
        )
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
          nomisBookingId = 201,
          nomisSentenceSequence = 3,
          dpsSentenceId = dpsSentenceIdForSequence3,
        )
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
          nomisBookingId = 201,
          nomisSentenceSequence = 4,
          dpsSentenceId = dpsSentenceIdForSequence4,
        )
        courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(
          nomisBookingId = 201,
          nomisSentenceSequence = 1,
          dpsTermId = dpsSentenceTermIdForSequence1Term1,
        )
        courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(
          nomisBookingId = 201,
          nomisSentenceSequence = 1,
          dpsTermId = dpsSentenceTermIdForSequence1Term2,
        )
        courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(
          nomisBookingId = 201,
          nomisSentenceSequence = 2,
          dpsTermId = dpsSentenceTermIdForSequence2Term1,
        )
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
          nomisCourtChargeId = 301,
        )
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
          nomisCourtChargeId = 302,
        )
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
          nomisCourtChargeId = 303,
        )
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
          nomisCourtChargeId = 304,
        )

        dpsCourtSentencingServer.stubUpdateCourtCasePostMerge(
          courtCasesCreated = dpsMigrationCreateResponse(
            courtCases = listOf(
              dpsCourtCaseIdFor10001 to 10001,
              dpsCourtCaseIdFor10002 to 10002,
            ),
            charges = listOf(
              dpsChargeIdFor501 to 501,
              dpsChargeIdFor502 to 502,
              dpsChargeIdFor503 to 503,
              dpsChargeIdFor504 to 504,
            ),
            courtAppearances = listOf(
              dpsCourtAppearanceFor401 to 401,
              dpsCourtAppearanceFor402 to 402,
            ),
            sentences = listOf(
              dpsSentenceIdForSequence1 to MigrationSentenceId(offenderBookingId = 301, sequence = 1),
              dpsSentenceIdForSequence2 to MigrationSentenceId(offenderBookingId = 301, sequence = 2),
              dpsSentenceIdForSequence3 to MigrationSentenceId(offenderBookingId = 301, sequence = 3),
              dpsSentenceIdForSequence4 to MigrationSentenceId(offenderBookingId = 301, sequence = 4),
            ),
            sentenceTerms = listOf(
              dpsSentenceTermIdForSequence1Term1 to NomisPeriodLengthId(offenderBookingId = 301, sentenceSequence = 1, termSequence = 1),
              dpsSentenceTermIdForSequence1Term2 to NomisPeriodLengthId(offenderBookingId = 301, sentenceSequence = 1, termSequence = 2),
              dpsSentenceTermIdForSequence2Term1 to NomisPeriodLengthId(offenderBookingId = 301, sentenceSequence = 2, termSequence = 1),
            ),
          ),
          courtCasesDeactivatedIds = listOf(dpsCourtCaseIdFor10001, dpsCourtCaseIdFor10002),
          sentencesDeactivatedIds = listOf(dpsSentenceIdForSequence1, dpsSentenceIdForSequence2, dpsSentenceIdForSequence3, dpsSentenceIdForSequence4),
        )
      }

      @Nested
      inner class Success {

        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubPostMigrationMapping()

          courtSentencingOffenderEventsQueue.sendMessage(
            mergeDomainEvent(
              bookingId = 1234,
              offenderNo = offenderNumberRetained,
              removedOffenderNo = offenderNumberRemoved,
            ),
          ).also { waitForAnyProcessingToComplete() }
        }

        @Test
        fun `will callback to NOMIS to find the cases changed by the merge`() {
          courtSentencingNomisApiMockServer.verify(
            getRequestedFor(urlPathEqualTo("/prisoners/$offenderNumberRetained/sentencing/court-cases/post-merge")),
          )
        }

        @Test
        fun `will call mapping service to get DPS ids for cases and sentences to updated`() {
          courtSentencingMappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases/nomis-court-case-id/10001")))
          courtSentencingMappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases/nomis-court-case-id/10002")))
          courtSentencingMappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences/nomis-booking-id/201/nomis-sentence-sequence/1")))
          courtSentencingMappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences/nomis-booking-id/201/nomis-sentence-sequence/2")))
          courtSentencingMappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences/nomis-booking-id/201/nomis-sentence-sequence/3")))
          courtSentencingMappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences/nomis-booking-id/201/nomis-sentence-sequence/4")))
        }

        @Test
        fun `will call DPS to synchronise any cases`() {
          // TODO - these verify calls will be collapsed into a single DPS API call
          dpsCourtSentencingServer.verify(postRequestedFor(urlPathEqualTo("/legacy/court-case/migration")))
          dpsCourtSentencingServer.verify(putRequestedFor(urlPathEqualTo("/legacy/court-case/$dpsCourtCaseIdFor10001")))
          dpsCourtSentencingServer.verify(putRequestedFor(urlPathEqualTo("/legacy/court-case/$dpsCourtCaseIdFor10002")))
          dpsCourtSentencingServer.verify(putRequestedFor(urlPathEqualTo("/legacy/sentence/$dpsSentenceIdForSequence1")))
          dpsCourtSentencingServer.verify(putRequestedFor(urlPathEqualTo("/legacy/sentence/$dpsSentenceIdForSequence2")))
          dpsCourtSentencingServer.verify(putRequestedFor(urlPathEqualTo("/legacy/sentence/$dpsSentenceIdForSequence3")))
          dpsCourtSentencingServer.verify(putRequestedFor(urlPathEqualTo("/legacy/sentence/$dpsSentenceIdForSequence4")))
        }

        @Test
        fun `will call mapping service to synchronise any new case mappings`() {
          courtSentencingMappingApiMockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/prisoner/$offenderNumberRetained/court-cases")))
        }

        @Test
        fun `will post any new case mappings`() {
          val request: CourtCaseMigrationMappingDto = CourtSentencingMappingApiMockServer.getRequestBody(postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/prisoner/$offenderNumberRetained/court-cases")))
          with(request) {
            assertThat(courtCases).hasSize(2)
            assertThat(courtCases[0].nomisCourtCaseId).isEqualTo(10001)
            assertThat(courtCases[0].dpsCourtCaseId).isEqualTo(dpsCourtCaseIdFor10001)
            assertThat(courtCases[1].nomisCourtCaseId).isEqualTo(10002)
            assertThat(courtCases[1].dpsCourtCaseId).isEqualTo(dpsCourtCaseIdFor10002)

            assertThat(courtAppearances).hasSize(2)
            assertThat(courtAppearances[0].nomisCourtAppearanceId).isEqualTo(401)
            assertThat(courtAppearances[0].dpsCourtAppearanceId).isEqualTo(dpsCourtAppearanceFor401)
            assertThat(courtAppearances[1].nomisCourtAppearanceId).isEqualTo(402)
            assertThat(courtAppearances[1].dpsCourtAppearanceId).isEqualTo(dpsCourtAppearanceFor402)

            assertThat(courtCharges).hasSize(4)
            assertThat(courtCharges[0].nomisCourtChargeId).isEqualTo(501)
            assertThat(courtCharges[0].dpsCourtChargeId).isEqualTo(dpsChargeIdFor501)
            assertThat(courtCharges[1].nomisCourtChargeId).isEqualTo(502)
            assertThat(courtCharges[1].dpsCourtChargeId).isEqualTo(dpsChargeIdFor502)
            assertThat(courtCharges[2].nomisCourtChargeId).isEqualTo(503)
            assertThat(courtCharges[2].dpsCourtChargeId).isEqualTo(dpsChargeIdFor503)
            assertThat(courtCharges[3].nomisCourtChargeId).isEqualTo(504)
            assertThat(courtCharges[3].dpsCourtChargeId).isEqualTo(dpsChargeIdFor504)

            assertThat(sentences).hasSize(4)
            assertThat(sentences[0].nomisBookingId).isEqualTo(301)
            assertThat(sentences[0].nomisSentenceSequence).isEqualTo(1)
            assertThat(sentences[0].dpsSentenceId).isEqualTo(dpsSentenceIdForSequence1)
            assertThat(sentences[1].nomisBookingId).isEqualTo(301)
            assertThat(sentences[1].nomisSentenceSequence).isEqualTo(2)
            assertThat(sentences[1].dpsSentenceId).isEqualTo(dpsSentenceIdForSequence2)
            assertThat(sentences[2].nomisBookingId).isEqualTo(301)
            assertThat(sentences[2].nomisSentenceSequence).isEqualTo(3)
            assertThat(sentences[2].dpsSentenceId).isEqualTo(dpsSentenceIdForSequence3)
            assertThat(sentences[3].nomisBookingId).isEqualTo(301)
            assertThat(sentences[3].nomisSentenceSequence).isEqualTo(4)
            assertThat(sentences[3].dpsSentenceId).isEqualTo(dpsSentenceIdForSequence4)
          }
        }

        @Test
        fun `will track telemetry for the merge`() {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-court-case-merge"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNumberRetained)
              assertThat(it["removedOffenderNo"]).isEqualTo(offenderNumberRemoved)
              assertThat(it["courtCasesCreatedCount"]).isEqualTo("2")
              assertThat(it["courtCasesDeactivatedCount"]).isEqualTo("2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class EventualSuccess {

        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubPostMigrationMappingFailureFollowedBySuccess()

          courtSentencingOffenderEventsQueue.sendMessage(
            mergeDomainEvent(
              bookingId = 1234,
              offenderNo = offenderNumberRetained,
              removedOffenderNo = offenderNumberRemoved,
            ),
          ).also { waitForAnyProcessingToComplete("from-nomis-synch-court-case-merge-mapping-retry-success") }
        }

        @Test
        fun `will callback to NOMIS to find the cases changed by the merge once`() {
          courtSentencingNomisApiMockServer.verify(
            1,
            getRequestedFor(urlPathEqualTo("/prisoners/$offenderNumberRetained/sentencing/court-cases/post-merge")),
          )
        }

        @Test
        fun `will call DPS to synchronise any cases once`() {
          // TODO - these verify calls will be collapsed into a single DPS API call
          dpsCourtSentencingServer.verify(1, postRequestedFor(urlPathEqualTo("/legacy/court-case/migration")))
          dpsCourtSentencingServer.verify(1, putRequestedFor(urlPathEqualTo("/legacy/court-case/$dpsCourtCaseIdFor10001")))
          dpsCourtSentencingServer.verify(1, putRequestedFor(urlPathEqualTo("/legacy/court-case/$dpsCourtCaseIdFor10002")))
          dpsCourtSentencingServer.verify(1, putRequestedFor(urlPathEqualTo("/legacy/sentence/$dpsSentenceIdForSequence1")))
          dpsCourtSentencingServer.verify(1, putRequestedFor(urlPathEqualTo("/legacy/sentence/$dpsSentenceIdForSequence2")))
          dpsCourtSentencingServer.verify(1, putRequestedFor(urlPathEqualTo("/legacy/sentence/$dpsSentenceIdForSequence3")))
          dpsCourtSentencingServer.verify(1, putRequestedFor(urlPathEqualTo("/legacy/sentence/$dpsSentenceIdForSequence4")))
        }

        @Test
        fun `will call mapping service to synchronise any new case mappings until it succeeds`() {
          courtSentencingMappingApiMockServer.verify(2, postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/prisoner/$offenderNumberRetained/court-cases")))
        }

        @Test
        fun `will track telemetry for the merge`() {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-court-case-merge"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNumberRetained)
              assertThat(it["removedOffenderNo"]).isEqualTo(offenderNumberRemoved)
              assertThat(it["courtCasesCreatedCount"]).isEqualTo("2")
              assertThat(it["courtCasesDeactivatedCount"]).isEqualTo("2")
            },
            isNull(),
          )
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-court-case-merge-mapping-retry-success"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNumberRetained)
              assertThat(it["removedOffenderNo"]).isEqualTo(offenderNumberRemoved)
              assertThat(it["courtCasesCreatedCount"]).isEqualTo("2")
              assertThat(it["courtCasesDeactivatedCount"]).isEqualTo("2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_FIXED_TERM_RECALLS-UPDATED")
  inner class FixedTermRecallUpdated {
    val bookingId = NOMIS_BOOKING_ID
    val offenderNo = OFFENDER_ID_DISPLAY

    @Nested
    inner class UpdatedInNomis {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetOffenderActiveRecallSentences(
          bookingId,
          listOf(
            sentenceResponse(
              bookingId = bookingId,
              sentenceSequence = 1,
            ).copy(
              offenderCharges = listOf(
                offenderChargeResponse(
                  offenderChargeId = 101,
                ),
              ),
              recallCustodyDate = RecallCustodyDate(
                returnToCustodyDate = LocalDate.parse("2023-04-01"),
                recallLength = 14,
              ),
            ),
            sentenceResponse(
              bookingId = bookingId,
              sentenceSequence = 2,
            ).copy(
              offenderCharges = listOf(
                offenderChargeResponse(
                  offenderChargeId = 201,
                ),
              ),
              recallCustodyDate = RecallCustodyDate(
                returnToCustodyDate = LocalDate.parse("2023-04-01"),
                recallLength = 14,
              ),
            ),
          ),
        )
        courtSentencingMappingApiMockServer.stubGetSentencesByNomisIds(
          listOf(
            SentenceMappingDto(
              nomisBookingId = bookingId,
              nomisSentenceSequence = 1,
              dpsSentenceId = "612cf742-feea-4562-b01d-ce643146fcf1",
            ),
            SentenceMappingDto(
              nomisBookingId = bookingId,
              nomisSentenceSequence = 2,
              dpsSentenceId = "870f7e03-6bfc-46e6-9782-a91daab5eab4",
            ),
          ),
        )

        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
          nomisCourtChargeId = 101,
        )
        courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
          nomisCourtChargeId = 201,
        )
        dpsCourtSentencingServer.stubPutSentenceForUpdate(sentenceId = "612cf742-feea-4562-b01d-ce643146fcf1")
        dpsCourtSentencingServer.stubPutSentenceForUpdate(sentenceId = "870f7e03-6bfc-46e6-9782-a91daab5eab4")

        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          offenderFixedTermRecalls(
            eventType = "OFFENDER_FIXED_TERM_RECALLS-UPDATED",
            auditModule = "OIUFTRDA",
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will retrieve active recall sentences from NOMIS`() {
        courtSentencingNomisApiMockServer.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/booking-id/$bookingId/sentences/recall")),
        )
      }

      @Test
      fun `will retrieve the DPS IDs from the mapping service`() {
        courtSentencingMappingApiMockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences/nomis-sentence-ids/get-list"))
            .withRequestBodyJsonPath("[0].nomisBookingId", equalTo(bookingId.toString()))
            .withRequestBodyJsonPath("[0].nomisSentenceSequence", equalTo("1"))
            .withRequestBodyJsonPath("[1].nomisBookingId", equalTo(bookingId.toString()))
            .withRequestBodyJsonPath("[1].nomisSentenceSequence", equalTo("2")),
        )
      }

      @Test
      fun `will update DPS with the latest custody date`() {
        val request1: LegacyCreateSentence = CourtSentencingDpsApiMockServer.getRequestBody(putRequestedFor(urlPathEqualTo("/legacy/sentence/612cf742-feea-4562-b01d-ce643146fcf1")))
        with(request1) {
          assertThat(this.returnToCustodyDate).isEqualTo(LocalDate.parse("2023-04-01"))
        }

        val request2: LegacyCreateSentence = CourtSentencingDpsApiMockServer.getRequestBody(putRequestedFor(urlPathEqualTo("/legacy/sentence/870f7e03-6bfc-46e6-9782-a91daab5eab4")))
        with(request2) {
          assertThat(this.returnToCustodyDate).isEqualTo(LocalDate.parse("2023-04-01"))
        }
      }

      @Test
      fun `will track telemetry for success`() {
        verify(telemetryClient).trackEvent(
          eq("recall-custody-date-synchronisation-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo("$bookingId")
            assertThat(it["changeType"]).isEqualTo("OFFENDER_FIXED_TERM_RECALLS-UPDATED")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MissingMappingsFailure {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetOffenderActiveRecallSentences(
          bookingId,
          listOf(
            sentenceResponse(
              bookingId = bookingId,
              sentenceSequence = 1,
            ),
            sentenceResponse(
              bookingId = bookingId,
              sentenceSequence = 2,
            ),
          ),
        )

        courtSentencingMappingApiMockServer.stubGetSentencesByNomisIds(
          listOf(
            SentenceMappingDto(
              nomisBookingId = bookingId,
              nomisSentenceSequence = 2,
              dpsSentenceId = "612cf742-feea-4562-b01d-ce643146fcf1",
            ),
          ),
        )
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          offenderFixedTermRecalls(
            eventType = "OFFENDER_FIXED_TERM_RECALLS-UPDATED",
            auditModule = "OIUFTRDA",
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will retrieve active recall sentences from NOMIS`() {
        courtSentencingNomisApiMockServer.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/booking-id/$bookingId/sentences/recall")),
        )
      }

      @Test
      fun `will retrieve the DPS IDs from the mapping service`() {
        courtSentencingMappingApiMockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences/nomis-sentence-ids/get-list"))
            .withRequestBodyJsonPath("[0].nomisBookingId", equalTo(bookingId.toString()))
            .withRequestBodyJsonPath("[0].nomisSentenceSequence", equalTo("1"))
            .withRequestBodyJsonPath("[1].nomisBookingId", equalTo(bookingId.toString()))
            .withRequestBodyJsonPath("[1].nomisSentenceSequence", equalTo("2")),
        )
      }

      @Test
      fun `will track telemetry for failure`() {
        verify(telemetryClient).trackEvent(
          eq("recall-custody-date-synchronisation-error"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo("$bookingId")
            assertThat(it["changeType"]).isEqualTo("OFFENDER_FIXED_TERM_RECALLS-UPDATED")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class UpdatedInNomisNoActiveRecallSentences {
      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetOffenderActiveRecallSentences(
          bookingId,
          emptyList(),
        )
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          offenderFixedTermRecalls(
            eventType = "OFFENDER_FIXED_TERM_RECALLS-UPDATED",
            auditModule = "OIUFTRDA",
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will retrieve active recall sentences from NOMIS`() {
        courtSentencingNomisApiMockServer.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/booking-id/$bookingId/sentences/recall")),
        )
      }

      @Test
      fun `will track telemetry for success`() {
        verify(telemetryClient).trackEvent(
          eq("recall-custody-date-synchronisation-ignored"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo("$bookingId")
            assertThat(it["reason"]).isEqualTo("No active recall sentences found for booking $bookingId")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class UpdatedInDps {
      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          offenderFixedTermRecalls(
            eventType = "OFFENDER_FIXED_TERM_RECALLS-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
            bookingId = bookingId,
            offenderNo = offenderNo,
          ),
        ).also { waitForAnyProcessingToComplete() }
      }

      @Test
      fun `will track telemetry for skip`() {
        verify(telemetryClient).trackEvent(
          eq("recall-custody-date-synchronisation-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo("$bookingId")
            assertThat(it["changeType"]).isEqualTo("OFFENDER_FIXED_TERM_RECALLS-UPDATED")
          },
          isNull(),
        )
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

fun courtCaseLinkingEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  caseId: Long = NOMIS_COURT_CASE_ID,
  combinedCaseId: Long = 4321L,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"caseId\": \"$caseId\",\"combinedCaseId\": \"$combinedCaseId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
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
  courtCaseId: Long? = NOMIS_COURT_CASE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"$courtAppearanceId\",${courtCaseId.let {"""\"caseId\":\"$courtCaseId\","""}}\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
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

fun courtEventChargeLinkingEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  eventId: Long = NOMIS_COURT_APPEARANCE_ID,
  chargeId: Long = NOMIS_OFFENDER_CHARGE_ID,
  combinedCaseId: Long = 4321L,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "NOMIS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"combinedCaseId\": \"$combinedCaseId\",\"eventId\": \"$eventId\",\"chargeId\": \"$chargeId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
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
  offenceCodeChange: String = "true",
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"chargeId\": \"$chargeId\",\"offenceCodeChange\": \"$offenceCodeChange\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun offenderFixedTermRecalls(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = //language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"$eventType\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
