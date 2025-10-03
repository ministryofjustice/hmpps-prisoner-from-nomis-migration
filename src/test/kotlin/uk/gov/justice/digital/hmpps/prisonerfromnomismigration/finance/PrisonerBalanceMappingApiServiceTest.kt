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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto.MappingType.NOMIS_CREATED

@SpringAPIServiceTest
@Import(PrisonerBalanceMappingApiService::class, PrisonerBalanceMappingApiMockServer::class)
class PrisonerBalanceMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: PrisonerBalanceMappingApiService

  @Autowired
  private lateinit var mockServer: PrisonerBalanceMappingApiMockServer

  @Nested
  inner class PrisonerBalance {
    @Nested
    inner class GetByNomisIdOrNull {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetPrisonerBalanceByNomisIdOrNull(nomisRootOffenderId = 12345L)

        apiService.getByNomisIdOrNull(nomisId = 12345L)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS id to service`() = runTest {
        mockServer.stubGetPrisonerBalanceByNomisIdOrNull()

        apiService.getByNomisIdOrNull(nomisId = 12345L)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/prisoner-balance/nomis-id/12345")),
        )
      }

      @Test
      fun `will return dpsId when mapping exists`() = runTest {
        mockServer.stubGetPrisonerBalanceByNomisIdOrNull(
          nomisRootOffenderId = 12345L,
          mapping = PrisonerBalanceMappingDto(
            dpsId = "A1234BC",
            nomisRootOffenderId = 12345L,
            mappingType = MIGRATED,
          ),
        )

        val mapping = apiService.getByNomisIdOrNull(nomisId = 12345L)!!

        assertThat(mapping.dpsId).isEqualTo("A1234BC")
      }

      @Test
      fun `will return null if mapping does not exist`() = runTest {
        mockServer.stubGetPrisonerBalanceByNomisIdOrNull(
          nomisRootOffenderId = 12345L,
          mapping = null,
        )

        assertThat(apiService.getByNomisIdOrNull(nomisId = 12345L)).isNull()
      }
    }

    @Nested
    inner class GetByNomisId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByNomisId(nomisRootOffenderId = 12345L)

        apiService.getByNomisId(nomisId = 12345L)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS id to service`() = runTest {
        mockServer.stubGetByNomisId(nomisRootOffenderId = 12345L)

        apiService.getByNomisId(nomisId = 12345L)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/prisoner-balance/nomis-id/12345")),
        )
      }

      @Test
      fun `will return dpsId`() = runTest {
        mockServer.stubGetByNomisId(
          nomisRootOffenderId = 12345L,
          mapping = PrisonerBalanceMappingDto(
            dpsId = "A1234BC",
            nomisRootOffenderId = 12345L,
            mappingType = MIGRATED,
          ),
        )

        val mapping = apiService.getByNomisId(nomisId = 12345L)

        assertThat(mapping.dpsId).isEqualTo("A1234BC")
      }
    }

    @Nested
    inner class CreateMapping {
      @Test
      internal fun `will pass oath2 token to migrate endpoint`() = runTest {
        mockServer.stubCreateMappingsForMigration()

        apiService.createMapping(
          PrisonerBalanceMappingDto(
            mappingType = MIGRATED,
            label = "2020-01-01T10:00",
            dpsId = "A1234KT",
            nomisRootOffenderId = 12345L,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonerBalanceMappingDto>>() {},
        )

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/prisoner-balance")).withHeader(
            "Authorization",
            equalTo("Bearer ABCDE"),
          ),
        )
      }

      @Test
      fun `will return success when OK response`() = runTest {
        mockServer.stubCreateMappingsForMigration()

        val result = apiService.createMapping(
          PrisonerBalanceMappingDto(
            mappingType = MIGRATED,
            label = "2020-01-01T10:00",
            dpsId = "A1234KT",
            nomisRootOffenderId = 12345L,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonerBalanceMappingDto>>() {},
        )

        assertThat(result.isError).isFalse()
      }

      @Test
      fun `will return error when 409 conflict`() = runTest {
        val dpsId = "A4321BC"
        val nomisId = 12345L
        val existingDpsId = "A4321BC"

        mockServer.stubCreateMappingsForMigration(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = PrisonerBalanceMappingDto(
                dpsId = dpsId,
                nomisRootOffenderId = nomisId,
                mappingType = NOMIS_CREATED,
              ),
              existing = PrisonerBalanceMappingDto(
                dpsId = existingDpsId,
                nomisRootOffenderId = nomisId,
                mappingType = NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = _409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        val result = apiService.createMapping(
          PrisonerBalanceMappingDto(
            mappingType = MIGRATED,
            label = "2020-01-01T10:00",
            dpsId = "A1234KT",
            nomisRootOffenderId = 12345L,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonerBalanceMappingDto>>() {},
        )

        assertThat(result.isError).isTrue()
        assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
        assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
      }
    }

    @Nested
    inner class GetMigrationDetails {
      @Test
      fun `will call the prisoner balance mapping endpoint`() = runTest {
        mockServer.stubGetMigrationDetails(migrationId = "2020-01-01T10%3A00")

        apiService.getMigrationDetails(migrationId = "2020-01-01T10:00")

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/prisoner-balance/migration-id/2020-01-01T10%3A00")),
        )
      }
    }
  }
}
