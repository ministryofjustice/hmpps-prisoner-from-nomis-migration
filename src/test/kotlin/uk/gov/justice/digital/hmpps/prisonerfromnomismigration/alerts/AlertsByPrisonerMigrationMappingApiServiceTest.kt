package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerAlertMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerAlertMappingsDto.MappingType.DPS_CREATED
import java.util.UUID

@SpringAPIServiceTest
@Import(AlertsByPrisonerMigrationMappingApiService::class, AlertsConfiguration::class, AlertsMappingApiMockServer::class)
class AlertsByPrisonerMigrationMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: AlertsByPrisonerMigrationMappingApiService

  @Autowired
  private lateinit var alertsMappingApiMockServer: AlertsMappingApiMockServer

  @Nested
  inner class PostMappings {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsMappingApiMockServer.stubPostMappings("A1234KT")

      apiService.createMapping(
        offenderNo = "A1234KT",
        PrisonerAlertMappingsDto(
          mappingType = DPS_CREATED,
          mappings = listOf(
            AlertMappingIdDto(
              nomisBookingId = 123456,
              nomisAlertSequence = 1,
              dpsAlertId = UUID.randomUUID().toString(),
            ),
          ),
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<AlertMappingDto>>() {},
      )

      alertsMappingApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to service`() = runTest {
      val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      alertsMappingApiMockServer.stubPostMappings("A1234KT")

      apiService.createMapping(
        offenderNo = "A1234KT",
        PrisonerAlertMappingsDto(
          mappingType = DPS_CREATED,
          mappings = listOf(
            AlertMappingIdDto(
              nomisBookingId = 123456,
              nomisAlertSequence = 1,
              dpsAlertId = dpsAlertId,
            ),
          ),
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<AlertMappingDto>>() {},
      )

      alertsMappingApiMockServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("mappings[0].nomisBookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("mappings[0].nomisAlertSequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("mappings[0].dpsAlertId", equalTo(dpsAlertId)))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("DPS_CREATED"))),
      )
    }
  }
}
