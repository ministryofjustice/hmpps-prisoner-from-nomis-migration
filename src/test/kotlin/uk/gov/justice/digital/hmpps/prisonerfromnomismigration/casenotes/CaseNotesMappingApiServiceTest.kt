package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import java.util.UUID

private const val OFFENDER_NUMBER = "G4803UT"

@SpringAPIServiceTest
@Import(
  CaseNotesByPrisonerMigrationMappingApiService::class,
  CaseNotesConfiguration::class,
  CaseNotesMappingApiMockServer::class,
)
class CaseNotesMappingApiServiceTest {
  val errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<CaseNoteMappingDto>>() {}

  @Autowired
  private lateinit var apiService: CaseNotesByPrisonerMigrationMappingApiService

  @Autowired
  private lateinit var caseNotesMappingApiMockServer: CaseNotesMappingApiMockServer

  @Nested
  inner class PostMappingsBatch {
    @Test
    fun `will pass oath2 token to service`() {
      runTest {
        caseNotesMappingApiMockServer.stubPostMappingsBatch()

        apiService.createMappings(
          listOf(
            CaseNoteMappingDto(
              nomisCaseNoteId = 123456,
              dpsCaseNoteId = UUID.randomUUID().toString(),
              nomisBookingId = 123,
              offenderNo = OFFENDER_NUMBER,
              mappingType = NOMIS_CREATED,
            ),
          ),
          errorJavaClass,
        )

        caseNotesMappingApiMockServer.verify(
          postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }
    }

    @Test
    fun `will pass ids to service`() = runTest {
      val dpsCaseNoteId1 = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      val dpsCaseNoteId2 = "cb3bf3b9-c23c-4787-9450-59259ab62b06"
      caseNotesMappingApiMockServer.stubPostMappingsBatch()

      apiService.createMappings(
        listOf(
          CaseNoteMappingDto(
            nomisCaseNoteId = 123456,
            dpsCaseNoteId = dpsCaseNoteId1,
            nomisBookingId = 1,
            offenderNo = OFFENDER_NUMBER,
            mappingType = NOMIS_CREATED,
            label = "2024-06-06",
          ),
          CaseNoteMappingDto(
            nomisCaseNoteId = 123457,
            dpsCaseNoteId = dpsCaseNoteId2,
            nomisBookingId = 1,
            offenderNo = OFFENDER_NUMBER,
            mappingType = NOMIS_CREATED,
            label = "2024-06-06",
          ),
        ),
        errorJavaClass,
      )

      caseNotesMappingApiMockServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("$[0].offenderNo", equalTo(OFFENDER_NUMBER)))
          .withRequestBody(matchingJsonPath("$[0].mappingType", equalTo("NOMIS_CREATED")))
          .withRequestBody(matchingJsonPath("$[0].label", equalTo("2024-06-06")))
          .withRequestBody(matchingJsonPath("$[0].nomisCaseNoteId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("$[0].dpsCaseNoteId", equalTo(dpsCaseNoteId1)))
          .withRequestBody(matchingJsonPath("$[0].nomisBookingId", equalTo("1")))
          .withRequestBody(matchingJsonPath("$[1].offenderNo", equalTo(OFFENDER_NUMBER)))
          .withRequestBody(matchingJsonPath("$[1].mappingType", equalTo("NOMIS_CREATED")))
          .withRequestBody(matchingJsonPath("$[1].label", equalTo("2024-06-06")))
          .withRequestBody(matchingJsonPath("$[1].nomisCaseNoteId", equalTo("123457")))
          .withRequestBody(matchingJsonPath("$[1].dpsCaseNoteId", equalTo(dpsCaseNoteId2)))
          .withRequestBody(matchingJsonPath("$[1].nomisBookingId", equalTo("1"))),
      )
    }

    @Test
    fun `will return success when no errors`() = runTest {
      caseNotesMappingApiMockServer.stubPostMappingsBatch()

      val result = apiService.createMappings(
        mappings = listOf(
          CaseNoteMappingDto(
            nomisCaseNoteId = 123456,
            dpsCaseNoteId = UUID.randomUUID().toString(),
            nomisBookingId = 1,
            offenderNo = OFFENDER_NUMBER,
            mappingType = NOMIS_CREATED,
          ),
        ),
        errorJavaClass,
      )
      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisCaseNoteId = 1234567890L
      val dpsCaseNoteId = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      val existingCaseNoteId = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      caseNotesMappingApiMockServer.stubPostMappingsBatch(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = CaseNoteMappingDto(
              dpsCaseNoteId = dpsCaseNoteId.toString(),
              nomisCaseNoteId = nomisCaseNoteId,
              offenderNo = OFFENDER_NUMBER,
              nomisBookingId = 1,
              mappingType = NOMIS_CREATED,
            ),
            existing = CaseNoteMappingDto(
              dpsCaseNoteId = existingCaseNoteId.toString(),
              nomisCaseNoteId = nomisCaseNoteId,
              offenderNo = OFFENDER_NUMBER,
              nomisBookingId = 1,
              mappingType = NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMappings(
        mappings = listOf(
          CaseNoteMappingDto(
            nomisCaseNoteId = 123456,
            dpsCaseNoteId = dpsCaseNoteId.toString(),
            nomisBookingId = 1,
            offenderNo = OFFENDER_NUMBER,
            mappingType = NOMIS_CREATED,
          ),
        ),
        errorJavaClass,
      )
      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsCaseNoteId).isEqualTo(dpsCaseNoteId.toString())
      assertThat(result.errorResponse.moreInfo.existing.dpsCaseNoteId).isEqualTo(existingCaseNoteId.toString())
    }
  }
}
