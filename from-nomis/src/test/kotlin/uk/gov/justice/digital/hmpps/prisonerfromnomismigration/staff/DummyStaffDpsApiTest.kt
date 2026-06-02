package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffDpsApiMockServer.Companion.migrateStaff

class DummyStaffDpsApiTest : SqsIntegrationTestBase() {
  @Test
  fun testApi() {
    val body: UserMigrationRequestDps = migrateStaff()

    val result = webTestClient.post()
      .uri("/prison-users/migrate/staff")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USER_STAFF__SYNC__RW")))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(body)
      .exchange()
      .expectStatus().isCreated
      .returnResult<UserMigrationResponseDps>()
      .responseBody
      .blockFirst()!!

    assertThat(result.staffId).isEqualTo("1234")
    assertThat(result.username!![0]).isEqualTo("JOHNSMITH_ADM")
  }
}
