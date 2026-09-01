package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.NomisIdentifierId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconAliasMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.SysconIdentifierMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CoreOffender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Identifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate

class CorePersonDataRepairResourceIntTest(
  @Autowired private val nomisApiMockServer: CorePersonNomisApiMockServer,
  @Autowired private val mappingApiMockServer: CorePersonMappingApiMockServer,
) : CorePersonIntegrationTestBase() {

  private val cprApiMock = CorePersonCprApiExtension.cprCorePersonServer

  @DisplayName("POST /prisoners/{prisonNumber}/core-person/aliases-identifiers/repair")
  @Nested
  inner class RepairCorePersonAliasesAndIdentifiers {
    val prisonNumber = "A1234KT"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/aliases-identifiers/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/aliases-identifiers/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/aliases-identifiers/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      private val prisonNumber = "A1234KT"
      private val aliasesAndIdentifiers = listOf(
        CoreOffender(
          offenderId = 10000L,
          firstName = "first",
          lastName = "last",
          workingName = true,
          title = CodeDescription("MRS", "Mrs"),
          birthPlace = "Sheffield",
          birthCountry = CodeDescription("UKR", "United Kingdom"),
          sex = CodeDescription("F", "Female"),
          ethnicity = CodeDescription("A1", "A1"),
          createDate = LocalDate.of(2001, 3, 3),
          middleName1 = "middle1",
          identifiers = listOf(
            Identifier(
              offenderId = 10000L,
              sequence = 1,
              type = CodeDescription("PNC", "PNC Number"),
              identifier = "20/0071818T",
              verified = true,
              issuedAuthority = "DVLA",
              issuedDate = LocalDate.of(2002, 2, 2),
            ),
          ),
        ),
      )

      private val aliasesMapping = listOf(
        SysconAliasMapping(
          nomisOffenderId = 10000L,
          cprAliasId = "7981274e-bcb6-4879-9712-4164743f2ee4",
        ),
      )
      private val identifiersMapping = listOf(
        SysconIdentifierMapping(
          nomisIdentifierId = NomisIdentifierId(
            nomisOffenderId = 10000L,
            nomisSequence = 1,
          ),
          cprIdentifierId = "84cdb577-63da-44e6-9293-150dc7d5cd14",
        ),
      )

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetAliasesAndIdentifiers(prisonNumber, aliasesAndIdentifiers = aliasesAndIdentifiers)
        cprApiMock.stubMigrateAliasesAndIdentifiers(
          nomisPrisonNumber = prisonNumber,
          aliasMappings = aliasesMapping,
          identifierMappings = identifiersMapping,
        )
        mappingApiMockServer.stubReplaceMappings()

        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/aliases-identifiers/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will retrieve current aliases and identifiers for the prisoner`() {
        nomisApiMockServer.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/core-person/$prisonNumber")))
      }

      @Test
      fun `will send aliases and identifiers to CPR`() {
        cprApiMock.verify(
          WireMock.postRequestedFor(WireMock.urlPathEqualTo("/syscon-sync/aliases-identifiers/$prisonNumber"))
            .withRequestBodyJsonPath("aliases[0].nomisOffenderId", aliasesAndIdentifiers[0].offenderId)
            .withRequestBodyJsonPath("aliases[0].titleCode", "MRS")
            .withRequestBodyJsonPath("aliases[0].firstName", "first")
            .withRequestBodyJsonPath("aliases[0].middleNames", "middle1")
            .withRequestBodyJsonPath("aliases[0].lastName", "last")
            .withRequestBodyJsonPath("aliases[0].birthPlace", "Sheffield")
            .withRequestBodyJsonPath("aliases[0].birthCountry", "UKR")
            .withRequestBodyJsonPath("aliases[0].sexCode", "F")
            .withRequestBodyJsonPath("aliases[0].isPrimary", true)
            .withRequestBodyJsonPath("aliases[0].ethnicity", "A1")
            .withRequestBodyJsonPath("aliases[0].createDate", LocalDate.of(2001, 3, 3))
            .withRequestBodyJsonPath(
              "identifiers[0].nomisIdentifierId.nomisOffenderId",
              aliasesAndIdentifiers[0].offenderId,
            )
            .withRequestBodyJsonPath("identifiers[0].nomisIdentifierId.nomisSequence", 1)
            .withRequestBodyJsonPath("identifiers[0].type", "PNC")
            .withRequestBodyJsonPath("identifiers[0].value", "20/0071818T")
            .withRequestBodyJsonPath("identifiers[0].verified", true)
            .withRequestBodyJsonPath("identifiers[0].issuedAuthority", "DVLA")
            .withRequestBodyJsonPath("identifiers[0].issuedDate", LocalDate.of(2002, 2, 2)),
        )
      }

      @Test
      fun `will replace mappings with the correct mapping type`() {
        mappingApiMockServer.verify(
          WireMock.postRequestedFor(
            WireMock.urlPathEqualTo("/mapping/core-person/replace"),
          ).withRequestBodyJsonPath("mappingType", CorePersonMappingsDto.MappingType.NOMIS_CREATED.toString())
            .withRequestBodyJsonPath(
              "aliases[0].mappingType",
              OffenderAliasMappingDto.MappingType.NOMIS_CREATED.toString(),
            )
            .withRequestBodyJsonPath(
              "identifiers[0].mappingType",
              OffenderIdentifierMappingDto.MappingType.NOMIS_CREATED.toString(),
            ),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          ArgumentMatchers.eq("core-person-aliases-identifiers-resynchronisation-repair"),
          check {
            Assertions.assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathNotFound {
      val prisonNumber = "A1234KT"

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetCorePerson(prisonNumber, status = HttpStatus.NOT_FOUND)

        webTestClient.post().uri("/prisoners/$prisonNumber/core-person/aliases-identifiers/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
      }

      @Test
      fun `will try to retrieve current aliases and identifiers for the prisoner`() {
        nomisApiMockServer.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/core-person/$prisonNumber")))
      }

      @Test
      fun `will not send aliases and identifiers to CPR`() {
        cprApiMock.verify(0, WireMock.postRequestedFor(WireMock.anyUrl()))
      }

      @Test
      fun `will not track telemetry for the repair`() {
        verifyNoInteractions(telemetryClient)
      }
    }
  }
}
