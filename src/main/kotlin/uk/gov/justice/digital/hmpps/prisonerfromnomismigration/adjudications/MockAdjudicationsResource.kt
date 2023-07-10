package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * This represents the possible interface for the adjudications service.
 * This can be deleted once real service is available.
 */
@RestController
class MockAdjudicationsResource {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('ROLE_ADJUDICATIONS_SYNC')") // todo which role?
  @PostMapping("/legacy/adjudications/migration")
  @Operation(hidden = true)
  suspend fun createAdjudicationsForMigration(
    @RequestBody @Valid
    adjudicationRequest: MockAdjudicationRequest,
  ): MockCreateAdjudicationResponse {
    log.info("Created adjudication for migration with id ${adjudicationRequest.adjudicationNumber} for offender no ${adjudicationRequest.offenderNo}. Request was $adjudicationRequest")
    return MockCreateAdjudicationResponse(adjudicationRequest.adjudicationNumber)
  }
}

data class MockAdjudicationRequest(
  // will change once adjudication API implemented
  val adjudicationNumber: Long,
  val offenderNo: String,
)

data class MockCreateAdjudicationResponse(
  val adjudicationNumber: Long,
)
