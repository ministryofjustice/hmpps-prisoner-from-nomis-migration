package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.identifyingmarks

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingIdentifyingMarksResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IdentifyingMarkImageDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IdentifyingMarksResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class IdentifyingMarksNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetIdentifyingMarks(
    bookingId: Long = 12345L,
    response: BookingIdentifyingMarksResponse = BookingIdentifyingMarksResponse(
      bookingId = bookingId,
      startDateTime = LocalDateTime.parse("2024-02-03T12:34:56"),
      endDateTime = LocalDateTime.parse("2024-10-21T12:34:56"),
      latestBooking = true,
      identifyingMarks = listOf(
        IdentifyingMarksResponse(
          idMarksSeq = 1,
          bodyPartCode = "ARM",
          markTypeCode = "TAT",
          sideCode = "L",
          partOrientationCode = "FRONT",
          commentText = "Dragon",
          imageIds = listOf(2345L, 3456L),
          createdBy = "A_USER",
          createDateTime = LocalDateTime.now(),
          modifiedBy = "ANOTHER_USER",
          modifiedDateTime = LocalDateTime.now(),
          auditModuleName = "MODULE",
        ),
      ),
    ),
  ) = nomisApi.stubFor(
    get(urlEqualTo("/bookings/$bookingId/identifying-marks")).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withStatus(HttpStatus.OK.value())
        .withBody(objectMapper.writeValueAsString(response)),
    ),
  )

  fun stubGetIdentifyingMarks(
    bookingId: Long = 12345L,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/bookings/$bookingId/identifying-marks")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetIdentifyingMarksImageDetails(
    offenderImageId: Long = 12345L,
    response: IdentifyingMarkImageDetailsResponse = IdentifyingMarkImageDetailsResponse(
      imageId = offenderImageId,
      bookingId = 23456L,
      idMarksSeq = 1L,
      captureDateTime = LocalDateTime.parse("2024-02-03T12:34:56"),
      bodyPartCode = "ARM",
      markTypeCode = "TAT",
      default = true,
      imageExists = true,
      imageSourceCode = "FILE",
      createdBy = "A_USER",
      createDateTime = LocalDateTime.now(),
      modifiedBy = "ANOTHER_USER",
      modifiedDateTime = LocalDateTime.now(),
      auditModuleName = "MODULE",
    ),
  ) = nomisApi.stubFor(
    get(urlEqualTo("/identifying-marks/images/$offenderImageId/details")).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withStatus(HttpStatus.OK.value())
        .withBody(objectMapper.writeValueAsString(response)),
    ),
  )

  fun stubGetIdentifyingMarksImageDetails(
    offenderImageId: Long,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/identifying-marks/images/$offenderImageId/details")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetIdentifyingMarksImageData(
    offenderImageId: Long = 12345L,
    response: ByteArray = byteArrayOf(1, 2, 3),
  ) = nomisApi.stubFor(
    get(urlEqualTo("/identifying-marks/images/$offenderImageId/data")).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withStatus(HttpStatus.OK.value())
        .withBody(response),
    ),
  )

  fun stubGetIdentifyingMarksImageData(
    offenderImageId: Long,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/identifying-marks/images/$offenderImageId/data")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
