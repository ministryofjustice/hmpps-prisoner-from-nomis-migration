package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.api.NOMISMigrationAndSyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.model.BedAssignmentNomisMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.model.BedAssignmentSyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.model.NomisBedAssignment

@Service
class BedAssignmentDpsApiService(@Qualifier("bedAssignmentApiWebClient") webClient: WebClient) {
  private val api = NOMISMigrationAndSyncApi(webClient)

  suspend fun migratePrisoner(
    offenderNo: String,
    bedAssignments: List<NomisBedAssignment>,
  ): List<BedAssignmentNomisMapping> = api
    .migrate(offenderNo, bedAssignments)
    .awaitSingle()

  suspend fun sync(offenderNo: String, bedAssignments: List<NomisBedAssignment>): BedAssignmentSyncResponse = api
    .sync(offenderNo, bedAssignments)
    .awaitSingle()
}
