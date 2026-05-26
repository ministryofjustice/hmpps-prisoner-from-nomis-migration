package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.users

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(UsersNomisApiService::class, UsersNomisApiMockServer::class)
class UsersNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: UsersNomisApiService

  @Autowired
  private lateinit var mockServer: UsersNomisApiMockServer

  @Nested
  inner class GetUsersDetail {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetUserDetails(nomisStaffUserId = 10000)

      apiService.getUserDetails(userId = 10000)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetUserDetails(nomisStaffUserId = 10000)

      apiService.getUserDetails(userId = 10000)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/users/10000")),
      )
    }

    @Test
    fun `will return user details`() = runTest {
      mockServer.stubGetUserDetails(nomisStaffUserId = 10000)

      val user = apiService.getUserDetails(userId = 10000)

      with(user) {
        assertThat(id).isEqualTo(10000)
        assertThat(firstName).isEqualTo("JOHN")
        assertThat(lastName).isEqualTo("SMITH")
        assertThat(email).isEqualTo("john.smith@justice.gov.uk")
        assertThat(status).isEqualTo("ACTIVE")
        assertThat(audit.createDatetime).isEqualTo(LocalDateTime.parse("2016-08-01T10:55"))
        assertThat(audit.createUsername).isEqualTo("KOFEADDY")
        assertThat(audit.modifyDatetime).isEqualTo(LocalDateTime.parse("2017-08-01T10:55"))
        assertThat(audit.modifyUserId).isEqualTo("KOFE_MOD")

        with(accounts[0]) {
          assertThat(username).isEqualTo("JOHNSMITH_ADM")
          assertThat(sourceCode).isEqualTo("USER")
          assertThat(status).isEqualTo("OPEN")
          assertThat(typeCode).isEqualTo("ADMIN")
          assertThat(activeCaseloadId).isEqualTo("MDI")
          assertThat(caseloads).containsExactly("LEI", "MDI", "NWEB")
          assertThat(roles).containsExactly("DPS_CODE_1", "DPS_CODE_2", "NOMIS_CODE_1")
          assertThat(lastLoggedIn).isEqualTo("2026-03-17T12:30:00")
          assertThat(audit.createDatetime).isEqualTo(LocalDateTime.parse("2016-08-01T10:55"))
          assertThat(audit.createUsername).isEqualTo("KOFEADDY")
          assertThat(audit.modifyDatetime).isEqualTo(LocalDateTime.parse("2017-08-01T10:55"))
          assertThat(audit.modifyUserId).isEqualTo("KOFE_MOD")
        }
      }
    }
  }
}
