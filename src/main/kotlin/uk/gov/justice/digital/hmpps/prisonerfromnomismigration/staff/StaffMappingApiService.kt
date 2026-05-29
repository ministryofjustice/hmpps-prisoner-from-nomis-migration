package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSingleOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.StaffMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.StaffMappingDto

@Service
class StaffMappingApiService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<StaffMappingDto>(domainUrl = "/mapping/staff", webClient) {
  private val staffMappingApi = StaffMappingResourceApi(webClient)

  suspend fun getByNomisStaffIdOrNull(nomisStaffId: Long): StaffMappingDto? = staffMappingApi.getStaffMappingByNomisId(nomisStaffId).awaitSingleOrNullForNotFound()
}
