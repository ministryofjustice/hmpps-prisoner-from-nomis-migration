package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSentencingMigrationSummary
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceTermMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.util.UUID

@Component
class CourtSentencingMappingApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = mappingApi.getRequestBody(pattern, jsonMapper = jsonMapper)
  }

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
          .withBody(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-sentencing/court-cases/nomis-court-case-id/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetCasesByNomisIds(response: List<CourtCaseMappingDto>) {
    mappingApi.stubFor(
      post(urlPathEqualTo("/mapping/court-sentencing/court-cases/nomis-case-ids/get-list")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetMigrationSummaryByOffenderNo(
    offenderNo: String = UUID.randomUUID().toString(),
    summary: CourtSentencingMigrationSummary = CourtSentencingMigrationSummary(
      offenderNo = offenderNo,
      mappingsCount = 1,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/court-sentencing/prisoner/$/migration-summary")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(summary)),
      ),
    )
  }

  fun stubGetMigrationSummaryByOffenderNo(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/prisoner/\\s+/migration-summary")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
                  "sentences": [],
                  "sentenceTerms": [],
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                 },
                 "duplicate" : {
                  "dpsCourtCaseId": "$duplicateDpsCourtCaseId",
                  "nomisCourtCaseId": $nomisCourtCaseId,
                  "courtAppearances": [],
                  "courtCharges": [],
                  "sentences": [],
                  "sentenceTerms": [],
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

  fun stubPostMigrationMapping() {
    mappingApi.stubFor(
      post(urlPathMatching("/mapping/court-sentencing/prisoner/\\S+/court-cases")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubPostMigrationMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post(urlPathMatching("/mapping/court-sentencing/prisoner/\\S+/court-cases")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMigrationMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/court-sentencing/prisoner/\\S+/court-cases")
  }

  fun createMigrationMappingCount() = createMappingCount("/mapping/court-sentencing/prisoner/\\S+/court-cases")

  fun stubMigrationMappingCreateConflict() {
    mappingApi.stubFor(
      post(urlPathMatching("/mapping/court-sentencing/prisoner/\\S+/court-cases"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "courtCases": [],
                  "courtAppearances": [],
                  "courtCharges": [],
                  "sentences": [],
                  "sentenceTerms": [],
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                 },
                 "duplicate" : {
                  "courtCases": [],
                  "courtAppearances": [],
                  "courtCharges": [],
                  "sentences": [],
                  "sentenceTerms": [],
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

  fun verifyCreateMappingMigrationOffenderIds(
    offenderNos: Array<String>,
    times: Int = 1,
  ) = offenderNos.forEach {
    verify(
      times,
      WireMock.postRequestedFor(urlPathMatching("/mapping/court-sentencing/prisoner/\\S+/court-cases")),
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
          .withBody(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }
  fun stubGetCourtAppearanceByNomisIdNotFoundFollowedBySuccess(
    nomisCourtAppearanceId: Long = 123456,
    dpsCourtAppearanceId: String = UUID.randomUUID().toString(),
    mapping: CourtAppearanceMappingDto = CourtAppearanceMappingDto(
      nomisCourtAppearanceId = nomisCourtAppearanceId,
      dpsCourtAppearanceId = dpsCourtAppearanceId,
      mappingType = CourtAppearanceMappingDto.MappingType.MIGRATED,
    ),
  ) {
    val scenarioName = "Cause Court Appearance create Success"
    val successScenario = "Cause Court Appearance create Success"
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/$nomisCourtAppearanceId"))
        .inScenario(scenarioName)
        .whenScenarioStateIs(STARTED)
        .willReturn(
          aResponse()
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo(successScenario),
    )

    mappingApi.stubFor(
      get(urlEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-id/$nomisCourtAppearanceId"))
        .inScenario(scenarioName)
        .whenScenarioStateIs(successScenario)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        )
        .willSetStateTo(successScenario),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
          .withBody(jsonMapper.writeValueAsString(mapping)),
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
            jsonMapper.writeValueAsString(ErrorResponse(HttpStatus.NOT_FOUND.value())),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
          .withBody(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetSentenceByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-sentencing/sentences/nomis-booking-id/\\d+/nomis-sentence-sequence/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetSentenceTermByNomisId(
    nomisSentenceSequence: Int = 1,
    nomisTermSequence: Int = 1,
    nomisBookingId: Long = 12345,
    dpsTermId: String = UUID.randomUUID().toString(),
    mapping: SentenceTermMappingDto = SentenceTermMappingDto(
      nomisSentenceSequence = nomisSentenceSequence,
      nomisTermSequence = nomisTermSequence,
      nomisBookingId = nomisBookingId,
      dpsTermId = dpsTermId,
      mappingType = SentenceTermMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/court-sentencing/sentence-terms/nomis-booking-id/$nomisBookingId/nomis-sentence-sequence/$nomisSentenceSequence/nomis-term-sequence/$nomisTermSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetSentenceTermByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-sentencing/sentence-terms/nomis-booking-id/\\d+/nomis-sentence-sequence/\\d+/nomis-term-sequence/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
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

  fun stubPostSentenceTermMapping() {
    mappingApi.stubFor(
      post("/mapping/court-sentencing/sentence-terms").willReturn(
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
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostSentenceTermMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/court-sentencing/sentence-terms").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
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

  fun stubPostSentenceTermMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/court-sentencing/sentence-terms")
  }

  fun stubSentenceTermMappingCreateConflict(
    existingDpsTermId: String = "10",
    duplicateDpsTermId: String = "11",
    nomisTermSequence: Int = 1,
    nomisSentenceSequence: Int = 1,
    nomisBookingId: Long = 12345,
  ) {
    mappingApi.stubFor(
      post(WireMock.urlPathEqualTo("/mapping/court-sentencing/sentence-terms"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "dpsTermId": "$existingDpsTermId",
                  "nomisSentenceSequence": $nomisSentenceSequence,
                  "nomisTermSequence": $nomisTermSequence,
                  "nomisBookingId": $nomisBookingId,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                 },
                 "duplicate" : {
                  "dpsTermId": "$duplicateDpsTermId",
                  "nomisSentenceSequence": $nomisSentenceSequence,
                  "nomisTermSequence": $nomisTermSequence,
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
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteSentenceTermMapping(dpsTermId: String) {
    mappingApi.stubFor(
      delete("/mapping/court-sentencing/sentence-terms/dps-term-id/$dpsTermId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubDeleteSentenceTermMappingByDpsId(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/court-sentencing/sentence-terms/dps-term-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCourtSentencingSummaryByMigrationId(offenderNo: String = "AN12345", whenCreated: String = "2020-01-01T11:10:00", count: Int = 278887) {
    val summary = CourtSentencingMigrationSummary(offenderNo = offenderNo, mappingsCount = count, whenCreated = whenCreated)
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/court-sentencing/prisoner/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(mappingApi.pageContent(jsonMapper.writeValueAsString(summary), count)),
      ),
    )
  }

  fun verifyCreateMappingCourtCaseIds(nomsCourtCaseIds: Array<String>, times: Int = 1) = nomsCourtCaseIds.forEach {
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

  fun createCourtCaseMappingCount() = createMappingCount("/mapping/court-sentencing/court-cases")

  fun createMappingCount(url: String) = mappingApi.findAll(WireMock.postRequestedFor(WireMock.urlPathMatching(url))).count()

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

  fun stubGetSentencesByNomisIds(response: List<SentenceMappingDto>) {
    mappingApi.stubFor(
      post(urlPathEqualTo("/mapping/court-sentencing/sentences/nomis-sentence-ids/get-list")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubReplaceOrCreateMappings() {
    mappingApi.stubFor(
      put("/mapping/court-sentencing/court-cases/replace").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubReplaceOrCreateMappingsFailureFollowedBySuccess() {
    mappingApi.stubMappingUpdateFailureFollowedBySuccess(url = "/mapping/court-sentencing/court-cases/replace")
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}
