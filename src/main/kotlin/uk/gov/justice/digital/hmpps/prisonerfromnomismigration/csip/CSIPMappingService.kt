package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPChildMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFullMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPReportMappingDto

@Service
class CSIPMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<CSIPFullMappingDto>(domainUrl = "/mapping/csip", webClient) {

  override fun createMappingUrl(): String {
    return super.createMappingUrl() + "/all"
  }

  suspend fun createChildMappings(
    csipFullMappingDto: CSIPFullMappingDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<CSIPFullMappingDto>>,
  ): CreateMappingResult<CSIPFullMappingDto> =
    webClient.post()
      .uri("/mapping/csip/children/all")
      .bodyValue(csipFullMappingDto)
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CSIPFullMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
      }
      .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getCSIPReportByNomisId(nomisCSIPReportId: Long): CSIPReportMappingDto? =
    webClient.get()
      .uri("/mapping/csip/nomis-csip-id/{nomisCSIPReportId}", nomisCSIPReportId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun getFullMappingByDPSReportId(dpsCSIPReportId: String): CSIPFullMappingDto? =
    webClient.get()
      .uri("/mapping/csip/dps-csip-id/{dpsCSIPReportId}/all", dpsCSIPReportId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCSIPReportMappingByDPSId(dpsCSIPReportId: String) {
    webClient.delete()
      .uri("/mapping/csip/dps-csip-id/{dpsCSIPReportId}/all", dpsCSIPReportId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getCSIPFactorByNomisId(nomisCSIPFactorId: Long): CSIPChildMappingDto? =
    webClient.get()
      .uri("/mapping/csip/factors/nomis-csip-factor-id/{nomisCSIPFactorId}", nomisCSIPFactorId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun createCSIPFactorMapping(
    mapping: CSIPChildMappingDto,
  ): CreateMappingResult<CSIPChildMappingDto> {
    return webClient.post()
      .uri("/mapping/csip/factors")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CSIPChildMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPChildMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }

  suspend fun getCSIPPlanByNomisId(nomisCSIPPlanId: Long): CSIPChildMappingDto? =
    webClient.get()
      .uri("/mapping/csip/plans/nomis-csip-plan-id/{nomisCSIPPlanId}", nomisCSIPPlanId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun createCSIPPlanMapping(
    mapping: CSIPChildMappingDto,
  ): CreateMappingResult<CSIPChildMappingDto> {
    return webClient.post()
      .uri("/mapping/csip/plans")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CSIPChildMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPChildMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }

  suspend fun getCSIPReviewByNomisId(nomisCSIPReviewId: Long): CSIPChildMappingDto? =
    webClient.get()
      .uri("/mapping/csip/reviews/nomis-csip-review-id/{nomisCSIPReviewId}", nomisCSIPReviewId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun createCSIPReviewMapping(
    mapping: CSIPChildMappingDto,
  ): CreateMappingResult<CSIPChildMappingDto> {
    return webClient.post()
      .uri("/mapping/csip/reviews")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CSIPChildMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPChildMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }

  suspend fun getCSIPAttendeeByNomisId(nomisCSIPAttendeeId: Long): CSIPChildMappingDto? =
    webClient.get()
      .uri("/mapping/csip/attendees/nomis-csip-attendee-id/{nomisCSIPAttendeeId}", nomisCSIPAttendeeId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun createCSIPAttendeeMapping(
    mapping: CSIPChildMappingDto,
  ): CreateMappingResult<CSIPChildMappingDto> {
    return webClient.post()
      .uri("/mapping/csip/attendees")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CSIPChildMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPChildMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }

  suspend fun getCSIPInterviewByNomisId(nomisCSIPInterviewId: Long): CSIPChildMappingDto? =
    webClient.get()
      .uri("/mapping/csip/interviews/nomis-csip-interview-id/{nomisCSIPInterviewId}", nomisCSIPInterviewId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun createCSIPInterviewMapping(
    mapping: CSIPChildMappingDto,
  ): CreateMappingResult<CSIPChildMappingDto> {
    return webClient.post()
      .uri("/mapping/csip/interviews")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CSIPChildMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPChildMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }
}
