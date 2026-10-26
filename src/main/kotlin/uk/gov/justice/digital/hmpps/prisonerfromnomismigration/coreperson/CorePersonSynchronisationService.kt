package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonMerge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AddressUsageByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactByNomisIdRequest.NomisContactType.EMAIL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactByNomisIdRequest.NomisContactType.PHONE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsByNomisIdsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMergeMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePersonAddressContact

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
    val nomisAddressesAndContacts = corePersonNomisApiService.getCorePersonAddressesAndContacts(nomisPrisonNumber = offenderNo)
    val existingMappingsRequest = mappingsRequest(nomisAddressesAndContacts)
    val existingMappings = corePersonMappingService.getMappingsFromNomisIds(existingMappingsRequest)
    val prisonAddressesAndContactsRequest = nomisAddressesAndContacts.toMigrateAddressesAndContactsRequest(existingMappings)
    val cprMergeResponse = corePersonCprApiService.processPrisonMerge(offenderNo, PrisonMerge(removedOffenderNo, prisonAddressesAndContactsRequest))
    val newAddressAndContactsMappings = cprMergeResponse.sysconAddressesAndContactsResponseBody.toCorePersonMappingsDto(migrationType = NOMIS_CREATED)
    corePersonMappingService.merge(
      CorePersonMergeMappingsDto(
        offenderNo,
        removedOffenderNo,
        newAddressAndContactsMappings.addresses,
        newAddressAndContactsMappings.contacts,
        newAddressAndContactsMappings.addressUsages,
      ),
    )
    telemetryClient.trackEvent("coreperson-prisoner-merge-synchronisation", telemetry)
  }

  private suspend fun mappingsRequest(addressContact: CorePersonAddressContact): CorePersonMappingsByNomisIdsRequest {
    val addresses = addressContact.addresses ?: emptyList()
    val addressByNomisIdRequest = addresses.map { it.addressId }
    val addressUsages = addresses.flatMap { address -> address.usages ?: emptyList() }
    val addressUsageByNomisIdRequests = addressUsages.map { AddressUsageByNomisIdRequest(it.addressId, it.usage) }
    val addressPhoneNumbers = addresses.flatMap { address -> address.phoneNumbers ?: emptyList() }
    val phoneNumbers = addressContact.phoneNumbers ?: emptyList()
    val emailAddresses = addressContact.emailAddresses ?: emptyList()
    val contactByNomisIdRequests = addressPhoneNumbers.map { ContactByNomisIdRequest(it.phoneId, PHONE) } +
      phoneNumbers.map { ContactByNomisIdRequest(it.phoneId, PHONE) } +
      emailAddresses.map { ContactByNomisIdRequest(it.emailAddressId, EMAIL) }
    return CorePersonMappingsByNomisIdsRequest(
      addresses = addressByNomisIdRequest,
      addressUsages = addressUsageByNomisIdRequests,
      contacts = contactByNomisIdRequests,
    )
  }
}
