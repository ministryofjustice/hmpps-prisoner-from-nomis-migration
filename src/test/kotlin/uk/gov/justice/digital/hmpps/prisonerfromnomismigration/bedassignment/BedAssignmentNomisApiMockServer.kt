package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class BedAssignmentNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetBedAssignmentsForPrisoner(offenderNo: String, response: String = bedAssignmentHistoryJson()) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/bed-assignments"))
        .willReturn(okJson(response)),
    )
  }

  fun stubGetBedAssignment(bookingId: Long, sequence: Int, response: String = bedAssignmentJson(bookingId, sequence)) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/booking-id/$bookingId/bed-assignments/$sequence"))
        .willReturn(okJson(response)),
    )
  }

  fun stubGetBedAssignmentsError(offenderNo: String, status: Int = 500) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/bed-assignments")).willReturn(status(status)),
    )
  }

  fun stubGetBedAssignmentError(bookingId: Long, sequence: Int, status: Int = 500) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/booking-id/$bookingId/bed-assignments/$sequence")).willReturn(status(status)),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun bedAssignmentJson(
  bookingId: Long = 123456,
  sequence: Int = 1,
  livingUnitId: Long = 987654,
  livingUnitDescription: String? = "A-1-001",
  reasonCode: String? = "TRANSFER",
  endDateTime: String? = null,
  createUsername: String? = "NOMIS.USER",
) = """
  {
    "bookingId": $bookingId,
    "sequence": $sequence,
    "livingUnitId": $livingUnitId,
    "livingUnitDescription": ${livingUnitDescription?.let { "\"$it\"" } ?: "null"},
    "assignmentDateTime": "2024-01-02T03:04:05",
    "assignmentEndDateTime": ${endDateTime?.let { "\"$it\"" } ?: "null"},
    "assignmentReasonCode": ${reasonCode?.let { "\"$it\"" } ?: "null"},
    "audit": {"createDatetime":"2024-01-02T03:04:05","createUsername":"${createUsername ?: "NOMIS.USER"}"}
  }
""".trimIndent()

fun bedAssignmentHistoryWithRowsJson(vararg bedAssignments: String): String = """{"bedAssignments":[${bedAssignments.joinToString(",") }]}"""
  .let { if (bedAssignments.isEmpty()) """{"bedAssignments":[]}""" else it }

fun bedAssignmentHistoryJson(
  bookingId: Long = 123456,
  sequence: Int = 1,
  livingUnitId: Long = 987654,
  livingUnitDescription: String? = "A-1-001",
  reasonCode: String? = "TRANSFER",
  endDateTime: String? = null,
  createUsername: String? = "NOMIS.USER",
): String = """{"bedAssignments":[${bedAssignmentJson(bookingId, sequence, livingUnitId, livingUnitDescription, reasonCode, endDateTime, createUsername)}]}"""
