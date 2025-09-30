package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Sentencing Migration Resource")
class SentencingAdjustmentsDataRepairResource(
  private val sentencingAdjustmentsSynchronisationService: SentencingAdjustmentsSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/prisoners/booking-id/{bookingId}/merge/sentencing-adjustments/repair")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_MIGRATE_SENTENCING')")
  @Operation(
    summary = "Resynchronises new adjustments for the given booking from NOMIS back to DPS",
    description = "Used when a merge has not be detected so new adjustments have not been copied to DPS, so emergency use only. Requires ROLE_MIGRATE_SENTENCING",
  )
  suspend fun repairPostMergeAdjustments(
    @PathVariable bookingId: Long,
  ) {
    sentencingAdjustmentsSynchronisationService.repairPostMergeAdjustments(bookingId = bookingId)
    telemetryClient.trackEvent(
      "from-nomis-synch-adjustment-merge-repair",
      mapOf(
        "bookingId" to bookingId.toString(),
      ),
      null,
    )
  }
}
