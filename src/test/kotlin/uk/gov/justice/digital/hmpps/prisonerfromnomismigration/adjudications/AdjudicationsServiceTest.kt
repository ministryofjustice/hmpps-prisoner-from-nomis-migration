@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
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
    }

    @Test
    fun `should call api with OAuth2 token`() {
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
            disIssued = emptyList(),
          ),
        )
      }

      adjudicationsApi.verify(
        postRequestedFor(WireMock.urlEqualTo("/reported-adjudications/migrate")).withHeader(
          "Authorization",
          WireMock.equalTo("Bearer ABCDE"),
        ),
      )
    }

    @Test
    fun `will return NULL for 406 error`() = runTest {
      adjudicationsApi.stubCreateAdjudicationForMigrationWithError(status = 406)

      val mappings = adjudicationsService.createAdjudication(
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
          disIssued = emptyList(),
        ),
      )

      assertThat(mappings).isNull()
    }

    @Test
    fun `will throw exception for any error other than 406`() = runTest {
      adjudicationsApi.stubCreateAdjudicationForMigrationWithError(status = 500)

      assertThrows<InternalServerError> {
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
            disIssued = emptyList(),
          ),
        )
      }
    }
  }

  @Nested
  @DisplayName("GET /reported-adjudications/{chargeNumber}/v2")
  inner class GetAdjudication {
    @BeforeEach
    internal fun setUp() {
      adjudicationsApi.stubChargeGet(chargeNumber = "1234")
    }

    @Test
    fun `should call api with OAuth2 token`(): Unit = runTest {
      adjudicationsService.getCharge("1234", "MDI")

      adjudicationsApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/1234/v2"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call api with prisonId in header token`(): Unit = runTest {
      adjudicationsService.getCharge("1234", "MDI")

      adjudicationsApi.verify(
        WireMock.getRequestedFor(WireMock.anyUrl())
          .withHeader("Active-Caseload", WireMock.equalTo("MDI")),
      )
    }

    @Test
    internal fun `will parse data for an adjudication`(): Unit = runTest {
      adjudicationsApi.stubChargeGet(chargeNumber = "1234", offenderNo = "A1234KT")

      val adjudication = adjudicationsService.getCharge("1234", "MDI")

      assertThat(adjudication?.reportedAdjudication?.prisonerNumber).isEqualTo("A1234KT")
    }

    @Test
    internal fun `when adjudication is not found null is returned`() = runTest {
      adjudicationsApi.stubChargeGetWithError("1234", status = 404)

      assertThat(adjudicationsService.getCharge("1234", "MDI")).isNull()
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      adjudicationsApi.stubChargeGetWithError("1234", status = 503)

      assertThrows<ServiceUnavailable> {
        adjudicationsService.getCharge("1234", "MDI")
      }
    }
  }
}
