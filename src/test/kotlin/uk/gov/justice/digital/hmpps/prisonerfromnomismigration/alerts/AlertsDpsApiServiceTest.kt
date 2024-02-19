package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension.Companion.dpsAlertsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.NomisAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.NomisAlertMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.NomisAlertMapping.Status.CREATED
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
        alert = NomisAlert(
          alertDate = LocalDate.now(),
          offenderBookId = 1234567,
          offenderNo = "A1234KL",
          alertSeq = 3,
          alertType = "X",
          alertCode = "XA",
          alertStatus = "ACTIVE",
          verifiedFlag = false,
          createDatetime = LocalDateTime.now(),
        ),
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
        alert = NomisAlert(
          alertDate = LocalDate.now(),
          offenderBookId = 1234567,
          offenderNo = "A1234KL",
          alertSeq = 3,
          alertType = "X",
          alertCode = "XA",
          alertStatus = "ACTIVE",
          verifiedFlag = false,
          createDatetime = LocalDateTime.now(),
        ),
      )

      dpsAlertsServer.verify(
        postRequestedFor(urlMatching("/alerts"))
          .withRequestBody(matchingJsonPath("alertType", equalTo("X")))
          .withRequestBody(matchingJsonPath("alertCode", equalTo("XA"))),
      )
    }

    @Test
    fun `will return dpsAlertId`() = runTest {
      dpsAlertsServer.stubPostAlert(
        NomisAlertMapping(
          offenderBookId = 12345,
          alertSeq = 2,
          alertUuid = UUID.fromString("f3f31737-6ee3-4ec5-8a79-0ac110fe50e2"),
          status = CREATED,
        ),
      )

      val dpsAlert = apiService.createAlert(
        alert = NomisAlert(
          alertDate = LocalDate.now(),
          offenderBookId = 1234567,
          offenderNo = "A1234KL",
          alertSeq = 3,
          alertType = "X",
          alertCode = "XA",
          alertStatus = "ACTIVE",
          verifiedFlag = false,
          createDatetime = LocalDateTime.now(),
        ),
      )

      assertThat(dpsAlert.alertUuid.toString()).isEqualTo("f3f31737-6ee3-4ec5-8a79-0ac110fe50e2")
    }
  }
}
