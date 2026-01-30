package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension.Companion.sentencingApi
import java.util.UUID

class SentencingAdjustmentsDataRepairResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: SentencingAdjustmentsNomisApiMockServer

  @DisplayName("POST /prisoners/booking-id/{bookingId}/merge/sentencing-adjustments/repair")
  @Nested
  inner class RepairAdjustments {
    val bookingId = 12345L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/booking-id/$bookingId/merge/sentencing-adjustments/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/booking-id/$bookingId/merge/sentencing-adjustments/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/booking-id/$bookingId/merge/sentencing-adjustments/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetAllByBookingId(
          bookingId = bookingId,
          sentenceAdjustments = listOf(
            sentenceAdjustmentResponse(bookingId = bookingId, sentenceAdjustmentId = 1001),
            sentenceAdjustmentResponse(bookingId = bookingId, sentenceAdjustmentId = 1002),
          ),
          keyDateAdjustments = listOf(
            keyDateAdjustmentResponse(bookingId = bookingId, keyDateAdjustmentId = 2001),
            keyDateAdjustmentResponse(bookingId = bookingId, keyDateAdjustmentId = 2002),
          ),
        )

        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "SENTENCE", nomisAdjustmentId = 1001)
        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "SENTENCE", nomisAdjustmentId = 1002, status = HttpStatus.NOT_FOUND)
        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "KEY-DATE", nomisAdjustmentId = 2001)
        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "KEY-DATE", nomisAdjustmentId = 2002, status = HttpStatus.NOT_FOUND)

        nomisApi.stubGetSentenceAdjustment(1002)
        nomisApi.stubGetKeyDateAdjustment(2002)

        sentencingApi.stubCreateSentencingAdjustmentForSynchronisation()
        sentencingApi.stubCreateSentencingAdjustmentForSynchronisation()

        mappingApi.stubMappingCreate(MappingApiExtension.ADJUSTMENTS_CREATE_MAPPING_URL)
        mappingApi.stubMappingCreate(MappingApiExtension.ADJUSTMENTS_CREATE_MAPPING_URL)

        webTestClient.post().uri("/prisoners/booking-id/$bookingId/merge/sentencing-adjustments/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will log the number of adjustments found`() {
        verify(telemetryClient).trackEvent(
          Mockito.eq("from-nomis-synch-adjustment-merge"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
            assertThat(it["sentenceAdjustments"]).isEqualTo("2")
            assertThat(it["keyDateAdjustments"]).isEqualTo("2")
            assertThat(it["sentenceAdjustmentsCreated"]).isEqualTo("1")
            assertThat(it["keyDateAdjustmentsCreated"]).isEqualTo("1")
          },
          isNull(),
        )
      }

      @Test
      fun `will retrieve the mappings for each adjustment`() {
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/1001")),
        )
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/1001")),
        )
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/2001")),
        )
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/2002")),
        )
      }

      @Test
      fun `will retrieve details of the 2 missing adjustments`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/sentence-adjustments/1002")))
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/key-date-adjustments/2002")))
      }

      @Test
      fun `will create the two missing adjustments in DPS`() {
        sentencingApi.verify(2, postRequestedFor(urlPathEqualTo("/legacy/adjustments")))
      }

      @Test
      fun `will create the two missing adjustments mappings`() {
        mappingApi.verify(2, postRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments")))
      }
    }
  }

  @DisplayName("POST /prisoners/booking-id/{bookingId}/update/sentencing-adjustments/repair")
  @Nested
  inner class UpdateAdjustments {
    val bookingId = 12345L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/booking-id/$bookingId/update/sentencing-adjustments/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/booking-id/$bookingId/update/sentencing-adjustments/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/booking-id/$bookingId/update/sentencing-adjustments/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      val dpsSentenceAdjustmentId: UUID = UUID.randomUUID()
      val dpsKeyDateAdjustmentId: UUID = UUID.randomUUID()

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetAllByBookingId(
          bookingId = bookingId,
          sentenceAdjustments = listOf(
            sentenceAdjustmentResponse(bookingId = bookingId, sentenceAdjustmentId = 1001),
          ),
          keyDateAdjustments = listOf(
            keyDateAdjustmentResponse(bookingId = bookingId, keyDateAdjustmentId = 2001),
          ),
        )

        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "SENTENCE", nomisAdjustmentId = 1001, adjustmentId = dpsSentenceAdjustmentId.toString())
        mappingApi.stubGetNomisSentencingAdjustment(adjustmentCategory = "KEY-DATE", nomisAdjustmentId = 2001, adjustmentId = dpsKeyDateAdjustmentId.toString())

        nomisApi.stubGetSentenceAdjustment(1001)
        nomisApi.stubGetKeyDateAdjustment(2001)

        sentencingApi.stubUpdateSentencingAdjustmentForSynchronisation(adjustmentId = dpsSentenceAdjustmentId.toString())
        sentencingApi.stubUpdateSentencingAdjustmentForSynchronisation(adjustmentId = dpsKeyDateAdjustmentId.toString())

        webTestClient.post().uri("/prisoners/booking-id/$bookingId/update/sentencing-adjustments/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will log the repair`() {
        verify(telemetryClient).trackEvent(
          Mockito.eq("from-nomis-synch-adjustment-update-repair"),
          check {
            assertThat(it["bookingId"]).isEqualTo(bookingId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will retrieve the mappings for each adjustment`() {
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id/1001")),
        )
        mappingApi.verify(
          getRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id/2001")),
        )
      }

      @Test
      fun `will retrieve details of the adjustments`() {
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/sentence-adjustments/1001")))
        nomisApi.verify(getRequestedFor(urlPathEqualTo("/key-date-adjustments/2001")))
      }

      @Test
      fun `will update the two adjustments in DPS`() {
        sentencingApi.verify(putRequestedFor(urlPathEqualTo("/legacy/adjustments/$dpsSentenceAdjustmentId")))
        sentencingApi.verify(putRequestedFor(urlPathEqualTo("/legacy/adjustments/$dpsKeyDateAdjustmentId")))
      }
    }
  }
}
