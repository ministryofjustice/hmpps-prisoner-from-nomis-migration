package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.Capacity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.Certification
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.ChangeHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.NomisMigrateLocationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.LocationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.UsageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.LOCATIONS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes
import java.util.*

@Service
class LocationsMigrationService(
  queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val locationsService: LocationsService,
  private val locationsMappingService: LocationsMappingService,
  @Value("\${locations.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<LocationsMigrationFilter, LocationIdResponse, LocationResponse, LocationMappingDto>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = locationsMappingService,
  telemetryClient = telemetryClient,
  migrationType = LOCATIONS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val invalidPrisons = listOf("ZZGHI", "UNKNWN", "TRN", "LT4")

  override suspend fun getIds(
    migrationFilter: LocationsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<LocationIdResponse> = nomisApiService.getLocationIds(pageNumber = pageNumber, pageSize = pageSize)

  override suspend fun migrateNomisEntity(context: MigrationContext<LocationIdResponse>) {
    with(context.body) {
      log.info("attempting to migrate $locationId")

      // Determine all valid locations for this offender pair
      val nomisLocationResponse = nomisApiService.getLocation(locationId)

      if (invalidPrisons.contains(nomisLocationResponse.prisonId)) {
        log.info("Will not migrate invalid prison locations, NOMIS location is $locationId, ${nomisLocationResponse.description}")
      } else {
        locationsMappingService.getMappingGivenNomisId(locationId)
          ?.run {
            log.info(
              """Will not migrate the location since it is migrated already, NOMIS location is $locationId, ${nomisLocationResponse.description}, as part migration ${this.label ?: "NONE"} (${this.mappingType})""",
            )
          }
          ?: run {
            val parent = nomisLocationResponse.parentLocationId?.let {
              locationsMappingService.getMappingGivenNomisId(it)
                ?: throw IllegalStateException("Parent NOMIS location $it not yet migrated for NOMIS location $locationId, ${nomisLocationResponse.description}")
            }

            val dpsRequest = toMigrationRequest(nomisLocationResponse, parent?.dpsLocationId)
            log.debug(
              "No location mapping for ${nomisLocationResponse.description}, sending location migrate upsert {}",
              dpsRequest,
            )
            val migratedLocation = locationsService.migrateLocation(dpsRequest)
              .also {
                createLocationMapping(
                  dpsLocationId = it.id.toString(),
                  nomisLocationId = nomisLocationResponse.locationId,
                  context = context,
                )
              }

            telemetryClient.trackEvent(
              "locations-migration-entity-migrated",
              mapOf(
                "dpsLocationId" to migratedLocation.id.toString(),
                "nomisLocationId" to nomisLocationResponse.locationId.toString(),
                "key" to nomisLocationResponse.description,
                "migrationId" to context.migrationId,
              ),
              null,
            )
          }
      }
    }
  }

  private suspend fun createLocationMapping(
    dpsLocationId: String,
    nomisLocationId: Long,
    context: MigrationContext<*>,
  ) = try {
    locationsMappingService.createMapping(
      LocationMappingDto(
        dpsLocationId = dpsLocationId,
        nomisLocationId = nomisLocationId,
        label = context.migrationId,
        mappingType = MIGRATED,
      ),
      object : ParameterizedTypeReference<DuplicateErrorResponse<LocationMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-location-duplicate",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "duplicateDpsLocationId" to duplicateErrorDetails.duplicate.dpsLocationId,
            "duplicateNomisLocationId" to duplicateErrorDetails.duplicate.nomisLocationId.toString(),
            "existingDpsLocationId" to duplicateErrorDetails.existing.dpsLocationId,
            "existingNomisLocationId" to duplicateErrorDetails.existing.nomisLocationId.toString(),
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      }
    }
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for DPS location id $dpsLocationId, Nomis $nomisLocationId, ",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = LocationMappingDto(
          dpsLocationId = dpsLocationId,
          nomisLocationId = nomisLocationId,
          mappingType = MIGRATED,
        ),
      ),
    )
  }
}

private val warningLogger = LoggerFactory.getLogger(LocationsMigrationService::class.java)

