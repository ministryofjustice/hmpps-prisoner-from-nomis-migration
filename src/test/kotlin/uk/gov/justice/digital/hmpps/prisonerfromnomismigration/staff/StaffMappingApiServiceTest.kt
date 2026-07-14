package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

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
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.StaffMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@ExtendWith(MappingApiExtension::class)
@SpringAPIServiceTest
@Import(StaffMappingApiService::class, StaffMappingApiMockServer::class)
class StaffMappingApiServiceTest {

  @Autowired
  private lateinit var apiService: StaffMappingApiService

  @Autowired
  private lateinit var mockServer: StaffMappingApiMockServer

  @Nested
  inner class CreateMapping {
    val errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<StaffMappingDto>>() {}

    @Test
    fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateMapping()

      apiService.createMapping(
        StaffMappingDto(
          mappingType = StaffMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisId = 54321L,
        ),
        errorJavaClass = errorJavaClass,
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/staff")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateMapping()

      val result = apiService.createMapping(
        StaffMappingDto(
          mappingType = StaffMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisId = 54321L,
        ),
        errorJavaClass = errorJavaClass,
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val dpsId = "1234"
      val existingDpsId = "5678"

      mockServer.stubCreateMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = StaffMappingDto(
              dpsId = dpsId,
              nomisId = 54321L,
              mappingType = StaffMappingDto.MappingType.MIGRATED,
            ),
            existing = StaffMappingDto(
              dpsId = existingDpsId,
              nomisId = 54321L,
              mappingType = StaffMappingDto.MappingType.MIGRATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMapping(
        StaffMappingDto(
          mappingType = StaffMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisId = 54321L,
        ),
        errorJavaClass = errorJavaClass,
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class GetByNomisStaffIdOrNull {
    val nomisStaffId = 12345L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetStaffByNomisIdOrNull(
        nomisId = nomisStaffId,
        mapping = StaffMappingDto(
          dpsId = "1234",
          nomisId = nomisStaffId,
          mappingType = StaffMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getByNomisStaffIdOrNull(nomisStaffId = nomisStaffId)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetStaffByNomisIdOrNull(
        nomisId = nomisStaffId,
        mapping = StaffMappingDto(
          dpsId = "1234",
          nomisId = nomisStaffId,
          mappingType = StaffMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getByNomisStaffIdOrNull(nomisStaffId = nomisStaffId)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/staff/nomis-id/$nomisStaffId")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetStaffByNomisIdOrNull(
        nomisId = nomisStaffId,
        mapping = StaffMappingDto(
          dpsId = "1234",
          nomisId = nomisStaffId,
          mappingType = StaffMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisStaffIdOrNull(nomisStaffId = nomisStaffId)

      assertThat(mapping?.dpsId).isEqualTo("1234")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetStaffByNomisIdOrNull(
        nomisId = nomisStaffId,
        mapping = null,
      )

      assertThat(apiService.getByNomisStaffIdOrNull(nomisStaffId = nomisStaffId)).isNull()
    }
  }

  @Nested
  inner class GetPagedModelMigrationCount {
    @BeforeEach
    fun setUp() {
      mockServer.stubGetPagedModelMigrationCount("2020-01-01T10:00:00", count = 56_766)
    }

    @Test
    fun `will supply authentication token`(): Unit = runTest {
      apiService.getPagedModelMigrationCount("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/staff/migration-id/.*"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return zero when not found`(): Unit = runTest {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/staff/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(apiService.getPagedModelMigrationCount("2020-01-01T10:00:00")).isEqualTo(0)
    }

    @Test
    fun `will return the mapping count when found`(): Unit = runTest {
      assertThat(apiService.getPagedModelMigrationCount("2020-01-01T10:00:00")).isEqualTo(56_766)
    }
  }
}
