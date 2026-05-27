package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse

@RestController
@Tag(name = "Visit Slots Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
class VisitSlotsRepairResource(
  private val visitSlotsService: VisitSlotsSynchronisationService,
) {

  @PostMapping("/visits/configuration/time-slots/prison-id/{prisonId}/day-of-week/{dayOfWeek}/time-slot-sequence/{timeSlotSequence}")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create a time slot in DPS from the slot in NOMIS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Visit slot created",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Visit mapping already exists",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint. Requires ROLE_NOMIS_PRISONER_API__SYNCHRONISATION__RW",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun createTimeSlot(
    @PathVariable prisonId: String,
    @PathVariable dayOfWeek: String,
    @PathVariable timeSlotSequence: Int,
  ) = visitSlotsService.createVisitTimeslot(
    prisonTd = prisonId,
    dayOfWeek = dayOfWeek,
    timeSlotSequence = timeSlotSequence,
  )

  @PostMapping("/visits/configuration/time-slots/prison-id/{prisonId}/day-of-week/{dayOfWeek}/time-slot-sequence/{timeSlotSequence}/visit-slot-id/{visitSlotId}")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create a visit slot in DPS from the slot in NOMIS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Visit slot created",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Visit slot mapping already exists",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint. Requires ROLE_NOMIS_PRISONER_API__SYNCHRONISATION__RW",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun createVisitSlot(
    @PathVariable prisonId: String,
    @PathVariable dayOfWeek: String,
    @PathVariable timeSlotSequence: Int,
    @PathVariable visitSlotId: Long,
  ) = visitSlotsService.createVisitSlot(
    prisonTd = prisonId,
    dayOfWeek = dayOfWeek,
    timeSlotSequence = timeSlotSequence,
    visitSlotId = visitSlotId,
  )
}
