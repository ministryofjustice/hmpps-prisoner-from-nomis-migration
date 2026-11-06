package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSingleOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.BedAssignmentHistoryResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BedAssignmentHistoryResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerBedAssignmentHistoryResponse

@Service
class BedAssignmentNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val api = BedAssignmentHistoryResourceApi(webClient)

  suspend fun getBedAssignments(offenderNo: String): PrisonerBedAssignmentHistoryResponse = api
    .getBedAssignmentsForPrisoner(offenderNo).awaitSingle()

  suspend fun getBedAssignment(bookingId: Long, sequence: Int): BedAssignmentHistoryResponse = api
    .getBedAssignment(bookingId, sequence).awaitSingle()

  suspend fun getBedAssignmentOrNull(bookingId: Long, sequence: Int): BedAssignmentHistoryResponse? = api
    .getBedAssignment(bookingId, sequence).awaitSingleOrNullForNotFound()
}
