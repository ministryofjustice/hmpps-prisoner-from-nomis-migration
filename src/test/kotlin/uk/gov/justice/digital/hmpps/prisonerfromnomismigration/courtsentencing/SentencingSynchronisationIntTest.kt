package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
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
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.isNotNull
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.verification.VerificationMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension.Companion.dpsCourtSentencingServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RecallCustodyDate
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate
import java.util.AbstractMap.SimpleEntry

private const val NOMIS_SENTENCE_SEQUENCE = 1
private const val NOMIS_TERM_SEQUENCE = 6
private const val DPS_SENTENCE_ID = "c1c1e2e2-2e3e-3e3e-3e3e-3e3e3e3e3e3e"
private const val DPS_TERM_ID = "d5c1e2e2-2e3e-3e3e-3e3e-3e3e3e3e3e3d"
private const val DPS_CASE_ID = "c7c1e2e2-2e3e-3e3e-3e3e-3e3e3e3e3e3d"
private const val DPS_APPEARANCE_ID = "d8c1e3e3-3e3e-3e3e-3e3e-3e3e3e3d7d7d"
private const val DPS_CHARGE_ID = "f1c1e3e3-3e3e-3e3e-3e3e-3e3e3e3e3e3e"
private const val DPS_CHARGE_2_ID = "d1c1e2e2-2e3e-3e3e-3e3e-3e3e3e3e3e3e"
private const val EXISTING_DPS_SENTENCE_ID = "c2c1e2e2-2e3e-3e3e-3e3e-3e3e3e3e3e3e"
private const val EXISTING_DPS_TERM_ID = "d2c1e2e2-2e3e-3e3e-3e3e-3e3e3e3e3e3d"
private const val OFFENDER_ID_DISPLAY = "A3864DZ"
private const val NOMIS_BOOKING_ID = 12344321L
private const val NOMIS_COURT_CASE_ID = 1234L
private const val NOMIS_COURT_APPEARANCE_ID = 5555L
private const val NOMIS_OFFENDER_CHARGE = 12L
private const val NOMIS_CONSEC_SENTENCE_SEQUENCE = 7777L
private const val DPS_CONSECUTIVE_SENTENCE_ID = "c4c1e2e2-2e3e-3e3e-3e3e-3e3e3e3e3e2d"

class SentencingSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var courtSentencingMappingApiService: CourtSentencingMappingApiService

  @Autowired
  private lateinit var courtSentencingNomisApiMockServer: CourtSentencingNomisApiMockServer

  @Autowired
  private lateinit var courtSentencingMappingApiMockServer: CourtSentencingMappingApiMockServer

  @Autowired
  private lateinit var jsonMapper: JsonMapper

  private fun Any.toJson(): String = jsonMapper.writeValueAsString(this)

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
          courtOrder = buildCourtOrderResponse(),
        )

        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          dpsCourtAppearanceId = DPS_APPEARANCE_ID,
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
            courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
            recallCustodyDate = RecallCustodyDate(
              returnToCustodyDate = LocalDate.parse("2023-01-01"),
              recallLength = 14,
            ),
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
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
                .withRequestBody(matchingJsonPath("chargeUuids[0]", equalTo(DPS_CHARGE_ID)))
                .withRequestBody(matchingJsonPath("chargeUuids[1]", equalTo(DPS_CHARGE_2_ID)))
                .withRequestBody(matchingJsonPath("active", equalTo("false")))
                .withRequestBody(matchingJsonPath("fine.fineAmount", equalTo("1.1")))
                .withRequestBody(matchingJsonPath("returnToCustodyDate", equalTo("2023-01-01")))
                .withRequestBody(matchingJsonPath("legacyData.bookingId", equalTo(NOMIS_BOOKING_ID.toString()))),
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
      inner class NoMappingMultipleSentences {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
            courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          )
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
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
                .withRequestBody(matchingJsonPath("chargeUuids[0]", equalTo(DPS_CHARGE_ID)))
                .withRequestBody(matchingJsonPath("chargeUuids[1]", equalTo(DPS_CHARGE_2_ID)))
                .withRequestBody(matchingJsonPath("active", equalTo("false")))
                .withRequestBody(matchingJsonPath("fine.fineAmount", equalTo("1.1"))),
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
      @DisplayName("Happy path - with consecutive sentence")
      inner class NoMappingWithConsecutiveSentence {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
            consecSequence = NOMIS_CONSEC_SENTENCE_SEQUENCE.toInt(),
            courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
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
      @DisplayName("Sentences without a case or level of 'AGG' or category of 'LICENCE' are ignored")
      inner class SentenceNotInScope {
        @BeforeEach
        fun setUp() {
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-INSERTED",
              sentenceLevel = "AGG",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will ignore sentences that are not in scope`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-created-ignored"),
              check {
                assertThat(it["reason"]).isEqualTo("sentence not in scope")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
            // will not create a sentence in DPS
            dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
          }
        }
      }

      @Nested
      @DisplayName("Sentences without a charge are invalid and will be rejected")
      inner class NoChargeExists {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
            offenderCharges = emptyList(),
            courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
            recallCustodyDate = RecallCustodyDate(
              returnToCustodyDate = LocalDate.parse("2023-01-01"),
              recallLength = 14,
            ),
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
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
        fun `will reject sentences that do not have charges`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-created-failed"),
              check {
                assertThat(it["reason"]).isEqualTo("No charges associated with sentence")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
            // will not create a sentence in DPS
            dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))

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
            courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
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
      @DisplayName("When court order court appearance not mapped")
      inner class NoAssociatedCourtAppearanceMappingFail {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          mockTwoChargeMappingGets()
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
            courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)

          dpsCourtSentencingServer.stubPostSentenceForCreate(sentenceId = DPS_SENTENCE_ID)

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
        fun `will not create a sentence in DPS`() {
          // sentence event may come after term event
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceTermEvent(
              eventType = "OFFENDER_SENTENCE_TERMS-INSERTED",
            ),
          ).also {
            waitForTelemetry()
          }

          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("sentence-synchronisation-created-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["reason"]).isEqualTo("parent court appearance $NOMIS_COURT_APPEARANCE_ID is not mapped")
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
          dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
        }
      }

      @Nested
      @DisplayName("When error from dps creating sentence")
      inner class FailureFromDps {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
            courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
            recallCustodyDate = RecallCustodyDate(
              returnToCustodyDate = LocalDate.parse("2023-01-01"),
              recallLength = 14,
            ),
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
          )

          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          mockTwoChargeMappingGets()
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
          )
          dpsCourtSentencingServer.stubPostSentenceForCreateError()
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
        fun `will track a telemetry event for error`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-created-error"),
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
      @DisplayName("Sentence includes a charge that is not mapped. Possible causes: unmigrated data, events (extremely) out of order")
      inner class ReferencesMissingChargeMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
            courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
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
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
            courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
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
            courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          )
          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
            nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
            dpsCourtAppearanceId = DPS_APPEARANCE_ID,
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
          courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
        )
        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          dpsCourtAppearanceId = DPS_APPEARANCE_ID,
        )

        courtSentencingMappingApiMockServer.stubGetByNomisId(nomisCourtCaseId = NOMIS_COURT_CASE_ID)

        // in the case of multiple events received at the same time - mapping doesn't exist
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
        mockTwoChargeMappingGets()

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
      @DisplayName("Sentences without a case or level of 'AGG' or category of 'LICENCE' are ignored")
      inner class SentenceNotInScope {
        @BeforeEach
        fun setUp() {
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-DELETED",
              sentenceCategory = "LICENCE",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will ignore sentences that are not in scope`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-deleted-ignored"),
              check {
                assertThat(it["reason"]).isEqualTo("sentence not in scope")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisSentenceLevel"]).isEqualTo("IND")
                assertThat(it["nomisSentenceCategory"]).isEqualTo("LICENCE")
                assertThat(it["nomisCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              },
              isNull(),
            )
            // will not create a sentence in DPS
            dpsCourtSentencingServer.verify(0, deleteRequestedFor(anyUrl()))
          }
        }
      }

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
              deleteRequestedFor(urlPathEqualTo("/legacy/sentence/$DPS_SENTENCE_ID")),
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
              deleteRequestedFor(urlPathEqualTo("/legacy/sentence/$DPS_SENTENCE_ID")),
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

            // web client retry twice more
            courtSentencingMappingApiMockServer.verify(
              3,
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
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
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
          courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          recallCustodyDate = RecallCustodyDate(
            returnToCustodyDate = LocalDate.parse("2024-01-01"),
            recallLength = 28,
          ),
        )

        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          dpsCourtAppearanceId = DPS_APPEARANCE_ID,
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
      @DisplayName("When mapping doesn't exist as sentence was deleted - update event received after deletion")
      inner class MappingDoesNotExistSentenceDeleted {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
          courtSentencingNomisApiMockServer.stubGetSentence(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-UPDATED",
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          courtSentencingNomisApiMockServer.stubGetSentence(status = NOT_FOUND)
          await untilAsserted {
            verify(telemetryClient, times(1)).trackEvent(
              eq("sentence-synchronisation-updated-skipped"),
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
        fun `the event is not placed on dead letter queue`() {
          courtSentencingNomisApiMockServer.stubGetSentence(status = NOT_FOUND)
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
              putRequestedFor(urlPathEqualTo("/legacy/sentence/$DPS_SENTENCE_ID"))
                .withRequestBody(matchingJsonPath("fine.fineAmount", equalTo("1.1")))
                .withRequestBody(matchingJsonPath("active", equalTo("false")))
                .withRequestBody(matchingJsonPath("chargeUuids[0]", equalTo(DPS_CHARGE_ID)))
                .withRequestBody(matchingJsonPath("chargeUuids[1]", equalTo(DPS_CHARGE_2_ID)))
                .withRequestBody(matchingJsonPath("legacyData.postedDate", isNotNull()))
                .withRequestBody(matchingJsonPath("legacyData.sentenceCalcType", equalTo("ADIMP_ORA")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceTypeDesc", equalTo("ADIMP_ORA description")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceCategory", equalTo("2003")))
                .withRequestBody(matchingJsonPath("returnToCustodyDate", equalTo("2024-01-01")))
                .withRequestBody(matchingJsonPath("legacyData.bookingId", equalTo(NOMIS_BOOKING_ID.toString()))),
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

      @Nested
      @DisplayName("When error from dps updating sentence")
      inner class FailureFromDps {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            nomisBookingId = NOMIS_BOOKING_ID,
            dpsSentenceId = DPS_SENTENCE_ID,
          )
          mockTwoChargeMappingGets()

          dpsCourtSentencingServer.stubPutSentenceForUpdate(sentenceId = DPS_SENTENCE_ID, status = INTERNAL_SERVER_ERROR)
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
        fun `will track a telemetry event for error`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-updated-error"),
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
      @DisplayName("Sentences without a case or level of 'AGG' or category of 'LICENCE' are ignored")
      inner class SentenceNotInScope {
        @BeforeEach
        fun setUp() {
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceEvent(
              eventType = "OFFENDER_SENTENCES-UPDATED",
              sentenceLevel = "AGG",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will ignore sentences that are not in scope`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-synchronisation-updated-ignored"),
              check {
                assertThat(it["reason"]).isEqualTo("sentence not in scope")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisSentenceLevel"]).isEqualTo("AGG")
                assertThat(it["nomisSentenceCategory"]).isEqualTo("2020")
                assertThat(it["nomisCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID.toString())
              },
              isNull(),
            )
            // will not create a sentence in DPS
            dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
          }
        }
      }

      @Nested
      @DisplayName("When court order court appearance not mapped")
      inner class NoAssociatedCourtAppearanceMappingFail {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            nomisBookingId = NOMIS_BOOKING_ID,
            dpsSentenceId = DPS_SENTENCE_ID,
          )
          mockTwoChargeMappingGets()
          courtSentencingNomisApiMockServer.stubGetSentence(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            caseId = NOMIS_COURT_CASE_ID,
            courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          )

          courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(status = NOT_FOUND)

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
        fun `will not update a sentence in DPS`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("sentence-synchronisation-updated-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["reason"]).isEqualTo("parent court appearance $NOMIS_COURT_APPEARANCE_ID is not mapped")
                assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
              },
              isNull(),
            )
          }
          dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_SENTENCE_CHARGES-DELETED")
  inner class SentenceChargeDeleted {

    @Nested
    @DisplayName("When mapping doesn't exist and nomis sentence does exist")
    inner class SentenceMappingDoesNotExist {
      @BeforeEach
      fun setUp() {
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
        courtSentencingNomisApiMockServer.stubGetSentence(
          endpointUsingCase = false,
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          caseId = NOMIS_COURT_CASE_ID,
          courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          recallCustodyDate = RecallCustodyDate(
            returnToCustodyDate = LocalDate.parse("2024-01-01"),
            recallLength = 28,
          ),
        )
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          sentenceChargeEvent(
            eventType = "OFFENDER_SENTENCE_CHARGES-DELETED",
          ),
        )
      }

      @Test
      fun `telemetry added to track the skip`() {
        await untilAsserted {
          verify(telemetryClient, times(1)).trackEvent(
            eq("sentence-charge-synchronisation-deleted-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE.toString())
              assertThat(it["reason"]).isEqualTo("sentence mapping does not exist, no update required")
            },
            isNull(),
          )
        }
      }

      @Test
      fun `the event is not placed on dead letter queue`() {
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
    @DisplayName("When mapping for parent sentence (and actual nomis sentence) do not exist")
    inner class SentenceMappingAndSentenceParentDoNotExist {
      @BeforeEach
      fun setUp() {
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
        courtSentencingNomisApiMockServer.stubGetSentenceByBooking(status = NOT_FOUND)
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          sentenceChargeEvent(
            eventType = "OFFENDER_SENTENCE_CHARGES-DELETED",
          ),
        )
      }

      @Test
      fun `telemetry added to track the skip`() {
        await untilAsserted {
          verify(telemetryClient, times(1)).trackEvent(
            eq("sentence-charge-synchronisation-deleted-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE.toString())
              assertThat(it["reason"]).isEqualTo("sentence does not exist in nomis, no update required")
            },
            isNull(),
          )
        }
      }

      @Test
      fun `the event is not placed on dead letter queue`() {
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
    @DisplayName("When sentence mapping exists")
    inner class SentenceMappingExists {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetSentence(
          endpointUsingCase = false,
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          caseId = NOMIS_COURT_CASE_ID,
          courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          recallCustodyDate = RecallCustodyDate(
            returnToCustodyDate = LocalDate.parse("2024-01-01"),
            recallLength = 28,
          ),
        )

        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          dpsCourtAppearanceId = DPS_APPEARANCE_ID,
        )
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

          // deletion of sentence charge should trigger an update to the sentence in DPS
          dpsCourtSentencingServer.stubPutSentenceForUpdate(sentenceId = DPS_SENTENCE_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceChargeEvent(
              eventType = "OFFENDER_SENTENCE_CHARGES-DELETED",
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
              putRequestedFor(urlPathEqualTo("/legacy/sentence/$DPS_SENTENCE_ID"))
                .withRequestBody(matchingJsonPath("fine.fineAmount", equalTo("1.1")))
                .withRequestBody(matchingJsonPath("active", equalTo("false")))
                .withRequestBody(matchingJsonPath("chargeUuids[0]", equalTo(DPS_CHARGE_ID)))
                .withRequestBody(matchingJsonPath("chargeUuids[1]", equalTo(DPS_CHARGE_2_ID)))
                .withRequestBody(matchingJsonPath("legacyData.postedDate", isNotNull()))
                .withRequestBody(matchingJsonPath("legacyData.sentenceCalcType", equalTo("ADIMP_ORA")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceTypeDesc", equalTo("ADIMP_ORA description")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceCategory", equalTo("2003")))
                .withRequestBody(matchingJsonPath("returnToCustodyDate", equalTo("2024-01-01")))
                .withRequestBody(matchingJsonPath("legacyData.bookingId", equalTo(NOMIS_BOOKING_ID.toString()))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-charge-synchronisation-deleted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE.toString())
                assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When error from dps updating sentence")
      inner class FailureFromDps {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            nomisBookingId = NOMIS_BOOKING_ID,
            dpsSentenceId = DPS_SENTENCE_ID,
          )
          mockTwoChargeMappingGets()

          // deletion of sentence charge should trigger an update to the sentence in DPS - return an error
          dpsCourtSentencingServer.stubPutSentenceForUpdate(sentenceId = DPS_SENTENCE_ID, status = INTERNAL_SERVER_ERROR)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceChargeEvent(
              eventType = "OFFENDER_SENTENCE_CHARGES-DELETED",
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
              putRequestedFor(urlPathEqualTo("/legacy/sentence/$DPS_SENTENCE_ID"))
                .withRequestBody(matchingJsonPath("fine.fineAmount", equalTo("1.1")))
                .withRequestBody(matchingJsonPath("active", equalTo("false")))
                .withRequestBody(matchingJsonPath("chargeUuids[0]", equalTo(DPS_CHARGE_ID)))
                .withRequestBody(matchingJsonPath("chargeUuids[1]", equalTo(DPS_CHARGE_2_ID)))
                .withRequestBody(matchingJsonPath("legacyData.postedDate", isNotNull()))
                .withRequestBody(matchingJsonPath("legacyData.sentenceCalcType", equalTo("ADIMP_ORA")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceTypeDesc", equalTo("ADIMP_ORA description")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceCategory", equalTo("2003")))
                .withRequestBody(matchingJsonPath("returnToCustodyDate", equalTo("2024-01-01")))
                .withRequestBody(matchingJsonPath("legacyData.bookingId", equalTo(NOMIS_BOOKING_ID.toString()))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for the error`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-charge-synchronisation-deleted-error"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE.toString())
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
  @DisplayName("OFFENDER_SENTENCE_CHARGES-INSERTED")
  inner class SentenceChargeInserted {

    @Nested
    @DisplayName("When mapping for parent sentence does not exist")
    inner class SentenceMappingAndSentenceParentDoNotExist {
      @BeforeEach
      fun setUp() {
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(status = NOT_FOUND)
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          sentenceChargeEvent(
            eventType = "OFFENDER_SENTENCE_CHARGES-INSERTED",
          ),
        )
      }

      @Test
      fun `telemetry added to track the skip`() {
        await untilAsserted {
          verify(telemetryClient, times(1)).trackEvent(
            eq("sentence-charge-synchronisation-inserted-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE.toString())
              assertThat(it["reason"]).isEqualTo("sentence mapping does not exist, no update required")
            },
            isNull(),
          )
        }
      }

      @Test
      fun `the event is not placed on dead letter queue`() {
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
    @DisplayName("When sentence mapping exists")
    inner class SentenceMappingExists {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetSentence(
          endpointUsingCase = false,
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          caseId = NOMIS_COURT_CASE_ID,
          courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          recallCustodyDate = RecallCustodyDate(
            returnToCustodyDate = LocalDate.parse("2024-01-01"),
            recallLength = 28,
          ),
        )

        courtSentencingMappingApiMockServer.stubGetCourtAppearanceByNomisId(
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
          dpsCourtAppearanceId = DPS_APPEARANCE_ID,
        )
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

          // insert of sentence charge should trigger an update to the sentence in DPS
          dpsCourtSentencingServer.stubPutSentenceForUpdate(sentenceId = DPS_SENTENCE_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceChargeEvent(
              eventType = "OFFENDER_SENTENCE_CHARGES-INSERTED",
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
              putRequestedFor(urlPathEqualTo("/legacy/sentence/$DPS_SENTENCE_ID"))
                .withRequestBody(matchingJsonPath("fine.fineAmount", equalTo("1.1")))
                .withRequestBody(matchingJsonPath("active", equalTo("false")))
                .withRequestBody(matchingJsonPath("chargeUuids[0]", equalTo(DPS_CHARGE_ID)))
                .withRequestBody(matchingJsonPath("chargeUuids[1]", equalTo(DPS_CHARGE_2_ID)))
                .withRequestBody(matchingJsonPath("legacyData.postedDate", isNotNull()))
                .withRequestBody(matchingJsonPath("legacyData.sentenceCalcType", equalTo("ADIMP_ORA")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceTypeDesc", equalTo("ADIMP_ORA description")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceCategory", equalTo("2003")))
                .withRequestBody(matchingJsonPath("returnToCustodyDate", equalTo("2024-01-01")))
                .withRequestBody(matchingJsonPath("legacyData.bookingId", equalTo(NOMIS_BOOKING_ID.toString()))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-charge-synchronisation-inserted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE.toString())
                assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When error from dps updating sentence")
      inner class FailureFromDps {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            nomisBookingId = NOMIS_BOOKING_ID,
            dpsSentenceId = DPS_SENTENCE_ID,
          )
          mockTwoChargeMappingGets()

          // deletion of sentence charge should trigger an update to the sentence in DPS - return an error
          dpsCourtSentencingServer.stubPutSentenceForUpdate(
            sentenceId = DPS_SENTENCE_ID,
            status = INTERNAL_SERVER_ERROR,
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceChargeEvent(
              eventType = "OFFENDER_SENTENCE_CHARGES-DELETED",
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
              putRequestedFor(urlPathEqualTo("/legacy/sentence/$DPS_SENTENCE_ID"))
                .withRequestBody(matchingJsonPath("fine.fineAmount", equalTo("1.1")))
                .withRequestBody(matchingJsonPath("active", equalTo("false")))
                .withRequestBody(matchingJsonPath("chargeUuids[0]", equalTo(DPS_CHARGE_ID)))
                .withRequestBody(matchingJsonPath("chargeUuids[1]", equalTo(DPS_CHARGE_2_ID)))
                .withRequestBody(matchingJsonPath("legacyData.postedDate", isNotNull()))
                .withRequestBody(matchingJsonPath("legacyData.sentenceCalcType", equalTo("ADIMP_ORA")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceTypeDesc", equalTo("ADIMP_ORA description")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceCategory", equalTo("2003")))
                .withRequestBody(matchingJsonPath("returnToCustodyDate", equalTo("2024-01-01")))
                .withRequestBody(matchingJsonPath("legacyData.bookingId", equalTo(NOMIS_BOOKING_ID.toString()))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for the error`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-charge-synchronisation-deleted-error"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_OFFENDER_CHARGE.toString())
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
  @DisplayName("courtsentencing.resync.sentence")
  inner class SentenceResynchronisation {

    @Nested
    @DisplayName("When resynchronisation of sentence required")
    inner class ResyncMessageReceived {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetSentence(
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          caseId = NOMIS_COURT_CASE_ID,
          courtOrder = buildCourtOrderResponse(eventId = NOMIS_COURT_APPEARANCE_ID),
          recallCustodyDate = RecallCustodyDate(
            returnToCustodyDate = LocalDate.parse("2024-01-01"),
            recallLength = 28,
          ),
          consecSequence = NOMIS_CONSEC_SENTENCE_SEQUENCE.toInt(),
        )
        mockTwoChargeMappingGets()

        dpsCourtSentencingServer.stubPutSentenceForUpdate(sentenceId = DPS_SENTENCE_ID)

        courtSentencingOffenderEventsQueue.sendMessage(
          SQSMessage(
            Type = "courtsentencing.resync.sentence",
            Message = OffenderSentenceResynchronisationEvent(
              offenderNo = OFFENDER_ID_DISPLAY,
              sentenceSeq = NOMIS_SENTENCE_SEQUENCE,
              bookingId = NOMIS_BOOKING_ID,
              dpsSentenceUuid = DPS_SENTENCE_ID,
              dpsAppearanceUuid = DPS_APPEARANCE_ID,
              caseId = NOMIS_COURT_CASE_ID,
              dpsConsecutiveSentenceUuid = DPS_CONSECUTIVE_SENTENCE_ID,
            ).toJson(),
          ).toJson(),
        )
      }

      @Test
      fun `will update DPS with the changes`() {
        await untilAsserted {
          dpsCourtSentencingServer.verify(
            1,
            putRequestedFor(urlPathEqualTo("/legacy/sentence/$DPS_SENTENCE_ID"))
              .withRequestBody(matchingJsonPath("fine.fineAmount", equalTo("1.1")))
              .withRequestBody(matchingJsonPath("active", equalTo("false")))
              .withRequestBody(matchingJsonPath("chargeUuids[0]", equalTo(DPS_CHARGE_ID)))
              .withRequestBody(matchingJsonPath("chargeUuids[1]", equalTo(DPS_CHARGE_2_ID)))
              .withRequestBody(matchingJsonPath("legacyData.postedDate", isNotNull()))
              .withRequestBody(matchingJsonPath("legacyData.sentenceCalcType", equalTo("ADIMP_ORA")))
              .withRequestBody(matchingJsonPath("legacyData.sentenceTypeDesc", equalTo("ADIMP_ORA description")))
              .withRequestBody(matchingJsonPath("legacyData.sentenceCategory", equalTo("2003")))
              .withRequestBody(matchingJsonPath("legacyData.bookingId", equalTo(NOMIS_BOOKING_ID.toString())))
              .withRequestBody(matchingJsonPath("returnToCustodyDate", equalTo("2024-01-01"))),
          )
        }
      }

      @Test
      fun `will track a telemetry event for success`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("sentence-resynchronisation-success"),
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

  @Nested
  @DisplayName("OFFENDER_SENTENCE_TERMS-INSERTED")
  inner class OffenderSentenceTermInserted {

    @Nested
    @DisplayName("When a term was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetSentenceTerm(
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          termSequence = NOMIS_TERM_SEQUENCE,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
        )
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          sentenceTermEvent(
            eventType = "OFFENDER_SENTENCE_TERMS-INSERTED",
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
            eq("sentence-term-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
            },
            isNull(),
          )
        }

        courtSentencingMappingApiMockServer.verify(
          0,
          getRequestedFor(urlPathMatching("/mapping/court-sentencing/sentence-terms/nomis-booking-id/\\d+/nomis-sentence-sequence/\\d+/nomis-term-sequence/\\d+")),
        )
        // will not create an sentence term in DPS
        dpsCourtSentencingServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When sentence term was created in NOMIS")
    inner class NomisCreated {
      @BeforeEach
      fun setUp() {
        // sentence is already created and mapped
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
          nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          nomisBookingId = NOMIS_BOOKING_ID,
          dpsSentenceId = DPS_SENTENCE_ID,
        )
      }

      @Nested
      @DisplayName("Happy path - When mapping does not exist yet")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingNomisApiMockServer.stubGetSentenceTerm(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            termSequence = NOMIS_TERM_SEQUENCE,
          )
          courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(status = NOT_FOUND)
          dpsCourtSentencingServer.stubPostPeriodLengthForCreate(
            periodLengthId = DPS_TERM_ID,
            sentenceId = DPS_SENTENCE_ID,
            appearanceId = DPS_APPEARANCE_ID,
            caseId = DPS_CASE_ID,
            prisonerId = OFFENDER_ID_DISPLAY,
          )
          courtSentencingMappingApiMockServer.stubPostSentenceTermMapping()
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceTermEvent(
              eventType = "OFFENDER_SENTENCE_TERMS-INSERTED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will create a sentence term in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              postRequestedFor(urlPathEqualTo("/legacy/period-length"))
                .withRequestBody(matchingJsonPath("periodYears", equalTo("1")))
                .withRequestBody(matchingJsonPath("periodMonths", equalTo("3")))
                .withRequestBody(matchingJsonPath("periodWeeks", equalTo("4")))
                .withRequestBody(matchingJsonPath("periodDays", equalTo("5")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceTermCode", equalTo("IMP")))
                .withRequestBody(matchingJsonPath("legacyData.lifeSentence", equalTo("false"))),
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentence-terms"))
                .withRequestBody(matchingJsonPath("dpsTermId", equalTo(DPS_TERM_ID)))
                .withRequestBody(matchingJsonPath("nomisTermSequence", equalTo(NOMIS_TERM_SEQUENCE.toString())))
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
              eq("sentence-term-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
                assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
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
          )
          courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            nomisBookingId = NOMIS_BOOKING_ID,
            nomisTermSequence = NOMIS_TERM_SEQUENCE,
            dpsTermId = DPS_TERM_ID,
          )
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceTermEvent(
              eventType = "OFFENDER_SENTENCE_TERMS-INSERTED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-term-synchronisation-created-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
                assertThat(it["reason"]).isEqualTo("sentence term mapping exists")
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
          // sentence is already created and mapped
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(dpsSentenceId = DPS_SENTENCE_ID)
          courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(status = NOT_FOUND)
          courtSentencingNomisApiMockServer.stubGetSentenceTerm(
            bookingId = NOMIS_BOOKING_ID,
            sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            offenderNo = OFFENDER_ID_DISPLAY,
            termSequence = NOMIS_TERM_SEQUENCE,
          )
          dpsCourtSentencingServer.stubPostPeriodLengthForCreate(
            periodLengthId = DPS_TERM_ID,
            sentenceId = DPS_SENTENCE_ID,
            appearanceId = DPS_APPEARANCE_ID,
            caseId = DPS_CASE_ID,
            prisonerId = OFFENDER_ID_DISPLAY,
          )
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            courtSentencingMappingApiMockServer.stubPostSentenceTermMappingFailureFollowedBySuccess()
            awsSqsCourtSentencingOffenderEventsClient.sendMessage(
              courtSentencingQueueOffenderEventsUrl,
              sentenceTermEvent(
                eventType = "OFFENDER_SENTENCE_TERMS-INSERTED",
              ),
            ).also {
              waitForTelemetry()
            }
          }

          @Test
          fun `will create a sentence in DPS`() {
            await untilAsserted {
              dpsCourtSentencingServer.verify(
                postRequestedFor(urlPathEqualTo("/legacy/period-length")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            await untilAsserted {
              courtSentencingMappingApiMockServer.verify(
                WireMock.exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentence-terms"))
                  .withRequestBody(matchingJsonPath("dpsTermId", equalTo(DPS_TERM_ID)))
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
                eq("sentence-term-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                  assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
                  assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("sentence-term-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                  assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
                  assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
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
            courtSentencingMappingApiMockServer.stubPostSentenceTermMapping(status = INTERNAL_SERVER_ERROR)
            awsSqsCourtSentencingOffenderEventsClient.sendMessage(
              courtSentencingQueueOffenderEventsUrl,
              sentenceTermEvent(
                eventType = "OFFENDER_SENTENCE_TERMS-INSERTED",
              ),
            )
            await untilCallTo {
              awsSqsCourtSentencingOffenderEventDlqClient.countAllMessagesOnQueue(
                courtSentencingQueueOffenderEventsDlqUrl,
              ).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create a sentence term in DPS`() {
            await untilAsserted {
              dpsCourtSentencingServer.verify(
                1,
                postRequestedFor(urlPathEqualTo("/legacy/period-length")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            courtSentencingMappingApiMockServer.verify(
              WireMock.exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentence-terms")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("sentence-term-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                  assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                  assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                  assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
                  assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
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
    @DisplayName("When sentence term was created in NOMIS - sentence event is slow")
    inner class NomisCreatedNoSentence {
      @Test
      internal fun `it will retry after detecting missing parent sentence`() {
        // sentence event may come after term event
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(NOT_FOUND)
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          sentenceTermEvent(
            eventType = "OFFENDER_SENTENCE_TERMS-INSERTED",
          ),
        ).also {
          waitForTelemetry()
        }

        await untilAsserted {
          verify(telemetryClient, times(2)).trackEvent(
            eq("sentence-term-synchronisation-created-failed"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
              assertThat(it["reason"]).isEqualTo("parent sentence not mapped")
              assertThat(it).doesNotContain(SimpleEntry("mapping", "initial-failure"))
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    @DisplayName("duplicate mapping - two messages received at the same time")
    inner class WhenDuplicate {

      @Test
      internal fun `it will not retry after a 409 (duplicate sentence term )`() {
        courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
          nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          nomisBookingId = NOMIS_BOOKING_ID,
          dpsSentenceId = DPS_SENTENCE_ID,
        )
        courtSentencingNomisApiMockServer.stubGetSentenceTerm(
          bookingId = NOMIS_BOOKING_ID,
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          offenderNo = OFFENDER_ID_DISPLAY,
          termSequence = NOMIS_TERM_SEQUENCE,
        )

        // in the case of multiple events received at the same time - mapping doesn't exist
        courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(status = NOT_FOUND)
        dpsCourtSentencingServer.stubPostPeriodLengthForCreate(
          periodLengthId = DPS_TERM_ID,
          sentenceId = DPS_SENTENCE_ID,
          appearanceId = DPS_APPEARANCE_ID,
          caseId = DPS_CASE_ID,
          prisonerId = OFFENDER_ID_DISPLAY,
        )

        courtSentencingMappingApiMockServer.stubSentenceTermMappingCreateConflict(
          existingDpsTermId = EXISTING_DPS_TERM_ID,
          duplicateDpsTermId = DPS_TERM_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          nomisTermSequence = NOMIS_TERM_SEQUENCE,
          nomisBookingId = NOMIS_BOOKING_ID,
        )

        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          sentenceTermEvent(
            eventType = "OFFENDER_SENTENCE_TERMS-INSERTED",
          ),
        )

        // wait for mapping calls before verifying
        await untilAsserted {
          courtSentencingMappingApiMockServer.verify(
            1,
            postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentence-terms")),
          )
        }

        // doesn't retry
        dpsCourtSentencingServer.verify(
          1,
          postRequestedFor(urlPathEqualTo("/legacy/period-length")),
        )

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            org.mockito.kotlin.eq("from-nomis-sync-sentence-term-duplicate"),
            check {
              assertThat(it["migrationId"]).isNull()
              assertThat(it["existingDpsTermId"]).isEqualTo(EXISTING_DPS_TERM_ID)
              assertThat(it["duplicateDpsTermId"]).isEqualTo(DPS_TERM_ID)
              assertThat(it["existingNomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              assertThat(it["existingNomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
              assertThat(it["duplicateNomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              assertThat(it["duplicateNomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_SENTENCE_TERMS-DELETED")
  inner class SentenceTermDeleted {

    @Nested
    @DisplayName("When sentence was deleted in NOMIS")
    inner class NomisDeleted {

      @Nested
      @DisplayName("Sentence without a case or level of 'AGG' or category of 'LICENCE' are ignored")
      inner class SentenceNotInScope {
        @BeforeEach
        fun setUp() {
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceTermEvent(
              eventType = "OFFENDER_SENTENCE_TERMS-DELETED",
            ),
          ).also {
            waitForTelemetry()
          }
        }
      }

      @Nested
      @DisplayName("When mapping does not exist")
      inner class NoMapping {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceTermEvent(
              eventType = "OFFENDER_SENTENCE_TERMS-DELETED",
            ),
          )
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-term-synchronisation-deleted-ignored"),
              check {
                assertThat(it["offenderNo"]).isEqualTo("A3864DZ")
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
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
          courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            nomisBookingId = NOMIS_BOOKING_ID,
            nomisTermSequence = NOMIS_TERM_SEQUENCE,
            dpsTermId = DPS_TERM_ID,
          )
          courtSentencingMappingApiMockServer.stubDeleteSentenceTermMapping(dpsTermId = DPS_TERM_ID)
          dpsCourtSentencingServer.stubDeletePeriodLength(DPS_TERM_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceTermEvent(
              eventType = "OFFENDER_SENTENCE_TERMS-DELETED",
            ),
          ).also {
            waitForTelemetry()
          }
        }

        @Test
        fun `will delete a sentence term in DPS`() {
          await untilAsserted {
            dpsCourtSentencingServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/legacy/period-length/$DPS_TERM_ID")),
            )
          }
        }

        @Test
        fun `will delete mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            courtSentencingMappingApiMockServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentence-terms/dps-term-id/$DPS_TERM_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-term-synchronisation-deleted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
                assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping fails to be deleted")
      inner class MappingSentenceTermDeleteFails {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            nomisBookingId = NOMIS_BOOKING_ID,
            nomisTermSequence = NOMIS_TERM_SEQUENCE,
            dpsTermId = DPS_TERM_ID,
          )

          courtSentencingMappingApiMockServer.stubDeleteSentenceTermMappingByDpsId(status = INTERNAL_SERVER_ERROR)
          dpsCourtSentencingServer.stubDeletePeriodLength(DPS_TERM_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceTermEvent(
              eventType = "OFFENDER_SENTENCE_TERMS-DELETED",
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
              deleteRequestedFor(urlPathEqualTo("/legacy/period-length/$DPS_TERM_ID")),
            )
          }
        }

        @Test
        fun `will try to delete sentence term mapping once and record failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-term-mapping-deleted-failed"),
              any(),
              isNull(),
            )

            // web client retry twice more
            courtSentencingMappingApiMockServer.verify(
              3,
              deleteRequestedFor(urlPathEqualTo("/mapping/court-sentencing/sentence-terms/dps-term-id/$DPS_TERM_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-term-synchronisation-deleted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
                assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_SENTENCE_TERMS-UPDATED")
  inner class SentenceTermUpdated {
    @Nested
    @DisplayName("When sentence was updated in DPS")
    inner class DPSUpdated {
      @BeforeEach
      fun setUp() {
        awsSqsCourtSentencingOffenderEventsClient.sendMessage(
          courtSentencingQueueOffenderEventsUrl,
          sentenceTermEvent(
            eventType = "OFFENDER_SENTENCE_TERMS-UPDATED",
            auditModule = "DPS_SYNCHRONISATION",
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("sentence-term-synchronisation-updated-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
              assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
              assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
            },
            isNull(),
          )
        }

        // will not bother getting the sentence term or the mapping
        courtSentencingNomisApiMockServer.verify(0, getRequestedFor(anyUrl()))
        courtSentencingMappingApiMockServer.verify(0, getRequestedFor(anyUrl()))
        // will not update a sentence in DPS
        dpsCourtSentencingServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When sentence was updated in NOMIS")
    inner class NomisUpdated {

      @BeforeEach
      fun setUp() {
        courtSentencingNomisApiMockServer.stubGetSentenceTerm(
          sentenceSequence = NOMIS_SENTENCE_SEQUENCE,
          bookingId = NOMIS_BOOKING_ID,
          offenderNo = OFFENDER_ID_DISPLAY,
          termSequence = NOMIS_TERM_SEQUENCE,
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceTermEvent(
              eventType = "OFFENDER_SENTENCE_TERMS-UPDATED",
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, times(2)).trackEvent(
              eq("sentence-term-synchronisation-updated-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
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
      @DisplayName("When mapping doesn't exist as sentence term was deleted - update event received after deletion")
      inner class MappingDoesNotExistSentenceTermDeleted {
        @BeforeEach
        fun setUp() {
          courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(status = NOT_FOUND)
          courtSentencingNomisApiMockServer.stubGetSentenceTerm(status = NOT_FOUND)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceTermEvent(
              eventType = "OFFENDER_SENTENCE_TERMS-UPDATED",
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          courtSentencingNomisApiMockServer.stubGetSentence(status = NOT_FOUND)
          await untilAsserted {
            verify(telemetryClient, times(1)).trackEvent(
              eq("sentence-term-synchronisation-updated-skipped"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is not placed on dead letter queue`() {
          courtSentencingNomisApiMockServer.stubGetSentenceTerm(status = NOT_FOUND)
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
          courtSentencingMappingApiMockServer.stubGetSentenceByNomisId(
            nomisBookingId = NOMIS_BOOKING_ID,
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            dpsSentenceId = DPS_CONSECUTIVE_SENTENCE_ID,
          )
          courtSentencingMappingApiMockServer.stubGetSentenceTermByNomisId(
            nomisSentenceSequence = NOMIS_SENTENCE_SEQUENCE,
            nomisBookingId = NOMIS_BOOKING_ID,
            nomisTermSequence = NOMIS_TERM_SEQUENCE,
            dpsTermId = DPS_TERM_ID,
          )

          dpsCourtSentencingServer.stubPutPeriodLengthForUpdate(periodLengthId = DPS_TERM_ID)
          awsSqsCourtSentencingOffenderEventsClient.sendMessage(
            courtSentencingQueueOffenderEventsUrl,
            sentenceTermEvent(
              eventType = "OFFENDER_SENTENCE_TERMS-UPDATED",
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
              putRequestedFor(urlPathEqualTo("/legacy/period-length/$DPS_TERM_ID"))
                .withRequestBody(matchingJsonPath("periodYears", equalTo("1")))
                .withRequestBody(matchingJsonPath("periodMonths", equalTo("3")))
                .withRequestBody(matchingJsonPath("periodWeeks", equalTo("4")))
                .withRequestBody(matchingJsonPath("periodDays", equalTo("5")))
                .withRequestBody(matchingJsonPath("legacyData.lifeSentence", equalTo("false")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceTermCode", equalTo("IMP")))
                .withRequestBody(matchingJsonPath("legacyData.sentenceTermDescription", equalTo("Imprisonment"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentence-term-synchronisation-updated-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
                assertThat(it["nomisSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQUENCE.toString())
                assertThat(it["nomisTermSequence"]).isEqualTo(NOMIS_TERM_SEQUENCE.toString())
                assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
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
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"caseId\": \"$caseId\",\"sentenceSeq\": \"$sentenceSequence\",\"sentenceLevel\": \"$sentenceLevel\",\"sentenceCategory\": \"$sentenceCategory\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun sentenceChargeEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  chargeId: Long = NOMIS_OFFENDER_CHARGE,
  sentenceSequence: Int = NOMIS_SENTENCE_SEQUENCE,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"chargeId\": \"$chargeId\",\"sentenceSeq\": \"$sentenceSequence\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

fun sentenceTermEvent(
  eventType: String,
  bookingId: Long = NOMIS_BOOKING_ID,
  sentenceSequence: Int = NOMIS_SENTENCE_SEQUENCE,
  termSequence: Int = NOMIS_TERM_SEQUENCE,
  offenderNo: String = OFFENDER_ID_DISPLAY,
  auditModule: String = "DPS",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"sentenceSeq\": \"$sentenceSequence\",\"termSequence\": \"$termSequence\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"COURT_EVENT\",\"auditModuleName\":\"$auditModule\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()
