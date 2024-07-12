package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import kotlinx.coroutines.delay
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * This represents the possible interface for the CaseNote API service.
 * This can be deleted once the real service is available.
 */
@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_MIGRATE_CASENOTES')")
class MockCaseNotesResource {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @PostMapping("/migrate/{offenderNo}/casenotes")
  @Operation(hidden = true)
  suspend fun migrateCaseNotes(
    @PathVariable
    offenderNo: String,
    @RequestBody @Valid
    caseNoteRequest: List<MigrateCaseNoteRequest>,
  ): List<DpsCaseNote> {
    log.info("Creating case notes for migrate case notes for offender $offenderNo, size ${caseNoteRequest.size}}")
    delay(350) // simulate time taken to store case notes
    return caseNoteRequest.map {
      DpsCaseNote(
        caseNoteId = UUID.randomUUID().toString(),
        dummyAttribute = it.dummyAttribute,
      )
    }
  }

  @PostMapping("/sync/upsert")
  @Operation(hidden = true)
  suspend fun syncCaseNote(
    @RequestBody @Valid
    caseNoteRequest: SyncCaseNoteRequest,
  ): DpsCaseNote {
    log.info("Creating case note for sync request $caseNoteRequest}")
    delay(20)
    return DpsCaseNote(
      caseNoteId = UUID.randomUUID().toString(),
      dummyAttribute = caseNoteRequest.dummyAttribute,
    )
  }

  @DeleteMapping("/sync/delete/{caseNoteId}")
  @Operation(hidden = true)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  suspend fun deleteCaseNote(
    @PathVariable
    caseNoteId: String,
  ) {
    log.info("Deleting case note $caseNoteId}")
    delay(10)
  }
}
