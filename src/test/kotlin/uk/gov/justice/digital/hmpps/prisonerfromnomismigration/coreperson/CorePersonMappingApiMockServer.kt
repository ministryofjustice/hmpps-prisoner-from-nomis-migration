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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressUsageMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto.NomisContactType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent

@Component
class CorePersonMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubCreateMappingsForMigration() {
    mappingApi.stubFor(
      post("/mapping/core-person").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }
  fun stubCreateMappingsForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/core-person").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateMappingsForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/core-person")

  fun stubGetMigrationCount(count: Int = 1, corePersonMappingDto: List<CorePersonMappingDto>? = null) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/core-person/migration-id/.*")).willReturn(
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

  fun stubReplaceMappings() {
    mappingApi.stubFor(
      post("/mapping/core-person/replace").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubGetCorePersonByNomisPrisonNumberOrNull(
    nomisPrisonNumber: String = "A1234BC",
    mapping: CorePersonMappingDto,
  ) {
    mapping.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/core-person/person/nomis-prison-number/$nomisPrisonNumber")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    }
  }

  fun stubGetByNomisAddressIdOrNull(
    nomisAddressId: Long = 123456,
    mapping: CorePersonAddressMappingDto? = corePersonAddressMapping(nomisAddressId),
  ) {
    stubGet(
      "/mapping/core-person/address/nomis-address-id/$nomisAddressId",
      mapping,
    )
  }

  fun stubGetByNomisAddressUsageIdOrNull(
    nomisAddressId: Long = 123456,
    addressUsageCode: String = "HOME",
    mapping: CorePersonAddressUsageMappingDto? = corePersonAddressUsageMapping(nomisAddressId, addressUsageCode),
  ) {
    stubGet(
      "/mapping/core-person/address-usage/nomis-address-id/$nomisAddressId/usage-code/$addressUsageCode",
      mapping,
    )
  }

  fun stubGetByNomisContactIdOrNull(
    nomisContactId: Long = 123456,
    nomisContactType: NomisContactType,
    mapping: CorePersonContactMappingDto? = corePersonContactMapping(nomisContactId, nomisContactType),
  ) {
    stubGet(
      "/mapping/core-person/contact/nomis-contact-id/$nomisContactId/type/$nomisContactType",
      mapping,
    )
  }

  fun stubDeleteByNomisAddressId(nomisAddressId: Long = 123456) = stubDelete("/mapping/core-person/address/nomis-address-id/$nomisAddressId")

  fun stubDeleteByNomisAddressUsageId(nomisAddressId: Long = 123456, addressUsageCode: String = "HOME") = stubDelete("/mapping/core-person/address-usage/nomis-address-id/$nomisAddressId/usage-code/$addressUsageCode")

  fun stubDeleteByNomisContactId(nomisContactId: Long = 123456, nomisContactType: NomisContactType) = stubDelete("/mapping/core-person/contact/nomis-contact-id/$nomisContactId/type/$nomisContactType")

  private fun stubDelete(path: String) {
    mappingApi.stubFor(
      delete(urlEqualTo(path)).willReturn(
        aResponse().withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateAddressMapping() = stubCreate("/mapping/core-person/address")

  fun stubCreateAddressMapping(error: DuplicateMappingErrorResponse) = stubCreate("/mapping/core-person/address", error)

  fun stubCreateAddressMappingFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/core-person/address")

  fun stubCreateAddressUsageMapping() = stubCreate("/mapping/core-person/address-usage")

  fun stubCreateAddressUsageMapping(error: DuplicateMappingErrorResponse) = stubCreate("/mapping/core-person/address-usage", error)

  fun stubCreateAddressUsageMappingFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/core-person/address-usage")

  fun stubCreateContactMapping() = stubCreate("/mapping/core-person/contact")

  fun stubCreateContactMapping(error: DuplicateMappingErrorResponse) = stubCreate("/mapping/core-person/contact", error)

  fun stubCreateContactMappingFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/core-person/contact")

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

  private fun corePersonAddressMapping(nomisAddressId: Long) = CorePersonAddressMappingDto(
    cprId = "cpr-address-id",
    nomisId = nomisAddressId,
    nomisPrisonNumber = "A1234BC",
    mappingType = CorePersonAddressMappingDto.MappingType.MIGRATED,
  )

  private fun corePersonAddressUsageMapping(nomisAddressId: Long, addressUsageCode: String) = CorePersonAddressUsageMappingDto(
    cprId = "cpr-address-usage-id",
    nomisId = nomisAddressId,
    addressUsageCode = addressUsageCode,
    nomisPrisonNumber = "A1234BC",
    mappingType = CorePersonAddressUsageMappingDto.MappingType.MIGRATED,
  )

  private fun corePersonContactMapping(nomisId: Long, nomisContactType: NomisContactType) = CorePersonContactMappingDto(
    cprId = "cpr-contact-id",
    nomisId = nomisId,
    nomisContactType = nomisContactType,
    nomisPrisonNumber = "A1234BC",
    mappingType = CorePersonContactMappingDto.MappingType.MIGRATED,
  )

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
}
