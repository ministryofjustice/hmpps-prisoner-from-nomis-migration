package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonIdentifiersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class CorePersonSynchronisationAliasIdentifierService(
  override val telemetryClient: TelemetryClient,
  private val corePersonCprApiService: CorePersonCprApiService,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val corePersonAliasIdentifierMappingService: CorePersonAliasIdentifierMappingService,
  private val queueService: SynchronisationQueueService,
) : TelemetryEnabled {
  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  suspend fun resynchroniseAliasesAndIdentifiers(prisonNumber: String) {
    val aliases = corePersonNomisApiService.getOffenderAliasesAndIdentifiers(nomisPrisonNumber = prisonNumber)
    val mapping = corePersonCprApiService.migrateCorePersonAliasesAndIdentifiers(
      prisonNumber,
      aliases.toMigrateAliasesAndIdentifiersRequest(
        existingMappings = corePersonAliasIdentifierMappingService.getMappingsFromNomisIds(aliases.toMappingsByNomisIdsRequest()),
      ),
    ).toCorePersonAliasIdentifierMappingsDto(mappingType = NOMIS_CREATED)
    corePersonAliasIdentifierMappingService.replaceMappings(mapping)
  }

  suspend fun offenderAdded(event: OffenderEvent) {
    val telemetry = aliasTelemetry(event)

    if (event.originatesInDps) {
      telemetryClient.trackEvent("coreperson-alias-synchronisation-created-skipped", telemetry)
      return
    }
    corePersonAliasIdentifierMappingService.getByNomisAliasIdOrNull(nomisOffenderId = event.offenderId)?.also {
      telemetryClient.trackEvent(
        "coreperson-alias-synchronisation-created-ignored",
        telemetry + ("cprAliasId" to it.cprId),
      )
    } ?: run {
      track("coreperson-alias-synchronisation-created", telemetry) {
        val nomisAlias = corePersonNomisApiService.getOffenderAlias(offenderId = event.offenderId)
        val cprAlias = corePersonCprApiService.syncCreateAlias(
          prisonNumber = event.offenderIdDisplay,
          alias = nomisAlias.toPrisonAlias().copy(identifiers = emptyList()),
        ).also {
          telemetry["cprAliasId"] = it.cprAliasId
        }
        val mapping = OffenderAliasMappingDto(
          cprId = cprAlias.cprAliasId,
          nomisOffenderId = event.offenderId,
          nomisPrisonNumber = event.offenderIdDisplay,
          mappingType = OffenderAliasMappingDto.MappingType.NOMIS_CREATED,
        )
        tryToCreateAliasMapping(mapping, telemetry)
      }
    }
  }

  suspend fun offenderUpdated(event: OffenderEvent) {
    val telemetry = aliasTelemetry(event)

    if (event.originatesInDps) {
      telemetryClient.trackEvent("coreperson-alias-synchronisation-updated-skipped", telemetry)
      return
    }
    track("coreperson-alias-synchronisation-updated", telemetry) {
      val cprAliasId = corePersonAliasIdentifierMappingService.getByNomisAliasId(nomisOffenderId = event.offenderId).cprId.also {
        telemetry["cprAliasId"] = it
      }
      val nomisAlias = corePersonNomisApiService.getOffenderAlias(offenderId = event.offenderId)
      corePersonCprApiService.syncUpdateAlias(
        prisonNumber = event.offenderIdDisplay,
        cprAliasId = cprAliasId,
        alias = nomisAlias.toPrisonAlias().copy(cprAliasId = cprAliasId, identifiers = emptyList()),
      )
    }
  }

  suspend fun offenderDeleted(event: OffenderEvent) {
    val telemetry = aliasTelemetry(event)

    if (event.originatesInDps) {
      telemetryClient.trackEvent("coreperson-alias-synchronisation-deleted-skipped", telemetry)
      return
    }
    corePersonAliasIdentifierMappingService.getByNomisAliasIdOrNull(nomisOffenderId = event.offenderId)?.also { mapping ->
      track("coreperson-alias-synchronisation-deleted", telemetry) {
        telemetry["cprAliasId"] = mapping.cprId
        corePersonCprApiService.syncDeleteAlias(prisonNumber = event.offenderIdDisplay, cprAliasId = mapping.cprId)
        corePersonAliasIdentifierMappingService.deleteByNomisAliasId(nomisOffenderId = event.offenderId)
      }
    } ?: run {
      telemetryClient.trackEvent("coreperson-alias-synchronisation-deleted-ignored", telemetry)
    }
  }

  private fun aliasTelemetry(event: OffenderEvent) = telemetryOf(
    "nomisPrisonNumber" to event.offenderIdDisplay,
    "nomisOffenderId" to event.offenderId,
  )

  private suspend fun tryToCreateAliasMapping(
    mapping: OffenderAliasMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createAliasMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for alias $mapping", e)
      queueService.sendMessage(
        messageType = CorePersonSynchronisationMessageType.RETRY_SYNCHRONISATION_ALIAS_MAPPING.name,
        synchronisationType = SynchronisationType.CORE_PERSON,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateAliasMapping(retryMessage: InternalMessage<OffenderAliasMappingDto>) {
    createAliasMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent("coreperson-alias-mapping-synchronisation-created", retryMessage.telemetryAttributes)
      }
  }

  private suspend fun createAliasMapping(mapping: OffenderAliasMappingDto) {
    corePersonAliasIdentifierMappingService.createAliasMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "coreperson-alias-mapping-synchronisation-duplicate",
          mapOf(
            "nomisPrisonNumber" to existing.nomisPrisonNumber,
            "existingNomisOffenderId" to existing.nomisOffenderId,
            "existingCprAliasId" to existing.cprId,
            "duplicateNomisOffenderId" to duplicate.nomisOffenderId,
            "duplicateCprAliasId" to duplicate.cprId,
            "type" to "ALIAS",
          ),
        )
      }
    }
  }

  suspend fun offenderIdentifierUpdated(event: OffenderIdentifierEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
      "nomisIdentifierSequence" to event.offenderIdSeq,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("coreperson-identifier-synchronisation-updated-skipped", telemetry)
      return
    }

    track("coreperson-identifier-synchronisation-updated", telemetry) {
      val cprAliasId = corePersonAliasIdentifierMappingService.getByNomisAliasId(nomisOffenderId = event.offenderId).cprId.also {
        telemetry["cprAliasId"] = it
      }
      val nomisIdentifiers = corePersonNomisApiService.getOffenderIdentifiers(offenderId = event.offenderId)
      val existingMappings = CorePersonAliasIdentifierMappingsResult(
        aliases = emptyList(),
        identifiers = corePersonAliasIdentifierMappingService.getIdentifierMappingsByNomisOffenderId(nomisOffenderId = event.offenderId),
      )
      val response = corePersonCprApiService.syncReplaceIdentifiers(
        prisonNumber = event.offenderIdDisplay,
        cprAliasId = cprAliasId,
        request = PrisonIdentifiersRequest(identifiers = nomisIdentifiers.map { it.toPrisonIdentifier(existingMappings) }),
      )
      val mappings = response.identifiersMappings.map {
        OffenderIdentifierMappingDto(
          cprId = it.cprIdentifierId,
          nomisOffenderId = it.nomisIdentifierId.nomisOffenderId,
          nomisIdentifierSequence = it.nomisIdentifierId.nomisSequence,
          nomisPrisonNumber = event.offenderIdDisplay,
          mappingType = OffenderIdentifierMappingDto.MappingType.NOMIS_CREATED,
        )
      }

      corePersonAliasIdentifierMappingService.replaceIdentifierMappings(nomisOffenderId = event.offenderId, mappings = mappings)
    }
  }
}
