package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonConfiguration
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(ProfileDetailsDpsApiService::class, ContactPersonConfiguration::class, ContactPersonProfileDetailsDpsApiMockServer::class)
class ContactPersonProfileDetailsDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: ProfileDetailsDpsApiService

  @Autowired
  private lateinit var dpsApi: ContactPersonProfileDetailsDpsApiMockServer

  @Nested
  inner class SyncDomesticStatus {
    private val prisonerNumber = "A1234AA"
    private val domesticStatus = "YES"
    private val createdDateTime = LocalDateTime.parse("2025-02-19T12:34:56")
    private val createdBy = "A_USER"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubSyncDomesticStatus(prisonerNumber, aResponse())

      apiService.syncDomesticStatus(prisonerNumber, aRequest())

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/domestic-status/$prisonerNumber"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubSyncDomesticStatus(prisonerNumber, aResponse())

      apiService.syncDomesticStatus(prisonerNumber, aRequest())

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/domestic-status/$prisonerNumber"))
          .withRequestBody(matchingJsonPath("domesticStatusCode", equalTo(domesticStatus)))
          .withRequestBody(matchingJsonPath("createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("createdDateTime", equalTo("$createdDateTime"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubSyncDomesticStatus(prisonerNumber, aResponse())

      val response = apiService.syncDomesticStatus(prisonerNumber, aRequest())

      assertThat(response.domesticStatusId).isEqualTo(321)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubSyncDomesticStatus(prisonerNumber, INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncDomesticStatus(prisonerNumber, aRequest())
      }
    }

    private fun aRequest() = DomesticStatusSyncRequest(
      domesticStatusCode = domesticStatus,
      createdBy = createdBy,
      createdDateTime = createdDateTime,
    )

    private fun aResponse(dpsId: Long = 321) = DomesticStatusSyncResponse(dpsId)
  }

  @Nested
  inner class SyncDependants {
    private val prisonerNumber = "A1234AA"
    private val dependants = "3"
    private val createdDateTime = LocalDateTime.parse("2025-02-19T12:34:56")
    private val createdBy = "A_USER"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubSyncDependants(prisonerNumber, aResponse())

      apiService.syncDependants(prisonerNumber, aRequest())

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/dependants/$prisonerNumber"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubSyncDependants(prisonerNumber, aResponse())

      apiService.syncDependants(prisonerNumber, aRequest())

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/dependants/$prisonerNumber"))
          .withRequestBody(matchingJsonPath("dependants", equalTo(dependants)))
          .withRequestBody(matchingJsonPath("createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("createdDateTime", equalTo("$createdDateTime"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubSyncDependants(prisonerNumber, aResponse())

      val response = apiService.syncDependants(prisonerNumber, aRequest())

      assertThat(response.dependantsId).isEqualTo(321)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubSyncDependants(prisonerNumber, INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncDependants(prisonerNumber, aRequest())
      }
    }

    private fun aRequest() = DependantsSyncRequest(
      dependants = dependants,
      createdBy = createdBy,
      createdDateTime = createdDateTime,
    )

    private fun aResponse(dpsId: Long = 321) = DependantsSyncResponse(dpsId)
  }
}