fun toMigrationRequest(nomisLocationResponse: LocationResponse, parentId: String?) =
  NomisMigrateLocationRequest(
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
    isCell = nomisLocationResponse.locationType == "CELL",
    history = nomisLocationResponse.amendments?.map {
      ChangeHistory(
        attribute = toHistoryAttribute(it.columnName),
        amendedDate = it.amendDateTime,
        oldValue = it.oldValue,
        newValue = it.newValue,
        amendedBy = it.amendedBy,
      )
    },
  )

private fun toLocationType(locationType: String): NomisMigrateLocationRequest.LocationType =
  when (locationType) {
    "WING", "BLK" -> NomisMigrateLocationRequest.LocationType.WING
    "SPUR" -> NomisMigrateLocationRequest.LocationType.SPUR
    "LAND", "TIER", "LAN" -> NomisMigrateLocationRequest.LocationType.LANDING
    "CELL" -> NomisMigrateLocationRequest.LocationType.CELL
    "ADJU" -> NomisMigrateLocationRequest.LocationType.ADJUDICATION_ROOM
    "ADMI" -> NomisMigrateLocationRequest.LocationType.ADMINISTRATION_AREA
    "APP" -> NomisMigrateLocationRequest.LocationType.APPOINTMENTS
    "AREA" -> NomisMigrateLocationRequest.LocationType.AREA
    "ASSO" -> NomisMigrateLocationRequest.LocationType.ASSOCIATION
    "BOOT" -> NomisMigrateLocationRequest.LocationType.BOOTH
    "BOX" -> NomisMigrateLocationRequest.LocationType.BOX
    "CLAS" -> NomisMigrateLocationRequest.LocationType.CLASSROOM
    "EXER" -> NomisMigrateLocationRequest.LocationType.EXERCISE_AREA
    "EXTE" -> NomisMigrateLocationRequest.LocationType.EXTERNAL_GROUNDS
    "FAIT" -> NomisMigrateLocationRequest.LocationType.FAITH_AREA
    "GROU" -> NomisMigrateLocationRequest.LocationType.GROUP
    "HCEL" -> NomisMigrateLocationRequest.LocationType.HOLDING_CELL
    "HOLD" -> NomisMigrateLocationRequest.LocationType.HOLDING_AREA
    "IGRO" -> NomisMigrateLocationRequest.LocationType.INTERNAL_GROUNDS
    "INSI" -> NomisMigrateLocationRequest.LocationType.INSIDE_PARTY
    "INTE" -> NomisMigrateLocationRequest.LocationType.INTERVIEW
    "LOCA" -> NomisMigrateLocationRequest.LocationType.LOCATION
    "MEDI" -> NomisMigrateLocationRequest.LocationType.MEDICAL
    "MOVE" -> NomisMigrateLocationRequest.LocationType.MOVEMENT_AREA
    "OFFI" -> NomisMigrateLocationRequest.LocationType.OFFICE
    "OUTS" -> NomisMigrateLocationRequest.LocationType.OUTSIDE_PARTY
    "POSI" -> NomisMigrateLocationRequest.LocationType.POSITION
    "RESI" -> NomisMigrateLocationRequest.LocationType.RESIDENTIAL_UNIT
    "ROOM" -> NomisMigrateLocationRequest.LocationType.ROOM
    "RTU" -> NomisMigrateLocationRequest.LocationType.RETURN_TO_UNIT
    "SHEL" -> NomisMigrateLocationRequest.LocationType.SHELF
    "SPOR" -> NomisMigrateLocationRequest.LocationType.SPORTS
    "STOR" -> NomisMigrateLocationRequest.LocationType.STORE
    "TABL" -> NomisMigrateLocationRequest.LocationType.TABLE
    "TRAI" -> NomisMigrateLocationRequest.LocationType.TRAINING_AREA
    "TRRM" -> NomisMigrateLocationRequest.LocationType.TRAINING_ROOM
    "VIDE" -> NomisMigrateLocationRequest.LocationType.VIDEO_LINK
    "VISIT" -> NomisMigrateLocationRequest.LocationType.VISITS
    "WORK" -> NomisMigrateLocationRequest.LocationType.WORKSHOP
    else -> throw IllegalArgumentException("Unknown location type $locationType")
  }

