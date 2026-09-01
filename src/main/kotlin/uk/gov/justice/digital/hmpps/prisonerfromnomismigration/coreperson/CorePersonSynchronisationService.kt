package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonMerge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MergeAdditionalInformationEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto

@Service
class CorePersonSynchronisationService(
  override val telemetryClient: TelemetryClient,
  private val corePersonCprApiService: CorePersonCprApiService,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val corePersonMappingService: CorePersonMappingService,
) : TelemetryEnabled {
  suspend fun synchronisePrisonerMerge(prisonerMergeEvent: PrisonerMergeDomainEvent) {
    val bookingId = prisonerMergeEvent.additionalInformation.bookingId
    val offenderNo = prisonerMergeEvent.additionalInformation.nomsNumber
    val removedOffenderNo = prisonerMergeEvent.additionalInformation.removedNomsNumber
    val telemetry = mapOf(
      "offenderNo" to offenderNo,
      "bookingId" to bookingId,
      "removedOffenderNo" to removedOffenderNo,
    )
    corePersonCprApiService.processPrisonMerge(offenderNo, prisonerMergeEvent.additionalInformation.toPrisonMerge())
    telemetryClient.trackEvent("coreperson-prisoner-merge-synchronisation", telemetry)
  }

  suspend fun resynchroniseAliasesAndIdentifiers(prisonNumber: String) {
    val aliasesAndIdentifiers = corePersonNomisApiService.getCorePerson(nomisPrisonNumber = prisonNumber).offenders
    val mapping = corePersonCprApiService.migrateCorePersonAliasesAndIdentifiers(
      prisonNumber,
      aliasesAndIdentifiers.toMigrateAliasesAndIdentifiersRequest(),
    ).toCorePersonMappingsDto(migrationType = CorePersonMappingsDto.MappingType.NOMIS_CREATED)
    corePersonMappingService.replaceMappings(mapping)
  }
}

private fun MergeAdditionalInformationEvent.toPrisonMerge(): PrisonMerge = PrisonMerge(fromPrisonNumber = this.removedNomsNumber)

class BookingException(message: String) : IllegalArgumentException(message)
