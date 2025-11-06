package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.LocationMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.VisitSlotsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMigrationMappingDto
import java.time.DayOfWeek

@Service
class VisitSlotsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<VisitTimeSlotMigrationMappingDto>("/mapping/visit-slots", webClient) {
  private val api = VisitSlotsResourceApi(webClient)
  private val locationApi = LocationMappingResourceApi(webClient)

  suspend fun getByNomisIdsOrNull(nomisPrisonId: String, nomisDayOfWeek: DayOfWeek, nomisSlotSequence: Int): VisitTimeSlotMappingDto? = api.prepare(
    api.getVisitTimeSlotMappingByNomisIdsRequestConfig(
      nomisPrisonId = nomisPrisonId,
      nomisDayOfWeek = nomisDayOfWeek.asMappingDayOfWeek(),
      nomisSlotSequence = nomisSlotSequence,
    ),
  )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getInternalLocationByNomisId(nomisLocationId: Long): LocationMappingDto = locationApi.getMappingGivenNomisId1(nomisLocationId = nomisLocationId).awaitSingle()
}

private fun DayOfWeek.asMappingDayOfWeek(): VisitSlotsResourceApi.NomisDayOfWeekGetVisitTimeSlotMappingByNomisIds = when (this) {
  DayOfWeek.MONDAY -> VisitSlotsResourceApi.NomisDayOfWeekGetVisitTimeSlotMappingByNomisIds.MONDAY
  DayOfWeek.TUESDAY -> VisitSlotsResourceApi.NomisDayOfWeekGetVisitTimeSlotMappingByNomisIds.TUESDAY
  DayOfWeek.WEDNESDAY -> VisitSlotsResourceApi.NomisDayOfWeekGetVisitTimeSlotMappingByNomisIds.WEDNESDAY
  DayOfWeek.THURSDAY -> VisitSlotsResourceApi.NomisDayOfWeekGetVisitTimeSlotMappingByNomisIds.THURSDAY
  DayOfWeek.FRIDAY -> VisitSlotsResourceApi.NomisDayOfWeekGetVisitTimeSlotMappingByNomisIds.FRIDAY
  DayOfWeek.SATURDAY -> VisitSlotsResourceApi.NomisDayOfWeekGetVisitTimeSlotMappingByNomisIds.SATURDAY
  DayOfWeek.SUNDAY -> VisitSlotsResourceApi.NomisDayOfWeekGetVisitTimeSlotMappingByNomisIds.SUNDAY
}
