package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.identifyingmarks

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.DistinguishingMarkSyncResponse
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@SpringAPIServiceTest
@Import(DistinguishingMarksDpsApiService::class, PrisonPersonConfiguration::class, DistinguishingMarksDpsApiMockServer::class)
class DistinguishingMarksDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: DistinguishingMarksDpsApiService

  @Autowired
  private lateinit var dpsApi: DistinguishingMarksDpsApiMockServer

  @Nested
  inner class SyncCreateDistinguishingMarks {
    private val prisonerNumber = "A1234AA"
    private val markType = "TAT"
    private val bodyPart = "HEAD"
    private val side = "L"
    private val partOrientation = "FRONT"
    private val comment = "head tattoo left front"
    private val appliesFrom = LocalDateTime.now().minusDays(1L)
    private val appliesTo = LocalDateTime.now()
    private val latestBooking = true
    private val createdAt = LocalDateTime.now()
    private val createdBy = "A_USER"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubSyncCreateDistinguishingMark(prisonerNumber, aResponse())

      apiService.syncCreateDistinguishingMark(prisonerNumber, markType, bodyPart, side, partOrientation, comment, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      dpsApi.verify(
        postRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubSyncCreateDistinguishingMark(prisonerNumber, aResponse())

      apiService.syncCreateDistinguishingMark(prisonerNumber, markType, bodyPart, side, partOrientation, comment, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      dpsApi.verify(
        postRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks"))
          .withRequestBody(matchingJsonPath("markType", equalTo(markType)))
          .withRequestBody(matchingJsonPath("bodyPart", equalTo(bodyPart)))
          .withRequestBody(matchingJsonPath("side", equalTo(side)))
          .withRequestBody(matchingJsonPath("partOrientation", equalTo(partOrientation)))
          .withRequestBody(matchingJsonPath("comment", equalTo(comment)))
          .withRequestBody(matchingJsonPath("appliesFrom", equalTo(appliesFrom.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("appliesTo", equalTo(appliesTo.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("createdAt", equalTo(createdAt.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("latestBooking", equalTo(latestBooking.toString()))),
      )
    }

    @Test
    fun `should not pass null data to the service`() = runTest {
      dpsApi.stubSyncCreateDistinguishingMark(prisonerNumber, aResponse())

      apiService.syncCreateDistinguishingMark(prisonerNumber, markType, bodyPart, null, null, null, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      dpsApi.verify(
        postRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks"))
          .withRequestBody(matchingJsonPath("side", absent()))
          .withRequestBody(matchingJsonPath("partOrientation", absent()))
          .withRequestBody(matchingJsonPath("comment", absent())),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val dpsId = UUID.randomUUID()
      dpsApi.stubSyncCreateDistinguishingMark(prisonerNumber, aResponse(dpsId))

      val response = apiService.syncCreateDistinguishingMark(prisonerNumber, markType, bodyPart, side, partOrientation, comment, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      assertThat(response.uuid).isEqualTo(dpsId)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubSyncCreateDistinguishingMark(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncCreateDistinguishingMark(prisonerNumber, markType, bodyPart, side, partOrientation, comment, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)
      }
    }

    private fun aResponse(uuid: UUID = UUID.randomUUID()) = DistinguishingMarkSyncResponse(uuid)
  }

  @Nested
  inner class SyncUpdateDistinguishingMarks {
    private val prisonerNumber = "A1234AA"
    private val dpsId = UUID.randomUUID()
    private val markType = "TAT"
    private val bodyPart = "HEAD"
    private val side = "L"
    private val partOrientation = "FRONT"
    private val comment = "head tattoo left front"
    private val appliesFrom = LocalDateTime.now().minusDays(1L)
    private val appliesTo = LocalDateTime.now()
    private val latestBooking = true
    private val createdAt = LocalDateTime.now()
    private val createdBy = "A_USER"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubSyncUpdateDistinguishingMark(prisonerNumber, dpsId)

      apiService.syncUpdateDistinguishingMark(prisonerNumber, dpsId, markType, bodyPart, side, partOrientation, comment, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$dpsId"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubSyncUpdateDistinguishingMark(prisonerNumber, dpsId)

      apiService.syncUpdateDistinguishingMark(prisonerNumber, dpsId, markType, bodyPart, side, partOrientation, comment, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$dpsId"))
          .withRequestBody(matchingJsonPath("uuid", equalTo(dpsId.toString())))
          .withRequestBody(matchingJsonPath("markType", equalTo(markType)))
          .withRequestBody(matchingJsonPath("bodyPart", equalTo(bodyPart)))
          .withRequestBody(matchingJsonPath("side", equalTo(side)))
          .withRequestBody(matchingJsonPath("partOrientation", equalTo(partOrientation)))
          .withRequestBody(matchingJsonPath("comment", equalTo(comment)))
          .withRequestBody(matchingJsonPath("appliesFrom", equalTo(appliesFrom.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("appliesTo", equalTo(appliesTo.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("createdAt", equalTo(createdAt.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("latestBooking", equalTo(latestBooking.toString()))),
      )
    }

    @Test
    fun `should not pass null data to the service`() = runTest {
      dpsApi.stubSyncUpdateDistinguishingMark(prisonerNumber, dpsId)

      apiService.syncUpdateDistinguishingMark(prisonerNumber, dpsId, markType, bodyPart, null, null, null, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$dpsId"))
          .withRequestBody(matchingJsonPath("side", absent()))
          .withRequestBody(matchingJsonPath("partOrientation", absent()))
          .withRequestBody(matchingJsonPath("comment", absent())),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubSyncUpdateDistinguishingMark(prisonerNumber, dpsId)

      val response = apiService.syncUpdateDistinguishingMark(prisonerNumber, dpsId, markType, bodyPart, side, partOrientation, comment, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      assertThat(response.uuid).isEqualTo(dpsId)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubSyncUpdateDistinguishingMark(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncUpdateDistinguishingMark(prisonerNumber, dpsId, markType, bodyPart, side, partOrientation, comment, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)
      }
    }
  }

  @Nested
  inner class SyncDeleteDistinguishingMark {
    private val prisonerNumber = "A1234AA"
    private val dpsId = UUID.randomUUID()

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubSyncDeleteDistinguishingMark(prisonerNumber, dpsId)

      apiService.syncDeleteDistinguishingMark(prisonerNumber, dpsId)

      dpsApi.verify(
        deleteRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$dpsId"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubSyncDeleteDistinguishingMark(prisonerNumber, dpsId)

      val response = apiService.syncDeleteDistinguishingMark(prisonerNumber, dpsId)

      assertThat(response.uuid).isEqualTo(dpsId)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubSyncDeleteDistinguishingMark(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncDeleteDistinguishingMark(prisonerNumber, dpsId)
      }
    }
  }
}
