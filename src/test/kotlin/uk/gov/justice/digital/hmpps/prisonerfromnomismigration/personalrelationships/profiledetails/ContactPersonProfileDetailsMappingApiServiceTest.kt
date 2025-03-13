package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonProfileDetailsMigrationMappingRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonConfiguration

@SpringAPIServiceTest
@Import(ContactPersonProfileDetailsMappingApiService::class, ContactPersonConfiguration::class, ContactPersonProfileDetailsMappingApiMockServer::class)
class ContactPersonProfileDetailsMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: ContactPersonProfileDetailsMappingApiService

  @Autowired
  private lateinit var mappingApi: ContactPersonProfileDetailsMappingApiMockServer

  @Nested
  inner class PutMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubPutMapping()

      apiService.createMapping(
        ContactPersonProfileDetailsMigrationMappingRequest(
          prisonerNumber = "A1234AA",
          migrationId = "label",
          domesticStatusDpsIds = "1,2,3",
          numberOfChildrenDpsIds = "1,2,3",
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<ContactPersonProfileDetailsMigrationMappingRequest>>() {},
      )

      mappingApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubPutMapping()

      apiService.createMapping(
        ContactPersonProfileDetailsMigrationMappingRequest(
          prisonerNumber = "A1234AA",
          migrationId = "label",
          domesticStatusDpsIds = "1,2,3",
          numberOfChildrenDpsIds = "1,2,3",
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<ContactPersonProfileDetailsMigrationMappingRequest>>() {},
      )

      mappingApi.verify(
        putRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234AA")))
          .withRequestBody(matchingJsonPath("domesticStatusDpsIds", equalTo("1,2,3")))
          .withRequestBody(matchingJsonPath("numberOfChildrenDpsIds", equalTo("1,2,3")))
          .withRequestBody(matchingJsonPath("migrationId", equalTo("label"))),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubPutMapping(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createMapping(
          ContactPersonProfileDetailsMigrationMappingRequest(
            prisonerNumber = "A1234AA",
            migrationId = "label",
            domesticStatusDpsIds = "1,2,3",
            numberOfChildrenDpsIds = "1,2,3",
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<ContactPersonProfileDetailsMigrationMappingRequest>>() {},
        )
      }
    }
  }
}
