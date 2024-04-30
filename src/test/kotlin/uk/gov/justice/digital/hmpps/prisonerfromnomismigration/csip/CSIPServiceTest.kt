package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.CSIPApiExtension.Companion.csipApi

private const val NOMIS_CSIP_ID = 1234L

@SpringAPIServiceTest
@Import(CSIPService::class, CSIPConfiguration::class)
internal class CSIPServiceTest {

  @Autowired
  private lateinit var csipService: CSIPService

  @Nested
  @DisplayName("POST /csip/migrate")
  inner class CreateCSIPForMigration {
    @BeforeEach
    internal fun setUp() {
      csipApi.stubCSIPMigration()

      runBlocking {
        csipService.migrateCSIP(
          CSIPMigrateRequest(
            nomisCSIPId = NOMIS_CSIP_ID,
            referralSummary = "Fighting on Prisoner Cell Block H",
          ),
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      csipApi.verify(
        postRequestedFor(urlEqualTo("/csip/migrate"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass data to the api`() {
      csipApi.verify(
        postRequestedFor(urlEqualTo("/csip/migrate"))
          .withRequestBody(matchingJsonPath("nomisCSIPId", equalTo("$NOMIS_CSIP_ID")))
          .withRequestBody(matchingJsonPath("referralSummary", equalTo("Fighting on Prisoner Cell Block H"))),
      )
    }
  }
}
