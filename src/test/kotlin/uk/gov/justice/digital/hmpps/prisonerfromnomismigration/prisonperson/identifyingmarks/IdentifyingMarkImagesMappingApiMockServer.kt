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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IdentifyingMarkImageMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IdentifyingMarkImageMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.time.LocalDateTime
import java.util.UUID

@Component
class IdentifyingMarkImagesMappingApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetMappingByNomisId(
    offenderImageId: Long,
    response: IdentifyingMarkImageMappingDto = IdentifyingMarkImageMappingDto(
      nomisOffenderImageId = offenderImageId,
      nomisBookingId = 12345L,
      nomisMarksSequence = 1L,
      dpsId = UUID.randomUUID(),
      offenderNo = "A1234AA",
      mappingType = NOMIS_CREATED,
      whenCreated = "${LocalDateTime.now()}",
      label = "some_label",
    ),
  ) {
    mappingApi.stubFor(
      get("/mapping/prisonperson/nomis-offender-image-id/$offenderImageId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetMappingByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/prisonperson/nomis-offender-image-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetMappingByDpsId(
    dpsId: UUID,
    response: IdentifyingMarkImageMappingDto = IdentifyingMarkImageMappingDto(
      nomisOffenderImageId = 23456L,
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
      get("/mapping/prisonperson/dps-image-id/$dpsId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetMappingByDpsId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/prisonperson/dps-image-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateMapping() {
    mappingApi.stubFor(
      post("/mapping/prisonperson/identifying-mark-image").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value()),
      ),
    )
  }

  fun stubCreateMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/prisonperson/identifying-mark-image").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateMapping(status: HttpStatus, error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/prisonperson/identifying-mark-image").willReturn(
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
