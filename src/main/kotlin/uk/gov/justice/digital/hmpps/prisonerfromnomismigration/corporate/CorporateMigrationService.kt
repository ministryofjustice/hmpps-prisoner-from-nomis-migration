package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.corporate

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.IdPair
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateOrganisationAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateOrganisationEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateOrganisationPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateOrganisationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateOrganisationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateOrganisationWebAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@Service
class CorporateMigrationService(
  val mappingApiService: CorporateMappingApiService,
  val nomisApiService: CorporateNomisApiService,
  val dpsApiService: CorporateDpsApiService,
  @Value("\${corporate.page.size:1000}") pageSize: Long,
  @Value("\${corporate.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${corporate.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value("\${corporate.complete-check.count}") completeCheckCount: Int,
  @Value("\${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : MigrationService<CorporateMigrationFilter, CorporateOrganisationIdResponse, CorporateMappingsDto>(
  mappingService = mappingApiService,
  migrationType = MigrationType.CORPORATE,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: CorporateMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<CorporateOrganisationIdResponse> = nomisApiService.getCorporateOrganisationIdsToMigrate(
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<CorporateOrganisationIdResponse>) {
    val nomisCorporateId = context.body.corporateId
    val alreadyMigratedMapping = mappingApiService.getByNomisCorporateIdOrNull(nomisCorporateId)

    alreadyMigratedMapping?.run {
      log.info("Will not migrate the nomis corporate=$nomisCorporateId since it was already mapped to DPS organisation ${this.dpsId} during migration ${this.label}")
    } ?: run {
      val corporateOrganisation = nomisApiService.getCorporateOrganisation(nomisCorporateId = context.body.corporateId)
      val mapping = dpsApiService.migrateOrganisation(corporateOrganisation.toDpsMigrateOrganisationRequest()).toCorporateMappingsDto(context.migrationId).also {
        // while DPS build their API - lets log everything we send - TODO - remove once API is there
        log.debug("Created DPS migrate organisation request: {}", it)
      }
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

  override suspend fun retryCreateMapping(context: MigrationContext<CorporateMappingsDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: CorporateMappingsDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      mappingApiService.createMappingsForMigration(mapping)
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-corporate-duplicate",
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
          "corporate-migration-entity-migrated",
          mapOf(
            "nomisId" to mapping.corporateMapping.nomisId,
            "dpsId" to mapping.corporateMapping.dpsId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }
}

fun CorporateOrganisation.toDpsMigrateOrganisationRequest(): MigrateOrganisationRequest = MigrateOrganisationRequest(
  nomisCorporateId = id,
  organisationName = name,
  active = active,
  caseloadId = caseload?.code,
  vatNumber = vatNumber,
  programmeNumber = programmeNumber,
  comments = comment,
  organisationTypes = types.map { organisationType ->
    MigrateOrganisationType(
      type = organisationType.type.code,
      createDateTime = organisationType.audit.createDatetime.toDateTime(),
      createUsername = organisationType.audit.createUsername,
      modifyDateTime = organisationType.audit.modifyDatetime.toDateTime(),
      modifyUsername = organisationType.audit.modifyUserId,
    )
  },
  phoneNumbers = phoneNumbers.map { phone ->
    MigrateOrganisationPhoneNumber(
      nomisPhoneId = phone.id,
      number = phone.number,
      extension = phone.extension,
      type = phone.type.code,
      createDateTime = phone.audit.createDatetime.toDateTime(),
      createUsername = phone.audit.createUsername,
      modifyDateTime = phone.audit.modifyDatetime.toDateTime(),
      modifyUsername = phone.audit.modifyUserId,
    )
  },

  addresses = this.addresses.map {
    MigrateOrganisationAddress(
      nomisAddressId = it.id,
      type = it.type?.code,
      flat = it.flat,
      premise = it.premise,
      street = it.street,
      locality = it.locality,
      postCode = it.postcode,
      city = it.city?.code,
      county = it.county?.code,
      country = it.country?.code,
      noFixedAddress = it.noFixedAddress ?: false,
      primaryAddress = it.primaryAddress,
      mailAddress = it.mailAddress,
      comment = it.comment,
      startDate = it.startDate,
      endDate = it.endDate,
      serviceAddress = it.isServices,
      contactPersonName = it.contactPersonName,
      businessHours = it.businessHours,
      phoneNumbers = it.phoneNumbers.map { phone ->
        MigrateOrganisationPhoneNumber(
          nomisPhoneId = phone.id,
          number = phone.number,
          extension = phone.extension,
          type = phone.type.code,
          createDateTime = phone.audit.createDatetime.toDateTime(),
          createUsername = phone.audit.createUsername,
          modifyDateTime = phone.audit.modifyDatetime.toDateTime(),
          modifyUsername = phone.audit.modifyUserId,
        )
      },
      createDateTime = it.audit.createDatetime.toDateTime(),
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime.toDateTime(),
      modifyUsername = it.audit.modifyUserId,
    )
  },
  emailAddresses = this.internetAddresses.filter { it.type == "EMAIL" }.map {
    MigrateOrganisationEmailAddress(
      nomisEmailAddressId = it.id,
      email = it.internetAddress,
      createDateTime = it.audit.createDatetime.toDateTime(),
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime.toDateTime(),
      modifyUsername = it.audit.modifyUserId,
    )
  },
  webAddresses = this.internetAddresses.filter { it.type == "WEB" }.map {
    MigrateOrganisationWebAddress(
      nomisWebAddressId = it.id,
      webAddress = it.internetAddress,
      createDateTime = it.audit.createDatetime.toDateTime(),
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime.toDateTime(),
      modifyUsername = it.audit.modifyUserId,
    )
  },
  createDateTime = this.audit.createDatetime.toDateTime(),
  createUsername = this.audit.createUsername,
  modifyDateTime = this.audit.modifyDatetime.toDateTime(),
  modifyUsername = this.audit.modifyUserId,
)

private fun MigrateOrganisationResponse.toCorporateMappingsDto(migrationId: String) = CorporateMappingsDto(
  mappingType = CorporateMappingsDto.MappingType.MIGRATED,
  label = migrationId,
  corporateMapping = organisation.toCorporateMappingIdDto(),
  corporateAddressMapping = addresses.map { it.address.toCorporateMappingIdDto() },
  corporateAddressPhoneMapping = addresses.flatMap { address -> address.phoneNumbers.map { it.toCorporateMappingIdDto() } },
  corporatePhoneMapping = phoneNumbers.map { it.toCorporateMappingIdDto() },
  corporateEmailMapping = emailAddresses.map { it.toCorporateMappingIdDto() },
  corporateWebMapping = webAddresses.map { it.toCorporateMappingIdDto() },
)

private fun IdPair.toCorporateMappingIdDto() = CorporateMappingIdDto(dpsId = this.dpsId.toString(), nomisId = this.nomisId)
private fun String?.toDateTime() = this?.let { java.time.LocalDateTime.parse(it) }
