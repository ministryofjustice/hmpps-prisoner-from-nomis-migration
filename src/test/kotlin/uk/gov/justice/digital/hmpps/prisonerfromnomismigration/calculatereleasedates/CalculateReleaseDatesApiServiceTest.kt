package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.CalculateReleaseDatesDpsApiExtension.Companion.calculateReleaseDatesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.CalculateReleaseDatesDpsApiMockServer.Companion.legacySentenceCalculation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.CalculateReleaseDatesDpsApiMockServer.Companion.mappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.CalculateReleaseDatesDpsApiMockServer.Companion.migrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

private const val PRISONER_ID = "A1234KT"
private const val NOMIS_CALCULATION_ID = 12345L

@ExtendWith(CalculateReleaseDatesDpsApiExtension::class)
@SpringAPIServiceTest
@Import(CalculateReleaseDatesApiService::class, CalculateReleaseDatesConfiguration::class)
class CalculateReleaseDatesApiServiceTest {
  @Autowired
  private lateinit var apiService: CalculateReleaseDatesApiService

  @Nested
  inner class MigratePrisoner {
    private val request = LegacySentenceCalculationMigrationRequest(listOf(legacySentenceCalculation()))

    @Test
    fun `will pass oauth2 token to service`() = runTest {
      calculateReleaseDatesApi.stubMigratePrisoner(PRISONER_ID)

      apiService.migratePrisoner(PRISONER_ID, request)

      calculateReleaseDatesApi.verify(
        postRequestedFor(urlEqualTo("/legacy-sentence-calculation/migrate/$PRISONER_ID"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the migrate endpoint with the calculations`() = runTest {
      calculateReleaseDatesApi.stubMigratePrisoner(PRISONER_ID)

      apiService.migratePrisoner(PRISONER_ID, request)

      calculateReleaseDatesApi.verify(
        postRequestedFor(urlEqualTo("/legacy-sentence-calculation/migrate/$PRISONER_ID"))
          .withRequestBody(matchingJsonPath("sentenceCalculations[0].nomisCalculationId", equalTo("$NOMIS_CALCULATION_ID")))
          .withRequestBody(matchingJsonPath("sentenceCalculations[0].prisonerId", equalTo(PRISONER_ID))),
      )
    }

    @Test
    fun `will return the mapping results`() = runTest {
      calculateReleaseDatesApi.stubMigratePrisoner(PRISONER_ID, migrationResponse(mappingResult(NOMIS_CALCULATION_ID, 6789)))

      val response = apiService.migratePrisoner(PRISONER_ID, request)

      assertThat(response.mappingResults).hasSize(1)
      assertThat(response.mappingResults[0].nomisCalculationId).isEqualTo(NOMIS_CALCULATION_ID)
      assertThat(response.mappingResults[0].crdsCalculationId).isEqualTo(6789)
    }
  }

  @Nested
  inner class SyncNew {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      calculateReleaseDatesApi.stubSyncNew()

      apiService.syncNew(legacySentenceCalculation())

      calculateReleaseDatesApi.verify(
        postRequestedFor(urlEqualTo("/legacy-sentence-calculation/sync"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the sync endpoint with the calculation`() = runTest {
      calculateReleaseDatesApi.stubSyncNew()

      apiService.syncNew(legacySentenceCalculation())

      calculateReleaseDatesApi.verify(
        postRequestedFor(urlEqualTo("/legacy-sentence-calculation/sync"))
          .withRequestBody(matchingJsonPath("nomisCalculationId", equalTo("$NOMIS_CALCULATION_ID")))
          .withRequestBody(matchingJsonPath("prisonerId", equalTo(PRISONER_ID))),
      )
    }

    @Test
    fun `will return the mapping result`() = runTest {
      calculateReleaseDatesApi.stubSyncNew(mappingResult(NOMIS_CALCULATION_ID, 6789))

      val response = apiService.syncNew(legacySentenceCalculation())

      assertThat(response.nomisCalculationId).isEqualTo(NOMIS_CALCULATION_ID)
      assertThat(response.crdsCalculationId).isEqualTo(6789)
    }
  }

  @Nested
  inner class SyncUpdated {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      calculateReleaseDatesApi.stubSyncUpdated()

      apiService.syncUpdated(legacySentenceCalculation())

      calculateReleaseDatesApi.verify(
        putRequestedFor(urlEqualTo("/legacy-sentence-calculation/sync"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the sync endpoint with the calculation`() = runTest {
      calculateReleaseDatesApi.stubSyncUpdated()

      apiService.syncUpdated(legacySentenceCalculation())

      calculateReleaseDatesApi.verify(
        putRequestedFor(urlEqualTo("/legacy-sentence-calculation/sync"))
          .withRequestBody(matchingJsonPath("nomisCalculationId", equalTo("$NOMIS_CALCULATION_ID"))),
      )
    }

    @Test
    fun `will return the mapping result`() = runTest {
      calculateReleaseDatesApi.stubSyncUpdated(mappingResult(NOMIS_CALCULATION_ID, null))

      val response = apiService.syncUpdated(legacySentenceCalculation())

      assertThat(response.nomisCalculationId).isEqualTo(NOMIS_CALCULATION_ID)
      assertThat(response.crdsCalculationId).isNull()
    }
  }

  @Nested
  inner class SyncDeleted {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      calculateReleaseDatesApi.stubSyncDeleted(NOMIS_CALCULATION_ID)

      apiService.syncDeleted(NOMIS_CALCULATION_ID)

      calculateReleaseDatesApi.verify(
        deleteRequestedFor(urlEqualTo("/legacy-sentence-calculation/sync/$NOMIS_CALCULATION_ID"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the delete endpoint with the NOMIS calculation id`() = runTest {
      calculateReleaseDatesApi.stubSyncDeleted(NOMIS_CALCULATION_ID)

      apiService.syncDeleted(NOMIS_CALCULATION_ID)

      calculateReleaseDatesApi.verify(deleteRequestedFor(urlEqualTo("/legacy-sentence-calculation/sync/$NOMIS_CALCULATION_ID")))
    }
  }

  @Nested
  inner class GetAllForPrisoner {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      calculateReleaseDatesApi.stubGetAllForPrisoner(PRISONER_ID)

      apiService.getAllForPrisoner(PRISONER_ID)

      calculateReleaseDatesApi.verify(
        getRequestedFor(urlEqualTo("/legacy-sentence-calculation/$PRISONER_ID"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the endpoint with the prisoner id and return the calculations`() = runTest {
      calculateReleaseDatesApi.stubGetAllForPrisoner(PRISONER_ID)

      val response = apiService.getAllForPrisoner(PRISONER_ID)

      calculateReleaseDatesApi.verify(getRequestedFor(urlEqualTo("/legacy-sentence-calculation/$PRISONER_ID")))
      assertThat(response).hasSize(1)
      assertThat(response[0].nomisCalculationId).isEqualTo(NOMIS_CALCULATION_ID)
      assertThat(response[0].prisonerId).isEqualTo(PRISONER_ID)
    }
  }
}
