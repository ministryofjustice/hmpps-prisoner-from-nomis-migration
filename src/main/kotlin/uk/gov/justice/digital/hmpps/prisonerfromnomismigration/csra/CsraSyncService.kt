package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.model.CsraSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.originatesInDps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AssessmentType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class CsraSyncService(
  private val csraNomisApiService: CsraNomisApiService,
  private val csraDpsApiService: CsraDpsApiService,
  private val csraMappingApiService: CsraMappingApiService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  val csraTypeNames = AssessmentType.entries.map { it.name }

  suspend fun create(event: AssessmentUpdateEvent) {
    if (event.assessmentType !in csraTypeNames) {
      return
    }
    if (event.auditModuleName.originatesInDps()) {
      telemetryClient.trackEvent("csras-synchronisation-created-skipped", event.toTelemetryProperties())
      return
    } else if (event.auditMissing()) {
      telemetryClient.trackEvent("csras-synchronisation-created-skipped-null", event.toTelemetryProperties())
      return
    }
    try {
      val nomisCsra = csraNomisApiService.getCsra(event.bookingId, event.assessmentSeq)

      csraDpsApiService.sync(event.offenderIdDisplay, CsraSyncRequest(nomisCsra.toDPSCreateCsra()))
        .apply {
          if (!this.created) {
            throw IllegalStateException("Csra ${event.bookingId} already exists in DPS")
            // probably redundant as this just depends on sending a UUID
          }
          tryToCreateMapping(event, this.csraReviewId.toString())
            .also { mappingCreateResult ->
              telemetryClient.trackEvent(
                "csras-synchronisation-created-success",
                event.toTelemetryProperties(
                  dpsCsraId = this.csraReviewId.toString(),
                  mappingFailed = mappingCreateResult == MappingResponse.MAPPING_FAILED,
                ),
              )
            }
        }
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "csras-synchronisation-created-failed",
        event.toTelemetryProperties() + mapOf("error" to (e.message ?: "unknown error")),
      )
      throw e
    }
  }

  suspend fun update(event: AssessmentUpdateEvent) {
    // TODO()
  }

  suspend fun delete(event: AssessmentUpdateEvent) {
    // TODO()
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED,
  }

  suspend fun tryToCreateMapping(event: AssessmentUpdateEvent, csraId: String): MappingResponse {
    val mapping = CsraMappingDto(
      dpsCsraId = csraId,
      nomisBookingId = event.bookingId,
      nomisSequence = event.assessmentSeq,
      offenderNo = event.offenderIdDisplay,
      mappingType = CsraMappingDto.MappingType.NOMIS_CREATED,
    )
    try {
      csraMappingApiService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CsraMappingDto>>() {},
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "csras-from-nomis-synch-duplicate",
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
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for dpsCsra id $csraId, booking ${event.bookingId}, seq ${event.assessmentSeq}, assessment ${event.assessmentSeq}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.CSRAS,
        message = mapping,
        telemetryAttributes = event.toTelemetryProperties(csraId),
      )
      return MappingResponse.MAPPING_FAILED
    }
  }
}

private fun AssessmentUpdateEvent.toTelemetryProperties(
  dpsCsraId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "bookingId" to this.bookingId.toString(),
  "sequence" to this.assessmentSeq.toString(),
  "offenderNo" to this.offenderIdDisplay,
) + (dpsCsraId?.let { mapOf("dpsCsraId" to it) } ?: emptyMap()) + (
  if (mappingFailed == true) mapOf("mapping" to "initial-failure") else emptyMap()
  )

private fun AssessmentUpdateEvent.auditMissing() = auditModuleName == null
private fun AssessmentUpdateEvent.isSourcedFromDPS() = auditModuleName.originatesInDps()
