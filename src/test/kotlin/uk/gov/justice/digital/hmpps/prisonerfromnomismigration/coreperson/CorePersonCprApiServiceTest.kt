package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiExtension.Companion.cprCorePersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiMockServer.Companion.migrateCorePersonRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(CorePersonCprApiService::class, CorePersonConfiguration::class)
class CorePersonCprApiServiceTest {
  @Autowired
  private lateinit var apiService: CorePersonCprApiService

  @Nested
  @Disabled
  inner class MigrateCorePerson {
    @Test
    internal fun `will pass oath2 token to core endpoint`() = runTest {
      // cprCorePersonServer.stubMigrateCorePerson()

      apiService.migrateCorePerson("A1234BC", migrateCorePersonRequest())

      cprCorePersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      // cprCorePersonServer.stubMigrateCorePerson("A4321BC")

      apiService.migrateCorePerson("A4321BC", migrateCorePersonRequest())

      cprCorePersonServer.verify(
        putRequestedFor(urlPathEqualTo("/syscon-sync/A4321BC")),
      )
    }
  }

  @Nested
  inner class SyncOffenderBelief {
    @Test
    internal fun `will pass oath2 token to sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateOffenderBelief()

      apiService.syncCreateOffenderBelief(prisonReligion())

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will post request data to the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateOffenderBelief()

      apiService.syncCreateOffenderBelief(prisonReligion())

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("prisonNumber", equalTo("A1234BC"))
          .withRequestBodyJsonPath("religionCode", equalTo("BAPT"))
          .withRequestBodyJsonPath("current", equalTo("true"))
          .withRequestBodyJsonPath("religionStatus", equalTo("R_STATUS"))
          .withRequestBodyJsonPath("changeReasonKnown", equalTo("They converted"))
          .withRequestBodyJsonPath("comments", equalTo("This is a comment"))
          .withRequestBodyJsonPath("verified", equalTo("true"))
          .withRequestBodyJsonPath("startDate", equalTo("2020-11-01"))
          .withRequestBodyJsonPath("endDate", equalTo("2022-07-19"))
          .withRequestBodyJsonPath("createUserId", equalTo("JIM_GEN"))
          .withRequestBodyJsonPath("createDateTime", equalTo("2020-11-01T01:02:00"))
          .withRequestBodyJsonPath("createDisplayName", equalTo("Jim Brown"))
          .withRequestBodyJsonPath("modifyDateTime", equalTo("2020-11-01T04:05:00"))
          .withRequestBodyJsonPath("modifyUserId", equalTo("FRED_ADM"))
          .withRequestBodyJsonPath("modifyDisplayName", equalTo("Fred Smith")),

      )
    }

    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateOffenderBelief()

      apiService.syncCreateOffenderBelief(prisonReligion())

      cprCorePersonServer.verify(
        postRequestedFor(urlPathEqualTo("/syscon-sync/religion")),
      )
    }

    @Test
    fun `should throw if bad request`() = runTest {
      cprCorePersonServer.stubSyncCreateOffenderBelief(status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.syncCreateOffenderBelief(prisonReligion())
      }
    }
  }

  fun prisonReligion() = PrisonReligion(
    prisonNumber = "A1234BC",
    religionCode = "BAPT",
    current = true,
    // TODO What is religion status?
    religionStatus = "R_STATUS",
    changeReasonKnown = "They converted",
    comments = "This is a comment",
    verified = true,
    startDate = LocalDate.parse("2020-11-01"),
    endDate = LocalDate.parse("2022-07-19"),
    createUserId = "JIM_GEN",
    createDateTime = LocalDateTime.parse("2020-11-01T01:02:00"),
    createDisplayName = "Jim Brown",
    modifyDateTime = LocalDateTime.parse("2020-11-01T04:05:00"),
    modifyUserId = "FRED_ADM",
    modifyDisplayName = "Fred Smith",
  )
}
