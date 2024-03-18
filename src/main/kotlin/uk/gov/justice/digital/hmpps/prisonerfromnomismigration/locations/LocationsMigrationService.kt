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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.UpsertLocationRequest
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

  override suspend fun getIds(
    migrationFilter: LocationsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<LocationIdResponse> = nomisApiService.getLocationIds(pageNumber = pageNumber, pageSize = pageSize)

  override suspend fun migrateNomisEntity(context: MigrationContext<LocationIdResponse>) {
    with(context.body) {
      log.info("attempting to migrate $this")

      // Determine all valid locations for this offender pair
      val nomisLocationResponse = nomisApiService.getLocation(locationId)
      locationsMappingService.getMappingGivenNomisId(locationId)
        ?.run {
          log.info(
            """Will not migrate the location since it is migrated already, NOMIS locationId is $locationId, as part migration ${this.label ?: "NONE"} (${this.mappingType})""",
          )
        }
        ?: run {
          val upsertSyncRequest = toUpsertSyncRequest(nomisLocationResponse)
          log.debug(
            "No location mapping - sending location migrate upsert {}",
            upsertSyncRequest,
          )
          val migratedLocation = locationsService.migrateLocation(upsertSyncRequest)
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
              "migrationId" to context.migrationId,
            ),
            null,
          )
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

fun toUpsertSyncRequest(id: UUID, nomisLocationResponse: LocationResponse) =
  toUpsertSyncRequest(nomisLocationResponse).copy(id = id)

fun toUpsertSyncRequest(nomisLocationResponse: LocationResponse) =
  UpsertLocationRequest(
    prisonId = nomisLocationResponse.prisonId,
    code = nomisLocationResponse.locationCode,
    locationType = toLocationType(nomisLocationResponse.locationType),
    lastUpdatedBy = nomisLocationResponse.modifyUsername ?: nomisLocationResponse.createUsername,
    description = nomisLocationResponse.userDescription,
    comments = nomisLocationResponse.comment,
    orderWithinParentLocation = nomisLocationResponse.listSequence,
    residentialHousingType = toResidentialHousingType(nomisLocationResponse.unitType),
    parentLocationPath = nomisLocationResponse.parentLocationId
      ?.let {
        // locationsMappingService.getMappingGivenNomisId(it)!!.nomisLocationId
        nomisLocationResponse.description.substringAfter("-").substringBeforeLast("-")
      },
    capacity = if (nomisLocationResponse.capacity != null || nomisLocationResponse.operationalCapacity != null) {
      Capacity(
        nomisLocationResponse.capacity ?: 0,
        nomisLocationResponse.operationalCapacity ?: 0,
      )
    } else {
      null
    },
    certification = if (nomisLocationResponse.certified || nomisLocationResponse.cnaCapacity != null) {
      Certification(nomisLocationResponse.certified, nomisLocationResponse.cnaCapacity ?: 0)
    } else {
      null
    },
    attributes = nomisLocationResponse.profiles
      ?.mapNotNull { toAttribute(it.profileType.name, it.profileCode) }
      ?.toSet(),
    usage = nomisLocationResponse.usages?.map { toUsage(it) }?.toSet(),

    createDate = nomisLocationResponse.createDatetime,
    // lastModifiedDate - no value available as it changes with occupancy
  )

private fun toLocationType(locationType: String): UpsertLocationRequest.LocationType =
  when (locationType) {
    "WING" -> UpsertLocationRequest.LocationType.WING
    "SPUR" -> UpsertLocationRequest.LocationType.SPUR
    "LAND", "TIER" -> UpsertLocationRequest.LocationType.LANDING
    "CELL" -> UpsertLocationRequest.LocationType.CELL
    "ADJU" -> UpsertLocationRequest.LocationType.ADJUDICATION_ROOM
    "ADMI" -> UpsertLocationRequest.LocationType.ADMINISTRATION_AREA
    "APP" -> UpsertLocationRequest.LocationType.APPOINTMENTS
    "AREA" -> UpsertLocationRequest.LocationType.AREA
    "ASSO" -> UpsertLocationRequest.LocationType.ASSOCIATION
    "BOOT" -> UpsertLocationRequest.LocationType.BOOTH
    "BOX" -> UpsertLocationRequest.LocationType.BOX
    "CLAS" -> UpsertLocationRequest.LocationType.CLASSROOM
    "EXER" -> UpsertLocationRequest.LocationType.EXERCISE_AREA
    "EXTE" -> UpsertLocationRequest.LocationType.EXTERNAL_GROUNDS
    "FAIT" -> UpsertLocationRequest.LocationType.FAITH_AREA
    "GROU" -> UpsertLocationRequest.LocationType.GROUP
    "HCEL" -> UpsertLocationRequest.LocationType.HOLDING_CELL
    "HOLD" -> UpsertLocationRequest.LocationType.HOLDING_AREA
    "IGRO" -> UpsertLocationRequest.LocationType.INTERNAL_GROUNDS
    "INSI" -> UpsertLocationRequest.LocationType.INSIDE_PARTY
    "INTE" -> UpsertLocationRequest.LocationType.INTERVIEW
    "LOCA" -> UpsertLocationRequest.LocationType.LOCATION
    "MEDI" -> UpsertLocationRequest.LocationType.MEDICAL
    "MOVE" -> UpsertLocationRequest.LocationType.MOVEMENT_AREA
    "OFFI" -> UpsertLocationRequest.LocationType.OFFICE
    "OUTS" -> UpsertLocationRequest.LocationType.OUTSIDE_PARTY
    "POSI" -> UpsertLocationRequest.LocationType.POSITION
    "RESI" -> UpsertLocationRequest.LocationType.RESIDENTIAL_UNIT
    "ROOM" -> UpsertLocationRequest.LocationType.ROOM
    "RTU" -> UpsertLocationRequest.LocationType.RETURN_TO_UNIT
    "SHEL" -> UpsertLocationRequest.LocationType.SHELF
    "SPOR" -> UpsertLocationRequest.LocationType.SPORTS
    "STOR" -> UpsertLocationRequest.LocationType.STORE
    "TABL" -> UpsertLocationRequest.LocationType.TABLE
    "TRAI" -> UpsertLocationRequest.LocationType.TRAINING_AREA
    "TRRM" -> UpsertLocationRequest.LocationType.TRAINING_ROOM
    "VIDE" -> UpsertLocationRequest.LocationType.VIDEO_LINK
    "VISIT" -> UpsertLocationRequest.LocationType.VISITS
    "WORK" -> UpsertLocationRequest.LocationType.WORKSHOP
    else -> throw IllegalArgumentException("Unknown location type $locationType")
  }

private fun toResidentialHousingType(unitType: LocationResponse.UnitType?): UpsertLocationRequest.ResidentialHousingType? =
  when (unitType) {
    LocationResponse.UnitType.HC -> UpsertLocationRequest.ResidentialHousingType.HEALTHCARE
    LocationResponse.UnitType.HOLC -> UpsertLocationRequest.ResidentialHousingType.HOLDING_CELL
    LocationResponse.UnitType.NA -> UpsertLocationRequest.ResidentialHousingType.NORMAL_ACCOMMODATION
    LocationResponse.UnitType.OU -> UpsertLocationRequest.ResidentialHousingType.OTHER_USE
    LocationResponse.UnitType.REC -> UpsertLocationRequest.ResidentialHousingType.RECEPTION
    LocationResponse.UnitType.SEG -> UpsertLocationRequest.ResidentialHousingType.SEGREGATION
    LocationResponse.UnitType.SPLC -> UpsertLocationRequest.ResidentialHousingType.SPECIALIST_CELL
    null -> null
  }

private fun toAttribute(type: String, code: String): UpsertLocationRequest.Attributes? =
  when (type) {
    "HOU_SANI_FIT" ->
      when (code) {
        "ABD" -> UpsertLocationRequest.Attributes.ANTI_BARRICADE_DOOR
        "ACB" -> UpsertLocationRequest.Attributes.AUDITABLE_CELL_BELL
        "FIB" -> UpsertLocationRequest.Attributes.FIXED_BED
        "MD" -> UpsertLocationRequest.Attributes.METAL_DOOR
        "MOB" -> UpsertLocationRequest.Attributes.MOVABLE_BED
        "PC" -> UpsertLocationRequest.Attributes.PRIVACY_CURTAIN
        "PS" -> UpsertLocationRequest.Attributes.PRIVACY_SCREEN
        "SCB" -> UpsertLocationRequest.Attributes.STANDARD_CELL_BELL
        "SETO" -> UpsertLocationRequest.Attributes.SEPARATE_TOILET
        "WD" -> UpsertLocationRequest.Attributes.WOODEN_DOOR
        else -> {
          warningLogger.warn("Unknown location attribute type $type, code $code")
          null
        }
      }

    "HOU_UNIT_ATT" ->
      when (code) {
        "A" -> UpsertLocationRequest.Attributes.CAT_A_CELL
        "DO" -> UpsertLocationRequest.Attributes.DOUBLE_OCCUPANCY
        "ELC" -> UpsertLocationRequest.Attributes.E_LIST_CELL
        "GC" -> UpsertLocationRequest.Attributes.GATED_CELL
        "LC" -> UpsertLocationRequest.Attributes.LISTENER_CELL
        "LF" -> UpsertLocationRequest.Attributes.LOCATE_FLAT
        "MO" -> UpsertLocationRequest.Attributes.MULTIPLE_OCCUPANCY
        "NSMC" -> UpsertLocationRequest.Attributes.NON_SMOKER_CELL
        "OC" -> UpsertLocationRequest.Attributes.OBSERVATION_CELL
        "SC" -> UpsertLocationRequest.Attributes.SAFE_CELL
        "SO" -> UpsertLocationRequest.Attributes.SINGLE_OCCUPANCY
        "SPC" -> UpsertLocationRequest.Attributes.SPECIAL_CELL
        "WA" -> UpsertLocationRequest.Attributes.WHEELCHAIR_ACCESS
        else -> {
          warningLogger.warn("Unknown location attribute type $type, code $code")
          null
        }
      }

    "HOU_USED_FOR" ->
      when (code) {
        "1" -> UpsertLocationRequest.Attributes.UNCONVICTED_JUVENILES
        "2" -> UpsertLocationRequest.Attributes.SENTENCED_JUVENILES
        "3" -> UpsertLocationRequest.Attributes.UNCONVICTED_18_20
        "4" -> UpsertLocationRequest.Attributes.SENTENCED_18_20
        "5" -> UpsertLocationRequest.Attributes.UNCONVICTED_ADULTS
        "6" -> UpsertLocationRequest.Attributes.SENTENCED_ADULTS
        "7" -> UpsertLocationRequest.Attributes.VULNERABLE_PRISONER_UNIT
        "8" -> UpsertLocationRequest.Attributes.SPECIAL_UNIT
        "9" -> UpsertLocationRequest.Attributes.RESETTLEMENT_HOSTEL
        "10" -> UpsertLocationRequest.Attributes.HEALTHCARE_CENTRE
        "11" -> UpsertLocationRequest.Attributes.NATIONAL_RESOURCE_HOSPITAL
        "12" -> UpsertLocationRequest.Attributes.OTHER_SPECIFIED
        "A" -> UpsertLocationRequest.Attributes.REMAND_CENTRE
        "B" -> UpsertLocationRequest.Attributes.LOCAL_PRISON
        "C" -> UpsertLocationRequest.Attributes.CLOSED_PRISON
        "D" -> UpsertLocationRequest.Attributes.OPEN_TRAINING
        "E" -> UpsertLocationRequest.Attributes.HOSTEL
        "H" -> UpsertLocationRequest.Attributes.NATIONAL_RESOURCE_HOSPITAL
        "I" -> UpsertLocationRequest.Attributes.CLOSED_YOUNG_OFFENDER
        "J" -> UpsertLocationRequest.Attributes.OPEN_YOUNG_OFFENDER
        "K" -> UpsertLocationRequest.Attributes.REMAND_UNDER_18
        "L" -> UpsertLocationRequest.Attributes.SENTENCED_UNDER_18
        "R" -> UpsertLocationRequest.Attributes.ECL_COMPONENT
        "T" -> UpsertLocationRequest.Attributes.ADDITIONAL_SPECIAL_UNIT
        "V" -> UpsertLocationRequest.Attributes.VULNERABLE_PRISONER_UNIT
        "Y" -> UpsertLocationRequest.Attributes.SECOND_CLOSED_TRAINER
        "Z" -> UpsertLocationRequest.Attributes.IMMIGRATION_DETAINEES
        else -> {
          warningLogger.warn("Unknown location attribute type $type, code $code")
          null
        }
      }

    "SUP_LVL_TYPE" ->
      when (code) {
        "A" -> UpsertLocationRequest.Attributes.CAT_A
        "E" -> UpsertLocationRequest.Attributes.CAT_A_EX
        "H" -> UpsertLocationRequest.Attributes.CAT_A_HI
        "B" -> UpsertLocationRequest.Attributes.CAT_B
        "C" -> UpsertLocationRequest.Attributes.CAT_C
        "D" -> UpsertLocationRequest.Attributes.CAT_D
        "GRANTED" -> UpsertLocationRequest.Attributes.PAROLE_GRANTED
        "I" -> UpsertLocationRequest.Attributes.YOI_CLOSED
        "J" -> UpsertLocationRequest.Attributes.YOI_OPEN
        "V" -> UpsertLocationRequest.Attributes.YOI_RESTRICTED
        "K" -> UpsertLocationRequest.Attributes.YOI_SHORT_SENTENCE
        "L" -> UpsertLocationRequest.Attributes.YOI_LONG_TERM_CLOSED
        "Z" -> UpsertLocationRequest.Attributes.UNCLASSIFIED
        "X" -> UpsertLocationRequest.Attributes.UNCATEGORISED_SENTENCED_MALE
        "LOW" -> UpsertLocationRequest.Attributes.LOW
        "MED" -> UpsertLocationRequest.Attributes.MEDIUM
        "HI" -> UpsertLocationRequest.Attributes.HIGH
        "N/A", "NA" -> UpsertLocationRequest.Attributes.NOT_APPLICABLE
        "P" -> UpsertLocationRequest.Attributes.PROV_A
        "PEND" -> UpsertLocationRequest.Attributes.PENDING
        "REF/REVIEW" -> UpsertLocationRequest.Attributes.REF_REVIEW
        "REFUSED" -> UpsertLocationRequest.Attributes.REFUSED_NO_REVIEW
        "STANDARD" -> UpsertLocationRequest.Attributes.STANDARD
        "Q" -> UpsertLocationRequest.Attributes.FEMALE_RESTRICTED
        "R" -> UpsertLocationRequest.Attributes.FEMALE_CLOSED
        "S" -> UpsertLocationRequest.Attributes.FEMALE_SEMI
        "T" -> UpsertLocationRequest.Attributes.FEMALE_OPEN
        "U" -> UpsertLocationRequest.Attributes.UN_SENTENCED
        "Y" -> UpsertLocationRequest.Attributes.YES
        "N" -> UpsertLocationRequest.Attributes.NO
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
