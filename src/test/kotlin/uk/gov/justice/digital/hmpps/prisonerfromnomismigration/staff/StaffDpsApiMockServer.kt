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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.OffsetDateTime
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

    fun migrateStaff() = UserMigrationRequestDps(
      user = UserDps(
        id = 1234,
        email = "john.smith@justice.gov.uk",
        firstName = "John",
        lastName = "Smith",
        status = UserStatusDps.ACTIVE,
        createdTimestamp = OffsetDateTime.parse("2020-12-04T10:42:43+00:00"),
        createdBy = "JIM_BEAM",
        modifiedTimestamp = OffsetDateTime.parse("2021-09-12T10:42:43+00:00"),
        modifiedBy = "FRED_BROWN",
      ),
      accounts = listOf(
        UserAccountDps(
          username = "JOHNSMITH_ADM",
          accountType = AccountTypeDps.ADMIN,
          accountStatus = AccountStatusDps.OPEN,
          activeCaseloadId = "MDI",
          createDateTime = OffsetDateTime.parse("2020-12-04T10:42:43+00:00"),
          createdBy = "JIM_BEAM2",
          lastModifiedDateTime = OffsetDateTime.parse("2020-12-04T10:42:43+00:00"),
          lastModifiedBy = "FRED_BROWN2",
        ),
      ),
      roles = listOf(
        UserRoleDps(
          username = "JOHNSMITH_ADM",
          roleCode = "DPS_CODE_1",
          createdTimestamp = OffsetDateTime.parse("2020-12-04T10:42:43+00:00"),
          createdBy = "JIM_BEAM3",
        ),
        UserRoleDps(
          username = "JOHNSMITH_ADM",
          roleCode = "DPS_CODE_2",
          createdTimestamp = OffsetDateTime.parse("2020-12-04T10:42:43+00:00"),
          createdBy = "JIM_BEAM3",
        ),
      ),
      accessibleCaseloads = listOf(
        UserAccessibleCaseloadDps(
          username = "JOHNSMITH_ADM",
          caseloadId = "MDI",
          createdTimestamp = OffsetDateTime.parse("2020-12-04T10:42:43+00:00"),
          createdBy = "JIM_BEAM4",
        ),
        UserAccessibleCaseloadDps(
          username = "JOHNSMITH_ADM",
          caseloadId = "NWEB",
          createdTimestamp = OffsetDateTime.parse("2020-12-04T10:42:43+00:00"),
          createdBy = "JIM_BEAM4",
        ),
      ),
    )

    fun migrateStaffResponse(nomisStaffId: Long, dpsStaffId: UUID) = UserMigrationResponseDps(
      userId = dpsStaffId,
      staffId = nomisStaffId.toString(),
      username = listOf("JSMITH_ADM"),
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

  fun stubMigrateStaff(nomisStaffId: Long = 1234, dpsStaffId: UUID = UUID.randomUUID(), response: UserMigrationResponseDps = migrateStaffResponse(nomisStaffId, dpsStaffId)) {
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
