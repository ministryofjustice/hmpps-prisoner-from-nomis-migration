package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.LocationsMigrationService.Companion.invalidPrisons
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.LocationsSynchronisationService.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.Capacity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.Certification
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.UsageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType.LOCATIONS
import java.util.*

@Service
class LocationsSynchronisationService(
  private val nomisApiService: NomisApiService,
  private val locationsMappingService: LocationsMappingService,
  private val locationsService: LocationsService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseUsage(event: LocationsOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("locations-synchronisation-skipped", event.toTelemetryProperties())
      return
    }
    val mapping = locationsMappingService.getMappingGivenNomisId(event.internalLocationId)
    if (mapping == null) {
      // There are some usage records in nomis with non-existent location ids !
      // (but no profile records)
      telemetryClient.trackEvent("locations-synchronisation-skipped-usage", event.toTelemetryProperties())
    } else {
      synchroniseUpdate(event, mapping)
    }
  }

  suspend fun synchroniseAttribute(event: LocationsOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("locations-synchronisation-skipped", event.toTelemetryProperties())
      return
    }
    val mapping = locationsMappingService.getMappingGivenNomisId(event.internalLocationId)
      ?: throw IllegalStateException("Cannot find mapping for location ${event.internalLocationId} to update attribute")
    synchroniseUpdate(event, mapping)
  }

  suspend fun synchroniseLocation(event: LocationsOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION" && !isVsipVisitRoomCreation(event)) {
      telemetryClient.trackEvent("locations-synchronisation-skipped", event.toTelemetryProperties())
      return
    }
    val mapping = locationsMappingService.getMappingGivenNomisId(event.internalLocationId)
    if (event.recordDeleted == true) {
      if (mapping == null) {
        throw IllegalStateException("Cannot find mapping for location ${event.internalLocationId} to delete")
      }
      tryToDeleteLocation(mapping.dpsLocationId, event)
      tryToDeleteMapping(mapping.dpsLocationId)
    } else if (mapping == null) {
      synchroniseCreate(event)
    } else {
      synchroniseUpdate(event, mapping)
    }
  }

  private fun isVsipVisitRoomCreation(event: LocationsOffenderEvent): Boolean = event.oldDescription == null &&
    event.description != null &&
    (
      event.description.endsWith("-VISITS-VSIP_CLO") ||
        event.description.endsWith("-VISITS-VSIP_SOC")
      )

  private suspend fun synchroniseCreate(event: LocationsOffenderEvent) {
    if (ignoreInvalidPrisons(event)) {
      return
    }
    try {
      val nomisLocation = nomisApiService.getLocation(event.internalLocationId)
      val parent = findParent(nomisLocation, event)

      val upsertSyncRequest = toUpsertSyncRequest(nomisLocation, parent?.dpsLocationId)
      log.debug("No location mapping - sending location upsert sync {} ", upsertSyncRequest)

      locationsService.upsertLocation(upsertSyncRequest).also { location ->
        tryToCreateLocationMapping(event, location.id.toString()).also { result ->
          telemetryClient.trackEvent(
            "locations-created-synchronisation-success",
            event.toTelemetryProperties(location.id.toString(), result == MAPPING_FAILED),
          )
        }
      }
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "locations-created-synchronisation-failed",
        event.toTelemetryProperties() + mapOf("exception" to (e.message ?: "")),
      )
      throw e
    }
  }

  private suspend fun synchroniseUpdate(event: LocationsOffenderEvent, mapping: LocationMappingDto) {
    if (ignoreInvalidPrisons(event)) {
      return
    }
    try {
      val nomisLocation = nomisApiService.getLocation(event.internalLocationId)
      val parent = findParent(nomisLocation, event)

      val upsertSyncRequest =
        toUpsertSyncRequest(UUID.fromString(mapping.dpsLocationId), nomisLocation, parent?.dpsLocationId)
      log.debug("Found location mapping: {}, sending location upsert sync {}", mapping, upsertSyncRequest)

      locationsService.upsertLocation(upsertSyncRequest)

      telemetryClient.trackEvent(
        "locations-updated-synchronisation-success",
        event.toTelemetryProperties(mapping.dpsLocationId),
      )
    } catch (e: Exception) {
      if (e is WebClientResponseException.Conflict &&
        (e.getResponseBodyAs(ErrorResponse::class.java) as ErrorResponse).errorCode == 107
      ) {
        log.error("Detected a permanent deactivation or cell converted to non-res: ignoring update", e)
        telemetryClient.trackEvent(
          "locations-updated-synchronisation-skipped-deactivated",
          event.toTelemetryProperties(mapping.dpsLocationId),
        )
      } else {
        telemetryClient.trackEvent(
          "locations-updated-synchronisation-failed",
          event.toTelemetryProperties(mapping.dpsLocationId) + mapOf("exception" to (e.message ?: "")),
        )
        throw e
      }
    }
  }

  private fun ignoreInvalidPrisons(event: LocationsOffenderEvent): Boolean {
    if (invalidPrisons.contains(event.prisonId)) {
      telemetryClient.trackEvent(
        "locations-synchronisation-skipped-ignored-prison",
        event.toTelemetryProperties(),
      )
      return true
    }
    return false
  }

  private suspend fun findParent(
    nomisLocation: LocationResponse,
    event: LocationsOffenderEvent,
  ): LocationMappingDto? {
    val parent = nomisLocation.parentLocationId?.let {
      locationsMappingService.getMappingGivenNomisId(nomisLocation.parentLocationId!!)
    }
    if (parent == null && nomisLocation.parentLocationId != null) {
      throw IllegalStateException("No mapping found for parent NOMIS location ${nomisLocation.parentLocationId} syncing NOMIS location ${event.internalLocationId}, ${nomisLocation.description}")
    }
    return parent
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED,
  }

  suspend fun tryToCreateLocationMapping(
    event: LocationsOffenderEvent,
    locationId: String,
  ): MappingResponse {
    val mapping = LocationMappingDto(
      dpsLocationId = locationId,
      nomisLocationId = event.internalLocationId,
      mappingType = NOMIS_CREATED,
    )
    try {
      locationsMappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<LocationMappingDto>>() {},
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "from-nomis-synch-location-duplicate",
            mapOf<String, String>(
              "duplicateDpsLocationId" to duplicateErrorDetails.duplicate.dpsLocationId,
              "duplicateNomisLocationId" to duplicateErrorDetails.duplicate.nomisLocationId.toString(),
              "existingDpsLocationId" to duplicateErrorDetails.existing.dpsLocationId,
              "existingNomisLocationId" to duplicateErrorDetails.existing.nomisLocationId.toString(),
            ),
            null,
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for dpsLocation id $locationId, nomisLocationId ${event.internalLocationId}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = LOCATIONS,
        message = mapping,
        telemetryAttributes = event.toTelemetryProperties(locationId),
      )
      return MAPPING_FAILED
    }
  }

  private suspend fun tryToDeleteLocation(dpsId: String, event: LocationsOffenderEvent) = runCatching {
    locationsService.deleteLocation(dpsId)
    telemetryClient.trackEvent("locations-deleted-synchronisation-success", event.toTelemetryProperties(dpsId))
  }.onFailure { e ->
    telemetryClient.trackEvent(
      "locations-deleted-synchronisation-failed",
      event.toTelemetryProperties(dpsId) + mapOf("exception" to (e.message ?: "")),
    )
    throw e
  }

  private suspend fun tryToDeleteMapping(dpsId: String) = runCatching {
    locationsMappingService.deleteMappingGivenDpsId(dpsId)
    telemetryClient.trackEvent("locations-deleted-mapping-success", mapOf("dpsLocationId" to dpsId))
  }.onFailure { e ->
    telemetryClient.trackEvent(
      "locations-deleted-mapping-failed",
      mapOf("dpsLocationId" to dpsId, "exception" to (e.message ?: "")),
    )
    log.warn("Unable to delete mapping for alert $dpsId. Please delete manually", e)
  }

  suspend fun retryCreateLocationMapping(retryMessage: InternalMessage<LocationMappingDto>) {
    locationsMappingService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<LocationMappingDto>>() {},
    ).also {
      telemetryClient.trackEvent(
        "locations-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }
}

private fun LocationsOffenderEvent.toTelemetryProperties(
  dpsLocationId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "nomisLocationId" to this.internalLocationId.toString(),
  "key" to (this.description ?: ""),
) + (dpsLocationId?.let { mapOf("dpsLocationId" to it) } ?: emptyMap()) + (
  if (mappingFailed == true) mapOf("mapping" to "initial-failure") else emptyMap()
  )

private val warningLogger = LoggerFactory.getLogger(LocationsMigrationService::class.java)

fun toUpsertSyncRequest(id: UUID, nomisLocationResponse: LocationResponse, parentId: String?) = toUpsertSyncRequest(nomisLocationResponse, parentId).copy(id = id)

fun toUpsertSyncRequest(nomisLocationResponse: LocationResponse, parentId: String?) = NomisSyncLocationRequest(
  prisonId = nomisLocationResponse.prisonId,
  code = nomisLocationResponse.locationCode,
  locationType = toLocationType(nomisLocationResponse.locationType),
  lastUpdatedBy = nomisLocationResponse.modifyUsername ?: nomisLocationResponse.createUsername,
  localName = nomisLocationResponse.userDescription,
  comments = nomisLocationResponse.comment,
  orderWithinParentLocation = nomisLocationResponse.listSequence,
  residentialHousingType = toResidentialHousingType(nomisLocationResponse.unitType),
  parentId = parentId?.let { UUID.fromString(parentId) },
  capacity = if (nomisLocationResponse.capacity != null || nomisLocationResponse.operationalCapacity != null) {
    Capacity(
      nomisLocationResponse.capacity ?: 0,
      nomisLocationResponse.operationalCapacity ?: 0,
    )
  } else {
    null
  },
  certification = if (nomisLocationResponse.certified != null || nomisLocationResponse.cnaCapacity != null) {
    Certification(nomisLocationResponse.certified ?: false, nomisLocationResponse.cnaCapacity ?: 0)
  } else {
    null
  },
  attributes = nomisLocationResponse.profiles
    ?.mapNotNull { toAttribute(it.profileType.name, it.profileCode) }
    ?.toSet(),
  usage = nomisLocationResponse.usages?.map { toUsage(it) }?.toSet(),

  createDate = nomisLocationResponse.createDatetime,
  // lastModifiedDate - no value available as it changes with occupancy
  deactivatedDate = if (nomisLocationResponse.active) {
    null
  } else {
    nomisLocationResponse.deactivateDate
  },
  deactivationReason = if (nomisLocationResponse.active) {
    null
  } else {
    toReason(nomisLocationResponse.reasonCode)
  },
  proposedReactivationDate = if (nomisLocationResponse.active) {
    null
  } else {
    nomisLocationResponse.reactivateDate
  },
  isDeactivated = !nomisLocationResponse.active,
)

private fun toLocationType(locationType: String): NomisSyncLocationRequest.LocationType = when (locationType) {
  "WING", "BLK" -> NomisSyncLocationRequest.LocationType.WING
  "SPUR" -> NomisSyncLocationRequest.LocationType.SPUR
  "LAND", "TIER", "LAN" -> NomisSyncLocationRequest.LocationType.LANDING
  "CELL" -> NomisSyncLocationRequest.LocationType.CELL
  "ADJU" -> NomisSyncLocationRequest.LocationType.ADJUDICATION_ROOM
  "ADMI" -> NomisSyncLocationRequest.LocationType.ADMINISTRATION_AREA
  "APP" -> NomisSyncLocationRequest.LocationType.APPOINTMENTS
  "AREA" -> NomisSyncLocationRequest.LocationType.AREA
  "ASSO" -> NomisSyncLocationRequest.LocationType.ASSOCIATION
  "BOOT" -> NomisSyncLocationRequest.LocationType.BOOTH
  "BOX" -> NomisSyncLocationRequest.LocationType.BOX
  "CLAS" -> NomisSyncLocationRequest.LocationType.CLASSROOM
  "EXER" -> NomisSyncLocationRequest.LocationType.EXERCISE_AREA
  "EXTE" -> NomisSyncLocationRequest.LocationType.EXTERNAL_GROUNDS
  "FAIT" -> NomisSyncLocationRequest.LocationType.FAITH_AREA
  "GROU" -> NomisSyncLocationRequest.LocationType.GROUP
  "HCEL" -> NomisSyncLocationRequest.LocationType.HOLDING_CELL
  "HOLD" -> NomisSyncLocationRequest.LocationType.HOLDING_AREA
  "IGRO" -> NomisSyncLocationRequest.LocationType.INTERNAL_GROUNDS
  "INSI" -> NomisSyncLocationRequest.LocationType.INSIDE_PARTY
  "INTE" -> NomisSyncLocationRequest.LocationType.INTERVIEW
  "LOCA" -> NomisSyncLocationRequest.LocationType.LOCATION
  "MEDI" -> NomisSyncLocationRequest.LocationType.MEDICAL
  "MOVE" -> NomisSyncLocationRequest.LocationType.MOVEMENT_AREA
  "OFFI" -> NomisSyncLocationRequest.LocationType.OFFICE
  "OUTS" -> NomisSyncLocationRequest.LocationType.OUTSIDE_PARTY
  "POSI" -> NomisSyncLocationRequest.LocationType.POSITION
  "RESI" -> NomisSyncLocationRequest.LocationType.RESIDENTIAL_UNIT
  "ROOM" -> NomisSyncLocationRequest.LocationType.ROOM
  "RTU" -> NomisSyncLocationRequest.LocationType.RETURN_TO_UNIT
  "SHEL" -> NomisSyncLocationRequest.LocationType.SHELF
  "SPOR" -> NomisSyncLocationRequest.LocationType.SPORTS
  "STOR" -> NomisSyncLocationRequest.LocationType.STORE
  "TABL" -> NomisSyncLocationRequest.LocationType.TABLE
  "TRAI" -> NomisSyncLocationRequest.LocationType.TRAINING_AREA
  "TRRM" -> NomisSyncLocationRequest.LocationType.TRAINING_ROOM
  "VIDE" -> NomisSyncLocationRequest.LocationType.VIDEO_LINK
  "VISIT" -> NomisSyncLocationRequest.LocationType.VISITS
  "WORK" -> NomisSyncLocationRequest.LocationType.WORKSHOP
  else -> throw IllegalArgumentException("Unknown location type $locationType")
}

private fun toResidentialHousingType(unitType: LocationResponse.UnitType?): NomisSyncLocationRequest.ResidentialHousingType? = when (unitType) {
  LocationResponse.UnitType.HC -> NomisSyncLocationRequest.ResidentialHousingType.HEALTHCARE
  LocationResponse.UnitType.HOLC -> NomisSyncLocationRequest.ResidentialHousingType.HOLDING_CELL
  LocationResponse.UnitType.NA -> NomisSyncLocationRequest.ResidentialHousingType.NORMAL_ACCOMMODATION
  LocationResponse.UnitType.OU -> NomisSyncLocationRequest.ResidentialHousingType.OTHER_USE
  LocationResponse.UnitType.REC -> NomisSyncLocationRequest.ResidentialHousingType.RECEPTION
  LocationResponse.UnitType.SEG -> NomisSyncLocationRequest.ResidentialHousingType.SEGREGATION
  LocationResponse.UnitType.SPLC -> NomisSyncLocationRequest.ResidentialHousingType.SPECIALIST_CELL
  null -> null
}

private fun toReason(reasonCode: LocationResponse.ReasonCode?): NomisSyncLocationRequest.DeactivationReason = when (reasonCode) {
  LocationResponse.ReasonCode.A -> NomisSyncLocationRequest.DeactivationReason.NEW_BUILDING
  LocationResponse.ReasonCode.B -> NomisSyncLocationRequest.DeactivationReason.CELL_RECLAIMS
  LocationResponse.ReasonCode.C -> NomisSyncLocationRequest.DeactivationReason.CHANGE_OF_USE
  LocationResponse.ReasonCode.D -> NomisSyncLocationRequest.DeactivationReason.REFURBISHMENT
  LocationResponse.ReasonCode.E -> NomisSyncLocationRequest.DeactivationReason.CLOSURE
  null, LocationResponse.ReasonCode.F -> NomisSyncLocationRequest.DeactivationReason.OTHER
  LocationResponse.ReasonCode.G -> NomisSyncLocationRequest.DeactivationReason.LOCAL_WORK
  LocationResponse.ReasonCode.H -> NomisSyncLocationRequest.DeactivationReason.STAFF_SHORTAGE
  LocationResponse.ReasonCode.I -> NomisSyncLocationRequest.DeactivationReason.MOTHBALLED
  LocationResponse.ReasonCode.J -> NomisSyncLocationRequest.DeactivationReason.DAMAGED
  LocationResponse.ReasonCode.K -> NomisSyncLocationRequest.DeactivationReason.OUT_OF_USE
  LocationResponse.ReasonCode.L -> NomisSyncLocationRequest.DeactivationReason.CELLS_RETURNING_TO_USE
}

private fun toAttribute(type: String, code: String): NomisSyncLocationRequest.Attributes? = when (type) {
  "HOU_SANI_FIT" ->
    when (code) {
      "ABD" -> NomisSyncLocationRequest.Attributes.ANTI_BARRICADE_DOOR
      "ACB" -> NomisSyncLocationRequest.Attributes.AUDITABLE_CELL_BELL
      "FIB" -> NomisSyncLocationRequest.Attributes.FIXED_BED
      "MD" -> NomisSyncLocationRequest.Attributes.METAL_DOOR
      "MOB" -> NomisSyncLocationRequest.Attributes.MOVABLE_BED
      "PC" -> NomisSyncLocationRequest.Attributes.PRIVACY_CURTAIN
      "PS" -> NomisSyncLocationRequest.Attributes.PRIVACY_SCREEN
      "SCB" -> NomisSyncLocationRequest.Attributes.STANDARD_CELL_BELL
      "SETO" -> NomisSyncLocationRequest.Attributes.SEPARATE_TOILET
      "WD" -> NomisSyncLocationRequest.Attributes.WOODEN_DOOR
      else -> {
        warningLogger.warn("Unknown location attribute type $type, code $code")
        null
      }
    }

  "HOU_UNIT_ATT" ->
    when (code) {
      "A" -> NomisSyncLocationRequest.Attributes.CAT_A_CELL
      "DO" -> NomisSyncLocationRequest.Attributes.DOUBLE_OCCUPANCY
      "ELC" -> NomisSyncLocationRequest.Attributes.E_LIST_CELL
      "GC" -> NomisSyncLocationRequest.Attributes.GATED_CELL
      "LC" -> NomisSyncLocationRequest.Attributes.LISTENER_CELL
      "LF" -> NomisSyncLocationRequest.Attributes.LOCATE_FLAT
      "MO" -> NomisSyncLocationRequest.Attributes.MULTIPLE_OCCUPANCY
      "NSMC" -> NomisSyncLocationRequest.Attributes.NON_SMOKER_CELL
      "OC" -> NomisSyncLocationRequest.Attributes.OBSERVATION_CELL
      "SC" -> NomisSyncLocationRequest.Attributes.SAFE_CELL
      "SO" -> NomisSyncLocationRequest.Attributes.SINGLE_OCCUPANCY
      "SPC" -> NomisSyncLocationRequest.Attributes.SPECIAL_CELL
      "WA" -> NomisSyncLocationRequest.Attributes.WHEELCHAIR_ACCESS
      else -> {
        warningLogger.warn("Unknown location attribute type $type, code $code")
        null
      }
    }

  "HOU_USED_FOR" ->
    when (code) {
      "1" -> NomisSyncLocationRequest.Attributes.UNCONVICTED_JUVENILES
      "2" -> NomisSyncLocationRequest.Attributes.SENTENCED_JUVENILES
      "3" -> NomisSyncLocationRequest.Attributes.UNCONVICTED_18_20
      "4" -> NomisSyncLocationRequest.Attributes.SENTENCED_18_20
      "5" -> NomisSyncLocationRequest.Attributes.UNCONVICTED_ADULTS
      "6" -> NomisSyncLocationRequest.Attributes.SENTENCED_ADULTS
      "7", "V" -> NomisSyncLocationRequest.Attributes.VULNERABLE_PRISONER_UNIT
      "8" -> NomisSyncLocationRequest.Attributes.SPECIAL_UNIT
      "9" -> NomisSyncLocationRequest.Attributes.RESETTLEMENT_HOSTEL
      "10" -> NomisSyncLocationRequest.Attributes.HEALTHCARE_CENTRE
      "11" -> NomisSyncLocationRequest.Attributes.NATIONAL_RESOURCE_HOSPITAL
      "12" -> NomisSyncLocationRequest.Attributes.OTHER_SPECIFIED
      "A" -> NomisSyncLocationRequest.Attributes.REMAND_CENTRE
      "B" -> NomisSyncLocationRequest.Attributes.LOCAL_PRISON
      "C" -> NomisSyncLocationRequest.Attributes.CLOSED_PRISON
      "D" -> NomisSyncLocationRequest.Attributes.OPEN_TRAINING
      "E" -> NomisSyncLocationRequest.Attributes.HOSTEL
      "H" -> NomisSyncLocationRequest.Attributes.NATIONAL_RESOURCE_HOSPITAL
      "I" -> NomisSyncLocationRequest.Attributes.CLOSED_YOUNG_OFFENDER
      "J" -> NomisSyncLocationRequest.Attributes.OPEN_YOUNG_OFFENDER
      "K" -> NomisSyncLocationRequest.Attributes.REMAND_UNDER_18
      "L" -> NomisSyncLocationRequest.Attributes.SENTENCED_UNDER_18
      "R" -> NomisSyncLocationRequest.Attributes.ECL_COMPONENT
      "T" -> NomisSyncLocationRequest.Attributes.ADDITIONAL_SPECIAL_UNIT
      "Y" -> NomisSyncLocationRequest.Attributes.SECOND_CLOSED_TRAINER
      "Z" -> NomisSyncLocationRequest.Attributes.IMMIGRATION_DETAINEES
      else -> {
        warningLogger.warn("Unknown location attribute type $type, code $code")
        null
      }
    }

  "SUP_LVL_TYPE" ->
    when (code) {
      "A" -> NomisSyncLocationRequest.Attributes.CAT_A
      "E" -> NomisSyncLocationRequest.Attributes.CAT_A_EX
      "H" -> NomisSyncLocationRequest.Attributes.CAT_A_HI
      "B" -> NomisSyncLocationRequest.Attributes.CAT_B
      "C" -> NomisSyncLocationRequest.Attributes.CAT_C
      "D" -> NomisSyncLocationRequest.Attributes.CAT_D
      "GRANTED" -> NomisSyncLocationRequest.Attributes.PAROLE_GRANTED
      "I" -> NomisSyncLocationRequest.Attributes.YOI_CLOSED
      "J" -> NomisSyncLocationRequest.Attributes.YOI_OPEN
      "V" -> NomisSyncLocationRequest.Attributes.YOI_RESTRICTED
      "K" -> NomisSyncLocationRequest.Attributes.YOI_SHORT_SENTENCE
      "L" -> NomisSyncLocationRequest.Attributes.YOI_LONG_TERM_CLOSED
      "Z" -> NomisSyncLocationRequest.Attributes.UNCLASSIFIED
      "X" -> NomisSyncLocationRequest.Attributes.UNCATEGORISED_SENTENCED_MALE
      "LOW" -> NomisSyncLocationRequest.Attributes.LOW
      "MED" -> NomisSyncLocationRequest.Attributes.MEDIUM
      "HI" -> NomisSyncLocationRequest.Attributes.HIGH
      "N/A", "NA" -> NomisSyncLocationRequest.Attributes.NOT_APPLICABLE
      "P" -> NomisSyncLocationRequest.Attributes.PROV_A
      "PEND" -> NomisSyncLocationRequest.Attributes.PENDING
      "REF/REVIEW" -> NomisSyncLocationRequest.Attributes.REF_REVIEW
      "REFUSED" -> NomisSyncLocationRequest.Attributes.REFUSED_NO_REVIEW
      "STANDARD" -> NomisSyncLocationRequest.Attributes.STANDARD
      "Q" -> NomisSyncLocationRequest.Attributes.FEMALE_RESTRICTED
      "R" -> NomisSyncLocationRequest.Attributes.FEMALE_CLOSED
      "S" -> NomisSyncLocationRequest.Attributes.FEMALE_SEMI
      "T" -> NomisSyncLocationRequest.Attributes.FEMALE_OPEN
      "U" -> NomisSyncLocationRequest.Attributes.UN_SENTENCED
      "Y" -> NomisSyncLocationRequest.Attributes.YES
      "N" -> NomisSyncLocationRequest.Attributes.NO
      else -> {
        warningLogger.warn("Unknown location attribute type $type, code $code")
        null
      }
    }

  "NON_ASSO_TYP" -> {
    warningLogger.warn("Ignoring location attribute type $type, code $code")
    null
  }

  else -> {
    warningLogger.warn("Unknown location attribute type $type, code $code")
    null
  }
}

private fun toUsage(it: UsageRequest) = NonResidentialUsageDto(
  when (it.internalLocationUsageType) {
    UsageRequest.InternalLocationUsageType.APP -> NonResidentialUsageDto.UsageType.APPOINTMENT
    UsageRequest.InternalLocationUsageType.VISIT -> NonResidentialUsageDto.UsageType.VISIT
    UsageRequest.InternalLocationUsageType.MOVEMENT -> NonResidentialUsageDto.UsageType.MOVEMENT
    UsageRequest.InternalLocationUsageType.OCCUR -> NonResidentialUsageDto.UsageType.OCCURRENCE
    UsageRequest.InternalLocationUsageType.OIC -> NonResidentialUsageDto.UsageType.ADJUDICATION_HEARING
    UsageRequest.InternalLocationUsageType.OTHER, UsageRequest.InternalLocationUsageType.OTH -> NonResidentialUsageDto.UsageType.OTHER
    UsageRequest.InternalLocationUsageType.PROG -> NonResidentialUsageDto.UsageType.PROGRAMMES_ACTIVITIES
    UsageRequest.InternalLocationUsageType.PROP -> NonResidentialUsageDto.UsageType.PROPERTY
  },
  it.sequence ?: 0,
  it.capacity,
)
