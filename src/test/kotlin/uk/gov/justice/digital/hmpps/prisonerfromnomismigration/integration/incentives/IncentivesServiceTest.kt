package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.incentives

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.CreateIncentiveIEP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentivesConfiguration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentivesService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.ReviewType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.ReviewType.REVIEW
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.IncentivesApiExtension.Companion.incentivesApi
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(IncentivesService::class, IncentivesConfiguration::class)
internal class IncentivesServiceTest {
  @Autowired
  private lateinit var incentivesService: IncentivesService

  @Nested
  @DisplayName("migrateIncentive")
  inner class MigrateIncentive {
    @BeforeEach
    internal fun setUp() {
      incentivesApi.stubFor(
        post(urlMatching("/iep/migration/booking/\\d*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody("""{"id": 654321}""")
        )
      )
    }

    @Test
    internal fun `will supply authentication token`() {
      incentivesService.migrateIncentive(
        CreateIncentiveIEP(
          prisonId = "BXI",
          iepTime = LocalDateTime.parse("2020-01-01T11:00:00"),
          userId = "J_SMITH",
          iepLevel = "ENH",
          comment = "Good job",
          current = true,
          reviewType = REVIEW,
        ),
        12345
      )

      incentivesApi.verify(
        postRequestedFor(urlEqualTo("/iep/migration/booking/12345"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    internal fun `will pass data as JSON to endpoint`() {
      incentivesService.migrateIncentive(
        CreateIncentiveIEP(
          prisonId = "BXI",
          iepTime = LocalDateTime.parse("2020-01-01T11:00:00"),
          userId = "J_SMITH",
          iepLevel = "ENH",
          comment = "Good job",
          current = true,
          reviewType = MIGRATED,
        ),
        12345
      )

      incentivesApi.verify(
        postRequestedFor(urlMatching("/iep/migration/booking/12345"))
          .withRequestBody(
            equalToJson(
              """
            {
          "prisonId" : "BXI",
          "iepTime": "2020-01-01T11:00:00",
          "userId": "J_SMITH",
          "iepLevel": "ENH",
          "comment": "Good job",
          "current": true,
          "reviewType": "MIGRATED"
            }
              """.trimIndent()
            )
          )
      )
    }

    @Test
    internal fun `will return newly created incentive id`() {
      val incentive = incentivesService.migrateIncentive(
        CreateIncentiveIEP(
          prisonId = "BXI",
          iepTime = LocalDateTime.parse("2020-01-01T11:00:00"),
          userId = "J_SMITH",
          iepLevel = "ENH",
          comment = "Good job",
          current = true,
          reviewType = REVIEW,
        ),
        12345
      )

      assertThat(incentive.id).isEqualTo(654321)
    }
  }
}
