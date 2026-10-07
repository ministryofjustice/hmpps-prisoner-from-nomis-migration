package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.AdvanceEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceDpsApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.MoneySupport
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_ADVANCE_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AdvanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAdvanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class AdvancesSynchronisationService(
  private val nomisApiService: AdvancesNomisApiService,
  private val dpsApiService: FinanceDpsApiService,
  private val mappingService: AdvancesMappingService,
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) : TelemetryEnabled {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun resynchroniseAdvance(advanceId: Long) {
    val advance = nomisApiService.getAdvance(advanceId)
      ?: throw NotFoundException("advanceId $advanceId not found")
    dpsApiService.syncPrisonerAdvance(advance.toSyncAdvanceDto())
  }

  suspend fun advanceInserted(event: AdvanceEvent) {
    val advanceId = event.offenderAdvanceId
    val telemetry = telemetryOf("nomisAdvanceId" to advanceId, "prisonNumber" to event.offenderIdDisplay)

    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("prisoneradvance-synchronisation-created-skipped", telemetry)
    } else {
      track("prisoneradvance-synchronisation-created", telemetry) {
        val advance = nomisApiService.getAdvance(advanceId)
          ?: throw NotFoundException("advanceId $advanceId not found")
        val dpsAdvance = dpsApiService.syncPrisonerAdvance(advance.toSyncAdvanceDto())

        tryToCreateMapping(
          AdvanceMappingDto(
            nomisAdvanceId = advance.id,
            dpsId = dpsAdvance.advanceUuid.toString(),
            mappingType = AdvanceMappingDto.MappingType.NOMIS_CREATED,
          ),
        ).also { mappingCreateResult ->
          telemetry["dpsAdvanceId"] = dpsAdvance.advanceUuid.toString()
          telemetry["mapping"] = if (mappingCreateResult == MappingResponse.MAPPING_FAILED) "initial-failure" else "success"
        }
      }
    }
  }

  private suspend fun tryToCreateMapping(mapping: AdvanceMappingDto): MappingResponse {
    try {
      createMapping(mapping)
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error("Failed to create mapping for $mapping", e)
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_ADVANCE_MAPPING.name,
        synchronisationType = SynchronisationType.FINANCE,
        message = mapping,
        telemetryAttributes = mapOf(
          "nomisAdvanceId" to mapping.nomisAdvanceId.toString(),
          "dpsAdvanceId" to mapping.dpsId,
        ),
      )
      return MappingResponse.MAPPING_FAILED
    }
  }

  private suspend fun createMapping(mapping: AdvanceMappingDto) {
    mappingService.createMapping(
      mapping,
      object : ParameterizedTypeReference<DuplicateErrorResponse<AdvanceMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "prisoneradvance-from-nomis-sync-duplicate",
          mapOf(
            "duplicateDpsAdvanceId" to duplicateErrorDetails.duplicate.dpsId,
            "duplicateNomisAdvanceId" to duplicateErrorDetails.duplicate.nomisAdvanceId.toString(),
            "existingDpsAdvanceId" to duplicateErrorDetails.existing.dpsId,
            "existingNomisAdvanceId" to duplicateErrorDetails.existing.nomisAdvanceId.toString(),
          ),
        )
      }
    }
  }

  suspend fun retryCreateMapping(retryMessage: InternalMessage<AdvanceMappingDto>) {
    createMapping(retryMessage.body)
    telemetryClient.trackEvent("prisoneradvance-synchronisation-mapping-created", retryMessage.telemetryAttributes)
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED,
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
  // TODO pull in from Nomis
  status = SyncCreateAdvanceRecordRequest.Status.ACTIVE,
  createdOn = createDatetime,
  createdBy = createdBy,
  // TODO this should actually always be set - even though nullable in nomis prisoner api it is always set in db
  legacyInformationNumber = informationNumber!!,
  // TODO pull in from Nomis
  legacyTransactionId = 123,
)
