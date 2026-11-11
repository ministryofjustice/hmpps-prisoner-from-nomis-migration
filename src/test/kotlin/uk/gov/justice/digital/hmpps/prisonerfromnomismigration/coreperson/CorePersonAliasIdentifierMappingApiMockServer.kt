package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent

@Component
class CorePersonAliasIdentifierMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubCreateMappingsForMigration() {
    mappingApi.stubFor(
      post("/mapping/core-person/alias-identifier-migration").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateMappingsForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/core-person/alias-identifier-migration").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateMappingsForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/core-person/alias-identifier-migration")

  fun stubGetMigrationCount(count: Int = 1, corePersonMappingDto: List<CorePersonMappingDto>? = null) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/core-person/alias-identifier-migration/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              jsonMapper = jsonMapper,
              content = corePersonMappingDto ?: emptyList(),
              pageSize = 1L,
              pageNumber = 0L,
              totalElements = count.toLong(),
              size = 1,
            ),
          ),
      ),
    )
  }

  fun stubGetMappingsFromNomisIds(
    result: CorePersonAliasIdentifierMappingsResult = CorePersonAliasIdentifierMappingsResult(aliases = emptyList(), identifiers = emptyList()),
  ) {
    mappingApi.stubFor(
      post("/mapping/core-person/alias-identifier/mappings/by-nomis-ids").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(result)),
      ),
    )
  }

  fun stubReplaceMappings() {
    mappingApi.stubFor(
      post("/mapping/core-person/alias-identifier/replace").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubGetCorePersonByNomisPrisonNumberOrNull(
    nomisPrisonNumber: String = "A1234BC",
    mapping: CorePersonMappingDto? = CorePersonMappingDto(cprId = "cpr-id", nomisPrisonNumber = nomisPrisonNumber, mappingType = CorePersonMappingDto.MappingType.MIGRATED),
  ) {
    stubGet(
      "/mapping/core-person/alias-identifier-migration/person/nomis-prison-number/$nomisPrisonNumber",
      mapping,
    )
  }

  fun stubGetByNomisAliasIdOrNull(
    nomisOffenderId: Long = 123456,
    mapping: OffenderAliasMappingDto? = offenderAliasMapping(nomisOffenderId),
  ) {
    stubGet(
      "/mapping/core-person/alias/nomis-offender-id/$nomisOffenderId",
      mapping,
    )
  }

  fun stubGetByNomisIdentifierIdOrNull(
    nomisOffenderId: Long = 123456,
    nomisIdentifierSequence: Int = 1,
    mapping: OffenderIdentifierMappingDto? = offenderIdentifierMapping(nomisOffenderId, nomisIdentifierSequence),
  ) {
    stubGet(
      "/mapping/core-person/identifier/nomis-offender-id/$nomisOffenderId/nomis-identifier-sequence/$nomisIdentifierSequence",
      mapping,
    )
  }

  fun stubDeleteByNomisAliasId(nomisOffenderId: Long = 123456) = stubDelete("/mapping/core-person/alias/nomis-offender-id/$nomisOffenderId")

  fun stubDeleteByNomisIdentifierId(nomisOffenderId: Long = 123456, nomisIdentifierSequence: Int = 1) = stubDelete("/mapping/core-person/identifier/nomis-offender-id/$nomisOffenderId/nomis-identifier-sequence/$nomisIdentifierSequence")

  private fun stubDelete(path: String) {
    mappingApi.stubFor(
      delete(urlEqualTo(path)).willReturn(
        aResponse().withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateAliasMapping() = stubCreate("/mapping/core-person/alias")

  fun stubCreateAliasMapping(error: DuplicateMappingErrorResponse) = stubCreate("/mapping/core-person/alias", error)

  fun stubCreateAliasMappingFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/core-person/alias")

  fun stubCreateIdentifierMapping() = stubCreate("/mapping/core-person/identifier")

  fun stubCreateIdentifierMapping(error: DuplicateMappingErrorResponse) = stubCreate("/mapping/core-person/identifier", error)

  fun stubCreateIdentifierMappingFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/core-person/identifier")

  private fun stubCreate(path: String) {
    mappingApi.stubFor(
      post(urlEqualTo(path)).willReturn(
        aResponse().withStatus(HttpStatus.CREATED.value()),
      ),
    )
  }

  private fun stubCreate(path: String, error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post(urlEqualTo(path)).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CONFLICT.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  private fun stubGet(path: String, mapping: Any?) {
    mappingApi.stubFor(
      get(urlEqualTo(path)).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(if (mapping == null) HttpStatus.NOT_FOUND.value() else HttpStatus.OK.value())
          .apply { mapping?.let { withBody(jsonMapper.writeValueAsString(it)) } },
      ),
    )
  }

  private fun offenderAliasMapping(nomisOffenderId: Long) = OffenderAliasMappingDto(
    cprId = "cpr-alias-id",
    nomisOffenderId = nomisOffenderId,
    nomisPrisonNumber = "A1234BC",
    mappingType = OffenderAliasMappingDto.MappingType.MIGRATED,
  )

  private fun offenderIdentifierMapping(nomisOffenderId: Long, nomisIdentifierSequence: Int) = OffenderIdentifierMappingDto(
    cprId = "cpr-identifier-id",
    nomisOffenderId = nomisOffenderId,
    nomisIdentifierSequence = nomisIdentifierSequence,
    nomisPrisonNumber = "A1234BC",
    mappingType = OffenderIdentifierMappingDto.MappingType.MIGRATED,
  )

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
}
