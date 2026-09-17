package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IdRange
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension

private const val RTP_ID = 12345L

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(DrugTestingNomisApiService::class, DrugTestingNomisApiMockServer::class)
class DrugTestingNomisApiServiceTest(
  @Autowired private val apiService: DrugTestingNomisApiService,
  @Autowired private val mockServer: DrugTestingNomisApiMockServer,
) {
  private val filter = DrugTestingMigrationFilter(
    includedPrisonIds = setOf("MDI"),
    excludedPrisonIds = setOf("LEI"),
  )

  @Nested
  inner class GetRandomTestingProgram {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetRandomTestingProgram(rtpId = RTP_ID)

      apiService.getRandomTestingProgram(RTP_ID)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetRandomTestingProgram(rtpId = RTP_ID)

      apiService.getRandomTestingProgram(RTP_ID)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/drug-testing/$RTP_ID")),
      )
    }

    @Test
    fun `will return random testing program`() = runTest {
      mockServer.stubGetRandomTestingProgram(
        rtpId = RTP_ID,
        response = randomTestingProgramResponse(rtpId = RTP_ID).copy(caseloadId = "BXI"),
      )

      val response = apiService.getRandomTestingProgram(RTP_ID)

      assertThat(response.rtpId).isEqualTo(RTP_ID)
      assertThat(response.caseloadId).isEqualTo("BXI")
      assertThat(response.offenderTestSelection[0].prisonNumber).isEqualTo("A1234BC")
    }

    @Test
    fun `will throw error when program does not exist`() = runTest {
      mockServer.stubGetRandomTestingProgram(rtpId = RTP_ID, status = HttpStatus.NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getRandomTestingProgram(RTP_ID)
      }
    }
  }

  @Nested
  inner class GetDrugTestingIdRanges {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetDrugTestingIdRanges()

      apiService.getDrugTestingIdRanges(pageSize = 100, filter = filter)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass filters and page size to service`() = runTest {
      mockServer.stubGetDrugTestingIdRanges()

      apiService.getDrugTestingIdRanges(pageSize = 100, filter = filter)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/drug-testing/id-ranges"))
          .withQueryParam("pageSize", equalTo("100"))
          .withQueryParam("includedPrisonIds", equalTo("MDI"))
          .withQueryParam("excludedPrisonIds", equalTo("LEI")),
      )
    }

    @Test
    fun `will return id ranges`() = runTest {
      mockServer.stubGetDrugTestingIdRanges(pageSize = 10, totalElements = 20)

      val response = apiService.getDrugTestingIdRanges(pageSize = 10, filter = filter)

      assertThat(response).containsExactly(IdRange(0, 10), IdRange(10, 20))
    }
  }

  @Nested
  inner class GetDrugTestingIdsInRange {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetDrugTestingIdsInRange()

      apiService.getDrugTestingIdsInRange(idRange = IdRange(1, 10), filter = filter)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass range and filters to service`() = runTest {
      mockServer.stubGetDrugTestingIdsInRange()

      apiService.getDrugTestingIdsInRange(idRange = IdRange(1, 10), filter = filter)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/drug-testing/ids-in-range"))
          .withQueryParam("fromId", equalTo("1"))
          .withQueryParam("toId", equalTo("10"))
          .withQueryParam("includedPrisonIds", equalTo("MDI"))
          .withQueryParam("excludedPrisonIds", equalTo("LEI")),
      )
    }

    @Test
    fun `will return ids in range`() = runTest {
      mockServer.stubGetDrugTestingIdsInRange(fromId = 1, toId = 5)

      val response = apiService.getDrugTestingIdsInRange(idRange = IdRange(1, 5), filter = filter)

      assertThat(response).containsExactly(1, 2, 3, 4)
    }
  }
}
