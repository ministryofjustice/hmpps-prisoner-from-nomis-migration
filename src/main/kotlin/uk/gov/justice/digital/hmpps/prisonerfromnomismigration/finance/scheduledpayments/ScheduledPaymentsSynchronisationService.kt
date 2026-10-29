package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceDpsApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceSynchronisationMessageType.RETRY_SYNCHRONISATION_SCHEDULED_PAYMENT_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.ScheduledPaymentEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ScheduledPaymentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

private const val TELEMETRY_PREFIX = "scheduled-payments-synchronisation"

@Service
class ScheduledPaymentsSynchronisationService(
  private val nomisApiService: ScheduledPaymentsNomisApiService,
  private val dpsApiService: FinanceDpsApiService,
  private val mappingService: ScheduledPaymentsMappingService,
  private val queueService: SynchronisationQueueService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun scheduledPaymentInserted(event: ScheduledPaymentEvent) {
    val nomisId = event.offenderScheduledPaymentId
    val telemetry = telemetryOf("nomisScheduledPaymentId" to nomisId, "prisonNumber" to event.offenderIdDisplay)

    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-created-skipped", telemetry)
      return
    }
    mappingService.getByNomisIdOrNull(nomisId = nomisId)?.also {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-created-ignored",
        telemetry + ("dpsScheduledPaymentId" to it.dpsId),
      )
    } ?: run {
      track("$TELEMETRY_PREFIX-created", telemetry) {
        val nomisScheduledPayment = nomisApiService.getScheduledPayment(nomisId)
        val dpsScheduledPayment = dpsApiService.createScheduledPayment(nomisScheduledPayment.toMigrateScheduledPaymentDto())
          .also { telemetry["dpsScheduledPaymentId"] = it.subscriptionId }

        tryToCreateMapping(
          ScheduledPaymentMappingDto(
            nomisScheduledPaymentId = nomisScheduledPayment.id,
            dpsId = dpsScheduledPayment.subscriptionId.toString(),
            mappingType = ScheduledPaymentMappingDto.MappingType.NOMIS_CREATED,
          ),
          telemetry,
        )
      }
    }
  }

  private suspend fun tryToCreateMapping(
    mapping: ScheduledPaymentMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for scheduled payment id ${mapping.nomisScheduledPaymentId},${mapping.dpsId}", e)
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_SCHEDULED_PAYMENT_MAPPING.name,
        synchronisationType = SynchronisationType.FINANCE,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateMapping(retryMessage: InternalMessage<ScheduledPaymentMappingDto>) {
    createMapping(retryMessage.body)
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-mapping-created",
      retryMessage.telemetryAttributes,
    )
  }

  private suspend fun createMapping(mapping: ScheduledPaymentMappingDto) {
    val result = mappingService.createMapping(
      mapping,
      object : ParameterizedTypeReference<DuplicateErrorResponse<ScheduledPaymentMappingDto>>() {},
    )
    result.errorResponse?.moreInfo?.let { (duplicate, existing) ->
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-duplicate",
        mapOf(
          "existingDpsScheduledPaymentId" to existing.dpsId,
          "existingNomisScheduledPaymentId" to existing.nomisScheduledPaymentId,
          "duplicateDpsScheduledPaymentId" to duplicate.dpsId,
          "duplicateNomisScheduledPaymentId" to duplicate.nomisScheduledPaymentId,
        ),
      )
    }
  }
}
