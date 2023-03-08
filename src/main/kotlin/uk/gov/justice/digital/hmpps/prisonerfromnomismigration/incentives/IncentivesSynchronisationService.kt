package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.ReviewType.REVIEW
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisIncentive
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class IncentivesSynchronisationService(
  private val nomisApiService: NomisApiService,
  private val telemetryClient: TelemetryClient,
  private val mappingService: IncentiveMappingService,
  private val incentiveService: IncentivesService,
  private val queueService: SynchronisationQueueService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseIncentive(iepEvent: IncentiveUpsertedOffenderEvent) {
    nomisApiService.getIncentive(iepEvent.bookingId, iepEvent.iepSeq).run {
      log.debug("received nomis incentive: ${this@run}")

      mappingService.findNomisIncentiveMapping(
        nomisBookingId = bookingId,
        nomisIncentiveSequence = incentiveSequence,
      )?.let { incentiveMapping ->
        log.debug("found nomis incentive mapping: $incentiveMapping")
        updateIncentiveService(this@run, incentiveMapping)

        if (!this.currentIep) {
          log.info("updating current IEP $currentIep \nfollowing update to non current IEP: ${this@run}")

          // nomis allows an IEP creation and updates to other IEPs in the same transaction. This delay will mitigate against an update being
          // processed before a create request (and potentially creating a duplicate in nomis)
          queueService.sendMessage(
            messageType = IncentiveSynchronisationMessageType.SYNCHRONISE_CURRENT_INCENTIVE.name,
            synchronisationType = SynchronisationType.INCENTIVES,
            message = IncentiveBooking(
              nomisBookingId = iepEvent.bookingId,
            ),
            delaySeconds = 5,
          )
        }
      } ?: run {
        log.debug("no nomis incentive mapping found")
        createIncentiveAndMapping(this@run)
      }
    }
  }

  suspend fun synchroniseDeletedIncentive(iepEvent: IncentiveDeletedOffenderEvent) {
    mappingService.findNomisIncentiveMapping(
      nomisBookingId = iepEvent.bookingId,
      nomisIncentiveSequence = iepEvent.iepSeq,
    )?.let {
      log.debug("found incentive mapping that requires deleting: $it")
      resynchroniseCurrentIncentive(iepEvent.bookingId)
      incentiveService.synchroniseDeleteIncentive(
        bookingId = it.nomisBookingId,
        incentiveId = it.incentiveId,
      )
      mappingService.deleteIncentiveMapping(
        incentiveId = it.incentiveId,
      )
      telemetryClient.trackEvent(
        "incentive-delete-synchronisation",
        mapOf(
          "bookingId" to iepEvent.bookingId.toString(),
          "incentiveSequence" to iepEvent.iepSeq.toString(),
          "incentiveId" to it.incentiveId.toString(),
        ),
        null,
      )
    } ?: run {
      log.warn("no incentive mapping found, ignoring the delete for ${iepEvent.bookingId} / ${iepEvent.iepSeq}")
      telemetryClient.trackEvent(
        "incentive-delete-synchronisation-ignored",
        mapOf(
          "bookingId" to iepEvent.bookingId.toString(),
          "incentiveSequence" to iepEvent.iepSeq.toString(),
        ),
        null,
      )
    }
  }

  suspend fun handleSynchroniseCurrentIncentiveMessage(internalMessage: InternalMessage<IncentiveBooking>) {
    resynchroniseCurrentIncentive(internalMessage.body.nomisBookingId)
  }

  private suspend fun resynchroniseCurrentIncentive(bookingId: Long) {
    nomisApiService.getCurrentIncentive(bookingId)?.let { currentIep ->
      // get mapping for current IEP
      mappingService.findNomisIncentiveMapping(
        nomisBookingId = currentIep.bookingId,
        nomisIncentiveSequence = currentIep.incentiveSequence,
      )?.let { currentIepMapping ->
        log.debug("found mapping for current IEP $currentIep ")
        updateIncentiveService(currentIep, currentIepMapping)
      } ?: run {
        log.info("no mapping found for current IEP $currentIep ")
        createIncentiveAndMapping(currentIep)
      }
    }
  }

  private suspend fun createIncentiveAndMapping(nomisIncentive: NomisIncentive) {
    incentiveService.synchroniseCreateIncentive(nomisIncentive.toIncentive(REVIEW), nomisIncentive.bookingId).also {
      createIncentiveMapping(nomisIncentive, it)
      telemetryClient.trackEvent(
        "incentive-created-synchronisation",
        mapOf(
          "bookingId" to nomisIncentive.bookingId.toString(),
          "incentiveSequence" to nomisIncentive.incentiveSequence.toString(),
          "incentiveId" to it.id.toString(),
        ),
        null,
      )
    }
  }

  private suspend fun updateIncentiveService(
    nomisIep: NomisIncentive,
    iepMapping: IncentiveNomisMapping,
  ) {
    incentiveService.synchroniseUpdateIncentive(
      nomisIep.bookingId,
      iepMapping.incentiveId,
      nomisIep.toUpdateIncentive(),
    )

    telemetryClient.trackEvent(
      "incentive-updated-synchronisation",
      mapOf(
        "bookingId" to nomisIep.bookingId.toString(),
        "incentiveSequence" to nomisIep.incentiveSequence.toString(),
        "incentiveId" to iepMapping.incentiveId.toString(),
        "currentIep" to nomisIep.currentIep.toString(),
      ),
      null,
    )
  }

  private suspend fun createIncentiveMapping(
    nomisIncentive: NomisIncentive,
    it: CreateIncentiveIEPResponse,
  ) {
    try {
      mappingService.createNomisIncentiveSynchronisationMapping(
        IncentiveNomisMapping(
          nomisBookingId = nomisIncentive.bookingId,
          nomisIncentiveSequence = nomisIncentive.incentiveSequence,
          incentiveId = it.id,
          mappingType = "NOMIS_CREATED",
        ),
      )
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for incentive id ${it.id}, nomisBookingId ${nomisIncentive.bookingId}, nomsSequence ${nomisIncentive.incentiveSequence}",
        e,
      )
      queueService.sendMessage(
        SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING.name,
        SynchronisationType.INCENTIVES,
        IncentiveNomisMapping(
          nomisBookingId = nomisIncentive.bookingId,
          nomisIncentiveSequence = nomisIncentive.incentiveSequence,
          incentiveId = it.id,
          mappingType = "NOMIS_CREATED",
        ),
      )
    }
  }

  suspend fun retryCreateIncentiveMapping(internalMessage: InternalMessage<IncentiveNomisMapping>) {
    log.info("Retrying mapping creation for booking id: ${internalMessage.body.nomisBookingId}, noms seq: ${internalMessage.body.nomisIncentiveSequence}, incentive id : ${internalMessage.body.incentiveId}")
    mappingService.createNomisIncentiveSynchronisationMapping(
      incentiveNomisMapping = internalMessage.body,
    )
  }
}
