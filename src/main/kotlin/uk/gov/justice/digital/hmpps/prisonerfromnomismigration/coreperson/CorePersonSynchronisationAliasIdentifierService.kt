package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonIdentifiersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto

@Service
class CorePersonSynchronisationAliasIdentifierService(
  override val telemetryClient: TelemetryClient,
  private val corePersonCprApiService: CorePersonCprApiService,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val corePersonAliasIdentifierMappingService: CorePersonAliasIdentifierMappingService,
) : TelemetryEnabled {
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
