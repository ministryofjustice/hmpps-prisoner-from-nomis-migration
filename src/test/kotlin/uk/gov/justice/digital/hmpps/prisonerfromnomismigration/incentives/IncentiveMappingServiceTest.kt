package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@SpringAPIServiceTest
@Import(IncentiveMappingService::class, IncentivesConfiguration::class)
class IncentiveMappingServiceTest {
  @Autowired
  private lateinit var incentiveMappingService: IncentiveMappingService

  @Nested
  inner class DeleteIncentiveMapping {
    @Test
    internal fun `will delete the incentive mapping`() {
      mappingApi.stubFor(
        delete(urlPathMatching("/mapping/incentives/incentive-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NO_CONTENT.value()),
        ),
      )

      runBlocking {
        incentiveMappingService.deleteIncentiveMapping(9876)
      }

      mappingApi.verify(
        deleteRequestedFor(
          urlPathEqualTo("/mapping/incentives/incentive-id/9876"),
        ).withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will ignore any errors and allow deletion to fail`() {
      mappingApi.stubFor(
        delete(urlPathMatching("/mapping/incentives/incentive-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.SERVICE_UNAVAILABLE.value()),
        ),
      )

      runBlocking {
        incentiveMappingService.deleteIncentiveMapping(9876)
      }
    }
  }
}
