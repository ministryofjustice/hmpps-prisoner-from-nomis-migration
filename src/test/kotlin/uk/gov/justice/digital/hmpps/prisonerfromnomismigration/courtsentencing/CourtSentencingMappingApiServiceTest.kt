package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import java.util.*

private const val NOMIS_COURT_CASE_ID = 1234L
private const val DPS_COURT_CASE_ID = "cc1"

@SpringAPIServiceTest
@Import(
  CourtSentencingMappingApiService::class,
  CourtSentencingConfiguration::class,
  CourtSentencingMappingApiMockServer::class,
)
class CourtSentencingMappingApiServiceTest {

  @Autowired
  private lateinit var apiService: CourtSentencingMappingApiService

  @Autowired
  private lateinit var courtSentencingMappingApiMockServer: CourtSentencingMappingApiMockServer

  @Nested
  inner class GetByNomisId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtSentencingMappingApiMockServer.stubGetByNomisId()

      apiService.getCourtCaseOrNullByNomisId(courtCaseId = NOMIS_COURT_CASE_ID)

      courtSentencingMappingApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      courtSentencingMappingApiMockServer.stubGetByNomisId()

      apiService.getCourtCaseOrNullByNomisId(courtCaseId = NOMIS_COURT_CASE_ID)

      courtSentencingMappingApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases/nomis-court-case-id/$NOMIS_COURT_CASE_ID")),
      )
    }

    @Test
    fun `will return dpsCourtCaseId when mapping exists`() = runTest {
      courtSentencingMappingApiMockServer.stubGetByNomisId(
        nomisCourtCaseId = NOMIS_COURT_CASE_ID,
        mapping = CourtCaseMappingDto(
          dpsCourtCaseId = DPS_COURT_CASE_ID,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
        ),
      )

      val mapping = apiService.getCourtCaseOrNullByNomisId(courtCaseId = NOMIS_COURT_CASE_ID)

      assertThat(mapping?.dpsCourtCaseId).isEqualTo(DPS_COURT_CASE_ID)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      courtSentencingMappingApiMockServer.stubGetByNomisId(NOT_FOUND)

      assertThat(apiService.getCourtCaseOrNullByNomisId(courtCaseId = NOMIS_COURT_CASE_ID)).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      courtSentencingMappingApiMockServer.stubGetByNomisId(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getCourtCaseOrNullByNomisId(courtCaseId = NOMIS_COURT_CASE_ID)
      }
    }
  }

  @Nested
  inner class PostMapping {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtSentencingMappingApiMockServer.stubPostMapping()

      apiService.createMapping(
        CourtCaseAllMappingDto(
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
          dpsCourtCaseId = DPS_COURT_CASE_ID,
          mappingType = CourtCaseAllMappingDto.MappingType.DPS_CREATED,
          courtCharges = emptyList(),
          courtAppearances = emptyList(),
          sentences = emptyList(),
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<CourtCaseAllMappingDto>>() {},
      )

      courtSentencingMappingApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to service`() = runTest {
      courtSentencingMappingApiMockServer.stubPostMapping()

      apiService.createMapping(
        CourtCaseAllMappingDto(
          nomisCourtCaseId = NOMIS_COURT_CASE_ID,
          dpsCourtCaseId = DPS_COURT_CASE_ID,
          mappingType = CourtCaseAllMappingDto.MappingType.DPS_CREATED,
          courtCharges = emptyList(),
          courtAppearances = emptyList(),
          sentences = emptyList(),
        ),
        object : ParameterizedTypeReference<DuplicateErrorResponse<CourtCaseAllMappingDto>>() {},
      )

      courtSentencingMappingApiMockServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("nomisCourtCaseId", equalTo(NOMIS_COURT_CASE_ID.toString())))
          .withRequestBody(matchingJsonPath("dpsCourtCaseId", equalTo(DPS_COURT_CASE_ID)))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("DPS_CREATED"))),
      )
    }
  }

  @Nested
  inner class ReplaceOrCreateMappings {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtSentencingMappingApiMockServer.stubReplaceOrCreateMappings()

      apiService.replaceOrCreateMappings(
        CourtCaseBatchMappingDto(
          mappingType = CourtCaseBatchMappingDto.MappingType.DPS_CREATED,
          courtCharges = emptyList(),
          courtAppearances = emptyList(),
          sentences = emptyList(),
          courtCases = emptyList(),
          sentenceTerms = emptyList(),
        ),
      )

      courtSentencingMappingApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass dto to service`() = runTest {
      courtSentencingMappingApiMockServer.stubReplaceOrCreateMappings()

      apiService.replaceOrCreateMappings(
        CourtCaseBatchMappingDto(
          mappingType = CourtCaseBatchMappingDto.MappingType.DPS_CREATED,
          courtCharges = emptyList(),
          courtAppearances = emptyList(),
          sentences = emptyList(),
          courtCases = emptyList(),
          sentenceTerms = emptyList(),
        ),
      )

      courtSentencingMappingApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-cases/replace"))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("DPS_CREATED"))),
      )
    }
  }

  @Nested
  inner class GetAllCourtAppearancesByNomisIds {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      courtSentencingMappingApiMockServer.stubGetAllCourtAppearanceByNomisIds()

      apiService.getAllCourtAppearancesByNomisIds(listOf(123, 456))

      courtSentencingMappingApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass requested IDs to service`() = runTest {
      courtSentencingMappingApiMockServer.stubGetAllCourtAppearanceByNomisIds()

      apiService.getAllCourtAppearancesByNomisIds(listOf(123, 456))

      courtSentencingMappingApiMockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/court-sentencing/court-appearances/nomis-court-appearance-ids/get-list")).withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withRequestBody(equalToJson("[123, 456]")),
      )
    }

    @Test
    internal fun `will parse response`() = runTest {
      val dpsId1 = UUID.randomUUID().toString()
      val dpsId2 = UUID.randomUUID().toString()
      courtSentencingMappingApiMockServer.stubGetAllCourtAppearanceByNomisIds(
        dpsCourtAppearanceIds = listOf(dpsId1, dpsId2),
      )

      with(apiService.getAllCourtAppearancesByNomisIds(listOf(123, 456))) {
        assertThat(this).hasSize(2)
        assertThat(this[0].dpsCourtAppearanceId).isEqualTo(dpsId1)
        assertThat(this[1].dpsCourtAppearanceId).isEqualTo(dpsId2)
      }
    }

    @Test
    internal fun `will throw if error`() = runTest {
      courtSentencingMappingApiMockServer.stubGetAllCourtAppearanceByNomisIds(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getAllCourtAppearancesByNomisIds(listOf(123, 456))
      }
    }
  }
}
