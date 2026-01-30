package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping

@Service
class CsraMappingService(
  @Qualifier("mappingApiWebClient") webClient: WebClient,
) : MigrationMapping<CsraMappingDto>("/mapping/csra", webClient) {
  fun getByNomisIdOrNull(offenderNo: String) {
    TODO()
  }
}

data class CsraMappingDto(
  val bookingId: Long,
  val sequence: Int,
  // TODO
)
