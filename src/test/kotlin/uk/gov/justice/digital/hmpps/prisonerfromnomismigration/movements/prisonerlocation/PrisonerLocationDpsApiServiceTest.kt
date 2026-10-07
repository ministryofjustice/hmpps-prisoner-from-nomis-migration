package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.prisonerlocation

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.prisonerlocation.PrisonerLocationDpsApiExtension.Companion.dpsPrisonerLocationServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.prisonerlocation.PrisonerLocationDpsApiMockServer.Companion.resyncExternalMovementsRequest

@ExtendWith(PrisonerLocationDpsApiExtension::class)
@SpringAPIServiceTest
@Import(PrisonerLocationDpsApiService::class, PrisonerLocationConfiguration::class, PrisonerLocationDpsApiMockServer::class)
class PrisonerLocationDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: PrisonerLocationDpsApiService

  @Nested
  inner class Resync {
    val request = resyncExternalMovementsRequest()

    @Test
    internal fun `should pass oauth2 token`() = runTest {
      dpsPrisonerLocationServer.stubResyncPrisoner()

      apiService.resyncPrisoner("A1234BC", request)

      dpsPrisonerLocationServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the resync endpoint`() = runTest {
      dpsPrisonerLocationServer.stubResyncPrisoner()

      apiService.resyncPrisoner("A1234BC", request)

      dpsPrisonerLocationServer.verify(
        putRequestedFor(urlPathEqualTo("/resync/external-movements/A1234BC")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsPrisonerLocationServer.stubResyncPrisoner(status = 500)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.resyncPrisoner("A1234BC", request)
      }
    }

    @Test
    fun `should return null if not found`() = runTest {
      dpsPrisonerLocationServer.stubResyncPrisoner(status = 404)

      assertThat(apiService.resyncPrisoner("A1234BC", request)).isNull()
    }
  }
}
