package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonProfileDetailsMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.DomesticStatusDetailsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigratePrisonerDomesticStatusRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigratePrisonerNumberOfChildrenRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.NumberOfChildrenDetailsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails.ContactPersonProfileType.CHILD
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails.ContactPersonProfileType.MARITAL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import kotlin.collections.first

@Service
class ContactPersonProfileDetailsMigrationService(
  val migrationMappingService: ContactPersonProfileDetailsMappingApiService,
  val nomisApiService: ContactPersonProfileDetailsNomisApiService,
  val nomisIdsApiService: NomisApiService,
  val dpsApiService: ContactPersonProfileDetailsDpsApiService,
  @Value("\${personalrelationships.profiledetails.page.size:1000}") pageSize: Long,
  @Value("\${personalrelationships.profiledetails.complete-check.delay-seconds:60}") completeCheckDelaySeconds: Int,
  @Value("\${personalrelationships.profiledetails.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value("\${personalrelationships.profiledetails.complete-check.count:9}") completeCheckCount: Int,
  @Value("\${complete-check.scheduled-retry-seconds:10}") completeCheckScheduledRetrySeconds: Int,
) : MigrationService<ContactPersonProfileDetailsMigrationFilter, PrisonerId, ContactPersonProfileDetailsMigrationMappingDto>(
  mappingService = migrationMappingService,
  migrationType = MigrationType.PERSONALRELATIONSHIPS_PROFILEDETAIL,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
) {
  override suspend fun getIds(
    migrationFilter: ContactPersonProfileDetailsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerId> = if (migrationFilter.prisonerNumber.isNullOrEmpty()) {
    nomisIdsApiService.getPrisonerIds(
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  } else {
    // If a single prisoner migration is requested then we'll trust the input as we're probably testing. Pretend that we called nomis-prisoner-api which found a single prisoner.
    PageImpl<PrisonerId>(mutableListOf(PrisonerId(migrationFilter.prisonerNumber)), Pageable.ofSize(1), 1)
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    val prisonerNumber = context.body.offenderNo
    val migrationId = context.migrationId

    val nomisResponse = nomisApiService.getProfileDetails(prisonerNumber, ContactPersonProfileType.all())

    val domesticStatusDpsIds =
      dpsApiService.migrateDomesticStatus(nomisResponse.toMigrateDomesticStatus(prisonerNumber))
        .let { (listOf(it.current) + it.history).joinToString() }

    val numberOfChildrenDpsIds =
      dpsApiService.migrateNumberOfChildren(nomisResponse.toMigrateNumberOfChildren(prisonerNumber))
        .let { (listOf(it.current) + it.history).joinToString() }

    val mapping = ContactPersonProfileDetailsMigrationMappingDto(
      prisonerNumber,
      migrationId,
      domesticStatusDpsIds,
      numberOfChildrenDpsIds,
    )

    createMappingOrOnFailureDo(mapping) {
      requeueCreateMapping(mapping, context)
    }
  }

  override suspend fun retryCreateMapping(context: MigrationContext<ContactPersonProfileDetailsMigrationMappingDto>) {
    createMappingOrOnFailureDo(context.body) {
      throw it
    }
  }

  private fun PrisonerProfileDetailsResponse.toMigrateDomesticStatus(prisonerNumber: String) = MigratePrisonerDomesticStatusRequest(
    prisonerNumber = prisonerNumber,
    current = bookings.findLatestBookingProfileDetails(MARITAL.name)?.toMigrateDomesticStatus(),
    history = bookings.findHistoricalBookingsProfileDetails(MARITAL.name).map { it.toMigrateDomesticStatus() },
  )

  private fun PrisonerProfileDetailsResponse.toMigrateNumberOfChildren(prisonerNumber: String) = MigratePrisonerNumberOfChildrenRequest(
    prisonerNumber = prisonerNumber,
    current = bookings.findLatestBookingProfileDetails(CHILD.name)?.toMigrateNumberOfChildren(),
    history = bookings.findHistoricalBookingsProfileDetails(CHILD.name).map { it.toMigrateNumberOfChildren() },
  )

  private fun List<BookingProfileDetailsResponse>.findLatestBookingProfileDetails(profileType: String) = first { it.latestBooking }
    .profileDetails
    .firstOrNull { it.type == profileType }

  private fun List<BookingProfileDetailsResponse>.findHistoricalBookingsProfileDetails(profileType: String) = filterNot { it.latestBooking }
    .flatMap { it.profileDetails }
    .filter { it.type == profileType }

  private fun ProfileDetailsResponse.toMigrateDomesticStatus() = DomesticStatusDetailsRequest(
    domesticStatusCode = code,
    createdBy = modifiedBy ?: createdBy,
    createdTime = modifiedDateTime ?: createDateTime,
  )

  private fun ProfileDetailsResponse.toMigrateNumberOfChildren() = NumberOfChildrenDetailsRequest(
    numberOfChildren = code,
    createdBy = modifiedBy ?: createdBy,
    createdTime = modifiedDateTime ?: createDateTime,
  )

  private suspend fun createMappingOrOnFailureDo(
    mapping: ContactPersonProfileDetailsMigrationMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      createMapping(mapping)
    }.onSuccess {
      telemetryClient.trackEvent(
        "contactperson-profiledetails-migration-entity-migrated",
        mapOf(
          "offenderNo" to mapping.prisonerNumber,
          "migrationId" to mapping.migrationId,
          "domesticStatusDpsIds" to mapping.domesticStatusDpsIds,
          "numberOfChildrenDpsIds" to mapping.numberOfChildrenDpsIds,
          "migrationType" to "CONTACTPERSON_PROFILEDETAILS",
        ),
        null,
      )
    }.onFailure {
      failureHandler(it)
    }
  }

  private suspend fun createMapping(mapping: ContactPersonProfileDetailsMigrationMappingDto) {
    migrationMappingService.createMapping(
      mapping,
      object :
        ParameterizedTypeReference<DuplicateErrorResponse<ContactPersonProfileDetailsMigrationMappingDto>>() {},
    )
  }

  private suspend fun requeueCreateMapping(
    mapping: ContactPersonProfileDetailsMigrationMappingDto,
    context: MigrationContext<*>,
  ) {
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = mapping,
      ),
    )
  }
}
