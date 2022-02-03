package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying what should be migrated from NOMIS to Visits service")
data class VisitsMigrationFilter(
  @Schema(description = "List of prison Ids (AKA Agency Ids) to migrate visits from", example = "MDI", required = true)
  val prisonIds: List<String> = emptyList(),

  @Schema(
    description = "List of visit types to migrate",
    example = "SCON",
    allowableValues = ["SCON", "OFFI"],
    defaultValue = "SCON"
  )
  val visitTypes: List<String> = listOf("SCON"),

  @Schema(
    description = "Only include visits created after this date. NB this is creation date not the actual visit date",
    example = "2020-03-23T12:00:00",
  )
  val fromDateTime: LocalDateTime? = null,

  @Schema(
    description = "Only include visits created before this date. NB this is creation date not the actual visit date",
    example = "2020-03-24T12:00:00",
  )
  val toDateTime: LocalDateTime? = null,
)
