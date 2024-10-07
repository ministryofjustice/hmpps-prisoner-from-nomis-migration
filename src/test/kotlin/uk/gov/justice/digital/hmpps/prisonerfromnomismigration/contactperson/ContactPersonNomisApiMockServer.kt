package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class ContactPersonNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetPerson(
    personId: Long = 123456,
    person: ContactPerson = contactPerson(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/persons/$personId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(person)),
      ),
    )
  }

  fun stubGetPerson(
    personId: Long = 123456,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/persons/$personId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetPersonIdsToMigrate(
    count: Long = 1,
    content: List<PersonIdResponse> = listOf(
      PersonIdResponse(123456),
    ),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/persons/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(pagePersonIdResponse(count = count, content = content)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  fun pagePersonIdResponse(
    count: Long,
    content: List<PersonIdResponse>,
  ) = pageContent(
    objectMapper = objectMapper,
    content = content,
    pageSize = 1L,
    pageNumber = 0L,
    totalElements = count,
    size = 1,
  )
}

fun contactPerson(): ContactPerson = ContactPerson(
  personId = 123456,
  firstName = "KWAME",
  lastName = "KOBE",
  interpreterRequired = false,
  keepBiometrics = false,
  audit = NomisAudit(
    createDatetime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    createUsername = "Q1251T",
  ),
  phoneNumbers = emptyList(),
  addresses = emptyList(),
  emailAddresses = emptyList(),
  employments = emptyList(),
  identifiers = emptyList(),
  contacts = emptyList(),
  restrictions = emptyList(),
)
