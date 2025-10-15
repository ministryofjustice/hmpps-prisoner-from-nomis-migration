package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse

@RestController
@RequestMapping("/sync/contactperson/profile-details", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Contact Person Migration Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
class ContactPersonProfileDetailsResource(
  private val syncService: ContactPersonProfileDetailsSyncService,
) {
  @PutMapping("/{prisonerNumber}/{profileType}")
  @Operation(
    summary = "Synchronises a profile detail to DPS",
    description = "Manually synchronises a profile detail to DPS. This is intended for use by developers to recover from errors. Requires role <b>PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Profile detail synchronised to DPS",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to start migration",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Found nothing to synchronise",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun syncContactPersonProfileDetail(
    @PathVariable prisonerNumber: String,
    @PathVariable profileType: ContactPersonProfileType,
  ) = runCatching {
    syncService.profileDetailsChanged(offenderNo = prisonerNumber, profileType = profileType)
  }.onFailure { e ->
    "Failed to sync profile details for $prisonerNumber/$profileType due to ${e.message}"
      .also {
        log.error(it, e)
        throw BadRequestException(it)
      }
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
