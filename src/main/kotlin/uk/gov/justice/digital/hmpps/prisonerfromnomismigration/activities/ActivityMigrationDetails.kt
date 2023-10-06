package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.fasterxml.jackson.annotation.JsonProperty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ActivityMigrationMappingDto

data class ActivityMigrationDetails(
  @JsonProperty("totalElements") val count: Long,
  val content: List<ActivityMigrationMappingDto>,
)
