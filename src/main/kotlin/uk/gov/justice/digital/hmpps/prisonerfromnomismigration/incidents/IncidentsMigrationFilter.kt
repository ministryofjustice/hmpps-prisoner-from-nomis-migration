package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying what should be migrated from NOMIS to the Incident Reporting service")
data class IncidentsMigrationFilter(

  @Schema(
    description = "Only include incidents created on or after this date",
    example = "2020-03-23",
  )
  val fromDate: LocalDate? = null,

  @Schema(
    description = "Only include incidents created before or on this date",
    example = "2020-03-24",
  )
  val toDate: LocalDate? = null,
)
