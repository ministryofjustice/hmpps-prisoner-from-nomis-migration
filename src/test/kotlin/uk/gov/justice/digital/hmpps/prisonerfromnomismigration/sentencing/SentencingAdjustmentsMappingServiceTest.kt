package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentencingAdjustmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentencingAdjustmentMappingDto.MappingType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentencingAdjustmentMappingDto.NomisAdjustmentCategory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.ADJUSTMENTS_CREATE_MAPPING_URL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

private const val ADJUSTMENT_ID = "05b332ad-58eb-4ec2-963c-c9c927856788"

@SpringAPIServiceTest
@Import(SentencingAdjustmentsMappingService::class, SentencingConfiguration::class)
class SentencingAdjustmentsMappingServiceTest {
  @Autowired
  private lateinit var sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService

  @Nested
  inner class CreateNomisSentencingAdjustmentSynchronisationMapping {
    @Test
    internal fun `will pass oath2 token to service`() {
      mappingApi.stubMappingCreate(ADJUSTMENTS_CREATE_MAPPING_URL)

      runBlocking {
        sentencingAdjustmentsMappingService.createMapping(
          SentencingAdjustmentMappingDto(
            nomisAdjustmentId = 1234L,
            nomisAdjustmentCategory = NomisAdjustmentCategory.SENTENCE,
            adjustmentId = ADJUSTMENT_ID,
            mappingType = MappingType.NOMIS_CREATED,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<SentencingAdjustmentMappingDto>>() {},
        )
      }

      mappingApi.verify(
        postRequestedFor(
          urlPathEqualTo("/mapping/sentencing/adjustments"),
        ).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will create mapping`() {
      mappingApi.stubMappingCreate(ADJUSTMENTS_CREATE_MAPPING_URL)

      runBlocking {
        sentencingAdjustmentsMappingService.createMapping(
          SentencingAdjustmentMappingDto(
            nomisAdjustmentId = 1234L,
            nomisAdjustmentCategory = NomisAdjustmentCategory.SENTENCE,
            adjustmentId = ADJUSTMENT_ID,
            mappingType = MappingType.NOMIS_CREATED,
          ),
          object : ParameterizedTypeReference<DuplicateErrorResponse<SentencingAdjustmentMappingDto>>() {},
        )
      }

      mappingApi.verify(
        postRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments"))
          .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo("1234")))
          .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("SENTENCE")))
          .withRequestBody(matchingJsonPath("adjustmentId", equalTo(ADJUSTMENT_ID)))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
      )
    }
  }

  @Nested
  inner class DeleteNomisSentencingAdjustmentMapping {
    @Test
    internal fun `will pass oath2 token to service`() {
      mappingApi.stubSentenceAdjustmentMappingDelete(ADJUSTMENT_ID)

      runBlocking {
        sentencingAdjustmentsMappingService.deleteNomisSentenceAdjustmentMapping(
          adjustmentId = ADJUSTMENT_ID,
        )
      }

      mappingApi.verify(
        deleteRequestedFor(
          urlPathEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID"),
        ).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }
  }
}
