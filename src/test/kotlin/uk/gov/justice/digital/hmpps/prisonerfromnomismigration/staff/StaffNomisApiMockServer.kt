package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseloadResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RoleResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffAccount
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffIdsPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class StaffNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetStaffDetails(
    nomisStaffStaffId: Long = 1234L,
    staff: StaffDetails = staffDetails(nomisStaffStaffId),
    dpsRolesOnly: Boolean = true,
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/staff/$nomisStaffStaffId"))
        .withQueryParam("dpsRolesOnly", equalTo(dpsRolesOnly.toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(staff)),
        ),
    )
  }

  fun staffDetails(staffId: Long = 1234) = StaffDetails(
    id = staffId,
    firstName = "JOHN",
    lastName = "SMITH",
    status = "ACTIVE",
    email = "john.smith@justice.gov.uk",
    audit = audit(),
    accounts = listOf(
      StaffAccount(
        username = "JOHNSMITH_ADM",
        sourceCode = "USER",
        status = "OPEN",
        typeCode = "ADMIN",
        activeCaseloadId = "MDI",
        lastLoggedIn = LocalDateTime.parse("2026-03-17T12:30:00"),
        caseloads = listOf(
          CaseloadResponse(
            caseload = "LEI",
            roles = listOf(
              RoleResponse("NOMIS_CODE_1", name = "Nomis Role 1", audit = audit()),
            ),
            audit = audit(),
          ),
          CaseloadResponse(caseload = "MDI", roles = emptyList(), audit = audit()),
          CaseloadResponse(
            caseload = "NWEB",
            roles = listOf(
              RoleResponse(code = "DPS_CODE_1", name = "Dps Role 1", audit = audit()),
              RoleResponse(code = "DPS_CODE_2", name = "Dps Role 2", audit = audit()),
            ),
            audit = audit(),
          ),
        ),
        audit = audit(),
      ),
    ),
  )

  fun stubGetStaffIdsFromId(
    content: List<StaffIdResponse>,
    staffId: Long = 0,
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/staff/ids/all-from-id"))
        .withQueryParam("staffId", equalTo(staffId.toString())).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(StaffIdsPage(content))),
        ),
    )
  }

  fun audit() = NomisAudit(
    createDatetime = LocalDateTime.parse("2016-08-01T10:55:00"),
    createUsername = "KOFEADDY",
    createDisplayName = "KOFE ADDY",
    modifyDatetime = LocalDateTime.parse("2017-08-01T10:55:00"),
    modifyUserId = "KOFE_MOD",
  )

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
}
