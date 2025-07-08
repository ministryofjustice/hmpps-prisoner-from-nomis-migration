package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

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
import org.springframework.core.ParameterizedTypeReference
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerRestrictionMappingDto.MappingType.NOMIS_CREATED

@SpringAPIServiceTest
@Import(PrisonerRestrictionMappingApiService::class, PrisonerRestrictionMappingApiMockServer::class)
class PrisonerRestrictionMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: PrisonerRestrictionMappingApiService

  @Autowired
  private lateinit var mockServer: PrisonerRestrictionMappingApiMockServer

  @Nested
  inner class GetByNomisPrisonerRestrictionIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisPrisonerRestrictionIdOrNull(nomisRestrictionId = 1234567)

      apiService.getByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisPrisonerRestrictionIdOrNull(nomisRestrictionId = 1234567)

      apiService.getByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisPrisonerRestrictionIdOrNull(
        nomisRestrictionId = 1234567,
        mapping = PrisonerRestrictionMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          offenderNo = "A1234KT",
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("1234567")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisPrisonerRestrictionIdOrNull(
        nomisRestrictionId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisPrisonerRestrictionIdOrNull(nomisPrisonerRestrictionId = 1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisPrisonerRestrictionId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisPrisonerRestrictionId(nomisPrisonerRestrictionId = 1234567)

      apiService.getByNomisPrisonerRestrictionId(nomisPrisonerRestrictionId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisPrisonerRestrictionId(nomisPrisonerRestrictionId = 1234567)

      apiService.getByNomisPrisonerRestrictionId(nomisPrisonerRestrictionId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/1234567")),
      )
    }
  }

  @Nested
  inner class DeleteByNomisPrisonerRestrictionId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisPrisonerRestrictionId(nomisPrisonerRestrictionId = 1234567)

      apiService.deleteByNomisPrisonerRestrictionId(nomisPrisonerRestrictionId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisPrisonerRestrictionId(nomisPrisonerRestrictionId = 1234567)

      apiService.deleteByNomisPrisonerRestrictionId(nomisPrisonerRestrictionId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction/nomis-prisoner-restriction-id/1234567")),
      )
    }
  }

  @Nested
  inner class CreateMapping {
    @Test
    internal fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateMapping()

      apiService.createMapping(
        PrisonerRestrictionMappingDto(
          label = "2020-01-01T10:00",
          dpsId = "1234567",
          nomisId = 1234567,
          offenderNo = "A1234KT",
          mappingType = MIGRATED,
        ),
        errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonerRestrictionMappingDto>>() {},
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateMapping()

      val result = apiService.createMapping(
        PrisonerRestrictionMappingDto(
          label = "2020-01-01T10:00",
          dpsId = "1234567",
          nomisId = 1234567,
          offenderNo = "A1234KT",
          mappingType = MIGRATED,
        ),
        errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonerRestrictionMappingDto>>() {},
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "956d4326-b0c3-47ac-ab12-f0165109a6c5"
      val existingDpsId = "f612a10f-4827-4022-be96-d882193dfabd"

      mockServer.stubCreateMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PrisonerRestrictionMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              offenderNo = "A1234KT",
              mappingType = NOMIS_CREATED,
            ),
            existing = PrisonerRestrictionMappingDto(
              dpsId = existingDpsId,
              nomisId = nomisId,
              offenderNo = "A1234KT",
              mappingType = NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMapping(
        PrisonerRestrictionMappingDto(
          label = "2020-01-01T10:00",
          dpsId = "1234567",
          nomisId = 1234567,
          offenderNo = "A1234KT",
          mappingType = MIGRATED,
        ),
        errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonerRestrictionMappingDto>>() {},
      )

      assertThat(result.isError).isTrue()
      with(result.errorResponse!!) {
        assertThat(moreInfo.duplicate.dpsId).isEqualTo(dpsId)
        assertThat(moreInfo.existing.dpsId).isEqualTo(existingDpsId)
      }
    }
  }

  @Nested
  inner class GetMigrationDetails {
    @Test
    fun `will call the prisoner restriction mapping endpoint`() = runTest {
      mockServer.stubGetMigrationDetails(migrationId = "2020-01-01T10%3A00")

      apiService.getMigrationDetails(migrationId = "2020-01-01T10:00")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/prisoner-restriction/migration-id/2020-01-01T10%3A00")),
      )
    }
  }
}
