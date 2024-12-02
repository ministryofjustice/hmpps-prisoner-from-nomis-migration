package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.identifyingmarks

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import java.time.LocalDate

@SpringAPIServiceTest
@Import(IdentifyingMarksNomisApiService::class, IdentifyingMarksNomisApiMockServer::class)
class IdentifyingMarksNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: IdentifyingMarksNomisApiService

  @Autowired
  private lateinit var nomisApi: IdentifyingMarksNomisApiMockServer

  @Nested
  inner class GetIdentifyingMarks {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubGetIdentifyingMarks(bookingId = 12345L)

      apiService.getIdentifyingMarks(bookingId = 12345L)

      nomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      nomisApi.stubGetIdentifyingMarks(bookingId = 12345L)

      apiService.getIdentifyingMarks(bookingId = 12345L)

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/bookings/12345/identifying-marks")),
      )
    }

    @Test
    fun `will return identifying marks`() = runTest {
      nomisApi.stubGetIdentifyingMarks(bookingId = 12345L)

      val identifyingMarks = apiService.getIdentifyingMarks(12345L)

      with(identifyingMarks) {
        assertThat(bookingId).isEqualTo(12345L)
        assertThat(startDateTime).isEqualTo("2024-02-03T12:34:56")
        assertThat(endDateTime).isEqualTo("2024-10-21T12:34:56")
        assertThat(latestBooking).isTrue()
        assertThat(this.identifyingMarks)
          .extracting("idMarksSeq", "bodyPartCode", "markTypeCode", "sideCode", "partOrientationCode", "commentText", "createdBy", "auditModuleName")
          .containsExactly(tuple(1L, "ARM", "TAT", "L", "FRONT", "Dragon", "A_USER", "MODULE"))
      }
    }

    @Test
    fun `will throw error when bookings do not exist`() = runTest {
      nomisApi.stubGetIdentifyingMarks(status = NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getIdentifyingMarks(12345L)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      nomisApi.stubGetIdentifyingMarks(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getIdentifyingMarks(12345L)
      }
    }
  }

  @Nested
  inner class GetIdentifyingMarksImageDetails {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubGetIdentifyingMarksImageDetails(offenderImageId = 12345L)

      apiService.getIdentifyingMarksImageDetails(offenderImageId = 12345L)

      nomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      nomisApi.stubGetIdentifyingMarksImageDetails(offenderImageId = 12345L)

      apiService.getIdentifyingMarksImageDetails(offenderImageId = 12345L)

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/identifying-marks/images/12345/details")),
      )
    }

    @Test
    fun `will return identifying marks`() = runTest {
      nomisApi.stubGetIdentifyingMarksImageDetails(offenderImageId = 12345L)

      val imageDetails = apiService.getIdentifyingMarksImageDetails(12345L)

      with(imageDetails) {
        assertThat(bookingId).isEqualTo(23456)
        assertThat(idMarksSeq).isEqualTo(1L)
        assertThat(captureDateTime).isEqualTo("2024-02-03T12:34:56")
        assertThat(bodyPartCode).isEqualTo("ARM")
        assertThat(markTypeCode).isEqualTo("TAT")
        assertThat(default).isTrue()
        assertThat(imageExists).isTrue()
        assertThat(imageSourceCode).isEqualTo("FILE")
        assertThat(createDateTime).startsWith("${LocalDate.now()}")
        assertThat(createdBy).isEqualTo("A_USER")
        assertThat(auditModuleName).isEqualTo("MODULE")
      }
    }

    @Test
    fun `will throw error when images does not exist`() = runTest {
      nomisApi.stubGetIdentifyingMarksImageDetails(offenderImageId = 12345L, status = NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getIdentifyingMarksImageDetails(12345L)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      nomisApi.stubGetIdentifyingMarksImageDetails(offenderImageId = 12345L, status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getIdentifyingMarksImageDetails(12345L)
      }
    }
  }

  @Nested
  inner class GetIdentifyingMarksImageData {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubGetIdentifyingMarksImageData(offenderImageId = 12345L)

      apiService.getIdentifyingMarksImageData(offenderImageId = 12345L)

      nomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      nomisApi.stubGetIdentifyingMarksImageData(offenderImageId = 12345L)

      apiService.getIdentifyingMarksImageData(offenderImageId = 12345L)

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/identifying-marks/images/12345/data")),
      )
    }

    @Test
    fun `will return identifying marks image data`() = runTest {
      nomisApi.stubGetIdentifyingMarksImageData(offenderImageId = 12345L)

      val imageDetails = apiService.getIdentifyingMarksImageData(12345L)

      assertThat(imageDetails).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `will throw error when image data does not exist`() = runTest {
      nomisApi.stubGetIdentifyingMarksImageData(offenderImageId = 12345L, status = NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getIdentifyingMarksImageData(12345L)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      nomisApi.stubGetIdentifyingMarksImageData(offenderImageId = 12345L, status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getIdentifyingMarksImageData(12345L)
      }
    }
  }
}
