package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.DomesticStatusDetailsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigratePrisonerDomesticStatusRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigratePrisonerNumberOfChildrenRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.NumberOfChildrenDetailsRequest
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

    private fun aResponse(dpsId: Long = 321) = SyncPrisonerNumberOfChildrenResponse(dpsId, active = true)
  }

  @Nested
  inner class MigrateDomesticStatus {
    private val prisonerNumber = "A1234AA"
    private val domesticStatus = "M"
    private val createdDateTime = LocalDateTime.parse("2025-02-19T12:34:56")
    private val createdBy = "A_USER"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubMigrateDomesticStatus()

      apiService.migrateDomesticStatus(aRequest())

      dpsApi.verify(
        postRequestedFor(urlPathMatching("/migrate/domestic-status"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubMigrateDomesticStatus()

      apiService.migrateDomesticStatus(aRequest())

      dpsApi.verify(
        postRequestedFor(urlPathMatching("/migrate/domestic-status"))
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo(prisonerNumber)))
          .withRequestBody(matchingJsonPath("current.domesticStatusCode", equalTo(domesticStatus)))
          .withRequestBody(matchingJsonPath("current.createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("current.createdTime", equalTo("$createdDateTime")))
          .withRequestBody(matchingJsonPath("history[0].domesticStatusCode", equalTo(domesticStatus)))
          .withRequestBody(matchingJsonPath("history[0].createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("history[0].createdTime", equalTo("$createdDateTime")))
          .withRequestBody(matchingJsonPath("history[1].domesticStatusCode", equalTo(domesticStatus)))
          .withRequestBody(matchingJsonPath("history[1].createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("history[1].createdTime", equalTo("$createdDateTime"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubMigrateDomesticStatus()

      val response = apiService.migrateDomesticStatus(aRequest())

      assertThat(response.prisonerNumber).isEqualTo(prisonerNumber)
      assertThat(response.current).isEqualTo(1)
      assertThat(response.history).containsExactlyElementsOf(listOf(2, 3))
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubMigrateDomesticStatus(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.migrateDomesticStatus(aRequest())
      }
    }

    private fun aRequest() = MigratePrisonerDomesticStatusRequest(
      prisonerNumber = prisonerNumber,
      current = DomesticStatusDetailsRequest(createdBy, createdDateTime, domesticStatus),
      history = listOf(
        DomesticStatusDetailsRequest(createdBy, createdDateTime, domesticStatus),
        DomesticStatusDetailsRequest(createdBy, createdDateTime, domesticStatus),
      ),
    )
  }

  @Nested
  inner class MigrateDependants {
    private val prisonerNumber = "A1234AA"
    private val numberOfChildren = "3"
    private val createdDateTime = LocalDateTime.parse("2025-02-19T12:34:56")
    private val createdBy = "A_USER"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubMigrateNumberOfChildren(migrateNumberOfChildrenResponse())

      apiService.migrateNumberOfChildren(aRequest())

      dpsApi.verify(
        postRequestedFor(urlPathMatching("/migrate/number-of-children"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubMigrateNumberOfChildren(migrateNumberOfChildrenResponse())

      apiService.migrateNumberOfChildren(aRequest())

      dpsApi.verify(
        postRequestedFor(urlPathMatching("/migrate/number-of-children"))
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo(prisonerNumber)))
          .withRequestBody(matchingJsonPath("current.numberOfChildren", equalTo(numberOfChildren)))
          .withRequestBody(matchingJsonPath("current.createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("current.createdTime", equalTo("$createdDateTime")))
          .withRequestBody(matchingJsonPath("history[0].numberOfChildren", equalTo(numberOfChildren)))
          .withRequestBody(matchingJsonPath("history[0].createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("history[0].createdTime", equalTo("$createdDateTime")))
          .withRequestBody(matchingJsonPath("history[1].numberOfChildren", equalTo(numberOfChildren)))
          .withRequestBody(matchingJsonPath("history[1].createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("history[1].createdTime", equalTo("$createdDateTime"))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubMigrateNumberOfChildren(migrateNumberOfChildrenResponse())

      val response = apiService.migrateNumberOfChildren(aRequest())

      assertThat(response.prisonerNumber).isEqualTo(prisonerNumber)
      assertThat(response.current).isEqualTo(4)
      assertThat(response.history).containsExactlyElementsOf(listOf(5, 6))
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubMigrateNumberOfChildren(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.migrateNumberOfChildren(aRequest())
      }
    }

    private fun aRequest() = MigratePrisonerNumberOfChildrenRequest(
      prisonerNumber = prisonerNumber,
      current = NumberOfChildrenDetailsRequest(createdBy, createdDateTime, numberOfChildren),
      history = listOf(
        NumberOfChildrenDetailsRequest(createdBy, createdDateTime, numberOfChildren),
        NumberOfChildrenDetailsRequest(createdBy, createdDateTime, numberOfChildren),
      ),
    )
  }

  @Nested
  inner class MergeProfileDetails {
    private val keepingPrisonerNumber = "A1234AA"
    private val removedPrisonerNumber = "B1234BB"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubMergeProfileDetails(keepingPrisonerNumber, removedPrisonerNumber)

      apiService.mergeProfileDetails(keepingPrisonerNumber, removedPrisonerNumber)

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/merge/keep/$keepingPrisonerNumber/remove/$removedPrisonerNumber"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubMergeProfileDetails(keepingPrisonerNumber, removedPrisonerNumber, INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.mergeProfileDetails(keepingPrisonerNumber, removedPrisonerNumber)
      }
    }
  }
}
