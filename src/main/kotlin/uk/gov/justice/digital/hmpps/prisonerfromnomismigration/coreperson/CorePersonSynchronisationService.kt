package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressesAndContactsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonMerge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMappingsDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMergeMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsByNomisIdsRequest

@Service
class CorePersonSynchronisationService(
  override val telemetryClient: TelemetryClient,
  private val corePersonCprApiService: CorePersonCprApiService,
  private val corePersonMappingService: CorePersonMappingService,
  private val corePersonNomisApiService: CorePersonNomisApiService,
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
    val prisonAddressesAndContactsRequest = prisonAddressesAndContactsRequestForMerge(offenderNo)
    val cprMergeResponse = corePersonCprApiService.processPrisonMerge(offenderNo, PrisonMerge(removedOffenderNo, prisonAddressesAndContactsRequest))
    val newAddressAndContactsMappings = cprMergeResponse.sysconAddressesAndContactsResponseBody.toCorePersonAddressContactMappingsDto(migrationType = NOMIS_CREATED)
    corePersonMappingService.merge(
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

  private suspend fun prisonAddressesAndContactsRequestForMerge(prisonNumber: String): PrisonAddressesAndContactsRequest {
    val nomisAddressesAndContacts = corePersonNomisApiService.getCorePersonAddressesAndContacts(nomisPrisonNumber = prisonNumber)
    val request = nomisAddressesAndContacts.toMappingsByNomisIdsRequest()
    val existingMappings = corePersonMappingService.getMappingsFromNomisIds(
      CorePersonMappingsByNomisIdsRequest(
        aliases = emptyList(),
        identifiers = emptyList(),
        addresses = request.addresses,
        contacts = request.contacts,
        addressUsages = request.addressUsages,
      ),
    )
    return nomisAddressesAndContacts.toMigrateAddressesAndContactsRequest(
      CorePersonAddressContactMappingsResult(
        addresses = existingMappings.addresses,
        contacts = existingMappings.contacts,
        addressUsages = existingMappings.addressUsages,
      ),
    )
  }
}
