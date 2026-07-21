package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.PrisonUserSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationResponse
import java.util.UUID

@RestController
@PreAuthorize("hasRole('ROLE_PRISON_USER_STAFF__SYNC__RW')")
class DummyStaffDpsApi {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PostMapping("/migrate/user")
  @ResponseStatus(value = HttpStatus.CREATED)
  suspend fun migrateStaff(@RequestBody @Valid staff: UserMigrationRequest): UserMigrationResponse = UserMigrationResponse(
    userId = UUID.randomUUID(),
    staffId = staff.user.staffId,
  )
    .also {
      log.info("Migrated staff ${staff.user.staffId}")
    }

  @PutMapping("/sync/user/{legacyStaffId}")
  @ResponseStatus(value = HttpStatus.OK)
  suspend fun syncStaff(@PathVariable legacyStaffId: Long, @RequestBody @Valid request: PrisonUserSyncRequest) {
    log.info("Upserted staff $legacyStaffId; user ${request.firstName} ${request.lastName}")
  }

  @DeleteMapping("/prison-users/staff/{nomisStaffId}")
  @ResponseStatus(value = HttpStatus.NO_CONTENT)
  suspend fun deletetaff(@PathVariable nomisStaffId: Long) {
    log.info("Deleted staff $nomisStaffId")
  }
}
