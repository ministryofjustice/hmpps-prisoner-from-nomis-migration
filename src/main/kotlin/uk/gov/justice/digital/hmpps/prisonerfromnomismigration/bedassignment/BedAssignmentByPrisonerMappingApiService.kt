package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.DuplicateDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.DuplicateError
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.SuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSingleOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.BedAssignmentMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BedAssignmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BedAssignmentMappingIdDto

@Service
class BedAssignmentByPrisonerMappingApiService(
  @Qualifier("mappingApiWebClient") webClient: WebClient,
) : MigrationMapping<BedAssignmentMigrationMapping>("/mapping/bed-assignments", webClient) {
  private val api = BedAssignmentMappingResourceApi(webClient)

  suspend fun createMappings(dto: BedAssignmentMigrationMapping): SuccessOrDuplicate<BedAssignmentMappingDto> = api
    .prepare(api.createBedAssignmentMappingsForPrisonerRequestConfig(dto.offenderNo, dto.prisonerMappings))
    .retrieve()
    .awaitSuccessOrDuplicate()

  suspend fun createBedAssignmentMapping(requested: BedAssignmentMappingDto): SuccessOrDuplicate<BedAssignmentMappingDto> = api
    .prepare(api.createBedAssignmentMappingRequestConfig(requested))
    .retrieve()
    .awaitSuccessOrDuplicate<BedAssignmentMappingDto>()

  suspend fun getBedAssignmentMappingOrNull(id: BedAssignmentMappingIdDto): BedAssignmentMappingDto? = api
    .getBedAssignmentMappingByNomisId(id.nomisBookingId, id.nomisSequence)
    .awaitSingleOrNullForNotFound()

  suspend fun reconcileMappings(
    dto: BedAssignmentMigrationMapping,
    onDuplicate: (SuccessOrDuplicate<BedAssignmentMappingDto>) -> Unit,
  ) {
    dto.prisonerMappings.mappings.forEach { id ->
      val requested = BedAssignmentMappingDto(
        dpsBedAssignmentId = id.dpsBedAssignmentId,
        nomisBookingId = id.nomisBookingId,
        nomisSequence = id.nomisSequence,
        mappingType = BedAssignmentMappingDto.MappingType.MIGRATED,
        label = dto.prisonerMappings.label,
      )
      val existing = getBedAssignmentMappingOrNull(id)
      if (existing != null) {
        if (!requested.isSameMappingAs(existing)) {
          onDuplicate(SuccessOrDuplicate(DuplicateError(DuplicateDetails(requested, existing))))
          throw IllegalStateException("Conflicting bed-assignment migration mapping for booking ${id.nomisBookingId}, sequence ${id.nomisSequence}")
        }
      } else {
        val result = createBedAssignmentMapping(requested)
        result.errorResponse?.moreInfo?.let {
          onDuplicate(result)
          check(requested.isSameMappingAs(it.duplicate) && requested.isSameMappingAs(it.existing)) {
            "Conflicting bed-assignment migration mapping for booking ${id.nomisBookingId}, sequence ${id.nomisSequence}"
          }
        }
      }
    }
  }
}
