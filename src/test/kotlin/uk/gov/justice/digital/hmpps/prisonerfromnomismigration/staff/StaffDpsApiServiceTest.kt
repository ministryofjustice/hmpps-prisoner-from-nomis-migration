package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffDpsApiMockServer.Companion.syncStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

@ExtendWith(StaffDpsApiExtension::class)
@SpringAPIServiceTest
@Import(StaffDpsApiService::class, StaffConfiguration::class)
class StaffDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: StaffDpsApiService

  @Nested
  inner class SyncStaff {
    @Test
    internal fun `will pass oauth2 token to sync endpoint`() = runTest {
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
          .withRequestBodyJsonPath("firstName", equalTo("John"))
          .withRequestBodyJsonPath("lastName", equalTo("Smith"))
          .withRequestBodyJsonPath("status", equalTo("ACTIVE"))
          .withRequestBodyJsonPath("createdTimestamp", equalTo("2020-12-04T10:42:43"))
          .withRequestBodyJsonPath("createdBy", equalTo("JIM_BEAM"))
          .withRequestBodyJsonPath("modifiedTimestamp", equalTo("2021-09-12T10:42:43"))
          .withRequestBodyJsonPath("modifiedBy", equalTo("FRED_BROWN"))
          .withRequestBodyJsonPath("emails[0].email", equalTo("john.smith@justice.gov.uk"))
          .withRequestBodyJsonPath("emails[0].createdTimestamp", equalTo("2020-12-04T10:42:43"))
          .withRequestBodyJsonPath("emails[0].createdBy", equalTo("JIM_BEAM"))
          .withRequestBodyJsonPath("emails[0].modifiedTimestamp", equalTo("2021-09-12T10:42:43"))
          .withRequestBodyJsonPath("emails[0].modifiedBy", equalTo("FRED_BROWN"))
          .withRequestBodyJsonPath("accounts[0].username", equalTo("JOHNSMITH_ADM"))
          .withRequestBodyJsonPath("accounts[0].accountType", equalTo("ADMIN"))
          .withRequestBodyJsonPath("accounts[0].accountStatus", equalTo("OPEN"))
          .withRequestBodyJsonPath("accounts[0].lastLoggedIn", equalTo("2026-03-17T12:30:00"))
          .withRequestBodyJsonPath("accounts[0].activeCaseloadId", equalTo("MDI"))
          .withRequestBodyJsonPath("accounts[0].roles[0].roleCode", equalTo("DPS_CODE_1"))
          .withRequestBodyJsonPath("accounts[0].roles[0].createdTimestamp", equalTo("2020-12-04T10:42:43"))
          .withRequestBodyJsonPath("accounts[0].roles[0].createdBy", equalTo("JIM_BEAM3"))
          .withRequestBodyJsonPath("accounts[0].caseloads[0].caseloadId", equalTo("MDI"))
          .withRequestBodyJsonPath("accounts[0].caseloads[0].createdTimestamp", equalTo("2020-12-04T10:42:43"))
          .withRequestBodyJsonPath("accounts[0].caseloads[0].createdBy", equalTo("JIM_BEAM4"))
          .withRequestBodyJsonPath("accounts[0].createdTimestamp", equalTo("2020-12-04T10:42:43"))
          .withRequestBodyJsonPath("accounts[0].createdBy", equalTo("JIM_BEAM2"))
          .withRequestBodyJsonPath("accounts[0].modifiedTimestamp", equalTo("2020-12-04T10:42:43"))
          .withRequestBodyJsonPath("accounts[0].modifiedBy", equalTo("FRED_BROWN2"))
          .withRequestBodyJsonPath("accounts[0].administrationCaseloads[0].caseloadId", equalTo("MDI"))
          .withRequestBodyJsonPath("accounts[0].administrationCaseloads[0].active", equalTo("true"))
          .withRequestBodyJsonPath("accounts[0].administrationCaseloads[0].expiryDate", equalTo("2028-12-04"))
          .withRequestBodyJsonPath("accounts[0].administrationCaseloads[0].createdTimestamp", equalTo("2020-12-04T10:42:43"))
          .withRequestBodyJsonPath("accounts[0].administrationCaseloads[0].createdBy", equalTo("JIM_BEAM6"))
          .withRequestBodyJsonPath("accounts[0].administrationCaseloads[0].modifiedTimestamp", equalTo("2020-12-05T10:42:43"))
          .withRequestBodyJsonPath("accounts[0].administrationCaseloads[0].modifiedBy", equalTo("FREDDY_SMITH"))
          .withRequestBodyJsonPath("accounts[0].memberCaseloads[0].caseloadId", equalTo("MDI"))
          .withRequestBodyJsonPath("accounts[0].memberCaseloads[0].active", equalTo("true"))
          .withRequestBodyJsonPath("accounts[0].memberCaseloads[0].startDate", equalTo("2020-12-04"))
          .withRequestBodyJsonPath("accounts[0].memberCaseloads[0].expiryDate", equalTo("2028-12-04"))
          .withRequestBodyJsonPath("accounts[0].memberCaseloads[0].createdTimestamp", equalTo("2020-12-04T10:42:43"))
          .withRequestBodyJsonPath("accounts[0].memberCaseloads[0].createdBy", equalTo("JIM_BEAM5"))
          .withRequestBodyJsonPath("accounts[0].memberCaseloads[0].modifiedTimestamp", equalTo("2020-12-05T10:42:43"))
          .withRequestBodyJsonPath("accounts[0].memberCaseloads[0].modifiedBy", equalTo("FREDDY_SMITH")),
      )
    }
  }

  @Nested
  inner class DeleteStaff {
    val nomisStaffId = 1234L

    @Test
    internal fun `will pass oauth2 token to delete endpoint`() = runTest {
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
        deleteRequestedFor(urlPathEqualTo("/sync/user/$nomisStaffId")),
      )
    }
  }
}
