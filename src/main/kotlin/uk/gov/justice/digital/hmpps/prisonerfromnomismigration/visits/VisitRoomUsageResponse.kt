package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Visit room usage and vsip mapping")
data class VisitRoomUsageResponse(
  @Schema(description = "The nomis internal location description")
  val agencyInternalLocationDescription: String,
  @Schema(description = "room usage count")
  val count: Long,
  @Schema(description = "VSIP room mapping")
  val vsipRoom: String?,
  @Schema(description = "The nomis prison id")
  val prisonId: String,
)
