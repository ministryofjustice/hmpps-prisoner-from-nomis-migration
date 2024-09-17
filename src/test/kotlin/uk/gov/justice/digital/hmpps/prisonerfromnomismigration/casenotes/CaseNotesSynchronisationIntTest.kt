package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

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
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiExtension.Companion.caseNotesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiMockServer.Companion.dpsCaseNote
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.AbstractMap.SimpleEntry
import java.util.UUID

private const val BOOKING_ID = 1234L
private const val NOMIS_CASE_NOTE_ID = 2345678L
private const val OFFENDER_ID_DISPLAY = "A3864DZ"
private const val DPS_CASE_NOTE_ID = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

class CaseNotesSynchronisationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var caseNotesNomisApiMockServer: CaseNotesNomisApiMockServer

  @Autowired
  private lateinit var caseNotesMappingApiMockServer: CaseNotesMappingApiMockServer

  @Nested
  @DisplayName("OFFENDER_CASE_NOTES-INSERTED")
  inner class CaseNoteInserted {

    @Nested
    @DisplayName("When caseNote was created in DPS")
    inner class DPSCreated {

      @BeforeEach
      fun setUp() {
        caseNotesNomisApiMockServer.stubGetCaseNote(
          bookingId = BOOKING_ID,
          caseNoteId = NOMIS_CASE_NOTE_ID,
          auditModuleName = "DPS_SYNCHRONISATION",
        )
        awsSqsCaseNoteOffenderEventsClient.sendMessage(
          caseNotesQueueOffenderEventsUrl,
          caseNoteEvent(
            eventType = "OFFENDER_CASE_NOTES-INSERTED",
            bookingId = BOOKING_ID,
            caseNoteId = NOMIS_CASE_NOTE_ID,
            offenderNo = OFFENDER_ID_DISPLAY,
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("casenotes-synchronisation-created-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
              assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
            },
            isNull(),
          )
        }

        // will not bother getting mapping
        caseNotesMappingApiMockServer.verify(
          count = 0,
          getRequestedFor(urlPathMatching("/mapping/casenotes/nomis-booking-id/\\d+/nomis-casenote-sequence/\\d+")),
        )
        // will not create an caseNote in DPS
        caseNotesApi.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("When caseNote was created in NOMIS")
    inner class NomisCreated {
      @BeforeEach
      fun setUp() {
        caseNotesNomisApiMockServer.stubGetCaseNote(
          bookingId = BOOKING_ID,
          caseNoteId = NOMIS_CASE_NOTE_ID,
          caseNote = caseNote(bookingId = BOOKING_ID, caseNoteId = NOMIS_CASE_NOTE_ID).copy(
            caseNoteType = CodeDescription("XNR", "Not For Release"),
            caseNoteSubType = CodeDescription("X", "Security"),
          ),
        )
      }

      @Nested
      @DisplayName("Happy path")
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          // caseNotesMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          caseNotesApi.stubPostCaseNote(dpsCaseNote().copy(id = UUID.fromString(DPS_CASE_NOTE_ID)))
          caseNotesMappingApiMockServer.stubPostMapping()

          awsSqsCaseNoteOffenderEventsClient.sendMessage(
            caseNotesQueueOffenderEventsUrl,
            caseNoteEvent(
              eventType = "OFFENDER_CASE_NOTES-INSERTED",
              bookingId = BOOKING_ID,
              caseNoteId = NOMIS_CASE_NOTE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
        }

        @Test
        fun `will create caseNote in DPS`() {
          await untilAsserted {
            caseNotesApi.verify(
              1,
              postRequestedFor(urlPathEqualTo("/sync/case-notes"))
                .withRequestBody(matchingJsonPath("legacyId", equalTo(NOMIS_CASE_NOTE_ID.toString())))
                .withRequestBody(matchingJsonPath("personIdentifier", equalTo(OFFENDER_ID_DISPLAY)))
                .withRequestBody(matchingJsonPath("locationId", equalTo("SWI")))
                .withRequestBody(matchingJsonPath("type", equalTo("XNR")))
                .withRequestBody(matchingJsonPath("subType", equalTo("X")))
                .withRequestBody(matchingJsonPath("text", equalTo("the actual casenote")))
                .withRequestBody(matchingJsonPath("systemGenerated", equalTo("false")))
                .withRequestBody(matchingJsonPath("createdDateTime", equalTo("2021-02-03T04:05:06")))
                .withRequestBody(matchingJsonPath("createdByUsername", equalTo("TBC"))) // TODO
                .withRequestBody(matchingJsonPath("source", equalTo("NOMIS")))
                .withRequestBody(matchingJsonPath("authorUsername", equalTo("me")))
                .withRequestBody(matchingJsonPath("authorUserId", equalTo("123456")))
                .withRequestBody(matchingJsonPath("authorName", equalTo("me too")))
                .withRequestBody(matchingJsonPath("occurrenceDateTime", equalTo("2021-02-03T04:05:06"))),
            )
          }
        }

        @Test
        fun `will create mapping between DPS and NOMIS ids`() {
          await untilAsserted {
            caseNotesMappingApiMockServer.verify(
              postRequestedFor(urlPathEqualTo("/mapping/casenotes"))
                .withRequestBody(matchingJsonPath("dpsCaseNoteId", equalTo(DPS_CASE_NOTE_ID)))
                .withRequestBody(matchingJsonPath("nomisBookingId", equalTo(BOOKING_ID.toString())))
                .withRequestBody(matchingJsonPath("offenderNo", equalTo(OFFENDER_ID_DISPLAY)))
                .withRequestBody(matchingJsonPath("nomisCaseNoteId", equalTo(NOMIS_CASE_NOTE_ID.toString())))
                .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("casenotes-synchronisation-created-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
                assertThat(it["dpsCaseNoteId"]).isEqualTo(DPS_CASE_NOTE_ID)
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
          caseNotesMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          caseNotesApi.stubPostCaseNote(
            dpsCaseNote().copy(id = UUID.fromString(DPS_CASE_NOTE_ID)),
          )
        }

        @Nested
        @DisplayName("Fails once")
        inner class FailsOnce {
          @BeforeEach
          fun setUp() {
            caseNotesMappingApiMockServer.stubPostMappingFailureFollowedBySuccess()

            awsSqsCaseNoteOffenderEventsClient.sendMessage(
              caseNotesQueueOffenderEventsUrl,
              caseNoteEvent(
                eventType = "OFFENDER_CASE_NOTES-INSERTED",
                bookingId = BOOKING_ID,
                caseNoteId = NOMIS_CASE_NOTE_ID,
                offenderNo = OFFENDER_ID_DISPLAY,
              ),
            )
          }

          @Test
          fun `will create caseNote in DPS`() {
            await untilAsserted {
              caseNotesApi.verify(
                postRequestedFor(urlPathEqualTo("/sync/case-notes")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping two times and succeed`() {
            await untilAsserted {
              caseNotesMappingApiMockServer.verify(
                exactly(2),
                postRequestedFor(urlPathEqualTo("/mapping/casenotes"))
                  .withRequestBody(matchingJsonPath("dpsCaseNoteId", equalTo(DPS_CASE_NOTE_ID)))
                  .withRequestBody(matchingJsonPath("nomisBookingId", equalTo(BOOKING_ID.toString())))
                  .withRequestBody(matchingJsonPath("nomisCaseNoteId", equalTo(NOMIS_CASE_NOTE_ID.toString())))
                  .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
              )
            }

            assertThat(
              awsSqsCaseNotesOffenderEventDlqClient.countAllMessagesOnQueue(caseNotesQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(0)
          }

          @Test
          fun `will track a telemetry event for partial success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("casenotes-synchronisation-created-success"),
                check {
                  assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                  assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
                  assertThat(it["dpsCaseNoteId"]).isEqualTo(DPS_CASE_NOTE_ID)
                  assertThat(it["mapping"]).isEqualTo("initial-failure")
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                },
                isNull(),
              )
            }

            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("casenotes-mapping-created-synchronisation-success"),
                check {
                  assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                  assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
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
            caseNotesMappingApiMockServer.stubPostMapping(status = INTERNAL_SERVER_ERROR)
            awsSqsCaseNoteOffenderEventsClient.sendMessage(
              caseNotesQueueOffenderEventsUrl,
              caseNoteEvent(
                eventType = "OFFENDER_CASE_NOTES-INSERTED",
                bookingId = BOOKING_ID,
                caseNoteId = NOMIS_CASE_NOTE_ID,
                offenderNo = OFFENDER_ID_DISPLAY,
              ),
            )
            await untilCallTo {
              awsSqsCaseNotesOffenderEventDlqClient.countAllMessagesOnQueue(caseNotesQueueOffenderEventsDlqUrl).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create caseNote in DPS`() {
            await untilAsserted {
              caseNotesApi.verify(
                1,
                postRequestedFor(urlPathEqualTo("/sync/case-notes")),
              )
            }
          }

          @Test
          fun `will attempt to create mapping several times and keep failing`() {
            caseNotesMappingApiMockServer.verify(
              exactly(3),
              postRequestedFor(urlPathEqualTo("/mapping/casenotes")),
            )
          }

          @Test
          fun `will track a telemetry event for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("casenotes-synchronisation-created-success"),
                check {
                  assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                  assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                  assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
                  assertThat(it["dpsCaseNoteId"]).isEqualTo(DPS_CASE_NOTE_ID)
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
  @DisplayName("OFFENDER_CASE_NOTES-UPDATED")
  inner class CaseNoteUpdated {
    @Nested
    @DisplayName("When caseNote was updated in DPS")
    inner class DPSUpdated {
      private val bookingId = 12345L
      private val caseNoteId = NOMIS_CASE_NOTE_ID

      @BeforeEach
      fun setUp() {
        caseNotesNomisApiMockServer.stubGetCaseNote(
          bookingId = bookingId,
          caseNoteId = caseNoteId,
          auditModuleName = "DPS_SYNCHRONISATION",
        )
        awsSqsCaseNoteOffenderEventsClient.sendMessage(
          caseNotesQueueOffenderEventsUrl,
          caseNoteEvent(
            eventType = "OFFENDER_CASE_NOTES-UPDATED",
            bookingId = bookingId,
            caseNoteId = caseNoteId,
            offenderNo = OFFENDER_ID_DISPLAY,
          ),
        )
      }

      @Test
      fun `the event is ignored`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("casenotes-synchronisation-updated-skipped"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
              assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
              assertThat(it["nomisCaseNoteId"]).isEqualTo(caseNoteId.toString())
            },
            isNull(),
          )
        }

        // will not bother getting mapping
        caseNotesMappingApiMockServer.verify(
          count = 0,
          getRequestedFor(urlPathMatching("/mapping/casenotes/nomis-booking-id/\\d+/nomis-casenote-sequence/\\d+")),
        )
        // will not update the caseNote in DPS
        caseNotesApi.verify(
          0,
          putRequestedFor(anyUrl()),
        )
      }
    }

    @Nested
    @DisplayName("When caseNote was updated in NOMIS")
    inner class NomisUpdated {
      lateinit var nomisCaseNote: CaseNoteResponse

      @BeforeEach
      fun setUp() {
        nomisCaseNote = caseNote(bookingId = BOOKING_ID, caseNoteId = NOMIS_CASE_NOTE_ID).copy(
          caseNoteType = CodeDescription("XNR", "Not For Release"),
          caseNoteSubType = CodeDescription("X", "Security"),
          occurrenceDateTime = "2023-08-12T13:14:15",
        )
        caseNotesNomisApiMockServer.stubGetCaseNote(
          bookingId = BOOKING_ID,
          caseNoteId = NOMIS_CASE_NOTE_ID,
          caseNote = nomisCaseNote,
        )
      }

      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)

          awsSqsCaseNoteOffenderEventsClient.sendMessage(
            caseNotesQueueOffenderEventsUrl,
            caseNoteEvent(
              eventType = "OFFENDER_CASE_NOTES-UPDATED",
              bookingId = BOOKING_ID,
              caseNoteId = NOMIS_CASE_NOTE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
        }

        @Test
        fun `telemetry added to track the failure`() {
          await untilAsserted {
            verify(telemetryClient, atLeastOnce()).trackEvent(
              eq("casenotes-synchronisation-updated-failed"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `the event is placed on dead letter queue`() {
          await untilAsserted {
            assertThat(
              awsSqsCaseNotesOffenderEventDlqClient.countAllMessagesOnQueue(caseNotesQueueOffenderEventsDlqUrl).get(),
            ).isEqualTo(1)
          }
        }
      }

      @Nested
      @DisplayName("When mapping does exist")
      inner class MappingExists {

        @BeforeEach
        fun setUp() {
          caseNotesMappingApiMockServer.stubGetByNomisId(
            caseNoteId = NOMIS_CASE_NOTE_ID,
            CaseNoteMappingDto(
              nomisBookingId = BOOKING_ID,
              nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
              dpsCaseNoteId = DPS_CASE_NOTE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
              mappingType = MIGRATED,
            ),
          )
          caseNotesApi.stubPostCaseNote(dpsCaseNote())
          awsSqsCaseNoteOffenderEventsClient.sendMessage(
            caseNotesQueueOffenderEventsUrl,
            caseNoteEvent(
              eventType = "OFFENDER_CASE_NOTES-UPDATED",
              bookingId = BOOKING_ID,
              caseNoteId = NOMIS_CASE_NOTE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
        }

        @Test
        fun `will update DPS with the changes`() {
          await untilAsserted {
            caseNotesApi.verify(
              1,
              postRequestedFor(urlPathEqualTo("/sync/case-notes"))
                .withRequestBody(matchingJsonPath("id", equalTo(DPS_CASE_NOTE_ID)))
                .withRequestBody(matchingJsonPath("legacyId", equalTo(NOMIS_CASE_NOTE_ID.toString())))
                .withRequestBody(matchingJsonPath("personIdentifier", equalTo(OFFENDER_ID_DISPLAY)))
                .withRequestBody(matchingJsonPath("locationId", equalTo(nomisCaseNote.prisonId)))
                .withRequestBody(matchingJsonPath("type", equalTo("XNR")))
                .withRequestBody(matchingJsonPath("subType", equalTo("X")))
                .withRequestBody(matchingJsonPath("text", equalTo("the actual casenote")))
                .withRequestBody(matchingJsonPath("systemGenerated", equalTo("false")))
                .withRequestBody(matchingJsonPath("createdDateTime", equalTo("2021-02-03T04:05:06")))
                .withRequestBody(matchingJsonPath("createdByUsername", equalTo("TBC"))) // TODO
                .withRequestBody(matchingJsonPath("source", equalTo("NOMIS")))
                .withRequestBody(matchingJsonPath("authorUsername", equalTo("me")))
                .withRequestBody(matchingJsonPath("authorUserId", equalTo("123456")))
                .withRequestBody(matchingJsonPath("authorName", equalTo("me too")))
                .withRequestBody(matchingJsonPath("occurrenceDateTime", equalTo("2023-08-12T13:14:15"))),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("casenotes-synchronisation-updated-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
                assertThat(it["dpsCaseNoteId"]).isEqualTo(DPS_CASE_NOTE_ID)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("OFFENDER_CASE_NOTES-DELETED")
  inner class CaseNoteDeleted {
    @Nested
    @DisplayName("When caseNote was deleted in either NOMIS or DPS")
    inner class DeletedInEitherNOMISOrDPS {
      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)
          awsSqsCaseNoteOffenderEventsClient.sendMessage(
            caseNotesQueueOffenderEventsUrl,
            caseNoteEvent(
              eventType = "OFFENDER_CASE_NOTES-DELETED",
              bookingId = BOOKING_ID,
              caseNoteId = NOMIS_CASE_NOTE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
        }

        @Test
        fun `telemetry added to track that the delete was ignored`() {
          await untilAsserted {
            verify(telemetryClient, atLeastOnce()).trackEvent(
              eq("casenotes-deleted-synchronisation-skipped"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping does exist")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApiMockServer.stubGetByNomisId(
            caseNoteId = NOMIS_CASE_NOTE_ID,
            CaseNoteMappingDto(
              nomisBookingId = BOOKING_ID,
              nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
              dpsCaseNoteId = DPS_CASE_NOTE_ID,
              offenderNo = "A1234KT",
              mappingType = MIGRATED,
            ),
          )
          caseNotesApi.stubDeleteCaseNote()
          caseNotesMappingApiMockServer.stubDeleteMapping()
          awsSqsCaseNoteOffenderEventsClient.sendMessage(
            caseNotesQueueOffenderEventsUrl,
            caseNoteEvent(
              eventType = "OFFENDER_CASE_NOTES-DELETED",
              bookingId = BOOKING_ID,
              caseNoteId = NOMIS_CASE_NOTE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
        }

        @Test
        fun `will delete CaseNote in DPS`() {
          await untilAsserted {
            caseNotesApi.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/sync/case-notes/$DPS_CASE_NOTE_ID")),
            )
          }
        }

        @Test
        fun `will delete CaseNote mapping`() {
          await untilAsserted {
            caseNotesMappingApiMockServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/mapping/casenotes/dps-casenote-id/$DPS_CASE_NOTE_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("casenotes-synchronisation-deleted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
                assertThat(it["dpsCaseNoteId"]).isEqualTo(DPS_CASE_NOTE_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("When mapping fails to be deleted")
      inner class MappingDeleteFails {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApiMockServer.stubGetByNomisId(
            caseNoteId = NOMIS_CASE_NOTE_ID,
            CaseNoteMappingDto(
              nomisBookingId = BOOKING_ID,
              nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
              dpsCaseNoteId = DPS_CASE_NOTE_ID,
              offenderNo = "A1234KT",
              mappingType = MIGRATED,
            ),
          )
          caseNotesApi.stubDeleteCaseNote()
          caseNotesMappingApiMockServer.stubDeleteMapping(status = INTERNAL_SERVER_ERROR)
          awsSqsCaseNoteOffenderEventsClient.sendMessage(
            caseNotesQueueOffenderEventsUrl,
            caseNoteEvent(
              eventType = "OFFENDER_CASE_NOTES-DELETED",
              bookingId = BOOKING_ID,
              caseNoteId = NOMIS_CASE_NOTE_ID,
              offenderNo = OFFENDER_ID_DISPLAY,
            ),
          )
        }

        @Test
        fun `will delete CaseNote in DPS`() {
          await untilAsserted {
            caseNotesApi.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/sync/case-notes/$DPS_CASE_NOTE_ID")),
            )
          }
        }

        @Test
        fun `will try to delete CaseNote mapping once and record failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("casenotes-deleted-mapping-failed"),
              any(),
              isNull(),
            )

            caseNotesMappingApiMockServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/mapping/casenotes/dps-casenote-id/$DPS_CASE_NOTE_ID")),
            )
          }
        }

        @Test
        fun `will track a telemetry event for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("casenotes-synchronisation-deleted-success"),
              check {
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_ID_DISPLAY)
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
                assertThat(it["dpsCaseNoteId"]).isEqualTo(DPS_CASE_NOTE_ID)
              },
              isNull(),
            )
          }
        }
      }
    }
  }
}

fun caseNoteEvent(
  eventType: String,
  bookingId: Long = BOOKING_ID,
  caseNoteId: Long = NOMIS_CASE_NOTE_ID,
  offenderNo: String = OFFENDER_ID_DISPLAY,
) = """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\",\"eventDatetime\":\"2024-07-10T15:00:25.489964\",\"bookingId\": \"$bookingId\",\"caseNoteId\": \"$caseNoteId\",\"offenderIdDisplay\": \"$offenderNo\",\"nomisEventType\":\"$eventType\",\"caseNoteType\":\"L\",\"caseNoteSubType\":\"LCE\",\"recordDeleted\":false }",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
""".trimIndent()
/*
    eventType = xtag.eventType,
    eventDatetime = xtag.nomisTimestamp,
    bookingId = xtag.content.p_offender_book_id?.toLong(),
    caseNoteId = xtag.content.p_case_note_id?.toLong(),
    caseNoteType = xtag.content.p_case_note_type,
    caseNoteSubType = xtag.content.p_case_note_sub_type,
    recordDeleted = "Y".equals(xtag.content.p_delete_flag),
 */

private fun caseNote(bookingId: Long = 123456, caseNoteId: Long = 3) = CaseNoteResponse(
  bookingId = bookingId,
  caseNoteId = caseNoteId,
  caseNoteType = CodeDescription("X", "Security"),
  caseNoteSubType = CodeDescription("X", "Security"),
  authorUsername = "me",
  authorStaffId = 123456L,
  authorName = "me too",
  amendments = emptyList(),
  createdDatetime = "2021-02-03T04:05:06",
  noteSourceCode = CaseNoteResponse.NoteSourceCode.INST,
  occurrenceDateTime = "2021-02-03T04:05:06",
  prisonId = "SWI",
  caseNoteText = "the actual casenote",
  auditModuleName = "module",
)
