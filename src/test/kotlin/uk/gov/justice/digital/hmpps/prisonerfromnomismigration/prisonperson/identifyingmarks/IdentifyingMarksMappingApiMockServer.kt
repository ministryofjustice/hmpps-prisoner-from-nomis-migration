package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.identifyingmarks

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IdentifyingMarkMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IdentifyingMarkMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.time.LocalDateTime
import java.util.UUID

@Component
class IdentifyingMarksMappingApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetMappingByNomisId(
    bookingId: Long,
    idMarksSeq: Long,
    response: IdentifyingMarkMappingDto = IdentifyingMarkMappingDto(
      nomisBookingId = bookingId,
      nomisMarksSequence = idMarksSeq,
      dpsId = UUID.randomUUID(),
      offenderNo = "A1234AA",
      mappingType = NOMIS_CREATED,
      whenCreated = "${LocalDateTime.now()}",
      label = "some_label",
    ),
  ) {
    mappingApi.stubFor(
      get("/mapping/prisonperson/nomis-booking-id/$bookingId/identifying-mark-sequence/$idMarksSeq").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetMappingByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/prisonperson/nomis-booking-id/.*/identifying-mark-sequence/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetMappingByDpsId(
    dpsId: UUID,
    response: IdentifyingMarkMappingDto = IdentifyingMarkMappingDto(
      nomisBookingId = 12345L,
      nomisMarksSequence = 1L,
      dpsId = dpsId,
      offenderNo = "A1234AA",
      mappingType = NOMIS_CREATED,
      whenCreated = "${LocalDateTime.now()}",
      label = "some_label",
    ),
  ) {
    mappingApi.stubFor(
      get("/mapping/prisonperson/dps-identifying-mark-id/$dpsId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetMappingByDpsId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/prisonperson/dps-identifying-mark-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateMapping() {
    mappingApi.stubFor(
      post("/mapping/prisonperson/identifying-mark").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value()),
      ),
    )
  }

  fun stubCreateMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/prisonperson/identifying-mark").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateMapping(status: HttpStatus, error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/prisonperson/identifying-mark").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
