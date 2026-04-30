package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.CourtScheduleResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleMappingDto

@Service
class CourtMappingApiService(@Qualifier("courtMappingApiWebClient") webClient: WebClient) {

  private val scheduleApi = CourtScheduleResourceApi(webClient)

  suspend fun createCourtScheduleMapping(mapping: CourtScheduleMappingDto) = scheduleApi.prepare(scheduleApi.createCourtScheduleMappingRequestConfig((mapping)))
    .retrieve()
    .awaitSuccessOrDuplicate<CourtScheduleMappingDto>()

  suspend fun getCourtScheduleMappingOrNull(nomisEventId: Long): CourtScheduleMappingDto? = scheduleApi.prepare(scheduleApi.getCourtScheduleMappingByNomisIdRequestConfig(nomisEventId))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()
}
