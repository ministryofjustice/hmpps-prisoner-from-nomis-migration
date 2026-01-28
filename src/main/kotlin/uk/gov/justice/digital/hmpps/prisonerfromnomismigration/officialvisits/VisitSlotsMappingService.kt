package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.SuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSuccessOrDuplicate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.LocationMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.VisitSlotsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMigrationMappingDto

@Service
class VisitSlotsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<VisitTimeSlotMigrationMappingDto>("/mapping/visit-slots", webClient) {
  private val api = VisitSlotsResourceApi(webClient)
  private val locationApi = LocationMappingResourceApi(webClient)

  suspend fun getTimeSlotByNomisIdsOrNull(nomisPrisonId: String, nomisDayOfWeek: String, nomisSlotSequence: Int): VisitTimeSlotMappingDto? = api.prepare(
    api.getVisitTimeSlotMappingByNomisIdsRequestConfig(
      nomisPrisonId = nomisPrisonId,
      nomisDayOfWeek = nomisDayOfWeek,
      nomisSlotSequence = nomisSlotSequence,
    ),
  )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getTimeSlotByNomisIds(nomisPrisonId: String, nomisDayOfWeek: String, nomisSlotSequence: Int): VisitTimeSlotMappingDto = api.getVisitTimeSlotMappingByNomisIds(
    nomisPrisonId = nomisPrisonId,
    nomisDayOfWeek = nomisDayOfWeek,
    nomisSlotSequence = nomisSlotSequence,
  )
    .awaitSingle()

  suspend fun createTimeSlotMapping(mapping: VisitTimeSlotMappingDto): SuccessOrDuplicate<VisitTimeSlotMappingDto> = api.prepare(api.createVisitTimeSlotMappingRequestConfig(mapping))
    .retrieve()
    .awaitSuccessOrDuplicate()

  suspend fun getVisitSlotByNomisId(nomisVisitSlotId: Long): VisitSlotMappingDto = api.getVisitSlotMappingByNomisId(nomisId = nomisVisitSlotId).awaitSingle()
  suspend fun getInternalLocationByNomisId(nomisLocationId: Long): LocationMappingDto = locationApi.getMappingGivenNomisId1(nomisLocationId = nomisLocationId).awaitSingle()
}
