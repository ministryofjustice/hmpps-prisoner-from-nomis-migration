package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.BAD_GATEWAY
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonNomisSyncApiExtension.Companion.nomisSyncApi

@SpringAPIServiceTest
@Import(PrisonPersonNomisSyncApiService::class, PrisonPersonNomisSyncApiMockServer::class)
class PrisonPersonNomisSyncApiServiceTest {
  @Autowired
  private lateinit var apiService: PrisonPersonNomisSyncApiService

  @Nested
  inner class SyncPhysicalAttributes {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisSyncApi.stubSyncPhysicalAttributes(prisonerNumber = "A1234AA")

      apiService.syncPhysicalAttributes(prisonerNumber = "A1234AA")

      nomisSyncApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      nomisSyncApi.stubSyncPhysicalAttributes(prisonerNumber = "A1234AA")

      apiService.syncPhysicalAttributes(prisonerNumber = "A1234AA")

      nomisSyncApi.verify(
        putRequestedFor(urlPathEqualTo("/prisonperson/A1234AA/physical-attributes")),
      )
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      nomisSyncApi.stubSyncPhysicalAttributes(status = BAD_GATEWAY)

      assertThrows<WebClientResponseException.BadGateway> {
        apiService.syncPhysicalAttributes("A1234AA")
      }
    }
  }
}
