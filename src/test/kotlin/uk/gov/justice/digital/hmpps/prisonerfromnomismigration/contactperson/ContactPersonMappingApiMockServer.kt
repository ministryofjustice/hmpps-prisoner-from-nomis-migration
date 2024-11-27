package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDateTime
import java.util.*

@Component
class ContactPersonMappingApiMockServer(private val objectMapper: ObjectMapper) {

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
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/person/nomis-person-id/$nomisPersonId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
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
          .withBody(objectMapper.writeValueAsString(error)),
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
              objectMapper = objectMapper,
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
          .withBody(objectMapper.writeValueAsString(error)),
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
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/contact/nomis-contact-id/$nomisContactId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
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
          .withBody(objectMapper.writeValueAsString(error)),
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
            .withBody(objectMapper.writeValueAsString(error)),
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
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/address/nomis-address-id/$nomisAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
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
          .withBody(objectMapper.writeValueAsString(error)),
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
            .withBody(objectMapper.writeValueAsString(error)),
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
            .withBody(objectMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingApi.stubFor(
        get(urlEqualTo("/mapping/contact-person/email/nomis-internet-address-id/$nomisInternetAddressId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
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
          .withBody(objectMapper.writeValueAsString(error)),
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
            .withBody(objectMapper.writeValueAsString(error)),
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
  fun verify(pattern: RequestPatternBuilder) = mappingApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingApi.verify(count, pattern)
  fun resetAll() = mappingApi.resetAll()
}
