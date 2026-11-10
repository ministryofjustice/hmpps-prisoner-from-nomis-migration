package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Size

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying who should be migrated from NOMIS to DPS")
data class PrisonerMigrationFilter(
  @field:Size(min = 1)
  @Schema(description = "Only migrate these specific prisoners", example = "[\"A1234AA\", \"B1234BB\"]")
  val offenderNos: List<String>? = null,
) {
  @get:Schema(hidden = true)
  @get:JsonIgnore
  @get:AssertTrue(message = "offenderNos must contain valid prisoner numbers")
  val isOffenderNosValid: Boolean
    get() = offenderNos?.all { it.matches(Regex("^[A-Z][0-9]{4}[A-Z]{2}$")) } ?: true
}
