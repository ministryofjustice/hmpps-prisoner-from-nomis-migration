package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.corporate

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class CorporateNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetCorporateOrganisation(
    corporateId: Long = 123456,
    corporate: CorporateOrganisation = corporateOrganisation(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/corporates/$corporateId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(corporate)),
      ),
    )
  }
  fun stubGetCorporateOrganisationIdsToMigrate(
    count: Long = 1,
    content: List<CorporateOrganisationIdResponse> = listOf(
      CorporateOrganisationIdResponse(123456),
    ),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/corporates/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(pageCorporateIdResponse(count = count, content = content)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  fun pageCorporateIdResponse(
    count: Long,
    content: List<CorporateOrganisationIdResponse>,
  ) = pageContent(
    objectMapper = objectMapper,
    content = content,
    pageSize = 1L,
    pageNumber = 0L,
    totalElements = count,
    size = 1,
  )
}

fun corporateOrganisation(corporateId: Long = 123456): CorporateOrganisation = CorporateOrganisation(
  id = corporateId,
  name = "Boots",
  active = true,
  phoneNumbers = emptyList(),
  addresses = emptyList(),
  internetAddresses = emptyList(),
  types = emptyList(),
  audit = nomisAudit(),
)

fun nomisAudit() = NomisAudit(
  createDatetime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
  createUsername = "Q1251T",
)
