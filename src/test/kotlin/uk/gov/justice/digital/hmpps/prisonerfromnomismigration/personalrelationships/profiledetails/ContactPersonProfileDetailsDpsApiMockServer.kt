package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.PrisonerDomesticStatusMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.PrisonerNumberOfChildrenMigrationResponse
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
      put(urlPathMatching("/sync/$prisonerNumber/domestic-status"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
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
            .withBody(objectMapper.writeValueAsString(response)),
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
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubMigrateDomesticStatus(
    response: PrisonerDomesticStatusMigrationResponse = migrateDomesticStatusResponse(),
  ) {
    dpsContactPersonServer.stubFor(
      post(urlPathMatching("/migrate/domestic-status"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubMigrateDomesticStatus(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    dpsContactPersonServer.stubFor(
      post(urlPathMatching("/migrate/domestic-status"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubMigrateNumberOfChildren(
    response: PrisonerNumberOfChildrenMigrationResponse = migrateNumberOfChildrenResponse(),
  ) {
    dpsContactPersonServer.stubFor(
      post(urlPathMatching("/migrate/number-of-children"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubMigrateNumberOfChildren(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    dpsContactPersonServer.stubFor(
      post(urlPathMatching("/migrate/number-of-children"))
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

fun migrateDomesticStatusResponse(prisonerNumber: String = "A1234AA", currentDpsId: Long = 1, historyDpsIds: List<Long> = listOf(2, 3)) = PrisonerDomesticStatusMigrationResponse(
  prisonerNumber = prisonerNumber,
  current = currentDpsId,
  history = historyDpsIds,
)

fun migrateNumberOfChildrenResponse(prisonerNumber: String = "A1234AA", currentDpsId: Long = 4, historyDpsIds: List<Long> = listOf(5, 6)) = PrisonerNumberOfChildrenMigrationResponse(
  prisonerNumber = prisonerNumber,
  current = currentDpsId,
  history = historyDpsIds,
)
