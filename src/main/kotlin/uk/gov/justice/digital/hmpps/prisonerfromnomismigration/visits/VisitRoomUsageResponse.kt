package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Visit id")
data class VisitRoomUsageResponse(
  @Schema(description = "The internal location description", required = true)
  val agencyInternalLocationDescription: String,
  val count: Long,
  val vsipRoom: String?,
  val prisonId: String
)
