package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.Telemetry
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.originatesInDps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.UUID

private const val TELEMETRY_PREFIX = "csras-synchronisation"

@Service
class CsraSyncService(
  private val csraNomisApiService: CsraNomisApiService,
  private val csraDpsApiService: CsraDpsApiService,
  private val csraMappingApiService: CsraMappingApiService,
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun create(event: AssessmentEvent) {
    val telemetryName = "$TELEMETRY_PREFIX-created"
    val (_, _, offenderIdDisplay, bookingId, assessmentSeq, assessmentType) = event
    val telemetry = telemetryOf(
      "bookingId" to bookingId.toString(),
      "sequence" to assessmentSeq.toString(),
      "offenderNo" to offenderIdDisplay,
      "assessmentType" to assessmentType.toString(),
    )
    if (assessmentType == "CATEGORY") {
      telemetryClient.trackEvent("$telemetryName-skipped-category", telemetry)
      return
    }
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$telemetryName-skipped", telemetry)
      return
    }
    try {
      val nomisCsra = csraNomisApiService.getCsra(event.bookingId, event.assessmentSeq)

      csraDpsApiService.sync(event.offenderIdDisplay, CsraSyncRequest(nomisCsra.toDPSCsra()))
        .apply {
          if (!this.created) {
            throw IllegalStateException("Csra ${event.bookingId} already exists in DPS")
            // probably redundant as this just depends on sending a UUID
          }
          telemetry["dpsCsraId"] = this.csraReviewId.toString()
          tryToCreateMapping(event, this.csraReviewId.toString(), telemetry)
            .also { mappingCreateResult ->
              if (mappingCreateResult == MappingResponse.MAPPING_FAILED) {
                telemetry["mapping"] = "initial-failure"
              }
              telemetryClient.trackEvent("$telemetryName-success", telemetry)
            }
        }
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "$telemetryName-failed",
        telemetry + ("error" to (e.message ?: e.javaClass.name)),
      )
      throw e
    }
  }

  suspend fun update(event: AssessmentEvent) {
    val telemetryName = "$TELEMETRY_PREFIX-updated"
    val (_, _, offenderIdDisplay, bookingId, assessmentSeq, assessmentType) = event
    val telemetry = telemetryOf(
      "bookingId" to bookingId.toString(),
      "sequence" to assessmentSeq.toString(),
      "offenderNo" to offenderIdDisplay,
      "assessmentType" to assessmentType.toString(),
    )
    if (assessmentType == "CATEGORY") {
      telemetryClient.trackEvent("$telemetryName-skipped-category", telemetry)
      return
    }
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$telemetryName-skipped", telemetry)
      return
    }
    track(telemetryName, telemetry) {
      val nomisData = csraNomisApiService.getCsra(bookingId, assessmentSeq)
      csraMappingApiService.getMappingByNomisId(bookingId, assessmentSeq)
        .also { mapping ->
          telemetry["dpsCsraId"] = mapping.dpsCsraId
          csraDpsApiService.sync(
            offenderIdDisplay,
            CsraSyncRequest(
              review = nomisData.toDPSCsra(),
              csraReviewId = UUID.fromString(mapping.dpsCsraId),
            ),
          )
        }
    }
  }

  suspend fun delete(event: AssessmentEvent) {
    val telemetryName = "$TELEMETRY_PREFIX-deleted"
    val (_, _, offenderIdDisplay, bookingId, assessmentSeq, assessmentType) = event
    val telemetry = telemetryOf(
      "bookingId" to bookingId.toString(),
      "sequence" to assessmentSeq.toString(),
      "offenderNo" to offenderIdDisplay,
      "assessmentType" to assessmentType.toString(),
    )
    if (event.assessmentType == "CATEGORY") {
      telemetryClient.trackEvent("$telemetryName-skipped-category", telemetry)
      return
    }
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$telemetryName-skipped", telemetry)
      return
    }
    TODO()
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED,
  }

  private suspend fun tryToCreateMapping(event: AssessmentEvent, csraId: String, telemetry: Telemetry): MappingResponse {
    val mapping = CsraMappingDto(
      dpsCsraId = csraId,
      nomisBookingId = event.bookingId,
      nomisSequence = event.assessmentSeq,
      offenderNo = event.offenderIdDisplay,
      mappingType = CsraMappingDto.MappingType.NOMIS_CREATED,
    )
    try {
      createMapping(mapping)
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for dpsCsra id $csraId, booking ${event.bookingId}, seq ${event.assessmentSeq}, assessment ${event.assessmentSeq}",
        e,
      )
      telemetry["original-error"] = e.message ?: e.javaClass.name
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.CSRAS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
      return MappingResponse.MAPPING_FAILED
    }
  }

  private suspend fun createMapping(mapping: CsraMappingDto) {
    csraMappingApiService.createMapping(
      mapping,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CsraMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "csras-from-nomis-sync-duplicate",
          mapOf(
            "duplicateDpsCsraId" to duplicateErrorDetails.duplicate.dpsCsraId,
            "duplicateBookingId" to duplicateErrorDetails.duplicate.nomisBookingId.toString(),
            "duplicateSequence" to duplicateErrorDetails.duplicate.nomisSequence.toString(),
            "existingDpsCsraId" to duplicateErrorDetails.existing.dpsCsraId,
            "existingBookingId" to duplicateErrorDetails.existing.nomisBookingId.toString(),
            "existingSequence" to duplicateErrorDetails.existing.nomisSequence.toString(),
          ),
        )
      }
    }
  }

  suspend fun retryCreateMapping(retryMessage: InternalMessage<CsraMappingDto>) {
    try {
      createMapping(retryMessage.body)
      telemetryClient.trackEvent("csras-mapping-created-success", retryMessage.telemetryAttributes)
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "csras-mapping-created-failure",
        retryMessage.telemetryAttributes + ("error" to (e.message ?: e.javaClass.name)),
      )
      throw e
    }
  }
}

private fun AssessmentEvent.auditMissing() = auditModuleName == null
private fun AssessmentEvent.isSourcedFromDPS() = auditModuleName.originatesInDps()
