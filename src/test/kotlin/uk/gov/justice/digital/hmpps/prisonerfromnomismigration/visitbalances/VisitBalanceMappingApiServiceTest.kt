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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto.MappingType.MIGRATED
import java.util.UUID

@SpringAPIServiceTest
@Import(VisitBalanceMappingApiService::class, VisitBalanceMappingApiMockServer::class)
class VisitBalanceMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitBalanceMappingApiService

  @Autowired
  private lateinit var mockServer: VisitBalanceMappingApiMockServer

  @Nested
  inner class GetByNomisPrisonNumberOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisPrisonNumberOrNull(nomisPrisonNumber = "A1234BC")

      apiService.getByNomisPrisonNumberOrNull(nomisPrisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisPrisonNumberOrNull()

      apiService.getByNomisPrisonNumberOrNull(nomisPrisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/visit-balance/nomis-prison-number/A1234BC")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisPrisonNumberOrNull(
        nomisPrisonNumber = "A1234BC",
        mapping = VisitBalanceMappingDto(
          dpsId = "1234567",
          nomisPrisonNumber = "A1234BC",
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisPrisonNumberOrNull(nomisPrisonNumber = "A1234BC")!!

      assertThat(mapping.dpsId).isEqualTo("1234567")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisPrisonNumberOrNull(
        nomisPrisonNumber = "A1234BC",
        mapping = null,
      )

      assertThat(apiService.getByNomisPrisonNumberOrNull(nomisPrisonNumber = "A1234BC")).isNull()
    }
  }

  @Nested
  inner class GetByNomisPrisonNumber {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisPrisonNumber(nomisPrisonNumber = "A1234BC")

      apiService.getByNomisPrisonNumber(nomisPrisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisPrisonNumber(nomisPrisonNumber = "A1234BC")

      apiService.getByNomisPrisonNumber(nomisPrisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/visit-balance/nomis-prison-number/A1234BC")),
      )
    }

    @Test
    fun `will return dpsId`() = runTest {
      mockServer.stubGetByNomisPrisonNumber(
        nomisPrisonNumber = "A1234BC",
        mapping = VisitBalanceMappingDto(
          dpsId = "1234567",
          nomisPrisonNumber = "A1234BC",
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisPrisonNumber(nomisPrisonNumber = "A1234BC")

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
          mappingType = VisitBalanceMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = UUID.randomUUID().toString(),
          nomisPrisonNumber = "A1234KT",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/visit-balance/migrate")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      val result = apiService.createMapping(
        VisitBalanceMappingDto(
          mappingType = VisitBalanceMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = UUID.randomUUID().toString(),
          nomisPrisonNumber = "A1234KT",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisPrisonNumber = "A4321BC"
      val dpsId = "956d4326-b0c3-47ac-ab12-f0165109a6c5"
      val existingDpsId = "f612a10f-4827-4022-be96-d882193dfabd"

      mockServer.stubCreateMappingsForMigration(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = VisitBalanceMappingDto(
              dpsId = dpsId,
              nomisPrisonNumber = nomisPrisonNumber,
              mappingType = VisitBalanceMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = VisitBalanceMappingDto(
              dpsId = existingDpsId,
              nomisPrisonNumber = nomisPrisonNumber,
              mappingType = VisitBalanceMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMapping(
        VisitBalanceMappingDto(
          mappingType = VisitBalanceMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = UUID.randomUUID().toString(),
          nomisPrisonNumber = "A1234KT",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse!!.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
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
