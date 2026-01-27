package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension.Companion.dpsOfficialVisitsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.migrateVisitConfigRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.migrateVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncCreateTimeSlotRequest

@SpringAPIServiceTest
@Import(OfficialVisitsDpsApiService::class, OfficialVisitsConfiguration::class)
class OfficialVisitsDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: OfficialVisitsDpsApiService

  @Nested
  inner class MigrateVisitConfiguration {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubMigrateVisitConfiguration()

      apiService.migrateVisitConfiguration(migrateVisitConfigRequest())

      dpsOfficialVisitsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      dpsOfficialVisitsServer.stubMigrateVisitConfiguration()

      apiService.migrateVisitConfiguration(migrateVisitConfigRequest())

      dpsOfficialVisitsServer.verify(
        postRequestedFor(urlPathEqualTo("/migrate/visit-configuration")),
      )
    }
  }

  @Nested
  inner class MigrateVisit {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubMigrateVisit()

      apiService.migrateVisit(migrateVisitRequest())

      dpsOfficialVisitsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      dpsOfficialVisitsServer.stubMigrateVisit()

      apiService.migrateVisit(migrateVisitRequest())

      dpsOfficialVisitsServer.verify(
        postRequestedFor(urlPathEqualTo("/migrate/visit")),
      )
    }
  }

  @Nested
  inner class CreateTimeSlot {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubCreateTimeSlot()

      apiService.createTimeSlot(syncCreateTimeSlotRequest())

      dpsOfficialVisitsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create endpoint`() = runTest {
      dpsOfficialVisitsServer.stubCreateTimeSlot()

      apiService.createTimeSlot(syncCreateTimeSlotRequest())

      dpsOfficialVisitsServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/time-slot")),
      )
    }
  }
}
