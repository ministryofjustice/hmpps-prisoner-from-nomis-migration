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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceAdjustmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto.MappingType.NOMIS_CREATED

@SpringAPIServiceTest
@Import(VisitBalanceMappingApiService::class, VisitBalanceMappingApiMockServer::class)
class VisitBalanceMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitBalanceMappingApiService

  @Autowired
  private lateinit var mockServer: VisitBalanceMappingApiMockServer

  @Nested
  inner class VisitBalanceAdjustment {

    @Nested
    inner class CreateMapping {
      @Test
      internal fun `will pass oath2 token to migrate endpoint`() = runTest {
        mockServer.stubCreateVisitBalanceAdjustmentMapping()

        apiService.createVisitBalanceAdjustmentMapping(
          VisitBalanceAdjustmentMappingDto(
            mappingType = VisitBalanceAdjustmentMappingDto.MappingType.NOMIS_CREATED,
            label = "2020-01-01T10:00",
            dpsId = "A1234KT",
            nomisVisitBalanceAdjustmentId = 12345L,
          ),
        )

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/visit-balance-adjustment")).withHeader(
            "Authorization",
            equalTo("Bearer ABCDE"),
          ),
        )
      }

      @Test
      fun `will return success when OK response`() = runTest {
        mockServer.stubCreateVisitBalanceAdjustmentMapping()

        val result = apiService.createVisitBalanceAdjustmentMapping(
          VisitBalanceAdjustmentMappingDto(
            mappingType = VisitBalanceAdjustmentMappingDto.MappingType.NOMIS_CREATED,
            label = "2020-01-01T10:00",
            dpsId = "A1234KT",
            nomisVisitBalanceAdjustmentId = 12345L,
          ),
        )

        assertThat(result.isError).isFalse()
      }

      @Test
      fun `will return error when 409 conflict`() = runTest {
        val dpsId = "A4321BC"
        val nomisVisitBalanceAdjustmentId = 12345L
        val existingNomisId = 67890L

        mockServer.stubCreateVisitBalanceAdjustmentMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = VisitBalanceAdjustmentMappingDto(
                dpsId = dpsId,
                nomisVisitBalanceAdjustmentId = nomisVisitBalanceAdjustmentId,
                mappingType = VisitBalanceAdjustmentMappingDto.MappingType.NOMIS_CREATED,
              ),
              existing = VisitBalanceAdjustmentMappingDto(
                dpsId = dpsId,
                nomisVisitBalanceAdjustmentId = existingNomisId,
                mappingType = VisitBalanceAdjustmentMappingDto.MappingType.NOMIS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = _409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        val result = apiService.createVisitBalanceAdjustmentMapping(
          VisitBalanceAdjustmentMappingDto(
            mappingType = VisitBalanceAdjustmentMappingDto.MappingType.NOMIS_CREATED,
            label = "2020-01-01T10:00",
            dpsId = "A1234KT",
            nomisVisitBalanceAdjustmentId = 12345L,
          ),
        )

        assertThat(result.isError).isTrue()
        assertThat(result.errorResponse!!.moreInfo.duplicate.nomisVisitBalanceAdjustmentId).isEqualTo(nomisVisitBalanceAdjustmentId)
        assertThat(result.errorResponse.moreInfo.existing.nomisVisitBalanceAdjustmentId).isEqualTo(existingNomisId)
      }
    }

    @Nested
    inner class GetByNomisVisitBalanceAdjustmentIdOrNull {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetVisitBalanceAdjustmentByNomisIdOrNull(nomisVisitBalanceAdjustmentId = 12345L)

        apiService.getByNomisVisitBalanceAdjustmentIdOrNull(nomisVisitBalanceAdjustmentId = 12345L)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS id to service`() = runTest {
        mockServer.stubGetVisitBalanceAdjustmentByNomisIdOrNull()

        apiService.getByNomisVisitBalanceAdjustmentIdOrNull(nomisVisitBalanceAdjustmentId = 12345L)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/visit-balance-adjustment/nomis-id/12345")),
        )
      }

      @Test
      fun `will return dpsId when mapping exists`() = runTest {
        mockServer.stubGetVisitBalanceAdjustmentByNomisIdOrNull(
          nomisVisitBalanceAdjustmentId = 12345L,
          mapping = VisitBalanceAdjustmentMappingDto(
            dpsId = "1234567",
            nomisVisitBalanceAdjustmentId = 12345L,
            mappingType = VisitBalanceAdjustmentMappingDto.MappingType.NOMIS_CREATED,
          ),
        )

        val mapping = apiService.getByNomisVisitBalanceAdjustmentIdOrNull(nomisVisitBalanceAdjustmentId = 12345L)!!

        assertThat(mapping.dpsId).isEqualTo("1234567")
      }

      @Test
      fun `will return null if mapping does not exist`() = runTest {
        mockServer.stubGetVisitBalanceAdjustmentByNomisIdOrNull(
          nomisVisitBalanceAdjustmentId = 12345L,
          mapping = null,
        )

        assertThat(apiService.getByNomisVisitBalanceAdjustmentIdOrNull(nomisVisitBalanceAdjustmentId = 12345L)).isNull()
      }
    }

    @Nested
    inner class GetByNomisVisitBalanceId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetVisitBalanceAdjustmentByNomisIdOrNull(nomisVisitBalanceAdjustmentId = 12345L)

        apiService.getByNomisVisitBalanceAdjustmentIdOrNull(nomisVisitBalanceAdjustmentId = 12345L)

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass NOMIS id to service`() = runTest {
        mockServer.stubGetVisitBalanceAdjustmentByNomisId(nomisVisitBalanceAdjustmentId = 12345L)

        apiService.getByNomisVisitBalanceAdjustmentIdOrNull(nomisVisitBalanceAdjustmentId = 12345L)

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/visit-balance-adjustment/nomis-id/12345")),
        )
      }

      @Test
      fun `will return dpsId`() = runTest {
        mockServer.stubGetVisitBalanceAdjustmentByNomisId(
          nomisVisitBalanceAdjustmentId = 12345L,
          mapping = VisitBalanceAdjustmentMappingDto(
            dpsId = "1234567",
            nomisVisitBalanceAdjustmentId = 12345L,
            mappingType = VisitBalanceAdjustmentMappingDto.MappingType.NOMIS_CREATED,
          ),
        )

        val mapping = apiService.getByNomisVisitBalanceAdjustmentIdOrNull(nomisVisitBalanceAdjustmentId = 12345L)!!

        assertThat(mapping.dpsId).isEqualTo("1234567")
      }
    }
  }
}
