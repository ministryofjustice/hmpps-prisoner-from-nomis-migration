package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.atPrisonPersonZone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesSyncResponse
import java.time.LocalDateTime

@Service
class PhysicalAttributesDpsApiService(@Qualifier("prisonPersonApiWebClient") private val webClient: WebClient) {

  suspend fun syncPhysicalAttributes(
    prisonerNumber: String,
    heightCentimetres: Int?,
    weightKilograms: Int?,
    appliesFrom: LocalDateTime,
    appliesTo: LocalDateTime?,
    latestBooking: Boolean,
    createdAt: LocalDateTime,
    createdBy: String,
  ): PhysicalAttributesSyncResponse =
    webClient
      .put()
      .uri("/sync/prisoners/{prisonerNumber}/physical-attributes", prisonerNumber)
      .bodyValue(syncPhysicalAttributesRequest(heightCentimetres, weightKilograms, appliesFrom, appliesTo, latestBooking, createdAt, createdBy))
      .retrieve()
      .awaitBody()

  private fun syncPhysicalAttributesRequest(
    heightCentimetres: Int?,
    weightKilograms: Int?,
    appliesFrom: LocalDateTime,
    appliesTo: LocalDateTime?,
    latestBooking: Boolean,
    createdAt: LocalDateTime,
    createdBy: String,
  ) =
    PhysicalAttributesSyncRequest(
      height = heightCentimetres,
      weight = weightKilograms,
      appliesFrom = appliesFrom.atPrisonPersonZone(),
      appliesTo = appliesTo?.atPrisonPersonZone(),
      latestBooking = latestBooking,
      createdAt = createdAt.atPrisonPersonZone(),
      createdBy = createdBy,
    )

  suspend fun migratePhysicalAttributes(
    prisonerNumber: String,
    requests: List<PhysicalAttributesMigrationRequest>,
  ): PhysicalAttributesMigrationResponse =
    webClient
      .put()
      .uri("/migration/prisoners/{prisonerNumber}/physical-attributes", prisonerNumber)
      .bodyValue(requests)
      .retrieve()
      .awaitBody()

  fun migratePhysicalAttributesRequest(
    heightCentimetres: Int?,
    weightKilograms: Int?,
    appliesFrom: LocalDateTime,
    appliesTo: LocalDateTime?,
    createdAt: LocalDateTime,
    createdBy: String,
  ) =
    PhysicalAttributesMigrationRequest(
      height = heightCentimetres,
      weight = weightKilograms,
      appliesFrom = appliesFrom.atPrisonPersonZone(),
      appliesTo = appliesTo?.atPrisonPersonZone(),
      createdAt = createdAt.atPrisonPersonZone(),
      createdBy = createdBy,
    )
}
