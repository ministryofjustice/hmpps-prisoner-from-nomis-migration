package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@Component
class BedAssignmentMappingApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetByNomisId(bookingId: Long, sequence: Int, response: String = bedAssignmentMappingJson(bookingId, sequence)) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/bed-assignments/booking-id/$bookingId/sequence/$sequence"))
        .willReturn(okJson(response)),
    )
  }

  fun stubGetByNomisId(bookingId: Long, sequence: Int, status: HttpStatus) {
    mappingApi.stubFor(
      get(urlEqualTo("/mapping/bed-assignments/booking-id/$bookingId/sequence/$sequence"))
        .willReturn(aResponse().withStatus(status.value()).withBody("""{"status":${status.value()}}""")),
    )
  }

  fun stubCreateMapping(status: HttpStatus = HttpStatus.CREATED) {
    mappingApi.stubFor(post("/mapping/bed-assignments").willReturn(status(status.value())))
  }

  fun stubCreateMappingFailureFollowedBySuccess() {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/bed-assignments")
  }

  fun stubCreateMigrationMappings(offenderNo: String, status: HttpStatus = HttpStatus.CREATED) {
    mappingApi.stubFor(post("/mapping/bed-assignments/$offenderNo/all").willReturn(status(status.value())))
  }

  fun stubCreateMigrationMappingsFailureFollowedBySuccess(offenderNo: String) {
    mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/bed-assignments/$offenderNo/all")
  }

  fun stubDeleteMapping(status: HttpStatus = HttpStatus.NO_CONTENT) {
    mappingApi.stubFor(
      delete(urlPathMatching("/mapping/bed-assignments/dps-bed-assignment-id/.*"))
        .willReturn(status(status.value())),
    )
  }

  fun stubDeleteMappingFailureFollowedBySuccess(dpsBedAssignmentId: String) {
    val path = "/mapping/bed-assignments/dps-bed-assignment-id/$dpsBedAssignmentId"
    mappingApi.stubFor(
      delete(urlEqualTo(path)).inScenario("Bed assignment mapping deletion")
        .whenScenarioStateIs(STARTED)
        .willReturn(status(HttpStatus.INTERNAL_SERVER_ERROR.value()))
        .willSetStateTo("Bed assignment mapping deletion succeeds"),
    )
    mappingApi.stubFor(
      delete(urlEqualTo(path)).inScenario("Bed assignment mapping deletion")
        .whenScenarioStateIs("Bed assignment mapping deletion succeeds")
        .willReturn(status(HttpStatus.NO_CONTENT.value()))
        .willSetStateTo(STARTED),
    )
  }

  fun stubMigrationCount(count: Long) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/bed-assignments/migration-id/.*/count-by-prisoner"))
        .willReturn(okJson(count.toString())),
    )
  }

  fun stubMigrationMappingsDuplicate(offenderNo: String, duplicateId: String, existingId: String) {
    val path = "/mapping/bed-assignments/$offenderNo/all"
    mappingApi.stubFor(
      post(path).willReturn(
        duplicateMappingResponse(duplicateId, existingId),
      ),
    )
  }

  fun stubMigrationMappingsCreateThenDuplicate(offenderNo: String, dpsId: String) {
    val path = "/mapping/bed-assignments/$offenderNo/all"
    mappingApi.stubFor(
      post(path).inScenario("Repeated bed-assignment migration")
        .whenScenarioStateIs(STARTED)
        .willReturn(status(HttpStatus.CREATED.value()))
        .willSetStateTo("Bed-assignment mappings already exist"),
    )
    mappingApi.stubFor(
      post(path).inScenario("Repeated bed-assignment migration")
        .whenScenarioStateIs("Bed-assignment mappings already exist")
        .willReturn(duplicateMappingResponse(dpsId, dpsId))
        .willSetStateTo(STARTED),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
}

fun bedAssignmentMappingJson(
  bookingId: Long = 123456,
  sequence: Int = 1,
  id: String = "e52d7268-6e10-41a8-a0b9-2319b32520d6",
  mappingType: String = "MIGRATED",
) = """
  {
    "nomisBookingId": $bookingId,
    "nomisSequence": $sequence,
    "dpsBedAssignmentId": "$id",
    "mappingType": "$mappingType"
  }
""".trimIndent()

private fun duplicateMappingResponse(duplicateId: String, existingId: String) = com.github.tomakehurst.wiremock.client.WireMock.jsonResponse(
  """
  {
    "moreInfo": {
      "duplicate": {
        "dpsBedAssignmentId": "$duplicateId",
        "nomisBookingId": 123456,
        "nomisSequence": 1,
        "mappingType": "MIGRATED"
      },
      "existing": {
        "dpsBedAssignmentId": "$existingId",
        "nomisBookingId": 123456,
        "nomisSequence": 1,
        "mappingType": "MIGRATED"
      }
    },
    "status": "409 CONFLICT",
    "errorCode": 1409,
    "userMessage": "Duplicate mapping"
  }
  """.trimIndent(),
  409,
)
