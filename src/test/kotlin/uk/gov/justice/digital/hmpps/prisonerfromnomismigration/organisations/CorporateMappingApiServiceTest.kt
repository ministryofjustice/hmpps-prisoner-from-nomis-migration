package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingDto.MappingType
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
        getRequestedFor(urlPathEqualTo("/mapping/corporate/organisation/migration-id/2020-01-01T10%3A00")),
      )
    }
  }

  @Nested
  inner class CreateCorporateMapping {
    @Test
    internal fun `will pass oath2 token to create corporate mapping endpoint`() = runTest {
      mockServer.stubCreateCorporateMapping()

      apiService.createCorporateMapping(
        CorporateMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/corporate/organisation")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateCorporateMapping()

      val result = apiService.createCorporateMapping(
        CorporateMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreateCorporateMapping(
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

      val result = apiService.createCorporateMapping(
        CorporateMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse!!.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class GetByNomisCorporateIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisCorporateIdOrNull(nomisCorporateId = 1234567)

      apiService.getByNomisCorporateIdOrNull(nomisCorporateId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisCorporateIdOrNull()

      apiService.getByNomisCorporateIdOrNull(nomisCorporateId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/organisation/nomis-corporate-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisCorporateIdOrNull(
        nomisCorporateId = 1234567,
        mapping = CorporateMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisCorporateIdOrNull(nomisCorporateId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("1234567")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisCorporateIdOrNull(
        nomisCorporateId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisCorporateIdOrNull(nomisCorporateId = 1234567)).isNull()
    }
  }
}
