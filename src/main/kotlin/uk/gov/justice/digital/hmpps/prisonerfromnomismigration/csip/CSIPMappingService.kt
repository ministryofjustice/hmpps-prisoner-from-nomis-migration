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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPAttendeeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFactorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPInterviewMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPPlanMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPReviewMappingDto

@Service
class CSIPMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<CSIPMappingDto>(domainUrl = "/mapping/csip", webClient) {

  suspend fun getCSIPReportByNomisId(nomisCSIPReportId: Long): CSIPMappingDto? =
    webClient.get()
      .uri("/mapping/csip/nomis-csip-id/{nomisCSIPReportId}", nomisCSIPReportId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCSIPReportMappingByDPSId(dpsCSIPReportId: String) {
    webClient.delete()
      .uri("/mapping/csip/dps-csip-id/{dpsCSIPReportId}", dpsCSIPReportId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getCSIPFactorByNomisId(nomisCSIPFactorId: Long): CSIPFactorMappingDto? =
    webClient.get()
      .uri("/mapping/csip/factors/nomis-csip-factor-id/{nomisCSIPFactorId}", nomisCSIPFactorId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCSIPFactorMappingByDPSId(dpsCSIPFactorId: String) {
    webClient.delete()
      .uri("/mapping/csip/factors/dps-csip-factor-id/{dpsCSIPFactorId}", dpsCSIPFactorId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCSIPFactorMapping(
    mapping: CSIPFactorMappingDto,
  ): CreateMappingResult<CSIPFactorMappingDto> {
    return webClient.post()
      .uri("/mapping/csip/factors")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CSIPFactorMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFactorMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }

  suspend fun getCSIPPlanByNomisId(nomisCSIPPlanId: Long): CSIPPlanMappingDto? =
    webClient.get()
      .uri("/mapping/csip/plans/nomis-csip-plan-id/{nomisCSIPPlanId}", nomisCSIPPlanId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCSIPPlanMappingByDPSId(dpsCSIPPlanId: String) {
    webClient.delete()
      .uri("/mapping/csip/plans/dps-csip-plan-id/{dpsCSIPPlanId}", dpsCSIPPlanId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCSIPPlanMapping(
    mapping: CSIPPlanMappingDto,
  ): CreateMappingResult<CSIPPlanMappingDto> {
    return webClient.post()
      .uri("/mapping/csip/plans")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CSIPPlanMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPPlanMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }

  suspend fun getCSIPReviewByNomisId(nomisCSIPReviewId: Long): CSIPReviewMappingDto? =
    webClient.get()
      .uri("/mapping/csip/reviews/nomis-csip-review-id/{nomisCSIPReviewId}", nomisCSIPReviewId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCSIPReviewMappingByDPSId(dpsCSIPReviewId: String) {
    webClient.delete()
      .uri("/mapping/csip/reviews/dps-csip-review-id/{dpsCSIPReviewId}", dpsCSIPReviewId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCSIPReviewMapping(
    mapping: CSIPReviewMappingDto,
  ): CreateMappingResult<CSIPReviewMappingDto> {
    return webClient.post()
      .uri("/mapping/csip/reviews")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CSIPReviewMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPReviewMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }

  suspend fun getCSIPAttendeeByNomisId(nomisCSIPAttendeeId: Long): CSIPAttendeeMappingDto? =
    webClient.get()
      .uri("/mapping/csip/attendees/nomis-csip-attendee-id/{nomisCSIPAttendeeId}", nomisCSIPAttendeeId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCSIPAttendeeMappingByDPSId(dpsCSIPAttendeeId: String) {
    webClient.delete()
      .uri("/mapping/csip/attendees/dps-csip-attendee-id/{dpsCSIPAttendeeId}", dpsCSIPAttendeeId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCSIPAttendeeMapping(
    mapping: CSIPAttendeeMappingDto,
  ): CreateMappingResult<CSIPAttendeeMappingDto> {
    return webClient.post()
      .uri("/mapping/csip/attendees")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CSIPAttendeeMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPAttendeeMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }

  suspend fun getCSIPInterviewByNomisId(nomisCSIPInterviewId: Long): CSIPInterviewMappingDto? =
    webClient.get()
      .uri("/mapping/csip/interviews/nomis-csip-interview-id/{nomisCSIPInterviewId}", nomisCSIPInterviewId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCSIPInterviewMappingByDPSId(dpsCSIPInterviewId: String) {
    webClient.delete()
      .uri("/mapping/csip/interviews/dps-csip-interview-id/{dpsCSIPInterviewId}", dpsCSIPInterviewId)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCSIPInterviewMapping(
    mapping: CSIPInterviewMappingDto,
  ): CreateMappingResult<CSIPInterviewMappingDto> {
    return webClient.post()
      .uri("/mapping/csip/interviews")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CSIPInterviewMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPInterviewMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }
}
