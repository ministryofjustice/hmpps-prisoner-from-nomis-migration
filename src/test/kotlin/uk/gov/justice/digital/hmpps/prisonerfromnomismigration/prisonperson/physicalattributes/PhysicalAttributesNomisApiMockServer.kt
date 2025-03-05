package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class PhysicalAttributesNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetPhysicalAttributes(
    offenderNo: String = "A1234AA",
    response: PrisonerPhysicalAttributesResponse = PrisonerPhysicalAttributesResponse(
      offenderNo = offenderNo,
      bookings = listOf(
        BookingPhysicalAttributesResponse(
          bookingId = 1,
          startDateTime = LocalDateTime.parse("2024-02-03T12:34:56"),
          endDateTime = LocalDateTime.parse("2024-10-21T12:34:56"),
          latestBooking = true,
          physicalAttributes = listOf(
            PhysicalAttributesResponse(
              attributeSequence = 1,
              heightCentimetres = 180,
              weightKilograms = 80,
              createdBy = "A_USER",
              createDateTime = LocalDateTime.now(),
              modifiedBy = "ANOTHER_USER",
              modifiedDateTime = LocalDateTime.now(),
              auditModuleName = "MODULE",
            ),
          ),
        ),
      ),
    ),
  ) = nomisApi.stubFor(
    get(urlEqualTo("/prisoners/$offenderNo/physical-attributes")).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withStatus(HttpStatus.OK.value())
        .withBody(objectMapper.writeValueAsString(response)),
    ),
  )

  fun stubGetPhysicalAttributes(
    offenderNo: String = "A1234AA",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/$offenderNo/physical-attributes")).willReturn(
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
