package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * This represents the possible interface for the CSIP api service.
 * This can be deleted once the real service is available.
 */
@RestController
class MockCSIPResource {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('ROLE_MIGRATE_CSIP')")
  @PostMapping("/csip/migrate")
  @Operation(hidden = true)
  suspend fun createCSIPForMigration(
    @RequestBody @Valid
    csipRequest: CSIPMigrateRequest,
  ): CSIPMigrateResponse {
    log.info("Created incident for migration with id ${csipRequest.nomisCSIPId} ")
    return CSIPMigrateResponse("DPS-${csipRequest.nomisCSIPId}")
  }
}

data class CSIPMigrateRequest(
  val nomisCSIPId: Long,
  val referralSummary: String?,
)

data class CSIPMigrateResponse(
  val dpsCSIPId: String,
)