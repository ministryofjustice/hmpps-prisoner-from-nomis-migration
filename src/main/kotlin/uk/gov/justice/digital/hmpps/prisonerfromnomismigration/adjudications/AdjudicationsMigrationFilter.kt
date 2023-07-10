package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying what should be migrated from NOMIS to the Adjudications service")
data class AdjudicationsMigrationFilter(

  @Schema(
    description = "Only include adjudications created on or after this date",
    example = "2020-03-23",
  )
  val fromDate: LocalDate? = null,

  @Schema(
    description = "Only include adjudications created before or on this date",
    example = "2020-03-24",
  )
  val toDate: LocalDate? = null,

  @Schema(
    description = "Only include adjudications for these prison ids",
    example = "['MDI','LEI']",
  )
  val prisonIds: List<String> = emptyList(),
)
