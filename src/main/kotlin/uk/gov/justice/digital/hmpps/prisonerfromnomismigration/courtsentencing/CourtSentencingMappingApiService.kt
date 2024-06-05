package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceAllMappingDto

@Service
class CourtSentencingMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<CourtCaseAllMappingDto>(domainUrl = "/mapping/court-sentencing/court-cases", webClient) {
  suspend fun getCourtCaseOrNullByNomisId(courtCaseId: Long): CourtCaseMappingDto? = webClient.get()
    .uri(
      "/mapping/court-sentencing/court-cases/nomis-court-case-id/{courtCase}",
      courtCaseId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCourtCaseMappingByDpsId(courtCaseId: String) = webClient.delete()
    .uri(
      "/mapping/court-sentencing/court-cases/dps-court-case-id/{courtCase}",
      courtCaseId,
    )
    .retrieve()
    .awaitBodilessEntity()

  suspend fun getCourtAppearanceOrNullByNomisId(courtAppearanceId: Long): CourtAppearanceAllMappingDto? = webClient.get()
    .uri(
      "/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/{courtAppearanceId}",
      courtAppearanceId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun createCourtAppearanceMapping(
    mapping: CourtAppearanceAllMappingDto,
  ): CreateMappingResult<CourtAppearanceAllMappingDto> {
    return webClient.post()
      .uri("/mapping/court-sentencing/court-appearances")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CourtAppearanceAllMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CourtAppearanceAllMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult<CourtAppearanceAllMappingDto>())
  }

  suspend fun deleteCourtAppearanceMappingByDpsId(courtAppearanceId: String) = webClient.delete()
    .uri(
      "/mapping/court-sentencing/court-appearances/dps-court-appearance-id/{courtAppearance}",
      courtAppearanceId,
    )
    .retrieve()
    .awaitBodilessEntity()

  suspend fun createCourtChargeMapping(
    mapping: CourtChargeMappingDto,
  ): CreateMappingResult<CourtChargeMappingDto> {
    return webClient.post()
      .uri("/mapping/court-sentencing/court-charges")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<CourtChargeMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateErrorResponse<CourtChargeMappingDto>>() {})))
      }
      .awaitFirstOrDefault(CreateMappingResult())
  }

  suspend fun getOffenderChargeOrNullByNomisId(offenderChargeId: Long): CourtChargeMappingDto? = webClient.get()
    .uri(
      "/mapping/court-sentencing/court-charges/nomis-court-charge-id/{offenderChargeId}",
      offenderChargeId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteCourtChargeMappingByNomisId(nomisCourtChargeId: Long) = webClient.delete()
    .uri(
      "/court-charges/nomis-court-charge-id/{nomisCourtChargeId}",
      nomisCourtChargeId,
    )
    .retrieve()
    .awaitBodilessEntity()

  suspend fun getSentenceOrNullByNomisId(bookingId: Long, sentenceSequence: Int): SentenceAllMappingDto? =
    webClient.get()
      .uri(
        "/mapping/court-sentencing/sentences/nomis-booking-id/{bookingId}/nomis-sentence-sequence/{sentenceSequence}",
        bookingId,
        sentenceSequence,
      )
      .retrieve()
      .awaitBodyOrNullWhenNotFound()

  suspend fun createSentenceMapping(
    mapping: SentenceAllMappingDto,
  ): CreateMappingResult<SentenceAllMappingDto> {
    return webClient.post()
      .uri("/mapping/court-sentencing/sentences")
      .bodyValue(
        mapping,
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult<SentenceAllMappingDto>() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(
          CreateMappingResult(
            it.getResponseBodyAs(
              object :
                ParameterizedTypeReference<DuplicateErrorResponse<SentenceAllMappingDto>>() {},
            ),
          ),
        )
      }
      .awaitFirstOrDefault(CreateMappingResult<SentenceAllMappingDto>())
  }

  suspend fun deleteSentenceMappingByDpsId(sentenceId: String) = webClient.delete()
    .uri(
      "/mapping/court-sentencing/sentences/dps-sentence-id/{sentenceId}",
      sentenceId,
    )
    .retrieve()
    .awaitBodilessEntity()
}
