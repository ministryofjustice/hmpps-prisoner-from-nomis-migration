package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AdjustmentIdResponse
import java.time.LocalDate

@SpringAPIServiceTest
@Import(SentencingAdjustmentsNomisApiService::class, SentencingConfiguration::class, SentencingAdjustmentsNomisApiMockServer::class)
class SentencingAdjustmentsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: SentencingAdjustmentsNomisApiService

  @Autowired
  private lateinit var sentencingAdjustmentsNomisApi: SentencingAdjustmentsNomisApiMockServer

  @Nested
  inner class GetAllByBookingId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      sentencingAdjustmentsNomisApi.stubGetAllByBookingId(1234567)

      apiService.getAllByBookingId(bookingId = 1234567)

      sentencingAdjustmentsNomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass booking id to service`() = runTest {
      sentencingAdjustmentsNomisApi.stubGetAllByBookingId(1234567)

      apiService.getAllByBookingId(bookingId = 1234567)

      sentencingAdjustmentsNomisApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/booking-id/1234567/sentencing-adjustments")),
      )
    }

    @Test
    internal fun `will request all adjustments, not just active ones`() = runTest {
      sentencingAdjustmentsNomisApi.stubGetAllByBookingId(1234567)

      apiService.getAllByBookingId(bookingId = 1234567)

      sentencingAdjustmentsNomisApi.verify(
        getRequestedFor(anyUrl()).withQueryParam("active-only", equalTo("false")),
      )
    }
  }

  @Nested
  inner class GetSentenceAdjustment {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      sentencingAdjustmentsNomisApi.stubGetSentenceAdjustment(1234567)

      apiService.getSentenceAdjustment(nomisSentenceAdjustmentId = 1234567)

      sentencingAdjustmentsNomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass adjustment id to service`() = runTest {
      sentencingAdjustmentsNomisApi.stubGetSentenceAdjustment(1234567)

      apiService.getSentenceAdjustment(nomisSentenceAdjustmentId = 1234567)

      sentencingAdjustmentsNomisApi.verify(
        getRequestedFor(urlPathEqualTo("/sentence-adjustments/1234567")),
      )
    }
  }

  @Nested
  inner class GetKeyDateAdjustment {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      sentencingAdjustmentsNomisApi.stubGetKeyDateAdjustment(1234567)

      apiService.getKeyDateAdjustment(nomisKeyDateAdjustmentId = 1234567)

      sentencingAdjustmentsNomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass adjustment id to service`() = runTest {
      sentencingAdjustmentsNomisApi.stubGetKeyDateAdjustment(1234567)

      apiService.getKeyDateAdjustment(nomisKeyDateAdjustmentId = 1234567)

      sentencingAdjustmentsNomisApi.verify(
        getRequestedFor(urlPathEqualTo("/key-date-adjustments/1234567")),
      )
    }
  }

  @Nested
  inner class GetSentencingAdjustmentIds {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      sentencingAdjustmentsNomisApi.stubGetSentencingAdjustmentIds()

      apiService.getSentencingAdjustmentIds()

      sentencingAdjustmentsNomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass mandatory parameters to service`() = runTest {
      sentencingAdjustmentsNomisApi.stubGetSentencingAdjustmentIds()

      apiService.getSentencingAdjustmentIds(pageNumber = 12, pageSize = 20)

      sentencingAdjustmentsNomisApi.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("fromDate", equalTo(""))
          .withQueryParam("toDate", equalTo(""))
          .withQueryParam("page", equalTo("12"))
          .withQueryParam("size", equalTo("20")),
      )
    }

    @Test
    fun `can pass optional parameters to service`() = runTest {
      sentencingAdjustmentsNomisApi.stubGetSentencingAdjustmentIds()

      apiService.getSentencingAdjustmentIds(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
        pageNumber = 12,
        pageSize = 20,
      )

      sentencingAdjustmentsNomisApi.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("fromDate", equalTo("2020-01-01"))
          .withQueryParam("toDate", equalTo("2020-01-02")),
      )
    }

    @Test
    fun `will return adjustment ids`() = runTest {
      sentencingAdjustmentsNomisApi.stubGetSentencingAdjustmentIds(content = listOf(AdjustmentIdResponse(1234567, "SENTENCE"), AdjustmentIdResponse(6543, "KEY_DATE")))

      val pages = apiService.getSentencingAdjustmentIds(pageNumber = 12, pageSize = 20)

      assertThat(pages.content).hasSize(2)
      assertThat(pages.content[0].adjustmentId).isEqualTo(1234567)
      assertThat(pages.content[0].adjustmentCategory).isEqualTo("SENTENCE")
      assertThat(pages.content[1].adjustmentId).isEqualTo(6543)
      assertThat(pages.content[1].adjustmentCategory).isEqualTo("KEY_DATE")
    }
  }
}
