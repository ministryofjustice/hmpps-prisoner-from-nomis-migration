package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsDto.MappingType.NOMIS_CREATED

@Service
class CorePersonSynchronisationAliasIdentifierService(
  private val corePersonCprApiService: CorePersonCprApiService,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val corePersonAliasIdentifierMappingService: CorePersonAliasIdentifierMappingService,
) {
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
}
