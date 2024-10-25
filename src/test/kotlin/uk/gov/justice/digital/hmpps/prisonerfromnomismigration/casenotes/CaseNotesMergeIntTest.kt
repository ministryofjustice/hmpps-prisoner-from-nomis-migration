package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

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
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiExtension.Companion.caseNotesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.bookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

class CaseNotesMergeIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var caseNotesMappingApiMockServer: CaseNotesMappingApiMockServer

  @Nested
  inner class OffenderMerged {
    @BeforeEach
    fun setUp() {
      caseNotesMappingApiMockServer.stubUpdateMappingsByNomisId()

      awsSqsCaseNoteOffenderEventsClient.sendMessage(
        caseNotesQueueOffenderEventsUrl,
        mergeDomainEvent(
          offenderNo = "A1234BB",
          removedOffenderNo = "A1234AA",
        ),
      )
    }

    @Test
    fun `will correct the mappings between the DPS and NOMIS casenotes`() {
      await untilAsserted {
        caseNotesMappingApiMockServer.verify(
          putRequestedFor(urlPathEqualTo("/mapping/casenotes/merge/from/A1234AA/to/A1234BB")),
        )
      }
    }

    @Test
    fun `will track telemetry for the merge`() {
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("casenotes-prisoner-merge"),
          check {
            assertThat(it["offenderNo"]).isEqualTo("A1234BB")
            assertThat(it["removedOffenderNo"]).isEqualTo("A1234AA")
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
