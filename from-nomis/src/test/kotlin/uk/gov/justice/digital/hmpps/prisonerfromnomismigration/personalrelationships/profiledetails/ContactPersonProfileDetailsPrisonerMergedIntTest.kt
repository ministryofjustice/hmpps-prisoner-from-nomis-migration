package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.mergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.countAllMessagesOnDLQQueue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.sendMessage

class ContactPersonProfileDetailsPrisonerMergedIntTest(
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiMockServer,
  @Autowired private val nomisSyncApi: ContactPersonNomisSyncApiMockServer,
) : SqsIntegrationTestBase() {

  private val keepingPrisonerNumber = "A1234AA"
  private val removedPrisonerNumber = "B1234BB"

  @Nested
  inner class HappyPath {
    @Test
    fun `should call merge endpoint and sync back to NOMIS`() {
      dpsApi.stubMergeProfileDetails(keepingPrisonerNumber, removedPrisonerNumber)
      nomisSyncApi.stubSyncProfileDetails(keepingPrisonerNumber, "MARITAL")
      nomisSyncApi.stubSyncProfileDetails(keepingPrisonerNumber, "CHILD")

      sendPrisonerMergedEvent().also {
        waitForAnyProcessingToComplete("contact-person-profile-details-prisoner-merged")
      }

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/merge/keep/$keepingPrisonerNumber/remove/$removedPrisonerNumber")),
      )

      nomisSyncApi.verify(
        putRequestedFor(urlPathEqualTo("/contactperson/sync/profile-details/$keepingPrisonerNumber/MARITAL")),
      )
      nomisSyncApi.verify(
        putRequestedFor(urlPathEqualTo("/contactperson/sync/profile-details/$keepingPrisonerNumber/CHILD")),
      )

      verify(telemetryClient).trackEvent(
        "contact-person-profile-details-prisoner-merged",
        mapOf(
          "bookingId" to "1234",
          "offenderNo" to keepingPrisonerNumber,
          "removedOffenderNo" to removedPrisonerNumber,
          "syncToNomis" to "$keepingPrisonerNumber-MARITAL,$keepingPrisonerNumber-CHILD",
        ),
        null,
      )
    }
  }

  @Nested
  inner class Errors {
    @Test
    fun `should put message on DLQ`() {
      dpsApi.stubMergeProfileDetails(keepingPrisonerNumber, removedPrisonerNumber, HttpStatus.BAD_GATEWAY)

      sendPrisonerMergedEvent().also {
        waitForDlqMessage()
      }

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/merge/keep/$keepingPrisonerNumber/remove/$removedPrisonerNumber")),
      )

      verify(telemetryClient).trackEvent(
        "contact-person-profile-details-prisoner-merged-error",
        mapOf(
          "bookingId" to "1234",
          "offenderNo" to keepingPrisonerNumber,
          "removedOffenderNo" to removedPrisonerNumber,
          "error" to "502 Bad Gateway from PUT http://localhost:8097/merge/keep/A1234AA/remove/B1234BB",
        ),
        null,
      )
    }
  }

  private fun sendPrisonerMergedEvent(): SendMessageResponse? = personalRelationshipsDomainEventsQueue.sendMessage(
    mergeDomainEvent(
      bookingId = 1234,
      offenderNo = keepingPrisonerNumber,
      removedOffenderNo = removedPrisonerNumber,
    ),
  )

  private fun waitForDlqMessage() = await untilAsserted {
    assertThat(
      personalRelationshipsDomainEventsQueue.countAllMessagesOnDLQQueue(),
    ).isEqualTo(1)
  }
}
