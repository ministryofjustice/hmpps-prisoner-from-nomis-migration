package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import java.util.UUID

@SpringAPIServiceTest
@Import(CorePersonMappingApiService::class, CorePersonMappingApiMockServer::class)
class CorePersonMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: CorePersonMappingApiService

  @Autowired
  private lateinit var mockServer: CorePersonMappingApiMockServer

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
        getRequestedFor(urlPathEqualTo("/mapping/core-person/person/nomis-prison-number/A1234BC")),
      )
    }

    @Test
    fun `will return cprId when mapping exists`() = runTest {
      mockServer.stubGetByNomisPrisonNumberOrNull(
        nomisPrisonNumber = "A1234BC",
        mapping = CorePersonMappingDto(
          cprId = "1234567",
          nomisPrisonNumber = "A1234BC",
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisPrisonNumberOrNull(nomisPrisonNumber = "A1234BC")

      assertThat(mapping?.cprId).isEqualTo("1234567")
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
        getRequestedFor(urlPathEqualTo("/mapping/core-person/person/nomis-prison-number/A1234BC")),
      )
    }

    @Test
    fun `will return cprId`() = runTest {
      mockServer.stubGetByNomisPrisonNumber(
        nomisPrisonNumber = "A1234BC",
        mapping = CorePersonMappingDto(
          cprId = "1234567",
          nomisPrisonNumber = "A1234BC",
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisPrisonNumber(nomisPrisonNumber = "A1234BC")

      assertThat(mapping.cprId).isEqualTo("1234567")
    }
  }

  @Nested
  inner class CreateMappingsForMigration {
    @Test
    internal fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      apiService.createMappingsForMigration(
        CorePersonMappingsDto(
          mappingType = CorePersonMappingsDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          personMapping = CorePersonMappingIdDto(
            cprId = UUID.randomUUID().toString(),
            nomisPrisonNumber = "A1234BC",
          ),
          phoneMappings = emptyList(),
          emailMappings = emptyList(),
          addressMappings = emptyList(),
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/migrate")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      val result = apiService.createMappingsForMigration(
        CorePersonMappingsDto(
          mappingType = CorePersonMappingsDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          personMapping = CorePersonMappingIdDto(
            cprId = UUID.randomUUID().toString(),
            nomisPrisonNumber = "A1234BC",
          ),
          phoneMappings = emptyList(),
          emailMappings = emptyList(),
          addressMappings = emptyList(),
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisPrisonNumber = "A4321BC"
      val cprId = "956d4326-b0c3-47ac-ab12-f0165109a6c5"
      val existingCprId = "f612a10f-4827-4022-be96-d882193dfabd"

      mockServer.stubCreateMappingsForMigration(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = CorePersonMappingDto(
              cprId = cprId,
              nomisPrisonNumber = nomisPrisonNumber,
              mappingType = CorePersonMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = CorePersonMappingDto(
              cprId = existingCprId,
              nomisPrisonNumber = nomisPrisonNumber,
              mappingType = CorePersonMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMappingsForMigration(
        CorePersonMappingsDto(
          mappingType = CorePersonMappingsDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          personMapping = CorePersonMappingIdDto(
            cprId = UUID.randomUUID().toString(),
            nomisPrisonNumber = "A1234BC",
          ),
          phoneMappings = emptyList(),
          emailMappings = emptyList(),
          addressMappings = emptyList(),
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.cprId).isEqualTo(cprId)
      assertThat(result.errorResponse!!.moreInfo.existing.cprId).isEqualTo(existingCprId)
    }
  }

  @Nested
  inner class GetMigrationDetails {
    @Test
    fun `will call the person mapping endpoint`() = runTest {
      mockServer.stubGetMigrationDetails(migrationId = "2020-01-01T10%3A00")

      apiService.getMigrationDetails(migrationId = "2020-01-01T10:00")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/core-person/migration-id/2020-01-01T10%3A00")),
      )
    }
  }
}
