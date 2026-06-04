package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.time.LocalDateTime

@ExtendWith(NomisApiExtension::class)
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
      mockServer.stubGetStaffDetails(nomisStaffId = 10000)

      apiService.getStaffDetails(staffId = 10000)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetStaffDetails(nomisStaffId = 10000)

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
            assertThat(caseloadId).isEqualTo("LEI")
            assertThat(roles.size).isEqualTo(0)
            assertThat(audit.createDatetime).isEqualTo(LocalDateTime.parse("2016-08-01T10:55"))
            assertThat(audit.createUsername).isEqualTo("KOFEADDY")
            assertThat(audit.modifyDatetime).isEqualTo(LocalDateTime.parse("2017-08-01T10:55"))
            assertThat(audit.modifyUserId).isEqualTo("KOFE_MOD")
          }
          with(caseloads[1]) {
            assertThat(caseloadId).isEqualTo("MDI")
            assertThat(roles.size).isEqualTo(0)
            assertThat(audit.createDatetime).isEqualTo(LocalDateTime.parse("2016-08-01T10:55"))
            assertThat(audit.createUsername).isEqualTo("KOFEADDY")
            assertThat(audit.modifyDatetime).isEqualTo(LocalDateTime.parse("2017-08-01T10:55"))
            assertThat(audit.modifyUserId).isEqualTo("KOFE_MOD")
          }
          with(caseloads[2]) {
            assertThat(caseloadId).isEqualTo("NWEB")
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

  @Nested
  inner class GetStaffIds {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      mockServer.stubGetStaffIds(
        pageNumber = 0,
        pageSize = 20,
        content = listOf(
          StaffIdResponse(
            staffId = 1234,
          ),
        ),
      )

      apiService.getStaffIds(
        pageNumber = 0,
        pageSize = 20,

      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get IDs endpoint`() = runTest {
      mockServer.stubGetStaffIds(
        pageNumber = 10,
        pageSize = 30,
        content = listOf(
          StaffIdResponse(
            staffId = 1234,
          ),
        ),
      )

      apiService.getStaffIds(
        pageNumber = 10,
        pageSize = 30,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/staff/ids"))
          .withQueryParam("page", equalTo("10"))
          .withQueryParam("size", equalTo("30")),
      )
    }

    @Test
    fun `will return page metadata in the response so it can be used by migration service`() = runTest {
      mockServer.stubGetStaffIds(
        content = (1..20).map {
          StaffIdResponse(
            staffId = it.toLong(),
          )
        },
        pageNumber = 10,
        pageSize = 20,
        totalElements = 1000,
      )

      val pageOfIds = apiService.getStaffIds(
        pageNumber = 10,
        pageSize = 20,
      )

      assertThat(pageOfIds.content).hasSize(20)
      assertThat(pageOfIds.page?.totalPages).isEqualTo(50)
      assertThat(pageOfIds.page?.totalElements).isEqualTo(1000)
      assertThat(pageOfIds.page?.propertySize).isEqualTo(20)
    }
  }
}
