package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.NonAssociation

class NonAssociationsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val nonAssociationsApi = NonAssociationsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    nonAssociationsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    nonAssociationsApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    nonAssociationsApi.stop()
  }
}

class NonAssociationsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8088
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubUpsertNonAssociationForSynchronisation(nonAssociationId: Long = 654321, firstOffenderNo: String = "A1234BC", secondOffenderNo: String = "D5678EF") {
    stubFor(
      WireMock.put(WireMock.urlMatching("/sync/upsert")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(
            NonAssociation(
              id = nonAssociationId,
              firstPrisonerNumber = firstOffenderNo,
              firstPrisonerRole = NonAssociation.FirstPrisonerRole.VICTIM,
              firstPrisonerRoleDescription = "Victim",
              secondPrisonerNumber = secondOffenderNo,
              secondPrisonerRole = NonAssociation.SecondPrisonerRole.PERPETRATOR,
              secondPrisonerRoleDescription = "Perpetrator",
              reason = NonAssociation.Reason.BULLYING,
              reasonDescription = "Bullying",
              restrictionType = NonAssociation.RestrictionType.CELL,
              restrictionTypeDescription = "Cell",
              comment = "John and Luke always end up fighting",
              authorisedBy = "OFF3_GEN",
              whenCreated = "2023-07-05T11:12:45",
              whenUpdated = "2023-07-06T13:35:17",
              updatedBy = "OFF3_GEN",
              isClosed = false,
              closedBy = "null",
              closedReason = "null",
              closedAt = "2023-07-09T15:44:23",
              isOpen = true,
            ).toJson(),
          ),
      ),
    )
  }
}

private fun Any.toJson(): String = ObjectMapper().writeValueAsString(this)
