package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.atPrisonPersonZone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.MigrationValueWithMetadataString
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.ProfileDetailsPhysicalAttributesMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.ProfileDetailsPhysicalAttributesMigrationResponse
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(ProfileDetailPhysicalAttributesDpsApiService::class, PrisonPersonConfiguration::class, ProfileDetailsPhysicalAttributesDpsApiMockServer::class)
class ProfileDetailsPhysicalAttributesDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: ProfileDetailPhysicalAttributesDpsApiService

  @Autowired
  private lateinit var dpsApi: ProfileDetailsPhysicalAttributesDpsApiMockServer

  @Nested
  inner class MigratePhysicalAttributes {
    private val prisonerNumber = "A1234AA"
    private val appliesFrom = LocalDateTime.now().minusDays(1L)
    private val appliesTo = LocalDateTime.now()
    private val buildCode = "SLIM"
    private val buildCreatedAt = LocalDateTime.now()
    private val buildCreatedBy = "A_USER"
    private val shoeSizeCode = "8.5"
    private val shoeSizeCreatedAt = LocalDateTime.now().minusDays(1)
    private val shoeSizeCreatedBy = "ANOTHER_USER"

    @Test
    fun `should pass auth token to the service`() = runTest {
      dpsApi.stubMigrateProfileDetailsPhysicalAttributes(prisonerNumber, aResponse())

      apiService.migrateProfileDetailsPhysicalAttributes(prisonerNumber, aRequest())

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/migration/prisoners/$prisonerNumber/profile-details-physical-attributes"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should pass data to the service`() = runTest {
      dpsApi.stubMigrateProfileDetailsPhysicalAttributes(prisonerNumber, aResponse())

      apiService.migrateProfileDetailsPhysicalAttributes(prisonerNumber, aRequest())

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/migration/prisoners/$prisonerNumber/profile-details-physical-attributes"))
          .withRequestBody(matchingJsonPath("[0].appliesFrom", equalTo(appliesFrom.atPrisonPersonZone())))
          .withRequestBody(matchingJsonPath("[0].appliesTo", equalTo(appliesTo.atPrisonPersonZone())))
          .withRequestBody(matchingJsonPath("[0].build.value", equalTo(buildCode)))
          .withRequestBody(matchingJsonPath("[0].build.lastModifiedAt", equalTo(buildCreatedAt.atPrisonPersonZone())))
          .withRequestBody(matchingJsonPath("[0].build.lastModifiedBy", equalTo(buildCreatedBy)))
          .withRequestBody(matchingJsonPath("[0].shoeSize.value", equalTo(shoeSizeCode)))
          .withRequestBody(matchingJsonPath("[0].shoeSize.lastModifiedAt", equalTo(shoeSizeCreatedAt.atPrisonPersonZone())))
          .withRequestBody(matchingJsonPath("[0].shoeSize.lastModifiedBy", equalTo(shoeSizeCreatedBy))),
      )
    }

    @Test
    fun `should not pass null data to the service`() = runTest {
      dpsApi.stubMigrateProfileDetailsPhysicalAttributes(prisonerNumber, aResponse())

      apiService.migrateProfileDetailsPhysicalAttributes(prisonerNumber, aRequest())

      dpsApi.verify(
        putRequestedFor(urlPathMatching("/migration/prisoners/$prisonerNumber/profile-details-physical-attributes"))
          .withRequestBody(matchingJsonPath("[0].hair", absent()))
          .withRequestBody(matchingJsonPath("[0].facialHair", absent()))
          .withRequestBody(matchingJsonPath("[0].face", absent()))
          .withRequestBody(matchingJsonPath("[0].leftEyeColour", absent()))
          .withRequestBody(matchingJsonPath("[0].rightEyeColour", absent())),
      )
    }

    @Test
    fun `should parse the response`() = runTest {
      dpsApi.stubMigrateProfileDetailsPhysicalAttributes(prisonerNumber, aResponse())

      val response = apiService.migrateProfileDetailsPhysicalAttributes(prisonerNumber, aRequest())

      assertThat(response.fieldHistoryInserted).containsExactly(321)
    }

    @Test
    fun `should throw when API returns an error`() = runTest {
      dpsApi.stubMigrateProfileDetailsPhysicalAttributes(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.migrateProfileDetailsPhysicalAttributes(prisonerNumber, aRequest())
      }
    }

    private fun aRequest() = listOf(
      ProfileDetailsPhysicalAttributesMigrationRequest(
        appliesFrom = appliesFrom.atPrisonPersonZone(),
        appliesTo = appliesTo.atPrisonPersonZone(),
        build = MigrationValueWithMetadataString(
          value = buildCode,
          lastModifiedAt = buildCreatedAt.atPrisonPersonZone(),
          lastModifiedBy = buildCreatedBy,
        ),
        shoeSize = MigrationValueWithMetadataString(
          value = shoeSizeCode,
          lastModifiedAt = shoeSizeCreatedAt.atPrisonPersonZone(),
          lastModifiedBy = shoeSizeCreatedBy,
        ),
      ),
    )

    private fun aResponse(ids: List<Long> = listOf(321)) = ProfileDetailsPhysicalAttributesMigrationResponse(ids)
  }
}
