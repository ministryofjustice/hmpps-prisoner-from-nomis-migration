package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSingleOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CaseNotesMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto

@Service
class CaseNotesMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<CaseNoteMappingDto>(domainUrl = "/mapping/casenotes", webClient) {
  private val api = CaseNotesMappingResourceApi(webClient)

  suspend fun getMappings(ids: List<Long>): List<CaseNoteMappingDto> = api
    .getCaseNotesMappingsByNomisId(ids).awaitSingle()

  suspend fun deleteMappingGivenDpsId(dpsCaseNoteId: String) {
    api.deleteCaseNotesMappingsByDpsId(dpsCaseNoteId).awaitSingle()
  }

  suspend fun getByDpsId(caseNoteId: String): List<CaseNoteMappingDto> = api
    .getCaseNotesMappingsByDpsId(caseNoteId).awaitSingle()

  suspend fun getMappingGivenNomisIdOrNull(caseNoteId: Long): CaseNoteMappingDto? = api
    .getCaseNotesMappingByNomisId(caseNoteId).awaitSingleOrNullForNotFound()

  suspend fun updateMappingsByNomisId(oldOffenderNo: String, newOffenderNo: String) {
    api.updateCaseNoteMappingsByNomisId(oldOffenderNo, newOffenderNo).awaitSingle()
  }

  suspend fun updateMappingsByBookingId(bookingId: Long, newOffenderNo: String): List<CaseNoteMappingDto> = api
    .updateCaseNotesMappingsByBookingId(bookingId, newOffenderNo).awaitSingle()

  suspend fun deleteMappingsByBookingId(bookingId: Long) {
    api.deleteCaseNotesMappingByBookingId(bookingId).awaitSingle()
  }

  suspend fun getMappingsByBookingId(bookingId: Long): List<CaseNoteMappingDto> = api
    .getCaseNotesMappingsByBookingId(bookingId).awaitSingle()
}
