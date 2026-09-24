package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactRelationship
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OfficialVisitor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class OfficialVisitsNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    fun officialVisitResponse() = OfficialVisitResponse(
      audit = NomisAudit(
        createDatetime = LocalDateTime.parse("2020-01-01T10:00"),
        createUsername = "B.BOB",
      ),
      visitId = 1,
      visitSlotId = 20,
      prisonId = "MDI",
      offenderNo = "A1234KT",
      bookingId = 30,
      currentTerm = true,
      startDateTime = LocalDateTime.parse("2020-01-01T10:00"),
      endDateTime = LocalDateTime.parse("2020-01-01T11:00"),
      internalLocationId = 40,
      visitStatus = CodeDescription("NORM", "Normal Completion"),
      visitors = listOf(officialVisitor()),
    )

    fun officialVisitor() = OfficialVisitor(
      id = 123,
      audit = NomisAudit(
        createDatetime = LocalDateTime.parse("2020-01-01T10:00"),
        createUsername = "B.BOB",
      ),
      personId = 20,
      firstName = "JANE",
      lastName = "DEO",
      leadVisitor = true,
      assistedVisit = true,
      relationships = listOf(
        ContactRelationship(
          relationshipType = CodeDescription(code = "POL", description = "Police"),
          contactType = CodeDescription(code = "O", description = "Official"),
        ),
      ),
    )
  }

  fun stubGetOfficialVisit(
    visitId: Long = 1234,
    response: OfficialVisitResponse = officialVisitResponse(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/official-visits/$visitId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubGetOfficialVisit(
    visitId: Long = 1234,
    errorStatus: HttpStatus,
    response: ErrorResponse,
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/official-visits/$visitId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(errorStatus.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetOfficialVisitsForPrisoner(offenderNo: String, response: List<OfficialVisitResponse> = emptyList()) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoner/$offenderNo/official-visits")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
