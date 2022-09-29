package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import io.swagger.v3.oas.annotations.Operation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
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
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  @PreAuthorize("hasRole('ROLE_MAINTAIN_IEP')")
  @PostMapping("/iep/migration/booking/{bookingId}")
  @Operation(hidden = true)
  suspend fun createIncentive(
    @PathVariable("bookingId") bookingId: Long,
    @RequestBody @Valid createIncentiveRequest: CreateIncentiveRequest
  ): CreateIncentiveResponse {
    val id = Random.nextLong()
    log.info("Created incentive for migration with id $id for booking $bookingId. Request was $createIncentiveRequest")
    return CreateIncentiveResponse(id)
  }

  @PreAuthorize("hasRole('ROLE_MAINTAIN_IEP')")
  @PostMapping("/iep/sync/booking/{bookingId}")
  @Operation(hidden = true)
  suspend fun createSynchroniseIncentive(
    @PathVariable("bookingId") bookingId: Long,
    @RequestBody @Valid createIncentiveRequest: CreateIncentiveRequest
  ): CreateIncentiveResponse {
    val id = Random.nextLong()
    log.info("Created incentive for synchronisation with id $id for booking $bookingId. Request was $createIncentiveRequest")
    return CreateIncentiveResponse(id)
  }

  @PreAuthorize("hasRole('ROLE_MAINTAIN_IEP')")
  @PatchMapping("/iep/sync/booking/{bookingId}/id/{id}")
  @Operation(hidden = true)
  suspend fun updateSynchroniseIncentive(
    @PathVariable("bookingId") bookingId: Long,
    @PathVariable("id") id: Long,
    @RequestBody @Valid updateIncentiveRequest: UpdateIncentiveRequest
  ): CreateIncentiveResponse {
    log.info("Update incentive for synchronisation with id $id for booking $bookingId. Request was $updateIncentiveRequest")
    return CreateIncentiveResponse(id)
  }
}

data class CreateIncentiveRequest(
  val locationId: String,
  val bookingId: Long,
  val prisonerNumber: String,
  val reviewTime: LocalDateTime,
  val reviewedBy: String?,
  val iepCode: String,
  val commentText: String? = null,
  val current: Boolean,
  val reviewType: String,
)

data class UpdateIncentiveRequest(
  val reviewTime: LocalDateTime,
  val commentText: String? = null,
  val current: Boolean,
)

data class CreateIncentiveResponse(
  val id: Long,
)