private fun toResidentialHousingType(unitType: LocationResponse.UnitType?): NomisMigrateLocationRequest.ResidentialHousingType? =
  when (unitType) {
    LocationResponse.UnitType.HC -> NomisMigrateLocationRequest.ResidentialHousingType.HEALTHCARE
    LocationResponse.UnitType.HOLC -> NomisMigrateLocationRequest.ResidentialHousingType.HOLDING_CELL
    LocationResponse.UnitType.NA -> NomisMigrateLocationRequest.ResidentialHousingType.NORMAL_ACCOMMODATION
    LocationResponse.UnitType.OU -> NomisMigrateLocationRequest.ResidentialHousingType.OTHER_USE
    LocationResponse.UnitType.REC -> NomisMigrateLocationRequest.ResidentialHousingType.RECEPTION
    LocationResponse.UnitType.SEG -> NomisMigrateLocationRequest.ResidentialHousingType.SEGREGATION
    LocationResponse.UnitType.SPLC -> NomisMigrateLocationRequest.ResidentialHousingType.SPECIALIST_CELL
    null -> null
  }

private fun toReason(reasonCode: LocationResponse.ReasonCode?): NomisMigrateLocationRequest.DeactivationReason =
  when (reasonCode) {
    LocationResponse.ReasonCode.A -> NomisMigrateLocationRequest.DeactivationReason.NEW_BUILDING
    LocationResponse.ReasonCode.B -> NomisMigrateLocationRequest.DeactivationReason.CELL_RECLAIMS
    LocationResponse.ReasonCode.C -> NomisMigrateLocationRequest.DeactivationReason.CHANGE_OF_USE
    LocationResponse.ReasonCode.D -> NomisMigrateLocationRequest.DeactivationReason.REFURBISHMENT
    LocationResponse.ReasonCode.E -> NomisMigrateLocationRequest.DeactivationReason.CLOSURE
    null, LocationResponse.ReasonCode.F -> NomisMigrateLocationRequest.DeactivationReason.OTHER
    LocationResponse.ReasonCode.G -> NomisMigrateLocationRequest.DeactivationReason.LOCAL_WORK
    LocationResponse.ReasonCode.H -> NomisMigrateLocationRequest.DeactivationReason.STAFF_SHORTAGE
    LocationResponse.ReasonCode.I -> NomisMigrateLocationRequest.DeactivationReason.MOTHBALLED
    LocationResponse.ReasonCode.J -> NomisMigrateLocationRequest.DeactivationReason.DAMAGED
    LocationResponse.ReasonCode.K -> NomisMigrateLocationRequest.DeactivationReason.OUT_OF_USE
    LocationResponse.ReasonCode.L -> NomisMigrateLocationRequest.DeactivationReason.CELLS_RETURNING_TO_USE
  }

