package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.CreateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.Prisoner

/**
 * This represents the possible interface for the CorePerson api service.
 * This can be deleted once the real service is available.
 */
@RestController
class MockCorePersonResource {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('ROLE_MIGRATE_CORE_PERSON')")
  @PutMapping("/syscon-sync/{nomisPrisonNumber}")
  @Operation(hidden = true)
  suspend fun migrateCorePerson(
    @Schema(description = "Nomis prison number", example = "A1234BC", required = true)
    @PathVariable
    nomisPrisonNumber: String,
    @RequestBody @Valid
    prisoner: Prisoner,
  ): CreateResponse {
    log.info("Created core person for migration with nomis prison number $nomisPrisonNumber ")
    return CreateResponse(
      addressIds = emptyList(),
      phoneIds = emptyList(),
      emailIds = emptyList(),
    )
  }
}
