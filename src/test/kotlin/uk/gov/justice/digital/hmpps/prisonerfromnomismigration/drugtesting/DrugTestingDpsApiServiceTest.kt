package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.DrugTestingDpsApiExtension.Companion.dpsDrugTestingServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.DrugTestingDpsApiMockServer.Companion.migrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import java.time.LocalDate

@ExtendWith(DrugTestingDpsApiExtension::class)
@SpringAPIServiceTest
@Import(DrugTestingDpsApiService::class, DrugTestingConfiguration::class)
class DrugTestingDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: DrugTestingDpsApiService

  @Test
  fun `will pass oauth2 token to service`() = runTest {
    dpsDrugTestingServer.stubMigrate(prisonId = "MDI", rtpDate = LocalDate.parse("2025-03-04"))

    apiService.migrate(prisonCode = "MDI", rtpDate = LocalDate.parse("2025-03-04"), migrationRequest())

    dpsDrugTestingServer.verify(
      putRequestedFor(anyUrl())
        .withHeader("Authorization", equalTo("Bearer ABCDE")),
    )
  }

  @Test
  fun `will call migrate endpoint`() = runTest {
    dpsDrugTestingServer.stubMigrate(prisonId = "MDI", rtpDate = LocalDate.parse("2025-03-04"))

    apiService.migrate(prisonCode = "MDI", rtpDate = LocalDate.parse("2025-03-04"), migrationRequest())

    dpsDrugTestingServer.verify(
      putRequestedFor(urlPathEqualTo("/resync/testing-lists/MDI/2025-03-04")),
    )
  }

  @Test
  fun `will pass migration request to service`() = runTest {
    dpsDrugTestingServer.stubMigrate(prisonId = "MDI", rtpDate = LocalDate.parse("2025-03-04"))

    apiService.migrate(prisonCode = "MDI", rtpDate = LocalDate.parse("2025-03-04"), migrationRequest())

    dpsDrugTestingServer.verify(
      putRequestedFor(urlPathEqualTo("/resync/testing-lists/MDI/2025-03-04"))
        .withRequestBody(matchingJsonPath("createdBy", equalTo("billy")))
        .withRequestBody(matchingJsonPath("prisoners[0].prisonerNumber", equalTo("A1234BC")))
        .withRequestBody(matchingJsonPath("prisoners[0].listType", equalTo("M"))),
    )
  }
}
