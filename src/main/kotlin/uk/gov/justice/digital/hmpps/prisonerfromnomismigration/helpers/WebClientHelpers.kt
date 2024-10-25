package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrNullWhenNotFound(): T? =
  this.bodyToMono<T>()
    .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.empty() }
    .awaitSingleOrNull()

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrNullWhenNotAcceptable(): T? =
  this.bodyToMono<T>()
    .onErrorResume(WebClientResponseException.NotAcceptable::class.java) { Mono.empty() }
    .awaitSingleOrNull()

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrNullWhenUnprocessableEntity(): T? =
  this.bodyToMono<T>()
    .onErrorResume(WebClientResponseException.UnprocessableEntity::class.java) { Mono.empty() }
    .awaitSingleOrNull()

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrLogAndRethrowBadRequest(): T =
  this.bodyToMono<T>()
    .doOnError(WebClientResponseException.BadRequest::class.java) {
      log.error("Received Bad Request (400) with body {}", it.responseBodyAsString)
    }
    .awaitSingle()

suspend inline fun WebClient.ResponseSpec.awaitBodilessEntityOrLogAndRethrowBadRequest() =
  this.toBodilessEntity()
    .doOnError(WebClientResponseException.BadRequest::class.java) {
      log.error("Received Bad Request (400) with body {}", it.responseBodyAsString)
    }
    .awaitSingle()

class DuplicateErrorResponse(
  val moreInfo: DuplicateErrorContent,
)

data class DuplicateErrorContent(
  val duplicate: Map<String, *>,
  val existing: Map<String, *>? = null,
)
