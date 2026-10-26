package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact

class CorePersonSynchronisationIntTest(
  @Autowired private val corePersonNomisApiMock: CorePersonNomisApiMockServer,
  @Autowired private val corePersonMappingApiMock: CorePersonMappingApiMockServer,
) : CorePersonIntegrationTestBase() {
  @Nested
  @DisplayName("prison-offender-events.prisoner.merged")
  inner class PrisonerMerge {
    @Nested
    inner class HappyPath {
      val bookingId = 1234567L
      val offenderNo = "A1234KT"
      val removedOffenderNo = "A1000KT"

      @BeforeEach
      fun setUp() {
        corePersonNomisApiMock.stubGetAddressesAndContacts(
          prisonNumber = offenderNo,
          addressesAndContacts = CorePersonAddressContact(
            addresses = emptyList(),
            phoneNumbers = emptyList(),
            emailAddresses = emptyList(),
          ),
        )
        corePersonMappingApiMock.stubGetMappingsFromNomisIds()
        corePersonCprApiMockServer.stubProcessPrisonMerge(offenderNo)
        corePersonMappingApiMock.stubMerge()
        awsSqsCorePersonOffenderEventsClient.sendMessage(
          corePersonQueueOffenderEventsUrl,
          mergeDomainEvent(
            bookingId = bookingId,
            offenderNo = offenderNo,
            removedOffenderNo = removedOffenderNo,
          ),
        )
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will track telemetry for the merge`() {
        verify(telemetryClient).trackEvent(
          eq("coreperson-prisoner-merge-synchronisation"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["removedOffenderNo"]).isEqualTo(removedOffenderNo)
          },
          isNull(),
        )
      }

      @Test
      fun `will call core person for the merge`() {
        corePersonCprApiMockServer.verify(
          postRequestedFor(urlMatching("/syscon-sync/person/$offenderNo/merge"))
            .withRequestBody(
              equalToJson(
                """
                {
                  "fromPrisonNumber": "$removedOffenderNo",
                  "toPrisonAddressesAndContactsRequest": {
                    "addresses": [],
                    "contacts": []
                  }
                }
                """.trimIndent(),
              ),
            ),
        )
      }
    }
  }
}
