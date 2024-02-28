package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.health

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.hmpps.kotlin.health.HealthPingCheck

@Component
class NomisApiHealth(@Qualifier("nomisApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component
class HmppsAuthApiHealth(@Qualifier("hmppsAuthApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)

@Component("nomisMappingApi")
class MappingApiHealth(@Qualifier("mappingApiHealthWebClient") webClient: WebClient) : HealthPingCheck(webClient)
