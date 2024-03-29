package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension.Companion.dpsAlertsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiMockServer.Companion.dpsAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.CreateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.MigrateAlertRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.UpdateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringAPIServiceTest
@Import(AlertsDpsApiService::class, AlertsConfiguration::class)
class AlertsDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: AlertsDpsApiService

  @Nested
  inner class CreateAlert {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      dpsAlertsServer.stubPostAlert()

      apiService.createAlert(
        alert = CreateAlert(
          prisonNumber = "A1234KL",
          activeFrom = LocalDate.now(),
          alertCode = "XA",
        ),
        createdByUsername = "B.MORRIS",
      )

      dpsAlertsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass alert to service`() = runTest {
      dpsAlertsServer.stubPostAlert()

      apiService.createAlert(
        alert = CreateAlert(
          prisonNumber = "A1234KL",
          activeFrom = LocalDate.now(),
          alertCode = "XA",
        ),
        createdByUsername = "B.MORRIS",
      )

      dpsAlertsServer.verify(
        postRequestedFor(urlMatching("/alerts"))
          .withRequestBody(matchingJsonPath("alertCode", equalTo("XA"))),
      )
    }

    @Test
    internal fun `will pass username to service via header`() = runTest {
      dpsAlertsServer.stubPostAlert()

      apiService.createAlert(
        alert = CreateAlert(
          prisonNumber = "A1234KL",
          activeFrom = LocalDate.now(),
          alertCode = "XA",
        ),
        createdByUsername = "B.MORRIS",
      )

      dpsAlertsServer.verify(
        postRequestedFor(urlMatching("/alerts"))
          .withHeader("Username", equalTo("B.MORRIS")),
      )
    }

    @Test
    internal fun `will pass source as NOMIS to service via header`() = runTest {
      dpsAlertsServer.stubPostAlert()

      apiService.createAlert(
        alert = CreateAlert(
          prisonNumber = "A1234KL",
          activeFrom = LocalDate.now(),
          alertCode = "XA",
        ),
        createdByUsername = "B.MORRIS",
      )

      dpsAlertsServer.verify(
        postRequestedFor(urlMatching("/alerts"))
          .withHeader("Source", equalTo("NOMIS")),
      )
    }

    @Test
    fun `will return dpsAlertId`() = runTest {
      dpsAlertsServer.stubPostAlert(response = dpsAlert().copy(alertUuid = UUID.fromString("f3f31737-6ee3-4ec5-8a79-0ac110fe50e2")))

      val dpsAlert = apiService.createAlert(
        alert = CreateAlert(
          prisonNumber = "A1234KL",
          activeFrom = LocalDate.now(),
          alertCode = "XA",
        ),
        createdByUsername = "B.MORRIS",
      )

      assertThat(dpsAlert.alertUuid.toString()).isEqualTo("f3f31737-6ee3-4ec5-8a79-0ac110fe50e2")
    }
  }

  @Nested
  inner class UpdateAlert {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      dpsAlertsServer.stubPutAlert()

      apiService.updateAlert(
        alertId = "f3f31737-6ee3-4ec5-8a79-0ac110fe50e2",
        alert = UpdateAlert(
          activeFrom = LocalDate.now().minusDays(1),
          activeTo = LocalDate.now(),
        ),
        updatedByUsername = "C.MORRIS",
      )

      dpsAlertsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass alert to service`() = runTest {
      dpsAlertsServer.stubPutAlert()

      apiService.updateAlert(
        alertId = "f3f31737-6ee3-4ec5-8a79-0ac110fe50e2",
        alert = UpdateAlert(
          activeFrom = LocalDate.parse("2020-01-23"),
          activeTo = LocalDate.parse("2023-01-23"),
        ),
        updatedByUsername = "C.MORRIS",
      )

      dpsAlertsServer.verify(
        putRequestedFor(urlEqualTo("/alerts/f3f31737-6ee3-4ec5-8a79-0ac110fe50e2"))
          .withRequestBody(matchingJsonPath("activeFrom", equalTo("2020-01-23")))
          .withRequestBody(matchingJsonPath("activeTo", equalTo("2023-01-23"))),
      )
    }

    @Test
    internal fun `will pass username to service via header`() = runTest {
      dpsAlertsServer.stubPutAlert()

      apiService.updateAlert(
        alertId = "f3f31737-6ee3-4ec5-8a79-0ac110fe50e2",
        alert = UpdateAlert(
          activeFrom = LocalDate.parse("2020-01-23"),
          activeTo = LocalDate.parse("2023-01-23"),
        ),
        updatedByUsername = "C.MORRIS",
      )

      dpsAlertsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Username", equalTo("C.MORRIS")),
      )
    }

    @Test
    internal fun `will pass source as NOMIS to service via header`() = runTest {
      dpsAlertsServer.stubPutAlert()

      apiService.updateAlert(
        alertId = "f3f31737-6ee3-4ec5-8a79-0ac110fe50e2",
        alert = UpdateAlert(
          activeFrom = LocalDate.parse("2020-01-23"),
          activeTo = LocalDate.parse("2023-01-23"),
        ),
        updatedByUsername = "C.MORRIS",
      )

      dpsAlertsServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Source", equalTo("NOMIS")),
      )
    }
  }

  @Nested
  inner class DeleteAlert {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      dpsAlertsServer.stubDeleteAlert()

      apiService.deleteAlert(
        alertId = "f3f31737-6ee3-4ec5-8a79-0ac110fe50e2",
      )

      dpsAlertsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass alert id to service`() = runTest {
      dpsAlertsServer.stubDeleteAlert()

      apiService.deleteAlert(
        alertId = "f3f31737-6ee3-4ec5-8a79-0ac110fe50e2",
      )

      dpsAlertsServer.verify(
        deleteRequestedFor(urlEqualTo("/alerts/f3f31737-6ee3-4ec5-8a79-0ac110fe50e2")),
      )
    }

    @Test
    internal fun `will pass source as NOMIS to service via header`() = runTest {
      dpsAlertsServer.stubDeleteAlert()

      apiService.deleteAlert(
        alertId = "f3f31737-6ee3-4ec5-8a79-0ac110fe50e2",
      )

      dpsAlertsServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Source", equalTo("NOMIS")),
      )
    }
  }

  @Nested
  inner class MigrateAlert {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      dpsAlertsServer.stubMigrateAlert()

      apiService.migrateAlert(
        alert = MigrateAlertRequest(
          prisonNumber = "A1234KL",
          activeFrom = LocalDate.now(),
          alertCode = "XA",
          comments = emptyList(),
          authorisedBy = null,
          description = null,
          createdBy = "B.MORRIS",
          createdByDisplayName = "B. Morris",
          createdAt = LocalDateTime.now(),
        ),
      )

      dpsAlertsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass alert to service`() = runTest {
      dpsAlertsServer.stubMigrateAlert()

      apiService.migrateAlert(
        alert = MigrateAlertRequest(
          prisonNumber = "A1234KL",
          activeFrom = LocalDate.now(),
          alertCode = "XA",
          comments = emptyList(),
          authorisedBy = null,
          description = null,
          createdBy = "B.MORRIS",
          createdByDisplayName = "B. Morris",
          createdAt = LocalDateTime.now(),
        ),
      )

      dpsAlertsServer.verify(
        postRequestedFor(urlMatching("/migrate/alerts"))
          .withRequestBody(matchingJsonPath("alertCode", equalTo("XA"))),
      )
    }

    @Test
    fun `will return dpsAlertId`() = runTest {
      dpsAlertsServer.stubMigrateAlert(response = dpsAlert().copy(alertUuid = UUID.fromString("f3f31737-6ee3-4ec5-8a79-0ac110fe50e2")))

      val dpsAlert = apiService.migrateAlert(
        alert = MigrateAlertRequest(
          prisonNumber = "A1234KL",
          activeFrom = LocalDate.now(),
          alertCode = "XA",
          comments = emptyList(),
          authorisedBy = null,
          description = null,
          createdBy = "B.MORRIS",
          createdByDisplayName = "B. Morris",
          createdAt = LocalDateTime.now(),
        ),
      )
      assertThat(dpsAlert?.alertUuid.toString()).isEqualTo("f3f31737-6ee3-4ec5-8a79-0ac110fe50e2")
    }

    @Test
    fun `will return nothing if DPS rejects alert as duplicate`() = runTest {
      dpsAlertsServer.stubMigrateAlert(status = HttpStatus.CONFLICT)

      val dpsAlert = apiService.migrateAlert(
        alert = MigrateAlertRequest(
          prisonNumber = "A1234KL",
          activeFrom = LocalDate.now(),
          alertCode = "XA",
          comments = emptyList(),
          authorisedBy = null,
          description = null,
          createdBy = "B.MORRIS",
          createdByDisplayName = "B. Morris",
          createdAt = LocalDateTime.now(),
        ),
      )
      assertThat(dpsAlert).isNull()
    }

    @Test
    fun `any other bad response is an error`() = runTest {
      dpsAlertsServer.stubMigrateAlert(status = HttpStatus.BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.migrateAlert(
          alert = MigrateAlertRequest(
            prisonNumber = "A1234KL",
            activeFrom = LocalDate.now(),
            alertCode = "XA",
            comments = emptyList(),
            authorisedBy = null,
            description = null,
            createdBy = "B.MORRIS",
            createdByDisplayName = "B. Morris",
            createdAt = LocalDateTime.now(),
          ),
        )
      }
    }
  }
}
