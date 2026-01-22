package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityWithRetry
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSentencingMigrationSummary
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NomisSentenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceTermMappingDto
import java.time.Duration

@Service
class CourtSentencingMappingApiService(
  @Qualifier("mappingApiWebClient") webClient: WebClient,
  @Value("\${courtsentencing.mapping-retry.count:8}") private val retryCount: Long,
  @Value("\${courtsentencing.mapping-retry.delay-milliseconds:10000}") private val delayMillisSeconds: Long,
) : MigrationMapping<CourtCaseMigrationMapping>(domainUrl = "/mapping/court-sentencing/prisoner", webClient) {

  suspend fun createMapping(
    offenderNo: String,
    mapping: CourtCaseBatchMappingDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<CourtCaseBatchMappingDto>>,
  ): CreateMappingResult<CourtCaseBatchMappingDto> = webClient.post()
    .uri("/mapping/court-sentencing/prisoner/{offenderNo}/court-cases", offenderNo)
    .bodyValue(
      mapping,
    )
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<CourtCaseBatchMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun createMapping(
    mapping: CourtCaseAllMappingDto,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<CourtCaseAllMappingDto>>,
  ): CreateMappingResult<CourtCaseAllMappingDto> = webClient.post()
    .uri("/mapping/court-sentencing/court-cases")
    .bodyValue(
      mapping,
    )
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<CourtCaseAllMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getCourtCaseOrNullByNomisId(courtCaseId: Long): CourtCaseMappingDto? = webClient.get()
    .uri(
      "/mapping/court-sentencing/court-cases/nomis-court-case-id/{courtCase}",
      courtCaseId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getCourtCaseByNomisId(courtCaseId: Long): CourtCaseMappingDto = webClient.get()
    .uri(
      "/mapping/court-sentencing/court-cases/nomis-court-case-id/{courtCase}",
      courtCaseId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getCourtCasesByNomisIds(nomisCaseIds: List<Long>): List<CourtCaseMappingDto> = webClient.post()
    .uri(
      "/mapping/court-sentencing/court-cases/nomis-case-ids/get-list",
    )
    .bodyValue(nomisCaseIds)
    .retrieve()
    .awaitBody()

  suspend fun deleteCourtCaseMappingByDpsId(courtCaseId: String): ResponseEntity<Void> = webClient.delete()
    .uri(
      "/mapping/court-sentencing/court-cases/dps-court-case-id/{courtCase}",
      courtCaseId,
    )
    .retrieve()
    .awaitBodilessEntity()

  suspend fun getCourtAppearanceOrNullByNomisId(courtAppearanceId: Long): CourtAppearanceMappingDto? = webClient.get()
    .uri(
      "/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/{courtAppearanceId}",
      courtAppearanceId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getCourtAppearanceByNomisId(courtAppearanceId: Long): CourtAppearanceMappingDto = webClient.get()
    .uri(
      "/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/{courtAppearanceId}",
      courtAppearanceId,
    )
    .retrieve()
    .awaitBody()

  suspend fun createCourtAppearanceMapping(
    mapping: CourtAppearanceAllMappingDto,
  ): CreateMappingResult<CourtAppearanceAllMappingDto> = webClient.post()
    .uri("/mapping/court-sentencing/court-appearances")
    .bodyValue(
      mapping,
    )
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<CourtAppearanceAllMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(
        CreateMappingResult(
          it.getResponseBodyAs(
            object :
              ParameterizedTypeReference<DuplicateErrorResponse<CourtAppearanceAllMappingDto>>() {},
          ),
        ),
      )
    }
    .awaitFirstOrDefault(CreateMappingResult<CourtAppearanceAllMappingDto>())

  suspend fun deleteCourtAppearanceMappingByDpsId(courtAppearanceId: String) = webClient.delete()
    .uri(
      "/mapping/court-sentencing/court-appearances/dps-court-appearance-id/{courtAppearance}",
      courtAppearanceId,
    )
    .retrieve()
    .awaitBodilessEntity()

  suspend fun createCourtChargeMapping(
    mapping: CourtChargeMappingDto,
  ): CreateMappingResult<CourtChargeMappingDto> = webClient.post()
    .uri("/mapping/court-sentencing/court-charges")
    .bodyValue(
      mapping,
    )
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<CourtChargeMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(
        CreateMappingResult(
          it.getResponseBodyAs(
            object :
              ParameterizedTypeReference<DuplicateErrorResponse<CourtChargeMappingDto>>() {},
          ),
        ),
      )
    }
    .awaitFirstOrDefault(CreateMappingResult())

  suspend fun getOffenderChargeOrNullByNomisId(offenderChargeId: Long): CourtChargeMappingDto? = webClient.get()
    .uri(
      "/mapping/court-sentencing/court-charges/nomis-court-charge-id/{offenderChargeId}",
      offenderChargeId,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getOffenderChargeByNomisId(offenderChargeId: Long): CourtChargeMappingDto = webClient.get()
    .uri(
      "/mapping/court-sentencing/court-charges/nomis-court-charge-id/{offenderChargeId}",
      offenderChargeId,
    )
    .retrieve()
    .awaitBody()

  suspend fun deleteCourtChargeMappingByNomisId(nomisCourtChargeId: Long) = webClient.delete()
    .uri(
      "/mapping/court-sentencing/court-charges/nomis-court-charge-id/{nomisCourtChargeId}",
      nomisCourtChargeId,
    )
    .retrieve()
    .awaitBodilessEntity()

  suspend fun getSentenceOrNullByNomisId(bookingId: Long, sentenceSequence: Int): SentenceMappingDto? = webClient.get()
    .uri(
      "/mapping/court-sentencing/sentences/nomis-booking-id/{bookingId}/nomis-sentence-sequence/{sentenceSequence}",
      bookingId,
      sentenceSequence,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getSentencesByNomisIds(nomisSentenceIds: List<NomisSentenceId>): List<SentenceMappingDto> = webClient.post()
    .uri(
      "/mapping/court-sentencing/sentences/nomis-sentence-ids/get-list",
    )
    .bodyValue(nomisSentenceIds)
    .retrieve()
    .awaitBody()

  suspend fun getSentenceTermOrNullByNomisId(bookingId: Long, sentenceSequence: Int, termSequence: Int): SentenceTermMappingDto? = webClient.get()
    .uri(
      "/mapping/court-sentencing/sentence-terms/nomis-booking-id/{bookingId}/nomis-sentence-sequence/{sentenceSequence}/nomis-term-sequence/{termSequence}",
      bookingId,
      sentenceSequence,
      termSequence,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getSentenceByNomisId(bookingId: Long, sentenceSequence: Long): SentenceMappingDto = webClient.get()
    .uri(
      "/mapping/court-sentencing/sentences/nomis-booking-id/{bookingId}/nomis-sentence-sequence/{sentenceSequence}",
      bookingId,
      sentenceSequence,
    )
    .retrieve()
    .awaitBody()

  suspend fun getSentenceTermByNomisId(bookingId: Long, sentenceSequence: Long, termSequence: Int): SentenceTermMappingDto = webClient.get()
    .uri(
      "/mapping/court-sentencing/sentence-terms/nomis-booking-id/{bookingId}/nomis-sentence-sequence/{sentenceSequence}/nomis-term-sequence/{termSequence}",
      bookingId,
      sentenceSequence,
      termSequence,
    )
    .retrieve()
    .awaitBody()

  suspend fun createSentenceMapping(
    mapping: SentenceMappingDto,
  ): CreateMappingResult<SentenceMappingDto> = webClient.post()
    .uri("/mapping/court-sentencing/sentences")
    .bodyValue(
      mapping,
    )
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<SentenceMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(
        CreateMappingResult(
          it.getResponseBodyAs(
            object :
              ParameterizedTypeReference<DuplicateErrorResponse<SentenceMappingDto>>() {},
          ),
        ),
      )
    }
    .awaitFirstOrDefault(CreateMappingResult<SentenceMappingDto>())

  suspend fun createSentenceTermMapping(
    mapping: SentenceTermMappingDto,
  ): CreateMappingResult<SentenceTermMappingDto> = webClient.post()
    .uri("/mapping/court-sentencing/sentence-terms")
    .bodyValue(
      mapping,
    )
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<SentenceTermMappingDto>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(
        CreateMappingResult(
          it.getResponseBodyAs(
            object :
              ParameterizedTypeReference<DuplicateErrorResponse<SentenceTermMappingDto>>() {},
          ),
        ),
      )
    }
    .awaitFirstOrDefault(CreateMappingResult<SentenceTermMappingDto>())

  suspend fun deleteSentenceMappingByDpsId(sentenceId: String) = webClient.delete()
    .uri(
      "/mapping/court-sentencing/sentences/dps-sentence-id/{sentenceId}",
      sentenceId,
    )
    .retrieve()
    .awaitBodilessEntityWithRetry(
      Retry.fixedDelay(
        retryCount,
        Duration.ofMillis(delayMillisSeconds),
      ),
    )

  suspend fun deleteSentenceTermMappingByDpsId(termId: String) = webClient.delete()
    .uri(
      "/mapping/court-sentencing/sentence-terms/dps-term-id/{termId}",
      termId,
    )
    .retrieve()
    .awaitBodilessEntityWithRetry(
      Retry.fixedDelay(
        retryCount,
        Duration.ofMillis(delayMillisSeconds),
      ),
    )

  suspend fun getOffenderMigrationSummaryOrNull(offenderNo: String): CourtSentencingMigrationSummary? = webClient.get()
    .uri(
      "/mapping/court-sentencing/prisoner/{offenderNo}/migration-summary",
      offenderNo,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun replaceOrCreateMappings(request: CourtCaseBatchMappingDto) {
    webClient.put()
      .uri("/mapping/court-sentencing/court-cases/replace")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }
}
