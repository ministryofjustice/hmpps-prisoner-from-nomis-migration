package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import io.swagger.v3.oas.annotations.Operation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * This represents the possible interface for the CaseNote API service.
 * This can be deleted once the real service is available.
 */
@RestController
@RequestMapping("/mock", produces = [MediaType.APPLICATION_JSON_VALUE])
class MockCaseNotesResource {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PreAuthorize("hasRole('ROLE_MIGRATE_CASENOTES')")
  @PostMapping("/migrate/{offenderNo}/casenotes")
  @Operation(hidden = true)
  suspend fun migrateCaseNotes(
    offenderNo: String,
//    @RequestBody @Valid
//    caseNoteRequest: List<MigrateCaseNoteRequest>, TODO get weird error
  ): List<DpsCaseNote> {
    val caseNoteRequest: List<MigrateCaseNoteRequest> = listOf()
    log.info("Creating case notes for migrate case notes for offender $offenderNo, size ${caseNoteRequest.size}}")
    return caseNoteRequest.map {
      DpsCaseNote(
        caseNoteId = UUID.randomUUID().toString(),
        dummyAttribute = it.dummyAttribute,
      )
    }
  }
}
