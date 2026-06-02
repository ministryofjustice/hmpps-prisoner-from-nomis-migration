package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.MoveActivityStartDatesException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationAlreadyInProgressException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException

@RestControllerAdvice
class HmppsPrisonerFromNomisMigrationExceptionHandler {

  @ExceptionHandler(ValidationException::class)
  fun handleValidationException(e: ValidationException): Mono<ResponseEntity<ErrorResponse>> = Mono.just(
    ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Validation failure: ${e.message}",
          developerMessage = e.message,
        ),
      ),
  ).also { log.info("Validation exception: {}", e.message) }

  @ExceptionHandler(NotFoundException::class)
  fun handleNotFoundException(e: NotFoundException): Mono<ResponseEntity<ErrorResponse>> = Mono.just(
    ResponseEntity
      .status(HttpStatus.NOT_FOUND)
      .body(
        ErrorResponse(
          status = HttpStatus.NOT_FOUND,
          userMessage = "Not Found: ${e.message}",
          developerMessage = e.message,
        ),
      ),
  ).also { log.info("Not Found: {}", e.message) }

  @ExceptionHandler(MigrationAlreadyInProgressException::class)
  fun handleMigrationAlreadyInProgressException(e: MigrationAlreadyInProgressException): Mono<ResponseEntity<ErrorResponse>> = Mono.just(
    ResponseEntity
      .status(HttpStatus.CONFLICT)
      .body(
        ErrorResponse(
          status = HttpStatus.CONFLICT,
          userMessage = "Migration already in progress: ${e.message}",
          developerMessage = e.message,
        ),
      ),
  ).also { log.info("Migration already in progress: {}", e.message) }

  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDeniedException(e: AccessDeniedException): Mono<ResponseEntity<ErrorResponse>> = Mono.just(
    ResponseEntity
      .status(HttpStatus.FORBIDDEN)
      .body(ErrorResponse(status = (HttpStatus.FORBIDDEN.value()))),
  ).also { log.debug("Forbidden (403) returned with message {}", e.message) }

  @ExceptionHandler(MethodArgumentTypeMismatchException::class)
  fun handleMethodArgumentTypeMismatchException(e: Exception): Mono<ResponseEntity<ErrorResponse>> = Mono.just(
    ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Invalid Argument: ${e.cause?.message}",
          developerMessage = e.message,
        ),
      ),
  ).also { log.info("Validation exception: {}", e.message) }

  @ExceptionHandler(BadRequestException::class)
  fun handleBadRequest(e: BadRequestException): Mono<ResponseEntity<ErrorResponse>> = Mono.just(
    ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Bad Request: ${e.message}",
          developerMessage = e.message,
        ),
      ),
  ).also { log.info("Bad request returned from downstream service: {}", e.message) }

  @ExceptionHandler(MoveActivityStartDatesException::class)
  fun handleBadRequest(e: MoveActivityStartDatesException): Mono<ResponseEntity<ErrorResponse>> = Mono.just(
    ResponseEntity
      .status(INTERNAL_SERVER_ERROR.value())
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = "Internal error: ${e.message}",
          developerMessage = e.message,
        ),
      ),
  ).also { log.error("Failed to move start dates cleanly", e) }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

data class ErrorResponse(
  val status: Int,
  val errorCode: Int? = null,
  val userMessage: String? = null,
  val developerMessage: String? = null,
  val moreInfo: String? = null,
) {
  constructor(
    status: HttpStatus,
    errorCode: Int? = null,
    userMessage: String? = null,
    developerMessage: String? = null,
    moreInfo: String? = null,
  ) :
    this(status.value(), errorCode, userMessage, developerMessage, moreInfo)
}

class BadRequestException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
