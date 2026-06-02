package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Service
class StaffDpsApiService(
  @Qualifier("staffApiWebClient") private val webClient: WebClient,
) {
  suspend fun migrateStaff(userMigrationRequest: UserMigrationRequestDps): UserMigrationResponseDps = webClient.post()
    .uri("/prison-users/migrate/staff")
    .bodyValue(userMigrationRequest)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}

data class UserMigrationRequestDps(
  val user: UserDps,
  val accounts: List<UserAccountDps>,
  val roles: List<UserRoleDps>? = null,
  val accessibleCaseloads: List<UserAccessibleCaseloadDps>? = null,
)

data class UserDps(
  val id: Long, // staffId?
  val email: String? = null,
  val firstName: String,
  val lastName: String,
  val status: UserStatusDps,
  val createdTimestamp: OffsetDateTime,
  val createdBy: String,
  val modifiedTimestamp: OffsetDateTime? = null,
  val modifiedBy: String? = null,
)

enum class UserStatusDps {
  ACTIVE,
  INACTIVE,
}

data class UserAccountDps(
  val username: String,
  val accountType: AccountTypeDps,
  val accountStatus: AccountStatusDps,
  val lastLoggedIn: LocalDateTime? = null,
  val activeCaseloadId: String? = null,
  val createDateTime: OffsetDateTime,
  val createdBy: String,
  val lastModifiedDateTime: OffsetDateTime?,
  val lastModifiedBy: String?,
)

enum class AccountTypeDps {
  ADMIN,
  GENERAL,
}

enum class AccountStatusDps {
  OPEN,
  EXPIRED,
  EXPIRED_GRACE,
  LOCKED_TIMED,
  LOCKED,
  EXPIRED_LOCKED_TIMED,
  EXPIRED_GRACE_LOCKED_TIMED,
  EXPIRED_LOCKED,
  EXPIRED_GRACE_LOCKED,
}

data class UserRoleDps(
  val username: String,
  val roleCode: String,
  val createdTimestamp: OffsetDateTime,
  val createdBy: String,
)

data class UserAccessibleCaseloadDps(
  val username: String,
  val caseloadId: String,
  val createdTimestamp: OffsetDateTime,
  val createdBy: String,
)

data class UserMigrationResponseDps(
  val userId: UUID? = null,
  val staffId: String? = null,
  val username: List<String>? = null,
)

data class ErrorResponseDps(
  val status: Int,
  val errorCode: Int? = null,
  val userMessage: String? = null,
  val developerMessage: String? = null,
)
