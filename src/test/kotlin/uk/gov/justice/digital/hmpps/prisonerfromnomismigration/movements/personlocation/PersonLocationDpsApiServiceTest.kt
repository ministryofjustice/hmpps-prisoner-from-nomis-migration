package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

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
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiMockServer.Companion.resyncExternalMovementsRequest

@ExtendWith(PersonLocationDpsApiExtension::class)
@SpringAPIServiceTest
@Import(PersonLocationDpsApiService::class, PersonLocationConfiguration::class, PersonLocationDpsApiMockServer::class)
class PersonLocationDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: PersonLocationDpsApiService

  @Nested
  inner class Resync {
    val request = resyncExternalMovementsRequest()

    @Test
    internal fun `should pass oauth2 token`() = runTest {
      dpsPersonLocationServer.stubResyncPrisoner()

      apiService.resyncPerson("A1234BC", request)

      dpsPersonLocationServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the resync endpoint`() = runTest {
      dpsPersonLocationServer.stubResyncPrisoner()

      apiService.resyncPerson("A1234BC", request)

      dpsPersonLocationServer.verify(
        putRequestedFor(urlPathEqualTo("/resync/external-movements/A1234BC")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsPersonLocationServer.stubResyncPrisoner(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.resyncPerson("A1234BC", request)
      }
    }

    @Test
    fun `should return null if not found`() = runTest {
      dpsPersonLocationServer.stubResyncPrisoner(status = NOT_FOUND)

      assertThat(apiService.resyncPerson("A1234BC", request)).isNull()
    }
  }
}
