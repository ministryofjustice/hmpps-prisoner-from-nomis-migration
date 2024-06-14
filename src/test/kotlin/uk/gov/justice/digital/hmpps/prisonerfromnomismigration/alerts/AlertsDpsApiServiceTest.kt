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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension.Companion.dpsAlertsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiMockServer.Companion.dpsAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiMockServer.Companion.mergedAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiMockServer.Companion.migratedAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.CreateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.MergeAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.MigrateAlert
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
  inner class MigrateAlerts {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      dpsAlertsServer.stubMigrateAlerts(offenderNo = "A1234KL")

      apiService.migrateAlerts(
        offenderNo = "A1234KL",
        alerts = listOf(
          MigrateAlert(
            offenderBookId = 1234567,
            bookingSeq = 1,
            alertSeq = 2,
            activeFrom = LocalDate.now(),
            alertCode = "XA",
            authorisedBy = null,
            description = null,
            createdBy = "B.MORRIS",
            createdByDisplayName = "B. Morris",
            createdAt = LocalDateTime.now(),
          ),
        ),
      )

      dpsAlertsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass alerts to service`() = runTest {
      dpsAlertsServer.stubMigrateAlerts(offenderNo = "A1234KL")

      apiService.migrateAlerts(
        offenderNo = "A1234KL",
        alerts = listOf(
          MigrateAlert(
            offenderBookId = 1234567,
            bookingSeq = 1,
            alertSeq = 2,
            activeFrom = LocalDate.now(),
            alertCode = "XA",
            authorisedBy = null,
            description = null,
            createdBy = "B.MORRIS",
            createdByDisplayName = "B. Morris",
            createdAt = LocalDateTime.now(),
          ),
        ),
      )

      dpsAlertsServer.verify(
        postRequestedFor(urlMatching("/migrate/A1234KL/alerts"))
          .withRequestBody(matchingJsonPath("$[0].alertCode", equalTo("XA"))),
      )
    }

    @Test
    fun `will return dpsAlertIds`() = runTest {
      dpsAlertsServer.stubMigrateAlerts(offenderNo = "A1234KL", response = listOf(migratedAlert().copy(alertUuid = UUID.fromString("f3f31737-6ee3-4ec5-8a79-0ac110fe50e2"))))

      val dpsAlerts = apiService.migrateAlerts(
        offenderNo = "A1234KL",
        alerts = listOf(
          MigrateAlert(
            offenderBookId = 1234567,
            bookingSeq = 1,
            alertSeq = 2,
            activeFrom = LocalDate.now(),
            alertCode = "XA",
            authorisedBy = null,
            description = null,
            createdBy = "B.MORRIS",
            createdByDisplayName = "B. Morris",
            createdAt = LocalDateTime.now(),
          ),
        ),
      )

      assertThat(dpsAlerts[0].alertUuid.toString()).isEqualTo("f3f31737-6ee3-4ec5-8a79-0ac110fe50e2")
    }
  }

  @Nested
  inner class MergeAlerts {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      dpsAlertsServer.stubMergePrisonerAlerts()

      apiService.mergePrisonerAlerts(
        offenderNo = "A1234KL",
        removedOffenderNo = "A1000KL",
        alerts = listOf(
          MergeAlert(
            offenderBookId = 1234567,
            alertSeq = 2,
            activeFrom = LocalDate.now(),
            alertCode = "XA",
            authorisedBy = null,
            description = null,
          ),
        ),
        retainedAlertIds = listOf(UUID.randomUUID().toString()),
      )

      dpsAlertsServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass alerts to service`() = runTest {
      dpsAlertsServer.stubMergePrisonerAlerts()

      apiService.mergePrisonerAlerts(
        offenderNo = "A1234KL",
        removedOffenderNo = "A1000KL",
        alerts = listOf(
          MergeAlert(
            offenderBookId = 1234567,
            alertSeq = 2,
            activeFrom = LocalDate.now(),
            alertCode = "XA",
            authorisedBy = null,
            description = null,
          ),
        ),
        retainedAlertIds = listOf("a01b4d92-8d19-4a7e-8fd7-85b4c5e296bb", "20640934-cd38-481e-9826-51da09a17f77"),
      )

      dpsAlertsServer.verify(
        postRequestedFor(urlMatching("/merge-alerts"))
          .withRequestBody(matchingJsonPath("prisonNumberMergeFrom", equalTo("A1000KL")))
          .withRequestBody(matchingJsonPath("prisonNumberMergeTo", equalTo("A1234KL")))
          .withRequestBody(matchingJsonPath("newAlerts[0].alertCode", equalTo("XA")))
          .withRequestBody(matchingJsonPath("retainedAlertUuids[0]", equalTo("a01b4d92-8d19-4a7e-8fd7-85b4c5e296bb")))
          .withRequestBody(matchingJsonPath("retainedAlertUuids[1]", equalTo("20640934-cd38-481e-9826-51da09a17f77"))),
      )
    }

    @Test
    fun `will return dpsAlertIds`() = runTest {
      dpsAlertsServer.stubMergePrisonerAlerts(created = listOf(mergedAlert().copy(alertUuid = UUID.fromString("f3f31737-6ee3-4ec5-8a79-0ac110fe50e2"))))

      val response = apiService.mergePrisonerAlerts(
        offenderNo = "A1234KL",
        removedOffenderNo = "A1000KL",
        alerts = listOf(
          MergeAlert(
            offenderBookId = 1234567,
            alertSeq = 2,
            activeFrom = LocalDate.now(),
            alertCode = "XA",
            authorisedBy = null,
            description = null,
          ),
        ),
        retainedAlertIds = listOf(UUID.randomUUID().toString()),
      )

      assertThat(response.alertsCreated[0].alertUuid.toString()).isEqualTo("f3f31737-6ee3-4ec5-8a79-0ac110fe50e2")
    }
  }
}
