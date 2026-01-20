package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OrganisationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@Component
class OrganisationsMappingApiMockServer(private val jsonMapper: JsonMapper) {

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
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/organisation/nomis-corporate-id/$nomisCorporateId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/address/nomis-address-id/$nomisAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/phone/nomis-phone-id/$nomisPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
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
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/address-phone/nomis-phone-id/$nomisPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
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

  fun stubCreateWebMapping() {
    mappingApi.stubFor(
      post("/mapping/corporate/web").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateWebMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/corporate/web").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateWebMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/corporate/web")

  fun stubGetByNomisWebIdOrNull(
    nomisWebId: Long = 123456,
    mapping: OrganisationsMappingDto? = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/web/nomis-internet-address-id/$nomisWebId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/web/nomis-internet-address-id/$nomisWebId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisWebId(
    nomisWebId: Long = 123456,
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = "123456",
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisWebIdOrNull(nomisWebId, mapping)

  fun stubDeleteByNomisWebId(
    nomisWebId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/corporate/web/nomis-internet-address-id/$nomisWebId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateEmailMapping() {
    mappingApi.stubFor(
      post("/mapping/corporate/email").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateEmailMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/corporate/email").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateEmailMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/corporate/email")

  fun stubGetByNomisEmailIdOrNull(
    nomisEmailId: Long = 123456,
    mapping: OrganisationsMappingDto? = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/email/nomis-internet-address-id/$nomisEmailId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/corporate/email/nomis-internet-address-id/$nomisEmailId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisEmailId(
    nomisEmailId: Long = 123456,
    mapping: OrganisationsMappingDto = OrganisationsMappingDto(
      nomisId = 123456,
      dpsId = "123456",
      mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisEmailIdOrNull(nomisEmailId, mapping)

  fun stubDeleteByNomisEmailId(
    nomisEmailId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/corporate/email/nomis-internet-address-id/$nomisEmailId")).willReturn(
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
