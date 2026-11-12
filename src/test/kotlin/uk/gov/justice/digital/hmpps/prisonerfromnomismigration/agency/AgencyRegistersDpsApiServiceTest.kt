package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyRegistersDpsApiExtension.Companion.agencyRegistersApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyRegistersDpsApiExtension.Companion.departmentDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyRegistersDpsApiExtension.Companion.legacyPrisonPhoneDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyAgencyType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyPrisonPhoneDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@ExtendWith(AgencyRegistersDpsApiExtension::class)
@SpringAPIServiceTest
@Import(AgencyRegistersDpsApiService::class, AgencyConfiguration::class)
class AgencyRegistersDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: AgencyRegistersDpsApiService

  @Nested
  inner class SyncPrison {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      agencyRegistersApi.stubSyncPrison("MDI")

      apiService.syncPrison("MDI", legacyAgencyDto().copy(agencyType = LegacyAgencyType.PRISON))

      agencyRegistersApi.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the sync prison endpoint with the request body`() = runTest {
      agencyRegistersApi.stubSyncPrison("MDI")
      val request = legacyAgencyDto().copy(agencyType = LegacyAgencyType.PRISON)

      apiService.syncPrison("MDI", request)

      val pattern = postRequestedFor(urlPathEqualTo("/legacy/sync/prison/id/MDI"))
      agencyRegistersApi.verify(pattern)
      assertThat(AgencyRegistersDpsApiExtension.getRequestBody<LegacyAgencyDto>(pattern)).isEqualTo(request)
    }

    @Test
    fun `will return the response`() = runTest {
      val response = legacyAgencyResponse()
      agencyRegistersApi.stubSyncPrison("MDI", response)

      assertThat(apiService.syncPrison("MDI", legacyAgencyDto().copy(agencyType = LegacyAgencyType.PRISON)))
        .isEqualTo(response)
    }
  }

  @Nested
  inner class UpdatePrisonPhoneNumber {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      agencyRegistersApi.stubUpdatePrisonPhoneNumber("MDI")

      apiService.updatePrisonPhoneNumber("MDI", legacyPrisonPhoneDto())

      agencyRegistersApi.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update prison phone number endpoint with the request body`() = runTest {
      agencyRegistersApi.stubUpdatePrisonPhoneNumber("MDI")
      val request = legacyPrisonPhoneDto()

      apiService.updatePrisonPhoneNumber("MDI", request)

      val pattern = putRequestedFor(urlPathEqualTo("/legacy/sync/prison/id/MDI/phone-number"))
      agencyRegistersApi.verify(pattern)
      assertThat(AgencyRegistersDpsApiExtension.getRequestBody<LegacyPrisonPhoneDto>(pattern)).isEqualTo(request)
    }

    @Test
    fun `will return the response`() = runTest {
      val response = departmentDto()
      agencyRegistersApi.stubUpdatePrisonPhoneNumber("MDI", response)

      assertThat(apiService.updatePrisonPhoneNumber("MDI", legacyPrisonPhoneDto())).isEqualTo(response)
    }
  }

  @Nested
  inner class SyncAgency {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      agencyRegistersApi.stubSyncAgency("SHFCC")

      apiService.syncAgency("SHFCC", legacyAgencyDto().copy(agencyType = LegacyAgencyType.COURT))

      agencyRegistersApi.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the sync agency endpoint with the request body`() = runTest {
      agencyRegistersApi.stubSyncAgency("SHFCC")
      val request = legacyAgencyDto().copy(agencyType = LegacyAgencyType.COURT)

      apiService.syncAgency("SHFCC", request)

      val pattern = postRequestedFor(urlPathEqualTo("/legacy/sync/agency/id/SHFCC"))
      agencyRegistersApi.verify(pattern)
      assertThat(AgencyRegistersDpsApiExtension.getRequestBody<LegacyAgencyDto>(pattern)).isEqualTo(request)
    }

    @Test
    fun `will return the response`() = runTest {
      val response = legacyAgencyResponse()
      agencyRegistersApi.stubSyncAgency("SHFCC", response)

      assertThat(apiService.syncAgency("SHFCC", legacyAgencyDto().copy(agencyType = LegacyAgencyType.COURT)))
        .isEqualTo(response)
    }
  }

  @Nested
  inner class MigrateAgency {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      agencyRegistersApi.stubMigrateAgency("SHEFCC", legacyAgencyResponse())

      apiService.migrateAgency("SHEFCC", legacyAgencyDto())

      agencyRegistersApi.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      agencyRegistersApi.stubMigrateAgency("SHEFCC", legacyAgencyResponse())

      apiService.migrateAgency("SHEFCC", legacyAgencyDto())

      agencyRegistersApi.verify(
        postRequestedFor(urlPathEqualTo("/legacy/migrate/agency/id/SHEFCC")),
      )
    }
  }
}
