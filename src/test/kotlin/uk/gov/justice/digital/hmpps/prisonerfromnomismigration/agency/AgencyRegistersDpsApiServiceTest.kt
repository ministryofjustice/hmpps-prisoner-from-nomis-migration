package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyRegistersDpsApiExtension.Companion.agencyRegistersApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@ExtendWith(AgencyRegistersDpsApiExtension::class)
@SpringAPIServiceTest
@Import(AgencyRegistersDpsApiService::class, AgencyConfiguration::class)
class AgencyRegistersDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: AgencyRegistersDpsApiService

  @Nested
  inner class MigrateAgency {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
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
