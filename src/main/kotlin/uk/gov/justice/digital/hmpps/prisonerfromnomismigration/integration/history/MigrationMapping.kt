package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.LatestMigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationDetails

abstract class MigrationMapping<MAPPING : Any>(
  val domainUrl: String,
  internal val webClient: WebClient,
) {
  open fun createMappingUrl() = domainUrl

  open suspend fun findLatestMigration(): LatestMigration? = webClient.get()
    .uri("$domainUrl/migrated/latest")
    .retrieve()
    .bodyToMono(LatestMigration::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .awaitSingleOrNull()

  open suspend fun getMigrationDetails(migrationId: String): MigrationDetails = webClient.get()
    .uri {
      it.path("$domainUrl/migration-id/{migrationId}")
        .queryParam("size", 1)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono(MigrationDetails::class.java)
    .awaitSingle()!!

  open suspend fun getMigrationCount(migrationId: String): Long = webClient.get()
    .uri {
      it.path("$domainUrl/migration-id/{migrationId}")
        .queryParam("size", 1)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono(MigrationDetails::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .awaitSingleOrNull()?.count ?: 0

  open suspend fun createMapping(
    mapping: MAPPING,
    errorJavaClass: ParameterizedTypeReference<DuplicateErrorResponse<MAPPING>>,
  ): CreateMappingResult<MAPPING> = webClient.post()
    .uri(createMappingUrl())
    .bodyValue(
      mapping,
    )
    .retrieve()
    .bodyToMono(Unit::class.java)
    .map { CreateMappingResult<MAPPING>() }
    .onErrorResume(WebClientResponseException.Conflict::class.java) {
      Mono.just(CreateMappingResult(it.getResponseBodyAs(errorJavaClass)))
    }
    .awaitFirstOrDefault(CreateMappingResult())
}

data class CreateMappingResult<MAPPING>(
  /* currently, only interested in the error response as success doesn't return a body*/
  val errorResponse: DuplicateErrorResponse<MAPPING>? = null,
) {
  val isError
    get() = errorResponse != null
}

class DuplicateErrorResponse<MAPPING>(
  val moreInfo: DuplicateErrorContent<MAPPING>,
)

data class DuplicateErrorContent<MAPPING>(
  val duplicate: MAPPING,
  val existing: MAPPING,
)
