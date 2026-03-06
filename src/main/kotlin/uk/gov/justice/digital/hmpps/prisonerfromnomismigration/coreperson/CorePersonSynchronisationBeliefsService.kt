package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonSynchronisationMessageType.RETRY_SYNCHRONISATION_CORE_PERSON_RELIGION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionUpdateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion.ReligionsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.UUID

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

  suspend fun offenderBeliefUpdated(event: OffenderBeliefEvent) {
    val (offenderIdDisplay) = event
    val telemetry = telemetryOf(
      "offenderNo" to offenderIdDisplay,
    )
    track(TELEMETRY_PREFIX, telemetry) {
      // TODO: work out if we need to grab all the religions or just the one we need
      corePersonNomisApiService.getOffenderReligions(event.offenderIdDisplay).mapIndexed { i, r ->
        PrisonReligionUpdateRequest(
          nomisReligionId = r.beliefId.toString(),
          current = i == 0,
          comments = r.comments,
          verified = r.verified,
          endDate = r.endDate,
          modifyDateTime = r.audit.modifyDatetime ?: r.audit.createDatetime,
          modifyUserId = r.audit.modifyUserId ?: r.audit.createUsername,
        )
      }.first { it.nomisReligionId == event.offenderBeliefId.toString() }.apply {
        corePersonCprApiService.syncUpdateOffenderBelief(
          prisonNumber = offenderIdDisplay,
          // TODO: grab the cpr religion id from the mapping service
          cprReligionId = UUID.randomUUID().toString(),
          religion = this,
        )
      }
    }
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

  suspend fun getNomisOffenderBelief(event: OffenderBeliefEvent): PrisonReligion = corePersonNomisApiService.getOffenderReligions(event.offenderIdDisplay).mapIndexed { i, r ->
    PrisonReligion(
      nomisReligionId = r.beliefId.toString(),
      current = i == 0,
      comments = r.comments,
      verified = r.verified,
      startDate = r.startDate,
      endDate = r.endDate,
      religionCode = r.belief.code,
      changeReasonKnown = r.changeReason,
      modifyDateTime = r.audit.modifyDatetime ?: r.audit.createDatetime,
      modifyUserId = r.audit.modifyUserId ?: r.audit.createUsername,
    )
  }.first { it.nomisReligionId == event.offenderBeliefId.toString() }

  suspend fun offenderBeliefDeleted(event: OffenderBeliefEvent) {
    val telemetry = telemetryOf(
      "prisonNumber" to event.offenderIdDisplay,
      "rootOffenderId" to event.rootOffenderId,
      "nomisOffenderBeliefId" to event.offenderBeliefId,
    )
    telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-notimplemented", telemetry)
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
              "type" to "RELIGION",
            ),
          )
        }
      }
  }
}
