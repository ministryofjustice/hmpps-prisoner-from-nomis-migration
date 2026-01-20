package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime
import java.util.*

@Component
class PrisonerRestrictionMappingApiMockServer(private val jsonMapper: JsonMapper) {

  companion object {
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = mappingApi.getRequestBody(pattern, jsonMapper = jsonMapper)
  }

  fun stubGetByNomisPrisonerRestrictionIdOrNull(
    nomisRestrictionId: Long = 123456,
    dpsRestrictionId: String = UUID.randomUUID().toString(),
    mapping: PrisonerRestrictionMappingDto? = PrisonerRestrictionMappingDto(
      offenderNo = "A1234KT",
      nomisId = nomisRestrictionId,
      dpsId = dpsRestrictionId,
      mappingType = PrisonerRestrictionMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/$nomisRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/$nomisRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisPrisonerRestrictionId(
    nomisPrisonerRestrictionId: Long = 123456,
    mapping: PrisonerRestrictionMappingDto? = PrisonerRestrictionMappingDto(
      offenderNo = "A1234KT",
      nomisId = nomisPrisonerRestrictionId,
      dpsId = UUID.randomUUID().toString(),
      mappingType = PrisonerRestrictionMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId, mapping = mapping)

  fun stubCreateMapping() {
    mappingApi.stubFor(
      post("/mapping/contact-person/prisoner-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/contact-person/prisoner-restriction")

  fun stubCreateMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/prisoner-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetMigrationDetails(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/contact-person/prisoner-restriction/migration-id/$migrationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              jsonMapper = jsonMapper,
              content = listOf(
                PrisonerRestrictionMappingDto(
                  dpsId = UUID.randomUUID().toString(),
                  nomisId = 123456,
                  offenderNo = "A1234KT",
                  mappingType = PrisonerRestrictionMappingDto.MappingType.MIGRATED,
                  label = migrationId,
                  whenCreated = LocalDateTime.now().toString(),
                ),
              ),
              pageSize = 1L,
              pageNumber = 0L,
              totalElements = count.toLong(),
              size = 1,
            ),
          ),
      ),
    )
  }

  fun stubDeleteByNomisPrisonerRestrictionId(
    nomisPrisonerRestrictionId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/$nomisPrisonerRestrictionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun stubReplacePrisonerRestrictions(offenderNo: String) {
    mappingApi.stubFor(
      post("/mapping/contact-person/replace/prisoner-restrictions/$offenderNo").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubReplaceAfterMergePrisonerRestrictions(retainedOffenderNo: String, removedOffenderNo: String) {
    mappingApi.stubFor(
      post("/mapping/contact-person/replace/prisoner-restrictions/$retainedOffenderNo/replaces/$removedOffenderNo").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
