package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrNullWhenNotFound(): T? = this.bodyToMono<T>()
  .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.empty() }
  .awaitSingleOrNull()

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrNullWhenUnprocessableEntity(): T? = this.bodyToMono<T>()
  .onErrorResume(WebClientResponseException.UnprocessableEntity::class.java) { Mono.empty() }
  .awaitSingleOrNull()

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrLogAndRethrowBadRequest(): T = this.bodyToMono<T>()
  .doOnError(WebClientResponseException.BadRequest::class.java) {
    log.error("Received Bad Request (400) with body {}", it.responseBodyAsString)
  }
  .awaitSingle()

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrLogAndRethrowError(): T = this.bodyToMono<T>()
  .doOnError(WebClientResponseException::class.java) {
    log.error("Received ${it.message} with body {}", it.responseBodyAsString)
  }
  .awaitSingle()

suspend inline fun WebClient.ResponseSpec.awaitBodilessEntityOrLogAndRethrowBadRequest() = this.toBodilessEntity()
  .doOnError(WebClientResponseException.BadRequest::class.java) {
    log.error("Received Bad Request (400) with body {}", it.responseBodyAsString)
  }
  .awaitSingle()

suspend inline fun WebClient.ResponseSpec.awaitBodilessEntityIgnoreNotFound() = this.toBodilessEntity()
  .onErrorResume(WebClientResponseException.NotFound::class.java) {
    Mono.empty()
  }
  .awaitSingleOrNull()

suspend inline fun WebClient.ResponseSpec.awaitBodilessEntityAsTrueNotFoundAsFalse(): Boolean = this.toBodilessEntity()
  .map { true }
  .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.just(false) }
  .awaitSingle()

class DuplicateErrorResponse(
  val moreInfo: DuplicateErrorContent,
)

data class DuplicateErrorContent(
  val duplicate: Map<String, *>,
  val existing: Map<String, *>? = null,
)

class ParentEntityNotFoundRetry(message: String) : RuntimeException(message)

class MissingChildEntityRetry(message: String) : RuntimeException(message)
