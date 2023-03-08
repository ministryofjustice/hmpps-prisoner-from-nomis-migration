package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class LatestMigration(@JsonProperty("label") val migrationId: String)
data class MigrationDetails(
  @JsonProperty("totalElements") val count: Long,
  val content: List<MigratedItem>,
) {
  val startedDateTime: LocalDateTime?
    get() = content.firstOrNull()?.whenCreated
}

data class MigratedItem(
  val whenCreated: LocalDateTime,
)
