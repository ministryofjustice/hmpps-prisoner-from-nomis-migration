package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

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
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(ContactPersonNomisSyncApiService::class, ContactPersonNomisSyncApiMockServer::class)
class ContactPersonNomisSyncApiServiceTest {
  @Autowired
  private lateinit var apiService: ContactPersonNomisSyncApiService

  @Autowired
  private lateinit var nomisSyncApi: ContactPersonNomisSyncApiMockServer

  @Nested
  inner class SyncProfileDetails {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisSyncApi.stubSyncProfileDetails(prisonerNumber = "A1234AA", profileType = "MARITAL")

      apiService.syncProfileDetails(prisonerNumber = "A1234AA", profileType = "MARITAL")

      nomisSyncApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      nomisSyncApi.stubSyncProfileDetails(prisonerNumber = "A1234AA", profileType = "MARITAL")

      apiService.syncProfileDetails(prisonerNumber = "A1234AA", profileType = "MARITAL")

      nomisSyncApi.verify(
        putRequestedFor(urlPathEqualTo("/contactperson/sync/profile-details/A1234AA/MARITAL")),
      )
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      nomisSyncApi.stubSyncProfileDetails(
        prisonerNumber = "A1234AA",
        profileType = "MARITAL",
        status = HttpStatus.BAD_GATEWAY,
      )

      assertThrows<WebClientResponseException.BadGateway> {
        apiService.syncProfileDetails(prisonerNumber = "A1234AA", profileType = "MARITAL")
      }
    }
  }
}
