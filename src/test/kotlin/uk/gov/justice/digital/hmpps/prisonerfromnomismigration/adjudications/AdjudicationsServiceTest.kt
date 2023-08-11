package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.AdjudicationMigrateDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateOffence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigratePrisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ReportingOfficer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApi

@SpringAPIServiceTest
@Import(AdjudicationsService::class, AdjudicationsConfiguration::class)
internal class AdjudicationsServiceTest {

  @Autowired
  private lateinit var adjudicationsService: AdjudicationsService

  @Nested
  @DisplayName("POST /reported-adjudications/migrate")
  inner class CreateAdjudicationForMigration {
    @BeforeEach
    internal fun setUp() {
      adjudicationsApi.stubCreateAdjudicationForMigration()
      runBlocking {
        adjudicationsService.createAdjudication(
          AdjudicationMigrateDto(
            agencyIncidentId = 1,
            oicIncidentId = 1,
            offenceSequence = 1,
            bookingId = 1,
            agencyId = "MDI",
            incidentDateTime = "2021-01-01T12:00:00",
            reportedDateTime = "2021-01-01T13:00:00",
            locationId = 1,
            statement = "statement",
            reportingOfficer = ReportingOfficer("M.BOB"),
            createdByUsername = "J.KWEKU",
            prisoner = MigratePrisoner(prisonerNumber = "A1234KL", gender = "M", currentAgencyId = "MDI"),
            offence = MigrateOffence("51:1B", offenceDescription = "Assault on another prisoner"),
            witnesses = emptyList(),
            damages = emptyList(),
            evidence = emptyList(),
            punishments = emptyList(),
            hearings = emptyList(),
          ),
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      adjudicationsApi.verify(
        postRequestedFor(WireMock.urlEqualTo("/reported-adjudications/migrate")).withHeader(
          "Authorization",
          WireMock.equalTo("Bearer ABCDE"),
        ),
      )
    }
  }
}
