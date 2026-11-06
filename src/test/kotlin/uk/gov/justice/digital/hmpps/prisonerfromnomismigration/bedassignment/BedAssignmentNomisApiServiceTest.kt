package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension

private const val NOMIS_OFFENDER_NO = "A1234KT"

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(BedAssignmentNomisApiService::class, BedAssignmentConfiguration::class, BedAssignmentNomisApiMockServer::class)
class BedAssignmentNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: BedAssignmentNomisApiService

  @Autowired
  private lateinit var mockServer: BedAssignmentNomisApiMockServer

  @Nested
  inner class GetBedAssignments {
    @Test
    fun `passes the oauth token`() = runTest {
      mockServer.stubGetBedAssignmentsForPrisoner(NOMIS_OFFENDER_NO)

      apiService.getBedAssignments(NOMIS_OFFENDER_NO)

      mockServer.verify(getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `requests the prisoner assignment history`() = runTest {
      mockServer.stubGetBedAssignmentsForPrisoner(NOMIS_OFFENDER_NO)

      apiService.getBedAssignments(NOMIS_OFFENDER_NO)

      mockServer.verify(getRequestedFor(urlEqualTo("/prisoners/$NOMIS_OFFENDER_NO/bed-assignments")))
    }

    @Test
    fun `returns every history row`() = runTest {
      mockServer.stubGetBedAssignmentsForPrisoner(
        NOMIS_OFFENDER_NO,
        bedAssignmentHistoryWithRowsJson(
          bedAssignmentJson(bookingId = 123456, sequence = 1),
          bedAssignmentJson(bookingId = 234567, sequence = 2, livingUnitId = 1234),
        ),
      )

      val response = apiService.getBedAssignments(NOMIS_OFFENDER_NO)

      assertThat(response.bedAssignments).hasSize(2)
      assertThat(response.bedAssignments.map { it.bookingId }).containsExactly(123456L, 234567L)
      assertThat(response.bedAssignments.map { it.sequence }).containsExactly(1, 2)
    }
  }

  @Nested
  inner class GetBedAssignment {
    @Test
    fun `requests an individual assignment by booking and sequence`() = runTest {
      mockServer.stubGetBedAssignment(123456, 2)

      apiService.getBedAssignment(123456, 2)

      mockServer.verify(
        getRequestedFor(urlEqualTo("/prisoners/booking-id/123456/bed-assignments/2")),
      )
    }

    @Test
    fun `returns null when the row no longer exists`() = runTest {
      mockServer.stubGetBedAssignmentError(123456, 2, 404)

      assertThat(apiService.getBedAssignmentOrNull(123456, 2)).isNull()
    }
  }
}
