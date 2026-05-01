package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse.Status._409_CONFLICT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

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
    inner class GetMigrationCount {
      @BeforeEach
      internal fun setUp() {
        mockServer.stubGetMigrationCount(count = 56_766)
      }

      @Test
      internal fun `will supply authentication token`(): Unit = runBlocking {
        apiService.getPagedModelMigrationCount("2020-01-01T10:00:00")

        mappingApi.verify(
          getRequestedFor(
            urlPathMatching("/mapping/prisoner-balance/migration-id/.*"),
          )
            .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will return zero when not found`(): Unit = runBlocking {
        mappingApi.stubFor(
          get(urlPathMatching("/mapping/prisoner-balance/migration-id/.*")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.NOT_FOUND.value())
              .withBody("""{"message":"Not found"}"""),
          ),
        )

        assertThat(apiService.getPagedModelMigrationCount("2020-01-01T10:00:00")).isEqualTo(0)
      }

      @Test
      internal fun `will return the mapping count when found`(): Unit = runBlocking {
        mockServer.stubGetMigrationCount(
          migrationId = "2020-01-01T11:10:00",
          count = 54_766,
        )
        assertThat(apiService.getPagedModelMigrationCount("2020-01-01T11:10:00")).isEqualTo(54_766)
      }

      @Test
      internal fun `will throw exception for any other error`() {
        mappingApi.stubFor(
          get(urlPathMatching("/mapping/prisoner-balance/migration-id/.*")).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
              .withBody("""{"message":"Tea"}"""),
          ),
        )

        assertThatThrownBy {
          runBlocking {
            apiService.getPagedModelMigrationCount("2020-01-01T10:00:00")
          }
        }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
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
  }
}
