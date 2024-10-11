package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonConfiguration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesSyncResponse
import java.time.LocalDateTime
import java.time.ZoneId

@SpringAPIServiceTest
@Import(PhysicalAttributesDpsApiService::class, PrisonPersonConfiguration::class, PhysicalAttributesDpsApiMockServer::class)
class PhysicalAttributesDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: PhysicalAttributesDpsApiService

  @Autowired
  private lateinit var dpsApi: PhysicalAttributesDpsApiMockServer

  @Nested
  inner class SyncPhysicalAttributes {
    private val prisonerNumber = "A1234AA"
    private val height = 180
    private val weight = 80
    private val appliesFrom = LocalDateTime.now().minusDays(1L)
    private val appliesTo = LocalDateTime.now()
    private val latestBooking = true
    private val createdAt = LocalDateTime.now()
    private val createdBy = "A_USER"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubSyncPhysicalAttributes(aResponse())

      apiService.syncPhysicalAttributes(prisonerNumber, height, weight, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/physical-attributes"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubSyncPhysicalAttributes(aResponse())

      apiService.syncPhysicalAttributes(prisonerNumber, height, weight, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/physical-attributes"))
          .withRequestBody(matchingJsonPath("height", equalTo(height.toString())))
          .withRequestBody(matchingJsonPath("weight", equalTo(weight.toString())))
          .withRequestBody(matchingJsonPath("appliesFrom", equalTo(appliesFrom.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("appliesTo", equalTo(appliesTo.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("createdAt", equalTo(createdAt.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("createdBy", equalTo(createdBy))),
      )
    }

    @Test
    fun `should not pass null data to the service`() = runTest {
      dpsApi.stubSyncPhysicalAttributes(aResponse())

      apiService.syncPhysicalAttributes(prisonerNumber, null, null, appliesFrom, null, latestBooking, createdAt, createdBy)

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/physical-attributes"))
          .withRequestBody(matchingJsonPath("height", absent()))
          .withRequestBody(matchingJsonPath("weight", absent()))
          .withRequestBody(matchingJsonPath("appliesTo", absent())),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubSyncPhysicalAttributes(aResponse(ids = listOf(321)))

      val response = apiService.syncPhysicalAttributes(prisonerNumber, height, weight, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      assertThat(response.fieldHistoryInserted).containsExactly(321L)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubSyncPhysicalAttributes(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncPhysicalAttributes(prisonerNumber, height, weight, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)
      }
    }

    private fun aResponse(ids: List<Long> = listOf(1)) = PhysicalAttributesSyncResponse(ids)
  }

  @Nested
  inner class MigratePhysicalAttributes {
    private val prisonerNumber = "A1234AA"
    private val height = 180
    private val weight = 80
    private val appliesFrom = LocalDateTime.now().minusDays(1L)
    private val appliesTo = LocalDateTime.now()
    private val createdAt = LocalDateTime.now()
    private val createdBy = "A_USER"
    private val latestBooking = true

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubMigratePhysicalAttributes(prisonerNumber, aResponse())

      apiService.migratePhysicalAttributes(
        prisonerNumber,
        listOf(
          apiService.migratePhysicalAttributesRequest(height, weight, appliesFrom, appliesTo, createdAt, createdBy, latestBooking),
        ),
      )
      dpsApi.verify(
        putRequestedFor(urlPathMatching("/migration/prisoners/$prisonerNumber/physical-attributes"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubMigratePhysicalAttributes(prisonerNumber, aResponse())

      apiService.migratePhysicalAttributes(
        prisonerNumber,
        listOf(
          apiService.migratePhysicalAttributesRequest(height, weight, appliesFrom, appliesTo, createdAt, createdBy, latestBooking),
        ),
      )

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/migration/prisoners/$prisonerNumber/physical-attributes"))
          .withRequestBody(matchingJsonPath("$[0].height", equalTo(height.toString())))
          .withRequestBody(matchingJsonPath("$[0].weight", equalTo(weight.toString())))
          .withRequestBody(matchingJsonPath("$[0].appliesFrom", equalTo(appliesFrom.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("$[0].appliesTo", equalTo(appliesTo.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("$[0].createdAt", equalTo(createdAt.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("$[0].createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("$[0].latestBooking", equalTo(latestBooking.toString()))),
      )
    }

    @Test
    fun `should not pass null data to the service`() = runTest {
      dpsApi.stubMigratePhysicalAttributes(prisonerNumber, aResponse())

      apiService.migratePhysicalAttributes(
        prisonerNumber,
        listOf(
          apiService.migratePhysicalAttributesRequest(null, null, appliesFrom, null, createdAt, createdBy, latestBooking),
        ),
      )

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/migration/prisoners/$prisonerNumber/physical-attributes"))
          .withRequestBody(matchingJsonPath("$[0].height", absent()))
          .withRequestBody(matchingJsonPath("$[0].weight", absent()))
          .withRequestBody(matchingJsonPath("$[0].appliesTo", absent())),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubMigratePhysicalAttributes(prisonerNumber, aResponse())

      val response = apiService.migratePhysicalAttributes(
        prisonerNumber,
        listOf(
          apiService.migratePhysicalAttributesRequest(height, weight, appliesFrom, appliesTo, createdAt, createdBy, latestBooking),
        ),
      )

      assertThat(response.fieldHistoryInserted).containsExactly(321)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubMigratePhysicalAttributes(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.migratePhysicalAttributes(
          prisonerNumber,
          listOf(
            apiService.migratePhysicalAttributesRequest(height, weight, appliesFrom, appliesTo, createdAt, createdBy, latestBooking),
          ),
        )
      }
    }

    private fun aResponse(ids: List<Long> = listOf(321)) = PhysicalAttributesMigrationResponse(ids)
  }
}
