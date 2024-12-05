package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.identifyingmarks

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.atPrisonPersonZone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.DistinguishingMarkCreationSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.DistinguishingMarkSyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.DistinguishingMarkUpdateSyncRequest
import java.time.LocalDateTime
import java.util.UUID

@Service
class DistinguishingMarksDpsApiService(@Qualifier("prisonPersonApiWebClient") private val webClient: WebClient) {

  suspend fun syncCreateDistinguishingMark(
    prisonerNumber: String,
    markType: String,
    bodyPart: String,
    side: String?,
    partOrientation: String?,
    comment: String?,
    appliesFrom: LocalDateTime,
    appliesTo: LocalDateTime?,
    latestBooking: Boolean,
    createdAt: LocalDateTime,
    createdBy: String,
  ): DistinguishingMarkSyncResponse =
    webClient
      .post()
      .uri("/sync/prisoners/{prisonerNumber}/distinguishing-marks", prisonerNumber)
      .bodyValue(syncCreateDistinguishingMarkRequest(markType, bodyPart, side, partOrientation, comment, appliesFrom, appliesTo, latestBooking, createdAt, createdBy))
      .retrieve()
      .awaitBody()

  private fun syncCreateDistinguishingMarkRequest(
    markType: String,
    bodyPart: String,
    side: String?,
    partOrientation: String?,
    comment: String?,
    appliesFrom: LocalDateTime,
    appliesTo: LocalDateTime?,
    latestBooking: Boolean,
    createdAt: LocalDateTime,
    createdBy: String,
  ) =
    DistinguishingMarkCreationSyncRequest(
      markType = markType,
      bodyPart = bodyPart,
      side = side,
      partOrientation = partOrientation,
      comment = comment,
      appliesFrom = appliesFrom.atPrisonPersonZone(),
      appliesTo = appliesTo?.atPrisonPersonZone(),
      latestBooking = latestBooking,
      createdAt = createdAt.atPrisonPersonZone(),
      createdBy = createdBy,
    )

  suspend fun syncUpdateDistinguishingMark(
    prisonerNumber: String,
    dpsId: UUID,
    markType: String,
    bodyPart: String,
    side: String?,
    partOrientation: String?,
    comment: String?,
    appliesFrom: LocalDateTime,
    appliesTo: LocalDateTime?,
    latestBooking: Boolean,
    createdAt: LocalDateTime,
    createdBy: String,
  ): DistinguishingMarkSyncResponse =
    webClient
      .put()
      .uri("/sync/prisoners/{prisonerNumber}/distinguishing-marks/{dpsId}", prisonerNumber, dpsId)
      .bodyValue(syncUpdateDistinguishingMarkRequest(dpsId, markType, bodyPart, side, partOrientation, comment, appliesFrom, appliesTo, latestBooking, createdAt, createdBy))
      .retrieve()
      .awaitBody()

  private fun syncUpdateDistinguishingMarkRequest(
    dpsId: UUID,
    markType: String,
    bodyPart: String,
    side: String?,
    partOrientation: String?,
    comment: String?,
    appliesFrom: LocalDateTime,
    appliesTo: LocalDateTime?,
    latestBooking: Boolean,
    createdAt: LocalDateTime,
    createdBy: String,
  ) =
    DistinguishingMarkUpdateSyncRequest(
      uuid = dpsId,
      markType = markType,
      bodyPart = bodyPart,
      side = side,
      partOrientation = partOrientation,
      comment = comment,
      appliesFrom = appliesFrom.atPrisonPersonZone(),
      appliesTo = appliesTo?.atPrisonPersonZone(),
      latestBooking = latestBooking,
      createdAt = createdAt.atPrisonPersonZone(),
      createdBy = createdBy,
    )

  suspend fun syncDeleteDistinguishingMark(
    prisonerNumber: String,
    dpsId: UUID,
  ): DistinguishingMarkSyncResponse =
    webClient
      .delete()
      .uri("/sync/prisoners/{prisonerNumber}/distinguishing-marks/{dpsId}", prisonerNumber, dpsId)
      .retrieve()
      .awaitBody()
}
