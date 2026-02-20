package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@PreAuthorize("hasRole('ROLE_PRISONER_CSRA__SYNC__RW')")
class DummyCsraApi {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PostMapping("/csras/migrate/{offenderNo}")
  @ResponseStatus(value = HttpStatus.CREATED)
  suspend fun createCsras(
    @PathVariable offenderNo: String,
    @RequestBody @Valid csras: List<CsraReviewDto>,
  ): List<MigrationResult> = csras.map {
    MigrationResult(
      dpsCsraId = UUID.randomUUID().toString(),
      nomisBookingId = it.bookingId,
      nomisSequence = it.sequenceNumber,
    )
  }.also {
    log.info("Created CSRAs $it for $offenderNo")
  }
}
