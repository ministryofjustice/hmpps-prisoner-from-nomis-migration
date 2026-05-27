package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.exactly
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
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.NOT_FOUND
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.SentenceIdAndAdjustmentType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.SyncSentenceAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeNomisEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.KeyDateAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentencingAdjustmentType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.ADJUSTMENTS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.KEYDATE_ADJUSTMENTS_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.SENTENCE_ADJUSTMENTS_GET_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension.Companion.sentencingApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate

private const val NOMIS_ADJUSTMENT_ID = 987L
private const val ADJUSTMENT_ID = "05b332ad-58eb-4ec2-963c-c9c927856788"
private const val OFFENDER_NUMBER = "G4803UT"
private const val BOOKING_ID = 1234L
private const val SENTENCE_SEQUENCE = 1L

class SentencingSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: SentencingAdjustmentsNomisApiMockServer

  @Autowired
  private lateinit var jsonMapper: JsonMapper

  private fun Any.toJson(): String = jsonMapper.writeValueAsString(this)

  @Nested
  @DisplayName("SENTENCE_ADJUSTMENT_UPSERTED")
  inner class SentenceAdjustmentUpserted {
    @Nested
    @DisplayName("When no mapping exists - new adjustment")
    inner class WhenNoMappingFound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubAllMappingsNotFound(SENTENCE_ADJUSTMENTS_GET_MAPPING_URL)
        mappingApi.stubAllMappingsNotFound(KEYDATE_ADJUSTMENTS_GET_MAPPING_URL)
      }

      @Nested
      inner class WhenCreateByDPS {
        @BeforeEach
        fun setUp() {
          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
              auditModuleName = "DPS_SYNCHRONISATION",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              sentenceSeq = SENTENCE_SEQUENCE,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("sentence-adjustment-synchronisation-skipped"),
              check {
                assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
                assertThat(it["adjustmentId"]).isNull()
              },
              isNull(),
            )
          }

          mappingApi.verify(
            exactly(0),
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
          )
          nomisApi.verify(exactly(0), getRequestedFor(urlPathEqualTo("/sentence-adjustments/$NOMIS_ADJUSTMENT_ID")))
          sentencingApi.verify(exactly(0), postRequestedFor(urlPathEqualTo("/legacy/adjustments")))
        }
      }

      @Nested
      inner class WhenCreateByNomis {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetSentenceAdjustment(
            adjustmentId = NOMIS_ADJUSTMENT_ID,
            prisonId = "WWI",
            bookingId = BOOKING_ID,
            offenderNo = OFFENDER_NUMBER,
            sentenceSequence = SENTENCE_SEQUENCE,
            bookingSequence = 1,
          )
          sentencingApi.stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId = ADJUSTMENT_ID)
          mappingApi.stubMappingCreate(ADJUSTMENTS_CREATE_MAPPING_URL)

          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
              auditModuleName = "OIDSENAD",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              sentenceSeq = SENTENCE_SEQUENCE,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `will retrieve mapping to check if this is a new adjustment`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")))
          }
        }

        @Test
        fun `will retrieve details about the adjustment from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlPathEqualTo("/sentence-adjustments/$NOMIS_ADJUSTMENT_ID")))
          }
        }

        @Test
        fun `will create the adjustment in the sentencing service`() {
          await untilAsserted {
            sentencingApi.verify(
              postRequestedFor(urlPathEqualTo("/legacy/adjustments"))
                .withRequestBody(matchingJsonPath("bookingId", equalTo(BOOKING_ID.toString())))
                .withRequestBody(matchingJsonPath("currentTerm", equalTo("true")))
                .withRequestBody(matchingJsonPath("offenderNo", equalTo(OFFENDER_NUMBER)))
                .withRequestBody(matchingJsonPath("sentenceSequence", equalTo(SENTENCE_SEQUENCE.toString())))
                .withRequestBody(matchingJsonPath("agencyId", equalTo("WWI"))),
            )
          }
        }

        @Test
        fun `will create a mapping between the two records`() {
          await untilAsserted {
            mappingApi.verify(
              postRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments"))
                .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo(NOMIS_ADJUSTMENT_ID.toString())))
                .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("SENTENCE")))
                .withRequestBody(matchingJsonPath("adjustmentId", equalTo(ADJUSTMENT_ID))),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("sentence-adjustment-created-synchronisation-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenThereAreFailures {
        @Nested
        inner class WhenMappingCreateFailsOnce {
          @BeforeEach
          fun setUp() {
            mappingApi.stubMappingCreateFailureFollowedBySuccess(ADJUSTMENTS_CREATE_MAPPING_URL)
            nomisApi.stubGetSentenceAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID)
            sentencingApi.stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId = ADJUSTMENT_ID)

            awsSqsSentencingOffenderEventsClient.sendMessage(
              sentencingQueueOffenderEventsUrl,
              sentencingEvent(
                eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
                auditModuleName = "OIDSENAD",
                adjustmentId = NOMIS_ADJUSTMENT_ID,
                bookingId = BOOKING_ID,
                sentenceSeq = SENTENCE_SEQUENCE,
                offenderIdDisplay = OFFENDER_NUMBER,
              ),
            )
          }

          @Test
          fun `will only create the adjustment once despite the failure`() {
            await untilAsserted {
              mappingApi.verify(
                exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments"))
                  .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo(NOMIS_ADJUSTMENT_ID.toString())))
                  .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("SENTENCE")))
                  .withRequestBody(matchingJsonPath("adjustmentId", equalTo(ADJUSTMENT_ID))),
              )
            }

            sentencingApi.verify(
              exactly(1),
              postRequestedFor(urlPathEqualTo("/legacy/adjustments")),
            )
          }

          @Test
          fun `will eventually fully succeed with no messages on the dead letter queue`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                Mockito.eq("adjustment-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                  assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
                  assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                  assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                  assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
                },
                isNull(),
              )
            }
            verify(telemetryClient).trackEvent(
              Mockito.eq("sentence-adjustment-created-synchronisation-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
            await untilCallTo {
              awsSqsSentencingOffenderEventsDlqClient?.countAllMessagesOnQueue(sentencingQueueOffenderEventsDlqUrl!!)
                ?.get()
            } matches { it == 0 }
          }
        }

        @Nested
        inner class WhenMappingCreateFailsForever {
          @BeforeEach
          fun setUp() {
            mappingApi.stubSentenceAdjustmentMappingCreateFailure()
            nomisApi.stubGetSentenceAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID)
            sentencingApi.stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId = ADJUSTMENT_ID)

            awsSqsSentencingOffenderEventsClient.sendMessage(
              sentencingQueueOffenderEventsUrl,
              sentencingEvent(
                eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
                auditModuleName = "OIDSENAD",
                adjustmentId = NOMIS_ADJUSTMENT_ID,
                bookingId = BOOKING_ID,
                sentenceSeq = SENTENCE_SEQUENCE,
                offenderIdDisplay = OFFENDER_NUMBER,
              ),
            )
          }

          @Test
          fun `will only create the adjustment once despite constant failures failure`() {
            await untilAsserted {
              mappingApi.verify(
                // Once and then twice via RETRY_SYNCHRONISATION_SENTENCING_ADJUSTMENT_MAPPING message
                exactly(3),
                postRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments"))
                  .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo(NOMIS_ADJUSTMENT_ID.toString())))
                  .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("SENTENCE")))
                  .withRequestBody(matchingJsonPath("adjustmentId", equalTo(ADJUSTMENT_ID))),
              )
            }

            sentencingApi.verify(
              exactly(1),
              postRequestedFor(urlPathEqualTo("/legacy/adjustments")),
            )
          }

          @Test
          fun `will eventually partially fail with a message on the dead letter queue`() {
            await untilCallTo {
              awsSqsVisitsMigrationDlqClient?.countAllMessagesOnQueue(sentencingQueueOffenderEventsDlqUrl!!)
                ?.get()
            } matches { it == 1 }

            verify(telemetryClient).trackEvent(
              Mockito.eq("sentence-adjustment-created-synchronisation-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
                assertThat(it["mapping"]).isEqualTo("initial-failure")
              },
              isNull(),
            )

            verify(telemetryClient, never()).trackEvent(
              Mockito.eq("adjustment-mapping-created-synchronisation-success"),
              any(),
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("When mapping has duplicate - adjustment already created by another message")
    inner class WhenDuplicate {

      @Test
      internal fun `it will not retry after a 409 (duplicate adjustment written to Sentencing API)`() {
        nomisApi.stubGetSentenceAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID)
        sentencingApi.stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId = ADJUSTMENT_ID)
        mappingApi.stubSentenceAdjustmentMappingCreateConflict(
          duplicateAdjustmentId = ADJUSTMENT_ID,
          nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
        )

        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          sentencingEvent(
            eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
            auditModuleName = "OIDSENAD",
            adjustmentId = NOMIS_ADJUSTMENT_ID,
            bookingId = BOOKING_ID,
            sentenceSeq = SENTENCE_SEQUENCE,
            offenderIdDisplay = OFFENDER_NUMBER,
          ),
        )

        // wait for all mappings to be created before verifying
        await untilCallTo { mappingApi.createMappingCount(ADJUSTMENTS_CREATE_MAPPING_URL) } matches { it == 1 }

        // check that one sentence-adjustment is still created - despite it being a duplicate
        assertThat(sentencingApi.createSentenceAdjustmentForSynchronisationCount()).isEqualTo(1)

        // doesn't retry
        mappingApi.verifyCreateMappingSentenceAdjustmentIds(arrayOf(ADJUSTMENT_ID), times = 1)

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-adjustment-duplicate"),
            check {
              assertThat(it["migrationId"]).isNull()
              assertThat(it["existingAdjustmentId"]).isEqualTo("10")
              assertThat(it["duplicateAdjustmentId"]).isEqualTo(ADJUSTMENT_ID)
              assertThat(it["existingNomisAdjustmentId"]).isEqualTo("$NOMIS_ADJUSTMENT_ID")
              assertThat(it["duplicateNomisAdjustmentId"]).isEqualTo("$NOMIS_ADJUSTMENT_ID")
              assertThat(it["existingNomisAdjustmentCategory"]).isEqualTo("SENTENCE")
              assertThat(it["duplicateNomisAdjustmentCategory"]).isEqualTo("SENTENCE")
            },
            isNull(),
          )
        }
      }

      @Test
      internal fun `it will not retry after a 409 (duplicate adjustment written to Sentencing API) which was after a 500 error`() {
        nomisApi.stubGetSentenceAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID)
        sentencingApi.stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId = ADJUSTMENT_ID)
        mappingApi.stubSentenceAdjustmentMappingCreateConflictAfter500Error(
          duplicateAdjustmentId = ADJUSTMENT_ID,
          nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
        )

        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          sentencingEvent(
            eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
            auditModuleName = "OIDSENAD",
            adjustmentId = NOMIS_ADJUSTMENT_ID,
            bookingId = BOOKING_ID,
            sentenceSeq = SENTENCE_SEQUENCE,
            offenderIdDisplay = OFFENDER_NUMBER,
          ),
        )

        // wait for all mappings to be created before verifying
        await untilCallTo { mappingApi.createMappingCount(ADJUSTMENTS_CREATE_MAPPING_URL) } matches { it == 2 }

        // check that one sentence-adjustment is still created - despite it being a duplicate
        assertThat(sentencingApi.createSentenceAdjustmentForSynchronisationCount()).isEqualTo(1)

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-adjustment-duplicate"),
            check {
              assertThat(it["migrationId"]).isNull()
              assertThat(it["existingAdjustmentId"]).isEqualTo("10")
              assertThat(it["duplicateAdjustmentId"]).isEqualTo(ADJUSTMENT_ID)
              assertThat(it["existingNomisAdjustmentId"]).isEqualTo("$NOMIS_ADJUSTMENT_ID")
              assertThat(it["duplicateNomisAdjustmentId"]).isEqualTo("$NOMIS_ADJUSTMENT_ID")
              assertThat(it["existingNomisAdjustmentCategory"]).isEqualTo("SENTENCE")
              assertThat(it["duplicateNomisAdjustmentCategory"]).isEqualTo("SENTENCE")
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    @DisplayName("When mapping exists - existing adjustment")
    inner class WhenMappingFound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetNomisSentencingAdjustment(
          adjustmentCategory = "SENTENCE",
          nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
          adjustmentId = ADJUSTMENT_ID,
        )
      }

      @Nested
      inner class WhenUpdatedByDPS {
        @BeforeEach
        fun setUp() {
          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
              auditModuleName = "DPS_SYNCHRONISATION",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              sentenceSeq = SENTENCE_SEQUENCE,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("sentence-adjustment-synchronisation-skipped"),
              check {
                assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
                assertThat(it["adjustmentId"]).isNull()
              },
              isNull(),
            )
          }

          mappingApi.verify(
            exactly(0),
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
          )
          nomisApi.verify(exactly(0), getRequestedFor(urlPathEqualTo("/sentence-adjustments/$NOMIS_ADJUSTMENT_ID")))
          sentencingApi.verify(
            exactly(0),
            putRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")),
          )
        }
      }

      @Nested
      inner class WhenUpdatedByNomis {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetSentenceAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID, prisonId = "WWI")
          sentencingApi.stubUpdateSentencingAdjustmentForSynchronisation(ADJUSTMENT_ID)

          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
              auditModuleName = "OIDSENAD",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              sentenceSeq = SENTENCE_SEQUENCE,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `will retrieve mapping to check if this is an updated adjustment`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")))
          }
        }

        @Test
        fun `will retrieve details about the adjustment from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlPathEqualTo("/sentence-adjustments/$NOMIS_ADJUSTMENT_ID")))
          }
        }

        @Test
        fun `will update the adjustment in the sentencing service`() {
          await untilAsserted {
            sentencingApi.verify(
              putRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID"))
                .withRequestBody(matchingJsonPath("agencyId", equalTo("WWI"))),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the update`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("sentence-adjustment-updated-synchronisation-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("When adjustment is hidden, that is, it is related to a key date adjustment")
    inner class WhenSentenceAdjustmentInNOMISIsHidden {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetSentenceAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID, hiddenForUsers = true)

        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          sentencingEvent(
            eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
            auditModuleName = "OIDSENAD",
            adjustmentId = NOMIS_ADJUSTMENT_ID,
            bookingId = BOOKING_ID,
            sentenceSeq = SENTENCE_SEQUENCE,
            offenderIdDisplay = OFFENDER_NUMBER,
          ),
        )
      }

      @Test
      fun `will retrieve details about the adjustment from NOMIS`() {
        await untilAsserted {
          nomisApi.verify(getRequestedFor(urlPathEqualTo("/sentence-adjustments/$NOMIS_ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `the adjustment is not created or updated in the sentencing service but skipped`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("sentence-adjustment-hidden-or-deleted-synchronisation-skipped"),
            check {
              assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
              assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
              assertThat(it["adjustmentId"]).isNull()
            },
            isNull(),
          )
        }

        sentencingApi.verify(exactly(0), anyRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When adjustment is has already been deleted")
    inner class WhenSentenceAdjustmentInNOMISDeleted {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetSentenceAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID, status = NOT_FOUND)

        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          sentencingEvent(
            eventType = "SENTENCE_ADJUSTMENT_UPSERTED",
            auditModuleName = "OIDSENAD",
            adjustmentId = NOMIS_ADJUSTMENT_ID,
            bookingId = BOOKING_ID,
            sentenceSeq = SENTENCE_SEQUENCE,
            offenderIdDisplay = OFFENDER_NUMBER,
          ),
        )
      }

      @Test
      fun `will retrieve details about the adjustment from NOMIS`() {
        await untilAsserted {
          nomisApi.verify(getRequestedFor(urlPathEqualTo("/sentence-adjustments/$NOMIS_ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `the adjustment is not created or updated in the sentencing service but skipped`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("sentence-adjustment-hidden-or-deleted-synchronisation-skipped"),
            check {
              assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
              assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
              assertThat(it["adjustmentId"]).isNull()
            },
            isNull(),
          )
        }

        sentencingApi.verify(exactly(0), anyRequestedFor(anyUrl()))
      }
    }
  }

  @Nested
  @DisplayName("SENTENCE_ADJUSTMENT_DELETED")
  inner class SentenceAdjustmentDeleted {
    @Nested
    inner class WhenMightBeDeletedByDPS {
      @Nested
      @DisplayName("When definitely deleted by DPS")
      inner class WhenMappingAlreadyDeleted {
        @BeforeEach
        fun setUp() {
          mappingApi.stubAllMappingsNotFound(SENTENCE_ADJUSTMENTS_GET_MAPPING_URL)
          mappingApi.stubAllMappingsNotFound(KEYDATE_ADJUSTMENTS_GET_MAPPING_URL)
          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "SENTENCE_ADJUSTMENT_DELETED",
              auditModuleName = "DPS_SYNCHRONISATION",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              sentenceSeq = SENTENCE_SEQUENCE,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `the event is not ignored but the there is nothing to delete`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("sentence-adjustment-delete-synchronisation-ignored"),
              check {
                assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
                assertThat(it["adjustmentId"]).isNull()
              },
              isNull(),
            )
          }

          mappingApi.verify(
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
          )
          sentencingApi.verify(
            exactly(0),
            deleteRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")),
          )
          mappingApi.verify(
            exactly(0),
            deleteRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
          )
        }
      }

      @Nested
      @DisplayName("When definitely deleted by NOMIS")
      inner class WhenMappingNotYetDeleted {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetNomisSentencingAdjustment(
            adjustmentCategory = "SENTENCE",
            nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
            adjustmentId = ADJUSTMENT_ID,
          )
          sentencingApi.stubDeleteSentencingAdjustmentForSynchronisation(ADJUSTMENT_ID)
          mappingApi.stubSentenceAdjustmentMappingDelete(ADJUSTMENT_ID)
          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "SENTENCE_ADJUSTMENT_DELETED",
              auditModuleName = "DPS_SYNCHRONISATION",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              sentenceSeq = SENTENCE_SEQUENCE,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `the event is not ignored but will try to delete at same time as the TO NOMIS Sync service`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("sentence-adjustment-delete-synchronisation-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
          }

          mappingApi.verify(
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
          )
          sentencingApi.verify(
            deleteRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")),
          )
          await untilAsserted {
            mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID")))
          }
        }
      }

      @Nested
      @DisplayName("When deleted by DPS but mapping not deleted yet")
      inner class WhenAdjustmentHasBeenDeleted {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetNomisSentencingAdjustment(
            adjustmentCategory = "SENTENCE",
            nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
            adjustmentId = ADJUSTMENT_ID,
          )
          sentencingApi.stubDeleteSentencingAdjustmentForSynchronisationNotFound(ADJUSTMENT_ID)
          mappingApi.stubSentenceAdjustmentMappingDelete(ADJUSTMENT_ID)
          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "SENTENCE_ADJUSTMENT_DELETED",
              auditModuleName = "DPS_SYNCHRONISATION",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              sentenceSeq = SENTENCE_SEQUENCE,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `the event is not ignored but will try to delete at same time as the TO NOMIS Sync service`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("sentence-adjustment-delete-synchronisation-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
              },
              isNull(),
            )
          }

          mappingApi.verify(
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
          )
          sentencingApi.verify(
            deleteRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")),
          )
          await untilAsserted {
            mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID")))
          }
        }
      }
    }

    @Nested
    @DisplayName("When no mapping exists - adjustment either already deleted or never created")
    inner class WhenNoMappingFound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubAllMappingsNotFound(SENTENCE_ADJUSTMENTS_GET_MAPPING_URL)
        mappingApi.stubAllMappingsNotFound(KEYDATE_ADJUSTMENTS_GET_MAPPING_URL)
        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          sentencingEvent(
            eventType = "SENTENCE_ADJUSTMENT_DELETED",
            auditModuleName = "OIDSENAD",
            adjustmentId = NOMIS_ADJUSTMENT_ID,
            bookingId = BOOKING_ID,
            sentenceSeq = SENTENCE_SEQUENCE,
            offenderIdDisplay = OFFENDER_NUMBER,
          ),
        )
      }

      @Test
      fun `will check if mapping exists for the adjustment`() {
        await untilAsserted {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `will do nothing but track telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("sentence-adjustment-delete-synchronisation-ignored"),
            check {
              assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
              assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
              assertThat(it["adjustmentId"]).isNull()
            },
            isNull(),
          )
        }

        sentencingApi.verify(
          exactly(0),
          deleteRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")),
        )
        mappingApi.verify(
          exactly(0),
          deleteRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
        )
      }
    }

    @Nested
    @DisplayName("When mapping found - adjustment exists")
    inner class WhenMappingFound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetNomisSentencingAdjustment(
          adjustmentCategory = "SENTENCE",
          nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
          adjustmentId = ADJUSTMENT_ID,
        )
        sentencingApi.stubDeleteSentencingAdjustmentForSynchronisation(ADJUSTMENT_ID)
        mappingApi.stubSentenceAdjustmentMappingDelete(ADJUSTMENT_ID)
        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          sentencingEvent(
            eventType = "SENTENCE_ADJUSTMENT_DELETED",
            auditModuleName = "OIDSENAD",
            adjustmentId = NOMIS_ADJUSTMENT_ID,
            bookingId = BOOKING_ID,
            sentenceSeq = SENTENCE_SEQUENCE,
            offenderIdDisplay = OFFENDER_NUMBER,
          ),
        )
      }

      @Test
      fun `will check if mapping exists for the adjustment`() {
        await untilAsserted {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `will delete the adjustment`() {
        await untilAsserted {
          sentencingApi.verify(deleteRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `will delete the adjustment mapping`() {
        await untilAsserted {
          mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `will create telemetry tracking the delete`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("sentence-adjustment-delete-synchronisation-success"),
            check {
              assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
              assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
              assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("KEY_DATE_ADJUSTMENT_UPSERTED")
  inner class KeyDateAdjustmentUpserted {
    @Nested
    @DisplayName("When no mapping exists - new adjustment")
    inner class WhenNoMappingFound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubAllMappingsNotFound(SENTENCE_ADJUSTMENTS_GET_MAPPING_URL)
        mappingApi.stubAllMappingsNotFound(KEYDATE_ADJUSTMENTS_GET_MAPPING_URL)
      }

      @Nested
      inner class WhenCreateByDPS {
        @BeforeEach
        fun setUp() {
          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "KEY_DATE_ADJUSTMENT_UPSERTED",
              auditModuleName = "DPS_SYNCHRONISATION",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("key-date-adjustment-synchronisation-skipped"),
              check {
                assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["adjustmentId"]).isNull()
              },
              isNull(),
            )
          }

          mappingApi.verify(
            exactly(0),
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
          )
          nomisApi.verify(exactly(0), getRequestedFor(urlPathEqualTo("/sentence-adjustments/$NOMIS_ADJUSTMENT_ID")))
          sentencingApi.verify(exactly(0), postRequestedFor(urlPathEqualTo("/legacy/adjustments")))
        }
      }

      @Nested
      inner class WhenCreateByNomis {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetKeyDateAdjustment(
            adjustmentId = NOMIS_ADJUSTMENT_ID,
            prisonId = "WWI",
            bookingId = BOOKING_ID,
            offenderNo = OFFENDER_NUMBER,
            bookingSequence = 2,
          )
          sentencingApi.stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId = ADJUSTMENT_ID)
          mappingApi.stubMappingCreate(ADJUSTMENTS_CREATE_MAPPING_URL)

          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "KEY_DATE_ADJUSTMENT_UPSERTED",
              auditModuleName = "OIDSENAD",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `will retrieve mapping to check if this is a new adjustment`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")))
          }
        }

        @Test
        fun `will retrieve details about the adjustment from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlPathEqualTo("/key-date-adjustments/$NOMIS_ADJUSTMENT_ID")))
          }
        }

        @Test
        fun `will create the adjustment in the sentencing service`() {
          await untilAsserted {
            postRequestedFor(urlPathEqualTo("/legacy/adjustments"))
              .withRequestBody(matchingJsonPath("bookingId", equalTo(BOOKING_ID.toString())))
              .withRequestBody(matchingJsonPath("offenderNo", equalTo(OFFENDER_NUMBER)))
              .withRequestBody(matchingJsonPath("currentTerm", equalTo("false")))
              .withRequestBody(matchingJsonPath("sentenceSequence", WireMock.absent()))
              .withRequestBody(matchingJsonPath("agencyId", equalTo("WWI")))
          }
        }

        @Test
        fun `will create a mapping between the two records`() {
          await untilAsserted {
            mappingApi.verify(
              postRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments"))
                .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo(NOMIS_ADJUSTMENT_ID.toString())))
                .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("KEY-DATE")))
                .withRequestBody(matchingJsonPath("adjustmentId", equalTo(ADJUSTMENT_ID))),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the create`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("key-date-adjustment-created-synchronisation-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenThereAreFailures {
        @Nested
        inner class WhenMappingCreateFailsOnce {
          @BeforeEach
          fun setUp() {
            mappingApi.stubMappingCreateFailureFollowedBySuccess(ADJUSTMENTS_CREATE_MAPPING_URL)
            nomisApi.stubGetKeyDateAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID)
            sentencingApi.stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId = ADJUSTMENT_ID)

            awsSqsSentencingOffenderEventsClient.sendMessage(
              sentencingQueueOffenderEventsUrl,
              sentencingEvent(
                eventType = "KEY_DATE_ADJUSTMENT_UPSERTED",
                auditModuleName = "OIDSENAD",
                adjustmentId = NOMIS_ADJUSTMENT_ID,
                bookingId = BOOKING_ID,
                offenderIdDisplay = OFFENDER_NUMBER,
              ),
            )
          }

          @Test
          fun `will only create the adjustment once despite the failure`() {
            await untilAsserted {
              mappingApi.verify(
                exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments"))
                  .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo(NOMIS_ADJUSTMENT_ID.toString())))
                  .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("KEY-DATE")))
                  .withRequestBody(matchingJsonPath("adjustmentId", equalTo(ADJUSTMENT_ID))),
              )
            }

            sentencingApi.verify(
              exactly(1),
              postRequestedFor(urlPathEqualTo("/legacy/adjustments")),
            )
          }

          @Test
          fun `will eventually fully succeed with no messages on the dead letter queue`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                Mockito.eq("adjustment-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                  assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
                  assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                  assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                },
                isNull(),
              )
            }
            verify(telemetryClient).trackEvent(
              Mockito.eq("key-date-adjustment-created-synchronisation-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              },
              isNull(),
            )
            await untilCallTo {
              awsSqsSentencingOffenderEventsDlqClient?.countAllMessagesOnQueue(sentencingQueueOffenderEventsDlqUrl!!)
                ?.get()
            } matches { it == 0 }
          }
        }

        @Nested
        inner class WhenMappingCreateFailsForever {
          @BeforeEach
          fun setUp() {
            mappingApi.stubSentenceAdjustmentMappingCreateFailure()
            nomisApi.stubGetKeyDateAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID)
            sentencingApi.stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId = ADJUSTMENT_ID)

            awsSqsSentencingOffenderEventsClient.sendMessage(
              sentencingQueueOffenderEventsUrl,
              sentencingEvent(
                eventType = "KEY_DATE_ADJUSTMENT_UPSERTED",
                auditModuleName = "OIDSENAD",
                adjustmentId = NOMIS_ADJUSTMENT_ID,
                bookingId = BOOKING_ID,
                offenderIdDisplay = OFFENDER_NUMBER,
              ),
            )
          }

          @Test
          fun `will only create the adjustment once despite constant failures failure`() {
            await untilAsserted {
              mappingApi.verify(
                // Once and then twice via RETRY_SYNCHRONISATION_SENTENCING_ADJUSTMENT_MAPPING message
                exactly(3),
                postRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments"))
                  .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo(NOMIS_ADJUSTMENT_ID.toString())))
                  .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("KEY-DATE")))
                  .withRequestBody(matchingJsonPath("adjustmentId", equalTo(ADJUSTMENT_ID))),
              )
            }

            sentencingApi.verify(
              exactly(1),
              postRequestedFor(urlPathEqualTo("/legacy/adjustments")),
            )
          }

          @Test
          fun `will eventually partially fail with a message on the dead letter queue`() {
            await untilCallTo {
              awsSqsVisitsMigrationDlqClient?.countAllMessagesOnQueue(sentencingQueueOffenderEventsDlqUrl!!)
                ?.get()
            } matches { it == 1 }

            verify(telemetryClient).trackEvent(
              Mockito.eq("key-date-adjustment-created-synchronisation-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["mapping"]).isEqualTo("initial-failure")
              },
              isNull(),
            )

            verify(telemetryClient, never()).trackEvent(
              Mockito.eq("adjustment-mapping-created-synchronisation-success"),
              any(),
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("When mapping exists - existing adjustment")
    inner class WhenMappingFound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetNomisSentencingAdjustment(
          adjustmentCategory = "KEY-DATE",
          nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
          adjustmentId = ADJUSTMENT_ID,
        )
      }

      @Nested
      inner class WhenUpdatedByDPS {
        @BeforeEach
        fun setUp() {
          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "KEY_DATE_ADJUSTMENT_UPSERTED",
              auditModuleName = "DPS_SYNCHRONISATION",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `the event is ignored`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("key-date-adjustment-synchronisation-skipped"),
              check {
                assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["adjustmentId"]).isNull()
              },
              isNull(),
            )
          }

          mappingApi.verify(
            exactly(0),
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
          )
          nomisApi.verify(exactly(0), getRequestedFor(urlPathEqualTo("/key-date-adjustments/$NOMIS_ADJUSTMENT_ID")))
          sentencingApi.verify(
            exactly(0),
            putRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")),
          )
        }
      }

      @Nested
      inner class WhenUpdatedByNomis {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetKeyDateAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID, prisonId = "WWI")
          sentencingApi.stubUpdateSentencingAdjustmentForSynchronisation(ADJUSTMENT_ID)

          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "KEY_DATE_ADJUSTMENT_UPSERTED",
              auditModuleName = "OIDSENAD",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `will retrieve mapping to check if this is an updated adjustment`() {
          await untilAsserted {
            mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")))
          }
        }

        @Test
        fun `will retrieve details about the adjustment from NOMIS`() {
          await untilAsserted {
            nomisApi.verify(getRequestedFor(urlPathEqualTo("/key-date-adjustments/$NOMIS_ADJUSTMENT_ID")))
          }
        }

        @Test
        fun `will update the adjustment in the sentencing service`() {
          await untilAsserted {
            sentencingApi.verify(
              putRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID"))
                .withRequestBody(matchingJsonPath("agencyId", equalTo("WWI"))),
            )
          }
        }

        @Test
        fun `will create telemetry tracking the update`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("key-date-adjustment-updated-synchronisation-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("When mapping has duplicate - adjustment already created by another message")
    inner class WhenDuplicate {

      @Test
      internal fun `it will not retry after a 409 (duplicate adjustment written to Sentencing API)`() {
        nomisApi.stubGetKeyDateAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID)
        sentencingApi.stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId = ADJUSTMENT_ID)
        mappingApi.stubSentenceAdjustmentMappingCreateConflict(
          duplicateAdjustmentId = ADJUSTMENT_ID,
          nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
          nomisAdjustmentCategory = "KEY-DATE",
        )

        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          sentencingEvent(
            eventType = "KEY_DATE_ADJUSTMENT_UPSERTED",
            auditModuleName = "OIDSENAD",
            adjustmentId = NOMIS_ADJUSTMENT_ID,
            bookingId = BOOKING_ID,
            offenderIdDisplay = OFFENDER_NUMBER,
          ),
        )

        // wait for all mappings to be created before verifying
        await untilCallTo { mappingApi.createMappingCount(ADJUSTMENTS_CREATE_MAPPING_URL) } matches { it == 1 }

        // check that one sentence-adjustment is still created - despite it being a duplicate
        assertThat(sentencingApi.createSentenceAdjustmentForSynchronisationCount()).isEqualTo(1)

        // doesn't retry
        mappingApi.verifyCreateMappingSentenceAdjustmentIds(arrayOf(ADJUSTMENT_ID), times = 1)

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("from-nomis-synch-adjustment-duplicate"),
            check {
              assertThat(it["migrationId"]).isNull()
              assertThat(it["existingAdjustmentId"]).isEqualTo("10")
              assertThat(it["duplicateAdjustmentId"]).isEqualTo(ADJUSTMENT_ID)
              assertThat(it["existingNomisAdjustmentId"]).isEqualTo("$NOMIS_ADJUSTMENT_ID")
              assertThat(it["duplicateNomisAdjustmentId"]).isEqualTo("$NOMIS_ADJUSTMENT_ID")
              assertThat(it["existingNomisAdjustmentCategory"]).isEqualTo("KEY-DATE")
              assertThat(it["duplicateNomisAdjustmentCategory"]).isEqualTo("KEY-DATE")
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    @DisplayName("When adjustment is has already been deleted")
    inner class WheKeyDateAdjustmentInNOMISDeleted {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetKeyDateAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID, status = NOT_FOUND)

        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          sentencingEvent(
            eventType = "KEY_DATE_ADJUSTMENT_UPSERTED",
            auditModuleName = "OIDSENAD",
            adjustmentId = NOMIS_ADJUSTMENT_ID,
            bookingId = BOOKING_ID,
            offenderIdDisplay = OFFENDER_NUMBER,
          ),
        )
      }

      @Test
      fun `will retrieve details about the adjustment from NOMIS`() {
        await untilAsserted {
          nomisApi.verify(getRequestedFor(urlPathEqualTo("/key-date-adjustments/$NOMIS_ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `the adjustment is not created or updated in the sentencing service but skipped`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("key-date-adjustment-deleted-synchronisation-skipped"),
            check {
              assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
              assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["adjustmentId"]).isNull()
            },
            isNull(),
          )
        }

        sentencingApi.verify(exactly(0), anyRequestedFor(anyUrl()))
      }
    }
  }

  @Nested
  @DisplayName("KEY_DATE_ADJUSTMENT_DELETED")
  inner class KeyDateAdjustmentDeleted {
    @Nested
    inner class WhenMightBeDeletedByDPS {
      @Nested
      @DisplayName("When definitely deleted by DPS")
      inner class WhenMappingAlreadyDeleted {
        @BeforeEach
        fun setUp() {
          mappingApi.stubAllMappingsNotFound(SENTENCE_ADJUSTMENTS_GET_MAPPING_URL)
          mappingApi.stubAllMappingsNotFound(KEYDATE_ADJUSTMENTS_GET_MAPPING_URL)
          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "KEY_DATE_ADJUSTMENT_DELETED",
              auditModuleName = "DPS_SYNCHRONISATION",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `the event is not ignored but the there is nothing to delete`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("key-date-adjustment-delete-synchronisation-ignored"),
              check {
                assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["adjustmentId"]).isNull()
              },
              isNull(),
            )
          }

          mappingApi.verify(
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
          )
          sentencingApi.verify(
            exactly(0),
            deleteRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")),
          )
          mappingApi.verify(
            exactly(0),
            deleteRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
          )
        }
      }

      @Nested
      @DisplayName("When definitely deleted by NOMIS")
      inner class WhenMappingNotYetDeleted {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetNomisSentencingAdjustment(
            adjustmentCategory = "KEY-DATE",
            nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
            adjustmentId = ADJUSTMENT_ID,
          )
          sentencingApi.stubDeleteSentencingAdjustmentForSynchronisation(ADJUSTMENT_ID)
          mappingApi.stubSentenceAdjustmentMappingDelete(ADJUSTMENT_ID)
          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "KEY_DATE_ADJUSTMENT_DELETED",
              auditModuleName = "DPS_SYNCHRONISATION",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `the event is not ignored but will try to delete at same time as the TO NOMIS Sync service`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("key-date-adjustment-delete-synchronisation-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              },
              isNull(),
            )
          }

          mappingApi.verify(
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
          )
          sentencingApi.verify(
            deleteRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")),
          )
          mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID")))
        }
      }

      @Nested
      @DisplayName("When deleted by DPS but mapping not deleted yet")
      inner class WhenAdjustmentHasBeenDeleted {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetNomisSentencingAdjustment(
            adjustmentCategory = "KEY-DATE",
            nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
            adjustmentId = ADJUSTMENT_ID,
          )
          sentencingApi.stubDeleteSentencingAdjustmentForSynchronisationNotFound(ADJUSTMENT_ID)
          mappingApi.stubSentenceAdjustmentMappingDelete(ADJUSTMENT_ID)
          awsSqsSentencingOffenderEventsClient.sendMessage(
            sentencingQueueOffenderEventsUrl,
            sentencingEvent(
              eventType = "KEY_DATE_ADJUSTMENT_DELETED",
              auditModuleName = "DPS_SYNCHRONISATION",
              adjustmentId = NOMIS_ADJUSTMENT_ID,
              bookingId = BOOKING_ID,
              offenderIdDisplay = OFFENDER_NUMBER,
            ),
          )
        }

        @Test
        fun `the event is not ignored but will try to delete at same time as the TO NOMIS Sync service`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              Mockito.eq("key-date-adjustment-delete-synchronisation-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
                assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              },
              isNull(),
            )
          }

          mappingApi.verify(
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
          )
          sentencingApi.verify(
            deleteRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")),
          )
          mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID")))
        }
      }
    }

    @Nested
    @DisplayName("When no mapping exists - adjustment either already deleted or never created")
    inner class WhenNoMappingFound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubAllMappingsNotFound(SENTENCE_ADJUSTMENTS_GET_MAPPING_URL)
        mappingApi.stubAllMappingsNotFound(KEYDATE_ADJUSTMENTS_GET_MAPPING_URL)
        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          sentencingEvent(
            eventType = "KEY_DATE_ADJUSTMENT_DELETED",
            auditModuleName = "OIDSENAD",
            adjustmentId = NOMIS_ADJUSTMENT_ID,
            bookingId = BOOKING_ID,
            offenderIdDisplay = OFFENDER_NUMBER,
          ),
        )
      }

      @Test
      fun `will check if mapping exists for the adjustment`() {
        await untilAsserted {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `will do nothing but track telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("key-date-adjustment-delete-synchronisation-ignored"),
            check {
              assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
              assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["adjustmentId"]).isNull()
            },
            isNull(),
          )
        }

        sentencingApi.verify(
          exactly(0),
          deleteRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")),
        )
        mappingApi.verify(
          exactly(0),
          deleteRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")),
        )
      }
    }

    @Nested
    @DisplayName("When mapping found - adjustment exists")
    inner class WhenMappingFound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetNomisSentencingAdjustment(
          adjustmentCategory = "KEY-DATE",
          nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
          adjustmentId = ADJUSTMENT_ID,
        )
        sentencingApi.stubDeleteSentencingAdjustmentForSynchronisation(ADJUSTMENT_ID)
        mappingApi.stubSentenceAdjustmentMappingDelete(ADJUSTMENT_ID)
        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          sentencingEvent(
            eventType = "KEY_DATE_ADJUSTMENT_DELETED",
            auditModuleName = "OIDSENAD",
            adjustmentId = NOMIS_ADJUSTMENT_ID,
            bookingId = BOOKING_ID,
            offenderIdDisplay = OFFENDER_NUMBER,
          ),
        )
      }

      @Test
      fun `will check if mapping exists for the adjustment`() {
        await untilAsserted {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `will delete the adjustment`() {
        await untilAsserted {
          sentencingApi.verify(deleteRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `will delete the adjustment mapping`() {
        await untilAsserted {
          mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `will create telemetry tracking the delete`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("key-date-adjustment-delete-synchronisation-success"),
            check {
              assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
              assertThat(it["adjustmentCategory"]).isEqualTo("KEY-DATE")
              assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("BOOKING_NUMBER-CHANGED")
  inner class PrisonerMerge {
    @Nested
    @DisplayName("When no adjustments exist at all in NOMIS")
    inner class NoAdjustmentsInNomis {
      val bookingId = BOOKING_ID

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetAllByBookingId(
          bookingId = bookingId,
          sentenceAdjustments = emptyList(),
          keyDateAdjustments = emptyList(),
        )
        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          mergeNomisEvent(
            bookingId = bookingId,
          ),
        )
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will do nothing more than log no adjustments found`() {
        verify(telemetryClient).trackEvent(
          Mockito.eq("from-nomis-synch-adjustment-merge"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["sentenceAdjustments"]).isEqualTo("0")
            assertThat(it["keyDateAdjustments"]).isEqualTo("0")
            assertThat(it["sentenceAdjustmentsCreated"]).isEqualTo("0")
            assertThat(it["keyDateAdjustmentsCreated"]).isEqualTo("0")
          },
          isNull(),
        )

        mappingApi.verify(0, getRequestedFor(urlPathMatching("/mapping/sentencing/adjustments/.*")))
      }
    }

    @Nested
    @DisplayName("When all adjustments exist in NOMIS and in DPS")
    inner class SameAdjustmentsInNomisAndDps {
      val bookingId = BOOKING_ID

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetAllByBookingId(
          bookingId = bookingId,
          sentenceAdjustments = listOf(
            sentenceAdjustmentResponse(bookingId = bookingId, sentenceAdjustmentId = 1001),
            sentenceAdjustmentResponse(bookingId = bookingId, sentenceAdjustmentId = 1002),
          ),
          keyDateAdjustments = listOf(
            keyDateAdjustmentResponse(bookingId = bookingId, keyDateAdjustmentId = 2001),
            keyDateAdjustmentResponse(bookingId = bookingId, keyDateAdjustmentId = 2002),
          ),
        )

        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "SENTENCE", nomisAdjustmentId = 1001)
        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "SENTENCE", nomisAdjustmentId = 1002)
        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "KEY-DATE", nomisAdjustmentId = 2001)
        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "KEY-DATE", nomisAdjustmentId = 2002)

        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          mergeNomisEvent(
            bookingId = bookingId,
          ),
        )

        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will log the number of adjustments found`() {
        verify(telemetryClient).trackEvent(
          Mockito.eq("from-nomis-synch-adjustment-merge"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["sentenceAdjustments"]).isEqualTo("2")
            assertThat(it["keyDateAdjustments"]).isEqualTo("2")
            assertThat(it["sentenceAdjustmentsCreated"]).isEqualTo("0")
            assertThat(it["keyDateAdjustmentsCreated"]).isEqualTo("0")
          },
          isNull(),
        )
      }

      @Test
      fun `will retrieve the mappings for each adjustment`() {
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/1001")),
        )
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/1001")),
        )
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/2001")),
        )
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/2002")),
        )
      }

      @Test
      fun `will not update or create any adjustments in DPS`() {
        sentencingApi.verify(0, postRequestedFor(urlPathEqualTo("/legacy/adjustments")))
      }
    }

    @Nested
    @DisplayName("When more adjustments exist in NOMIS than in DPS")
    inner class MoreAdjustmentsInNomisThenDps {
      val bookingId = BOOKING_ID

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetAllByBookingId(
          bookingId = bookingId,
          sentenceAdjustments = listOf(
            sentenceAdjustmentResponse(bookingId = bookingId, sentenceAdjustmentId = 1001),
            sentenceAdjustmentResponse(bookingId = bookingId, sentenceAdjustmentId = 1002),
          ),
          keyDateAdjustments = listOf(
            keyDateAdjustmentResponse(bookingId = bookingId, keyDateAdjustmentId = 2001),
            keyDateAdjustmentResponse(bookingId = bookingId, keyDateAdjustmentId = 2002),
          ),
        )

        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "SENTENCE", nomisAdjustmentId = 1001)
        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "SENTENCE", nomisAdjustmentId = 1002, status = NOT_FOUND)
        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "KEY-DATE", nomisAdjustmentId = 2001)
        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "KEY-DATE", nomisAdjustmentId = 2002, status = NOT_FOUND)

        nomisApi.stubGetSentenceAdjustment(1002)
        nomisApi.stubGetKeyDateAdjustment(2002)

        sentencingApi.stubCreateSentencingAdjustmentForSynchronisation()
        sentencingApi.stubCreateSentencingAdjustmentForSynchronisation()

        mappingApi.stubMappingCreate(ADJUSTMENTS_CREATE_MAPPING_URL)
        mappingApi.stubMappingCreate(ADJUSTMENTS_CREATE_MAPPING_URL)

        awsSqsSentencingOffenderEventsClient.sendMessage(
          sentencingQueueOffenderEventsUrl,
          mergeNomisEvent(
            bookingId = bookingId,
          ),
        ).also { waitForAnyProcessingToComplete("from-nomis-synch-adjustment-merge") }
      }

      @Test
      fun `will log the number of adjustments found`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("from-nomis-synch-adjustment-merge"),
            check {
              assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
              assertThat(it["sentenceAdjustments"]).isEqualTo("2")
              assertThat(it["keyDateAdjustments"]).isEqualTo("2")
              assertThat(it["sentenceAdjustmentsCreated"]).isEqualTo("1")
              assertThat(it["keyDateAdjustmentsCreated"]).isEqualTo("1")
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will retrieve the mappings for each adjustment`() {
        await untilAsserted {
          mappingApi.verify(
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/1001")),
          )
          mappingApi.verify(
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/1001")),
          )
          mappingApi.verify(
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/2001")),
          )
          mappingApi.verify(
            getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/2002")),
          )
        }
      }

      @Test
      fun `will retrieve details of the 2 missing adjustments`() {
        await untilAsserted {
          nomisApi.verify(getRequestedFor(urlPathEqualTo("/sentence-adjustments/1002")))
          nomisApi.verify(getRequestedFor(urlPathEqualTo("/key-date-adjustments/2002")))
        }
      }

      @Test
      fun `will create the two missing adjustments in DPS`() {
        await untilAsserted {
          sentencingApi.verify(2, postRequestedFor(urlPathEqualTo("/legacy/adjustments")))
        }
      }

      @Test
      fun `will create the two missing adjustments mappings`() {
        await untilAsserted {
          mappingApi.verify(2, postRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments")))
        }
      }
    }
  }

  @Nested
  @DisplayName("courtsentencing.resync.sentence-adjustments")
  inner class CourtSentencingSentenceAdjustmentSynchronisation {
    @Nested
    inner class WhenSingleAdjustmentInSingleSentence {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetNomisSentencingAdjustment(
          adjustmentCategory = "SENTENCE",
          nomisAdjustmentId = NOMIS_ADJUSTMENT_ID,
          adjustmentId = ADJUSTMENT_ID,
        )
        nomisApi.stubGetSentenceAdjustment(adjustmentId = NOMIS_ADJUSTMENT_ID, prisonId = "WWI")
        sentencingApi.stubUpdateSentencingAdjustmentForSynchronisation(ADJUSTMENT_ID)

        courtSentencingOffenderEventsQueue.sendMessage(
          SQSMessage(
            Type = "courtsentencing.resync.sentence-adjustments",
            Message = SyncSentenceAdjustment(
              offenderNo = OFFENDER_NUMBER,
              sentences = listOf(
                SentenceIdAndAdjustmentType(
                  sentenceId = SentenceId(
                    offenderBookingId = BOOKING_ID,
                    sentenceSequence = SENTENCE_SEQUENCE,
                  ),
                  adjustmentIds = listOf(NOMIS_ADJUSTMENT_ID),
                ),
              ),
            ).toJson(),
          ).toJson(),
        )
      }

      @Test
      fun `will retrieve mapping to check if this is an updated adjustment`() {
        await untilAsserted {
          mappingApi.verify(getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/$NOMIS_ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `will retrieve details about the adjustment from NOMIS`() {
        await untilAsserted {
          nomisApi.verify(getRequestedFor(urlPathEqualTo("/sentence-adjustments/$NOMIS_ADJUSTMENT_ID")))
        }
      }

      @Test
      fun `will update the adjustment in the sentencing service`() {
        await untilAsserted {
          sentencingApi.verify(
            putRequestedFor(urlPathEqualTo("/legacy/adjustments/$ADJUSTMENT_ID"))
              .withRequestBody(matchingJsonPath("agencyId", equalTo("WWI"))),
          )
        }
      }

      @Test
      fun `will create telemetry tracking the update`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("sentence-adjustment-updated-synchronisation-success"),
            check {
              assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
              assertThat(it["adjustmentCategory"]).isEqualTo("SENTENCE")
              assertThat(it["nomisAdjustmentId"]).isEqualTo(NOMIS_ADJUSTMENT_ID.toString())
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["sentenceSequence"]).isEqualTo(SENTENCE_SEQUENCE.toString())
            },
            isNull(),
          )
        }
      }
    }
  }
}

