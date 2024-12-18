package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.util.UUID

@Component
class CourtSentencingMappingApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetByNomisId(
    nomisCourtCaseId: Long = 123456,
    dpsCourtCaseId: String = UUID.randomUUID().toString(),
    mapping: CourtCaseMappingDto = CourtCaseMappingDto(
      nomisCourtCaseId = nomisCourtCaseId,
      dpsCourtCaseId = dpsCourtCaseId,
      mappingType = CourtCaseMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/court-sentencing/court-cases/nomis-court-case-id/$nomisCourtCaseId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-sentencing/court-cases/nomis-court-case-id/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMapping() {
    mappingApi.stubFor(
      post("/mapping/court-sentencing/court-cases").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/court-sentencing/court-cases").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/court-sentencing/court-cases")
  }

  fun stubCourtCaseMappingCreateConflict(
    existingDpsCourtCaseId: String = "10",
    duplicateDpsCourtCaseId: String = "11",
    nomisCourtCaseId: Long = 123,
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/court-sentencing/court-cases"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "dpsCourtCaseId": "$existingDpsCourtCaseId",
                  "nomisCourtCaseId": $nomisCourtCaseId,
                  "courtAppearances": [],
                  "courtCharges": [],
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                 },
                 "duplicate" : {
                  "dpsCourtCaseId": "$duplicateDpsCourtCaseId",
                  "nomisCourtCaseId": $nomisCourtCaseId,
                  "courtAppearances": [],
                  "courtCharges": [],
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                  }
              }
              }""",
            ),
        ),
    )
  }

  fun stubGetCourtAppearanceByNomisId(
    nomisCourtAppearanceId: Long = 123456,
    dpsCourtAppearanceId: String = UUID.randomUUID().toString(),
    mapping: CourtAppearanceMappingDto = CourtAppearanceMappingDto(
      nomisCourtAppearanceId = nomisCourtAppearanceId,
      dpsCourtAppearanceId = dpsCourtAppearanceId,
      mappingType = CourtAppearanceMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/$nomisCourtAppearanceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetCourtAppearanceByNomisId(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostCourtAppearanceMapping() {
    mappingApi.stubFor(
      post("/mapping/court-sentencing/court-appearances").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostCourtAppearanceMapping(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      post("/mapping/court-sentencing/court-appearances").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostCourtAppearanceMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/court-sentencing/court-appearances")
  }

  fun stubCourtAppearanceMappingCreateConflict(
    existingDpsCourtAppearanceId: String = "10",
    duplicateDpsCourtAppearanceId: String = "11",
    nomisCourtAppearanceId: Long = 123,
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/court-sentencing/court-appearances"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "dpsCourtAppearanceId": "$existingDpsCourtAppearanceId",
                  "nomisCourtAppearanceId": $nomisCourtAppearanceId,
                  "courtCharges": [],
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                 },
                 "duplicate" : {
                  "dpsCourtAppearanceId": "$duplicateDpsCourtAppearanceId",
                  "nomisCourtAppearanceId": $nomisCourtAppearanceId,
                  "courtCharges": [],
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                  }
              }
              }""",
            ),
        ),
    )
  }

  fun stubDeleteCourtCaseMapping(dpsCourtCaseId: String) {
    mappingApi.stubFor(
      delete("/mapping/court-sentencing/court-cases/dps-court-case-id/$dpsCourtCaseId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubDeleteCourtCaseMappingByDpsId(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/court-sentencing/court-cases/dps-court-case-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteCourtAppearanceMappingByDpsId(dpsCourtAppearanceId: String) {
    mappingApi.stubFor(
      delete("/mapping/court-sentencing/court-appearances/dps-court-appearance-id/$dpsCourtAppearanceId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteCourtAppearanceMappingByDpsId(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/court-sentencing/court-appearances/dps-court-appearance-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetCourtChargeByNomisId(
    nomisCourtChargeId: Long = 123456,
    dpsCourtChargeId: String = UUID.randomUUID().toString(),
    mapping: CourtChargeMappingDto = CourtChargeMappingDto(
      nomisCourtChargeId = nomisCourtChargeId,
      dpsCourtChargeId = dpsCourtChargeId,
      mappingType = CourtChargeMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/court-sentencing/court-charges/nomis-court-charge-id/$nomisCourtChargeId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetCourtChargeByNomisIdNotFound(
    nomisCourtChargeId: Long,
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/court-sentencing/court-charges/nomis-court-charge-id/$nomisCourtChargeId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value())
          .withBody(
            objectMapper.writeValueAsString(ErrorResponse(HttpStatus.NOT_FOUND.value())),
          ),
      ),
    )
  }

  fun stubGetCourtChargeByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-sentencing/court-charges/nomis-court-charge-id/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostCourtChargeMapping() {
    mappingApi.stubFor(
      post("/mapping/court-sentencing/court-charges").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostCourtChargeMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/court-sentencing/court-charges").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostCourtChargeMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/court-sentencing/court-charges")
  }

  fun stubCourtChargeMappingCreateConflict(
    existingDpsCourtChargeId: String = "10",
    duplicateDpsCourtChargeId: String = "11",
    nomisCourtChargeId: Long = 123,
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/court-sentencing/court-charges"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "dpsCourtChargeId": "$existingDpsCourtChargeId",
                  "nomisCourtChargeId": $nomisCourtChargeId,
                  "courtCharges": [],
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                 },
                 "duplicate" : {
                  "dpsCourtChargeId": "$duplicateDpsCourtChargeId",
                  "nomisCourtChargeId": $nomisCourtChargeId,
                  "courtCharges": [],
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                  }
              }
              }""",
            ),
        ),
    )
  }

  fun stubDeleteCourtChargeMapping(nomisChargeId: Long) {
    mappingApi.stubFor(
      delete("/mapping/court-sentencing/court-charges/nomis-court-charge-id/$nomisChargeId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubGetSentenceByNomisId(
    nomisSentenceSequence: Int = 1,
    nomisBookingId: Long = 12345,
    dpsSentenceId: String = UUID.randomUUID().toString(),
    mapping: SentenceMappingDto = SentenceMappingDto(
      nomisSentenceSequence = nomisSentenceSequence,
      nomisBookingId = nomisBookingId,
      dpsSentenceId = dpsSentenceId,
      mappingType = SentenceMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/court-sentencing/sentences/nomis-booking-id/$nomisBookingId/nomis-sentence-sequence/$nomisSentenceSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetSentenceByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-sentencing/sentences/nomis-booking-id/\\d+/nomis-sentence-sequence/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostSentenceMapping() {
    mappingApi.stubFor(
      post("/mapping/court-sentencing/sentences").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostSentenceMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/court-sentencing/sentences").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostSentenceMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/court-sentencing/sentences")
  }

  fun stubSentenceMappingCreateConflict(
    existingDpsSentenceId: String = "10",
    duplicateDpsSentenceId: String = "11",
    nomisSentenceSequence: Int = 1,
    nomisBookingId: Long = 12345,
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/court-sentencing/sentences"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "dpsSentenceId": "$existingDpsSentenceId",
                  "nomisSentenceSequence": $nomisSentenceSequence,
                  "nomisBookingId": $nomisBookingId,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                 },
                 "duplicate" : {
                  "dpsSentenceId": "$duplicateDpsSentenceId",
                  "nomisSentenceSequence": $nomisSentenceSequence,
                  "nomisBookingId": $nomisBookingId,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                  }
              }
              }""",
            ),
        ),
    )
  }

  fun stubDeleteSentenceMapping(dpsSentenceId: String) {
    mappingApi.stubFor(
      delete("/mapping/court-sentencing/sentences/dps-sentence-id/$dpsSentenceId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubDeleteSentenceMappingByDpsId(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/court-sentencing/sentences/dps-sentence-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCourtCaseMappingByMigrationId(whenCreated: String = "2020-01-01T11:10:00", count: Int = 278887) {
    val content = """{
      "dpsCourtCaseId": "191747",
      "nomisCourtCaseId": 123,
      "label": "2022-02-14T09:58:45",
      "whenCreated": "$whenCreated",
      "mappingType": "MIGRATED"
    }"""
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-sentencing/court-cases/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(mappingApi.pageContent(content, count)),
      ),
    )
  }

  fun verifyCreateMappingCourtCaseIds(nomsCourtCaseIds: Array<String>, times: Int = 1) =
    nomsCourtCaseIds.forEach {
      verify(
        times,
        WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/court-sentencing/court-cases")).withRequestBody(
          WireMock.matchingJsonPath(
            "nomisCourtCaseId",
            WireMock.equalTo(it),
          ),
        ),
      )
    }

  fun verifyCreateMappingCourtCaseIds(
    migrationId: String,
    nomsCourtCaseIds: Array<String>,
    times: Int = 1,
  ) =
    nomsCourtCaseIds.forEach {
      verify(
        times,
        WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/court-sentencing/court-cases"))
          .withRequestBody(
            WireMock.matchingJsonPath(
              "nomisCourtCaseId",
              WireMock.equalTo(it),
            ),
          )
          .withRequestBody(
            WireMock.matchingJsonPath(
              "label",
              WireMock.equalTo(migrationId),
            ),
          ),
      )
    }

  fun createCourtCaseMappingCount() =
    createMappingCount("/mapping/court-sentencing/court-cases")

  fun createMappingCount(url: String) =
    mappingApi.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(url))).count()

  fun verifyCreateMappingCourtCase(
    dpsCourtCaseId: String,
    nomisCourtCaseId: Long,
    times: Int = 1,
  ) {
    verify(
      times,
      WireMock.postRequestedFor(WireMock.urlPathEqualTo("/mapping/court-sentencing/court-cases"))
        .withRequestBody(
          WireMock.matchingJsonPath(
            "dpsCourtCaseId",
            WireMock.equalTo(dpsCourtCaseId),
          ),
        )
        .withRequestBody(
          WireMock.matchingJsonPath(
            "nomisCourtCaseId",
            WireMock.equalTo(nomisCourtCaseId.toString()),
          ),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
