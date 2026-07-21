package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffDpsApiExtension.Companion.dpsStaffServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

class StaffDataRepairResourceIntTest(
  @Autowired private val nomisApiMockServer: StaffNomisApiMockServer,
) : StaffIntegrationTestBase() {

  @DisplayName("POST /staff/{staffId}/repair")
  @Nested
  inner class RepairPrisonerBalance {
    val staffId: Long = 1234

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/staff/$staffId/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/staff/$staffId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/staff/$staffId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      val staffId: Long = 1234

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetStaffDetailsById(nomisStaffId = staffId)
        dpsStaffServer.stubSyncStaff()

        webTestClient.post().uri("/staff/$staffId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will retrieve staff details for the staff`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/staff/id/$staffId")))
      }

      @Test
      fun `will send staff details to DPS`() {
        dpsStaffServer.verify(
          putRequestedFor(urlPathEqualTo("/sync/user/1234"))
            // TODO determine if needed
            // .withRequestBodyJsonPath("emails[0].legacyEmailId", equalTo("3456"))
            .withRequestBodyJsonPath("emails[0].email", equalTo("john.smith@justice.gov.uk"))
            .withRequestBodyJsonPath("firstName", equalTo("JOHN"))
            .withRequestBodyJsonPath("lastName", equalTo("SMITH"))
            .withRequestBodyJsonPath("accounts[0].username", equalTo("JOHNSMITH_ADM"))
            .withRequestBodyJsonPath("accounts[0].activeCaseloadId", equalTo("MDI"))
            .withRequestBodyJsonPath("accounts[0].roles[0].roleCode", equalTo("DPS_CODE_1"))
            .withRequestBodyJsonPath("accounts[0].caseloads[0].caseloadId", equalTo("LEI")),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          eq("staff-resynchronisation-repair"),
          check {
            assertThat(it["staffId"]).isEqualTo(staffId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathNotFound {
      val staffId: Long = 9999

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetStaffDetailsByIdNotFound(staffId)
        dpsStaffServer.stubMigrateStaff()

        webTestClient.post().uri("/staff/$staffId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
          .expectBody()
          .jsonPath("userMessage").isEqualTo("Not Found: No staff for nomisStaffId 9999 was found")
      }

      @Test
      fun `will try to retrieve staff details from Nomis`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/staff/id/$staffId")))
      }

      @Test
      fun `will not send staff details to DPS`() {
        dpsStaffServer.verify(0, getRequestedFor(anyUrl()))
      }

      @Test
      fun `will not track telemetry for the repair`() {
        verifyNoInteractions(telemetryClient)
      }
    }
  }
}
