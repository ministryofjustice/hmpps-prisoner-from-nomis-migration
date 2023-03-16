package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping

@Service
class IncentiveMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<IncentiveNomisMapping>(domainUrl = "/mapping/incentives", webClient) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun findNomisIncentiveMapping(nomisBookingId: Long, nomisIncentiveSequence: Long): IncentiveNomisMapping? =
    webClient.get()
      .uri(
        "/mapping/incentives/nomis-booking-id/{nomisBookingId}/nomis-incentive-sequence/{nomisIncentiveSequence}",
        nomisBookingId,
        nomisIncentiveSequence,
      )
      .retrieve()
      .bodyToMono(IncentiveNomisMapping::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }.awaitSingleOrNull()

  suspend fun deleteIncentiveMapping(incentiveId: Long): Unit? = webClient.delete()
    .uri(
      "/mapping/incentives/incentive-id/{incentiveId}",
      incentiveId,
    )
    .retrieve()
    .bodyToMono(Unit::class.java)
    .onErrorResume(WebClientResponseException::class.java) {
      log.error("Unable to deleting mapping for incentiveId $incentiveId but ignoring and allowing dangling record", it)
      Mono.empty()
    }.awaitSingleOrNull()
}

data class IncentiveNomisMapping(
  val nomisBookingId: Long,
  val nomisIncentiveSequence: Long,
  val incentiveId: Long,
  val label: String? = null,
  val mappingType: String,
)
