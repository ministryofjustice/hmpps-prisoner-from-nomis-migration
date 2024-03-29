package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.fasterxml.jackson.annotation.JsonFormat
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import kotlin.random.Random

/**
 * This represents the possible interface for the adjustments service.
 * This can be deleted once real service is available.
 */
@RestController
@PreAuthorize("hasRole('ROLE_SENTENCE_ADJUSTMENTS_SYNCHRONISATION')")
class MockSentencingResource {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('ROLE_SENTENCING_SYNC')")
  @PostMapping("/legacy/adjustments/migration")
  @Operation(hidden = true)
  suspend fun createAdjustmentForMigration(
    @RequestBody @Valid
    adjustmentRequest: MockAdjustmentRequest,
  ): MockCreateAdjustmentResponse {
    val id = Random.nextLong().toString()
    log.info("Created adjustment for migration with id $id for booking ${adjustmentRequest.bookingId}. Request was $adjustmentRequest")
    return MockCreateAdjustmentResponse(id)
  }

  @PostMapping("/legacy/adjustments")
  @Operation(hidden = true)
  suspend fun createAdjustmentForSynchronisation(
    @RequestBody @Valid
    adjustmentRequest: MockAdjustmentRequest,
  ): MockCreateAdjustmentResponse {
    val id = Random.nextLong().toString()
    log.info("Created adjustment for synchronisation with id $id for booking ${adjustmentRequest.bookingId}. Request was $adjustmentRequest")
    return MockCreateAdjustmentResponse(id)
  }

  @PutMapping("/legacy/adjustments/{adjustmentId}")
  @Operation(hidden = true)
  suspend fun updateAdjustmentForSynchronisation(
    @PathVariable adjustmentId: String,
    @RequestBody @Valid
    adjustmentRequest: MockAdjustmentRequest,
  ) {
    log.info("Updated adjustment for synchronisation with id $adjustmentId for booking ${adjustmentRequest.bookingId}. Request was $adjustmentRequest")
  }

  @DeleteMapping("/legacy/adjustments/{adjustmentId}")
  @Operation(hidden = true)
  suspend fun deleteAdjustmentForSynchronisation(
    @PathVariable adjustmentId: String,
  ) {
    log.info("Delete adjustment for synchronisation with id $adjustmentId")
  }
}

data class MockAdjustmentRequest(
  // will change once Sentencing API implemented
  val bookingId: Long,
  val sentenceSequence: Long?,
  val adjustmentType: String,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentDate: LocalDate?,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentFromDate: LocalDate?,
  val adjustmentDays: Long,
  val comment: String?,
  val active: Boolean,
)

data class MockCreateAdjustmentResponse(
  val id: String,
)
