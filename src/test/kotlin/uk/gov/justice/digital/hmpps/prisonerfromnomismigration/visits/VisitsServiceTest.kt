package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension.Companion.visitsApi
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(VisitsService::class, VisitsConfiguration::class)
internal class VisitsServiceTest {
  @Autowired
  private lateinit var visitsService: VisitsService

  @Nested
  @DisplayName("createVisit")
  inner class CreateVisit {
    @BeforeEach
    internal fun setUp() {
      visitsApi.stubFor(
        WireMock.post(urlEqualTo("/visits")).willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody("""{"visitId": "654321"}""")
        )
      )
    }

    @Test
    internal fun `will supply authentication token`() {
      visitsService.createVisit(
        CreateVsipVisit(
          prisonId = "BXI",
          prisonerId = "A1234AA",
          startTimestamp = LocalDateTime.parse("2020-01-01T09:00:00"),
          endTimestamp = LocalDateTime.parse("2020-01-01T11:45:00"),
          visitType = "STANDARD_SOCIAL",
          visitStatus = "BOOKED",
          visitRoom = "SOCIAL_CENTRE_1",
          contactList = listOf(
            VsipVisitor(nomisPersonId = 5668, leadVisitor = true),
            VsipVisitor(nomisPersonId = 5678, leadVisitor = false)
          )

        )
      )

      visitsApi.verify(
        postRequestedFor(urlEqualTo("/visits"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
    }

    @Test
    internal fun `will pass data as JSON to endpoint`() {
      visitsService.createVisit(
        CreateVsipVisit(
          prisonId = "BXI",
          prisonerId = "A1234AA",
          startTimestamp = LocalDateTime.parse("2020-01-01T09:00:00"),
          endTimestamp = LocalDateTime.parse("2020-01-01T11:45:00"),
          visitType = "STANDARD_SOCIAL",
          visitStatus = "BOOKED",
          visitRoom = "SOCIAL_CENTRE_1",
          contactList = listOf(
            VsipVisitor(nomisPersonId = 5668, leadVisitor = true),
            VsipVisitor(nomisPersonId = 5678, leadVisitor = false)
          )

        )
      )

      visitsApi.verify(
        postRequestedFor(urlEqualTo("/visits"))
          .withRequestBody(
            equalToJson(
              """
            {
              "prisonId": "BXI",
              "prisonerId": "A1234AA",
              "startTimestamp": "2020-01-01T09:00:00",
              "endTimestamp": "2020-01-01T11:45:00",
              "visitType": "STANDARD_SOCIAL",
              "visitStatus": "BOOKED",
              "visitRoom": "SOCIAL_CENTRE_1",
              "contactList": [
                {
                  "nomisPersonId": 5668,
                  "leadVisitor": true
                },
                {
                  "nomisPersonId": 5678,
                  "leadVisitor": false
                }
              ]
            }
              """.trimIndent()
            )
          )
      )
    }

    @Test
    internal fun `will return newly created VSIP visitId`() {
      val visit = visitsService.createVisit(
        CreateVsipVisit(
          prisonId = "BXI",
          prisonerId = "A1234AA",
          startTimestamp = LocalDateTime.parse("2020-01-01T09:00:00"),
          endTimestamp = LocalDateTime.parse("2020-01-01T11:45:00"),
          visitType = "STANDARD_SOCIAL",
          visitStatus = "BOOKED",
          visitRoom = "SOCIAL_CENTRE_1",
          contactList = listOf(
            VsipVisitor(nomisPersonId = 5668, leadVisitor = true),
            VsipVisitor(nomisPersonId = 5678, leadVisitor = false)
          )
        )
      )
      assertThat(visit.visitId).isEqualTo("654321")
    }
  }
}
