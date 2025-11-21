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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitIdResponse

@SpringAPIServiceTest
@Import(OfficialVisitsNomisApiService::class, OfficialVisitsConfiguration::class, OfficialVisitsNomisApiMockServer::class)
class OfficialVisitsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: OfficialVisitsNomisApiService

  @Autowired
  private lateinit var mockServer: OfficialVisitsNomisApiMockServer

  @Nested
  inner class GetOfficialVisitIds {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetOfficialVisitIds(
        content = listOf(
          VisitIdResponse(
            visitId = 1234,
          ),
        ),
      )

      apiService.getOfficialVisitIds(pageNumber = 0, pageSize = 20)

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get IDs endpoint`() = runTest {
      mockServer.stubGetOfficialVisitIds(
        content = listOf(
          VisitIdResponse(
            visitId = 1234,
          ),
        ),
      )

      apiService.getOfficialVisitIds(pageNumber = 10, pageSize = 30)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/official-visits/ids"))
          .withQueryParam("page", equalTo("10"))
          .withQueryParam("size", equalTo("30")),
      )
    }

    @Test
    fun `will return page metadata in the response so it can be used by migration service`() = runTest {
      mockServer.stubGetOfficialVisitIds(
        content = (1..20).map {
          VisitIdResponse(
            visitId = it.toLong(),
          )
        },
        pageNumber = 10,
        pageSize = 20,
        totalElements = 1000,
      )

      val pageOfIds = apiService.getOfficialVisitIds(pageNumber = 10, pageSize = 20)

      assertThat(pageOfIds.content).hasSize(20)
      assertThat(pageOfIds.page?.totalPages).isEqualTo(50)
      assertThat(pageOfIds.page?.totalElements).isEqualTo(1000)
      assertThat(pageOfIds.page?.propertySize).isEqualTo(20)
    }
  }

  @Nested
  inner class GetOfficialVisit {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetOfficialVisit(
        visitId = 1234,
      )

      apiService.getOfficialVisit(
        visitId = 1234,
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get time slot endpoint`() = runTest {
      mockServer.stubGetOfficialVisit(
        visitId = 1234,
      )

      apiService.getOfficialVisit(
        visitId = 1234,
      )
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/official-visits/1234")),
      )
    }
  }
}
