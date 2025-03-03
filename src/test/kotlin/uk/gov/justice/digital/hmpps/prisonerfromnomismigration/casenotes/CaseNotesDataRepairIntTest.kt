package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseNoteAmendment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseNoteResponse.SourceSystem
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import java.time.LocalDateTime

private const val BOOKING_ID = 1234L
private const val NOMIS_CASE_NOTE_ID = 2345678L
private const val NOMIS_CASE_NOTE_ID2 = 2345699L
private const val OFFENDER_ID_DISPLAY = "A3864DZ"
private const val DPS_CASE_NOTE_ID = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

class CaseNotesDataRepairIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var caseNotesNomisApiMockServer: CaseNotesNomisApiMockServer

  @Autowired
  private lateinit var caseNotesMappingApiMockServer: CaseNotesMappingApiMockServer

  @Nested
  inner class CaseNoteDeleteRepair {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.delete().uri("/casenotes/1234/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.delete().uri("/casenotes/1234/repair")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.delete().uri("/casenotes/1234/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    @DisplayName("When caseNote delete was requested")
    inner class DeletedCaseNote {
      @Nested
      @DisplayName("When mapping doesn't exist")
      inner class MappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApiMockServer.stubGetByNomisId(status = NOT_FOUND)

          webTestClient.delete().uri("/casenotes/$NOMIS_CASE_NOTE_ID/repair")
            .headers(setAuthorisation(roles = listOf("NOMIS_CASENOTES")))
            .exchange()
            .expectStatus().isNoContent
        }

        @Test
        fun `telemetry added to track that the delete was ignored`() {
          await untilAsserted {
            verify(telemetryClient, atLeastOnce()).trackEvent(
              eq("casenotes-deleted-synchronisation-skipped"),
              check {
                assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("Happy path - when mapping does exist")
      inner class HappyPath {
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
          caseNotesMappingApiMockServer.stubGetByDpsId(
            DPS_CASE_NOTE_ID,
            listOf(
              CaseNoteMappingDto(
                nomisBookingId = BOOKING_ID,
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                offenderNo = "A1234KT",
                mappingType = MIGRATED,
              ),
            ),
          )
          caseNotesApi.stubDeleteCaseNote()
          caseNotesMappingApiMockServer.stubDeleteMapping()

          webTestClient.delete().uri("/casenotes/$NOMIS_CASE_NOTE_ID/repair")
            .headers(setAuthorisation(roles = listOf("NOMIS_CASENOTES")))
            .exchange()
            .expectStatus().isNoContent
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
        fun `will track telemetry events for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("casenotes-repair-deleted-success"),
              check {
                assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
              },
              isNull(),
            )
          }
          verify(telemetryClient).trackEvent(
            eq("casenotes-synchronisation-deleted-success"),
            check {
              assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
              assertThat(it["dpsCaseNoteId"]).isEqualTo(DPS_CASE_NOTE_ID)
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("When merge duplicate exists")
      inner class MergeExists {
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
          caseNotesMappingApiMockServer.stubGetByDpsId(
            DPS_CASE_NOTE_ID,
            listOf(
              CaseNoteMappingDto(
                nomisBookingId = BOOKING_ID,
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                offenderNo = "A1234KT",
                mappingType = MIGRATED,
              ),
              CaseNoteMappingDto(
                nomisBookingId = BOOKING_ID,
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID2,
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                offenderNo = "A1234KT",
                mappingType = MIGRATED,
              ),
            ),
          )
          caseNotesApi.stubDeleteCaseNote()
          caseNotesNomisApiMockServer.stubDeleteCaseNote(NOMIS_CASE_NOTE_ID2)
          caseNotesMappingApiMockServer.stubDeleteMapping()

          webTestClient.delete().uri("/casenotes/$NOMIS_CASE_NOTE_ID/repair")
            .headers(setAuthorisation(roles = listOf("NOMIS_CASENOTES")))
            .exchange()
            .expectStatus().isNoContent
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
        fun `will delete related CaseNote in Nomis`() {
          await untilAsserted {
            caseNotesNomisApiMockServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/casenotes/$NOMIS_CASE_NOTE_ID2")),
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
        fun `will track 2 telemetry events for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("casenotes-synchronisation-deleted-success"),
              check {
                assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
                assertThat(it["dpsCaseNoteId"]).isEqualTo(DPS_CASE_NOTE_ID)
              },
              isNull(),
            )
            verify(telemetryClient).trackEvent(
              eq("casenotes-synchronisation-deleted-related-success"),
              check {
                assertThat(it["bookingId"]).isEqualTo(BOOKING_ID.toString())
                assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID2.toString())
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
          caseNotesMappingApiMockServer.stubGetByDpsId(
            DPS_CASE_NOTE_ID,
            listOf(
              CaseNoteMappingDto(
                nomisBookingId = BOOKING_ID,
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                offenderNo = "A1234KT",
                mappingType = MIGRATED,
              ),
            ),
          )
          caseNotesApi.stubDeleteCaseNote()
          caseNotesMappingApiMockServer.stubDeleteMapping(status = INTERNAL_SERVER_ERROR)

          webTestClient.delete().uri("/casenotes/$NOMIS_CASE_NOTE_ID/repair")
            .headers(setAuthorisation(roles = listOf("NOMIS_CASENOTES")))
            .exchange()
            .expectStatus().isNoContent
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
              eq("casenotes-synchronisation-deleted-failed"),
              any(),
              isNull(),
            )

            caseNotesMappingApiMockServer.verify(
              1,
              deleteRequestedFor(urlPathEqualTo("/mapping/casenotes/dps-casenote-id/$DPS_CASE_NOTE_ID")),
            )
          }
        }
      }

      @Nested
      @DisplayName("When casenotes api POST fails")
      inner class CasenotesApiFail {
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
          caseNotesMappingApiMockServer.stubGetByDpsId(
            DPS_CASE_NOTE_ID,
            listOf(
              CaseNoteMappingDto(
                nomisBookingId = BOOKING_ID,
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                offenderNo = "A1234KT",
                mappingType = MIGRATED,
              ),
            ),
          )
          caseNotesApi.stubDeleteCaseNoteFailure()
          caseNotesMappingApiMockServer.stubDeleteMapping()

          webTestClient.delete().uri("/casenotes/$NOMIS_CASE_NOTE_ID/repair")
            .headers(setAuthorisation(roles = listOf("NOMIS_CASENOTES")))
            .exchange()
            .expectStatus().isNoContent
        }

        @Test
        fun `will track a telemetry event for failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("casenotes-synchronisation-deleted-failed"),
              check {
                assertThat(it["nomisCaseNoteId"]).isEqualTo(NOMIS_CASE_NOTE_ID.toString())
                assertThat(it["error"]).isEqualTo("500 Internal Server Error from DELETE http://localhost:8096/sync/case-notes/$DPS_CASE_NOTE_ID")
              },
              isNull(),
            )
          }
        }
      }
    }
  }
}

private fun caseNote(bookingId: Long = 123456, caseNoteId: Long = 3) = CaseNoteResponse(
  bookingId = bookingId,
  caseNoteId = caseNoteId,
  caseNoteType = CodeDescription("XNR", "Not For Release"),
  caseNoteSubType = CodeDescription("X", "Security"),
  authorUsername = "me",
  authorStaffId = 123456L,
  authorFirstName = "First",
  authorLastName = "Last",
  amendments = listOf(
    CaseNoteAmendment(
      createdDateTime = LocalDateTime.parse("2021-02-03T04:05:06"),
      text = "amendment text",
      authorUsername = "authorone",
      authorStaffId = 2001,
      authorFirstName = "AUTHOR",
      authorLastName = "ONE",
      sourceSystem = CaseNoteAmendment.SourceSystem.NOMIS,
    ),
  ),
  creationDateTime = LocalDateTime.parse("2021-02-03T04:05:06"),
  createdDatetime = LocalDateTime.parse("2023-11-12T13:14:15"),
  createdUsername = "John",
  noteSourceCode = CaseNoteResponse.NoteSourceCode.INST,
  occurrenceDateTime = LocalDateTime.parse("2021-02-03T04:05:06"),
  prisonId = "SWI",
  caseNoteText = "the actual casenote",
  auditModuleName = "module",
  sourceSystem = SourceSystem.NOMIS,
)
