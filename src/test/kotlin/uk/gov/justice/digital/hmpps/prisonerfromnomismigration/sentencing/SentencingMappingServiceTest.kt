package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

private const val ADJUSTMENT_ID = "05b332ad-58eb-4ec2-963c-c9c927856788"

@SpringAPIServiceTest
@Import(SentencingMappingService::class, SentencingConfiguration::class)
class SentencingMappingServiceTest {
  @Autowired
  private lateinit var sentencingMappingService: SentencingMappingService

  @Nested
  inner class CreateNomisSentencingAdjustmentSynchronisationMapping {
    @Test
    internal fun `will pass oath2 token to service`() {
      mappingApi.stubSentenceAdjustmentMappingCreate()

      runBlocking {
        sentencingMappingService.createNomisSentencingAdjustmentSynchronisationMapping(
          nomisAdjustmentId = 1234L,
          nomisAdjustmentCategory = "SENTENCE",
          adjustmentId = ADJUSTMENT_ID
        )
      }

      mappingApi.verify(
        postRequestedFor(
          urlPathEqualTo("/mapping/sentencing/adjustments")
        ).withHeader("Authorization", equalTo("Bearer ABCDE"))
      )
    }

    @Test
    internal fun `will create mapping`() {
      mappingApi.stubSentenceAdjustmentMappingCreate()

      runBlocking {
        sentencingMappingService.createNomisSentencingAdjustmentSynchronisationMapping(
          nomisAdjustmentId = 1234L,
          nomisAdjustmentCategory = "SENTENCE",
          adjustmentId = ADJUSTMENT_ID
        )
      }

      mappingApi.verify(
        postRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments"))
          .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo("1234")))
          .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("SENTENCE")))
          .withRequestBody(matchingJsonPath("adjustmentId", equalTo(ADJUSTMENT_ID)))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED")))
      )
    }
  }
}
