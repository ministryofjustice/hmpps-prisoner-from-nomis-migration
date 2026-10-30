package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
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
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiMockServer.Companion.resyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiMockServer.Companion.syncCustodialSeriesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationDpsApiMockServer.Companion.syncExternalMovementRequest
import java.util.UUID

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

  @Nested
  inner class SyncExternalMovement {
    @Test
    fun `should call the sync endpoint and pass the request`() = runTest {
      dpsPersonLocationServer.stubSyncExternalMovement("A1234BC")

      apiService.syncExternalMovement("A1234BC", syncExternalMovementRequest())

      dpsPersonLocationServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/external-movements/A1234BC"))
          .withRequestBody(matchingJsonPath("syncUser.username", equalTo("USER")))
          .withRequestBody(matchingJsonPath("movement.legacyBookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("movement.journey.type", equalTo("TRANSFER"))),
      )
    }

    @Test
    fun `should rethrow bad request`() = runTest {
      dpsPersonLocationServer.stubSyncExternalMovement(status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.syncExternalMovement("A1234BC", syncExternalMovementRequest())
      }
    }
  }

  @Nested
  inner class DeleteExternalMovement {
    private val dpsId = UUID.randomUUID()

    @Test
    fun `should call the delete endpoint`() = runTest {
      dpsPersonLocationServer.stubDeleteExternalMovement(dpsId)

      apiService.deleteExternalMovement(dpsId)

      dpsPersonLocationServer.verify(deleteRequestedFor(urlPathEqualTo("/sync/external-movements/$dpsId")))
    }

    @Test
    fun `should throw if not found`() = runTest {
      dpsPersonLocationServer.stubDeleteExternalMovement(dpsId, status = NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.deleteExternalMovement(dpsId)
      }
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsPersonLocationServer.stubDeleteExternalMovement(dpsId, status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteExternalMovement(dpsId)
      }
    }
  }

  @Nested
  inner class DeleteCustodialSeries {
    private val dpsId = UUID.randomUUID()

    @Test
    fun `should call the delete endpoint`() = runTest {
      dpsPersonLocationServer.stubDeleteCustodialSeries(dpsId)

      apiService.deleteCustodialSeries(dpsId)

      dpsPersonLocationServer.verify(deleteRequestedFor(urlPathEqualTo("/sync/custodial-series/$dpsId")))
    }

    @Test
    fun `should throw if not found`() = runTest {
      dpsPersonLocationServer.stubDeleteCustodialSeries(dpsId, status = NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.deleteCustodialSeries(dpsId)
      }
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsPersonLocationServer.stubDeleteCustodialSeries(dpsId, status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteCustodialSeries(dpsId)
      }
    }
  }

  @Nested
  inner class SyncCustodialSeries {
    @Test
    fun `should call the sync endpoint and pass the request`() = runTest {
      dpsPersonLocationServer.stubSyncCustodialSeries("A1234BC")

      apiService.syncCustodialSeries("A1234BC", syncCustodialSeriesRequest())

      dpsPersonLocationServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/custodial-series/A1234BC"))
          .withRequestBody(matchingJsonPath("syncUser.username", equalTo("USER")))
          .withRequestBody(matchingJsonPath("custodialSeries.legacyBookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("custodialSeries.status", equalTo("OPEN"))),
      )
    }

    @Test
    fun `should rethrow bad request`() = runTest {
      dpsPersonLocationServer.stubSyncCustodialSeries(status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.syncCustodialSeries("A1234BC", syncCustodialSeriesRequest())
      }
    }
  }
}
