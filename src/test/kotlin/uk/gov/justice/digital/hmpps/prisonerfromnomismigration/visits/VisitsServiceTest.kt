package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsService.VisitCreateAborted
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsService.VisitCreated
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
        post(urlEqualTo("/migrate-visits")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody("654321"),
        ),
      )
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      visitsService.createVisit(
        CreateVsipVisit(
          prisonId = "BXI",
          prisonerId = "A1234AA",
          startTimestamp = LocalDateTime.parse("2020-01-01T09:00:00"),
          endTimestamp = LocalDateTime.parse("2020-01-01T11:45:00"),
          visitType = "SOCIAL",
          visitStatus = VsipStatus.BOOKED,
          visitRoom = "SOCIAL_CENTRE_1",
          contactList = listOf(
            VsipVisitor(nomisPersonId = 5668),
            VsipVisitor(nomisPersonId = 5678),
          ),
          visitRestriction = VisitRestriction.OPEN,
          actionedBy = "user1",
          createDateTime = LocalDateTime.parse("2020-01-01T08:00:00"),
        ),
      )

      visitsApi.verify(
        postRequestedFor(urlEqualTo("/migrate-visits"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass data as JSON to endpoint`(): Unit = runBlocking {
      visitsService.createVisit(
        CreateVsipVisit(
          prisonId = "BXI",
          prisonerId = "A1234AA",
          startTimestamp = LocalDateTime.parse("2020-01-01T09:00:00"),
          endTimestamp = LocalDateTime.parse("2020-01-01T11:45:00"),
          visitType = "SOCIAL",
          visitStatus = VsipStatus.BOOKED,
          visitRoom = "SOCIAL_CENTRE_1",
          contactList = listOf(
            VsipVisitor(nomisPersonId = 5668),
            VsipVisitor(nomisPersonId = 5678),
          ),
          legacyData = VsipLegacyData(leadVisitorId = 123),
          visitRestriction = VisitRestriction.OPEN,
          visitContact = VsipLegacyContactOnVisit("James Flack"),
          actionedBy = "user1",
          createDateTime = LocalDateTime.parse("2020-01-01T08:00:00"),
          modifyDateTime = LocalDateTime.parse("2020-01-01T14:00:00"),
        ),
      )

      visitsApi.verify(
        postRequestedFor(urlEqualTo("/migrate-visits"))
          .withRequestBody(
            equalToJson(
              """
            {
              "prisonId": "BXI",
              "prisonerId": "A1234AA",
              "startTimestamp": "2020-01-01T09:00:00",
              "endTimestamp": "2020-01-01T11:45:00",
              "visitType": "SOCIAL",
              "visitStatus": "BOOKED",
              "visitRoom": "SOCIAL_CENTRE_1",
              "contactList": [
                {
                  "nomisPersonId": 5668
                },
                {
                  "nomisPersonId": 5678
                }
              ],
              "legacyData" : {
                "leadVisitorId" : 123
              },
              "visitors" : [ ],
              "visitNotes" : [ ],
              "visitRestriction": "OPEN",
              "visitContact" : {
                "name" : "James Flack"
              },
              "actionedBy": "user1",
              "createDateTime": "2020-01-01T08:00:00",
              "modifyDateTime": "2020-01-01T14:00:00"
            }
              """.trimIndent(),
            ),
          ),
      )
    }

    @Test
    internal fun `will return newly created VSIP visitId`(): Unit = runBlocking {
      val response = visitsService.createVisit(
        CreateVsipVisit(
          prisonId = "BXI",
          prisonerId = "A1234AA",
          startTimestamp = LocalDateTime.parse("2020-01-01T09:00:00"),
          endTimestamp = LocalDateTime.parse("2020-01-01T11:45:00"),
          visitType = "SOCIAL",
          visitStatus = VsipStatus.BOOKED,
          visitRoom = "SOCIAL_CENTRE_1",
          contactList = listOf(
            VsipVisitor(nomisPersonId = 5668),
            VsipVisitor(nomisPersonId = 5678),
          ),
          visitRestriction = VisitRestriction.OPEN,
          actionedBy = "user1",
          createDateTime = LocalDateTime.parse("2020-01-01T08:00:00"),
        ),
      )
      assertThat(response).isEqualTo(VisitCreated("654321"))
    }

    @Test
    internal fun `will return abort visit for a 422 response`(): Unit = runBlocking {
      visitsApi.stubFor(
        post(urlEqualTo("/migrate-visits")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.UNPROCESSABLE_CONTENT.value()),
        ),
      )

      val response = visitsService.createVisit(
        CreateVsipVisit(
          prisonId = "BXI",
          prisonerId = "A1234AA",
          startTimestamp = LocalDateTime.parse("2020-01-01T09:00:00"),
          endTimestamp = LocalDateTime.parse("2020-01-01T11:45:00"),
          visitType = "SOCIAL",
          visitStatus = VsipStatus.BOOKED,
          visitRoom = "SOCIAL_CENTRE_1",
          contactList = listOf(
            VsipVisitor(nomisPersonId = 5668),
            VsipVisitor(nomisPersonId = 5678),
          ),
          visitRestriction = VisitRestriction.OPEN,
          actionedBy = "user1",
          createDateTime = LocalDateTime.parse("2020-01-01T08:00:00"),
        ),
      )
      assertThat(response).isEqualTo(VisitCreateAborted)
    }
  }
}
