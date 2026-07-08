package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.originatesInDps
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

@Service
class PropertySyncService(
  private val nomisApiService: PropertyNomisApiService,
  private val propertyDpsApiService: PropertyDpsApiService,
  private val propertyMappingService: PropertyMappingService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun created(event: PropertyEvent) {
    if (event.auditModuleName.originatesInDps()) {
      telemetryClient.trackEvent("property-synchronisation-created-skipped", event.toTelemetryProperties())
      return
    }
    try {
      val nomisPropertyContainer = nomisApiService.getPropertyContainer(event.propertyContainerId)
      propertyMappingService.getMappingByNomisId(event.propertyContainerId)
        ?.let { mappingResponse ->
          telemetryClient.trackEvent(
            "property-sync-create-failed",
            event.toTelemetryProperties(
              dpsId = mappingResponse.dpsPropertyContainerId,
            ),
          )
        }
        ?: run {
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
                  telemetryClient.trackEvent(
                    "property-synchronisation-created-success",

                    mapOf(
                      "dpsPropertyContainerId" to dpsResponse.dpsId.toString(),
                      "nomisPropertyContainerId" to dpsResponse.nomisPropertyContainerId.toString(),
                      "bookingId" to nomisPropertyContainer.bookingId.toString(),
                      "offenderNo" to nomisPropertyContainer.offenderNo,
                      "mapping" to (
                        if (mappingCreateResult == MappingResponse.MAPPING_FAILED) "initial-failure" else "success"
                        ),
                    ),
                  )
                }
            }
        }
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "property-synchronisation-created-failed",
        event.toTelemetryProperties() + mapOf("error" to (e.message ?: "unknown error")),
      )
      throw e
    }
  }

  suspend fun tryToCreateMapping(mapping: PropertyContainerMappingDto): MappingResponse {
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
          "mapping" to "initial-failure",
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
      } else {
        telemetryClient.trackEvent(
          "property-synchronisation-mapping-created-success",
          mapOf(
            "dpsPropertyContainerId" to mapping.dpsPropertyContainerId,
            "nomisPropertyContainerId" to mapping.nomisPropertyContainerId.toString(),
            "bookingId" to mapping.bookingId.toString(),
            "offenderNo" to mapping.offenderNo,
            "mapping" to "success",
          ),
        )
      }
    }
  }

  suspend fun updated(event: PropertyEvent) {
  }

  suspend fun deleted(event: PropertyEvent) {
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED,
  }

  suspend fun retryCreateMapping(retryMessage: InternalMessage<PropertyContainerMappingDto>) {
    val mapping = retryMessage.body
    createMapping(mapping)
  }

  suspend fun toDpsProperty(nomisProperty: PropertyContainerGetResponse) = SyncPropertyContainerRequest(
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

private fun PropertyEvent.toTelemetryProperties(
  dpsId: String? = null,
  mappingFailed: Boolean? = null,
): Map<String, String> = mapOf(
  "nomisPropertyContainerId" to this.propertyContainerId.toString(),
  "bookingId" to this.bookingId.toString(),
) +
  (offenderIdDisplay?.let { mapOf("offenderNo" to it) } ?: emptyMap()) +
  (dpsId?.let { mapOf("dpsId" to it) } ?: emptyMap()) +
  (if (mappingFailed == true) mapOf("mapping" to "initial-failure") else emptyMap())
