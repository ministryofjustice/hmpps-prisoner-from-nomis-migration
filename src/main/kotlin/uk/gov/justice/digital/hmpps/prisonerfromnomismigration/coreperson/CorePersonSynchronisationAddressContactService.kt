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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonEmailAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonPhoneMappingDto
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
              corePersonCprApiService.syncCreateContact(
                prisonNumber = event.offenderIdDisplay,
                contact = nomisAddress.toPrisonEmailAddressRequest(),
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
        corePersonCprApiService.syncUpdateContact(
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
        corePersonCprApiService.syncDeleteContact(prisonNumber = event.offenderIdDisplay, cprContactId = it.cprId)
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

  suspend fun offenderAddressAdded(event: OffenderAddressEvent) {
    val telemetry = telemetryOf(
      "prisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.ownerId,
      "nomisAddressId" to event.addressId,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("coreperson-address-synchronisation-created-skipped", telemetry)
    } else {
      corePersonMappingService.getByNomisAddressIdOrNull(nomisAddressId = event.addressId)?.also {
        telemetryClient.trackEvent(
          "coreperson-address-synchronisation-created-ignored",
          telemetry + ("cprAddressId" to it.cprId),
        )
      } ?: run {
        track("coreperson-address-synchronisation-created", telemetry) {
          val prisonNumber = event.offenderIdDisplay
          val nomisAddress = corePersonNomisApiService.getOffenderAddress(event.ownerId, event.addressId)
          val cprAddress = corePersonCprApiService.syncCreateAddress(
            prisonNumber = prisonNumber,
            address = nomisAddress.toPrisonAddressRequest(),
          ).also {
            telemetry["cprAddressId"] = it.cprAddressId
          }
          val mapping = CorePersonAddressMappingDto(
            nomisId = event.addressId,
            cprId = cprAddress.cprAddressId,
            nomisPrisonNumber = prisonNumber,
            mappingType = CorePersonAddressMappingDto.MappingType.NOMIS_CREATED,
          )
          tryToCreateAddressMapping(mapping, telemetry)
        }
      }
    }
  }

  suspend fun offenderAddressUpdated(event: OffenderAddressEvent) {
    val telemetry = telemetryOf(
      "prisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.ownerId,
      "nomisAddressId" to event.addressId,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("coreperson-address-synchronisation-updated-skipped", telemetry)
    } else {
      track("coreperson-address-synchronisation-updated", telemetry) {
        val mapping = corePersonMappingService.getByNomisAddressId(event.addressId)
        telemetry["cprAddressId"] = mapping.cprId
        val nomisAddress = corePersonNomisApiService.getOffenderAddress(event.ownerId, event.addressId)
        corePersonCprApiService.syncUpdateAddress(
          prisonNumber = mapping.nomisPrisonNumber,
          cprAddressId = mapping.cprId,
          address = nomisAddress.toPrisonAddressRequest(),
        )
      }
    }
  }

  suspend fun offenderAddressDeleted(event: OffenderAddressEvent) {
    val telemetry = telemetryOf(
      "prisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.ownerId,
      "nomisAddressId" to event.addressId,
    )
    corePersonMappingService.getByNomisAddressIdOrNull(event.addressId)?.also { mapping ->
      track("coreperson-address-synchronisation-deleted", telemetry) {
        telemetry["cprAddressId"] = mapping.cprId
        corePersonCprApiService.syncDeleteAddress(mapping.nomisPrisonNumber, mapping.cprId)
        corePersonMappingService.deleteByNomisAddressId(event.addressId)
      }
    } ?: run {
      telemetryClient.trackEvent("coreperson-address-synchronisation-deleted-ignored", telemetry)
    }
  }

  private suspend fun tryToCreateAddressMapping(
    mapping: CorePersonAddressMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createAddressMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for address id $mapping", e)
      queueService.sendMessage(
        messageType = CorePersonSynchronisationMessageType.RETRY_SYNCHRONISATION_ADDRESS_MAPPING.name,
        synchronisationType = SynchronisationType.CORE_PERSON,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateAddressMapping(retryMessage: InternalMessage<CorePersonAddressMappingDto>) {
    createAddressMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent("coreperson-address-mapping-synchronisation-created", retryMessage.telemetryAttributes)
      }
  }

  private suspend fun createAddressMapping(mapping: CorePersonAddressMappingDto) {
    corePersonMappingService.createAddressMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "coreperson-address-mapping-synchronisation-duplicate",
          mapOf(
            "nomisPrisonNumber" to existing.nomisPrisonNumber,
            "existingNomisAddressId" to existing.nomisId,
            "existingCprAddressId" to existing.cprId,
            "duplicateNomisAddressId" to duplicate.nomisId,
            "duplicateCprAddressId" to duplicate.cprId,
            "type" to "ADDRESS",
          ),
        )
      }
    }
  }

  suspend fun offenderPhoneAdded(event: OffenderPhoneEvent) {
    val telemetry = phoneTelemetry(event)

    if (event.originatesInDps) {
      telemetryClient.trackEvent("coreperson-phone-synchronisation-created-skipped", telemetry)
    } else {
      corePersonMappingService.getByNomisPhoneIdOrNull(event.phoneId)?.also {
        telemetryClient.trackEvent(
          "coreperson-phone-synchronisation-created-ignored",
          telemetry + ("cprPhoneId" to it.cprId),
        )
      } ?: run {
        track("coreperson-phone-synchronisation-created", telemetry) {
          val nomisPhone = getOffenderPhone(event)
          val cprPhone = corePersonCprApiService.syncCreateContact(
            prisonNumber = event.offenderIdDisplay,
            contact = nomisPhone.toPrisonPhoneNumberRequest(),
          ).also {
            telemetry["cprPhoneId"] = it.cprContactId
          }
          tryToCreatePhoneMapping(
            CorePersonPhoneMappingDto(
              nomisId = event.phoneId,
              cprId = cprPhone.cprContactId,
              nomisPrisonNumber = event.offenderIdDisplay,
              mappingType = CorePersonPhoneMappingDto.MappingType.NOMIS_CREATED,
            ),
            telemetry,
          )
        }
      }
    }
  }

  suspend fun offenderPhoneUpdated(event: OffenderPhoneEvent) {
    val telemetry = phoneTelemetry(event)

    if (event.originatesInDps) {
      telemetryClient.trackEvent("coreperson-phone-synchronisation-updated-skipped", telemetry)
    } else {
      track("coreperson-phone-synchronisation-updated", telemetry) {
        val mapping = corePersonMappingService.getByNomisPhoneId(event.phoneId)
        telemetry["cprPhoneId"] = mapping.cprId
        val nomisPhone = getOffenderPhone(event)
        corePersonCprApiService.syncUpdateContact(
          prisonNumber = mapping.nomisPrisonNumber,
          cprContactId = mapping.cprId,
          contact = nomisPhone.toPrisonPhoneNumberRequest(),
        )
      }
    }
  }

  suspend fun offenderPhoneDeleted(event: OffenderPhoneEvent) {
    val telemetry = phoneTelemetry(event)

    corePersonMappingService.getByNomisPhoneIdOrNull(event.phoneId)?.also { mapping ->
      track("coreperson-phone-synchronisation-deleted", telemetry) {
        telemetry["cprPhoneId"] = mapping.cprId
        corePersonCprApiService.syncDeleteContact(mapping.nomisPrisonNumber, mapping.cprId)
        corePersonMappingService.deleteByNomisPhoneId(event.phoneId)
      }
    } ?: run {
      telemetryClient.trackEvent("coreperson-phone-synchronisation-deleted-ignored", telemetry)
    }
  }

  private suspend fun getOffenderPhone(event: OffenderPhoneEvent) = if (event.addressId == null) {
    corePersonNomisApiService.getOffenderPhone(event.offenderId, event.phoneId)
  } else {
    corePersonNomisApiService.getOffenderAddressPhone(event.offenderId, event.addressId, event.phoneId)
  }

  private fun phoneTelemetry(event: OffenderPhoneEvent) = telemetryOf(
    "prisonNumber" to event.offenderIdDisplay,
    "nomisOffenderId" to event.offenderId,
    "nomisPhoneId" to event.phoneId,
  ).also {
    event.addressId?.let { addressId -> it["nomisAddressId"] = addressId }
  }

  private suspend fun tryToCreatePhoneMapping(
    mapping: CorePersonPhoneMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createPhoneMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for phone id $mapping", e)
      queueService.sendMessage(
        messageType = CorePersonSynchronisationMessageType.RETRY_SYNCHRONISATION_PHONE_MAPPING.name,
        synchronisationType = SynchronisationType.CORE_PERSON,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreatePhoneMapping(retryMessage: InternalMessage<CorePersonPhoneMappingDto>) {
    createPhoneMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent("coreperson-phone-mapping-synchronisation-created", retryMessage.telemetryAttributes)
      }
  }

  private suspend fun createPhoneMapping(mapping: CorePersonPhoneMappingDto) {
    corePersonMappingService.createPhoneMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "coreperson-phone-mapping-synchronisation-duplicate",
          mapOf(
            "nomisPrisonNumber" to existing.nomisPrisonNumber,
            "existingNomisPhoneId" to existing.nomisId,
            "existingCprPhoneId" to existing.cprId,
            "duplicateNomisPhoneId" to duplicate.nomisId,
            "duplicateCprPhoneId" to duplicate.cprId,
            "type" to "PHONE",
          ),
        )
      }
    }
  }
}
