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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionRequest
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
      cprCorePersonServer.stubSyncCreateOffenderBelief("A1234BC")

      apiService.syncCreateOffenderBelief("A1234BC", prisonReligionRequest())

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will post request data to the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateOffenderBelief()

      apiService.syncCreateOffenderBelief("A1234BC", prisonReligionRequest())

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("religions[0].religionCode", equalTo("BAPT"))
          .withRequestBodyJsonPath("religions[0].current", equalTo("true"))
          .withRequestBodyJsonPath("religions[0].changeReasonKnown", equalTo("true"))
          .withRequestBodyJsonPath("religions[0].comments", equalTo("This is a comment"))
          .withRequestBodyJsonPath("religions[0].verified", equalTo("true"))
          .withRequestBodyJsonPath("religions[0].startDate", equalTo("2020-11-01"))
          .withRequestBodyJsonPath("religions[0].endDate", equalTo("2022-07-19"))
          .withRequestBodyJsonPath("religions[0].modifyDateTime", equalTo("2020-11-01T04:05:00"))
          .withRequestBodyJsonPath("religions[0].modifyUserId", equalTo("FRED_ADM")),

      )
    }

    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateOffenderBelief("A1234BC")

      apiService.syncCreateOffenderBelief("A1234BC", prisonReligionRequest())

      cprCorePersonServer.verify(
        postRequestedFor(urlPathEqualTo("/syscon-sync/religion/A1234BC")),
      )
    }

    @Test
    fun `should throw if bad request`() = runTest {
      cprCorePersonServer.stubSyncCreateOffenderBelief("A1234BC", status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.syncCreateOffenderBelief("A1234BC", prisonReligionRequest())
      }
    }
  }

  fun prisonReligionRequest() = PrisonReligionRequest(
    religions = listOf(
      PrisonReligion(
        religionCode = "BAPT",
        current = true,
        changeReasonKnown = true,
        comments = "This is a comment",
        verified = true,
        startDate = LocalDate.parse("2020-11-01"),
        endDate = LocalDate.parse("2022-07-19"),
        modifyDateTime = LocalDateTime.parse("2020-11-01T04:05:00"),
        modifyUserId = "FRED_ADM",
      ),
    ),
  )
}
