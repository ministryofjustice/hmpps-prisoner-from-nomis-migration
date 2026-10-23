package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressUsage.AddressUsageCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressesAndContactsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonContact.Type
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonMerge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MergeAdditionalInformationEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AddressUsageByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactByNomisIdRequest.NomisContactType.EMAIL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactByNomisIdRequest.NomisContactType.PHONE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsByNomisIdsRequest

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
    val addressesAndContacts = corePersonNomisApiService.getCorePersonAddressesAndContacts(nomisPrisonNumber = offenderNo).toMigrateAddressesAndContactsRequest()
    val mappingsRequest = addressesAndContacts.mappingsRequest()
    val mappings = corePersonMappingService.getMappingsFromNomisIds(mappingsRequest)
    // populate the addresses and contacts

    corePersonCprApiService.processPrisonMerge(offenderNo, PrisonMerge(offenderNo, addressesAndContacts))

    telemetryClient.trackEvent("coreperson-prisoner-merge-synchronisation", telemetry)
  }

  private suspend fun PrisonAddressesAndContactsRequest.mappingsRequest(): CorePersonMappingsByNomisIdsRequest {
    val nomisAddressIds = addresses.map { it.nomisAddressId!! }
    val nomisAddressUsageIds = addresses.flatMap { it.addressUsage }
      .map { AddressUsageByNomisIdRequest(it.nomisAddressUsageId!!, it.addressUsageCode.value) }
    val nomisContactIds =
      (addresses.flatMap { it.contacts } + contacts)
        .map { ContactByNomisIdRequest(it.nomisContactId!!, if (it.type == Type.EMAIL) EMAIL else PHONE) }
      return CorePersonMappingsByNomisIdsRequest(
        addresses = nomisAddressIds,
        contacts = nomisContactIds,
        addressUsages = nomisAddressUsageIds,
      )
  }
}