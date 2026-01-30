package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OrganisationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporateAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporateInternetAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporateOrganisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporateOrganisationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorporatePhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.MigrateOrganisationWebAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncCreateWebRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncOrganisationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateOrganisationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateTypesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.model.SyncUpdateWebRequest
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
  private val webAddressMappingCreator = RetryableMappingCreator<OrganisationsMappingDto>(OrganisationMappingType.WEB) {
    mappingApiService.createWebMapping(it)
  }
  private val emailAddressMappingCreator = RetryableMappingCreator<OrganisationsMappingDto>(OrganisationMappingType.EMAIL) {
    mappingApiService.createEmailMapping(it)
  }

  suspend fun resynchronizeOrganisation(organisationId: Long) {
    val telemetry = telemetryOf("organisationId" to organisationId)
    val corporateOrganisation = nomisApiService.getCorporateOrganisation(nomisCorporateId = organisationId)
    val mapping = dpsApiService.migrateOrganisation(corporateOrganisation.toDpsMigrateOrganisationRequest())
    corporateMappingCreator.tryToCreateMapping(
      OrganisationsMappingDto(
        nomisId = organisationId,
        dpsId = "$organisationId",
        mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
      ),
      telemetry,
    )

    mapping.addresses.forEach {
      addressMappingCreator.tryToCreateMapping(
        OrganisationsMappingDto(
          nomisId = it.address.nomisId,
          dpsId = it.address.dpsId.toString(),
          mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
        ),
        telemetry,
      )
      it.phoneNumbers.forEach { phoneNumber ->
        addressPhoneMappingCreator.tryToCreateMapping(
          OrganisationsMappingDto(
            nomisId = phoneNumber.nomisId,
            dpsId = "${phoneNumber.dpsId}",
            mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
          ),
          telemetry,
        )
      }
    }
    mapping.phoneNumbers.forEach {
      phoneMappingCreator.tryToCreateMapping(
        OrganisationsMappingDto(
          nomisId = it.nomisId,
          dpsId = "${it.dpsId}",
          mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
        ),
        telemetry,
      )
    }
    mapping.webAddresses.forEach {
      emailAddressMappingCreator.tryToCreateMapping(
        OrganisationsMappingDto(
          nomisId = it.nomisId,
          dpsId = "${it.dpsId}",
          mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
        ),
        telemetry,
      )
    }

    telemetryClient.trackEvent(
      "from-nomis-synch-organisation-resynchronisation-repair",
      telemetry,
    )
  }

  suspend fun corporateInserted(event: CorporateEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId)
    if (event.originatesInDps) {
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
    if (event.originatesInDps) {
      telemetryClient.trackEvent(
        "organisations-corporate-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("organisations-corporate-synchronisation-updated", telemetry) {
        val dpsOrganisationId = mappingApiService.getByNomisCorporateId(nomisCorporateId = event.corporateId).dpsId.toLong().also {
          telemetry["dpsOrganisationId"] = it
        }
        val nomisCorporate = nomisApiService.getCorporateOrganisation(nomisCorporateId = event.corporateId)
        dpsApiService.updateOrganisation(dpsOrganisationId, nomisCorporate.toDpsUpdateOrganisationRequest(dpsOrganisationId))
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
    if (event.originatesInDps) {
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
    if (event.originatesInDps) {
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
        dpsApiService.updateOrganisationAddress(dpsOrganisationAddressId, nomisAddress.toDpsUpdateOrganisationAddressRequest(dpsOrganisationId = event.corporateId))
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
    if (event.originatesInDps) {
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
    if (event.originatesInDps) {
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
        dpsApiService.updateOrganisationPhone(dpsOrganisationPhoneId, nomisPhone.toDpsUpdateOrganisationPhoneRequest(dpsOrganisationId = event.corporateId))
      }
    }
  }

  suspend fun corporatePhoneDeleted(event: CorporatePhoneEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisPhoneId" to event.phoneId)
    mappingApiService.getByNomisPhoneIdOrNull(nomisPhoneId = event.phoneId)?.also {
      track("organisations-phone-synchronisation-deleted", telemetry) {
        telemetry["dpsOrganisationPhoneId"] = it.dpsId
        dpsApiService.deleteOrganisationPhone(it.dpsId.toLong())
        mappingApiService.deleteByNomisPhoneId(event.phoneId)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "organisations-phone-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }

  suspend fun corporateAddressPhoneInserted(event: CorporateAddressPhoneEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisPhoneId" to event.phoneId, "nomisAddressId" to event.addressId)
    if (event.originatesInDps) {
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
    if (event.originatesInDps) {
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
  suspend fun corporateAddressPhoneDeleted(event: CorporateAddressPhoneEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisAddressId" to event.addressId, "nomisPhoneId" to event.phoneId)
    mappingApiService.getByNomisAddressPhoneIdOrNull(nomisPhoneId = event.phoneId)?.also {
      track("organisations-address-phone-synchronisation-deleted", telemetry) {
        telemetry["dpsOrganisationAddressPhoneId"] = it.dpsId
        dpsApiService.deleteOrganisationAddressPhone(it.dpsId.toLong())
        mappingApiService.deleteByNomisAddressPhoneId(event.phoneId)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "organisations-address-phone-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }

  suspend fun corporateInternetAddressInserted(event: CorporateInternetAddressEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisInternetAddressId" to event.internetAddressId)
    if (event.originatesInDps) {
      telemetryClient.trackEvent(
        "organisations-internet-address-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      val nomisCorporate = nomisApiService.getCorporateOrganisation(nomisCorporateId = event.corporateId)
      val nomisInternetAddress = nomisCorporate.internetAddresses.find { it.id == event.internetAddressId }!!
      if (nomisInternetAddress.type == "EMAIL") {
        mappingApiService.getByNomisEmailIdOrNull(nomisEmailId = event.internetAddressId)?.also {
          telemetryClient.trackEvent(
            "organisations-email-synchronisation-created-ignored",
            telemetry + ("dpsOrganisationEmailId" to it.dpsId),
          )
        } ?: run {
          track("organisations-email-synchronisation-created", telemetry) {
            val dpsOrganisationEmail =
              dpsApiService.createOrganisationEmail(nomisInternetAddress.toDpsCreateOrganisationEmailRequest(event.corporateId))
                .also { dpsOrganisationEmail ->
                  telemetry["dpsOrganisationEmailId"] = dpsOrganisationEmail.organisationEmailId
                }
            emailAddressMappingCreator.tryToCreateMapping(
              OrganisationsMappingDto(
                nomisId = event.internetAddressId,
                dpsId = "${dpsOrganisationEmail.organisationEmailId}",
                mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
              ),
              telemetry,
            )
          }
        }
      } else {
        mappingApiService.getByNomisWebIdOrNull(nomisWebId = event.internetAddressId)?.also {
          telemetryClient.trackEvent(
            "organisations-web-address-synchronisation-created-ignored",
            telemetry + ("dpsOrganisationWebAddressId" to it.dpsId),
          )
        } ?: run {
          track("organisations-web-address-synchronisation-created", telemetry) {
            val dpsOrganisationWebAddress = dpsApiService.createOrganisationWebAddress(
              nomisInternetAddress.toDpsCreateOrganisationWebAddressRequest(event.corporateId),
            ).also { dpsOrganisationWebAddress ->
              telemetry["dpsOrganisationWebAddressId"] = dpsOrganisationWebAddress.organisationWebAddressId
            }
            webAddressMappingCreator.tryToCreateMapping(
              OrganisationsMappingDto(
                nomisId = event.internetAddressId,
                dpsId = "${dpsOrganisationWebAddress.organisationWebAddressId}",
                mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
              ),
              telemetry,
            )
          }
        }
      }
    }
  }
  suspend fun corporateInternetAddressUpdated(event: CorporateInternetAddressEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisInternetAddressId" to event.internetAddressId)
    if (event.originatesInDps) {
      telemetryClient.trackEvent(
        "organisations-internet-address-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      val nomisCorporate = nomisApiService.getCorporateOrganisation(nomisCorporateId = event.corporateId)
      val nomisInternetAddress = nomisCorporate.internetAddresses.find { it.id == event.internetAddressId }!!
      val internetAddressMapping = getInternetAddressMapping(event.internetAddressId)
      when (internetAddressMapping) {
        is InternetAddressMapping.EmailAddressMapping -> corporateEmailAddressUpdated(
          nomisInternetAddress = nomisInternetAddress,
          dpsOrganisationId = event.corporateId,
          dpsOrganisationEmailId = internetAddressMapping.dpsId,
          telemetry = telemetry,
        )

        is InternetAddressMapping.WebAddressMapping -> corporateWebAddressUpdated(
          nomisInternetAddress = nomisInternetAddress,
          dpsOrganisationId = event.corporateId,
          dpsOrganisationWebAddressId = internetAddressMapping.dpsId,
          telemetry = telemetry,
        )

        null -> {
          telemetryClient.trackEvent("organisations-internet-address-synchronisation-updated-error", telemetry)
          throw IllegalStateException("Unable to find mapping for ${event.internetAddressId} - assuming we have not received create event yet")
        }
      }
    }
  }

  private suspend fun corporateEmailAddressUpdated(
    nomisInternetAddress: CorporateInternetAddress,
    dpsOrganisationId: Long,
    dpsOrganisationEmailId: Long,
    telemetry: MutableMap<String, Any>,
  ) {
    telemetry["dpsOrganisationEmailId"] = dpsOrganisationEmailId
    track("organisations-email-synchronisation-updated", telemetry) {
      // check if type has changed
      if (nomisInternetAddress.isWebAddress()) {
        convertEmailToWebAddress(nomisInternetAddress, dpsOrganisationId, dpsOrganisationEmailId, telemetry)
      } else {
        dpsApiService.updateOrganisationEmail(
          dpsOrganisationEmailId,
          nomisInternetAddress.toDpsUpdateOrganisationEmailRequest(dpsOrganisationId = dpsOrganisationId),
        )
      }
    }
  }

  private suspend fun convertEmailToWebAddress(
    nomisInternetAddress: CorporateInternetAddress,
    dpsOrganisationId: Long,
    dpsOrganisationEmailId: Long,
    telemetry: MutableMap<String, Any>,
  ) {
    // remove email address mapping and DPS address
    mappingApiService.deleteByNomisEmailId(nomisInternetAddress.id)
    dpsApiService.deleteOrganisationEmail(dpsOrganisationEmailId)

    // recreate as a web address
    val dpsOrganisationWebAddress = dpsApiService.createOrganisationWebAddress(
      nomisInternetAddress.toDpsCreateOrganisationWebAddressRequestForUpdateSwitch(dpsOrganisationId),
    ).also { dpsOrganisationWebAddress ->
      telemetry["dpsOrganisationWebAddressId"] = dpsOrganisationWebAddress.organisationWebAddressId
    }
    webAddressMappingCreator.tryToCreateMapping(
      OrganisationsMappingDto(
        nomisId = nomisInternetAddress.id,
        dpsId = "${dpsOrganisationWebAddress.organisationWebAddressId}",
        mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
      ),
      telemetry,
    )
  }

  private suspend fun corporateWebAddressUpdated(
    nomisInternetAddress: CorporateInternetAddress,
    dpsOrganisationId: Long,
    dpsOrganisationWebAddressId: Long,
    telemetry: MutableMap<String, Any>,
  ) {
    telemetry["dpsOrganisationWebAddressId"] = dpsOrganisationWebAddressId
    track("organisations-web-address-synchronisation-updated", telemetry) {
      // check if type has changed
      if (nomisInternetAddress.isEmailAddress()) {
        convertWebAddressToEmail(
          nomisInternetAddress = nomisInternetAddress,
          dpsOrganisationId = dpsOrganisationId,
          dpsOrganisationWebAddressId = dpsOrganisationWebAddressId,
          telemetry = telemetry,
        )
      } else {
        dpsApiService.updateOrganisationWebAddress(
          dpsOrganisationWebAddressId,
          nomisInternetAddress.toDpsUpdateOrganisationWebAddressRequest(dpsOrganisationId = dpsOrganisationId),
        )
      }
    }
  }

  private suspend fun convertWebAddressToEmail(
    nomisInternetAddress: CorporateInternetAddress,
    dpsOrganisationId: Long,
    dpsOrganisationWebAddressId: Long,
    telemetry: MutableMap<String, Any>,
  ) {
    // remove web address mapping and DPS address
    mappingApiService.deleteByNomisWebId(nomisInternetAddress.id)
    dpsApiService.deleteOrganisationWebAddress(dpsOrganisationWebAddressId)

    // recreate as an email
    val dpsOrganisationEmail =
      dpsApiService.createOrganisationEmail(
        nomisInternetAddress.toDpsCreateOrganisationEmailRequestForUpdateSwitch(dpsOrganisationId),
      )
        .also { dpsOrganisationEmail ->
          telemetry["dpsOrganisationEmailId"] = dpsOrganisationEmail.organisationEmailId
        }
    emailAddressMappingCreator.tryToCreateMapping(
      OrganisationsMappingDto(
        nomisId = nomisInternetAddress.id,
        dpsId = "${dpsOrganisationEmail.organisationEmailId}",
        mappingType = OrganisationsMappingDto.MappingType.NOMIS_CREATED,
      ),
      telemetry,
    )
  }

  suspend fun corporateInternetAddressDeleted(event: CorporateInternetAddressEvent) {
    val telemetry = telemetryOf("nomisCorporateId" to event.corporateId, "dpsOrganisationId" to event.corporateId, "nomisInternetAddressId" to event.internetAddressId)
    val internetAddressMapping = getInternetAddressMapping(event.internetAddressId)
    when (internetAddressMapping) {
      is InternetAddressMapping.EmailAddressMapping -> track("organisations-email-synchronisation-deleted", telemetry) {
        telemetry["dpsOrganisationEmailId"] = internetAddressMapping.dpsId
        dpsApiService.deleteOrganisationEmail(internetAddressMapping.dpsId)
        mappingApiService.deleteByNomisEmailId(event.internetAddressId)
      }

      is InternetAddressMapping.WebAddressMapping -> track("organisations-web-address-synchronisation-deleted", telemetry) {
        telemetry["dpsOrganisationWebAddressId"] = internetAddressMapping.dpsId
        dpsApiService.deleteOrganisationWebAddress(internetAddressMapping.dpsId)
        mappingApiService.deleteByNomisWebId(event.internetAddressId)
      }

      null -> telemetryClient.trackEvent("organisations-internet-address-synchronisation-deleted-ignored", telemetry)
    }
  }
  suspend fun corporateTypeInserted(event: CorporateTypeEvent) = corporateTypeChanged("inserted", event)
  suspend fun corporateTypeUpdated(event: CorporateTypeEvent) = corporateTypeChanged("updated", event)
  suspend fun corporateTypeDeleted(event: CorporateTypeEvent) = corporateTypeChanged("deleted", event)
  suspend fun corporateTypeChanged(eventType: String, event: CorporateTypeEvent) {
    val telemetry = telemetryOf(
      "nomisCorporateId" to event.corporateId,
      "dpsOrganisationId" to event.corporateId,
      "nomisCorporateType" to event.corporateType,
    )
    if (event.originatesInDps) {
      telemetryClient.trackEvent(
        "organisations-type-synchronisation-$eventType-skipped",
        telemetry,
      )
    } else {
      track("organisations-type-synchronisation-$eventType", telemetry) {
        val nomisCorporate = nomisApiService.getCorporateOrganisation(nomisCorporateId = event.corporateId)
        dpsApiService.updateOrganisationTypes(
          event.corporateId,
          nomisCorporate.types.toDpsUpdateOrganisationTypesRequest(dpsOrganisationId = event.corporateId),
        )
      }
    }
  }
  private fun CorporateInternetAddress.isWebAddress() = type == "WEB"
  private fun CorporateInternetAddress.isEmailAddress() = type == "EMAIL"
  sealed class InternetAddressMapping(val mapping: OrganisationsMappingDto) {
    class WebAddressMapping(mapping: OrganisationsMappingDto) : InternetAddressMapping(mapping)
    class EmailAddressMapping(mapping: OrganisationsMappingDto) : InternetAddressMapping(mapping)
    val dpsId get() = mapping.dpsId.toLong()
  }
  private suspend fun getInternetAddressMapping(internetAddressId: Long): InternetAddressMapping? = mappingApiService.getByNomisWebIdOrNull(internetAddressId)?.let {
    InternetAddressMapping.WebAddressMapping(it)
  } ?: mappingApiService.getByNomisEmailIdOrNull(internetAddressId)?.let {
    InternetAddressMapping.EmailAddressMapping(it)
  }

  suspend fun retryCreateCorporateMapping(retryMessage: InternalMessage<OrganisationsMappingDto>) = corporateMappingCreator.retryCreateMapping(retryMessage)
  suspend fun retryCreateAddressMapping(retryMessage: InternalMessage<OrganisationsMappingDto>) = addressMappingCreator.retryCreateMapping(retryMessage)
  suspend fun retryCreatePhoneMapping(retryMessage: InternalMessage<OrganisationsMappingDto>) = phoneMappingCreator.retryCreateMapping(retryMessage)
  suspend fun retryCreateAddressPhoneMapping(retryMessage: InternalMessage<OrganisationsMappingDto>) = addressPhoneMappingCreator.retryCreateMapping(retryMessage)
  suspend fun retryCreateWebMapping(retryMessage: InternalMessage<OrganisationsMappingDto>) = webAddressMappingCreator.retryCreateMapping(retryMessage)
  suspend fun retryCreateEmailMapping(retryMessage: InternalMessage<OrganisationsMappingDto>) = emailAddressMappingCreator.retryCreateMapping(retryMessage)

  enum class OrganisationMappingType(val messageType: OrganisationsSynchronisationMessageType) {
    CORPORATE(OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_ORGANISATION_MAPPING),
    ADDRESS(OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_ADDRESS_MAPPING),
    PHONE(OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_PHONE_MAPPING),
    ADDRESS_PHONE(OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_ADDRESS_PHONE_MAPPING),
    WEB(OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_WEB_MAPPING),
    EMAIL(OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_EMAIL_MAPPING),
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

fun CorporateOrganisation.toDpsMigrateOrganisationRequest(): MigrateOrganisationRequest = MigrateOrganisationRequest(
  nomisCorporateId = id,
  organisationName = name,
  active = active,
  caseloadId = caseload?.code,
  vatNumber = vatNumber,
  programmeNumber = programmeNumber,
  comments = comment,
  organisationTypes = types.map { organisationType ->
    MigrateOrganisationType(
      type = organisationType.type.code,
      createDateTime = organisationType.audit.createDatetime,
      createUsername = organisationType.audit.createUsername,
      modifyDateTime = organisationType.audit.modifyDatetime,
      modifyUsername = organisationType.audit.modifyUserId,
    )
  },
  phoneNumbers = phoneNumbers.map { phone ->
    MigrateOrganisationPhoneNumber(
      nomisPhoneId = phone.id,
      number = phone.number,
      extension = phone.extension,
      type = phone.type.code,
      createDateTime = phone.audit.createDatetime,
      createUsername = phone.audit.createUsername,
      modifyDateTime = phone.audit.modifyDatetime,
      modifyUsername = phone.audit.modifyUserId,
    )
  },

  addresses = this.addresses.map {
    MigrateOrganisationAddress(
      nomisAddressId = it.id,
      type = it.type?.code,
      flat = it.flat,
      premise = it.premise,
      street = it.street,
      locality = it.locality,
      postCode = it.postcode,
      city = it.city?.code,
      county = it.county?.code,
      country = it.country?.code,
      noFixedAddress = it.noFixedAddress ?: false,
      primaryAddress = it.primaryAddress,
      mailAddress = it.mailAddress,
      comment = it.comment,
      startDate = it.startDate,
      endDate = it.endDate,
      serviceAddress = it.isServices,
      contactPersonName = it.contactPersonName,
      businessHours = it.businessHours,
      phoneNumbers = it.phoneNumbers.map { phone ->
        MigrateOrganisationPhoneNumber(
          nomisPhoneId = phone.id,
          number = phone.number,
          extension = phone.extension,
          type = phone.type.code,
          createDateTime = phone.audit.createDatetime,
          createUsername = phone.audit.createUsername,
          modifyDateTime = phone.audit.modifyDatetime,
          modifyUsername = phone.audit.modifyUserId,
        )
      },
      createDateTime = it.audit.createDatetime,
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime,
      modifyUsername = it.audit.modifyUserId,
    )
  },
  emailAddresses = this.internetAddresses.filter { it.type == "EMAIL" }.map {
    MigrateOrganisationEmailAddress(
      nomisEmailAddressId = it.id,
      email = it.internetAddress,
      createDateTime = it.audit.createDatetime,
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime,
      modifyUsername = it.audit.modifyUserId,
    )
  },
  webAddresses = this.internetAddresses.filter { it.type == "WEB" }.map {
    MigrateOrganisationWebAddress(
      nomisWebAddressId = it.id,
      webAddress = it.internetAddress,
      createDateTime = it.audit.createDatetime,
      createUsername = it.audit.createUsername,
      modifyDateTime = it.audit.modifyDatetime,
      modifyUsername = it.audit.modifyUserId,
    )
  },
  createDateTime = this.audit.createDatetime,
  createUsername = this.audit.createUsername,
  modifyDateTime = this.audit.modifyDatetime,
  modifyUsername = this.audit.modifyUserId,
)

fun CorporateOrganisation.toDpsCreateOrganisationRequest() = SyncCreateOrganisationRequest(
  organisationId = id,
  organisationName = this.name,
  programmeNumber = programmeNumber,
  vatNumber = vatNumber,
  caseloadId = caseload?.code,
  comments = comment,
  active = active,
  deactivatedDate = expiryDate,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime,
)
fun CorporateOrganisation.toDpsUpdateOrganisationRequest(dpsOrganisationId: Long) = SyncUpdateOrganisationRequest(
  organisationId = dpsOrganisationId,
  organisationName = this.name,
  programmeNumber = programmeNumber,
  vatNumber = vatNumber,
  caseloadId = caseload?.code,
  comments = comment,
  active = active,
  deactivatedDate = expiryDate,
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!,
)
fun CorporateAddress.toDpsCreateOrganisationAddressRequest(dpsOrganisationId: Long) = SyncCreateAddressRequest(
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
  mailAddress = this.mailAddress,
  startDate = this.startDate,
  endDate = this.endDate,
  noFixedAddress = this.noFixedAddress == true,
  contactPersonName = this.contactPersonName,
  businessHours = this.businessHours,
  serviceAddress = this.isServices,
  comments = this.comment,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime,
)
fun CorporateAddress.toDpsUpdateOrganisationAddressRequest(dpsOrganisationId: Long) = SyncUpdateAddressRequest(
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
  mailAddress = this.mailAddress,
  startDate = this.startDate,
  endDate = this.endDate,
  noFixedAddress = this.noFixedAddress == true,
  contactPersonName = this.contactPersonName,
  businessHours = this.businessHours,
  serviceAddress = this.isServices,
  comments = this.comment,
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!,
)

fun CorporatePhoneNumber.toDpsCreateOrganisationPhoneRequest(dpsOrganisationId: Long) = SyncCreatePhoneRequest(
  organisationId = dpsOrganisationId,
  phoneType = this.type.code,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime,
  phoneNumber = this.number,
  extNumber = this.extension,
)

fun CorporatePhoneNumber.toDpsCreateOrganisationAddressPhoneRequest(dpsOrganisationAddressId: Long) = SyncCreateAddressPhoneRequest(
  organisationAddressId = dpsOrganisationAddressId,
  phoneType = this.type.code,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime,
  phoneNumber = this.number,
  extNumber = this.extension,
)

fun CorporatePhoneNumber.toDpsUpdateOrganisationAddressPhoneRequest() = SyncUpdateAddressPhoneRequest(
  phoneType = this.type.code,
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!,
  phoneNumber = this.number,
  extNumber = this.extension,
)

fun CorporatePhoneNumber.toDpsUpdateOrganisationPhoneRequest(dpsOrganisationId: Long) = SyncUpdatePhoneRequest(
  organisationId = dpsOrganisationId,
  phoneType = this.type.code,
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!,
  phoneNumber = this.number,
  extNumber = this.extension,
)
fun CorporateInternetAddress.toDpsCreateOrganisationWebAddressRequest(dpsOrganisationId: Long) = SyncCreateWebRequest(
  organisationId = dpsOrganisationId,
  webAddress = this.internetAddress,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime,
)
fun CorporateInternetAddress.toDpsUpdateOrganisationWebAddressRequest(dpsOrganisationId: Long) = SyncUpdateWebRequest(
  organisationId = dpsOrganisationId,
  webAddress = this.internetAddress,
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!,
)
fun CorporateInternetAddress.toDpsCreateOrganisationEmailRequest(dpsOrganisationId: Long) = SyncCreateEmailRequest(
  organisationId = dpsOrganisationId,
  emailAddress = this.internetAddress,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime,
)
fun CorporateInternetAddress.toDpsCreateOrganisationEmailRequestForUpdateSwitch(dpsOrganisationId: Long) = SyncCreateEmailRequest(
  emailAddress = this.internetAddress,
  organisationId = dpsOrganisationId,
  createdBy = this.audit.modifyUserId!!,
  createdTime = this.audit.modifyDatetime!!,
)
fun CorporateInternetAddress.toDpsCreateOrganisationWebAddressRequestForUpdateSwitch(dpsOrganisationId: Long) = SyncCreateWebRequest(
  webAddress = this.internetAddress,
  organisationId = dpsOrganisationId,
  createdBy = this.audit.modifyUserId!!,
  createdTime = this.audit.modifyDatetime!!,
)
fun CorporateInternetAddress.toDpsUpdateOrganisationEmailRequest(dpsOrganisationId: Long) = SyncUpdateEmailRequest(
  organisationId = dpsOrganisationId,
  emailAddress = this.internetAddress,
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!,
)
fun List<CorporateOrganisationType>.toDpsUpdateOrganisationTypesRequest(dpsOrganisationId: Long) = SyncUpdateTypesRequest(
  organisationId = dpsOrganisationId,
  types = this.map {
    SyncOrganisationType(
      type = it.type.code,
      createdBy = it.audit.createUsername,
      createdTime = it.audit.createDatetime,
      updatedBy = it.audit.modifyUserId,
      updatedTime = it.audit.modifyDatetime,
    )
  },
)
