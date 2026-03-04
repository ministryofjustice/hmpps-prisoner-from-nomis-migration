package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@SpringAPIServiceTest
@Import(ReligionsMappingService::class, ReligionsMappingApiMockServer::class)
class ReligionsMappingApiServiceTest {
  val errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<ReligionsMigrationMappingDto>>() {}

  @Autowired
  private lateinit var apiService: ReligionsMappingService

  @Autowired
  private lateinit var mockServer: ReligionsMappingApiMockServer

  @Nested
  inner class CreateMappingsForMigration {
    @Test
    fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      apiService.createMapping(
        ReligionsMigrationMappingDto(
          mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          cprId = "1233",
          nomisPrisonNumber = "A1234BC",
          religions = emptyList(),
        ),
        errorJavaClass = errorJavaClass,
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person-religion")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      val result = apiService.createMapping(
        ReligionsMigrationMappingDto(
          mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          cprId = "1233",
          nomisPrisonNumber = "A1234BC",
          religions = emptyList(),
        ),
        errorJavaClass = errorJavaClass,
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val cprId = "1234"
      val existingCprId = "5678"

      mockServer.stubCreateMappingsForMigration(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = ReligionsMigrationMappingDto(
              cprId = cprId,
              nomisPrisonNumber = "A1234BC",
              mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
              religions = emptyList(),
            ),
            existing = ReligionsMigrationMappingDto(
              cprId = existingCprId,
              nomisPrisonNumber = "A1234BC",
              mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
              religions = emptyList(),
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMapping(
        ReligionsMigrationMappingDto(
          mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          cprId = "1233",
          nomisPrisonNumber = "A1234BC",
          religions = emptyList(),
        ),
        errorJavaClass = errorJavaClass,
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.cprId).isEqualTo(cprId)
      assertThat(result.errorResponse.moreInfo.existing.cprId).isEqualTo(existingCprId)
    }
  }

  @Nested
  inner class GetReligionsByNomisPrisonNumber {
    val nomisPrisonNumber = "A1234BC"

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetReligionsByNomisPrisonNumber(
        nomisPrisonNumber = nomisPrisonNumber,
        mapping = ReligionsMappingDto(
          cprId = "1234",
          nomisPrisonNumber = nomisPrisonNumber,
          mappingType = ReligionsMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getReligionsByPrisonNumberOrNull(
        prisonNumber = nomisPrisonNumber,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetReligionsByNomisPrisonNumber(
        nomisPrisonNumber = nomisPrisonNumber,
        mapping = ReligionsMappingDto(
          cprId = "1234",
          nomisPrisonNumber = nomisPrisonNumber,
          mappingType = ReligionsMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getReligionsByPrisonNumberOrNull(
        prisonNumber = nomisPrisonNumber,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/core-person-religion/religions/nomis-prison-number/$nomisPrisonNumber")),
      )
    }
  }

  @Nested
  inner class CreateReligionMapping {
    @Test
    fun `will pass oath2 token to create endpoint`() = runTest {
      mockServer.stubCreateReligionMapping()

      apiService.createReligionMapping(
        ReligionMappingDto(
          mappingType = ReligionMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          cprId = "1233",
          nomisId = 3311,
          nomisPrisonNumber = "A1234BC",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person-religion/religion"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateReligionMapping()

      val result = apiService.createReligionMapping(
        ReligionMappingDto(
          mappingType = ReligionMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          cprId = "1233",
          nomisId = 3321,
          nomisPrisonNumber = "A1234BC",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val cprId = "1234"
      val existingCprId = "5678"

      mockServer.stubCreateReligionMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = ReligionMappingDto(
              cprId = cprId,
              nomisId = 4321,
              mappingType = ReligionMappingDto.MappingType.MIGRATED,
              nomisPrisonNumber = "A1234BC",
            ),
            existing = ReligionMappingDto(
              cprId = existingCprId,
              nomisId = 4321,
              mappingType = ReligionMappingDto.MappingType.MIGRATED,
              nomisPrisonNumber = "A1234BC",
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createReligionMapping(
        ReligionMappingDto(
          mappingType = ReligionMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          cprId = "1233",
          nomisId = 3321,
          nomisPrisonNumber = "A1234BC",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.cprId).isEqualTo(cprId)
      assertThat(result.errorResponse.moreInfo.existing?.cprId).isEqualTo(existingCprId)
    }
  }

  @Nested
  inner class GetReligionByNomisId {
    val nomisId = 123456L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetReligionByNomisId(
        nomisId = nomisId,
        mapping = ReligionMappingDto(
          cprId = "1234",
          nomisId = nomisId,
          mappingType = ReligionMappingDto.MappingType.MIGRATED,
          nomisPrisonNumber = "A1234BC",
        ),
      )

      apiService.getReligionByNomisId(
        nomisReligionId = nomisId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetReligionByNomisId(
        nomisId = nomisId,
        mapping = ReligionMappingDto(
          cprId = "1234",
          nomisId = nomisId,
          mappingType = ReligionMappingDto.MappingType.MIGRATED,
          nomisPrisonNumber = "A1234BC",
        ),
      )

      apiService.getReligionByNomisId(
        nomisReligionId = nomisId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/core-person-religion/religion/nomis-id/$nomisId")),
      )
    }
  }

  @Nested
  inner class DeleteReligionByNomisId {
    val nomisId = 123456L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteReligionByNomisId(
        nomisId = nomisId,
      )

      apiService.deleteReligionByNomisId(
        nomisReligionId = nomisId,
      )

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteReligionByNomisId(
        nomisId = nomisId,
      )

      apiService.deleteReligionByNomisId(
        nomisReligionId = nomisId,
      )

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/core-person-religion/religion/nomis-id/$nomisId")),
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
          urlPathMatching("/mapping/core-person-religion/migration-id/.*"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return zero when not found`(): Unit = runTest {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/core-person-religion/migration-id/.*")).willReturn(
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
