package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CsraMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerCsraMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import java.util.UUID

private const val OFFENDER_NUMBER = "G4803UT"

@ExtendWith(MappingApiExtension::class)
@SpringAPIServiceTest
@Import(
  CsraMappingApiService::class,
  CsraByPrisonerMappingApiService::class,
  CsraConfiguration::class,
  CsraMappingApiMockServer::class,
)
class CsraMappingApiServiceTest {
  @Autowired
  private lateinit var migrationApiService: CsraByPrisonerMappingApiService

  @Autowired
  private lateinit var syncApiService: CsraMappingApiService

  @Autowired
  private lateinit var csraMappingApiMockServer: CsraMappingApiMockServer

  @Autowired
  private lateinit var jsonMapper: JsonMapper

  @Nested
  inner class MigrationPostMappings {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      csraMappingApiMockServer.stubPostMappings(OFFENDER_NUMBER)

      migrationApiService.createMappings(
        CsraMigrationMapping(
          PrisonerCsraMappingsDto(
            PrisonerCsraMappingsDto.MappingType.MIGRATED,
            listOf(
              CsraMappingIdDto(
                dpsCsraId = UUID.randomUUID().toString(),
                nomisBookingId = 123,
                nomisSequence = 1,
              ),
            ),
          ),
          OFFENDER_NUMBER,
        ),
      )

      csraMappingApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass ids to service`() = runTest {
      val dpsCsraId1 = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      val dpsCsraId2 = "cb3bf3b9-c23c-4787-9450-59259ab62b06"
      csraMappingApiMockServer.stubPostMappings(OFFENDER_NUMBER)

      migrationApiService.createMappings(
        CsraMigrationMapping(
          PrisonerCsraMappingsDto(
            PrisonerCsraMappingsDto.MappingType.MIGRATED,
            listOf(
              CsraMappingIdDto(
                dpsCsraId = dpsCsraId1,
                nomisBookingId = 123456,
                nomisSequence = 1,
              ),
              CsraMappingIdDto(
                dpsCsraId = dpsCsraId2,
                nomisBookingId = 123457,
                nomisSequence = 4,
              ),
            ),
            label = "2024-06-06",
          ),
          OFFENDER_NUMBER,
        ),
      )

      csraMappingApiMockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/csras/$OFFENDER_NUMBER/all"))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED")))
          .withRequestBody(matchingJsonPath("label", equalTo("2024-06-06")))
          .withRequestBody(matchingJsonPath("mappings[0].nomisBookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("mappings[0].nomisSequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("mappings[0].dpsCsraId", equalTo(dpsCsraId1)))
          .withRequestBody(matchingJsonPath("mappings[1].nomisBookingId", equalTo("123457")))
          .withRequestBody(matchingJsonPath("mappings[1].nomisSequence", equalTo("4")))
          .withRequestBody(matchingJsonPath("mappings[1].dpsCsraId", equalTo(dpsCsraId2))),
      )
    }

    @Test
    fun `will return success when no errors`() = runTest {
      csraMappingApiMockServer.stubPostMappings(OFFENDER_NUMBER)

      val result = migrationApiService.createMappings(
        CsraMigrationMapping(
          PrisonerCsraMappingsDto(
            PrisonerCsraMappingsDto.MappingType.MIGRATED,
            listOf(
              CsraMappingIdDto(
                dpsCsraId = UUID.randomUUID().toString(),
                nomisBookingId = 123,
                nomisSequence = 1,
              ),
            ),
          ),
          OFFENDER_NUMBER,
        ),
      )
      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisBookingId = 1234567890L
      val dpsCsraId = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      val existingCsraId = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      csraMappingApiMockServer.stubPostMappings(
        OFFENDER_NUMBER,
        DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = CsraMappingDto(
              dpsCsraId = dpsCsraId.toString(),
              nomisBookingId = nomisBookingId,
              offenderNo = OFFENDER_NUMBER,
              nomisSequence = 1,
              mappingType = NOMIS_CREATED,
            ),
            existing = CsraMappingDto(
              dpsCsraId = existingCsraId.toString(),
              nomisBookingId = nomisBookingId,
              offenderNo = OFFENDER_NUMBER,
              nomisSequence = 1,
              mappingType = NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = migrationApiService.createMappings(
        CsraMigrationMapping(
          PrisonerCsraMappingsDto(
            PrisonerCsraMappingsDto.MappingType.NOMIS_CREATED,
            listOf(
              CsraMappingIdDto(
                dpsCsraId = dpsCsraId.toString(),
                nomisBookingId = nomisBookingId,
                nomisSequence = 1,
              ),
            ),
          ),
          OFFENDER_NUMBER,
        ),
      )
      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsCsraId).isEqualTo(dpsCsraId.toString())
      assertThat(result.errorResponse.moreInfo.existing?.dpsCsraId).isEqualTo(existingCsraId.toString())
    }
  }

  @Nested
  inner class SyncPostMappings {
    val dpsCsraId1 = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

    @Test
    fun `will pass oath2 token to service`() = runTest {
      csraMappingApiMockServer.stubPostMapping()

      syncApiService.createMapping(
        csraMappingDto(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<CsraMappingDto>>() {},
      )

      csraMappingApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass ids to service`() = runTest {
      csraMappingApiMockServer.stubPostMapping()

      syncApiService.createMapping(
        csraMappingDto(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<CsraMappingDto>>() {},
      )

      csraMappingApiMockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/csras"))
          .withRequestBody(matchingJsonPath("dpsCsraId", equalTo(dpsCsraId1)))
          .withRequestBody(matchingJsonPath("nomisBookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisSequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("offenderNo", equalTo(OFFENDER_NUMBER)))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED")))
          .withRequestBody(matchingJsonPath("label", equalTo("2024-06-06"))),
      )
    }

    @Test
    fun `will return success when no errors`() = runTest {
      csraMappingApiMockServer.stubPostMapping()

      val result = syncApiService.createMapping(
        csraMappingDto(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<CsraMappingDto>>() {},
      )
      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisBookingId = 1234567890L
      val dpsCsraId = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      val existingCsraId = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      csraMappingApiMockServer.stubPostMapping(
        DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = CsraMappingDto(
              dpsCsraId = dpsCsraId.toString(),
              nomisBookingId = nomisBookingId,
              offenderNo = OFFENDER_NUMBER,
              nomisSequence = 1,
              mappingType = NOMIS_CREATED,
            ),
            existing = CsraMappingDto(
              dpsCsraId = existingCsraId.toString(),
              nomisBookingId = nomisBookingId,
              offenderNo = OFFENDER_NUMBER,
              nomisSequence = 1,
              mappingType = NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = syncApiService.createMapping(
        csraMappingDto(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<CsraMappingDto>>() {},
      )
      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsCsraId).isEqualTo(dpsCsraId.toString())
      assertThat(result.errorResponse.moreInfo.existing.dpsCsraId).isEqualTo(existingCsraId.toString())
    }

    private fun csraMappingDto(): CsraMappingDto = CsraMappingDto(
      dpsCsraId = dpsCsraId1,
      nomisBookingId = 12345,
      nomisSequence = 1,
      offenderNo = OFFENDER_NUMBER,
      mappingType = CsraMappingDto.MappingType.NOMIS_CREATED,
      label = "2024-06-06",
    )
  }
}
