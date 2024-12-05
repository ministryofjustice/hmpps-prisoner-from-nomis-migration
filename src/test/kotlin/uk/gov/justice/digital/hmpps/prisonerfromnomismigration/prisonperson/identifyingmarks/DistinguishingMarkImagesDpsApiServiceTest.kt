package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.identifyingmarks

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
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonConfiguration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.DistinguishingMarkImageSyncResponse
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@SpringAPIServiceTest
@Import(DistinguishingMarkImagesDpsApiService::class, PrisonPersonConfiguration::class, DistinguishingMarkImagesDpsApiMockServer::class)
class DistinguishingMarkImagesDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: DistinguishingMarkImagesDpsApiService

  @Autowired
  private lateinit var dpsApi: DistinguishingMarkImagesDpsApiMockServer

  @Nested
  inner class SyncCreateDistinguishingMarkImages {
    private val prisonerNumber = "A1234AA"
    private val markId = UUID.randomUUID()
    private val imageSource = "FILE"
    private val appliesFrom = LocalDateTime.now().minusDays(1L)
    private val appliesTo = LocalDateTime.now()
    private val latestBooking = true
    private val createdAt = LocalDateTime.now()
    private val createdBy = "A_USER"
    private val image = FileSystemResource(File(javaClass.getResource("/images/tattoo.jpg")!!.file)).contentAsByteArray

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubSyncCreateDistinguishingMarkImage(prisonerNumber, markId, aResponse())

      apiService.syncCreateDistinguishingMarkImage(prisonerNumber, markId, imageSource, appliesFrom, appliesTo, latestBooking, createdAt, createdBy, image)

      dpsApi.verify(
        postRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$markId/images"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubSyncCreateDistinguishingMarkImage(prisonerNumber, markId, aResponse())

      apiService.syncCreateDistinguishingMarkImage(prisonerNumber, markId, imageSource, appliesFrom, appliesTo, latestBooking, createdAt, createdBy, image)

      dpsApi.verify(
        postRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$markId/images")),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      val imageId = UUID.randomUUID()
      dpsApi.stubSyncCreateDistinguishingMarkImage(prisonerNumber, markId, aResponse(imageId))

      val response = apiService.syncCreateDistinguishingMarkImage(prisonerNumber, markId, imageSource, appliesFrom, appliesTo, latestBooking, createdAt, createdBy, image)

      assertThat(response.uuid).isEqualTo(imageId)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubSyncCreateDistinguishingMarkImage(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncCreateDistinguishingMarkImage(prisonerNumber, markId, imageSource, appliesFrom, appliesTo, latestBooking, createdAt, createdBy, image)
      }
    }

    private fun aResponse(uuid: UUID = UUID.randomUUID()) = DistinguishingMarkImageSyncResponse(uuid)
  }

  @Nested
  inner class SyncUpdateDistinguishingMarkImages {
    private val prisonerNumber = "A1234AA"
    private val markId = UUID.randomUUID()
    private val imageId = UUID.randomUUID()
    private val default = true
    private val appliesFrom = LocalDateTime.now().minusDays(1L)
    private val appliesTo = LocalDateTime.now()
    private val latestBooking = true
    private val createdAt = LocalDateTime.now()
    private val createdBy = "A_USER"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubSyncUpdateDistinguishingMarkImage(prisonerNumber, markId, imageId)

      apiService.syncUpdateDistinguishingMarkImage(prisonerNumber, markId, imageId, default, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$markId/images/$imageId"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubSyncUpdateDistinguishingMarkImage(prisonerNumber, markId, imageId)

      apiService.syncUpdateDistinguishingMarkImage(prisonerNumber, markId, imageId, default, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$markId/images/$imageId"))
          .withRequestBody(matchingJsonPath("default", equalTo(default.toString())))
          .withRequestBody(matchingJsonPath("appliesFrom", equalTo(appliesFrom.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("appliesTo", equalTo(appliesTo.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("createdAt", equalTo(createdAt.atZone(ZoneId.of("Europe/London")).toString())))
          .withRequestBody(matchingJsonPath("createdBy", equalTo(createdBy)))
          .withRequestBody(matchingJsonPath("latestBooking", equalTo(latestBooking.toString()))),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubSyncUpdateDistinguishingMarkImage(prisonerNumber, markId, imageId)

      val response = apiService.syncUpdateDistinguishingMarkImage(prisonerNumber, markId, imageId, default, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)

      assertThat(response.uuid).isEqualTo(imageId)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubSyncUpdateDistinguishingMarkImage(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncUpdateDistinguishingMarkImage(prisonerNumber, markId, imageId, default, appliesFrom, appliesTo, latestBooking, createdAt, createdBy)
      }
    }
  }

  @Nested
  inner class SyncDeleteDistinguishingMark {
    private val prisonerNumber = "A1234AA"
    private val markId = UUID.randomUUID()
    private val imageId = UUID.randomUUID()

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubSyncDeleteDistinguishingMarkImage(prisonerNumber, markId, imageId)

      apiService.syncDeleteDistinguishingMarkImage(prisonerNumber, markId, imageId)

      dpsApi.verify(
        deleteRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/distinguishing-marks/$markId/images/$imageId"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubSyncDeleteDistinguishingMarkImage(prisonerNumber, markId, imageId)

      val response = apiService.syncDeleteDistinguishingMarkImage(prisonerNumber, markId, imageId)

      assertThat(response.uuid).isEqualTo(imageId)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubSyncDeleteDistinguishingMarkImage(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncDeleteDistinguishingMarkImage(prisonerNumber, markId, imageId)
      }
    }
  }
}
