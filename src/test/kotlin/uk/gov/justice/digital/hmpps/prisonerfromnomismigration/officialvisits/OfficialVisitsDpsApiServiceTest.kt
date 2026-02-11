package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncCreateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncCreateOfficialVisitorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncCreateTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncCreateVisitSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncUpdateTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncUpdateVisitSlotRequest

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

  @Nested
  inner class UpdateTimeSlot {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubUpdateTimeSlot(123)

      apiService.updateTimeSlot(prisonTimeSlotId = 123, syncUpdateTimeSlotRequest())

      dpsOfficialVisitsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update endpoint`() = runTest {
      dpsOfficialVisitsServer.stubUpdateTimeSlot(123)

      apiService.updateTimeSlot(prisonTimeSlotId = 123, syncUpdateTimeSlotRequest())

      dpsOfficialVisitsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/time-slot/123")),
      )
    }
  }

  @Nested
  inner class DeleteTimeSlot {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubDeleteTimeSlot(123)

      apiService.deleteTimeSlot(prisonTimeSlotId = 123)

      dpsOfficialVisitsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete endpoint`() = runTest {
      dpsOfficialVisitsServer.stubDeleteTimeSlot(123)

      apiService.deleteTimeSlot(prisonTimeSlotId = 123)

      dpsOfficialVisitsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/time-slot/123")),
      )
    }
  }

  @Nested
  inner class CreateVisitSlot {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubCreateVisitSlot()

      apiService.createVisitSlot(syncCreateVisitSlotRequest())

      dpsOfficialVisitsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create endpoint`() = runTest {
      dpsOfficialVisitsServer.stubCreateVisitSlot()

      apiService.createVisitSlot(syncCreateVisitSlotRequest())

      dpsOfficialVisitsServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/visit-slot")),
      )
    }
  }

  @Nested
  inner class UpdateVisitSlot {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubUpdateVisitSlot(123)

      apiService.updateVisitSlot(prisonVisitSlotId = 123, syncUpdateVisitSlotRequest())

      dpsOfficialVisitsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the update endpoint`() = runTest {
      dpsOfficialVisitsServer.stubUpdateVisitSlot(123)

      apiService.updateVisitSlot(prisonVisitSlotId = 123, syncUpdateVisitSlotRequest())

      dpsOfficialVisitsServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/visit-slot/123")),
      )
    }
  }

  @Nested
  inner class DeleteVisitSlot {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubDeleteVisitSlot(123)

      apiService.deleteVisitSlot(prisonVisitSlotId = 123)

      dpsOfficialVisitsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete endpoint`() = runTest {
      dpsOfficialVisitsServer.stubDeleteVisitSlot(123)

      apiService.deleteVisitSlot(prisonVisitSlotId = 123)

      dpsOfficialVisitsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/visit-slot/123")),
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
  inner class CreateVisit {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubCreateVisit()

      apiService.createVisit(syncCreateOfficialVisitRequest())

      dpsOfficialVisitsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create endpoint`() = runTest {
      dpsOfficialVisitsServer.stubCreateVisit()

      apiService.createVisit(syncCreateOfficialVisitRequest())

      dpsOfficialVisitsServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/official-visit")),
      )
    }
  }

  @Nested
  inner class DeleteVisit {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubDeleteVisit(123)

      apiService.deleteVisit(123)

      dpsOfficialVisitsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete endpoint`() = runTest {
      dpsOfficialVisitsServer.stubDeleteVisit(123)

      apiService.deleteVisit(123)

      dpsOfficialVisitsServer.verify(
        deleteRequestedFor(urlPathEqualTo("/sync/official-visit/id/123")),
      )
    }
  }

  @Nested
  inner class CreateVisitor {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOfficialVisitsServer.stubCreateVisitor(123)

      apiService.createVisitor(officialVisitId = 123, syncCreateOfficialVisitorRequest())

      dpsOfficialVisitsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create endpoint`() = runTest {
      dpsOfficialVisitsServer.stubCreateVisitor(123)

      apiService.createVisitor(officialVisitId = 123, syncCreateOfficialVisitorRequest())

      dpsOfficialVisitsServer.verify(
        postRequestedFor(urlPathEqualTo("/sync/official-visit/123/visitor")),
      )
    }
  }
}
