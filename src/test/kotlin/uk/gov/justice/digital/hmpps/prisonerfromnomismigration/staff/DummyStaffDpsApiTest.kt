package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffDpsApiMockServer.Companion.migrateStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffDpsApiMockServer.Companion.syncStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationResponse

class DummyStaffDpsApiTest : SqsIntegrationTestBase() {
  @Test
  fun testMigrate() {
    val body: UserMigrationRequest = migrateStaff()

    val result = webTestClient.post()
      .uri("/migrate/user")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USER_STAFF__SYNC__RW")))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(body)
      .exchange()
      .expectStatus().isCreated
      .returnResult<UserMigrationResponse>()
      .responseBody
      .blockFirst()!!

    assertThat(result.staffId).isEqualTo("1234")
    // TODO this should be a list of usernames
    assertThat(result.username).isEqualTo("JOHNSMITH_ADM")
  }

  @Test
  fun testSync() {
    val body: UserMigrationRequest = syncStaff()

    val result = webTestClient.put()
      .uri("/prison-users/staff")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USER_STAFF__SYNC__RW")))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(body)
      .exchange()
      .expectStatus().isOk
      .returnResult<UserMigrationResponse>()
      .responseBody
      .blockFirst()!!

    assertThat(result.staffId).isEqualTo("1234")
    assertThat(result.username).isEqualTo("JOHNSMITH_ADM")
  }

  @Test
  fun testDelete() {
    webTestClient.delete()
      .uri("/prison-users/staff/1234")
      .headers(setAuthorisation(roles = listOf("ROLE_PRISON_USER_STAFF__SYNC__RW")))
      .exchange()
      .expectStatus().isNoContent
  }
}
