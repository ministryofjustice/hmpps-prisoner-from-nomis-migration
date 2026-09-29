package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonEmailAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class CorePersonSynchronisationAddressContactService(
  override val telemetryClient: TelemetryClient,
  private val corePersonCprApiService: CorePersonCprApiService,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val corePersonMappingService: CorePersonMappingService,
  private val queueService: SynchronisationQueueService,
) : TelemetryEnabled {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun resynchroniseAddressesAndContacts(prisonNumber: String) {
    val addressesAndContacts = corePersonNomisApiService.getCorePersonAddressesAndContacts(nomisPrisonNumber = prisonNumber)
    val mapping = corePersonCprApiService.migrateCorePersonAddressesAndContacts(
      prisonNumber,
      addressesAndContacts.toMigrateAddressesAndContactsRequest(),
    ).toCorePersonMappingsDto(migrationType = CorePersonMappingsDto.MappingType.NOMIS_CREATED)
    corePersonMappingService.replaceMappings(mapping)
  }

  suspend fun offenderEmailAdded(event: OffenderEmailEvent) {
    val telemetry =
      telemetryOf(
        "nomisOffenderId" to event.offenderId,
        "cprContactId" to event.offenderId,
        "nomisInternetAddressId" to event.internetAddressId,
      )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("coreperson-email-synchronisation-created-skipped", telemetry)
    } else {
      corePersonMappingService.getByNomisEmailIdOrNull(nomisInternetAddressId = event.internetAddressId)?.also {
        telemetryClient.trackEvent(
          "coreperson-email-synchronisation-created-ignored",
          telemetry + ("cprContactEmailId" to it.cprId),
        )
      } ?: run {
        track("coreperson-email-synchronisation-created", telemetry) {
          corePersonNomisApiService.getOffenderEmail(
            emailAddressId = event.internetAddressId,
            offenderId = event.offenderId,
          ).also { nomisAddress ->
            val cprEmail =
              corePersonCprApiService.syncCreateEmail(
                prisonNumber = event.offenderIdDisplay,
                email = nomisAddress.toPrisonEmailAddressRequest(),
              )
                .also {
                  telemetry["cprContactEmailId"] = it.cprContactId
                }
            val mapping = CorePersonEmailAddressMappingDto(
              nomisId = event.internetAddressId,
              cprId = cprEmail.cprContactId,
              mappingType = CorePersonEmailAddressMappingDto.MappingType.NOMIS_CREATED,
              nomisPrisonNumber = event.offenderIdDisplay,
            )

            tryToCreateMapping(mapping, telemetry)
          }
        }
      }
    }
  }

  suspend fun offenderEmailUpdated(event: OffenderEmailEvent) {
    val telemetry =
      telemetryOf("nomisOffenderId" to event.offenderId, "cprContactId" to event.offenderId, "nomisInternetAddressId" to event.internetAddressId)

    if (event.originatesInDps) {
      telemetryClient.trackEvent("coreperson-email-synchronisation-updated-skipped", telemetry)
    } else {
      track("coreperson-email-synchronisation-updated", telemetry) {
        val cprContactEmailId =
          corePersonMappingService.getByNomisEmailId(nomisInternetAddressId = event.internetAddressId).cprId.also {
            telemetry["cprContactEmailId"] = it
          }
        val nomisAddress = corePersonNomisApiService.getOffenderEmail(offenderId = event.offenderId, emailAddressId = event.internetAddressId)
        corePersonCprApiService.syncUpdateEmail(
          event.offenderIdDisplay,
          cprContactEmailId,
          nomisAddress.toPrisonEmailAddressRequest(),
        )
      }
    }
  }

  suspend fun offenderEmailDeleted(event: OffenderEmailEvent) {
    val telemetry =
      telemetryOf("nomisOffenderId" to event.offenderId, "cprContactId" to event.offenderId, "nomisInternetAddressId" to event.internetAddressId)

    corePersonMappingService.getByNomisEmailIdOrNull(nomisInternetAddressId = event.internetAddressId)?.also {
      track("coreperson-email-synchronisation-deleted", telemetry) {
        telemetry["cprContactEmailId"] = it.cprId
        corePersonCprApiService.syncDeleteEmail(prisonNumber = event.offenderIdDisplay, cprContactId = it.cprId)
        corePersonMappingService.deleteByNomisEmailId(event.internetAddressId)
      }
    } ?: run {
      telemetryClient.trackEvent("coreperson-email-synchronisation-deleted-ignored", telemetry)
    }
  }

  private suspend fun tryToCreateMapping(
    mapping: CorePersonEmailAddressMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createEmailMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for person restriction id $mapping", e)
      queueService.sendMessage(
        messageType = CorePersonSynchronisationMessageType.RETRY_SYNCHRONISATION_EMAIL_MAPPING.name,
        synchronisationType = SynchronisationType.CORE_PERSON,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateEmailMapping(retryMessage: InternalMessage<CorePersonEmailAddressMappingDto>) {
    createEmailMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "coreperson-email-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
  private suspend fun createEmailMapping(
    mapping: CorePersonEmailAddressMappingDto,
  ) {
    corePersonMappingService.createEmailMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "coreperson-email-mapping-synchronisation-duplicate",
          mapOf(
            "nomisPrisonNumber" to existing.nomisPrisonNumber,
            "existingNomisInternetAddressId" to existing.nomisId,
            "existingCprContactId" to existing.cprId,
            "duplicateNomisInternetAddressId" to duplicate.nomisId,
            "duplicateCprContactId" to duplicate.cprId,
            "type" to "EMAIL",
          ),
        )
      }
    }
  }
}
