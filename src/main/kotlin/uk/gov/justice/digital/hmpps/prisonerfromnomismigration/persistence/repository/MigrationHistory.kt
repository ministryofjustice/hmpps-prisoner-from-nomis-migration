package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.STARTED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.time.LocalDateTime

data class MigrationHistory(
  @Id val migrationId: String,
  val whenStarted: LocalDateTime = LocalDateTime.now(),
  var whenEnded: LocalDateTime? = null,
  val estimatedRecordCount: Long,
  val filter: String? = null,
  var recordsMigrated: Long = 0,
  var recordsFailed: Long = 0,
  val synchronisationType: SynchronisationType,
  var status: MigrationStatus = STARTED,
  @Transient
  @Value("false")
  @JsonIgnore
  val new: Boolean = true,
) : Persistable<String> {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MigrationHistory) return false

    if (migrationId != other.migrationId) return false

    return true
  }

  override fun hashCode(): Int {
    return migrationId.hashCode()
  }

  override fun isNew(): Boolean = new

  override fun getId(): String = migrationId
}
