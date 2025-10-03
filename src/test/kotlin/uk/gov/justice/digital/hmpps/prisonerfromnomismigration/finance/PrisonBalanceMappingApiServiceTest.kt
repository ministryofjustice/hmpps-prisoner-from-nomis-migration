package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse.Status._409_CONFLICT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonBalanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonBalanceMappingDto.MappingType.NOMIS_CREATED

@SpringAPIServiceTest
@Import(PrisonBalanceMappingApiService::class, PrisonBalanceMappingApiMockServer::class)
class PrisonBalanceMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: PrisonBalanceMappingApiService

  @Autowired
  private lateinit var mockServer: PrisonBalanceMappingApiMockServer

  @Nested
  inner class PrisonBalance {
    @Nested
    inner class GetByNomisIdOrNull {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetPrisonBalanceByNomisIdOrNull(nomisId = "LEI")

        apiService.getByNomisIdOrNull(nomisId = "LEI")

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS id to service`() = runTest {
        mockServer.stubGetPrisonBalanceByNomisIdOrNull()

        apiService.getByNomisIdOrNull(nomisId = "MDI")

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/prison-balance/nomis-id/MDI")),
        )
      }

      @Test
      fun `will return dpsId when mapping exists`() = runTest {
        mockServer.stubGetPrisonBalanceByNomisIdOrNull(
          nomisId = "LEI",
          mapping = PrisonBalanceMappingDto(
            dpsId = "LEI",
            nomisId = "MDI",
            mappingType = MIGRATED,
          ),
        )

        val mapping = apiService.getByNomisIdOrNull(nomisId = "LEI")!!

        assertThat(mapping.dpsId).isEqualTo("LEI")
      }

      @Test
      fun `will return null if mapping does not exist`() = runTest {
        mockServer.stubGetPrisonBalanceByNomisIdOrNull(
          nomisId = "LEI",
          mapping = null,
        )

        assertThat(apiService.getByNomisIdOrNull(nomisId = "LEI")).isNull()
      }
    }

    @Nested
    inner class GetByNomisId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByNomisId(nomisId = "MDI")

        apiService.getByNomisId(nomisId = "MDI")

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS id to service`() = runTest {
        mockServer.stubGetByNomisId(nomisId = "MDI")

        apiService.getByNomisId(nomisId = "MDI")

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/prison-balance/nomis-id/MDI")),
        )
      }

      @Test
      fun `will return dpsId`() = runTest {
        mockServer.stubGetByNomisId(
          nomisId = "MDI",
          mapping = PrisonBalanceMappingDto(
            dpsId = "LEI",
            nomisId = "MDI",
            mappingType = MIGRATED,
          ),
        )

        val mapping = apiService.getByNomisId(nomisId = "MDI")

        assertThat(mapping.dpsId).isEqualTo("LEI")
      }
    }

    @Nested
    inner class CreateMapping {
      @Test
      internal fun `will pass oath2 token to migrate endpoint`() = runTest {
        mockServer.stubCreateMappingsForMigration()

        apiService.createMapping(
          PrisonBalanceMappingDto(
            mappingType = MIGRATED,
            label = "2020-01-01T10:00",
            dpsId = "A1234KT",
            nomisId = "MDI",
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonBalanceMappingDto>>() {},
        )

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/prison-balance")).withHeader(
            "Authorization",
            equalTo("Bearer ABCDE"),
          ),
        )
      }

      @Test
      fun `will return success when OK response`() = runTest {
        mockServer.stubCreateMappingsForMigration()

        val result = apiService.createMapping(
          PrisonBalanceMappingDto(
            mappingType = MIGRATED,
            label = "2020-01-01T10:00",
            dpsId = "A1234KT",
            nomisId = "MDI",
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonBalanceMappingDto>>() {},
        )

        assertThat(result.isError).isFalse()
      }

      @Test
      fun `will return error when 409 conflict`() = runTest {
        val dpsId = "A4321BC"
        val nomisId = "MDI"
        val existingDpsId = "A4321BC"

        mockServer.stubCreateMappingsForMigration(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PrisonBalanceMappingDto(
                dpsId = dpsId,
                nomisId = nomisId,
                mappingType = NOMIS_CREATED,
              ),
              existing = PrisonBalanceMappingDto(
                dpsId = existingDpsId,
                nomisId = nomisId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = _409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        val result = apiService.createMapping(
          PrisonBalanceMappingDto(
            mappingType = MIGRATED,
            label = "2020-01-01T10:00",
            dpsId = "A1234KT",
            nomisId = "MDI",
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonBalanceMappingDto>>() {},
        )

        assertThat(result.isError).isTrue()
        assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
        assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
      }
    }

    @Nested
    inner class GetMigrationDetails {
      @Test
      fun `will call the prison balance mapping endpoint`() = runTest {
        mockServer.stubGetMigrationDetails(migrationId = "2020-01-01T10%3A00")

        apiService.getMigrationDetails(migrationId = "2020-01-01T10:00")

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/prison-balance/migration-id/2020-01-01T10%3A00")),
        )
      }
    }
  }
}
