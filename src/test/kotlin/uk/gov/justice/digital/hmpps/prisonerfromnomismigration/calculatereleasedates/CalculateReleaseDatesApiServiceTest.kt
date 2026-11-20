package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.CalculateReleaseDatesApiExtension.Companion.calculateReleaseDatesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.CalculateReleaseDatesApiExtension.Companion.legacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.CalculateReleaseDatesApiExtension.Companion.legacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.CalculateReleaseDatesApiExtension.Companion.legacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.CalculateReleaseDatesApiExtension.Companion.legacySentenceCalculationToCrdsMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@ExtendWith(CalculateReleaseDatesApiExtension::class)
@SpringAPIServiceTest
@Import(CalculateReleaseDatesApiService::class, CalculateReleaseDatesConfiguration::class)
class CalculateReleaseDatesApiServiceTest {
  @Autowired
  private lateinit var apiService: CalculateReleaseDatesApiService

  @Nested
  inner class SyncNew {
    @Test
    fun `will pass oauth2 token to endpoint`() = runTest {
      calculateReleaseDatesApi.stubSyncNew()

      apiService.syncNew(legacySentenceCalculationDto())

      calculateReleaseDatesApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the sync endpoint with the request body`() = runTest {
      calculateReleaseDatesApi.stubSyncNew()
      val request = legacySentenceCalculationDto()

      apiService.syncNew(request)

      val pattern = postRequestedFor(urlPathEqualTo("/legacy-sentence-calculation/sync"))
      calculateReleaseDatesApi.verify(pattern)
      assertThat(CalculateReleaseDatesApiExtension.getRequestBody<LegacySentenceCalculationDto>(pattern)).isEqualTo(request)
    }

    @Test
    fun `will return the response`() = runTest {
      val response = legacySentenceCalculationToCrdsMappingResult(crdsCalculationId = 99)
      calculateReleaseDatesApi.stubSyncNew(response)

      assertThat(apiService.syncNew(legacySentenceCalculationDto())).isEqualTo(response)
    }
  }

  @Nested
  inner class SyncUpdated {
    @Test
    fun `will pass oauth2 token to endpoint`() = runTest {
      calculateReleaseDatesApi.stubSyncUpdated()

      apiService.syncUpdated(legacySentenceCalculationDto())

      calculateReleaseDatesApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the sync endpoint with the request body`() = runTest {
      calculateReleaseDatesApi.stubSyncUpdated()
      val request = legacySentenceCalculationDto()

      apiService.syncUpdated(request)

      val pattern = putRequestedFor(urlPathEqualTo("/legacy-sentence-calculation/sync"))
      calculateReleaseDatesApi.verify(pattern)
      assertThat(CalculateReleaseDatesApiExtension.getRequestBody<LegacySentenceCalculationDto>(pattern)).isEqualTo(request)
    }

    @Test
    fun `will return the response`() = runTest {
      val response = legacySentenceCalculationToCrdsMappingResult(crdsCalculationId = 99)
      calculateReleaseDatesApi.stubSyncUpdated(response)

      assertThat(apiService.syncUpdated(legacySentenceCalculationDto())).isEqualTo(response)
    }
  }

  @Nested
  inner class SyncDeleted {
    @Test
    fun `will pass oauth2 token to endpoint`() = runTest {
      calculateReleaseDatesApi.stubSyncDeleted()

      apiService.syncDeleted(12345)

      calculateReleaseDatesApi.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete endpoint`() = runTest {
      calculateReleaseDatesApi.stubSyncDeleted(12345)

      apiService.syncDeleted(12345)

      calculateReleaseDatesApi.verify(deleteRequestedFor(urlPathEqualTo("/legacy-sentence-calculation/sync/12345")))
    }
  }

  @Nested
  inner class Migrate {
    @Test
    fun `will pass oauth2 token to endpoint`() = runTest {
      calculateReleaseDatesApi.stubMigrate()

      apiService.migrate("A1234BC", legacySentenceCalculationMigrationRequest())

      calculateReleaseDatesApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the migrate endpoint with the request body`() = runTest {
      calculateReleaseDatesApi.stubMigrate("A1234BC")
      val request = legacySentenceCalculationMigrationRequest()

      apiService.migrate("A1234BC", request)

      val pattern = postRequestedFor(urlPathEqualTo("/legacy-sentence-calculation/migrate/A1234BC"))
      calculateReleaseDatesApi.verify(pattern)
      assertThat(CalculateReleaseDatesApiExtension.getRequestBody<LegacySentenceCalculationMigrationRequest>(pattern)).isEqualTo(request)
    }

    @Test
    fun `will return the response`() = runTest {
      val response = legacySentenceCalculationMigrationResponse()
      calculateReleaseDatesApi.stubMigrate("A1234BC", response)

      assertThat(apiService.migrate("A1234BC", legacySentenceCalculationMigrationRequest())).isEqualTo(response)
    }
  }

  @Nested
  inner class GetAllForPrisoner {
    @Test
    fun `will pass oauth2 token to endpoint`() = runTest {
      calculateReleaseDatesApi.stubGetAllForPrisoner()

      apiService.getAllForPrisoner("A1234BC")

      calculateReleaseDatesApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get endpoint`() = runTest {
      calculateReleaseDatesApi.stubGetAllForPrisoner("A1234BC")

      apiService.getAllForPrisoner("A1234BC")

      calculateReleaseDatesApi.verify(getRequestedFor(urlPathEqualTo("/legacy-sentence-calculation/A1234BC")))
    }

    @Test
    fun `will return the response`() = runTest {
      val response = listOf(legacySentenceCalculationDto())
      calculateReleaseDatesApi.stubGetAllForPrisoner("A1234BC", response)

      assertThat(apiService.getAllForPrisoner("A1234BC")).isEqualTo(response)
    }
  }
}
