package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PropertyContainerCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PropertyContainerGetResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.model.SyncPropertyContainerRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.model.SyncPropertyContainerResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.UUID

private const val TELEMETRY_PREFIX = "property-synchronisation"

@Service
class PropertySyncService(
  private val nomisApiService: PropertyNomisApiService,
  private val propertyDpsApiService: PropertyDpsApiService,
  private val propertyMappingService: PropertyMappingService,
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun created(event: PropertyEvent) {
    val telemetryName = "$TELEMETRY_PREFIX-created"
    val (_, _, bookingId, offenderIdDisplay, propertyContainerId, auditModuleName) = event
    val telemetry = telemetryOf(
      "nomisPropertyContainerId" to propertyContainerId.toString(),
      "bookingId" to bookingId.toString(),
      "offenderNo" to offenderIdDisplay.toString(),
    )
    if (event.originatesInDps) {
      telemetryClient.trackEvent("$telemetryName-skipped", telemetry)
      return
    }
    val nomisPropertyContainer = nomisApiService.getPropertyContainer(event.propertyContainerId)
    val mapping = propertyMappingService.getMappingByNomisIdOrNull(event.propertyContainerId)
    if (mapping != null) {
      telemetry["dpsPropertyContainerId"] = mapping.dpsPropertyContainerId
      telemetryClient.trackEvent("$telemetryName-mapping-exists-ignored", telemetry)
    } else {
      track(telemetryName, telemetry) {
        propertyDpsApiService.upsert(toDpsProperty(nomisPropertyContainer))
          .also { dpsResponse ->
            if (dpsResponse.mappingType != SyncPropertyContainerResponse.MappingType.CREATED) {
              throw IllegalStateException("Property ${event.propertyContainerId} already exists in DPS")
              // probably redundant as this just depends on sending a UUID
            }
            tryToCreateMapping(
              PropertyContainerMappingDto(
                dpsPropertyContainerId = dpsResponse.dpsId.toString(),
                nomisPropertyContainerId = dpsResponse.nomisPropertyContainerId,
                bookingId = nomisPropertyContainer.bookingId,
                offenderNo = nomisPropertyContainer.offenderNo,
                mappingType = PropertyContainerMappingDto.MappingType.NOMIS_CREATED,
              ),
            )
              .also { mappingCreateResult ->
                telemetry["dpsPropertyContainerId"] = dpsResponse.dpsId.toString()
                telemetry["mapping"] = if (mappingCreateResult == MappingResponse.MAPPING_FAILED) "initial-failure" else "success"
              }
          }
      }
    }
  }

  private suspend fun tryToCreateMapping(mapping: PropertyContainerMappingDto): MappingResponse {
    try {
      createMapping(mapping)
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error("Failed to create mapping for $mapping", e)
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.PROPERTY,
        message = mapping,
        telemetryAttributes = mapOf(
          "bookingId" to mapping.bookingId.toString(),
          "dpsPropertyContainerId" to mapping.dpsPropertyContainerId,
          "nomisPropertyContainerId" to mapping.nomisPropertyContainerId.toString(),
          "offenderNo" to mapping.offenderNo,
        ),
      )
      return MappingResponse.MAPPING_FAILED
    }
  }

  private suspend fun createMapping(mapping: PropertyContainerMappingDto) {
    propertyMappingService.createMapping(
      mapping,
      object : ParameterizedTypeReference<DuplicateErrorResponse<PropertyContainerMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "property-from-nomis-sync-duplicate",
          mapOf(
            "duplicateDpsPropertyContainerId" to duplicateErrorDetails.duplicate.dpsPropertyContainerId,
            "duplicateNomisPropertyContainerId" to duplicateErrorDetails.duplicate.nomisPropertyContainerId.toString(),
            "existingDpsPropertyContainerId" to duplicateErrorDetails.existing.dpsPropertyContainerId,
            "existingNomisPropertyContainerId" to duplicateErrorDetails.existing.nomisPropertyContainerId.toString(),
          ),
        )
      }
    }
  }

  suspend fun updated(event: PropertyEvent) {
    val telemetryName = "$TELEMETRY_PREFIX-updated"
    val (_, _, bookingId, offenderIdDisplay, propertyContainerId) = event
    val telemetry = telemetryOf(
      "nomisPropertyContainerId" to propertyContainerId.toString(),
      "bookingId" to bookingId.toString(),
      "offenderNo" to offenderIdDisplay.toString(),
    )
    if (event.originatesInDps) {
      telemetryClient.trackEvent("$telemetryName-skipped", telemetry)
      return
    }
    track(telemetryName, telemetry) {
      val nomisPropertyContainer = nomisApiService.getPropertyContainer(propertyContainerId)
      propertyMappingService.getMappingByNomisId(propertyContainerId)
        .also { mappingResponse ->
          telemetry["dpsPropertyContainerId"] = mappingResponse.dpsPropertyContainerId
          propertyDpsApiService.upsert(toDpsProperty(mappingResponse.dpsPropertyContainerId, nomisPropertyContainer))
        }
    }
  }

  suspend fun deleted(event: PropertyEvent) {
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-deleted-ignored",
      mapOf(
        "nomisPropertyContainerId" to event.propertyContainerId.toString(),
        "bookingId" to event.bookingId.toString(),
        "offenderNo" to event.offenderIdDisplay.toString(),
      ),
    )
    // TODO: not sure if this ever happens
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED,
  }

  suspend fun retryCreateMapping(retryMessage: InternalMessage<PropertyContainerMappingDto>) {
    createMapping(retryMessage.body)
    telemetryClient.trackEvent("$TELEMETRY_PREFIX-mapping-created", retryMessage.telemetryAttributes)
  }

  suspend fun toDpsProperty(nomisProperty: PropertyContainerGetResponse) = toDpsProperty(null, nomisProperty)

  suspend fun toDpsProperty(dpsId: String?, nomisProperty: PropertyContainerGetResponse) = SyncPropertyContainerRequest(
    dpsId = dpsId?.let { UUID.fromString(dpsId) },
    nomisPropertyContainerId = nomisProperty.containerId,
    prisonerNumber = nomisProperty.offenderNo,
    internalLocationId = nomisProperty.toDpsLocation(),
    prisonId = nomisProperty.prisonId,
    containerCode = nomisProperty.toDpsContainerCode(),
    sealMark = nomisProperty.sealMark,
    active = nomisProperty.active,
    proposedDisposalDate = nomisProperty.proposedDisposalDate,
    expiryDate = nomisProperty.expiryDate,
    createDateTime = nomisProperty.createdDateTime,
    createUsername = nomisProperty.createdBy,
    modifyDateTime = nomisProperty.updatedDateTime,
    modifyUsername = nomisProperty.updatedBy,
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
}
