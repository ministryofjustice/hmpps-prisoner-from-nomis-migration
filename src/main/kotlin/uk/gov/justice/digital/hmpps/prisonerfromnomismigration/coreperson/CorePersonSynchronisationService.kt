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
    val telemetry = telemetryOf(
      "offenderNo" to offenderNo,
      "bookingId" to bookingId,
      "removedOffenderNo" to removedOffenderNo,
    )
    track("coreperson-prisoner-merge-synchronisation", telemetry) {
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
    }
  }

  suspend fun synchroniseBookingDeleted(bookingDeletedEvent: NomisBookingDeletedEvent) {
    val telemetry = telemetryOf(
      "offenderNo" to bookingDeletedEvent.offenderIdDisplay,
      "bookingId" to bookingDeletedEvent.bookingId,
    )
    track("coreperson-booking-deleted-synchronisation", telemetry) {
      val workingNameOffenderId = corePersonNomisApiService.getWorkingNameOffenderId(bookingDeletedEvent.offenderIdDisplay)
        .also { telemetry["nomisOffenderId"] = it }
      val cprAliasId = corePersonAliasIdentifierMappingService.getByNomisAliasId(workingNameOffenderId).cprId
        .also { telemetry["cprAliasId"] = it }
      corePersonCprApiService.syncPrimaryName(bookingDeletedEvent.offenderIdDisplay, cprAliasId)
    }
  }

  // assuming for now that only the primary name of the prisoner the booking was moved from will change
  suspend fun synchroniseBookingReassigned(bookingReassignedEvent: NomisBookingReassignedEvent) {
    val telemetry = telemetryOf(
      "offenderNo" to bookingReassignedEvent.offenderIdDisplay,
      "previousOffenderNo" to bookingReassignedEvent.previousOffenderIdDisplay,
      "bookingId" to bookingReassignedEvent.bookingId,
      "nomisOffenderId" to bookingReassignedEvent.previousOffenderId,
    )
    track("coreperson-booking-reassigned-synchronisation", telemetry) {
      val cprAliasId = corePersonAliasIdentifierMappingService.getByNomisAliasId(bookingReassignedEvent.previousOffenderId).cprId
        .also { telemetry["cprAliasId"] = it }
      corePersonCprApiService.syncPrimaryName(bookingReassignedEvent.previousOffenderIdDisplay, cprAliasId)
    }
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
