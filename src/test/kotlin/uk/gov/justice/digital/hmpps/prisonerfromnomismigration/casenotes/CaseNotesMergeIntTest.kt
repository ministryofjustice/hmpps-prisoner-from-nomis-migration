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
    val survivorOffenderNo = "A1234BB"
    val removedOffenderNo = "A1234AA"
    val aMomentAgo = LocalDateTime.now().minusSeconds(1).toString()

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
            text = "text 2",
          ),
          caseNoteTemplate(
            caseNoteId = 103,
            bookingId = 2,
            text = "text 3",
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
            caseNoteId = 112,
            bookingId = 1,
            text = "text 4",
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
            dpsCaseNoteId = "00001111-2222-3333-4444-000000002",
            nomisCaseNoteId = 102,
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
    }

    @Test
    fun `will add mappings for the newly created NOMIS casenotes`() {
      sendMergeMessage()
      await untilAsserted {
        caseNotesMappingApiMockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/casenotes/batch"))
            .withRequestBodyJsonPath("[0].dpsCaseNoteId", "00001111-2222-3333-4444-000000003")
            .withRequestBodyJsonPath("[0].nomisCaseNoteId", 111)
            .withRequestBodyJsonPath("[0].offenderNo", survivorOffenderNo)
            .withRequestBodyJsonPath("[0].nomisBookingId", 1)
            .withRequestBodyJsonPath("[0].mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("[1].dpsCaseNoteId", "00001111-2222-3333-4444-000000004")
            .withRequestBodyJsonPath("[1].nomisCaseNoteId", 112)
            .withRequestBodyJsonPath("[1].offenderNo", survivorOffenderNo)
            .withRequestBodyJsonPath("[1].nomisBookingId", 1)
            .withRequestBodyJsonPath("[1].mappingType", "NOMIS_CREATED"),
        )
      }
    }

    @Test
    fun `will track telemetry for the merge`() {
      sendMergeMessage()
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
    fun `merge data not yet present`() {
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner(
        survivorOffenderNo,
        listOf(
          caseNoteTemplate(
            caseNoteId = 101,
            bookingId = 1,
            text = "Just an old case note which happens to be a previous merge",
            auditModuleName = "MERGE",
          ),
        ),
      )
      sendMergeMessage()

      await untilAsserted {
        verify(telemetryClient, times(2)).trackEvent(
          eq("casenotes-prisoner-merge-failed"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BB")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1234AA")
            assertThat(it["bookingId"]).isEqualTo("1")
            assertThat(it["error"]).isEqualTo("Merge data not ready for A1234BB")
          },
          isNull(),
        )
      }
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
  inner class BookingMoved {
    val uuid1 = generateUUID(1)
    val uuid2 = generateUUID(2)

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
    }

    @Test
    fun `will track telemetry for the merge`() {
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
