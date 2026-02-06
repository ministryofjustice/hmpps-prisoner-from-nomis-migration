package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonEmploymentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime
import java.util.*

@Component
class ContactPersonMappingApiMockServer(private val jsonMapper: JsonMapper) {

  companion object {
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = mappingApi.getRequestBody(pattern, jsonMapper = jsonMapper)
  }

  fun stubGetByNomisPersonIdOrNull(
    nomisPersonId: Long = 123456,
    mapping: PersonMappingDto? = PersonMappingDto(
      nomisId = 123456,
      dpsId = UUID.randomUUID().toString(),
      mappingType = PersonMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/person/nomis-person-id/$nomisPersonId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/person/nomis-person-id/$nomisPersonId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisPersonId(
    nomisPersonId: Long = 123456,
    mapping: PersonMappingDto = PersonMappingDto(
      nomisId = 123456,
      dpsId = UUID.randomUUID().toString(),
      mappingType = PersonMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisPersonIdOrNull(nomisPersonId, mapping)

  fun stubDeleteByNomisPersonId(
    nomisPersonId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/contact-person/person/nomis-person-id/$nomisPersonId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateMappingsForMigration() {
    mappingApi.stubFor(
      post("/mapping/contact-person/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateMappingsForMigrationFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/contact-person/migrate")

  fun stubCreateMappingsForMigration(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/migrate").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetMigrationDetails(migrationId: String = "2020-01-01T11:10:00", count: Int = 1) {
    mappingApi.stubFor(
      get(urlPathMatching("/mapping/contact-person/person/migration-id/$migrationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(
              jsonMapper = jsonMapper,
              content = listOf(
                PersonMappingDto(
                  dpsId = UUID.randomUUID().toString(),
                  nomisId = 123456,
                  mappingType = PersonMappingDto.MappingType.MIGRATED,
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

  fun stubReplaceMappingsForPrisoner(offenderNo: String) {
    mappingApi.stubFor(
      post("/mapping/contact-person/replace/prisoner/$offenderNo").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }
  fun stubReplaceMappingsForPerson(personId: Long) {
    mappingApi.stubFor(
      post("/mapping/contact-person/replace/person/$personId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubReplaceMappingsForPrisonerFailureFollowedBySuccess(offenderNo: String) = mappingApi.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/contact-person/replace/prisoner/$offenderNo")

  fun stubCreatePersonMapping() {
    mappingApi.stubFor(
      post("/mapping/contact-person/person").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreatePersonMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/person").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreatePersonMappingFailureFollowedBySuccess() = mappingApi.stubMappingCreateFailureFollowedBySuccess("/mapping/contact-person/person")

  fun stubGetByNomisContactIdOrNull(
    nomisContactId: Long = 123456,
    mapping: PersonContactMappingDto? = PersonContactMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = PersonContactMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/contact/nomis-contact-id/$nomisContactId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/contact/nomis-contact-id/$nomisContactId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisContactId(
    nomisContactId: Long = 123456,
    mapping: PersonContactMappingDto = PersonContactMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = PersonContactMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisContactIdOrNull(nomisContactId, mapping)

  fun stubDeleteByNomisContactId(
    nomisContactId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/contact-person/contact/nomis-contact-id/$nomisContactId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateContactMapping() {
    mappingApi.stubFor(
      post("/mapping/contact-person/contact").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateContactMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/contact").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateContactMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/contact-person/contact")
        .inScenario("Retry Mapping Contact Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Contact Success"),
    )

    mappingApi.stubFor(
      post("/mapping/contact-person/contact")
        .inScenario("Retry Mapping Contact Scenario")
        .whenScenarioStateIs("Cause Mapping Contact Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByNomisAddressIdOrNull(
    nomisAddressId: Long = 123456,
    mapping: PersonAddressMappingDto? = PersonAddressMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/address/nomis-address-id/$nomisAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/address/nomis-address-id/$nomisAddressId")).willReturn(
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
    mapping: PersonAddressMappingDto = PersonAddressMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/address/nomis-address-id/$nomisAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    }
  }

  fun stubDeleteByNomisAddressId(
    nomisAddressId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/contact-person/address/nomis-address-id/$nomisAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreateAddressMapping() {
    mappingApi.stubFor(
      post("/mapping/contact-person/address").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateAddressMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/address").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateAddressMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/contact-person/address")
        .inScenario("Retry Mapping Address Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Address Success"),
    )

    mappingApi.stubFor(
      post("/mapping/contact-person/address")
        .inScenario("Retry Mapping Address Scenario")
        .whenScenarioStateIs("Cause Mapping Address Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }
  fun stubGetByNomisEmailIdOrNull(
    nomisInternetAddressId: Long = 123456,
    mapping: PersonEmailMappingDto? = PersonEmailMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = PersonEmailMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/email/nomis-internet-address-id/$nomisInternetAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/email/nomis-internet-address-id/$nomisInternetAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }
  fun stubGetByNomisEmailId(
    nomisInternetAddressId: Long = 123456,
    mapping: PersonEmailMappingDto = PersonEmailMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = PersonEmailMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisEmailIdOrNull(nomisInternetAddressId, mapping)

  fun stubDeleteByNomisEmailId(
    nomisInternetAddressId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/contact-person/email/nomis-internet-address-id/$nomisInternetAddressId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubCreateEmailMapping() {
    mappingApi.stubFor(
      post("/mapping/contact-person/email").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateEmailMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/email").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateEmailMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/contact-person/email")
        .inScenario("Retry Mapping Email Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Email Success"),
    )

    mappingApi.stubFor(
      post("/mapping/contact-person/email")
        .inScenario("Retry Mapping Email Scenario")
        .whenScenarioStateIs("Cause Mapping Email Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubCreatePhoneMapping() {
    mappingApi.stubFor(
      post("/mapping/contact-person/phone").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreatePhoneMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/phone").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreatePhoneMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/contact-person/phone")
        .inScenario("Retry Mapping Phone Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Phone Success"),
    )

    mappingApi.stubFor(
      post("/mapping/contact-person/phone")
        .inScenario("Retry Mapping Phone Scenario")
        .whenScenarioStateIs("Cause Mapping Phone Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByNomisPhoneIdOrNull(
    nomisPhoneId: Long = 123456,
    mapping: PersonPhoneMappingDto? = PersonPhoneMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
      mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/phone/nomis-phone-id/$nomisPhoneId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/phone/nomis-phone-id/$nomisPhoneId")).willReturn(
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
    mapping: PersonPhoneMappingDto = PersonPhoneMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
      mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisPhoneIdOrNull(nomisPhoneId, mapping)

  fun stubDeleteByNomisPhoneId(
    nomisPhoneId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/contact-person/phone/nomis-phone-id/$nomisPhoneId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateIdentifierMapping() {
    mappingApi.stubFor(
      post("/mapping/contact-person/identifier").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateIdentifierMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/identifier").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateIdentifierMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/contact-person/identifier")
        .inScenario("Retry Mapping Identifier Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Identifier Success"),
    )

    mappingApi.stubFor(
      post("/mapping/contact-person/identifier")
        .inScenario("Retry Mapping Identifier Scenario")
        .whenScenarioStateIs("Cause Mapping Identifier Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByNomisIdentifierIdsOrNull(
    nomisPersonId: Long = 123456,
    nomisSequenceNumber: Long = 4,
    mapping: PersonIdentifierMappingDto? = PersonIdentifierMappingDto(
      nomisPersonId = 123456,
      nomisSequenceNumber = 4,
      dpsId = "654321",
      mappingType = PersonIdentifierMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/identifier/nomis-person-id/$nomisPersonId/nomis-sequence-number/$nomisSequenceNumber")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/identifier/nomis-person-id/$nomisPersonId/nomis-sequence-number/$nomisSequenceNumber")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisIdentifierIds(
    nomisPersonId: Long = 123456,
    nomisSequenceNumber: Long = 4,
    mapping: PersonIdentifierMappingDto = PersonIdentifierMappingDto(
      nomisPersonId = 123456,
      nomisSequenceNumber = 4,
      dpsId = "654321",
      mappingType = PersonIdentifierMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisIdentifierIdsOrNull(nomisPersonId, nomisSequenceNumber, mapping)

  fun stubCreateEmploymentMapping() {
    mappingApi.stubFor(
      post("/mapping/contact-person/employment").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateEmploymentMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/employment").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateEmploymentMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/contact-person/employment")
        .inScenario("Retry Mapping Employment Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Employment Success"),
    )

    mappingApi.stubFor(
      post("/mapping/contact-person/employment")
        .inScenario("Retry Mapping Employment Scenario")
        .whenScenarioStateIs("Cause Mapping Employment Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByNomisEmploymentIdsOrNull(
    nomisPersonId: Long = 123456,
    nomisSequenceNumber: Long = 4,
    mapping: PersonEmploymentMappingDto? = PersonEmploymentMappingDto(
      nomisPersonId = 123456,
      nomisSequenceNumber = 4,
      dpsId = "654321",
      mappingType = PersonEmploymentMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/employment/nomis-person-id/$nomisPersonId/nomis-sequence-number/$nomisSequenceNumber")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/employment/nomis-person-id/$nomisPersonId/nomis-sequence-number/$nomisSequenceNumber")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetByNomisEmploymentIds(
    nomisPersonId: Long = 123456,
    nomisSequenceNumber: Long = 4,
    mapping: PersonEmploymentMappingDto = PersonEmploymentMappingDto(
      nomisPersonId = 123456,
      nomisSequenceNumber = 4,
      dpsId = "654321",
      mappingType = PersonEmploymentMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetByNomisEmploymentIdsOrNull(nomisPersonId, nomisSequenceNumber, mapping)

  fun stubGetByNomisContactRestrictionIdOrNull(
    nomisContactRestrictionId: Long = 123456,
    mapping: PersonContactRestrictionMappingDto? = PersonContactRestrictionMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = PersonContactRestrictionMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/contact-restriction/nomis-contact-restriction-id/$nomisContactRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/contact-restriction/nomis-contact-restriction-id/$nomisContactRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubDeleteByNomisContactRestrictionId(
    nomisContactRestrictionId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/contact-person/contact-restriction/nomis-contact-restriction-id/$nomisContactRestrictionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubDeleteByNomisIdentifierIds(
    nomisPersonId: Long = 123456,
    nomisSequenceNumber: Long = 4,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/contact-person/identifier/nomis-person-id/$nomisPersonId/nomis-sequence-number/$nomisSequenceNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }
  fun stubDeleteByNomisEmploymentIds(
    nomisPersonId: Long = 123456,
    nomisSequenceNumber: Long = 4,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/contact-person/employment/nomis-person-id/$nomisPersonId/nomis-sequence-number/$nomisSequenceNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateContactRestrictionMapping() {
    mappingApi.stubFor(
      post("/mapping/contact-person/contact-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateContactRestrictionMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/contact-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateContactRestrictionMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/contact-person/contact-restriction")
        .inScenario("Retry Mapping Contact Restriction Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Contact Restriction Success"),
    )

    mappingApi.stubFor(
      post("/mapping/contact-person/contact-restriction")
        .inScenario("Retry Mapping Contact Restriction Scenario")
        .whenScenarioStateIs("Cause Mapping Contact Restriction Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetByNomisPersonRestrictionIdOrNull(
    nomisPersonRestrictionId: Long = 123456,
    mapping: PersonRestrictionMappingDto? = PersonRestrictionMappingDto(
      nomisId = 123456,
      dpsId = "654321",
      mappingType = PersonRestrictionMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/person-restriction/nomis-person-restriction-id/$nomisPersonRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/person-restriction/nomis-person-restriction-id/$nomisPersonRestrictionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubDeleteByNomisPersonRestrictionId(
    nomisPersonRestrictionId: Long = 123456,
  ) {
    mappingApi.stubFor(
      delete(urlEqualTo("/mapping/contact-person/person-restriction/nomis-person-restriction-id/$nomisPersonRestrictionId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreatePersonRestrictionMapping() {
    mappingApi.stubFor(
      post("/mapping/contact-person/person-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreatePersonRestrictionMapping(error: DuplicateMappingErrorResponse) {
    mappingApi.stubFor(
      post("/mapping/contact-person/person-restriction").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreatePersonRestrictionMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      post("/mapping/contact-person/person-restriction")
        .inScenario("Retry Mapping Person Restriction Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause Mapping Person Restriction Success"),
    )

    mappingApi.stubFor(
      post("/mapping/contact-person/person-restriction")
        .inScenario("Retry Mapping Person Restriction Scenario")
        .whenScenarioStateIs("Cause Mapping Person Restriction Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }
  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
