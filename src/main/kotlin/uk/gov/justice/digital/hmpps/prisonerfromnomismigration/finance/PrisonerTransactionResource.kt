package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus.OK
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCases
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException

@RestController
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
@Tag(name = "Finance Migration Resource")
class PrisonerTransactionResource(
  private val service: TransactionSynchronisationService,
) {
  @GetMapping("/sync/prisoner-transaction/payload/{transactionId}")
  @ResponseStatus(value = OK)
  @Operation(
    summary = "Provides the sync payload for debug purposes",
    description = """
      Provides the payload for a prisoner transaction sync, no synchronisation to Dps is performed. Useful for investigating sync errors. 
      Requires role <b>PRISONER_FROM_NOMIS__MIGRATION__RW</b>
      """,
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Migration payload returned",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = MigrationCreateCourtCases::class),
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
        description = "Incorrect permissions to call endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun getPrisonerTransactionSyncPayload(
    @Schema(description = "transaction id", example = "1234")
    @PathVariable transactionId: Long,
  ) = try {
    service.getPrisonerTransactionSyncPayload(transactionId)
  } catch (_: NotFound) {
    throw NotFoundException("No prisoner transaction for $transactionId was found")
  }
}
