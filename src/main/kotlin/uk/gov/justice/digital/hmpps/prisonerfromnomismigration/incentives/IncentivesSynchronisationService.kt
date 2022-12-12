package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.ReviewType.REVIEW
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.IncentiveDeletedOffenderEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.IncentiveUpsertedOffenderEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisIncentive

@Service
class IncentivesSynchronisationService(
  private val nomisApiService: NomisApiService,
  private val telemetryClient: TelemetryClient,
  private val mappingService: IncentiveMappingService,
  private val incentiveService: IncentivesService,
  private val queueService: MigrationQueueService
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseIncentive(iepEvent: IncentiveUpsertedOffenderEvent) {
    nomisApiService.getIncentive(iepEvent.bookingId, iepEvent.iepSeq).run {

      log.debug("received nomis incentive: ${this@run}")

      mappingService.findNomisIncentiveMapping(
        nomisBookingId = bookingId,
        nomisIncentiveSequence = incentiveSequence
      )?.let { incentiveMapping ->
        log.debug("found nomis incentive mapping: $incentiveMapping")
        updateIncentiveService(this@run, incentiveMapping)

        if (!this.currentIep) {
          log.info("updating current IEP $currentIep \nfollowing update to non current IEP: ${this@run}")
          // send message to synchronis current record

          resynchroniseCurrentIncentive(iepEvent.bookingId)
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
      nomisIncentiveSequence = iepEvent.iepSeq
    )?.let {
      log.debug("found incentive mapping that requires deleting: $it")
      resynchroniseCurrentIncentive(iepEvent.bookingId)
      incentiveService.synchroniseDeleteIncentive(
        bookingId = it.nomisBookingId,
        incentiveId = it.incentiveId
      )
      mappingService.deleteIncentiveMapping(
        incentiveId = it.incentiveId
      )
      telemetryClient.trackEvent(
        "incentive-delete-synchronisation",
        mapOf(
          "bookingId" to iepEvent.bookingId.toString(),
          "incentiveSequence" to iepEvent.iepSeq.toString(),
          "incentiveId" to it.incentiveId.toString(),
        ),
        null
      )
    } ?: run {
      log.warn("no incentive mapping found, ignoring the delete for ${iepEvent.bookingId} / ${iepEvent.iepSeq}")
      telemetryClient.trackEvent(
        "incentive-delete-synchronisation-ignored",
        mapOf(
          "bookingId" to iepEvent.bookingId.toString(),
          "incentiveSequence" to iepEvent.iepSeq.toString(),
        ),
        null
      )
    }
  }

  private suspend fun resynchroniseCurrentIncentive(bookingId: Long) {
    nomisApiService.getCurrentIncentive(bookingId).let { currentIep ->
      // get mapping for current IEP
      mappingService.findNomisIncentiveMapping(
        nomisBookingId = currentIep.bookingId,
        nomisIncentiveSequence = currentIep.incentiveSequence
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
          "incentiveId" to it.id.toString()
        ),
        null
      )
    }
  }

  private suspend fun updateIncentiveService(
    nomisIep: NomisIncentive,
    iepMapping: IncentiveNomisMapping
  ) {
    incentiveService.synchroniseUpdateIncentive(
      nomisIep.bookingId, iepMapping.incentiveId,
      nomisIep.toUpdateIncentive()
    )

    telemetryClient.trackEvent(
      "incentive-updated-synchronisation",
      mapOf(
        "bookingId" to nomisIep.bookingId.toString(),
        "incentiveSequence" to nomisIep.incentiveSequence.toString(),
        "currentIep" to nomisIep.currentIep.toString()
      ),
      null
    )
  }

  private suspend fun createIncentiveMapping(
    nomisIncentive: NomisIncentive,
    it: CreateIncentiveIEPResponse
  ) {
    try {
      mappingService.createNomisIncentiveSynchronisationMapping(
        nomisBookingId = nomisIncentive.bookingId,
        nomisIncentiveSequence = nomisIncentive.incentiveSequence,
        incentiveId = it.id
      )
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for incentive id ${it.id}, nomisBookingId ${nomisIncentive.bookingId}, nomsSequence ${nomisIncentive.incentiveSequence}",
        e
      )
      queueService.sendMessage(
        IncentiveMessages.RETRY_INCENTIVE_SYNCHRONISATION_MAPPING,
        MigrationContext(
          type = MigrationType.INCENTIVES, "dummy", 0,
          body = IncentiveMapping(
            nomisBookingId = nomisIncentive.bookingId,
            nomisIncentiveSequence = nomisIncentive.incentiveSequence,
            incentiveId = it.id
          )
        )
      )
    }
  }

  fun retryCreateIncentiveMapping(context: MigrationContext<IncentiveMapping>) {
    log.info("Retrying mapping creation for booking id: ${context.body.nomisBookingId}, noms seq: ${context.body.nomisIncentiveSequence}, incentive id : ${context.body.incentiveId}")
    mappingService.createNomisIncentiveSynchronisationMapping(
      nomisBookingId = context.body.nomisBookingId,
      nomisIncentiveSequence = context.body.nomisIncentiveSequence,
      incentiveId = context.body.incentiveId,
    )
  }
}
