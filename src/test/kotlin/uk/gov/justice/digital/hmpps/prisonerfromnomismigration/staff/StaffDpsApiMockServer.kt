package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.MigratedUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.MigratedUserAccessibleCaseload
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.MigratedUserAccount
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.MigratedUserRole
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.UserMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDateTime
import java.util.UUID

class StaffDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    private var enableResetBeforeEach = true

    @JvmField
    val dpsStaffServer = StaffDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsStaffServer.getRequestBody(pattern, jsonMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsStaffServer.getRequestBodies(pattern, jsonMapper)

    fun resetAndDisableResetBeforeEach() {
      enableResetBeforeEach = false
      dpsStaffServer.resetAll()
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsStaffServer.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    if (enableResetBeforeEach) dpsStaffServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsStaffServer.stop()
    enableResetBeforeEach = true
  }
}

class StaffDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    const val WIREMOCK_PORT = 8089

    fun migrateStaff() = UserMigrationRequest(
      user = MigratedUser(
        // TODO this should be a long
        id = 1234.toString(),
        email = "john.smith@justice.gov.uk",
        firstName = "John",
        lastName = "Smith",
        status = MigratedUser.Status.ACTIVE,
        createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
        createdBy = "JIM_BEAM",
        modifiedTimestamp = LocalDateTime.parse("2021-09-12T10:42:43"),
        modifiedBy = "FRED_BROWN",
      ),
      accounts = listOf(
        MigratedUserAccount(
          username = "JOHNSMITH_ADM",
          accountType = MigratedUserAccount.AccountType.ADMIN,
          accountStatus = MigratedUserAccount.AccountStatus.OPEN,
          activeCaseloadId = "MDI",
          createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
          createdBy = "JIM_BEAM2",
          modifiedTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
          modifiedBy = "FRED_BROWN2",
        ),
      ),
      roles = listOf(
        MigratedUserRole(
          username = "JOHNSMITH_ADM",
          roleCode = "DPS_CODE_1",
          createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
          createdBy = "JIM_BEAM3",
        ),
        MigratedUserRole(
          username = "JOHNSMITH_ADM",
          roleCode = "DPS_CODE_2",
          createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
          createdBy = "JIM_BEAM3",
        ),
      ),
      accessibleCaseloads = listOf(
        MigratedUserAccessibleCaseload(
          username = "JOHNSMITH_ADM",
          caseloadId = "MDI",
          createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
          createdBy = "JIM_BEAM4",
        ),
        MigratedUserAccessibleCaseload(
          username = "JOHNSMITH_ADM",
          caseloadId = "NWEB",
          createdTimestamp = LocalDateTime.parse("2020-12-04T10:42:43"),
          createdBy = "JIM_BEAM4",
        ),
      ),
    )

    fun migrateStaffResponse(nomisStaffId: Long, dpsStaffId: UUID) = UserMigrationResponse(
      userId = dpsStaffId,
      staffId = nomisStaffId.toString(),
      // TOD this should be a list
      username = "JSMITH_ADM",
    )
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubMigrateStaff(
    nomisStaffId: Long = 1234,
    dpsStaffId: UUID = UUID.randomUUID(),
    response: UserMigrationResponse =
      migrateStaffResponse(nomisStaffId, dpsStaffId),
  ) {
    stubFor(
      post("/prison-users/migrate/staff")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }
}
