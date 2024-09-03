package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest.MigrationType.PHYSICAL_ATTRIBUTES

@SpringAPIServiceTest
@Import(PrisonPersonMappingApiService::class, PrisonPersonConfiguration::class, PrisonPersonMappingApiMockServer::class)
class PrisonPersonMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: PrisonPersonMappingApiService

  @Autowired
  private lateinit var mappingApi: PrisonPersonMappingApiMockServer

  @Nested
  inner class PostMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubPostMapping()

      apiService.createMapping(
        PrisonPersonMigrationMappingRequest(
          nomisPrisonerNumber = "A1234AA",
          migrationType = PHYSICAL_ATTRIBUTES,
          dpsIds = listOf(1, 2, 3),
          label = "label",
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonPersonMigrationMappingRequest>>() {},
      )

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubPostMapping()

      apiService.createMapping(
        PrisonPersonMigrationMappingRequest(
          nomisPrisonerNumber = "A1234AA",
          migrationType = PHYSICAL_ATTRIBUTES,
          dpsIds = listOf(1, 2),
          label = "label",
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonPersonMigrationMappingRequest>>() {},
      )

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("nomisPrisonerNumber", equalTo("A1234AA")))
          .withRequestBody(matchingJsonPath("migrationType", equalTo("PHYSICAL_ATTRIBUTES")))
          .withRequestBody(matchingJsonPath("dpsIds[?(@ == 1)]"))
          .withRequestBody(matchingJsonPath("dpsIds[?(@ == 2)]"))
          .withRequestBody(matchingJsonPath("label", equalTo("label"))),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubPostMapping(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createMapping(
          PrisonPersonMigrationMappingRequest(
            nomisPrisonerNumber = "A1234AA",
            migrationType = PHYSICAL_ATTRIBUTES,
            dpsIds = listOf(1, 2),
            label = "label",
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonPersonMigrationMappingRequest>>() {},
        )
      }
    }
  }
}
