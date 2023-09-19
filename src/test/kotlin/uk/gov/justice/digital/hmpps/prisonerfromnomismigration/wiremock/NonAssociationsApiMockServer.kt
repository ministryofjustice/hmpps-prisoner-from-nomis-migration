package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NO_CONTENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.NonAssociation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.NonAssociation.FirstPrisonerRole
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.NonAssociation.Reason
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.NonAssociation.RestrictionType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations.model.NonAssociation.SecondPrisonerRole

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

  fun stubUpsertNonAssociationForSynchronisation(nonAssociationId: Long = 4321, firstOffenderNo: String = "A1234BC", secondOffenderNo: String = "D5678EF") {
    stubFor(
      put(urlMatching("/sync/upsert")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(
            NonAssociation(
              id = nonAssociationId,
              firstPrisonerNumber = firstOffenderNo,
              firstPrisonerRole = FirstPrisonerRole.VICTIM,
              firstPrisonerRoleDescription = "Victim",
              secondPrisonerNumber = secondOffenderNo,
              secondPrisonerRole = SecondPrisonerRole.PERPETRATOR,
              secondPrisonerRoleDescription = "Perpetrator",
              reason = Reason.BULLYING,
              reasonDescription = "Bullying",
              restrictionType = RestrictionType.CELL,
              restrictionTypeDescription = "Cell",
              comment = "John and Luke always end up fighting",
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

  fun stubDeleteNonAssociationForSynchronisation(nonAssociationId: Long) {
    stubFor(
      delete(urlMatching("/sync/$nonAssociationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(NO_CONTENT.value()),
      ),
    )
  }

  fun stubDeleteNonAssociationForSynchronisationNotFound(nonAssociationId: Long) {
    stubFor(
      delete(urlMatching("/sync/$nonAssociationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value()),
      ),
    )
  }

  fun stubUpsertNonAssociationForMigration(nonAssociationId: Long = 4321, firstOffenderNo: String = "A1234BC", secondOffenderNo: String = "D5678EF") {
    stubFor(
      post(urlMatching("/migrate")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(
            NonAssociation(
              id = nonAssociationId,
              firstPrisonerNumber = firstOffenderNo,
              firstPrisonerRole = FirstPrisonerRole.VICTIM,
              firstPrisonerRoleDescription = "Victim",
              secondPrisonerNumber = secondOffenderNo,
              secondPrisonerRole = SecondPrisonerRole.PERPETRATOR,
              secondPrisonerRoleDescription = "Perpetrator",
              reason = Reason.BULLYING,
              reasonDescription = "Bullying",
              restrictionType = RestrictionType.CELL,
              restrictionTypeDescription = "Cell",
              comment = "John and Luke always end up fighting",
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

  fun createNonAssociationSynchronisationCount() =
    findAll(putRequestedFor(urlMatching("/sync/upsert"))).count()

  fun createNonAssociationMigrationCount() =
    findAll(postRequestedFor(urlMatching("/migrate"))).count()
}

private fun Any.toJson(): String = ObjectMapper().writeValueAsString(this)
