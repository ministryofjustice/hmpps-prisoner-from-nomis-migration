package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.StaffResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffIdsPage

@Service
class StaffNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val api = StaffResourceApi(webClient)

  suspend fun getStaffDetails(staffId: Long, dpsRolesOnly: Boolean = true): StaffDetails = api.getUser(staffId, dpsRolesOnly)
    .awaitSingle()

  suspend fun getStaffIdsFromId(lastStaffId: Long = 0, pageSize: Long): StaffIdsPage = api.getStaffIdsFromId(staffId = lastStaffId, size = pageSize.toInt())
    .awaitSingle()
}
