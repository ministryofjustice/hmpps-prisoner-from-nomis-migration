package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsByNomisIdsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IdentifierByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderAliasMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OffenderIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

@ExtendWith(MappingApiExtension::class)
@SpringAPIServiceTest
@Import(CorePersonAliasIdentifierMappingService::class, CorePersonAliasIdentifierMappingApiMockServer::class)
class CorePersonAliasIdentifierMappingServiceTest(
  @Autowired private val apiService: CorePersonAliasIdentifierMappingService,
  @Autowired private val mockServer: CorePersonAliasIdentifierMappingApiMockServer,
) {
  @Nested
  inner class GetMappingsFromNomisIds {
    private val request = CorePersonAliasIdentifierMappingsByNomisIdsRequest(
      aliases = listOf(1234567),
      identifiers = listOf(IdentifierByNomisIdRequest(nomisId = 1234567, nomisSequence = 2)),
    )

    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubGetMappingsFromNomisIds()

      apiService.getMappingsFromNomisIds(request)

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/alias-identifier/mappings/by-nomis-ids"))
          .withRequestBodyJsonPath("aliases[0]", 1234567)
          .withRequestBodyJsonPath("identifiers[0].nomisId", 1234567)
          .withRequestBodyJsonPath("identifiers[0].nomisSequence", 2),
      )
    }

    @Test
    fun `will return mappings`() = runTest {
      mockServer.stubGetMappingsFromNomisIds(
        CorePersonAliasIdentifierMappingsResult(
          aliases = listOf(
            OffenderAliasMappingDto(cprId = "cpr-alias-id", nomisOffenderId = 1234567, nomisPrisonNumber = "A1234BC", mappingType = OffenderAliasMappingDto.MappingType.MIGRATED),
          ),
          identifiers = listOf(
            OffenderIdentifierMappingDto(cprId = "cpr-identifier-id", nomisOffenderId = 1234567, nomisIdentifierSequence = 2, nomisPrisonNumber = "A1234BC", mappingType = OffenderIdentifierMappingDto.MappingType.MIGRATED),
          ),
        ),
      )

      val result = apiService.getMappingsFromNomisIds(request)

      assertThat(result.aliases.single().cprId).isEqualTo("cpr-alias-id")
      assertThat(result.identifiers.single().cprId).isEqualTo("cpr-identifier-id")
    }
  }

  private fun aliasMapping() = OffenderAliasMappingDto(
    cprId = "cpr-alias-id",
    nomisOffenderId = 1234567,
    nomisPrisonNumber = "A1234BC",
    mappingType = OffenderAliasMappingDto.MappingType.NOMIS_CREATED,
  )

  private fun identifierMapping() = OffenderIdentifierMappingDto(
    cprId = "cpr-identifier-id",
    nomisOffenderId = 1234567,
    nomisIdentifierSequence = 2,
    nomisPrisonNumber = "A1234BC",
    mappingType = OffenderIdentifierMappingDto.MappingType.NOMIS_CREATED,
  )

  @Nested
  inner class GetCorePersonByPrisonNumberOrNull {
    @Test
    fun `will pass prison number to service`() = runTest {
      mockServer.stubGetCorePersonByNomisPrisonNumberOrNull("A1234BC")

      apiService.getCorePersonByPrisonNumberOrNull("A1234BC")

      mockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/alias-identifier-migration/person/nomis-prison-number/A1234BC")))
    }

    @Test
    fun `will return mapping when it exists`() = runTest {
      mockServer.stubGetCorePersonByNomisPrisonNumberOrNull("A1234BC")

      assertThat(apiService.getCorePersonByPrisonNumberOrNull("A1234BC")!!.cprId).isEqualTo("cpr-id")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetCorePersonByNomisPrisonNumberOrNull("A1234BC", null)

      assertThat(apiService.getCorePersonByPrisonNumberOrNull("A1234BC")).isNull()
    }
  }

  @Nested
  inner class GetMigrationCount {
    @Test
    fun `will return the count of mappings for the migration`() = runTest {
      mockServer.stubGetMigrationCount(count = 56)

      assertThat(apiService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(56)

      mockServer.verify(getRequestedFor(urlPathMatching("/mapping/core-person/alias-identifier-migration/migration-id/.*")))
    }
  }

  @Nested
  inner class ReplaceMappings {
    @Test
    fun `will pass mappings to service`() = runTest {
      mockServer.stubReplaceMappings()

      apiService.replaceMappings(
        CorePersonAliasIdentifierMappingsDto(
          mappingType = CorePersonAliasIdentifierMappingsDto.MappingType.NOMIS_CREATED,
          personMapping = CorePersonMappingIdDto(cprId = "cpr-id", nomisPrisonNumber = "A1234BC"),
          aliases = listOf(aliasMapping()),
          identifiers = listOf(identifierMapping()),
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/alias-identifier/replace"))
          .withRequestBodyJsonPath("personMapping.nomisPrisonNumber", "A1234BC")
          .withRequestBodyJsonPath("aliases[0].cprId", "cpr-alias-id")
          .withRequestBodyJsonPath("identifiers[0].cprId", "cpr-identifier-id"),
      )
    }
  }

  @Nested
  inner class GetByNomisAliasIdOrNull {
    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisAliasIdOrNull(1234567)

      apiService.getByNomisAliasIdOrNull(1234567)

      mockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/alias/nomis-offender-id/1234567")))
    }

    @Test
    fun `will return mapping when it exists`() = runTest {
      mockServer.stubGetByNomisAliasIdOrNull(1234567)

      assertThat(apiService.getByNomisAliasIdOrNull(1234567)!!.nomisOffenderId).isEqualTo(1234567)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisAliasIdOrNull(1234567, null)

      assertThat(apiService.getByNomisAliasIdOrNull(1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisAliasId {
    @Test
    fun `will return mapping`() = runTest {
      mockServer.stubGetByNomisAliasIdOrNull(1234567)

      assertThat(apiService.getByNomisAliasId(1234567).nomisOffenderId).isEqualTo(1234567)
    }
  }

  @Nested
  inner class DeleteByNomisAliasId {
    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisAliasId(1234567)

      apiService.deleteByNomisAliasId(1234567)

      mockServer.verify(deleteRequestedFor(urlPathEqualTo("/mapping/core-person/alias/nomis-offender-id/1234567")))
    }
  }

  @Nested
  inner class CreateAliasMapping {
    @Test
    fun `will pass mapping to service`() = runTest {
      mockServer.stubCreateAliasMapping()

      apiService.createAliasMapping(aliasMapping())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/alias"))
          .withRequestBodyJsonPath("cprId", "cpr-alias-id")
          .withRequestBodyJsonPath("nomisOffenderId", 1234567)
          .withRequestBodyJsonPath("nomisPrisonNumber", "A1234BC")
          .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
      )
    }

    @Test
    fun `will return success result when created`() = runTest {
      mockServer.stubCreateAliasMapping()

      val result = apiService.createAliasMapping(aliasMapping())

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisOffenderId = 1234567890L
      val cprId = "cpr-alias-id"
      val existingCprId = "existing-cpr-alias-id"

      mockServer.stubCreateAliasMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = OffenderAliasMappingDto(
              cprId = cprId,
              nomisOffenderId = nomisOffenderId,
              nomisPrisonNumber = "A1234BC",
              mappingType = OffenderAliasMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = OffenderAliasMappingDto(
              cprId = existingCprId,
              nomisOffenderId = nomisOffenderId,
              nomisPrisonNumber = "A1234BC",
              mappingType = OffenderAliasMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createAliasMapping(aliasMapping())

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.cprId).isEqualTo(cprId)
      assertThat(result.errorResponse.moreInfo.existing.cprId).isEqualTo(existingCprId)
    }
  }

  @Nested
  inner class GetByNomisIdentifierIdOrNull {
    @Test
    fun `will pass NOMIS offender id and sequence to service`() = runTest {
      mockServer.stubGetByNomisIdentifierIdOrNull(1234567, 2)

      apiService.getByNomisIdentifierIdOrNull(1234567, 2)

      mockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/identifier/nomis-offender-id/1234567/nomis-identifier-sequence/2")))
    }

    @Test
    fun `will return mapping when it exists`() = runTest {
      mockServer.stubGetByNomisIdentifierIdOrNull(1234567, 2)

      with(apiService.getByNomisIdentifierIdOrNull(1234567, 2)!!) {
        assertThat(nomisOffenderId).isEqualTo(1234567)
        assertThat(nomisIdentifierSequence).isEqualTo(2)
      }
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisIdentifierIdOrNull(1234567, 2, null)

      assertThat(apiService.getByNomisIdentifierIdOrNull(1234567, 2)).isNull()
    }
  }

  @Nested
  inner class GetByNomisIdentifierId {
    @Test
    fun `will return mapping`() = runTest {
      mockServer.stubGetByNomisIdentifierIdOrNull(1234567, 2)

      assertThat(apiService.getByNomisIdentifierId(1234567, 2).cprId).isEqualTo("cpr-identifier-id")
    }
  }

  @Nested
  inner class GetIdentifierMappingsByNomisOffenderId {
    @Test
    fun `will pass NOMIS offender id to service`() = runTest {
      mockServer.stubGetIdentifierMappingsByNomisOffenderId(1234567)

      apiService.getIdentifierMappingsByNomisOffenderId(1234567)

      mockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/identifier/nomis-offender-id/1234567")))
    }

    @Test
    fun `will return the mappings`() = runTest {
      mockServer.stubGetIdentifierMappingsByNomisOffenderId(
        1234567,
        listOf(
          OffenderIdentifierMappingDto(
            cprId = "cpr-identifier-1",
            nomisOffenderId = 1234567,
            nomisIdentifierSequence = 1,
            nomisPrisonNumber = "A1234BC",
            mappingType = OffenderIdentifierMappingDto.MappingType.MIGRATED,
          ),
          OffenderIdentifierMappingDto(
            cprId = "cpr-identifier-2",
            nomisOffenderId = 1234567,
            nomisIdentifierSequence = 2,
            nomisPrisonNumber = "A1234BC",
            mappingType = OffenderIdentifierMappingDto.MappingType.NOMIS_CREATED,
          ),
        ),
      )

      val mappings = apiService.getIdentifierMappingsByNomisOffenderId(1234567)

      assertThat(mappings.map { it.nomisIdentifierSequence to it.cprId }).containsExactly(
        1 to "cpr-identifier-1",
        2 to "cpr-identifier-2",
      )
    }

    @Test
    fun `will return an empty list when there are no mappings`() = runTest {
      mockServer.stubGetIdentifierMappingsByNomisOffenderId(1234567, emptyList())

      assertThat(apiService.getIdentifierMappingsByNomisOffenderId(1234567)).isEmpty()
    }
  }

  @Nested
  inner class ReplaceIdentifierMappings {
    @Test
    fun `will pass NOMIS offender id and mappings to service`() = runTest {
      mockServer.stubReplaceIdentifierMappings(1234567)

      apiService.replaceIdentifierMappings(
        1234567,
        listOf(
          OffenderIdentifierMappingDto(
            cprId = "cpr-identifier-1",
            nomisOffenderId = 1234567,
            nomisIdentifierSequence = 1,
            nomisPrisonNumber = "A1234BC",
            mappingType = OffenderIdentifierMappingDto.MappingType.NOMIS_CREATED,
          ),
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/identifier/nomis-offender-id/1234567/replace"))
          .withRequestBodyJsonPath("$[0].cprId", "cpr-identifier-1")
          .withRequestBodyJsonPath("$[0].nomisOffenderId", "1234567")
          .withRequestBodyJsonPath("$[0].nomisIdentifierSequence", "1")
          .withRequestBodyJsonPath("$[0].nomisPrisonNumber", "A1234BC")
          .withRequestBodyJsonPath("$[0].mappingType", "NOMIS_CREATED"),
      )
    }

    @Test
    fun `will pass an empty list to service`() = runTest {
      mockServer.stubReplaceIdentifierMappings(1234567)

      apiService.replaceIdentifierMappings(1234567, emptyList())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/identifier/nomis-offender-id/1234567/replace"))
          .withRequestBody(equalToJson("[]")),
      )
    }
  }

  @Nested
  inner class DeleteByNomisIdentifierId {
    @Test
    fun `will pass NOMIS offender id and sequence to service`() = runTest {
      mockServer.stubDeleteByNomisIdentifierId(1234567, 2)

      apiService.deleteByNomisIdentifierId(1234567, 2)

      mockServer.verify(deleteRequestedFor(urlPathEqualTo("/mapping/core-person/identifier/nomis-offender-id/1234567/nomis-identifier-sequence/2")))
    }
  }

  @Nested
  inner class CreateIdentifierMapping {
    @Test
    fun `will pass mapping to service`() = runTest {
      mockServer.stubCreateIdentifierMapping()

      apiService.createIdentifierMapping(identifierMapping())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/identifier"))
          .withRequestBodyJsonPath("cprId", "cpr-identifier-id")
          .withRequestBodyJsonPath("nomisOffenderId", 1234567)
          .withRequestBodyJsonPath("nomisIdentifierSequence", 2)
          .withRequestBodyJsonPath("nomisPrisonNumber", "A1234BC")
          .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
      )
    }

    @Test
    fun `will return success result when created`() = runTest {
      mockServer.stubCreateIdentifierMapping()

      val result = apiService.createIdentifierMapping(identifierMapping())

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisOffenderId = 1234567890L
      val cprId = "cpr-identifier-id"
      val existingCprId = "existing-cpr-identifier-id"

      mockServer.stubCreateIdentifierMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = OffenderIdentifierMappingDto(
              cprId = cprId,
              nomisOffenderId = nomisOffenderId,
              nomisIdentifierSequence = 2,
              nomisPrisonNumber = "A1234BC",
              mappingType = OffenderIdentifierMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = OffenderIdentifierMappingDto(
              cprId = existingCprId,
              nomisOffenderId = nomisOffenderId,
              nomisIdentifierSequence = 2,
              nomisPrisonNumber = "A1234BC",
              mappingType = OffenderIdentifierMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createIdentifierMapping(identifierMapping())

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.cprId).isEqualTo(cprId)
      assertThat(result.errorResponse.moreInfo.existing.cprId).isEqualTo(existingCprId)
    }
  }
}
