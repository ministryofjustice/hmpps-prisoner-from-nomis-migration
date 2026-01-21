package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@SpringAPIServiceTest
@Import(OfficialVisitsMappingService::class, OfficialVisitsMappingApiMockServer::class)
class OfficialVisitsMappingApiServiceTest {

  @Autowired
  private lateinit var apiService: OfficialVisitsMappingService

  @Autowired
  private lateinit var mockServer: OfficialVisitsMappingApiMockServer

  @Nested
  inner class CreateMappingsForMigration {
    val errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<OfficialVisitMigrationMappingDto>>() {}

    @Test
    fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      apiService.createMapping(
        OfficialVisitMigrationMappingDto(
          mappingType = OfficialVisitMigrationMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisId = 54321L,
          visitors = emptyList(),
        ),
        errorJavaClass = errorJavaClass,
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/official-visits")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      val result = apiService.createMapping(
        OfficialVisitMigrationMappingDto(
          mappingType = OfficialVisitMigrationMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisId = 54321L,
          visitors = emptyList(),
        ),
        errorJavaClass = errorJavaClass,
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val dpsId = "1234"
      val existingDpsId = "5678"

      mockServer.stubCreateMappingsForMigration(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = OfficialVisitMigrationMappingDto(
              dpsId = dpsId,
              nomisId = 54321L,
              visitors = emptyList(),
              mappingType = OfficialVisitMigrationMappingDto.MappingType.MIGRATED,
            ),
            existing = OfficialVisitMigrationMappingDto(
              dpsId = existingDpsId,
              nomisId = 54321L,
              visitors = emptyList(),
              mappingType = OfficialVisitMigrationMappingDto.MappingType.MIGRATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMapping(
        OfficialVisitMigrationMappingDto(
          mappingType = OfficialVisitMigrationMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisId = 54321L,
          visitors = emptyList(),
        ),
        errorJavaClass = errorJavaClass,
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class CreateVisitMapping {

    @Test
    fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateVisitMapping()

      apiService.createVisitMapping(
        OfficialVisitMappingDto(
          mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisId = 54321L,
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/official-visits/visit")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateVisitMapping()

      val result = apiService.createVisitMapping(
        OfficialVisitMappingDto(
          mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisId = 54321L,
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val dpsId = "1234"
      val existingDpsId = "5678"

      mockServer.stubCreateVisitMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = OfficialVisitMappingDto(
              dpsId = dpsId,
              nomisId = 54321L,
              mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
            ),
            existing = OfficialVisitMappingDto(
              dpsId = existingDpsId,
              nomisId = 54321L,
              mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createVisitMapping(
        OfficialVisitMappingDto(
          mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisId = 54321L,
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing?.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class GetByVisitNomisIdsOrNull {
    val nomisVisitId = 12345L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByVisitNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = nomisVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getByVisitNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByVisitNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = nomisVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getByVisitNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/official-visits/visit/nomis-id/$nomisVisitId")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByVisitNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = "1234",
          nomisId = nomisVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByVisitNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
      )

      assertThat(mapping?.dpsId).isEqualTo("1234")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByVisitNomisIdsOrNull(
        nomisVisitId = nomisVisitId,
        mapping = null,
      )

      assertThat(
        apiService.getByVisitNomisIdsOrNull(
          nomisVisitId = nomisVisitId,
        ),
      ).isNull()
    }
  }

  @Nested
  inner class CreateVisitorMapping {

    @Test
    fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateVisitorMapping()

      apiService.createVisitorMapping(
        OfficialVisitorMappingDto(
          mappingType = OfficialVisitorMappingDto.MappingType.NOMIS_CREATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisId = 54321L,
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateVisitorMapping()

      val result = apiService.createVisitorMapping(
        OfficialVisitorMappingDto(
          mappingType = OfficialVisitorMappingDto.MappingType.NOMIS_CREATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisId = 54321L,
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val dpsId = "1234"
      val existingDpsId = "5678"

      mockServer.stubCreateVisitorMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = OfficialVisitorMappingDto(
              dpsId = dpsId,
              nomisId = 54321L,
              mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
            ),
            existing = OfficialVisitorMappingDto(
              dpsId = existingDpsId,
              nomisId = 54321L,
              mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createVisitorMapping(
        OfficialVisitorMappingDto(
          mappingType = OfficialVisitorMappingDto.MappingType.NOMIS_CREATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisId = 54321L,
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing?.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class GetByVisitorNomisIdsOrNull {
    val nomisVisitorId = 12345L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByVisitorNomisIdsOrNull(
        nomisVisitorId = nomisVisitorId,
        mapping = OfficialVisitorMappingDto(
          dpsId = "1234",
          nomisId = nomisVisitorId,
          mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getByVisitorNomisIdsOrNull(
        nomisVisitorId = nomisVisitorId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByVisitorNomisIdsOrNull(
        nomisVisitorId = nomisVisitorId,
        mapping = OfficialVisitorMappingDto(
          dpsId = "1234",
          nomisId = nomisVisitorId,
          mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getByVisitorNomisIdsOrNull(
        nomisVisitorId = nomisVisitorId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor/nomis-id/$nomisVisitorId")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByVisitorNomisIdsOrNull(
        nomisVisitorId = nomisVisitorId,
        mapping = OfficialVisitorMappingDto(
          dpsId = "1234",
          nomisId = nomisVisitorId,
          mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByVisitorNomisIdsOrNull(
        nomisVisitorId = nomisVisitorId,
      )

      assertThat(mapping?.dpsId).isEqualTo("1234")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByVisitorNomisIdsOrNull(
        nomisVisitorId = nomisVisitorId,
        mapping = null,
      )

      assertThat(
        apiService.getByVisitorNomisIdsOrNull(
          nomisVisitorId = nomisVisitorId,
        ),
      ).isNull()
    }
  }

  @Nested
  inner class GetInternalLocationByNomisId {
    val nomisLocationId = 1234L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetInternalLocationByNomisId(
        nomisLocationId = nomisLocationId,
      )

      apiService.getInternalLocationByNomisId(
        nomisLocationId = nomisLocationId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetInternalLocationByNomisId(
        nomisLocationId = nomisLocationId,
      )

      apiService.getInternalLocationByNomisId(
        nomisLocationId = nomisLocationId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/locations/nomis/$nomisLocationId")),
      )
    }
  }

  @Nested
  inner class GetMigrationCount {
    @BeforeEach
    fun setUp() {
      mockServer.stubGetMigrationCount("2020-01-01T10:00:00", count = 56_766)
    }

    @Test
    fun `will supply authentication token`(): Unit = runTest {
      apiService.getMigrationCount("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/official-visits/migration-id/.*"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return zero when not found`(): Unit = runTest {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/official-visits/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(apiService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(0)
    }

    @Test
    fun `will return the mapping count when found`(): Unit = runTest {
      assertThat(apiService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(56_766)
    }
  }
}
