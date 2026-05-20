package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter to allow initial migration testing with 1 offender")
data class CourtSentencingMigrationFilter(

  val offenderNo: String? = null,

  // only offender no is used to filter - TODO remove dates
  @Schema(
    description = "Only include court cases created on or after this date",
    example = "2020-03-23",
  )
  val fromDate: LocalDate? = null,

  @Schema(
    description = "Only include court cases created before or on this date",
    example = "2020-03-24",
  )
  val toDate: LocalDate? = null,

  val deleteExisting: Boolean = false,
)
