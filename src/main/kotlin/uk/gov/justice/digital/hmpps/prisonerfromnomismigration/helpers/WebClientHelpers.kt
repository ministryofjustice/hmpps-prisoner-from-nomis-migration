package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.infrastructure.RequestConfig
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrNullWhenNotFound(): T? = this.bodyToMono<T>()
  .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.empty() }
  .awaitSingleOrNull()

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitBodyOrNullWhenUnprocessableContent(): T? = this.bodyToMono<T>()
  .onErrorResume(WebClientResponseException.UnprocessableContent::class.java) { Mono.empty() }
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

suspend inline fun WebClient.ResponseSpec.awaitBodilessEntityWithRetry(retrySpec: Retry) = this.toBodilessEntity()
  .retryWhen(retrySpec)
  .awaitSingleOrNull()

suspend inline fun WebClient.ResponseSpec.awaitBodilessEntityAsTrueNotFoundAsFalse(): Boolean = this.toBodilessEntity()
  .map { true }
  .onErrorResume(WebClientResponseException.NotFound::class.java) { Mono.just(false) }
  .awaitSingle()

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitSuccessOrDuplicate(): SuccessOrDuplicate<T> = this.bodyToMono<Unit>()
  .map { SuccessOrDuplicate<T>() }
  .onErrorResume(WebClientResponseException.Conflict::class.java) {
    Mono.just(SuccessOrDuplicate(it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateError<T>>() {})))
  }
  .awaitFirstOrDefault(SuccessOrDuplicate())

suspend inline fun <reified T : Any> WebClient.ResponseSpec.awaitSuccessOrBadRequestErrorMessage(): SuccessOrBadRequest<T> = this.bodyToMono<T>()
  .map { SuccessOrBadRequest(successResponse = it) }
  .onErrorResume(WebClientResponseException.BadRequest::class.java) {
    Mono.just(SuccessOrBadRequest(errorResponse = it.getResponseBodyAs(object : ParameterizedTypeReference<ErrorResponse>() {})?.developerMessage ?: "Unknown 400 error"))
  }
  .awaitSingle()

suspend inline fun <reified T : Any, reified C : Any> ApiClient.awaitSuccessOrBadRequestErrorMessage(requestConfig: RequestConfig<C>): SuccessOrBadRequest<T> = this.prepare(requestConfig).retrieve().awaitSuccessOrBadRequestErrorMessage()

data class SuccessOrDuplicate<T>(
  val errorResponse: DuplicateError<T>? = null,
) {
  val isError
    get() = errorResponse != null
}

class DuplicateError<T>(
  val moreInfo: DuplicateDetails<T>,
)

data class DuplicateDetails<MAPPING>(
  val duplicate: MAPPING,
  val existing: MAPPING?,
)

data class SuccessOrBadRequest<T>(
  val errorResponse: String? = null,
  val successResponse: T? = null,
) {
  val isError
    get() = errorResponse != null
}

open class ParentEntityNotFoundRetry(message: String) : RuntimeException(message)

class MissingChildEntityRetry(message: String) : RuntimeException(message)
