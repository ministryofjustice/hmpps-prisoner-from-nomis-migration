package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiExtension.Companion.caseNotesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseNoteAmendment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseNoteResponse.SourceSystem
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime
import java.time.OffsetDateTime

class CaseNotesMergeIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var caseNotesMappingApiMockServer: CaseNotesMappingApiMockServer

  @Autowired
  private lateinit var caseNotesNomisApiMockServer: CaseNotesNomisApiMockServer

  @Nested
  inner class OffenderMerged {
    private val survivorOffenderNo = "A1234BB"
    private val removedOffenderNo = "A1234AA"
    private val aMomentAgo = LocalDateTime.now().minusSeconds(1).toString()
    private val fiveMinutesAgo = LocalDateTime.now().minusMinutes(5).toString()

    @BeforeEach
    fun setUp() {
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner(
        survivorOffenderNo,
        listOf(
          caseNoteTemplate(
            caseNoteId = 101,
            bookingId = 1,
            text = "text 1",
            // an old merge (irrelevant)
            auditModuleName = "MERGE",
          ),
          caseNoteTemplate(
            caseNoteId = 102,
            bookingId = 1,
            // there can be duplicate case notes
            text = "text 2 dupe",
          ),
          caseNoteTemplate(
            caseNoteId = 192,
            bookingId = 1,
            text = "text 2 dupe",
          ),
          caseNoteTemplate(
            caseNoteId = 103,
            bookingId = 2,
            text = "text 3",
          ),
          caseNoteTemplate(
            caseNoteId = 193,
            bookingId = 2,
            text = "text 3 dupe",
          ),
          caseNoteTemplate(
            caseNoteId = 104,
            bookingId = 2,
            text = "text 4",
          ),
          caseNoteTemplate(
            caseNoteId = 111,
            bookingId = 1,
            text = "text 3",
            auditModuleName = "MERGE",
            createdDatetime = aMomentAgo,
          ),
          caseNoteTemplate(
            caseNoteId = 191,
            bookingId = 1,
            text = "text 3 dupe",
            auditModuleName = "MERGE",
            createdDatetime = aMomentAgo,
          ),
          caseNoteTemplate(
            caseNoteId = 112,
            bookingId = 1,
            text = "text 4",
            auditModuleName = "MERGE",
            createdDatetime = aMomentAgo,
          ),
          // a previous merge that is already mapped from 5 minutes ago
          caseNoteTemplate(
            caseNoteId = 120,
            bookingId = 1,
            text = "text 4",
            auditModuleName = "MERGE",
            createdDatetime = fiveMinutesAgo,
          ),
        ),
      )
      caseNotesMappingApiMockServer.stubGetMappings(
        listOf(
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000001",
            nomisCaseNoteId = 101,
            offenderNo = survivorOffenderNo,
            nomisBookingId = 1,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000002",
            nomisCaseNoteId = 102,
            offenderNo = survivorOffenderNo,
            nomisBookingId = 1,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000002",
            nomisCaseNoteId = 192,
            offenderNo = survivorOffenderNo,
            nomisBookingId = 1,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000003",
            nomisCaseNoteId = 103,
            offenderNo = removedOffenderNo,
            nomisBookingId = 2,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000003",
            nomisCaseNoteId = 193,
            offenderNo = removedOffenderNo,
            nomisBookingId = 2,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000004",
            nomisCaseNoteId = 104,
            offenderNo = removedOffenderNo,
            nomisBookingId = 2,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000004",
            nomisCaseNoteId = 120,
            offenderNo = removedOffenderNo,
            nomisBookingId = 2,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
        ),
      )
      caseNotesMappingApiMockServer.stubUpdateMappingsByNomisId()
      caseNotesMappingApiMockServer.stubPostMappingsBatch()
    }

    private fun sendMergeMessage() {
      awsSqsCaseNoteOffenderEventsClient.sendMessage(
        caseNotesQueueOffenderEventsUrl,
        mergeDomainEvent(
          offenderNo = survivorOffenderNo,
          removedOffenderNo = removedOffenderNo,
          bookingId = 1,
          occurredAt = OffsetDateTime.now().toString(),
        ),
      )
    }

    @Test
    fun `will correct the mappings between the existing DPS and NOMIS casenotes`() {
      sendMergeMessage()
      await untilAsserted {
        caseNotesMappingApiMockServer.verify(
          putRequestedFor(urlPathEqualTo("/mapping/casenotes/merge/from/A1234AA/to/A1234BB")),
        )
      }
      // ensure the process has finished before the test ends by checking the telemetry
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("casenotes-prisoner-merge"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BB")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("1")
          },
          isNull(),
        )
        // also verify that we ignored any case notes that we have already got the mappings for
        verify(telemetryClient).trackEvent(
          eq("casenotes-prisoner-merge-existing-mappings"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BB")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("1")
            assertThat(it["mappingsCount"]).isEqualTo("1")
            assertThat(it["mappings"]).isEqualTo("120")
          },
          isNull(),
        )
      }
    }

    @Test
    fun `will add mappings for the newly created NOMIS casenotes`() {
      sendMergeMessage()
      await untilAsserted {
        caseNotesMappingApiMockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/casenotes/batch"))
            .withRequestBodyJsonPath("$.size()", "3")
            .withRequestBodyJsonPath("[0].dpsCaseNoteId", "00001111-2222-3333-4444-000000003")
            .withRequestBodyJsonPath("[0].nomisCaseNoteId", 111)
            .withRequestBodyJsonPath("[0].offenderNo", survivorOffenderNo)
            .withRequestBodyJsonPath("[0].nomisBookingId", 1)
            .withRequestBodyJsonPath("[0].mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("[1].dpsCaseNoteId", "00001111-2222-3333-4444-000000003")
            .withRequestBodyJsonPath("[1].nomisCaseNoteId", 191)
            .withRequestBodyJsonPath("[1].offenderNo", survivorOffenderNo)
            .withRequestBodyJsonPath("[1].nomisBookingId", 1)
            .withRequestBodyJsonPath("[1].mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("[2].dpsCaseNoteId", "00001111-2222-3333-4444-000000004")
            .withRequestBodyJsonPath("[2].nomisCaseNoteId", 112)
            .withRequestBodyJsonPath("[2].offenderNo", survivorOffenderNo)
            .withRequestBodyJsonPath("[2].nomisBookingId", 1)
            .withRequestBodyJsonPath("[2].mappingType", "NOMIS_CREATED"),
        )
      }
    }

    @Test
    fun `first update fails`() {
      caseNotesMappingApiMockServer.stubUpdateMappingsByNomisIdError(HttpStatus.INTERNAL_SERVER_ERROR)
      sendMergeMessage()

      await untilAsserted {
        verify(telemetryClient, times(2)).trackEvent(
          eq("casenotes-prisoner-merge-failed"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BB")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("1")
            assertThat(it["error"]).isEqualTo("500 Internal Server Error from PUT http://localhost:8083/mapping/casenotes/merge/from/A1234AA/to/A1234BB")
          },
          isNull(),
        )
      }
      await untilAsserted {
        caseNotesMappingApiMockServer.verify(
          2,
          putRequestedFor(urlPathEqualTo("/mapping/casenotes/merge/from/A1234AA/to/A1234BB")),
        )
      }
      caseNotesMappingApiMockServer.verify(
        0,
        postRequestedFor(urlPathEqualTo("/mapping/casenotes/batch")),
      )
    }

    @Test
    fun `second update fails`() {
      caseNotesMappingApiMockServer.stubPostMappingsBatch(HttpStatus.INTERNAL_SERVER_ERROR)
      sendMergeMessage()

      await untilAsserted {
        verify(telemetryClient, times(2)).trackEvent(
          eq("casenotes-prisoner-merge-failed"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BB")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("1")
            assertThat(it["error"]).isEqualTo("500 Internal Server Error from POST http://localhost:8083/mapping/casenotes/batch")
          },
          isNull(),
        )
      }
      await untilAsserted {
        caseNotesMappingApiMockServer.verify(
          2,
          putRequestedFor(urlPathEqualTo("/mapping/casenotes/merge/from/A1234AA/to/A1234BB")),
        )
      }
      caseNotesMappingApiMockServer.verify(
        2,
        postRequestedFor(urlPathEqualTo("/mapping/casenotes/batch")),
      )
    }

    @Test
    fun `merge has already occurred`() {
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner(
        survivorOffenderNo,
        listOf(
          caseNoteTemplate(
            caseNoteId = 101,
            bookingId = 1,
            text = "a case note",
          ),
          caseNoteTemplate(
            caseNoteId = 111,
            bookingId = 1,
            text = "a case note",
            auditModuleName = "MERGE",
            createdDatetime = aMomentAgo,
          ),
        ),
      )
      caseNotesMappingApiMockServer.stubGetMappings(
        listOf(
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000001",
            nomisCaseNoteId = 101,
            offenderNo = survivorOffenderNo,
            nomisBookingId = 1,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000001",
            nomisCaseNoteId = 111,
            offenderNo = survivorOffenderNo,
            nomisBookingId = 1,
            mappingType = CaseNoteMappingDto.MappingType.NOMIS_CREATED,
          ),
        ),
      )
      sendMergeMessage()

      await untilAsserted {
        verify(telemetryClient, times(1)).trackEvent(
          eq("casenotes-prisoner-merge-skipped"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BB")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("1")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  inner class OffenderMergedNoMergeCopies {
    private val survivorOffenderNo = "A1234BB"
    private val removedOffenderNo = "A1234AA"
    private val aMomentAgo = LocalDateTime.now().minusSeconds(1).toString()
    private val fiveMinutesAgo = LocalDateTime.now().minusMinutes(5).toString()

    @BeforeEach
    fun setUp() {
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner(
        survivorOffenderNo,
        listOf(
          caseNoteTemplate(
            caseNoteId = 101,
            bookingId = 1,
            text = "text 1",
            // an old merge (irrelevant)
            auditModuleName = "MERGE",
          ),
          caseNoteTemplate(
            caseNoteId = 102,
            bookingId = 1,
            // there can be duplicate case notes
            text = "text 2 dupe",
          ),
          caseNoteTemplate(
            caseNoteId = 192,
            bookingId = 1,
            text = "text 2 dupe",
          ),
          caseNoteTemplate(
            caseNoteId = 103,
            bookingId = 2,
            text = "text 3",
          ),
          caseNoteTemplate(
            caseNoteId = 193,
            bookingId = 2,
            text = "text 3 dupe",
          ),
          caseNoteTemplate(
            caseNoteId = 104,
            bookingId = 2,
            text = "text 4",
          ),
        ),
      )
      caseNotesMappingApiMockServer.stubGetMappings(
        listOf(
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000001",
            nomisCaseNoteId = 101,
            offenderNo = survivorOffenderNo,
            nomisBookingId = 1,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000002",
            nomisCaseNoteId = 102,
            offenderNo = survivorOffenderNo,
            nomisBookingId = 1,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000002",
            nomisCaseNoteId = 192,
            offenderNo = survivorOffenderNo,
            nomisBookingId = 1,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000003",
            nomisCaseNoteId = 103,
            offenderNo = removedOffenderNo,
            nomisBookingId = 2,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000003",
            nomisCaseNoteId = 193,
            offenderNo = removedOffenderNo,
            nomisBookingId = 2,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = "00001111-2222-3333-4444-000000004",
            nomisCaseNoteId = 104,
            offenderNo = removedOffenderNo,
            nomisBookingId = 2,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
        ),
      )
      caseNotesMappingApiMockServer.stubUpdateMappingsByNomisId()
      caseNotesMappingApiMockServer.stubPostMappingsBatch()
    }

    private fun sendMergeMessage() {
      awsSqsCaseNoteOffenderEventsClient.sendMessage(
        caseNotesQueueOffenderEventsUrl,
        mergeDomainEvent(
          offenderNo = survivorOffenderNo,
          removedOffenderNo = removedOffenderNo,
          bookingId = 1,
          occurredAt = OffsetDateTime.now().toString(),
        ),
      )
    }

    @Test
    fun `will correct the mappings between the existing DPS and NOMIS casenotes`() {
      sendMergeMessage()
      await untilAsserted {
        caseNotesMappingApiMockServer.verify(
          putRequestedFor(urlPathEqualTo("/mapping/casenotes/merge/from/A1234AA/to/A1234BB")),
        )
      }
      // ensure the process has finished before the test ends by checking the telemetry
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("casenotes-prisoner-merge"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BB")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("1")
          },
          isNull(),
        )
      }
    }

    @Test
    fun `first update fails`() {
      caseNotesMappingApiMockServer.stubUpdateMappingsByNomisIdError(HttpStatus.INTERNAL_SERVER_ERROR)
      sendMergeMessage()

      await untilAsserted {
        verify(telemetryClient, times(2)).trackEvent(
          eq("casenotes-prisoner-merge-failed"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BB")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("1")
            assertThat(it["error"]).isEqualTo("500 Internal Server Error from PUT http://localhost:8083/mapping/casenotes/merge/from/A1234AA/to/A1234BB")
          },
          isNull(),
        )
      }
      await untilAsserted {
        caseNotesMappingApiMockServer.verify(
          2,
          putRequestedFor(urlPathEqualTo("/mapping/casenotes/merge/from/A1234AA/to/A1234BB")),
        )
      }
      caseNotesMappingApiMockServer.verify(
        0,
        postRequestedFor(urlPathEqualTo("/mapping/casenotes/batch")),
      )
    }
  }

  @Nested
  inner class BookingMoved {
    private val uuid1 = generateUUID(1)
    private val uuid2 = generateUUID(2)

    @BeforeEach
    fun setUp() {
      caseNotesMappingApiMockServer.stubUpdateMappingsByBookingId(
        listOf(
          CaseNoteMappingDto(
            dpsCaseNoteId = uuid1,
            nomisCaseNoteId = 1,
            offenderNo = "A1234AA",
            nomisBookingId = 1,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
          CaseNoteMappingDto(
            dpsCaseNoteId = uuid2,
            nomisCaseNoteId = 2,
            offenderNo = "A1234AA",
            nomisBookingId = 1,
            mappingType = CaseNoteMappingDto.MappingType.MIGRATED,
          ),
        ),
      )
      caseNotesApi.stubMoveCaseNotes()

      awsSqsCaseNoteOffenderEventsClient.sendMessage(
        caseNotesQueueOffenderEventsUrl,
        bookingMovedDomainEvent(
          bookingId = 12,
          movedToNomsNumber = "A1234BB",
          movedFromNomsNumber = "A1234AA",
        ),
      )
    }

    @Test
    fun `will correct the mappings between the DPS and NOMIS casenotes`() {
      await untilAsserted {
        caseNotesMappingApiMockServer.verify(
          putRequestedFor(urlPathEqualTo("/mapping/casenotes/merge/booking-id/12/to/A1234BB")),
        )
      }
      // ensure the process has finished before the test ends by checking the telemetry
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("casenotes-booking-moved"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12")
            assertThat(it["movedToNomsNumber"]).isEqualTo("A1234BB")
            assertThat(it["movedFromNomsNumber"]).isEqualTo("A1234AA")
            assertThat(it["count"]).isEqualTo("2")
          },
          isNull(),
        )
      }
    }

    @Test
    fun `will correct the DPS casenotes`() {
      await untilAsserted {
        caseNotesApi.verify(
          putRequestedFor(urlPathEqualTo("/move/case-notes"))
            .withRequestBodyJsonPath("fromPersonIdentifier", "A1234AA")
            .withRequestBodyJsonPath("toPersonIdentifier", "A1234BB")
            .withRequestBodyJsonPath("caseNoteIds[0]", uuid1)
            .withRequestBodyJsonPath("caseNoteIds[1]", uuid2),
        )
      }
      // ensure the process has finished before the test ends by checking the telemetry
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("casenotes-booking-moved"),
          check {
            assertThat(it["bookingId"]).isEqualTo("12")
            assertThat(it["movedToNomsNumber"]).isEqualTo("A1234BB")
            assertThat(it["movedFromNomsNumber"]).isEqualTo("A1234AA")
            assertThat(it["count"]).isEqualTo("2")
          },
          isNull(),
        )
      }
    }

    private fun generateUUID(i: Int): String = "00001111-2222-3333-4444-000000${i.toString().padStart(6, '0')}"
  }
}

