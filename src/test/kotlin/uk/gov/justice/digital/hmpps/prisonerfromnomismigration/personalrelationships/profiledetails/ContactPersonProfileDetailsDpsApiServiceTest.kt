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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerDomesticStatusResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerNumberOfChildrenResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerDomesticStatusRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerNumberOfChildrenRequest
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(ContactPersonProfileDetailsDpsApiService::class, ContactPersonConfiguration::class, ContactPersonProfileDetailsDpsApiMockServer::class)
class ContactPersonProfileDetailsDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: ContactPersonProfileDetailsDpsApiService

  @Autowired
  private lateinit var dpsApi: ContactPersonProfileDetailsDpsApiMockServer

  @Nested
  inner class SyncDomesticStatus {
    private val prisonerNumber = "A1234AA"
    private val domesticStatus = "M"
    private val createdDateTime = LocalDateTime.parse("2025-02-19T12:34:56")
    private val createdBy = "A_USER"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubSyncDomesticStatus(prisonerNumber, aResponse())

      apiService.syncDomesticStatus(prisonerNumber, aRequest())

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/$prisonerNumber/domestic-status"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubSyncDomesticStatus(prisonerNumber, aResponse())

      apiService.syncDomesticStatus(prisonerNumber, aRequest())

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/$prisonerNumber/domestic-status"))
          .withRequestBody(matchingJsonPath("domesticStatusCode", equalTo(domesticStatus)))
          .withRequestBody(matchingJsonPath("createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("createdTime", equalTo("$createdDateTime"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubSyncDomesticStatus(prisonerNumber, aResponse())

      val response = apiService.syncDomesticStatus(prisonerNumber, aRequest())

      assertThat(response.id).isEqualTo(321)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubSyncDomesticStatus(prisonerNumber, INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncDomesticStatus(prisonerNumber, aRequest())
      }
    }

    private fun aRequest() = SyncUpdatePrisonerDomesticStatusRequest(
      domesticStatusCode = domesticStatus,
      createdBy = createdBy,
      createdTime = createdDateTime,
    )

    private fun aResponse(dpsId: Long = 321) = SyncPrisonerDomesticStatusResponse(
      id = dpsId,
      active = true,
      domesticStatusCode = "M",
      createdTime = LocalDateTime.now(),
      createdBy = "A_USER",
    )
  }

  @Nested
  inner class SyncDependants {
    private val prisonerNumber = "A1234AA"
    private val numberOfChildren = "3"
    private val createdDateTime = LocalDateTime.parse("2025-02-19T12:34:56")
    private val createdBy = "A_USER"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubSyncNumberOfChildren(prisonerNumber, aResponse())

      apiService.syncNumberOfChildren(prisonerNumber, aRequest())

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/$prisonerNumber/number-of-children"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubSyncNumberOfChildren(prisonerNumber, aResponse())

      apiService.syncNumberOfChildren(prisonerNumber, aRequest())

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/$prisonerNumber/number-of-children"))
          .withRequestBody(matchingJsonPath("numberOfChildren", equalTo(numberOfChildren)))
          .withRequestBody(matchingJsonPath("createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("createdTime", equalTo("$createdDateTime"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubSyncNumberOfChildren(prisonerNumber, aResponse())

      val response = apiService.syncNumberOfChildren(prisonerNumber, aRequest())

      assertThat(response.id).isEqualTo(321)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubSyncNumberOfChildren(prisonerNumber, INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncNumberOfChildren(prisonerNumber, aRequest())
      }
    }

    private fun aRequest() = SyncUpdatePrisonerNumberOfChildrenRequest(
      numberOfChildren = numberOfChildren,
      createdBy = createdBy,
      createdTime = createdDateTime,
    )

    private fun aResponse(dpsId: Long = 321) = SyncPrisonerNumberOfChildrenResponse(dpsId)
  }
}
