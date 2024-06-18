package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonerprofile

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesHistoryDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesSyncRequest
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class PrisonPersonDpsApiService(@Qualifier("prisonPersonApiWebClient") private val webClient: WebClient) {
  private val zone = ZoneId.of("Europe/London")

  suspend fun syncPhysicalAttributes(
    prisonerNumber: String,
    heightCentimetres: Int?,
    weightKilograms: Int?,
    appliesFrom: LocalDateTime,
    appliesTo: LocalDateTime?,
    createdAt: LocalDateTime,
    createdBy: String,
  ): PhysicalAttributesHistoryDto =
    webClient
      .put()
      .uri("/sync/prisoners/{prisonerNumber}/physical-attributes", prisonerNumber)
      .bodyValue(updatePhysicalAttributesRequest(heightCentimetres, weightKilograms, appliesFrom, appliesTo, createdAt, createdBy))
      .retrieve()
      .awaitBody()

  private fun updatePhysicalAttributesRequest(
    heightCentimetres: Int?,
    weightKilograms: Int?,
    appliesFrom: LocalDateTime,
    appliesTo: LocalDateTime?,
    createdAt: LocalDateTime,
    createdBy: String,
  ) =
    PhysicalAttributesSyncRequest(
      height = heightCentimetres,
      weight = weightKilograms,
      appliesFrom = appliesFrom.atZone(zone).toString(),
      appliesTo = appliesTo?.atZone(zone)?.toString(),
      createdAt = createdAt.atZone(zone).toString(),
      createdBy = createdBy,
    )
}
