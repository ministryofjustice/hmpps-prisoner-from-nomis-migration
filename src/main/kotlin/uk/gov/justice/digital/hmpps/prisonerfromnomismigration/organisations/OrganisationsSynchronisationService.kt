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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OrganisationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporatePhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.time.LocalDateTime

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

  private val corporateMappingCreator = RetryableMappingCreator<OrganisationsMappingDto>(OrganisationMappingType.CORPORATE) {
    mappingApiService.createOrganisationMapping(it)
  }
  private val addressMappingCreator = RetryableMappingCreator<OrganisationsMappingDto>(OrganisationMappingType.ADDRESS) {
    mappingApiService.createAddressMapping(it)
  }
  private val phoneMappingCreator = RetryableMappingCreator<OrganisationsMappingDto>(OrganisationMappingType.PHONE) {
    mappingApiService.createPhoneMapping(it)
  }
  private val addressPhoneMappingCreator = RetryableMappingCreator<OrganisationsMappingDto>(OrganisationMappingType.ADDRESS_PHONE) {
    mappingApiService.createAddressPhoneMapping(it)
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
            corporateMappingCreator.tryToCreateMapping(
              OrganisationsMappingDto(
                nomisId = event.corporateId,
                dpsId = "${dpsOrganisation.organisationId}",
                mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
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
  suspend fun corporateAddressInserted(event: CorporateAddressEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisAddressId" to event.addressId)
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "organisations-address-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      mappingApiService.getByNomisAddressIdOrNull(nomisAddressId = event.addressId)?.also {
        telemetryClient.trackEvent(
          "organisations-address-synchronisation-created-ignored",
          telemetry + ("dpsOrganisationAddressId" to it.dpsId),
        )
      } ?: run {
        track("organisations-address-synchronisation-created", telemetry) {
          nomisApiService.getCorporateOrganisation(nomisCorporateId = event.corporateId).also { organisation ->
            val nomisAddress = organisation.addresses.find { it.id == event.addressId }!!
            val dpsOrganisationAddress = dpsApiService.createOrganisationAddress(nomisAddress.toDpsCreateOrganisationAddressRequest(event.corporateId)).also { dpsAddress ->
              telemetry["dpsOrganisationAddressId"] = dpsAddress.organisationAddressId
            }
            addressMappingCreator.tryToCreateMapping(
              OrganisationsMappingDto(
                nomisId = event.addressId,
                dpsId = "${dpsOrganisationAddress.organisationAddressId}",
                mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
              ),
              telemetry,
            )
          }
        }
      }
    }
  }
  suspend fun corporateAddressUpdated(event: CorporateAddressEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisAddressId" to event.addressId)
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "organisations-address-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("organisations-address-synchronisation-updated", telemetry) {
        val dpsOrganisationAddressId = mappingApiService.getByNomisAddressId(nomisAddressId = event.addressId).dpsId.toLong().also {
          telemetry["dpsOrganisationAddressId"] = it
        }
        val nomisCorporate = nomisApiService.getCorporateOrganisation(nomisCorporateId = event.corporateId)
        val nomisAddress = nomisCorporate.addresses.find { it.id == event.addressId }!!
        dpsApiService.updateOrganisationAddress(dpsOrganisationAddressId, nomisAddress.toDpsUpdateOrganisationAddressRequest())
      }
    }
  }
  suspend fun corporateAddressDeleted(event: CorporateAddressEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisAddressId" to event.addressId)
    mappingApiService.getByNomisAddressIdOrNull(nomisAddressId = event.addressId)?.also {
      track("organisations-address-synchronisation-deleted", telemetry) {
        telemetry["dpsOrganisationAddressId"] = it.dpsId
        dpsApiService.deleteOrganisationAddress(it.dpsId.toLong())
        mappingApiService.deleteByNomisAddressId(event.addressId)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "organisations-address-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }

  suspend fun corporatePhoneInserted(event: CorporatePhoneEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisPhoneId" to event.phoneId)
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        if (event.isAddress) "organisations-address-phone-synchronisation-created-skipped" else "organisations-phone-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      mappingApiService.getByNomisPhoneIdOrNull(nomisPhoneId = event.phoneId)?.also {
        telemetryClient.trackEvent(
          "organisations-phone-synchronisation-created-ignored",
          telemetry + ("dpsOrganisationPhoneId" to it.dpsId),
        )
      } ?: run {
        track("organisations-phone-synchronisation-created", telemetry) {
          nomisApiService.getCorporateOrganisation(nomisCorporateId = event.corporateId).also { organisation ->
            val nomisPhone = organisation.phoneNumbers.find { it.id == event.phoneId }!!
            val dpsOrganisationPhone = dpsApiService.createOrganisationPhone(nomisPhone.toDpsCreateOrganisationPhoneRequest(event.corporateId)).also { dpsPhone ->
              telemetry["dpsOrganisationPhoneId"] = dpsPhone.organisationPhoneId
            }
            phoneMappingCreator.tryToCreateMapping(
              OrganisationsMappingDto(
                nomisId = event.phoneId,
                dpsId = "${dpsOrganisationPhone.organisationPhoneId}",
                mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
              ),
              telemetry,
            )
          }
        }
      }
    }
  }
  suspend fun corporatePhoneUpdated(event: CorporatePhoneEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisPhoneId" to event.phoneId)
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "organisations-phone-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("organisations-phone-synchronisation-updated", telemetry) {
        val dpsOrganisationPhoneId = mappingApiService.getByNomisPhoneId(nomisPhoneId = event.phoneId).dpsId.toLong().also {
          telemetry["dpsOrganisationPhoneId"] = it
        }
        val nomisCorporate = nomisApiService.getCorporateOrganisation(nomisCorporateId = event.corporateId)
        val nomisPhone = nomisCorporate.phoneNumbers.find { it.id == event.phoneId }!!
        dpsApiService.updateOrganisationPhone(dpsOrganisationPhoneId, nomisPhone.toDpsUpdateOrganisationPhoneRequest())
      }
    }
  }

  suspend fun corporateAddressPhoneInserted(event: CorporateAddressPhoneEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisPhoneId" to event.phoneId, "nomisAddressId" to event.addressId)
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "organisations-address-phone-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      mappingApiService.getByNomisAddressPhoneIdOrNull(nomisPhoneId = event.phoneId)?.also {
        telemetryClient.trackEvent(
          "organisations-address-phone-synchronisation-created-ignored",
          telemetry + ("dpsOrganisationAddressPhoneId" to it.dpsId),
        )
      } ?: run {
        track("organisations-address-phone-synchronisation-created", telemetry) {
          nomisApiService.getCorporateOrganisation(nomisCorporateId = event.corporateId).also { organisation ->
            val nomisAddress = organisation.addresses.find { it.id == event.addressId }!!
            val nomisPhone = nomisAddress.phoneNumbers.find { it.id == event.phoneId }!!
            val dpsOrganisationAddressId = mappingApiService.getByNomisAddressId(nomisAddressId = event.addressId).dpsId.toLong().also {
              telemetry["dpsOrganisationAddressId"] = it
            }
            val dpsOrganisationPhone = dpsApiService.createOrganisationAddressPhone(
              nomisPhone.toDpsCreateOrganisationAddressPhoneRequest(
                dpsOrganisationId = event.corporateId,
                dpsOrganisationAddressId = dpsOrganisationAddressId,
              ),
            ).also { dpsPhone ->
              telemetry["dpsOrganisationAddressPhoneId"] = dpsPhone.organisationAddressPhoneId
            }
            addressPhoneMappingCreator.tryToCreateMapping(
              OrganisationsMappingDto(
                nomisId = event.phoneId,
                dpsId = "${dpsOrganisationPhone.organisationAddressPhoneId}",
                mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
              ),
              telemetry,
            )
          }
        }
      }
    }
  }
  suspend fun corporateAddressPhoneUpdated(event: CorporateAddressPhoneEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisAddressId" to event.addressId, "nomisPhoneId" to event.phoneId)
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "organisations-address-phone-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("organisations-address-phone-synchronisation-updated", telemetry) {
        val dpsOrganisationAddressPhoneId = mappingApiService.getByNomisAddressPhoneId(nomisPhoneId = event.phoneId).dpsId.toLong().also {
          telemetry["dpsOrganisationAddressPhoneId"] = it
        }
        val nomisCorporate = nomisApiService.getCorporateOrganisation(nomisCorporateId = event.corporateId)
        val nomisAddress = nomisCorporate.addresses.find { it.id == event.addressId }!!
        val nomisPhone = nomisAddress.phoneNumbers.find { it.id == event.phoneId }!!
        dpsApiService.updateOrganisationAddressPhone(dpsOrganisationAddressPhoneId, nomisPhone.toDpsUpdateOrganisationAddressPhoneRequest())
      }
    }
  }

  suspend fun retryCreateCorporateMapping(retryMessage: InternalMessage<OrganisationsMappingDto>) = corporateMappingCreator.retryCreateMapping(retryMessage)
  suspend fun retryCreateAddressMapping(retryMessage: InternalMessage<OrganisationsMappingDto>) = addressMappingCreator.retryCreateMapping(retryMessage)
  suspend fun retryCreatePhoneMapping(retryMessage: InternalMessage<OrganisationsMappingDto>) = phoneMappingCreator.retryCreateMapping(retryMessage)
  suspend fun retryCreateAddressPhoneMapping(retryMessage: InternalMessage<OrganisationsMappingDto>) = addressPhoneMappingCreator.retryCreateMapping(retryMessage)

  enum class OrganisationMappingType(val messageType: OrganisationsSynchronisationMessageType) {
    CORPORATE(OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_ORGANISATION_MAPPING),
    ADDRESS(OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_ADDRESS_MAPPING),
    PHONE(OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_PHONE_MAPPING),
    ADDRESS_PHONE(OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_ADDRESS_PHONE_MAPPING),
  }

  inner class RetryableMappingCreator<T : Any>(private val type: OrganisationMappingType, private val create: suspend (T) -> CreateMappingResult<OrganisationsMappingDto>) {
    suspend fun createMapping(mapping: T) {
      create(mapping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "from-nomis-sync-organisations-duplicate",
            mapOf(
              "existingNomisId" to existing.nomisId,
              "existingDpsId" to existing.dpsId,
              "duplicateNomisId" to duplicate.nomisId,
              "duplicateDpsId" to duplicate.dpsId,
              "type" to type.name.uppercase(),
            ),
          )
        }
      }
    }

    suspend fun retryCreateMapping(retryMessage: InternalMessage<T>) {
      create(retryMessage.body)
        .also {
          telemetryClient.trackEvent(
            "organisations-${type.name.lowercase()}-mapping-synchronisation-created",
            retryMessage.telemetryAttributes,
          )
        }
    }
    suspend fun tryToCreateMapping(
      mapping: T,
      telemetry: Map<String, Any>,
    ) {
      try {
        createMapping(mapping)
      } catch (e: Exception) {
        log.error("Failed to create mapping for ${type.name.lowercase()} mapping id $mapping", e)
        queueService.sendMessage(
          messageType = type.messageType.name,
          synchronisationType = SynchronisationType.ORGANISATIONS,
          message = mapping,
          telemetryAttributes = telemetry.valuesAsStrings(),
        )
      }
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
fun CorporateAddress.toDpsCreateOrganisationAddressRequest(dpsOrganisationId: Long) = SyncCreateOrganisationAddressRequest(
  organisationId = dpsOrganisationId,
  addressType = this.type?.code,
  primaryAddress = this.primaryAddress,
  flat = this.flat,
  property = this.premise,
  street = this.street,
  area = this.locality,
  cityCode = this.city?.code,
  countyCode = this.county?.code,
  countryCode = this.country?.code,
  postcode = this.postcode,
  verified = null,
  mailFlag = this.mailAddress,
  startDate = this.startDate,
  endDate = this.endDate,
  noFixedAddress = this.noFixedAddress,
  contactPersonName = this.contactPersonName,
  businessHours = this.businessHours,
  servicesAddress = this.isServices,
  comments = this.comment,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime.toDateTime(),
)
fun CorporateAddress.toDpsUpdateOrganisationAddressRequest() = SyncUpdateOrganisationAddressRequest(
  addressType = this.type?.code,
  primaryAddress = this.primaryAddress,
  flat = this.flat,
  property = this.premise,
  street = this.street,
  area = this.locality,
  cityCode = this.city?.code,
  countyCode = this.county?.code,
  countryCode = this.country?.code,
  postcode = this.postcode,
  verified = null,
  mailFlag = this.mailAddress,
  startDate = this.startDate,
  endDate = this.endDate,
  noFixedAddress = this.noFixedAddress,
  contactPersonName = this.contactPersonName,
  businessHours = this.businessHours,
  servicesAddress = this.isServices,
  comments = this.comment,
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!.toDateTime(),
)

fun CorporatePhoneNumber.toDpsCreateOrganisationPhoneRequest(dpsOrganisationId: Long) = SyncCreateOrganisationPhoneRequest(
  organisationId = dpsOrganisationId,
  phoneType = this.type.code,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime.toDateTime(),
  phoneNumber = this.number,
  extNumber = this.extension,
)

fun CorporatePhoneNumber.toDpsCreateOrganisationAddressPhoneRequest(dpsOrganisationId: Long, dpsOrganisationAddressId: Long) = SyncCreateOrganisationAddressPhoneRequest(
  organisationId = dpsOrganisationId,
  organisationAddressId = dpsOrganisationAddressId,
  phoneType = this.type.code,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime.toDateTime(),
  phoneNumber = this.number,
  extNumber = this.extension,
)

fun CorporatePhoneNumber.toDpsUpdateOrganisationAddressPhoneRequest() = SyncUpdateOrganisationAddressPhoneRequest(
  phoneType = this.type.code,
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!.toDateTime(),
  phoneNumber = this.number,
  extNumber = this.extension,
)

fun CorporatePhoneNumber.toDpsUpdateOrganisationPhoneRequest() = SyncUpdateOrganisationPhoneRequest(
  phoneType = this.type.code,
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!.toDateTime(),
  phoneNumber = this.number,
  extNumber = this.extension,
)

private fun String.toDateTime() = this.let { LocalDateTime.parse(it) }
