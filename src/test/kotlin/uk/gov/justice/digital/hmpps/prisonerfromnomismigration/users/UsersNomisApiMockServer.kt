package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.users

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.UserAccount
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.UserDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class UsersNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetUserDetails(
    nomisStaffUserId: Long = 12345L,
    user: UserDetails = userDetails(nomisStaffUserId),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/users/$nomisStaffUserId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            jsonMapper.writeValueAsString(user),
          ),
      ),
    )
  }

  fun userDetails(userId: Long = 1234) = UserDetails(
    id = userId,
    firstName = "JOHN",
    lastName = "SMITH",
    status = "ACTIVE",
    email = "john.smith@justice.gov.uk",
    audit = audit(),
    accounts = listOf(
      UserAccount(
        username = "JOHNSMITH_ADM",
        sourceCode = "USER",
        status = "OPEN",
        typeCode = "ADMIN",
        activeCaseloadId = "MDI",
        lastLoggedIn = LocalDateTime.parse("2026-03-17T12:30:00"),
        caseloads = listOf("LEI", "MDI", "NWEB"),
        roles = listOf("DPS_CODE_1", "DPS_CODE_2", "NOMIS_CODE_1"),
        audit = audit(),
      ),
    ),
  )

  fun audit() = NomisAudit(
    createDatetime = LocalDateTime.parse("2016-08-01T10:55:00"),
    createUsername = "KOFEADDY",
    createDisplayName = "KOFE ADDY",
    modifyDatetime = LocalDateTime.parse("2017-08-01T10:55:00"),
    modifyUserId = "KOFE_MOD",
  )

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
}
