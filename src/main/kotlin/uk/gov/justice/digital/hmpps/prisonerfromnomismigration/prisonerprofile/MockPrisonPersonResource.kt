package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonerprofile

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.UpdatePhysicalAttributesRequest

/**
 * TODO SDIT-1816 This represents the possible interface for the Prion Person sync API. This can be deleted once the real service is available.
 */
@RestController
class MockPrisonPersonResource {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('ROLE_PRISON_PERSON_API__PHYSICAL_ATTRIBUTES_SYNC__RW')")
  @PostMapping("/sync/prisoners/{prisonerNumber}/physical-attributes")
  @Operation(hidden = true)
  suspend fun syncPhysicalAttributes(
    @PathVariable prisonerNumber: String,
    @RequestBody @Valid request: UpdatePhysicalAttributesRequest,
  ): PhysicalAttributesDto =
    PhysicalAttributesDto(request.height, request.weight)
      .also {
        log.info("Synchronised physical attributes for $prisonerNumber with body: $request")
      }
}
