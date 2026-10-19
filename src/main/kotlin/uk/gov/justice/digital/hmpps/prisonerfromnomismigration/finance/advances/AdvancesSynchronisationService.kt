package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.AdvanceEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceDpsApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceSynchronisationMessageType.RETRY_SYNCHRONISATION_ADVANCE_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.MoneySupport
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdvanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAdvanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

private const val TELEMETRY_PREFIX = "prisoneradvance-synchronisation"

@Service
class AdvancesSynchronisationService(
  private val nomisApiService: AdvancesNomisApiService,
  private val dpsApiService: FinanceDpsApiService,
  private val mappingService: AdvancesMappingService,
  private val queueService: SynchronisationQueueService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun resynchroniseAdvance(advanceId: Long) {
    val advance = nomisApiService.getAdvance(advanceId)
      ?: throw NotFoundException("advanceId $advanceId not found")
    dpsApiService.createAdvance(advance.toSyncAdvanceDto())
  }

  suspend fun advanceInserted(event: AdvanceEvent) {
    val advanceId = event.offenderAdvanceId
    val telemetry = telemetryOf("nomisAdvanceId" to advanceId, "prisonNumber" to event.offenderIdDisplay)

    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("prisoneradvance-synchronisation-created-skipped", telemetry)
      return
    }
    mappingService.getByNomisIdOrNull(nomisId = event.offenderAdvanceId)?.also {
      telemetryClient.trackEvent(
        "prisoneradvance-synchronisation-created-ignored",
        telemetry + ("dpsAdvanceId" to it.dpsId),
      )
    } ?: run {
      track("prisoneradvance-synchronisation-created", telemetry) {
        nomisApiService.getAdvance(advanceId)
          .also { nomisAdvance ->
            val dpsAdvance = dpsApiService.createAdvance(nomisAdvance!!.toSyncAdvanceDto())
              .also {
                telemetry["dpsAdvanceId"] = it.advanceId
              }

            tryToCreateMapping(
              AdvanceMappingDto(
                nomisAdvanceId = nomisAdvance.id,
                dpsId = dpsAdvance.advanceId.toString(),
                mappingType = AdvanceMappingDto.MappingType.NOMIS_CREATED,
              ),
              telemetry,
            )
          }
      }
    }
  }
  suspend fun deductionUpdated(event: AdvanceEvent) {
    log.info("Deduction updated event received")
  }

  private suspend fun tryToCreateMapping(
    mapping: AdvanceMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for advance id ${mapping.nomisAdvanceId},${mapping.dpsId}", e)
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_ADVANCE_MAPPING.name,
        synchronisationType = SynchronisationType.FINANCE,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateMapping(retryMessage: InternalMessage<AdvanceMappingDto>) {
    createMapping(retryMessage.body)
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-mapping-created",
      retryMessage.telemetryAttributes,
    )
  }

  private suspend fun createMapping(mapping: AdvanceMappingDto) {
    val result = mappingService.createMapping(
      mapping,
      object : ParameterizedTypeReference<DuplicateErrorResponse<AdvanceMappingDto>>() {},
    )
    result.errorResponse?.moreInfo?.let { (duplicate, existing) ->
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-duplicate",
        mapOf(
          "existingDpsAdvanceId" to existing.dpsId,
          "existingNomisAdvanceId" to existing.nomisAdvanceId,
          "duplicateDpsAdvanceId" to duplicate.dpsId,
          "duplicateNomisAdvanceId" to duplicate.nomisAdvanceId,
        ),
      )
    }
  }
}

fun PrisonerAdvanceDto.toSyncAdvanceDto() = SyncCreateAdvanceRecordRequest(
  legacyPaymentProfileId = id,
  prisonNumber = prisonNumber,
  prisonID = caseloadId,
  amount = MoneySupport.penceToPounds(advanceAmount),
  repaymentStartDate = startDate.atStartOfDay(),
  repaymentAmount = MoneySupport.penceToPounds(repaymentAmount),
  reference = reference,
  comment = comment,
  status = SyncCreateAdvanceRecordRequest.Status.valueOf(status.name),
  createdOn = createDatetime,
  createdBy = createdBy,
  legacyInformationNumber = informationNumber,
  // TODO pull in from Nomis
  legacyTransactionId = 123,
)
