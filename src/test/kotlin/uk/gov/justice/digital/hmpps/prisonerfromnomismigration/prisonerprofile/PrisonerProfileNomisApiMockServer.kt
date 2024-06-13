package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonerprofile

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class PrisonerProfileNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetPhysicalAttributes(
    offenderNo: String = "A1234AA",
    response: PrisonerPhysicalAttributesResponse = PrisonerPhysicalAttributesResponse(
      offenderNo = offenderNo,
      bookings = listOf(
        BookingPhysicalAttributesResponse(
          bookingId = 1,
          startDate = LocalDate.parse("2024-02-03"),
          endDate = LocalDate.parse("2024-10-21"),
          physicalAttributes = listOf(
            PhysicalAttributesResponse(
              heightCentimetres = 180,
              weightKilograms = 80,
              createDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
              modifiedDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
              auditModuleName = "MODULE",
            ),
          ),
        ),
      ),
    ),
  ) =
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/physical-attributes")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )

  fun stubGetPhysicalAttributes(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/\\w+/physical-attributes")).willReturn(
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
