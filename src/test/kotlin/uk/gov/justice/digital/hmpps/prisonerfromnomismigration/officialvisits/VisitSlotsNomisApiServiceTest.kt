package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(VisitSlotsNomisApiService::class, OfficialVisitsConfiguration::class, VisitSlotsNomisApiMockServer::class)
class VisitSlotsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitSlotsNomisApiService

  @Autowired
  private lateinit var mockServer: VisitSlotsNomisApiMockServer

  @Nested
  inner class GetVisitTimeSlot {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubGetVisitTimeSlot(
        prisonId = "LEI",
        dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON,
        timeSlotSequence = 1,
      )

      apiService.getVisitTimeSlot(
        prisonId = "LEI",
        dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON,
        timeSlotSequence = 1,
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get time slot endpoint`() = runTest {
      mockServer.stubGetVisitTimeSlot(
        prisonId = "LEI",
        dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON,
        timeSlotSequence = 1,
      )

      apiService.getVisitTimeSlot(
        prisonId = "LEI",
        dayOfWeek = VisitsConfigurationResourceApi.DayOfWeekGetVisitTimeSlot.MON,
        timeSlotSequence = 1,
      )
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/visits/configuration/time-slots/prison-id/LEI/day-of-week/MON/time-slot-sequence/1")),
      )
    }
  }
}
