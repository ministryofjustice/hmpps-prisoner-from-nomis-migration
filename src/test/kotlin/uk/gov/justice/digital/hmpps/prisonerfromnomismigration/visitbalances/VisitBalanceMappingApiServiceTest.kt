package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse.Status._409_CONFLICT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto.MappingType.NOMIS_CREATED

@SpringAPIServiceTest
@Import(VisitBalanceMappingApiService::class, VisitBalanceMappingApiMockServer::class)
class VisitBalanceMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitBalanceMappingApiService

  @Autowired
  private lateinit var mockServer: VisitBalanceMappingApiMockServer

  @Nested
  inner class GetByNomisVisitBalanceIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisIdOrNull(nomisVisitBalanceId = 12345L)

      apiService.getByNomisVisitBalanceIdOrNull(nomisVisitBalanceId = 12345L)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisIdOrNull()

      apiService.getByNomisVisitBalanceIdOrNull(nomisVisitBalanceId = 12345L)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/visit-balance/nomis-id/12345")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisIdOrNull(
        nomisVisitBalanceId = 12345L,
        mapping = VisitBalanceMappingDto(
          dpsId = "1234567",
          nomisVisitBalanceId = 12345L,
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisVisitBalanceIdOrNull(nomisVisitBalanceId = 12345L)!!

      assertThat(mapping.dpsId).isEqualTo("1234567")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisIdOrNull(
        nomisVisitBalanceId = 12345L,
        mapping = null,
      )

      assertThat(apiService.getByNomisVisitBalanceIdOrNull(nomisVisitBalanceId = 12345L)).isNull()
    }
  }

  @Nested
  inner class GetByNomisVisitBalanceId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisId(nomisVisitBalanceId = 12345L)

      apiService.getByNomisVisitBalanceId(nomisVisitBalanceId = 12345L)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisId(nomisVisitBalanceId = 12345L)

      apiService.getByNomisVisitBalanceId(nomisVisitBalanceId = 12345L)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/visit-balance/nomis-id/12345")),
      )
    }

    @Test
    fun `will return dpsId`() = runTest {
      mockServer.stubGetByNomisId(
        nomisVisitBalanceId = 12345L,
        mapping = VisitBalanceMappingDto(
          dpsId = "1234567",
          nomisVisitBalanceId = 12345L,
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisVisitBalanceId(nomisVisitBalanceId = 12345L)

      assertThat(mapping.dpsId).isEqualTo("1234567")
    }
  }

  @Nested
  inner class CreateMapping {
    @Test
    internal fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      apiService.createMapping(
        VisitBalanceMappingDto(
          mappingType = MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "A1234KT",
          nomisVisitBalanceId = 12345L,
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/visit-balance")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      val result = apiService.createMapping(
        VisitBalanceMappingDto(
          mappingType = MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "A1234KT",
          nomisVisitBalanceId = 12345L,
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val dpsId = "A4321BC"
      val nomisVisitBalanceId = 12345L
      val existingDpsId = "A4321BC"

      mockServer.stubCreateMappingsForMigration(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = VisitBalanceMappingDto(
              dpsId = dpsId,
              nomisVisitBalanceId = nomisVisitBalanceId,
              mappingType = NOMIS_CREATED,
            ),
            existing = VisitBalanceMappingDto(
              dpsId = existingDpsId,
              nomisVisitBalanceId = nomisVisitBalanceId,
              mappingType = NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = _409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMapping(
        VisitBalanceMappingDto(
          mappingType = MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "A1234KT",
          nomisVisitBalanceId = 12345L,
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class GetMigrationDetails {
    @Test
    fun `will call the vist balance mapping endpoint`() = runTest {
      mockServer.stubGetMigrationDetails(migrationId = "2020-01-01T10%3A00")

      apiService.getMigrationDetails(migrationId = "2020-01-01T10:00")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/visit-balance/migration-id/2020-01-01T10%3A00")),
      )
    }
  }
}
