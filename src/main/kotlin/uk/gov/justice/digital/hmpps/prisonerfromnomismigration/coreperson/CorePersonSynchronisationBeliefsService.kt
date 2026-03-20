package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonSynchronisationMessageType.RETRY_SYNCHRONISATION_CORE_PERSON_RELIGION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligion
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
    val telemetryName = "$TELEMETRY_PREFIX-created"
    val (offenderIdDisplay) = event
    val telemetry = telemetryOf(
      "prisonNumber" to offenderIdDisplay,
      "nomisId" to event.offenderBeliefId,
    )
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$telemetryName-skipped", telemetry)
    } else {
      val mapping = religionsMappingService.getReligionByNomisIdOrNull(event.offenderBeliefId)
      if (mapping != null) {
        telemetryClient.trackEvent(
          "$telemetryName-ignored",
          telemetry + ("cprId" to mapping.cprId),
        )
      } else {
        track(telemetryName, telemetry) {
          getNomisOffenderBelief(event).apply {
            corePersonCprApiService.syncCreateOffenderBelief(
              prisonNumber = offenderIdDisplay,
              religion = this,
            ).also {
              tryToCreateMapping(
                ReligionMappingDto(
                  nomisPrisonNumber = offenderIdDisplay,
                  cprId = it.religionMappings.cprReligionId,
                  nomisId = event.offenderBeliefId,
                  mappingType = ReligionMappingDto.MappingType.NOMIS_CREATED,
                ),
                telemetry = telemetry + ("cprId" to it.religionMappings.cprReligionId),
              )
            }
          }
        }
      }
    }
  }

  suspend fun offenderBeliefUpdated(event: OffenderBeliefEvent) {
    val telemetryName = "$TELEMETRY_PREFIX-updated"
    val (offenderIdDisplay) = event
    val telemetry = telemetryOf(
      "prisonNumber" to offenderIdDisplay,
      "nomisId" to event.offenderBeliefId,
    )
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$telemetryName-skipped", telemetry)
    } else {
      track(telemetryName, telemetry) {
        religionsMappingService.getReligionByNomisId(nomisReligionId = event.offenderBeliefId)
          .also { mapping ->
            telemetry["cprId"] = mapping.cprId
            event.toPrisonReligionUpdateRequest().apply {
              corePersonCprApiService.syncUpdateOffenderBelief(
                offenderIdDisplay,
                mapping.cprId,
                this,
              )
            }
          }
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

  suspend fun getNomisOffenderBelief(event: OffenderBeliefEvent): PrisonReligion = corePersonNomisApiService.getOffenderReligions(event.offenderIdDisplay).mapIndexed { i, r ->
    PrisonReligion(
      nomisReligionId = r.beliefId.toString(),
      current = i == 0,
      comments = r.comments,
      startDate = r.startDate,
      endDate = r.endDate,
      religionCode = r.belief.code,
      changeReasonKnown = r.changeReason,
      createDateTime = r.audit.createDatetime,
      createUserId = r.audit.createUsername,
      modifyDateTime = r.audit.modifyDatetime,
      modifyUserId = r.audit.modifyUserId,
    )
  }.first { it.nomisReligionId == event.offenderBeliefId.toString() }

  suspend fun OffenderBeliefEvent.toPrisonReligionUpdateRequest(): PrisonReligionUpdateRequest = corePersonNomisApiService.getOffenderReligions(offenderIdDisplay)
    .mapIndexed { i, r -> Pair(i == 0, r) }
    .first { it.second.beliefId == offenderBeliefId }
    .let {
      PrisonReligionUpdateRequest(
        nomisReligionId = it.second.beliefId.toString(),
        current = it.first,
        comments = it.second.comments,
        endDate = it.second.endDate,
        // for an update we must have the modified by fields set
        modifyDateTime = it.second.audit.modifyDatetime!!,
        modifyUserId = it.second.audit.modifyUserId!!,
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
        synchronisationType = SynchronisationType.CORE_PERSON_RELIGION,
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
    PrisonReligion(
      nomisReligionId = r.beliefId.toString(),
      current = i == 0,
      religionCode = r.belief.code,
      startDate = r.startDate,
      endDate = r.endDate,
      comments = r.comments,
      changeReasonKnown = r.changeReason,
      createDateTime = r.audit.createDatetime,
      createUserId = r.audit.createUsername,
      modifyDateTime = r.audit.modifyDatetime,
      modifyUserId = r.audit.modifyUserId,
    )
  },
)
