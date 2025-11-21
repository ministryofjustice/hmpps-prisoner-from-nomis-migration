package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitsConfigurationResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitTimeSlotIdResponse

@SpringAPIServiceTest
@Import(VisitSlotsNomisApiService::class, OfficialVisitsConfiguration::class, VisitSlotsNomisApiMockServer::class)
class VisitSlotsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitSlotsNomisApiService

  @Autowired
  private lateinit var mockServer: VisitSlotsNomisApiMockServer

  @Nested
  inner class GetVisitTimeSlotIds {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetVisitTimeSlotIds(
        content = listOf(
          VisitTimeSlotIdResponse(
            prisonId = "LEI",
            dayOfWeek = VisitTimeSlotIdResponse.DayOfWeek.MON,
            timeSlotSequence = 1,
          ),
        ),
      )

      apiService.getVisitTimeSlotIds(pageNumber = 0, pageSize = 20)

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get IDs endpoint`() = runTest {
      mockServer.stubGetVisitTimeSlotIds(
        content = listOf(
          VisitTimeSlotIdResponse(
            prisonId = "LEI",
            dayOfWeek = VisitTimeSlotIdResponse.DayOfWeek.MON,
            timeSlotSequence = 1,
          ),
        ),
      )

      apiService.getVisitTimeSlotIds(pageNumber = 10, pageSize = 30)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/visits/configuration/time-slots/ids"))
          .withQueryParam("page", equalTo("10"))
          .withQueryParam("size", equalTo("30")),
      )
    }

    @Test
    fun `will return page metadata in the response so it can be used by migration service`() = runTest {
      mockServer.stubGetVisitTimeSlotIds(
        content = (1..20).map {
          VisitTimeSlotIdResponse(
            prisonId = "LEI",
            dayOfWeek = VisitTimeSlotIdResponse.DayOfWeek.MON,
            timeSlotSequence = it,
          )
        },
        pageNumber = 10,
        pageSize = 20,
        totalElements = 1000,
      )

      val pageOfIds = apiService.getVisitTimeSlotIds(pageNumber = 10, pageSize = 20)

      assertThat(pageOfIds.content).hasSize(20)
      assertThat(pageOfIds.page?.totalPages).isEqualTo(50)
      assertThat(pageOfIds.page?.totalElements).isEqualTo(1000)
      assertThat(pageOfIds.page?.propertySize).isEqualTo(20)
    }
  }

  @Nested
  inner class GetVisitTimeSlot {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
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
