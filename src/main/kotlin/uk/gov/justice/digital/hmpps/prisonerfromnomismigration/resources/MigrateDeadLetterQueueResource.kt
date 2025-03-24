package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.resources

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import kotlinx.coroutines.future.await
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.hmpps.sqs.GetDlqRequest
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

@RestController
@RequestMapping("/migrate/dead-letter-queue", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
class MigrateDeadLetterQueueResource(
  private val hmppsQueueService: HmppsQueueService,
) {
  @GetMapping("/{migrationType}")
  @Operation(
    summary = "Gets all (max 100) the dlq messages for the specified migration type",
    description = "Requires role <b>ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "All dlq messages for specified migration type",
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = MigrationHistory::class)),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun getDlqMessages(
    @PathVariable @Schema(description = "Migration Type") migrationType: MigrationType,
  ) = migrationType.getQueue().run {
    hmppsQueueService.getDlqMessages(GetDlqRequest(this, 100))
  }

  @GetMapping("/{migrationType}/count")
  @Operation(
    summary = "Gets count of the dlq messages for the specified migration type",
    description = "Requires role <b>ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Count of dlq messages for specified migration type",
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = MigrationHistory::class)),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun getDlqMessagesCount(
    @PathVariable @Schema(description = "Migration Type") migrationType: MigrationType,
  ) = migrationType.getQueue().run {
    this.sqsDlqClient!!.countMessagesOnQueue(this.dlqUrl!!).await()
  }

  @DeleteMapping("/{migrationType}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Deletes all the dlq messages for the specified migration type",
    description = "Requires role <b>ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    responses = [
      ApiResponse(
        responseCode = "204",
        description = "All dlq messages have been deleted for specified migration type",
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = MigrationHistory::class)),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun deleteDlqMessages(
    @PathVariable @Schema(description = "Migration Type") migrationType: MigrationType,
  ) = migrationType.getQueue().run {
    hmppsQueueService.purgeQueue(PurgeQueueRequest(this.dlqName!!, this.sqsDlqClient!!, this.dlqUrl!!))
  }

  private fun MigrationType.getQueue() = hmppsQueueService.findByQueueId(this.queueId)!!
}
