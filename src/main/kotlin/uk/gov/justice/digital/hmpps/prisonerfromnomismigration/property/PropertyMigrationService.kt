package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PropertyContainerCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PropertyContainerGetResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.model.SyncPropertyContainerRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes
import java.util.UUID

@Service
class PropertyMigrationService(
  private val nomisApiService: PropertyNomisApiService,
  private val propertyDpsApiService: PropertyDpsApiService,
  private val propertyMappingService: PropertyMappingService,
  jsonMapper: JsonMapper,
  @Value($$"${property.page.size:1000}") pageSize: Long,
  @Value($$"${property.complete-check.delay-seconds:10}") completeCheckDelaySeconds: Int,
  @Value($$"${property.complete-check.retry-seconds:10}") completeCheckRetrySeconds: Int,
  @Value($$"${property.complete-check.count:9}") completeCheckCount: Int,
) : ByPageNumberMigrationService<PropertyMigrationFilter, PropertyContainerIdResponse, PropertyContainerMappingDto>(
  mappingService = propertyMappingService,
  migrationType = MigrationType.PROPERTY,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckCount = completeCheckCount,
  jsonMapper = jsonMapper,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getIds(
    migrationFilter: PropertyMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PropertyContainerIdResponse> = nomisApiService.getPropertyContainerIds(
    pageNumber = pageNumber,
    pageSize = pageSize,
    prisonIds = migrationFilter.prisonIds,
  ).let {
    PageImpl(
      it.content!!.map { a ->
        PropertyContainerIdResponse(a.containerId)
      },
      PageRequest.of(pageNumber.toInt(), pageSize.toInt()),
      it.totalElements!!,
    )
  }

  override suspend fun getPageOfIds(
    migrationFilter: PropertyMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<PropertyContainerIdResponse> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: PropertyMigrationFilter): Long = getIds(
    migrationFilter,
    1,
    0,
  ).totalElements

  override suspend fun migrateNomisEntity(context: MigrationContext<PropertyContainerIdResponse>) {
    log.info("attempting to migrate ${context.body}")
    val nomisId = context.body.propertyContainerId

    try {
      propertyMappingService.getMappingByNomisId(nomisId)
        ?.run {
          log.info("Will not migrate the container since it is migrated already, NOMIS id is $nomisId, dpsPropertyContainerId is ${this.dpsPropertyContainerId} as part migration ${this.label ?: "NONE"} (${this.mappingType})")
        }
        ?: run {
          val container = nomisApiService.getPropertyContainer(nomisId)

          propertyDpsApiService.migrate(container.toProperty())
            .also {
              createPropertyMapping(
                nomisPropertyContainerId = nomisId,
                dpsId = it.dpsId,
                bookingId = container.bookingId,
                offenderNo = container.offenderNo,
                context = context,
              )

              telemetryClient.trackEvent(
                "property-migration-entity-migrated",
                mapOf(
                  "nomisId" to nomisId,
                  "dpsId" to it.dpsId,
                  "migrationId" to context.migrationId,
                ),
              )
            }
        }
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "property-migration-entity-migrate-failed",
        mapOf(
          "nomisId" to nomisId,
          "migrationId" to context.migrationId,
          "error" to (e.message ?: "unknown error"),
        ),
      )
      throw e
    }
  }

  private suspend fun createPropertyMapping(
    nomisPropertyContainerId: Long,
    dpsId: UUID,
    bookingId: Long,
    offenderNo: String,
    context: MigrationContext<*>,
  ) = try {
    propertyMappingService.createMapping(
      PropertyContainerMappingDto(
        nomisPropertyContainerId = nomisPropertyContainerId,
        dpsPropertyContainerId = dpsId.toString(),
        label = context.migrationId,
        mappingType = PropertyContainerMappingDto.MappingType.MIGRATED,
        bookingId = bookingId,
        offenderNo = offenderNo,
      ),
      object : ParameterizedTypeReference<DuplicateErrorResponse<PropertyContainerMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "property-nomis-migration-duplicate",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "duplicateDpsId" to duplicateErrorDetails.duplicate.dpsPropertyContainerId,
            "duplicateNomisId" to duplicateErrorDetails.duplicate.nomisPropertyContainerId.toString(),
            "existingDpsId" to duplicateErrorDetails.existing.dpsPropertyContainerId,
            "existingNomisId" to duplicateErrorDetails.existing.nomisPropertyContainerId.toString(),
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      }
    }
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for nomisPropertyContainerId: $nomisPropertyContainerId, dpsPropertyContainerId $dpsId",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = PropertyContainerMappingDto(
          nomisPropertyContainerId = nomisPropertyContainerId,
          dpsPropertyContainerId = dpsId.toString(),
          mappingType = PropertyContainerMappingDto.MappingType.MIGRATED,
          bookingId = bookingId,
          offenderNo = offenderNo,
        ),
      ),
    )
  }

  suspend fun PropertyContainerGetResponse.toProperty() = SyncPropertyContainerRequest(
    nomisPropertyContainerId = containerId,
    prisonerNumber = offenderNo,
    internalLocationId = toDpsLocation(),
    prisonId = prisonId,
    containerCode = toDpsContainerCode(),
    sealMark = sealMark,
    active = active,
    proposedDisposalDate = proposedDisposalDate,
    expiryDate = expiryDate,
    createDateTime = createdDateTime,
    createUsername = createdBy,
    modifyDateTime = updatedDateTime,
    modifyUsername = updatedBy,
  )

  private fun PropertyContainerGetResponse.toDpsContainerCode(): SyncPropertyContainerRequest.ContainerCode = when (containerCode) {
    PropertyContainerCode.BRA -> SyncPropertyContainerRequest.ContainerCode.Branston_Storage
    PropertyContainerCode.BULK -> SyncPropertyContainerRequest.ContainerCode.Bulk
    PropertyContainerCode.CO -> SyncPropertyContainerRequest.ContainerCode.Confiscated
    PropertyContainerCode.DES -> SyncPropertyContainerRequest.ContainerCode.For_Destruction
    PropertyContainerCode.VALU -> SyncPropertyContainerRequest.ContainerCode.Valuables
  }

  private suspend fun PropertyContainerGetResponse.toDpsLocation(): UUID? = internalLocationId?.let {
    UUID.fromString(propertyMappingService.getDpsLocation(it).dpsLocationId)
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, PropertyMigrationFilter> = jsonMapper
    .readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<PropertyMigrationFilter, ByPageNumber>> = jsonMapper
    .readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PropertyContainerIdResponse> = jsonMapper
    .readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, PropertyContainerMappingDto> = jsonMapper
    .readValue(json)
}

data class PropertyContainerIdResponse(
  val propertyContainerId: Long,
)
