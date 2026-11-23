package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsResult
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
      val prisonMergeRequest = constructPrisonMerge(prisonNumber = offenderNo, removedPrisonNumber = removedOffenderNo)
      val cprMergeResponse = corePersonCprApiService.processPrisonMerge(offenderNo, prisonMergeRequest)
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
      syncPrimaryName(bookingDeletedEvent.offenderIdDisplay, telemetry)
    }
  }

  // assuming for now that only the primary name of the prisoner the booking was moved from will change
  suspend fun synchroniseBookingReassigned(bookingReassignedEvent: NomisBookingReassignedEvent) {
    val telemetry = telemetryOf(
      "offenderNo" to bookingReassignedEvent.offenderIdDisplay,
      "previousOffenderNo" to bookingReassignedEvent.previousOffenderIdDisplay,
      "bookingId" to bookingReassignedEvent.bookingId,
    )
    track("coreperson-booking-reassigned-synchronisation", telemetry) {
      // could be that they have moved an old booking so still need to get the working name from NOMIS
      syncPrimaryName(bookingReassignedEvent.previousOffenderIdDisplay, telemetry)
    }
  }

  private suspend fun syncPrimaryName(prisonNumber: String, telemetry: MutableMap<String, Any>) {
    val workingNameOffenderId = corePersonNomisApiService.getWorkingNameOffenderId(prisonNumber)
      .also { telemetry["nomisOffenderId"] = it }
    val cprAliasId = corePersonAliasIdentifierMappingService.getByNomisAliasId(workingNameOffenderId).cprId
      .also { telemetry["cprAliasId"] = it }
    corePersonCprApiService.syncPrimaryName(prisonNumber, cprAliasId)
  }

  private suspend fun constructPrisonMerge(prisonNumber: String, removedPrisonNumber: String): PrisonMerge {
    val nomisAddressesAndContacts = corePersonNomisApiService.getCorePersonAddressesAndContacts(nomisPrisonNumber = prisonNumber)
    val addressesRequest = nomisAddressesAndContacts.toMappingsByNomisIdsRequest()

    val nomisAliasesAndIdentifiers = corePersonNomisApiService.getOffenderAliasesAndIdentifiers(nomisPrisonNumber = prisonNumber)
    val aliasesRequest = nomisAliasesAndIdentifiers.toMappingsByNomisIdsRequest()

    val existingMappings = corePersonMappingService.getMappingsFromNomisIds(
      CorePersonMappingsByNomisIdsRequest(
        aliases = aliasesRequest.aliases,
        identifiers = aliasesRequest.identifiers,
        addresses = addressesRequest.addresses,
        contacts = addressesRequest.contacts,
        addressUsages = addressesRequest.addressUsages,
      ),
    )
    return PrisonMerge(
      removedPrisonNumber,
      nomisAddressesAndContacts.toMigrateAddressesAndContactsRequest(
        CorePersonAddressContactMappingsResult(
          addresses = existingMappings.addresses,
          contacts = existingMappings.contacts,
          addressUsages = existingMappings.addressUsages,
        ),
      ),
      nomisAliasesAndIdentifiers.toMigrateAliasesAndIdentifiersRequest(
        CorePersonAliasIdentifierMappingsResult(
          aliases = existingMappings.aliases,
          identifiers = existingMappings.identifiers,
        ),
      ),
    )
  }
}
