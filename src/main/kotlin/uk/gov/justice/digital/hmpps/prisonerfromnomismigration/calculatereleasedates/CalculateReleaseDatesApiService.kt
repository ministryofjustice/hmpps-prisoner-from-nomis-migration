package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.api.LegacySentenceCalculationControllerApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationToCrdsMappingResult

@Service
class CalculateReleaseDatesApiService(@Qualifier("calculateReleaseDatesApiWebClient") webClient: WebClient) {
  private val api = LegacySentenceCalculationControllerApi(webClient)

  suspend fun migratePrisoner(prisonerId: String, request: LegacySentenceCalculationMigrationRequest): LegacySentenceCalculationMigrationResponse = api.migrate(prisonerId, request).awaitSingle()

  suspend fun syncNew(request: LegacySentenceCalculationDto): LegacySentenceCalculationToCrdsMappingResult = api.syncNew(request).awaitSingle()

  suspend fun syncUpdated(request: LegacySentenceCalculationDto): LegacySentenceCalculationToCrdsMappingResult = api.syncUpdated(request).awaitSingle()

  suspend fun syncDeleted(nomisCalculationId: Long) {
    api.syncDeleted(nomisCalculationId).awaitSingle()
  }

  suspend fun getAllForPrisoner(prisonerId: String): List<LegacySentenceCalculationDto> = api.getAllForPrisoner(prisonerId).awaitSingle()
}
