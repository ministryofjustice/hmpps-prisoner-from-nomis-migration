package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffDpsApiExtension.Companion.dpsStaffServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffDpsApiMockServer.Companion.migrateStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffDpsApiMockServer.Companion.syncStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

@ExtendWith(StaffDpsApiExtension::class)
@SpringAPIServiceTest
@Import(StaffDpsApiService::class, StaffConfiguration::class)
class StaffDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: StaffDpsApiService

  @Nested
  inner class MigrateStaff {
    @Test
    internal fun `will pass oath2 token to migrate endpoint`() = runTest {
      dpsStaffServer.stubMigrateStaff()

      apiService.migrateStaff(migrateStaff())

      dpsStaffServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      dpsStaffServer.stubMigrateStaff()

      apiService.migrateStaff(migrateStaff())

      dpsStaffServer.verify(
        postRequestedFor(urlPathEqualTo("/migrate/user")),
      )
    }

    @Test
    internal fun `will migrate request data  to migrate endpoint`() = runTest {
      dpsStaffServer.stubMigrateStaff()

      apiService.migrateStaff(migrateStaff())

      dpsStaffServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("user.staffId", equalTo("1234"))
          .withRequestBodyJsonPath("user.emails[0].legacyEmailId", equalTo("3456"))
          .withRequestBodyJsonPath("user.emails[0].email", equalTo("john.smith@justice.gov.uk"))
          .withRequestBodyJsonPath("user.firstName", equalTo("John"))
          .withRequestBodyJsonPath("user.lastName", equalTo("Smith"))
          .withRequestBodyJsonPath("accounts[0].username", equalTo("JOHNSMITH_ADM"))
          .withRequestBodyJsonPath("roles[0].roleCode", equalTo("DPS_CODE_1"))
          .withRequestBodyJsonPath("accessibleCaseloads[0].caseloadId", equalTo("MDI")),
      )
    }
  }

  @Nested
  inner class SyncStaff {
    @Test
    internal fun `will pass oath2 token to sync endpoint`() = runTest {
      dpsStaffServer.stubSyncStaff()

      apiService.syncStaff(1234, syncStaff())

      dpsStaffServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the sync endpoint`() = runTest {
      dpsStaffServer.stubSyncStaff()

      apiService.syncStaff(1234, syncStaff())

      dpsStaffServer.verify(
        putRequestedFor(urlPathEqualTo("/sync/user/1234")),
      )
    }

    @Test
    internal fun `will send request data to sync endpoint`() = runTest {
      dpsStaffServer.stubSyncStaff()

      apiService.syncStaff(1234, syncStaff())

      dpsStaffServer.verify(
        putRequestedFor(anyUrl())
          // TODO check if needed
          // .withRequestBodyJsonPath("emails[0].legacyEmailId", equalTo("3456"))
          .withRequestBodyJsonPath("emails[0].email", equalTo("john.smith@justice.gov.uk"))
          .withRequestBodyJsonPath("firstName", equalTo("John"))
          .withRequestBodyJsonPath("lastName", equalTo("Smith"))
          .withRequestBodyJsonPath("accounts[0].username", equalTo("JOHNSMITH_ADM"))
          .withRequestBodyJsonPath("accounts[0].roles[0].roleCode", equalTo("DPS_CODE_1"))
          .withRequestBodyJsonPath("accounts[0].caseloads[0].caseloadId", equalTo("MDI")),
      )
    }
  }

  @Nested
  inner class DeleteStaff {
    val nomisStaffId = 1234L

    @Test
    internal fun `will pass oath2 token to delete endpoint`() = runTest {
      dpsStaffServer.stubDeleteStaff()

      apiService.deleteStaff(nomisStaffId)

      dpsStaffServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete endpoint`() = runTest {
      dpsStaffServer.stubDeleteStaff()

      apiService.deleteStaff(nomisStaffId)

      dpsStaffServer.verify(
        deleteRequestedFor(urlPathEqualTo("/prison-users/staff/$nomisStaffId")),
      )
    }
  }
}
