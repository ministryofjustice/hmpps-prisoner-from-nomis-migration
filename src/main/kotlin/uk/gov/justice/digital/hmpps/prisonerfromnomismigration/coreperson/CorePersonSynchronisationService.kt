package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonMerge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMappingsDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMergeMappingsDto

@Service
class CorePersonSynchronisationService(
  override val telemetryClient: TelemetryClient,
  private val corePersonCprApiService: CorePersonCprApiService,
  private val corePersonAddressContactMappingService: CorePersonAddressContactMappingService,
  private val corePersonSynchronisationAddressContactService: CorePersonSynchronisationAddressContactService,
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
    val prisonAddressesAndContactsRequest = corePersonSynchronisationAddressContactService.prisonAddressesAndContactsRequest(offenderNo)
    val cprMergeResponse = corePersonCprApiService.processPrisonMerge(offenderNo, PrisonMerge(removedOffenderNo, prisonAddressesAndContactsRequest))
    val newAddressAndContactsMappings = cprMergeResponse.sysconAddressesAndContactsResponseBody.toCorePersonAddressContactMappingsDto(migrationType = NOMIS_CREATED)
    corePersonAddressContactMappingService.merge(
      CorePersonAddressContactMergeMappingsDto(
        offenderNo,
        removedOffenderNo,
        newAddressAndContactsMappings.addresses,
        newAddressAndContactsMappings.contacts,
        newAddressAndContactsMappings.addressUsages,
      ),
    )
    telemetryClient.trackEvent("coreperson-prisoner-merge-synchronisation", telemetry)
  }
}
