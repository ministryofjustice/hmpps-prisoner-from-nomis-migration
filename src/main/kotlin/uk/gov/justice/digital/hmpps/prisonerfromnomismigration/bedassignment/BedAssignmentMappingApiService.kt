package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSingleOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorContent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.BedAssignmentMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BedAssignmentMappingDto

@Service
class BedAssignmentMappingApiService(
  @Qualifier("mappingApiWebClient") webClient: WebClient,
) : MigrationMapping<BedAssignmentMappingDto>("/mapping/bed-assignments", webClient) {
  private val api = BedAssignmentMappingResourceApi(webClient)

  suspend fun getMappingByNomisId(bookingId: Long, sequence: Int): BedAssignmentMappingDto = api
    .getBedAssignmentMappingByNomisId(bookingId, sequence).awaitSingle()

  suspend fun getMappingByNomisIdOrNull(bookingId: Long, sequence: Int): BedAssignmentMappingDto? = api
    .getBedAssignmentMappingByNomisId(bookingId, sequence).awaitSingleOrNullForNotFound()

  override suspend fun createMapping(
    mapping: BedAssignmentMappingDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<BedAssignmentMappingDto>>,
  ): CreateMappingResult<BedAssignmentMappingDto> {
    val result = api.prepare(api.createBedAssignmentMappingRequestConfig(mapping))
      .retrieve()
      .awaitSuccessOrDuplicate<BedAssignmentMappingDto>()
    return CreateMappingResult(
      result.errorResponse?.moreInfo?.let {
        DuplicateErrorResponse(DuplicateErrorContent(it.duplicate, requireNotNull(it.existing)))
      },
    )
  }

  suspend fun deleteMappingByDpsId(dpsBedAssignmentId: String) {
    api.prepare(api.deleteBedAssignmentMappingByDpsIdRequestConfig(dpsBedAssignmentId))
      .retrieve()
      .awaitBodilessEntity()
  }
}