fun caseNoteTemplate(
  caseNoteId: Long,
  bookingId: Long,
  text: String = "text",
  auditModuleName: String = "OIDCXXXX",
  type: String = "GEN",
  createdDatetime: String = "2021-02-03T04:05:06",
) = CaseNoteResponse(
  caseNoteId = caseNoteId,
  bookingId = bookingId,
  caseNoteText = text,
  caseNoteType = CodeDescription(type, "desc"),
  auditModuleName = auditModuleName,
  caseNoteSubType = CodeDescription("OUTCOME", "desc"),
  authorUsername = "me",
  authorStaffId = 123456L,
  authorFirstName = "First",
  authorLastName = "Last",
  amendments = listOf(CaseNoteAmendment("$text amend", "me", LocalDateTime.parse("2021-02-03T04:05:07"), CaseNoteAmendment.SourceSystem.NOMIS)),
  createdDatetime = LocalDateTime.parse(createdDatetime),
  creationDateTime = LocalDateTime.parse("2023-04-05T06:07:08"),
  createdUsername = "John",
  noteSourceCode = CaseNoteResponse.NoteSourceCode.INST,
  occurrenceDateTime = LocalDateTime.parse("2021-02-03T04:05:06"),
  prisonId = "SWI",
  sourceSystem = SourceSystem.NOMIS,
)
