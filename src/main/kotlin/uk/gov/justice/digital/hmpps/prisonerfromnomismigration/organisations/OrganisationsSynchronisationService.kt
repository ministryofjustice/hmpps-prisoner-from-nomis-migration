package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.doesOriginateInDps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class OrganisationsSynchronisationService(
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
  private val mappingApiService: OrganisationsMappingApiService,
  private val nomisApiService: OrganisationsNomisApiService,
  private val dpsApiService: OrganisationsDpsApiService,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun corporateInserted(event: CorporateEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId)
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "organisations-corporate-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      mappingApiService.getByNomisCorporateIdOrNull(nomisCorporateId = event.corporateId)?.also {
        telemetryClient.trackEvent(
          "organisations-corporate-synchronisation-created-ignored",
          telemetry,
        )
      } ?: run {
        track("organisations-corporate-synchronisation-created", telemetry) {
          nomisApiService.getCorporateOrganisation(nomisCorporateId = event.corporateId).also { organisation ->
            val dpsOrganisation = dpsApiService.createOrganisation(organisation.toDpsCreateOrganisationRequest())
            tryToCreateMapping(
              CorporateMappingDto(
                nomisId = event.corporateId,
                dpsId = "${dpsOrganisation.organisationId}",
                mappingType = CorporateMappingDto.MappingType.NOMIS_CREATED,
              ),
              telemetry,
            )
          }
        }
      }
    }
  }
  suspend fun corporateUpdated(event: CorporateEvent) {
    val telemetry =
      telemetryOf("nomisCorporateId" to event.corporateId)
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "organisations-corporate-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("organisations-corporate-synchronisation-updated", telemetry) {
        val contactId = mappingApiService.getByNomisCorporateId(nomisCorporateId = event.corporateId).dpsId.toLong().also {
          telemetry["dpsOrganisationId"] = it
        }
        val nomisCorporate = nomisApiService.getCorporateOrganisation(nomisCorporateId = event.corporateId)
        dpsApiService.updateOrganisation(contactId, nomisCorporate.toDpsUpdateOrganisationRequest())
      }
    }
  }
  suspend fun corporateDeleted(event: CorporateEvent) {
    val telemetry =
      telemetryOf("nomisCorporateId" to event.corporateId)
    mappingApiService.getByNomisCorporateIdOrNull(nomisCorporateId = event.corporateId)?.also {
      track("organisations-corporate-synchronisation-deleted", telemetry) {
        telemetry["dpsOrganisationId"] = it.dpsId
        dpsApiService.deleteOrganisation(it.dpsId.toLong())
        mappingApiService.deleteByNomisCorporateId(event.corporateId)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "organisations-corporate-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }
  suspend fun corporateAddressInserted(event: CorporateAddressEvent) {}
  suspend fun corporateAddressUpdated(event: CorporateAddressEvent) {}
  suspend fun corporateAddressDeleted(event: CorporateAddressEvent) {}

  private suspend fun tryToCreateMapping(
    mapping: CorporateMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for organisations mapping id $mapping", e)
      queueService.sendMessage(
        messageType = OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_ORGANISATION_MAPPING.name,
        synchronisationType = SynchronisationType.ORGANISATIONS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun createMapping(
    mapping: CorporateMappingDto,
  ) {
    mappingApiService.createOrganisationMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "from-nomis-sync-organisations-duplicate",
          mapOf(
            "existingNomisCorporateId" to existing.nomisId,
            "existingDpsOrganisationId" to existing.dpsId,
            "duplicateNomisCorporateId" to duplicate.nomisId,
            "duplicateDpsOrganisationId" to duplicate.dpsId,
            "type" to "ORGANISATION",
          ),
        )
      }
    }
  }

  suspend fun retryCreateCorporateMapping(retryMessage: InternalMessage<CorporateMappingDto>) {
    createMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "organisations-corporate-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
}

fun CorporateOrganisation.toDpsCreateOrganisationRequest() = SyncCreateOrganisationRequest(
  organisationId = id,
  organisationName = this.name,
  programmeNumber = programmeNumber,
  vatNumber = vatNumber,
  caseloadId = caseload?.code,
  comments = comment,
  active = active,
  deactivatedDate = expiryDate,
)
fun CorporateOrganisation.toDpsUpdateOrganisationRequest() = SyncUpdateOrganisationRequest(
  organisationName = this.name,
  programmeNumber = programmeNumber,
  vatNumber = vatNumber,
  caseloadId = caseload?.code,
  comments = comment,
  active = active,
  deactivatedDate = expiryDate,
)
