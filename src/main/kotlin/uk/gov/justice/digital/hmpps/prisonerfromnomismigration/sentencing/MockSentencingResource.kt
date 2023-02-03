package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import kotlin.random.Random

/**
 * This represents the possible interface for the incentives service.
 * This can be deleted once real service is available.
 */
@RestController
class MockSentencingResource {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  @PreAuthorize("hasRole('ROLE_SENTENCING_SYNC')")
  @PostMapping("/migration/sentencing/adjustments")
  @Operation(hidden = true)
  suspend fun createAdjustment(
    @RequestBody @Valid createAdjustmentRequest: MockCreateAdjustmentRequest
  ): MockCreateAdjustmentResponse {
    val id = Random.nextLong().toString()
    log.info("Created adjustment for migration with id $id for booking ${createAdjustmentRequest.bookingId}. Request was $createAdjustmentRequest")
    return MockCreateAdjustmentResponse(id)
  }
}

data class MockCreateAdjustmentRequest(
  // will change once Sentencing API implemented
  val bookingId: Long,
  val sentenceSequence: Long,
  val adjustmentType: String,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentDate: LocalDate,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentFromDate: LocalDate?,
  val adjustmentDays: Long,
  val comment: String?,
  val active: Boolean,
)

/*data class UpdateAdjustmentRequest(
  val reviewTime: LocalDateTime,
  val commentText: String? = null,
  val current: Boolean,
)*/

data class MockCreateAdjustmentResponse(
  val id: String,
)
