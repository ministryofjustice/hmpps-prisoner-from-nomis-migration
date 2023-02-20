package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension.Companion.sentencingApi
import java.time.LocalDate

private const val ADJUSTMENT_ID = "05b332ad-58eb-4ec2-963c-c9c927856788"

@SpringAPIServiceTest
@Import(SentencingService::class, SentencingConfiguration::class)
internal class SentencingServiceTest {

  @Autowired
  private lateinit var sentencingService: SentencingService

  @Nested
  @DisplayName("POST /migration/sentencing/adjustments")
  inner class CreateAdjustmentForMigration {
    @BeforeEach
    internal fun setUp() {
      sentencingApi.stubCreateSentencingAdjustmentForMigration(sentenceAdjustmentId = ADJUSTMENT_ID)
      runBlocking {
        sentencingService.migrateSentencingAdjustment(
          SentencingAdjustment(
            bookingId = 1234,
            sentenceSequence = 2,
            adjustmentType = "RX",
            adjustmentDate = LocalDate.parse("2022-01-01"),
            adjustmentFromDate = LocalDate.parse("2021-07-01"),
            adjustmentDays = 99,
            comment = "Remand added",
            active = true,
          )
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      sentencingApi.verify(
        postRequestedFor(urlEqualTo("/migration/sentencing/adjustments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will pass data to the api`() {
      sentencingApi.verify(
        postRequestedFor(urlEqualTo("/migration/sentencing/adjustments"))
          .withRequestBody(WireMock.matchingJsonPath("bookingId", equalTo("1234")))
          .withRequestBody(WireMock.matchingJsonPath("sentenceSequence", equalTo("2")))
          .withRequestBody(WireMock.matchingJsonPath("adjustmentType", equalTo("RX")))
          .withRequestBody(WireMock.matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(WireMock.matchingJsonPath("adjustmentDays", equalTo("99")))
          .withRequestBody(WireMock.matchingJsonPath("adjustmentFromDate", equalTo("2021-07-01")))
          .withRequestBody(WireMock.matchingJsonPath("comment", equalTo("Remand added")))
          .withRequestBody(WireMock.matchingJsonPath("active", equalTo("true")))
      )
    }
  }

  @Nested
  @DisplayName("POST /synchronisation/sentencing/adjustments")
  inner class CreateAdjustmentForSynchronisation {
    @BeforeEach
    internal fun setUp() {
      sentencingApi.stubCreateSentencingAdjustmentForSynchronisation(sentenceAdjustmentId = ADJUSTMENT_ID)
      runBlocking {
        sentencingService.createSentencingAdjustment(
          SentencingAdjustment(
            bookingId = 1234,
            sentenceSequence = 2,
            adjustmentType = "RX",
            adjustmentDate = LocalDate.parse("2022-01-01"),
            adjustmentFromDate = LocalDate.parse("2021-07-01"),
            adjustmentDays = 99,
            comment = "Remand added",
            active = true,
          )
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      sentencingApi.verify(
        postRequestedFor(urlEqualTo("/synchronisation/sentencing/adjustments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will pass data to the api`() {
      sentencingApi.verify(
        postRequestedFor(urlEqualTo("/synchronisation/sentencing/adjustments"))
          .withRequestBody(WireMock.matchingJsonPath("bookingId", equalTo("1234")))
          .withRequestBody(WireMock.matchingJsonPath("sentenceSequence", equalTo("2")))
          .withRequestBody(WireMock.matchingJsonPath("adjustmentType", equalTo("RX")))
          .withRequestBody(WireMock.matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(WireMock.matchingJsonPath("adjustmentDays", equalTo("99")))
          .withRequestBody(WireMock.matchingJsonPath("adjustmentFromDate", equalTo("2021-07-01")))
          .withRequestBody(WireMock.matchingJsonPath("comment", equalTo("Remand added")))
          .withRequestBody(WireMock.matchingJsonPath("active", equalTo("true")))
      )
    }
  }

  @Nested
  @DisplayName("PUT /synchronisation/sentencing/adjustments")
  inner class UpdateAdjustmentForSynchronisation {
    @BeforeEach
    internal fun setUp() {
      sentencingApi.stubUpdateSentencingAdjustmentForSynchronisation(adjustmentId = ADJUSTMENT_ID)
      runBlocking {
        sentencingService.updateSentencingAdjustment(
          ADJUSTMENT_ID,
          SentencingAdjustment(
            bookingId = 1234,
            sentenceSequence = 2,
            adjustmentType = "RX",
            adjustmentDate = LocalDate.parse("2022-01-01"),
            adjustmentFromDate = LocalDate.parse("2021-07-01"),
            adjustmentDays = 99,
            comment = "Remand added",
            active = true,
          )
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      sentencingApi.verify(
        putRequestedFor(urlEqualTo("/synchronisation/sentencing/adjustments/$ADJUSTMENT_ID"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    fun `will pass data to the api`() {
      sentencingApi.verify(
        putRequestedFor(urlEqualTo("/synchronisation/sentencing/adjustments/$ADJUSTMENT_ID"))
          .withRequestBody(WireMock.matchingJsonPath("bookingId", equalTo("1234")))
          .withRequestBody(WireMock.matchingJsonPath("sentenceSequence", equalTo("2")))
          .withRequestBody(WireMock.matchingJsonPath("adjustmentType", equalTo("RX")))
          .withRequestBody(WireMock.matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
          .withRequestBody(WireMock.matchingJsonPath("adjustmentDays", equalTo("99")))
          .withRequestBody(WireMock.matchingJsonPath("adjustmentFromDate", equalTo("2021-07-01")))
          .withRequestBody(WireMock.matchingJsonPath("comment", equalTo("Remand added")))
          .withRequestBody(WireMock.matchingJsonPath("active", equalTo("true")))
      )
    }
  }

  @Nested
  @DisplayName("DELETE /synchronisation/sentencing/adjustments")
  inner class DeleteAdjustmentForSynchronisation {
    @BeforeEach
    internal fun setUp() {
      sentencingApi.stubDeleteSentencingAdjustmentForSynchronisation(adjustmentId = ADJUSTMENT_ID)
      runBlocking {
        sentencingService.deleteSentencingAdjustment(
          ADJUSTMENT_ID,
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      sentencingApi.verify(
        deleteRequestedFor(urlEqualTo("/synchronisation/sentencing/adjustments/$ADJUSTMENT_ID"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }
  }
}
