package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import java.time.LocalDateTime

@RestController
@Tag(name = "Visit Migration Resource")
@RequestMapping("/migrate", produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitMigrationResource(
  private val visitsMigrationService: VisitsMigrationService,
) {
  @PreAuthorize("hasRole('ROLE_MIGRATE_VISITS')")
  @PostMapping("/visits")
  @ResponseStatus(value = ACCEPTED)
  @Operation(
    summary = "Starts a visit migration",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously. Requires role <b>MIGRATE_VISITS</b>",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = VisitsMigrationFilter::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "202",
        description = "Migration process started",
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
        responseCode = "409",
        description = "Migration already in progress",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun migrateVisits(
    @RequestBody @Valid
    migrationFilter: VisitsMigrationFilter,
  ) = visitsMigrationService.startMigration(migrationFilter)

  @PreAuthorize("hasRole('ROLE_MIGRATE_VISITS')")
  @GetMapping("/visits/rooms/usage")
  @Operation(
    summary = "get visit room usage and mappings by filter",
    description = "Retrieves a list of rooms with usage count and vsip mapping for the (filtered) visits",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "list of visit room and count is returned",
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
    ],
  )
  suspend fun getVisitRoomUsageDetailsByFilter(
    @RequestParam(value = "prisonIds")
    @Parameter(
      description = "Filter results by prison ids (returns all prisons if not specified)",
      example = "['MDI','LEI']",
    )
    prisonIds: List<String>?,
    @RequestParam(value = "visitTypes")
    @Parameter(
      description = "Filter results by visitType (returns all types if not specified)",
      example = "['SCON','OFFI']",
    )
    visitTypes: List<String>?,
    @RequestParam(value = "fromDateTime")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filter results by visits that start on or after the given timestamp",
      example = "2021-11-03T09:00:00",
    )
    fromDateTime: LocalDateTime?,
    @RequestParam(value = "toDateTime")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Parameter(
      description = "Filter results by visits that start on or before the given timestamp",
      example = "2021-11-03T09:00:00",
    )
    toDateTime: LocalDateTime?,
  ): List<VisitRoomUsageResponse> = visitsMigrationService.findRoomUsageByFilter(
    VisitsMigrationFilter(
      visitTypes = visitTypes ?: listOf(),
      prisonIds = prisonIds ?: listOf(),
      toDateTime = toDateTime,
      fromDateTime = fromDateTime,
      // not used
      ignoreMissingRoom = false,
    ),
  )
}
