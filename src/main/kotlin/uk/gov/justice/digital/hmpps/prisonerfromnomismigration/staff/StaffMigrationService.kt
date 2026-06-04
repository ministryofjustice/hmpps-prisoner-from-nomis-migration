package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.StaffMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseloadResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RoleResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffAccount
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastIdMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationDivision
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.collections.flatMap

@Service
class StaffMigrationService(
  private val staffMappingService: StaffMappingApiService,
  private val nomisApiService: StaffNomisApiService,
  private val dpsApiService: StaffDpsApiService,
  jsonMapper: JsonMapper,
  @Value($$"${staff.page.size:1000}") pageSize: Long,
  @Value($$"${staff.parallel.count:8}") getIdsParallelCount: Int,
  @Value($$"${staff.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${staff.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${staff.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByLastIdMigrationService<StaffMigrationFilter, StaffIdResponse, StaffMappingDto>(
  staffMappingService,
  MigrationType.STAFF,
  pageSize = pageSize,
  getIdsParallelCount = getIdsParallelCount,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckRetrySeconds,
  completeCheckRetrySeconds = completeCheckCount,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getPageOfIdsFromId(
    lastId: StaffIdResponse?,
    migrationFilter: StaffMigrationFilter,
    pageSize: Long,
  ): List<StaffIdResponse> = nomisApiService.getStaffIdsFromId(
    lastStaffId = lastId?.staffId ?: 0,
    pageSize = pageSize,
  ).ids

  override fun compare(
    first: StaffIdResponse,
    second: StaffIdResponse?,
  ): Int = first.staffId.compareTo(second?.staffId ?: Long.MAX_VALUE)

  override suspend fun getPageOfIds(
    migrationFilter: StaffMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<StaffIdResponse> = nomisApiService.getStaffIds(
    pageNumber = pageNumber,
    pageSize = pageSize,
  ).content!!

  override suspend fun getTotalNumberOfIds(migrationFilter: StaffMigrationFilter): Long = nomisApiService.getStaffIds(
    pageNumber = 0,
    pageSize = 1,
  ).page?.totalElements!!

  override suspend fun migrateNomisEntity(context: MigrationContext<StaffIdResponse>) {
    val nomisStaffId = context.body.staffId
    val alreadyMigratedMapping = staffMappingService.getByNomisStaffIdOrNull(
      nomisStaffId = nomisStaffId,
    )

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the nomis staff id=$nomisStaffId since it was already mapped to DPS staff $dpsId during migration $label")
    } ?: run {
      val nomisStaff = nomisApiService.getStaffDetails(staffId = nomisStaffId)
      val dpsStaff = dpsApiService.migrateStaff(nomisStaff.toMigrateStaffRequest())

      val mapping = StaffMappingDto(
        dpsId = dpsStaff.userId.toString(),
        nomisId = nomisStaffId,
        mappingType = StaffMappingDto.MappingType.MIGRATED,
        label = context.migrationId,
      )
      createMappingOrOnFailureDo(context, mapping) {
        queueService.sendMessage(
          MigrationMessageType.RETRY_MIGRATION_MAPPING,
          MigrationContext(
            context = context,
            body = mapping,
          ),
        )
      }
    }
  }

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: StaffMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      mappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<StaffMappingDto>>() {},
      )
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "staff-migration-duplicate",
          mapOf(
            "duplicateDpsId" to duplicateErrorDetails.duplicate.dpsId,
            "duplicateNomisId" to duplicateErrorDetails.duplicate.nomisId,
            "existingDpsId" to duplicateErrorDetails.existing.dpsId,
            "existingNomisId" to duplicateErrorDetails.existing.nomisId,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "staff-migration-entity-migrated",
          mapOf(
            "nomisId" to mapping.nomisId,
            "dpsId" to mapping.dpsId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }

  override suspend fun retryCreateMapping(context: MigrationContext<StaffMappingDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, StaffMigrationFilter> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<StaffMigrationFilter, ByLastId<StaffIdResponse>>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, StaffIdResponse> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, StaffMappingDto> = jsonMapper.readValue(json)

  override fun parseContextDivisionFilter(json: String): MigrationMessage<*, MigrationDivision<StaffMigrationFilter, StaffIdResponse>> = jsonMapper.readValue(json)
}

suspend fun StaffDetails.toMigrateStaffRequest() = this.toDpsMigrateStaffRequest()
private fun StaffDetails.toDpsMigrateStaffRequest(): UserMigrationRequestDps = UserMigrationRequestDps(
  user = UserDps(
    id = id,
    email = email,
    firstName = firstName,
    lastName = lastName,
    status = UserStatusDps.valueOf(status),
    createdTimestamp = audit.createDatetime.toOffsetDateTime(),
    createdBy = audit.createUsername,
    modifiedTimestamp = audit.modifyDatetime?.toOffsetDateTime(),
    modifiedBy = audit.modifyUserId,
  ),
  accounts = accounts.map { it.toDpsUserAccount() },
  // n.b. only roles for staff migration are roles on the NWEB (DPS) caseload - only these are returned from Nomis
  roles = accounts.flatMap { staffUserAccount ->
    staffUserAccount.caseloads.flatMap { caseload ->
      caseload.roles.map { it.toDpsRole(staffUserAccount.username) }
    }
  },
  accessibleCaseloads = accounts.flatMap { staffUserAccount ->
    staffUserAccount.caseloads.map {
      it.toDpsUserAccessibleCaseload(staffUserAccount.username)
    }
  },
)

private fun StaffAccount.toDpsUserAccount() = UserAccountDps(
  username = username,
  accountType = AccountTypeDps.valueOf(typeCode),
  accountStatus = AccountStatusDps.valueOf(status),
  activeCaseloadId = activeCaseloadId,
  lastLoggedIn = lastLoggedIn,
  createDateTime = audit.createDatetime.toOffsetDateTime(),
  createdBy = audit.createUsername,
  lastModifiedDateTime = audit.modifyDatetime?.toOffsetDateTime(),
  lastModifiedBy = audit.modifyUserId,
)

private fun RoleResponse.toDpsRole(username: String) = UserRoleDps(
  username = username,
  roleCode = code,
  createdTimestamp = audit.createDatetime.toOffsetDateTime(),
  createdBy = audit.createUsername,
)

private fun CaseloadResponse.toDpsUserAccessibleCaseload(username: String) = UserAccessibleCaseloadDps(
  username = username,
  caseloadId = caseloadId,
  createdTimestamp = audit.createDatetime.toOffsetDateTime(),
  createdBy = audit.createUsername,
)

fun LocalDateTime.toOffsetDateTime(): OffsetDateTime = atZone(ZoneId.of("Europe/London")).toOffsetDateTime()
