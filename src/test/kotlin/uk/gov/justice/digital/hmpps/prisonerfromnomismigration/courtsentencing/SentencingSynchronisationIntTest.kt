package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.verification.VerificationMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension.Companion.dpsCourtSentencingServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceMappingDto
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.AbstractMap.SimpleEntry

private const val NOMIS_SENTENCE_SEQUENCE = 1
private const val DPS_SENTENCE_ID = "cc1"
private const val DPS_APPEARANCE_ID = "d8c1e3e3-3e3e-3e3e-3e3e-3e3e3e3d7d7d"
private const val DPS_CHARGE_ID = "f1c1e3e3-3e3e-3e3e-3e3e-3e3e3e3e3e3e"
private const val DPS_CHARGE_2_ID = "d1c1e2e2-2e3e-3e3e-3e3e-3e3e3e3e3e3e"
private const val EXISTING_DPS_SENTENCE_ID = "cc2"
private const val OFFENDER_ID_DISPLAY = "A3864DZ"
private const val NOMIS_BOOKING_ID = 12344321L
private const val NOMIS_COURT_CASE_ID = 1234L
private const val NOMIS_COURT_APPEARANCE_ID = 5555L
private const val NOMIS_CONSEC_SENTENCE_SEQUENCE = 7777L
private const val DPS_CONSECUTIVE_SENTENCE_ID = "c4c1e2e2-2e3e-3e3e-3e3e-3e3e3e3e3e2d"

class SentencingSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var courtSentencingNomisApiMockServer: CourtSentencingNomisApiMockServer

  @Autowired
  private lateinit var courtSentencingMappingApiMockServer: CourtSentencingMappingApiMockServer

  @Nested
  @DisplayName("OFFENDER_SENTENCES-INSERTED")
  inner class OffenderSentenceInserted {

    @Nested
    @DisplayName("When sentencing was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetSentence(
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          sentenceEvent(
            eventType = "OFFENDER_SENTENCES-INSERTED",
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
            eq("sentence-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
            },
            isNull(),
          )
        }

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/sentences/nomis-booking-id/\\d+/nomis-sentence-sequence/\\d+")),
        )
        // will not create an sentence in DPS
        dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When sentence was created in NOMIS")
    inner class NomisCreated {
      @BeforeEach
      fun setUp() {
        courtSentencingMappingApiMockServer.stubGetByNomisId(nomisCourtCaseId = NOMIS_COURT_CASE_ID)
      }

      @Nested
      @DisplayName("Happy path - When mapping does not exist yet")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          mockTwoChargeMappingGets()
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
          )
          dpsCourtSentencingServer.stubPostSentenceForCreate(sentenceId = DPS_SENTENCE_ID)
          courtSentencingMappingApiMockServer.stubPostSentenceMapping()
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-INSERTED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will create a sentence in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              postRequestedFor(urlPathEqualTo("/legacy/sentence"))
                .withRequestBody(matchingJsonPath("chargeLifetimeUuid", equalTo(DPS_CHARGE_ID)))
                .withRequestBody(matchingJsonPath("active", equalTo("false")))
                .withRequestBody(matchingJsonPath("prisonId", equalTo("MDI")))
                .withRequestBody(matchingJsonPath("fine.fineAmount", equalTo("1.1")))
                .withRequestBody(matchingJsonPath("periodLengths[0].periodYears", equalTo("1")))
                .withRequestBody(matchingJsonPath("periodLengths[0].periodMonths", equalTo("3"))),
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences"))
                .withRequestBody(matchingJsonPath("dpsSentenceId", equalTo(DPS_SENTENCE_ID)))
                .withRequestBody(matchingJsonPath("nomisSentenceSequence", equalTo(NOMIS_SENTENCE_SEQUENCE.toString())))
                .withRequestBody(matchingJsonPath("nomisBookingId", equalTo(NOMIS_BOOKING_ID.toString())))
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("Happy path - When mapping does not exist yet")
      inner class NoMappingWithConsecutiveSentence {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
            consecSequence = NOMIS_CONSEC_SENTENCE_SEQUENCE.toInt(),
          )
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
            nomisBookingId = NOMIS_BOOKING_ID,
            nomisSentenceSequence = NOMIS_CONSEC_SENTENCE_SEQUENCE.toInt(),
            dpsSentenceId = DPS_CONSECUTIVE_SENTENCE_ID,
          )
          mockTwoChargeMappingGets()
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
          )
          dpsCourtSentencingServer.stubPostSentenceForCreate(sentenceId = DPS_SENTENCE_ID)
          courtSentencingMappingApiMockServer.stubPostSentenceMapping()
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-INSERTED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will create a sentence in DPS for a consecutive sentence`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              postRequestedFor(urlPathEqualTo("/legacy/sentence"))
                .withRequestBody(matchingJsonPath("consecutiveToLifetimeUuid", equalTo(DPS_CONSECUTIVE_SENTENCE_ID))),

            )
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
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
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            nomisBookingId = NOMIS_BOOKING_ID,
            dpsSentenceId = DPS_SENTENCE_ID,
            mapping = SentenceMappingDto(
              nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
              nomisBookingId = NOMIS_BOOKING_ID,
              dpsSentenceId = DPS_SENTENCE_ID,
            ),
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-INSERTED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-created-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
          }
          // will not create a sentence in DPS
          dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping POST fails")
      inner class MappingFail {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          mockTwoChargeMappingGets()
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
          )
          dpsCourtSentencingServer.stubPostSentenceForCreate(sentenceId = DPS_SENTENCE_ID)
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            courtSentencingMappingApiMockServer.stubPostSentenceMappingFailureFollowedBySuccess()
            awsSqsCourtSentencingOffenderEventsClient.sendMessage(
              courtSentencingQueueOffenderEventsUrl,
              sentenceEvent(
                eventType = "OFFENDER_SENTENCES-INSERTED",
              ),
            ).also {
              waitForTelemetry()
            }
          }

          @Test
          fun `will create a sentence in DPS`() {
            await untilAsserted {
              dpsCourtSentencingServer.verify(
                postRequestedFor(urlPathEqualTo("/legacy/sentence")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            await untilAsserted {
              courtSentencingMappingApiMockServer.verify(
                WireMock.exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences"))
                  .withRequestBody(matchingJsonPath("dpsSentenceId", equalTo(DPS_SENTENCE_ID)))
                  .withRequestBody(
                    matchingJsonPath(
                      "nomisSentenceSequence",
                      equalTo(NOMIS_SENTENCE_SEQUENCE.toString()),
                    ),
                  )
                  .withRequestBody(matchingJsonPath("nomisBookingId", equalTo(NOMIS_BOOKING_ID.toString())))
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
                eq("sentence-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                  assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("sentence-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                  assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
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
            courtSentencingMappingApiMockServer.stubPostSentenceMapping(status = INTERNAL_SERVER_ERROR)
            awsSqsCourtSentencingOffenderEventsClient.sendMessage(
              courtSentencingQueueOffenderEventsUrl,
              sentenceEvent(
                eventType = "OFFENDER_SENTENCES-INSERTED",
              ),
            )
            await untilCallTo {
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create a sentence in DPS`() {
            await untilAsserted {
              dpsCourtSentencingServer.verify(
                1,
                postRequestedFor(urlPathEqualTo("/legacy/sentence")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            courtSentencingMappingApiMockServer.verify(
              WireMock.exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("sentence-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                  assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }
          }
        }
      }

      @Nested
      @DisplayName("Sentence includes a charge that is not mapped. Possible causes: unmigrated data, events (extremely) out of order")
      inner class ReferencesMissingChargeMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = 101,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )
          // one of the two charges is not mapped
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisIdNotFound(nomisCourtChargeId = 102)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-INSERTED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will track a telemetry event to indicate a missing mapping`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("charge-mapping-missing"),
              check {
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `will retry charge mapping retrieval as part of sentence retry`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              4,
              getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-charges/nomis-court-charge-id/\\d+")),
            )
          }
        }
      }

      @Nested
      @DisplayName("Sentence includes a charge that is not mapped. Possible causes: unmigrated data, events (extremely) out of order")
      inner class ReferencesMissingConsecutiveSentence {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
            nomisCourtChargeId = 101,
            dpsCourtChargeId = DPS_CHARGE_ID,
          )
          // one of the two charges is not mapped
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisIdNotFound(nomisCourtChargeId = 102)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-INSERTED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will track a telemetry event to indicate a missing mapping`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("charge-mapping-missing"),
              check {
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `will retry charge mapping retrieval as part of sentence retry`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              4,
              getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-charges/nomis-court-charge-id/\\d+")),
            )
          }
        }
      }

      @Nested
      @DisplayName("Error retrieving charge mapping will retry")
      inner class ChargeMappingFailsWithOtherError {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetByNomisId(nomisCourtCaseId = NOMIS_COURT_CASE_ID)
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(status = INTERNAL_SERVER_ERROR)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-INSERTED",
            ),
          )
        }

        @Test
        fun `will retry charge mapping retrieval as part of sentence retry (fails on first call so 2 calls in total)`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              2,
              getRequestedFor(urlPathMatching("/mapping/court-sentencing/court-charges/nomis-court-charge-id/\\d+")),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("duplicate mapping - two messages received at the same time")
    inner class WhenDuplicate {

      @Test
      internal fun `it will not retry after a 409 (duplicate sentence written to Sentencing API)`() {
        courtSentencingNomisApiMockServer.stubGetSentence(
          bookingId = NOMIS_BOOKING_ID,
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          offenderNo = OFFENDER_ID_DISPLAY,
          caseId = NOMIS_COURT_CASE_ID,
        )

        courtSentencingMappingApiMockServer.stubGetByNomisId(nomisCourtCaseId = NOMIS_COURT_CASE_ID)

        // in the case of multiple events received at the same time - mapping doesn't exist
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
        mockTwoChargeMappingGets()

        courtSentencingNomisApiMockServer.stubGetSentence(
          bookingId = NOMIS_BOOKING_ID,
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
        )
        dpsCourtSentencingServer.stubPostSentenceForCreate(sentenceId = DPS_SENTENCE_ID)

        courtSentencingMappingApiMockServer.stubSentenceMappingCreateConflict(
          existingDpsSentenceId = EXISTING_DPS_SENTENCE_ID,
          duplicateDpsSentenceId = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
        )

        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          sentenceEvent(
            eventType = "OFFENDER_SENTENCES-INSERTED",
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            bookingId = NOMIS_BOOKING_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
          ),
        )

        // wait for mapping calls before verifying
        await untilAsserted {
          courtSentencingMappingApiMockServer.verify(
            1,
            postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences")),
          )
        }

        // doesn't retry
        dpsCourtSentencingServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/legacy/sentence")),
        )

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            org.mockito.kotlin.eq("from-nomis-sync-sentence-duplicate"),
            check {
              assertThat(it["migrationId"]).isNull()
              assertThat(it["existingDpsSentenceId"]).isEqualTo(EXISTING_DPS_SENTENCE_ID)
              assertThat(it["duplicateDpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              assertThat(it["existingNomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              assertThat(it["duplicateNomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun mockTwoChargeMappingGets() {
    courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
      nomisCourtChargeId = 101,
      dpsCourtChargeId = DPS_CHARGE_ID,
    )
    courtSentencingMappingApiMockServer.stubGetCourtChargeByNomisId(
      nomisCourtChargeId = 102,
      dpsCourtChargeId = DPS_CHARGE_2_ID,
    )
  }

  @Nested
  @DisplayName("OFFENDER_SENTENCES-DELETED")
  inner class SentenceDeleted {

    @Nested
    @DisplayName("When sentence was deleted in NOMIS")
    inner class NomisDeleted {

      @Nested
      @DisplayName("When mapping does not exist")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-DELETED",
            ),
          )
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-deleted-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
          }
          // will not delete a sentence in DPS
          dpsCourtSentencingServer.verify(0, deleteRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When mapping exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
            nomisBookingId = NOMIS_BOOKING_ID,
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            dpsSentenceId = DPS_SENTENCE_ID,
            mapping = SentenceMappingDto(
              nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
              nomisBookingId = NOMIS_BOOKING_ID,
              dpsSentenceId = DPS_SENTENCE_ID,
            ),
          )
          courtSentencingMappingApiMockServer.stubDeleteSentenceMapping(dpsSentenceId = DPS_SENTENCE_ID)
          dpsCourtSentencingServer.stubDeleteSentence(DPS_SENTENCE_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-DELETED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will delete a sentence in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/sentence/$DPS_SENTENCE_ID")),
              // TODO DPS to implement this endpoint
            )
          }
        }

        @Test
        fun `will delete mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences/dps-sentence-id/$DPS_SENTENCE_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-deleted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping fails to be deleted")
      inner class MappingSentenceDeleteFails {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
            nomisBookingId = NOMIS_BOOKING_ID,
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            dpsSentenceId = DPS_SENTENCE_ID,
            mapping = SentenceMappingDto(
              nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
              nomisBookingId = NOMIS_BOOKING_ID,
              dpsSentenceId = DPS_SENTENCE_ID,
            ),
          )

          courtSentencingMappingApiMockServer.stubDeleteSentenceMappingByDpsId(status = INTERNAL_SERVER_ERROR)
          dpsCourtSentencingServer.stubDeleteSentence(DPS_SENTENCE_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-DELETED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will delete a sentence in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/sentence/$DPS_SENTENCE_ID")),
              // TODO DPS to implement this endpoint
            )
          }
        }

        @Test
        fun `will try to delete sentence mapping once and record failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-mapping-deleted-failed"),
              any(),
              isNull(),
            )

            courtSentencingMappingApiMockServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentences/dps-sentence-id/$DPS_SENTENCE_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-deleted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_SENTENCES-UPDATED")
  inner class SentenceUpdated {
    @Nested
    @DisplayName("When sentence was updated in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          sentenceEvent(
            eventType = "OFFENDER_SENTENCES-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("sentence-synchronisation-updated-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
            },
            isNull(),
          )
        }

        // will not bother getting the sentence or the mapping
        courtSentencingNomisApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/prisoners/booking-id/\\d+/sentencing/sentence-sequence/\\d+")),
        )

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/sentences/nomis-booking-id/\\d+/nomis-sentence-sequence/\\d+")),
        )
        // will not update a sentence in DPS
        dpsCourtSentencingServer.verify(0, WireMock.putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When sentence was updated in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetSentence(
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          caseId = NOMIS_COURT_CASE_ID,
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-UPDATED",
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("sentence-synchronisation-updated-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
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
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            nomisBookingId = NOMIS_BOOKING_ID,
            dpsSentenceId = DPS_SENTENCE_ID,
          )
          mockTwoChargeMappingGets()

          dpsCourtSentencingServer.stubPutSentenceForUpdate(sentenceId = DPS_SENTENCE_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-UPDATED",
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
              WireMock.putRequestedFor(urlPathEqualTo("/sentence/$DPS_SENTENCE_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-updated-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              },
              isNull(),
            )
          }
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
}

fun sentenceEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  caseId: Long = NOMIS_COURT_CASE_ID,
  sentenceSequence: Int = NOMIS_SENTENCE_SEQUENCE,
  sentenceLevel: String = "IND",
  sentenceCategory: String = "2020",
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"caseId\": \"$caseId\",\"sentenceSequence\": \"$sentenceSequence\",\"sentenceLevel\": \"$sentenceLevel\",\"sentenceCategory\": \"$sentenceCategory\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()
