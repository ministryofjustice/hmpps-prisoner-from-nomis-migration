package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressesAndContactsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonMerge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.NomisBookingDeletedEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.NomisBookingReassignedEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
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
  private val corePersonAliasIdentifierMappingService: CorePersonAliasIdentifierMappingService,
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

  suspend fun synchroniseBookingDeleted(bookingDeletedEvent: NomisBookingDeletedEvent) {
    val telemetry = telemetryOf(
      "offenderNo" to bookingDeletedEvent.offenderIdDisplay,
      "bookingId" to bookingDeletedEvent.bookingId,
    )
    track("coreperson-booking-deleted-synchronisation", telemetry) {
      val offenderId = corePersonNomisApiService.getWorkingNameOffenderId(bookingDeletedEvent.offenderIdDisplay)
        .also { telemetry["nomisOffenderId"] = it }
      val cprAliasId = corePersonAliasIdentifierMappingService.getByNomisAliasId(offenderId).cprId
        .also { telemetry["cprAliasId"] = it }
      corePersonCprApiService.syncPrimaryName(bookingDeletedEvent.offenderIdDisplay, cprAliasId)
    }
  }

  suspend fun synchroniseBookingReassigned(bookingReassignedEvent: NomisBookingReassignedEvent) {
    // assuming for now that this will only change the from offender id
    val cprFromAliasId = corePersonAliasIdentifierMappingService.getByNomisAliasId(bookingReassignedEvent.previousOffenderId).cprId

    // Booking moved can change the working name only.
    // Yes, but can it actually change anything i.e. will it change the working name on either prisoner?
    // TODO Investigate whether this is a no-op or whether any action needs to be taken
    // Actually the FROM can change as the previous booking could have a different offender id linked
    // need to try out a booking moved to see what options there are on selection of new prisoner / alias
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
