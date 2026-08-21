package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonSynchronisationMessageType.RETRY_SYNCHRONISATION_CORE_PERSON_RELIGION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionUpdateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconReligionResponseBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion.ReligionsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

private const val TELEMETRY_PREFIX = "coreperson-beliefs-synchronisation"
typealias Telemetry = MutableMap<String, Any>

@Service
class CorePersonSynchronisationBeliefsService(
  override val telemetryClient: TelemetryClient,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val corePersonCprApiService: CorePersonCprApiService,
  private val religionsMappingService: ReligionsMappingService,
  private val queueService: SynchronisationQueueService,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun offenderBeliefCreated(event: OffenderBeliefEvent) {
    val (offenderIdDisplay) = event
    val telemetry = telemetryOf(
      "prisonNumber" to offenderIdDisplay,
      "nomisId" to event.offenderBeliefId,
    )
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-created-skipped", telemetry)
    } else {
      val mappingExists = religionsMappingService.existsReligionMappingByNomisPrisonNumber(event.offenderIdDisplay)
      if (mappingExists) {
        telemetryClient.trackEvent(
          "$TELEMETRY_PREFIX-created-ignored",
          telemetry,
        )
      } else {
        // There is no mapping so we cannot ignore this as there will be no update because no beliefs exist yet.
        createBelief(telemetry, getNomisOffenderBelief(event), offenderIdDisplay, event.offenderBeliefId)
      }
    }
  }

  suspend fun offenderBeliefUpdated(event: OffenderBeliefEvent) {
    val (offenderIdDisplay) = event
    val telemetry = telemetryOf(
      "prisonNumber" to offenderIdDisplay,
      "nomisId" to event.offenderBeliefId,
    )
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-updated-created-skipped", telemetry)
    } else {
      val allBeliefs = corePersonNomisApiService.getOffenderReligions(event.offenderIdDisplay)
      val currentBelief = allBeliefs.first()
      val currentBeliefMapping = religionsMappingService.getReligionByNomisIdOrNull(currentBelief.beliefId)
      if (currentBeliefMapping != null) {
        // This event can only be a simple update of the comments field.
        track("$TELEMETRY_PREFIX-updated", telemetry) {
          religionsMappingService.getReligionByNomisId(nomisReligionId = event.offenderBeliefId)
            .also { mapping ->
              telemetry["cprId"] = mapping.cprId
              allBeliefs.toPrisonReligionUpdateRequest(event.offenderBeliefId).apply {
                corePersonCprApiService.syncUpdateOffenderBelief(
                  offenderIdDisplay,
                  mapping.cprId,
                  this,
                )
              }
            }
        }
      } else {
        // This event indicates a new active belief has been created.
        telemetry += ("nomisId" to currentBelief.beliefId)
        createBelief(telemetry, currentBelief.toPrisonReligionHistory(true), offenderIdDisplay, currentBelief.beliefId)
      }
    }
  }

  suspend fun createBelief(telemetry: Telemetry, prisonerReligionHistory: PrisonReligionHistory, offenderIdDisplay: String, offenderBeliefId: Long) = track("$TELEMETRY_PREFIX-created", telemetry) {
    prisonerReligionHistory.apply {
      corePersonCprApiService.syncCreateOffenderBelief(
        prisonNumber = offenderIdDisplay,
        religion = this,
      ).also {
        tryToCreateMapping(
          ReligionMappingDto(
            nomisPrisonNumber = offenderIdDisplay,
            cprId = it.religionMappings.cprReligionId,
            nomisId = offenderBeliefId,
            mappingType = ReligionMappingDto.MappingType.NOMIS_CREATED,
          ),
          telemetry = telemetry + ("cprId" to it.religionMappings.cprReligionId),
        )
      }
    }
  }

  suspend fun offenderBeliefDeleted(event: OffenderBeliefEvent) {
    val telemetry = telemetryOf(
      "prisonNumber" to event.offenderIdDisplay,
      "rootOffenderId" to event.rootOffenderId,
      "nomisOffenderBeliefId" to event.offenderBeliefId,
    )
    telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-notimplemented", telemetry)
  }

  suspend fun resynchroniseOffenderBelief(prisonNumber: String) {
    val religions = corePersonNomisApiService.getOffenderReligions(nomisPrisonNumber = prisonNumber)
    val mapping = corePersonCprApiService.migrateCorePersonReligion(prisonNumber, religions.toMigrateReligionsRequest())
      .toReligionsMapping()
    religionsMappingService.replaceMappings(mapping)
  }

  fun SysconReligionResponseBody.toReligionsMapping() = ReligionsMigrationMappingDto(
    cprId = prisonNumber,
    nomisPrisonNumber = prisonNumber,
    religions = religionMappings.map {
      ReligionMigrationMappingDto(
        cprId = it.cprReligionId,
        nomisId = it.nomisReligionId.toLong(),
        nomisPrisonNumber = prisonNumber,
      )
    },
    mappingType = ReligionsMigrationMappingDto.MappingType.NOMIS_CREATED,
  )

  suspend fun getNomisOffenderBelief(event: OffenderBeliefEvent): PrisonReligionHistory = getNomisOffenderBeliefs(event).first { it.nomisReligionId == event.offenderBeliefId.toString() }

  suspend fun getNomisOffenderBeliefs(event: OffenderBeliefEvent): List<PrisonReligionHistory> = corePersonNomisApiService.getOffenderReligions(event.offenderIdDisplay).mapIndexed { i, r ->
    r.toPrisonReligionHistory(i == 0)
  }

  suspend fun OffenderBelief.toPrisonReligionHistory(current: Boolean): PrisonReligionHistory = PrisonReligionHistory(
    nomisReligionId = beliefId.toString(),
    current = current,
    comments = comments,
    startDate = startDate,
    endDate = endDate,
    religionCode = PrisonReligionHistory.ReligionCode.valueOf(belief.code),
    changeReasonKnown = changeReason ?: false,
    createDateTime = audit.createDatetime,
    createUserId = audit.createUsername,
    modifyDateTime = audit.modifyDatetime,
    modifyUserId = audit.modifyUserId,
  )

  suspend fun List<OffenderBelief>.toPrisonReligionUpdateRequest(offenderBeliefId: Long): PrisonReligionUpdateRequest = first { it.beliefId == offenderBeliefId }
    .let {
      PrisonReligionUpdateRequest(
        comments = it.comments,
        // for an update we must have the modified by fields set
        modifyDateTime = it.audit.modifyDatetime!!,
        modifyUserId = it.audit.modifyUserId!!,
      )
    }

  suspend fun retryCreateMapping(retryMessage: InternalMessage<ReligionMappingDto>) {
    createMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "${TELEMETRY_PREFIX}-mapping-created",
          retryMessage.telemetryAttributes,
        )
      }
  }

  private suspend fun tryToCreateMapping(
    mapping: ReligionMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for core person ${mapping.nomisPrisonNumber} religion id ${mapping.nomisId},${mapping.cprId}", e)
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_CORE_PERSON_RELIGION_MAPPING.name,
        synchronisationType = SynchronisationType.CORE_PERSON,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  private suspend fun createMapping(mapping: ReligionMappingDto) {
    religionsMappingService.createReligionMapping(mapping)
      .takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "$TELEMETRY_PREFIX-duplicate",
            mapOf(
              "nomisPrisonNumber" to existing!!.nomisPrisonNumber,
              "existingNomisId" to existing.nomisId,
              "existingCprId" to existing.cprId,
              "duplicateNomisId" to duplicate.nomisId,
              "duplicateCprId" to duplicate.cprId,
            ),
          )
        }
      }
  }
}

fun List<OffenderBelief>.toMigrateReligionsRequest(): PrisonReligionRequest = PrisonReligionRequest(
  religions = this.mapIndexed { i, r ->
    PrisonReligionHistory(
      nomisReligionId = r.beliefId.toString(),
      current = i == 0,
      religionCode = PrisonReligionHistory.ReligionCode.valueOf(r.belief.code),
      startDate = r.startDate,
      endDate = r.endDate,
      comments = r.comments,
      changeReasonKnown = r.changeReason ?: false,
      createDateTime = r.audit.createDatetime,
      createUserId = r.audit.createUsername,
      modifyDateTime = r.audit.modifyDatetime,
      modifyUserId = r.audit.modifyUserId,
    )
  },
)
