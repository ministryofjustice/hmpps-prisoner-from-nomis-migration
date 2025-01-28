package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.identifyingmarks

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.atPrisonPersonZone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.DistinguishingMarkImageSyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.DistinguishingMarkImageUpdateSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.DistinguishingMarkSyncResponse
import java.time.LocalDateTime
import java.util.UUID

@Service
class DistinguishingMarkImagesDpsApiService(@Qualifier("prisonPersonApiWebClient") private val webClient: WebClient) {

  suspend fun syncCreateDistinguishingMarkImage(
    prisonerNumber: String,
    markId: UUID,
    imageSource: String,
    appliesFrom: LocalDateTime,
    appliesTo: LocalDateTime?,
    latestBooking: Boolean,
    createdAt: LocalDateTime,
    createdBy: String,
    image: ByteArray,
  ): DistinguishingMarkImageSyncResponse = webClient
    .post()
    .uri { builder ->
      builder.path("/sync/prisoners/{prisonerNumber}/distinguishing-marks/{markId}/images")
        .queryParam("imageSource", imageSource)
        .queryParam("appliesFrom", appliesFrom)
        .queryParam("appliesTo", appliesTo)
        .queryParam("createdAt", createdAt)
        .queryParam("createdBy", createdBy)
        .queryParam("latestBooking", latestBooking)
        .build(prisonerNumber, markId)
    }
    .body(BodyInserters.fromMultipartData(MultipartBodyBuilder().apply { part("file", image) }.build()))
    .retrieve()
    .awaitBody()

  suspend fun syncUpdateDistinguishingMarkImage(
    prisonerNumber: String,
    markId: UUID,
    imageId: UUID,
    default: Boolean,
    appliesFrom: LocalDateTime,
    appliesTo: LocalDateTime?,
    latestBooking: Boolean,
    createdAt: LocalDateTime,
    createdBy: String,
  ): DistinguishingMarkImageSyncResponse = webClient
    .put()
    .uri("/sync/prisoners/{prisonerNumber}/distinguishing-marks/{markId}/images/{imageId}", prisonerNumber, markId, imageId)
    .bodyValue(syncUpdateDistinguishingMarkRequest(default, appliesFrom, appliesTo, latestBooking, createdAt, createdBy))
    .retrieve()
    .awaitBody()

  private fun syncUpdateDistinguishingMarkRequest(
    default: Boolean,
    appliesFrom: LocalDateTime,
    appliesTo: LocalDateTime?,
    latestBooking: Boolean,
    createdAt: LocalDateTime,
    createdBy: String,
  ) = DistinguishingMarkImageUpdateSyncRequest(
    default = default,
    appliesFrom = appliesFrom.atPrisonPersonZone(),
    appliesTo = appliesTo?.atPrisonPersonZone(),
    latestBooking = latestBooking,
    createdAt = createdAt.atPrisonPersonZone(),
    createdBy = createdBy,
  )

  suspend fun syncDeleteDistinguishingMarkImage(
    prisonerNumber: String,
    markId: UUID,
    imageId: UUID,
  ): DistinguishingMarkSyncResponse = webClient
    .delete()
    .uri("/sync/prisoners/{prisonerNumber}/distinguishing-marks/{markId}/images/{imageId}", prisonerNumber, markId, imageId)
    .retrieve()
    .awaitBody()
}
