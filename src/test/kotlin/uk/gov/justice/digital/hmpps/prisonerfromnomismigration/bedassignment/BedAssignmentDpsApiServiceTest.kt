package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.BedAssignmentDpsApiExtension.Companion.bedAssignmentApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.model.NomisBedAssignment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

private const val OFFENDER_NO = "A1234KT"
private const val DPS_ID = "e52d7268-6e10-41a8-a0b9-2319b32520d6"

@ExtendWith(BedAssignmentDpsApiExtension::class)
@SpringAPIServiceTest
@Import(BedAssignmentDpsApiService::class, BedAssignmentConfiguration::class)
class BedAssignmentDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: BedAssignmentDpsApiService

  @Autowired
  private lateinit var jsonMapper: JsonMapper

  @Nested
  inner class MigratePrisoner {
    @Test
    fun `passes the oauth token`() = runTest {
      bedAssignmentApi.stubMigrate(OFFENDER_NO, """[{"id":"$DPS_ID","bookingId":123456,"sequence":1}]""")

      apiService.migratePrisoner(OFFENDER_NO, listOf(nomisBedAssignment()))

      bedAssignmentApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `posts the complete assignment payload and returns stable DPS ids`() = runTest {
      bedAssignmentApi.stubMigrate(OFFENDER_NO, """[{"id":"$DPS_ID","bookingId":123456,"sequence":1}]""")

      val response = apiService.migratePrisoner(OFFENDER_NO, listOf(nomisBedAssignment()))

      bedAssignmentApi.verify(
        postRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/migrate/$OFFENDER_NO"))
          .withRequestBody(matchingJsonPath("$[0].bookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("$[0].sequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("$[0].livingUnitId", equalTo("987654")))
          .withRequestBody(matchingJsonPath("$[0].livingUnitDescription", equalTo("A-1-001")))
          .withRequestBody(matchingJsonPath("$[0].assignmentDateTime", equalTo("2024-01-02T03:04:05")))
          .withRequestBody(matchingJsonPath("$[0].assignmentReasonCode", equalTo("TRANSFER")))
          .withRequestBody(matchingJsonPath("$[0].createdBy", equalTo("NOMIS.USER"))),
      )
      assertThat(response).hasSize(1)
      assertThat(response.single().id.toString()).isEqualTo(DPS_ID)
      assertThat(response.single().bookingId).isEqualTo(123456)
      assertThat(response.single().sequence).isEqualTo(1)
    }
  }

  @Nested
  inner class Sync {
    @Test
    fun `passes the oauth token`() = runTest {
      bedAssignmentApi.stubSync(OFFENDER_NO)

      apiService.sync(OFFENDER_NO, listOf(nomisBedAssignment()))

      bedAssignmentApi.verify(putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `puts the full history and returns created updated and deleted rows`() = runTest {
      bedAssignmentApi.stubSync(
        OFFENDER_NO,
        """
        {
          "created":[{"id":"$DPS_ID","bookingId":123456,"sequence":1}],
          "updated":[{"id":"f3f31737-6ee3-4ec5-8a79-0ac110fe50e2","bookingId":123457,"sequence":2}],
          "deleted":[{"id":"8b07cbd9-0820-0a0f-c32f-a9429b618e0b","bookingId":123458,"sequence":3}]
        }
        """.trimIndent(),
      )

      val response = apiService.sync(OFFENDER_NO, listOf(nomisBedAssignment()))

      bedAssignmentApi.verify(
        putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$OFFENDER_NO"))
          .withRequestBody(matchingJsonPath("$[0].bookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("$[0].createdBy", equalTo("NOMIS.USER"))),
      )
      assertThat(response.created.single().id.toString()).isEqualTo(DPS_ID)
      assertThat(response.updated.single().bookingId).isEqualTo(123457)
      assertThat(response.deleted.single().id.toString()).isEqualTo("8b07cbd9-0820-0a0f-c32f-a9429b618e0b")
    }

    @Test
    fun `sends an empty history to remove all DPS assignments`() = runTest {
      bedAssignmentApi.stubSync(OFFENDER_NO)

      apiService.sync(OFFENDER_NO, emptyList())

      bedAssignmentApi.verify(
        putRequestedFor(urlEqualTo("/nomis-sync/bed-assignments/sync/$OFFENDER_NO"))
          .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.equalToJson("[]")),
      )
    }
  }

  private fun nomisBedAssignment(): NomisBedAssignment = jsonMapper.readValue(
    """
    {
      "bookingId": 123456,
      "sequence": 1,
      "livingUnitId": 987654,
      "livingUnitDescription": "A-1-001",
      "assignmentDateTime": "2024-01-02T03:04:05",
      "assignmentEndDateTime": null,
      "assignmentReasonCode": "TRANSFER",
      "locationId": null,
      "createdBy": "NOMIS.USER"
    }
    """.trimIndent(),
  )
}
