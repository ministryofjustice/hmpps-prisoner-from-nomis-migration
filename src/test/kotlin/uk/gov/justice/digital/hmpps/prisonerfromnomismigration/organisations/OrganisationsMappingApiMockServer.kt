package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OrganisationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime

@Component
class OrganisationsMappingApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubCreateMappingsForMigration() {
    mappingApi.stubFor(
      post("/mapping/corporate/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  @Suppress("unused")
  fun stubCreateMappingsForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/corporate/migrate")

  fun stubCreateMappingsForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/corporate/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }
  fun stubGetMigrationDetails(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/corporate/organisation/migration-id/$migrationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              objectMapper = objectMapper,
              content = listOf(
                OrganisationsMappingDto(
                  dpsId = "654321",
                  nomisId = 123456,
                  mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
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

  fun stubCreateCorporateMapping() {
    mappingApi.stubFor(
      post("/mapping/corporate/organisation").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateCorporateMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/corporate/organisation").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCorporateMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/corporate/organisation")

  fun stubGetByNomisCorporateIdOrNull(
    nomisCorporateId: Long = 123456,
    mapping: OrganisationsMappingDto? = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = "123456",
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/organisation/nomis-corporate-id/$nomisCorporateId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/organisation/nomis-corporate-id/$nomisCorporateId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisCorporateId(
    nomisCorporateId: Long = 123456,
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = "123456",
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisCorporateIdOrNull(nomisCorporateId, mapping)

  fun stubDeleteByNomisCorporateId(
    nomisCorporateId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/corporate/organisation/nomis-corporate-id/$nomisCorporateId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateAddressMapping() {
    mappingApi.stubFor(
      post("/mapping/corporate/address").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateAddressMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/corporate/address").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateAddressMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/corporate/address")

  fun stubGetByNomisAddressIdOrNull(
    nomisAddressId: Long = 123456,
    mapping: OrganisationsMappingDto? = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/address/nomis-address-id/$nomisAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/address/nomis-address-id/$nomisAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisAddressId(
    nomisAddressId: Long = 123456,
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = "123456",
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisAddressIdOrNull(nomisAddressId, mapping)

  fun stubDeleteByNomisAddressId(
    nomisAddressId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/corporate/address/nomis-address-id/$nomisAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreatePhoneMapping() {
    mappingApi.stubFor(
      post("/mapping/corporate/phone").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreatePhoneMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/corporate/phone").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreatePhoneMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/corporate/phone")

  fun stubGetByNomisPhoneIdOrNull(
    nomisPhoneId: Long = 123456,
    mapping: OrganisationsMappingDto? = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/phone/nomis-phone-id/$nomisPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/phone/nomis-phone-id/$nomisPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisPhoneId(
    nomisPhoneId: Long = 123456,
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = "123456",
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisPhoneIdOrNull(nomisPhoneId, mapping)

  fun stubDeleteByNomisPhoneId(
    nomisPhoneId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/corporate/phone/nomis-phone-id/$nomisPhoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateAddressPhoneMapping() {
    mappingApi.stubFor(
      post("/mapping/corporate/address-phone").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateAddressPhoneMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/corporate/address-phone").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateAddressPhoneMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/corporate/address-phone")

  fun stubGetByNomisAddressPhoneIdOrNull(
    nomisPhoneId: Long = 123456,
    mapping: OrganisationsMappingDto? = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/address-phone/nomis-phone-id/$nomisPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/address-phone/nomis-phone-id/$nomisPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisAddressPhoneId(
    nomisPhoneId: Long = 123456,
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = "123456",
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisAddressPhoneIdOrNull(nomisPhoneId, mapping)

  fun stubDeleteByNomisAddressPhoneId(
    nomisPhoneId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/corporate/address-phone/nomis-phone-id/$nomisPhoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
