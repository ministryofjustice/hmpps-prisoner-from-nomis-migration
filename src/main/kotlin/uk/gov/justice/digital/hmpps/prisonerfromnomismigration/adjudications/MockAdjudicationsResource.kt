package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.AdjudicationMigrateDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ChargeNumberMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateResponse

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
  @PostMapping("/reported-adjudications/migrate")
  @Operation(hidden = true)
  suspend fun createAdjudicationsForMigration(
    @RequestBody @Valid
    adjudicationRequest: AdjudicationMigrateDto,
  ): MigrateResponse {
    log.info("Created adjudication for migration with id ${adjudicationRequest.oicIncidentId} for offender no ${adjudicationRequest.prisoner.prisonerNumber}. Request was $adjudicationRequest")
    return MigrateResponse(
      chargeNumberMapping = ChargeNumberMapping(
        "${adjudicationRequest.oicIncidentId}/${adjudicationRequest.offenceSequence}",
        oicIncidentId = adjudicationRequest.oicIncidentId,
        offenceSequence = adjudicationRequest.offenceSequence,
      ),
      hearingMappings = emptyList(),
      punishmentMappings = emptyList(),
    )
  }
}
