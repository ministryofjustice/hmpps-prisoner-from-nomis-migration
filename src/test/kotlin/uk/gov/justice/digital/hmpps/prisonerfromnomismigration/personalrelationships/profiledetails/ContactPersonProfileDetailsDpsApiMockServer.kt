package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension.Companion.objectMapper

@Component
class ContactPersonProfileDetailsDpsApiMockServer {

  fun stubSyncDomesticStatus(
    prisonerNumber: String = "A1234AA",
    response: DomesticStatusSyncResponse,
  ) {
    dpsContactPersonServer.stubFor(
      put(urlPathMatching("/sync/domestic-status/$prisonerNumber"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncDomesticStatus(
    prisonerNumber: String = "A1234AA",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    dpsContactPersonServer.stubFor(
      put(urlPathMatching("/sync/domestic-status/$prisonerNumber"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubSyncDependants(
    prisonerNumber: String = "A1234AA",
    response: DependantsSyncResponse,
  ) {
    dpsContactPersonServer.stubFor(
      put(urlPathMatching("/sync/dependants/$prisonerNumber"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubSyncDependants(
    prisonerNumber: String = "A1234AA",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    dpsContactPersonServer.stubFor(
      put(urlPathMatching("/sync/dependants/$prisonerNumber"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = dpsContactPersonServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = dpsContactPersonServer.verify(count, pattern)
}
