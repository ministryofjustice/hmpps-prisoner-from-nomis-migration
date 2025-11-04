package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OrganisationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OrganisationsMappingDto.MappingType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.OrganisationsMappingDto.MappingType.NOMIS_CREATED

@SpringAPIServiceTest
@Import(OrganisationsMappingApiService::class, OrganisationsMappingApiMockServer::class)
class OrganisationsMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: OrganisationsMappingApiService

  @Autowired
  private lateinit var mockServer: OrganisationsMappingApiMockServer

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
            duplicate = OrganisationsMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
            existing = OrganisationsMappingDto(
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
      assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class CreateCorporateMapping {
    @Test
    internal fun `will pass oath2 token to create corporate mapping endpoint`() = runTest {
      mockServer.stubCreateCorporateMapping()

      apiService.createOrganisationMapping(
        OrganisationsMappingDto(
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

      val result = apiService.createOrganisationMapping(
        OrganisationsMappingDto(
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
            duplicate = OrganisationsMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
            existing = OrganisationsMappingDto(
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

      val result = apiService.createOrganisationMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
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
        mapping = OrganisationsMappingDto(
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

  @Nested
  inner class GetByNomisCorporateId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisCorporateId(nomisCorporateId = 1234567)

      apiService.getByNomisCorporateId(nomisCorporateId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisCorporateId(nomisCorporateId = 1234567)

      apiService.getByNomisCorporateId(nomisCorporateId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/organisation/nomis-corporate-id/1234567")),
      )
    }
  }

  @Nested
  inner class DeleteByNomisCorporateId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisCorporateId(nomisCorporateId = 1234567)

      apiService.deleteByNomisCorporateId(nomisCorporateId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to DELETE endpoint`() = runTest {
      mockServer.stubDeleteByNomisCorporateId(nomisCorporateId = 1234567)

      apiService.deleteByNomisCorporateId(nomisCorporateId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/corporate/organisation/nomis-corporate-id/1234567")),
      )
    }
  }

  @Nested
  inner class CreateAddressMapping {
    @Test
    internal fun `will pass oath2 token to create address mapping endpoint`() = runTest {
      mockServer.stubCreateAddressMapping()

      apiService.createAddressMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/corporate/address")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateAddressMapping()

      val result = apiService.createAddressMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreateAddressMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = OrganisationsMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
            existing = OrganisationsMappingDto(
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

      val result = apiService.createAddressMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = nomisId,
          dpsId = dpsId,
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class GetByNomisAddressIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull(nomisAddressId = 1234567)

      apiService.getByNomisAddressIdOrNull(nomisAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull()

      apiService.getByNomisAddressIdOrNull(nomisAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/address/nomis-address-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull(
        nomisAddressId = 1234567,
        mapping = OrganisationsMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisAddressIdOrNull(nomisAddressId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("1234567")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull(
        nomisAddressId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisAddressIdOrNull(nomisAddressId = 1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisAddressId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisAddressId(nomisAddressId = 1234567)

      apiService.getByNomisAddressId(nomisAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisAddressId(nomisAddressId = 1234567)

      apiService.getByNomisAddressId(nomisAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/address/nomis-address-id/1234567")),
      )
    }
  }

  @Nested
  inner class DeleteByNomisAddressId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisAddressId(nomisAddressId = 1234567)

      apiService.deleteByNomisAddressId(nomisAddressId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to DELETE endpoint`() = runTest {
      mockServer.stubDeleteByNomisAddressId(nomisAddressId = 1234567)

      apiService.deleteByNomisAddressId(nomisAddressId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/corporate/address/nomis-address-id/1234567")),
      )
    }
  }

  @Nested
  inner class CreatePhoneMapping {
    @Test
    internal fun `will pass oath2 token to create phone mapping endpoint`() = runTest {
      mockServer.stubCreatePhoneMapping()

      apiService.createPhoneMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/corporate/phone")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreatePhoneMapping()

      val result = apiService.createPhoneMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreatePhoneMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = OrganisationsMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
            existing = OrganisationsMappingDto(
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

      val result = apiService.createPhoneMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = nomisId,
          dpsId = dpsId,
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class GetByNomisPhoneIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull(nomisPhoneId = 1234567)

      apiService.getByNomisPhoneIdOrNull(nomisPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull()

      apiService.getByNomisPhoneIdOrNull(nomisPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/phone/nomis-phone-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull(
        nomisPhoneId = 1234567,
        mapping = OrganisationsMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisPhoneIdOrNull(nomisPhoneId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("1234567")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull(
        nomisPhoneId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisPhoneIdOrNull(nomisPhoneId = 1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisPhoneId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisPhoneId(nomisPhoneId = 1234567)

      apiService.getByNomisPhoneId(nomisPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisPhoneId(nomisPhoneId = 1234567)

      apiService.getByNomisPhoneId(nomisPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/phone/nomis-phone-id/1234567")),
      )
    }
  }

  @Nested
  inner class DeleteByNomisPhoneId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisPhoneId(nomisPhoneId = 1234567)

      apiService.deleteByNomisPhoneId(nomisPhoneId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to DELETE endpoint`() = runTest {
      mockServer.stubDeleteByNomisPhoneId(nomisPhoneId = 1234567)

      apiService.deleteByNomisPhoneId(nomisPhoneId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/corporate/phone/nomis-phone-id/1234567")),
      )
    }
  }

  @Nested
  inner class CreateAddressPhoneMapping {
    @Test
    internal fun `will pass oath2 token to create phone mapping endpoint`() = runTest {
      mockServer.stubCreateAddressPhoneMapping()

      apiService.createAddressPhoneMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateAddressPhoneMapping()

      val result = apiService.createAddressPhoneMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreateAddressPhoneMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = OrganisationsMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
            existing = OrganisationsMappingDto(
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

      val result = apiService.createAddressPhoneMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = nomisId,
          dpsId = dpsId,
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class GetByNomisAddressPhoneIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisAddressPhoneIdOrNull(nomisPhoneId = 1234567)

      apiService.getByNomisAddressPhoneIdOrNull(nomisPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisAddressPhoneIdOrNull()

      apiService.getByNomisAddressPhoneIdOrNull(nomisPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone/nomis-phone-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisAddressPhoneIdOrNull(
        nomisPhoneId = 1234567,
        mapping = OrganisationsMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisAddressPhoneIdOrNull(nomisPhoneId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("1234567")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisAddressPhoneIdOrNull(
        nomisPhoneId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisAddressPhoneIdOrNull(nomisPhoneId = 1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisAddressPhoneId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisAddressPhoneId(nomisPhoneId = 1234567)

      apiService.getByNomisAddressPhoneId(nomisPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisAddressPhoneId(nomisPhoneId = 1234567)

      apiService.getByNomisAddressPhoneId(nomisPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone/nomis-phone-id/1234567")),
      )
    }
  }

  @Nested
  inner class DeleteByNomisAddressPhoneId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisAddressPhoneId(nomisPhoneId = 1234567)

      apiService.deleteByNomisAddressPhoneId(nomisPhoneId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to DELETE endpoint`() = runTest {
      mockServer.stubDeleteByNomisAddressPhoneId(nomisPhoneId = 1234567)

      apiService.deleteByNomisAddressPhoneId(nomisPhoneId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/corporate/address-phone/nomis-phone-id/1234567")),
      )
    }
  }

  @Nested
  inner class CreateWebMapping {
    @Test
    internal fun `will pass oath2 token to create web mapping endpoint`() = runTest {
      mockServer.stubCreateWebMapping()

      apiService.createWebMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/corporate/web")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateWebMapping()

      val result = apiService.createWebMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreateWebMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = OrganisationsMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
            existing = OrganisationsMappingDto(
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

      val result = apiService.createWebMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = nomisId,
          dpsId = dpsId,
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class GetByNomisWebIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisWebIdOrNull(nomisWebId = 1234567)

      apiService.getByNomisWebIdOrNull(nomisWebId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisWebIdOrNull()

      apiService.getByNomisWebIdOrNull(nomisWebId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/web/nomis-internet-address-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisWebIdOrNull(
        nomisWebId = 1234567,
        mapping = OrganisationsMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisWebIdOrNull(nomisWebId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("1234567")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisWebIdOrNull(
        nomisWebId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisWebIdOrNull(nomisWebId = 1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisWebId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisWebId(nomisWebId = 1234567)

      apiService.getByNomisWebId(nomisWebId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisWebId(nomisWebId = 1234567)

      apiService.getByNomisWebId(nomisWebId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/web/nomis-internet-address-id/1234567")),
      )
    }
  }

  @Nested
  inner class DeleteByNomisWebId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisWebId(nomisWebId = 1234567)

      apiService.deleteByNomisWebId(nomisWebId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to DELETE endpoint`() = runTest {
      mockServer.stubDeleteByNomisWebId(nomisWebId = 1234567)

      apiService.deleteByNomisWebId(nomisWebId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/corporate/web/nomis-internet-address-id/1234567")),
      )
    }
  }

  @Nested
  inner class CreateEmailMapping {
    @Test
    internal fun `will pass oath2 token to create email mapping endpoint`() = runTest {
      mockServer.stubCreateEmailMapping()

      apiService.createEmailMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/corporate/email")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateEmailMapping()

      val result = apiService.createEmailMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreateEmailMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = OrganisationsMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
            existing = OrganisationsMappingDto(
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

      val result = apiService.createEmailMapping(
        OrganisationsMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = nomisId,
          dpsId = dpsId,
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class GetByNomisEmailIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull(nomisEmailId = 1234567)

      apiService.getByNomisEmailIdOrNull(nomisEmailId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull()

      apiService.getByNomisEmailIdOrNull(nomisEmailId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/email/nomis-internet-address-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull(
        nomisEmailId = 1234567,
        mapping = OrganisationsMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisEmailIdOrNull(nomisEmailId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("1234567")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull(
        nomisEmailId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisEmailIdOrNull(nomisEmailId = 1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisEmailId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisEmailId(nomisEmailId = 1234567)

      apiService.getByNomisEmailId(nomisEmailId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisEmailId(nomisEmailId = 1234567)

      apiService.getByNomisEmailId(nomisEmailId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/corporate/email/nomis-internet-address-id/1234567")),
      )
    }
  }

  @Nested
  inner class DeleteByNomisEmailId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisEmailId(nomisEmailId = 1234567)

      apiService.deleteByNomisEmailId(nomisEmailId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to DELETE endpoint`() = runTest {
      mockServer.stubDeleteByNomisEmailId(nomisEmailId = 1234567)

      apiService.deleteByNomisEmailId(nomisEmailId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/corporate/email/nomis-internet-address-id/1234567")),
      )
    }
  }
}
