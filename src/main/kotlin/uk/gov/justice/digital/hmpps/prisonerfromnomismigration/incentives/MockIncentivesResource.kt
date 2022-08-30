package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import io.swagger.v3.oas.annotations.Operation
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import javax.validation.Valid
import kotlin.random.Random

/**
 * This represents the possible interface for the incentives service.
 * This can be deleted once real service is available.
 */
@RestController
class MockIncentivesResource {
  @PreAuthorize("hasRole('ROLE_MIGRATE_INCENTIVES')")
  @PostMapping("/migrate-incentive")
  @Operation(hidden = true)
  fun createIncentive(
    @RequestBody @Valid createIncentiveRequest: CreateIncentiveRequest
  ): CreateIncentiveResponse = CreateIncentiveResponse(Random.nextLong())
}

data class CreateIncentiveRequest(
  val locationId: String,
  val bookingId: Long,
  val prisonerNumber: String,
  val reviewTime: LocalDateTime,
  val reviewedBy: String,
  val iepCode: String,
  val commentText: String? = null,
  val current: Boolean,
  val reviewType: ReviewType,
)

data class CreateIncentiveResponse(
  val id: Long,
)

enum class ReviewType {
  INITIAL, REVIEW, TRANSFER, ADJUSTMENT
}
