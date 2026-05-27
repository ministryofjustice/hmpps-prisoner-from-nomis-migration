package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying what should be migrated from NOMIS to Appointments service")
data class AppointmentsMigrationFilter(

  @Schema(
    description = "Only include appointments on or after this date",
    example = "2020-03-23",
  )
  val fromDate: LocalDate? = null,

  @Schema(
    description = "Only include appointments before or on this date",
    example = "2020-03-24",
  )
  val toDate: LocalDate? = null,

  @Schema(
    description = "Only include appointments for these prison ids",
    example = "['MDI','LEI']",
  )
  val prisonIds: List<String> = emptyList(),
)
