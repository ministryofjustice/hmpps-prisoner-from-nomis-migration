package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.util.*

@Component
class TransferScheduleMappingApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = mappingApi.getRequestBody(pattern, jsonMapper = jsonMapper)
  }

  fun stubCreateTransferScheduleMapping() {
    mappingApi.stubFor(
      post("/mapping/transfer-scheduler/schedule")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateTransferScheduleMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/transfer-scheduler/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTransferScheduleMappingConflict(error: Any) {
    mappingApi.stubFor(
      post("/mapping/transfer-scheduler/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTransferScheduleMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/transfer-scheduler/schedule")

  fun stubGetTransferScheduleMapping(
    nomisEventId: Long = 1L,
    dpsTransferScheduleId: UUID = UUID.randomUUID(),
    prisonerNumber: String = "A1234BC",
  ) {
    mappingApi.stubFor(
      get("/mapping/transfer-scheduler/schedule/nomis-id/$nomisEventId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(
              transferScheduleMapping(
                nomisEventId = nomisEventId,
                dpsTransferScheduleId = dpsTransferScheduleId,
                prisonerNumber = prisonerNumber,
              ),
            ),
          ),
      ),
    )
  }

  fun stubGetTransferScheduleMapping(nomisEventId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get("/mapping/transfer-scheduler/schedule/nomis-id/$nomisEventId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTransferMovementMapping() {
    mappingApi.stubFor(
      post("/mapping/transfer-scheduler/movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateTransferMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/transfer-scheduler/movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTransferMovementMappingConflict(error: Any) {
    mappingApi.stubFor(
      post("/mapping/transfer-scheduler/movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteTransferScheduleMapping(nomisEventId: Long = 1L) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/transfer-scheduler/schedule/nomis-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteTransferScheduleMapping(nomisEventId: Long = 1L, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/transfer-scheduler/schedule/nomis-id/$nomisEventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetTransferMovementMapping(
    nomisBookingId: Long = 12345L,
    nomisMovementSeq: Int = 3,
    dpsTransferMovementId: UUID = UUID.randomUUID(),
    prisonerNumber: String = "A1234BC",
  ) {
    mappingApi.stubFor(
      get("/mapping/transfer-scheduler/movement/nomis-id/$nomisBookingId/$nomisMovementSeq").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(
              transferMovementMapping(
                nomisBookingId = nomisBookingId,
                nomisMovementSeq = nomisMovementSeq,
                dpsTransferMovementId = dpsTransferMovementId,
                prisonerNumber = prisonerNumber,
              ),
            ),
          ),
      ),
    )
  }

  fun stubGetTransferMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/transfer-scheduler/movement/nomis-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}

fun transferScheduleMapping(
  nomisEventId: Long = 1L,
  prisonerNumber: String = "A1234BC",
  dpsTransferScheduleId: UUID = UUID.randomUUID(),
) = TransferScheduleMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisEventId = nomisEventId,
  dpsTransferScheduleId = dpsTransferScheduleId,
  mappingType = TransferScheduleMappingDto.MappingType.NOMIS_CREATED,
)

fun transferMovementMapping(
  nomisBookingId: Long = 12345L,
  nomisMovementSeq: Int = 3,
  prisonerNumber: String = "A1234BC",
  dpsTransferMovementId: UUID = UUID.randomUUID(),
) = TransferMovementMappingDto(
  prisonerNumber = prisonerNumber,
  nomisBookingId = nomisBookingId,
  nomisMovementSeq = nomisMovementSeq,
  dpsTransferMovementId = dpsTransferMovementId,
  mappingType = TransferMovementMappingDto.MappingType.NOMIS_CREATED,
)
