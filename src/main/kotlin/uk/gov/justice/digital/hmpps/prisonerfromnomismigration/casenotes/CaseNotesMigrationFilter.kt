package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Filter specifying what should be migrated from NOMIS to the Case notes service")
data class CaseNotesMigrationFilter(

  @Schema(
    description = "Only include casenotes of these specific prisoners",
    example = "A1234AA,B1234BB",
  )
  val offenderNos: List<String>? = null,
)
