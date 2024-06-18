package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonerprofile

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonerprofile.PrisonPersonDpsApiExtension.Companion.dpsPrisonPersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesHistoryDto
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

@SpringAPIServiceTest
@Import(PrisonPersonDpsApiService::class, PrisonerProfileConfiguration::class)
class PrisonPersonDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: PrisonPersonDpsApiService

  @Nested
  inner class SyncPhysicalAttributes {
    private val prisonerNumber = "A1234AA"
    private val height = 180
    private val weight = 80
    private val appliesFrom = LocalDateTime.now().minusDays(1L)
    private val appliesTo = LocalDateTime.now()
    private val createdAt = LocalDateTime.now()
    private val createdBy = "A_USER"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsPrisonPersonServer.stubSyncPrisonPerson(aResponse())

      apiService.syncPhysicalAttributes(prisonerNumber, height, weight, appliesFrom, appliesTo, createdAt, createdBy)

      dpsPrisonPersonServer.verify(
        putRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/physical-attributes"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsPrisonPersonServer.stubSyncPrisonPerson(aResponse())

      apiService.syncPhysicalAttributes(prisonerNumber, height, weight, appliesFrom, appliesTo, createdAt, createdBy)

      dpsPrisonPersonServer.verify(
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
      dpsPrisonPersonServer.stubSyncPrisonPerson(aResponse(heightCentimetres = null, weightKilograms = null, appliesToTime = null))

      apiService.syncPhysicalAttributes(prisonerNumber, null, null, appliesFrom, null, createdAt, createdBy)

      dpsPrisonPersonServer.verify(
        putRequestedFor(urlPathMatching("/sync/prisoners/$prisonerNumber/physical-attributes"))
          .withRequestBody(matchingJsonPath("height", absent()))
          .withRequestBody(matchingJsonPath("weight", absent()))
          .withRequestBody(matchingJsonPath("appliesTo", absent())),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsPrisonPersonServer.stubSyncPrisonPerson(aResponse(physicalAttributesHistoryId = 321))

      val response = apiService.syncPhysicalAttributes(prisonerNumber, height, weight, appliesFrom, appliesTo, createdAt, createdBy)

      assertThat(response.physicalAttributesHistoryId).isEqualTo(321L)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsPrisonPersonServer.stubSyncPrisonPerson(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.syncPhysicalAttributes(prisonerNumber, height, weight, appliesFrom, appliesTo, createdAt, createdBy)
      }
    }

    private fun aResponse(
      physicalAttributesHistoryId: Long = 123,
      heightCentimetres: Int? = height,
      weightKilograms: Int? = weight,
      appliesFromTime: ZonedDateTime = appliesFrom.atZone(ZoneId.of("Europe/London")),
      appliesToTime: ZonedDateTime? = appliesTo.atZone(ZoneId.of("Europe/London")),
      createdAtTime: ZonedDateTime = createdAt.atZone(ZoneId.of("Europe/London")),
      createdByUser: String = createdBy,
    ) = PhysicalAttributesHistoryDto(
      physicalAttributesHistoryId = physicalAttributesHistoryId,
      height = heightCentimetres,
      weight = weightKilograms,
      appliesFrom = appliesFromTime.toString(),
      appliesTo = appliesToTime?.toString(),
      createdAt = createdAtTime.toString(),
      createdBy = createdByUser,
    )
  }
}
