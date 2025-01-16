package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.corporate

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse

@SpringAPIServiceTest
@Import(CorporateMappingApiService::class, CorporateMappingApiMockServer::class)
class CorporateMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: CorporateMappingApiService

  @Autowired
  private lateinit var mockServer: CorporateMappingApiMockServer

  @Nested
  inner class CreateMappingsForMigration {
    @Test
    internal fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      apiService.createMappingsForMigration(
        CorporateMappingsDto(
          mappingType = CorporateMappingsDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          corporateMapping = CorporateMappingIdDto(
            dpsId = "7654321",
            nomisId = 1234567,
          ),
          corporatePhoneMapping = emptyList(),
          corporateAddressPhoneMapping = emptyList(),
          corporateEmailMapping = emptyList(),
          corporateWebMapping = emptyList(),
          corporateAddressMapping = emptyList(),
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/corporate/migrate")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      val result = apiService.createMappingsForMigration(
        CorporateMappingsDto(
          mappingType = CorporateMappingsDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          corporateMapping = CorporateMappingIdDto(
            dpsId = "7654321",
            nomisId = 1234567,
          ),
          corporatePhoneMapping = emptyList(),
          corporateAddressPhoneMapping = emptyList(),
          corporateEmailMapping = emptyList(),
          corporateWebMapping = emptyList(),
          corporateAddressMapping = emptyList(),
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "956d4326-b0c3-47ac-ab12-f0165109a6c5"
      val existingDpsId = "f612a10f-4827-4022-be96-d882193dfabd"

      mockServer.stubCreateMappingsForMigration(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = CorporateMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
            existing = CorporateMappingDto(
              dpsId = existingDpsId,
              nomisId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMappingsForMigration(
        CorporateMappingsDto(
          mappingType = CorporateMappingsDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          corporateMapping = CorporateMappingIdDto(
            dpsId = "7654321",
            nomisId = 1234567,
          ),
          corporatePhoneMapping = emptyList(),
          corporateAddressPhoneMapping = emptyList(),
          corporateEmailMapping = emptyList(),
          corporateWebMapping = emptyList(),
          corporateAddressMapping = emptyList(),
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
    fun `will call the corporate mapping endpoint`() = runTest {
      mockServer.stubGetMigrationDetails(migrationId = "2020-01-01T10%3A00")

      apiService.getMigrationDetails(migrationId = "2020-01-01T10:00")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/corporate/migration-id/2020-01-01T10%3A00")),
      )
    }
  }
}