private fun toAttribute(type: String, code: String): NomisMigrateLocationRequest.Attributes? =
  when (type) {
    "HOU_SANI_FIT" ->
      when (code) {
        "ABD" -> NomisMigrateLocationRequest.Attributes.ANTI_BARRICADE_DOOR
        "ACB" -> NomisMigrateLocationRequest.Attributes.AUDITABLE_CELL_BELL
        "FIB" -> NomisMigrateLocationRequest.Attributes.FIXED_BED
        "MD" -> NomisMigrateLocationRequest.Attributes.METAL_DOOR
        "MOB" -> NomisMigrateLocationRequest.Attributes.MOVABLE_BED
        "PC" -> NomisMigrateLocationRequest.Attributes.PRIVACY_CURTAIN
        "PS" -> NomisMigrateLocationRequest.Attributes.PRIVACY_SCREEN
        "SCB" -> NomisMigrateLocationRequest.Attributes.STANDARD_CELL_BELL
        "SETO" -> NomisMigrateLocationRequest.Attributes.SEPARATE_TOILET
        "WD" -> NomisMigrateLocationRequest.Attributes.WOODEN_DOOR
        else -> {
          warningLogger.warn("Unknown location attribute type $type, code $code")
          null
        }
      }

    "HOU_UNIT_ATT" ->
      when (code) {
        "A" -> NomisMigrateLocationRequest.Attributes.CAT_A_CELL
        "DO" -> NomisMigrateLocationRequest.Attributes.DOUBLE_OCCUPANCY
        "ELC" -> NomisMigrateLocationRequest.Attributes.E_LIST_CELL
        "GC" -> NomisMigrateLocationRequest.Attributes.GATED_CELL
        "LC" -> NomisMigrateLocationRequest.Attributes.LISTENER_CELL
        "LF" -> NomisMigrateLocationRequest.Attributes.LOCATE_FLAT
        "MO" -> NomisMigrateLocationRequest.Attributes.MULTIPLE_OCCUPANCY
        "NSMC" -> NomisMigrateLocationRequest.Attributes.NON_SMOKER_CELL
        "OC" -> NomisMigrateLocationRequest.Attributes.OBSERVATION_CELL
        "SC" -> NomisMigrateLocationRequest.Attributes.SAFE_CELL
        "SO" -> NomisMigrateLocationRequest.Attributes.SINGLE_OCCUPANCY
        "SPC" -> NomisMigrateLocationRequest.Attributes.SPECIAL_CELL
        "WA" -> NomisMigrateLocationRequest.Attributes.WHEELCHAIR_ACCESS
        else -> {
          warningLogger.warn("Unknown location attribute type $type, code $code")
          null
        }
      }

    "HOU_USED_FOR" ->
      when (code) {
        "1" -> NomisMigrateLocationRequest.Attributes.UNCONVICTED_JUVENILES
        "2" -> NomisMigrateLocationRequest.Attributes.SENTENCED_JUVENILES
        "3" -> NomisMigrateLocationRequest.Attributes.UNCONVICTED_18_20
        "4" -> NomisMigrateLocationRequest.Attributes.SENTENCED_18_20
        "5" -> NomisMigrateLocationRequest.Attributes.UNCONVICTED_ADULTS
        "6" -> NomisMigrateLocationRequest.Attributes.SENTENCED_ADULTS
        "7", "V" -> NomisMigrateLocationRequest.Attributes.VULNERABLE_PRISONER_UNIT
        "8" -> NomisMigrateLocationRequest.Attributes.SPECIAL_UNIT
        "9" -> NomisMigrateLocationRequest.Attributes.RESETTLEMENT_HOSTEL
        "10" -> NomisMigrateLocationRequest.Attributes.HEALTHCARE_CENTRE
        "11" -> NomisMigrateLocationRequest.Attributes.NATIONAL_RESOURCE_HOSPITAL
        "12" -> NomisMigrateLocationRequest.Attributes.OTHER_SPECIFIED
        "A" -> NomisMigrateLocationRequest.Attributes.REMAND_CENTRE
        "B" -> NomisMigrateLocationRequest.Attributes.LOCAL_PRISON
        "C" -> NomisMigrateLocationRequest.Attributes.CLOSED_PRISON
        "D" -> NomisMigrateLocationRequest.Attributes.OPEN_TRAINING
        "E" -> NomisMigrateLocationRequest.Attributes.HOSTEL
        "H" -> NomisMigrateLocationRequest.Attributes.NATIONAL_RESOURCE_HOSPITAL
        "I" -> NomisMigrateLocationRequest.Attributes.CLOSED_YOUNG_OFFENDER
        "J" -> NomisMigrateLocationRequest.Attributes.OPEN_YOUNG_OFFENDER
        "K" -> NomisMigrateLocationRequest.Attributes.REMAND_UNDER_18
        "L" -> NomisMigrateLocationRequest.Attributes.SENTENCED_UNDER_18
        "R" -> NomisMigrateLocationRequest.Attributes.ECL_COMPONENT
        "T" -> NomisMigrateLocationRequest.Attributes.ADDITIONAL_SPECIAL_UNIT
        "Y" -> NomisMigrateLocationRequest.Attributes.SECOND_CLOSED_TRAINER
        "Z" -> NomisMigrateLocationRequest.Attributes.IMMIGRATION_DETAINEES
        else -> {
          warningLogger.warn("Unknown location attribute type $type, code $code")
          null
        }
      }

    "SUP_LVL_TYPE" ->
      when (code) {
        "A" -> NomisMigrateLocationRequest.Attributes.CAT_A
        "E" -> NomisMigrateLocationRequest.Attributes.CAT_A_EX
        "H" -> NomisMigrateLocationRequest.Attributes.CAT_A_HI
        "B" -> NomisMigrateLocationRequest.Attributes.CAT_B
        "C" -> NomisMigrateLocationRequest.Attributes.CAT_C
        "D" -> NomisMigrateLocationRequest.Attributes.CAT_D
        "GRANTED" -> NomisMigrateLocationRequest.Attributes.PAROLE_GRANTED
        "I" -> NomisMigrateLocationRequest.Attributes.YOI_CLOSED
        "J" -> NomisMigrateLocationRequest.Attributes.YOI_OPEN
        "V" -> NomisMigrateLocationRequest.Attributes.YOI_RESTRICTED
        "K" -> NomisMigrateLocationRequest.Attributes.YOI_SHORT_SENTENCE
        "L" -> NomisMigrateLocationRequest.Attributes.YOI_LONG_TERM_CLOSED
        "Z" -> NomisMigrateLocationRequest.Attributes.UNCLASSIFIED
        "X" -> NomisMigrateLocationRequest.Attributes.UNCATEGORISED_SENTENCED_MALE
        "LOW" -> NomisMigrateLocationRequest.Attributes.LOW
        "MED" -> NomisMigrateLocationRequest.Attributes.MEDIUM
        "HI" -> NomisMigrateLocationRequest.Attributes.HIGH
        "N/A", "NA" -> NomisMigrateLocationRequest.Attributes.NOT_APPLICABLE
        "P" -> NomisMigrateLocationRequest.Attributes.PROV_A
        "PEND" -> NomisMigrateLocationRequest.Attributes.PENDING
        "REF/REVIEW" -> NomisMigrateLocationRequest.Attributes.REF_REVIEW
        "REFUSED" -> NomisMigrateLocationRequest.Attributes.REFUSED_NO_REVIEW
        "STANDARD" -> NomisMigrateLocationRequest.Attributes.STANDARD
        "Q" -> NomisMigrateLocationRequest.Attributes.FEMALE_RESTRICTED
        "R" -> NomisMigrateLocationRequest.Attributes.FEMALE_CLOSED
        "S" -> NomisMigrateLocationRequest.Attributes.FEMALE_SEMI
        "T" -> NomisMigrateLocationRequest.Attributes.FEMALE_OPEN
        "U" -> NomisMigrateLocationRequest.Attributes.UN_SENTENCED
        "Y" -> NomisMigrateLocationRequest.Attributes.YES
        "N" -> NomisMigrateLocationRequest.Attributes.NO
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

private fun toUsage(it: UsageRequest) =
  NonResidentialUsageDto(
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

private fun toHistoryAttribute(columnName: String?): String =
  when (columnName) {
    "Unit Type" -> "RESIDENTIAL_HOUSING_TYPE"
    "Active" -> "ACTIVE"
    "Living Unit Id" -> "CODE"
    "Comments" -> "COMMENTS"
    "Accommodation Type" -> "LOCATION_TYPE"
    "Certified" -> "CERTIFIED"
    "Description" -> "DESCRIPTION"
    "Baseline CNA" -> "CERTIFIED_CAPACITY"
    "Operational Capacity" -> "OPERATIONAL_CAPACITY"
    "Deactivate Reason" -> "DEACTIVATED_REASON"
    "Proposed Reactivate Date" -> "PROPOSED_REACTIVATION_DATE"
    "Sequence" -> "ORDER_WITHIN_PARENT_LOCATION"
    "Deactivate Date" -> "DEACTIVATED_DATE"
    "Maximum Capacity" -> "CAPACITY"
    null -> "ATTRIBUTES"
    else -> throw IllegalArgumentException("Unknown history attribute column name $columnName")
  }