fun sentencingEvent(
  eventType: String,
  offenderIdDisplay: String = OFFENDER_NUMBER,
  bookingId: Long = BOOKING_ID,
  sentenceSeq: Long? = null,
  adjustmentId: Long = NOMIS_ADJUSTMENT_ID,
  auditModuleName: String = "OIDSENAD",
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventId\":\"5958295\",\"eventType\":\"$eventType\",\"eventDatetime\":\"2019-10-21T15:00:25.489964\",\"offenderIdDisplay\":\"$offenderIdDisplay\",\"bookingId\": \"$bookingId\",${sentenceSeq.asJson()}\"nomisEventType\":\"WHATEVER\",\"adjustmentId\":\"$adjustmentId\",\"auditModuleName\":\"$auditModuleName\" }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()

private fun Long?.asJson() = if (this == null) "" else """ \"sentenceSeq\":\"$this\", """

internal fun sentenceAdjustmentResponse(
  bookingId: Long = 2,
  offenderNo: String = "G4803UT",
  sentenceAdjustmentId: Long = 3,
  hiddenForUsers: Boolean = false,
  prisonId: String = "MDI",
): SentenceAdjustmentResponse = SentenceAdjustmentResponse(
  bookingId = bookingId,
  id = sentenceAdjustmentId,
  offenderNo = offenderNo,
  sentenceSequence = 0,
  comment = "a comment",
  adjustmentDate = LocalDate.parse("2021-10-06"),
  adjustmentFromDate = LocalDate.parse("2021-10-07"),
  active = true,
  hiddenFromUsers = hiddenForUsers,
  adjustmentDays = 8,
  adjustmentType = SentencingAdjustmentType(
    code = "RST",
    description = "RST Desc",
  ),
  hasBeenReleased = false,
  prisonId = prisonId,
  bookingSequence = 1,
)

internal fun keyDateAdjustmentResponse(
  bookingId: Long = 2,
  keyDateAdjustmentId: Long = 3,
  offenderNo: String = "G4803UT",
  prisonId: String = "MDI",
): KeyDateAdjustmentResponse = KeyDateAdjustmentResponse(
  bookingId = bookingId,
  id = keyDateAdjustmentId,
  offenderNo = offenderNo,
  comment = "a comment",
  adjustmentDate = LocalDate.parse("2021-10-06"),
  adjustmentFromDate = LocalDate.parse("2021-10-07"),
  active = true,
  adjustmentDays = 8,
  adjustmentType = SentencingAdjustmentType(
    code = "ADA",
    description = "Additional days",
  ),
  hasBeenReleased = false,
  prisonId = prisonId,
  bookingSequence = 1,
)
