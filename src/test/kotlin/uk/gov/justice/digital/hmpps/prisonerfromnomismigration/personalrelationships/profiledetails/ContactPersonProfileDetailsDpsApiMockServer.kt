package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerDomesticStatusResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerNumberOfChildrenResponse

@Component
class ContactPersonProfileDetailsDpsApiMockServer {

  fun stubSyncDomesticStatus(
    prisonerNumber: String = "A1234AA",
    response: SyncPrisonerDomesticStatusResponse,
  ) {
    dpsContactPersonServer.stubFor(
      put(urlPathMatching("/sync/$prisonerNumber/domestic-status"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncDomesticStatus(
    prisonerNumber: String = "A1234AA",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    dpsContactPersonServer.stubFor(
      put(urlPathMatching("/sync/$prisonerNumber/domestic-status"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubSyncNumberOfChildren(
    prisonerNumber: String = "A1234AA",
    response: SyncPrisonerNumberOfChildrenResponse,
  ) {
    dpsContactPersonServer.stubFor(
      put(urlPathMatching("/sync/$prisonerNumber/number-of-children"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncNumberOfChildren(
    prisonerNumber: String = "A1234AA",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    dpsContactPersonServer.stubFor(
      put(urlPathMatching("/sync/$prisonerNumber/number-of-children"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubMergeProfileDetails(keepingPrisonerNumber: String = "A1234AA", removedPrisonerNumber: String = "B1234BB") {
    dpsContactPersonServer.stubFor(
      put(urlPathMatching("/merge/keep/$keepingPrisonerNumber/remove/$removedPrisonerNumber"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubMergeProfileDetails(
    keepingPrisonerNumber: String = "A1234AA",
    removedPrisonerNumber: String = "B1234BB",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    dpsContactPersonServer.stubFor(
      put(urlPathMatching("/merge/keep/$keepingPrisonerNumber/remove/$removedPrisonerNumber"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = dpsContactPersonServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = dpsContactPersonServer.verify(count, pattern)
}
