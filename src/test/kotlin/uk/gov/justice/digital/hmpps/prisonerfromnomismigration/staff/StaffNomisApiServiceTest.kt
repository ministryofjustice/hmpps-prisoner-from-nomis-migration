package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffIdResponse
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(StaffNomisApiService::class, StaffNomisApiMockServer::class)
class StaffNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: StaffNomisApiService

  @Autowired
  private lateinit var mockServer: StaffNomisApiMockServer

  @Nested
  inner class GetStaffDetail {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetStaffDetails(nomisStaffStaffId = 10000)

      apiService.getStaffDetails(staffId = 10000)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetStaffDetails(nomisStaffStaffId = 10000)

      apiService.getStaffDetails(staffId = 10000)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/staff/10000")),
      )
    }

    @Test
    fun `will return staff details`() = runTest {
      mockServer.stubGetStaffDetails()

      val staff = apiService.getStaffDetails(1234)

      with(staff) {
        assertThat(id).isEqualTo(1234)
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
          assertThat(lastLoggedIn).isEqualTo("2026-03-17T12:30:00")
          assertThat(audit.createDatetime).isEqualTo(LocalDateTime.parse("2016-08-01T10:55"))
          assertThat(audit.createUsername).isEqualTo("KOFEADDY")
          assertThat(audit.modifyDatetime).isEqualTo(LocalDateTime.parse("2017-08-01T10:55"))
          assertThat(audit.modifyUserId).isEqualTo("KOFE_MOD")

          assertThat(caseloads.size).isEqualTo(3)
          with(caseloads[0]) {
            assertThat(caseload).isEqualTo("LEI")
            assertThat(roles[0].code).isEqualTo("NOMIS_CODE_1")
            assertThat(audit.createDatetime).isEqualTo(LocalDateTime.parse("2016-08-01T10:55"))
            assertThat(audit.createUsername).isEqualTo("KOFEADDY")
            assertThat(audit.modifyDatetime).isEqualTo(LocalDateTime.parse("2017-08-01T10:55"))
            assertThat(audit.modifyUserId).isEqualTo("KOFE_MOD")
          }
          with(caseloads[1]) {
            assertThat(caseload).isEqualTo("MDI")
            assertThat(roles.size).isEqualTo(0)
          }
          with(caseloads[2]) {
            assertThat(caseload).isEqualTo("NWEB")
            assertThat(audit.createDatetime).isEqualTo(LocalDateTime.parse("2016-08-01T10:55"))
            assertThat(audit.createUsername).isEqualTo("KOFEADDY")
            assertThat(audit.modifyDatetime).isEqualTo(LocalDateTime.parse("2017-08-01T10:55"))
            assertThat(audit.modifyUserId).isEqualTo("KOFE_MOD")
            assertThat(roles[0].code).isEqualTo("DPS_CODE_1")
            assertThat(roles[0].audit.createDatetime).isEqualTo(LocalDateTime.parse("2016-08-01T10:55"))
            assertThat(roles[0].audit.createUsername).isEqualTo("KOFEADDY")
            assertThat(roles[0].audit.modifyDatetime).isEqualTo(LocalDateTime.parse("2017-08-01T10:55"))
            assertThat(roles[0].audit.modifyUserId).isEqualTo("KOFE_MOD")
            assertThat(roles[1].code).isEqualTo("DPS_CODE_2")
          }
        }
      }
    }
  }

  @Nested
  inner class GetStaffIdsFromId {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetStaffIdsFromId(
        content = listOf(
          StaffIdResponse(
            staffId = 1234,
          ),
        ),
      )

      apiService.getStaffIdsFromId(
        lastStaffId = 0,
        pageSize = 20,
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get IDs endpoint`() = runTest {
      mockServer.stubGetStaffIdsFromId(
        staffId = 99,
        content = listOf(
          StaffIdResponse(
            staffId = 1234,
          ),
        ),
      )

      apiService.getStaffIdsFromId(
        lastStaffId = 99,
        pageSize = 30,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/staff/ids/all-from-id"))
          .withQueryParam("staffId", equalTo("99"))
          .withQueryParam("size", equalTo("30")),
      )
    }
  }
}
