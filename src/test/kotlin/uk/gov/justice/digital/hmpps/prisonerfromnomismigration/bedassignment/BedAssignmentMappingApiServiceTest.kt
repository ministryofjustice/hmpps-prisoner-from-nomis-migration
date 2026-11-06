package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BedAssignmentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BedAssignmentMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBedAssignmentMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

private const val MAPPING_OFFENDER_NO = "A1234KT"
private const val MAPPING_DPS_ID = "e52d7268-6e10-41a8-a0b9-2319b32520d6"
private const val MIGRATION_ID = "c0833945-b3b4-4a58-a285-0e61e872a6aa"

@ExtendWith(MappingApiExtension::class)
@SpringAPIServiceTest
@Import(
  BedAssignmentMappingApiService::class,
  BedAssignmentByPrisonerMappingApiService::class,
  BedAssignmentConfiguration::class,
  BedAssignmentMappingApiMockServer::class,
)
class BedAssignmentMappingApiServiceTest {
  @Autowired
  private lateinit var mappingService: BedAssignmentMappingApiService

  @Autowired
  private lateinit var migrationMappingService: BedAssignmentByPrisonerMappingApiService

  @Autowired
  private lateinit var mockServer: BedAssignmentMappingApiMockServer

  @Nested
  inner class GetByNomisId {
    @Test
    fun `passes the oauth token and both NOMIS key parts`() = runTest {
      mockServer.stubGetByNomisId(123456, 2)

      mappingService.getMappingByNomisId(123456, 2)

      mockServer.verify(
        getRequestedFor(urlEqualTo("/mapping/bed-assignments/booking-id/123456/sequence/2"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `returns mapping details`() = runTest {
      mockServer.stubGetByNomisId(123456, 2)

      val mapping = mappingService.getMappingByNomisId(123456, 2)

      assertThat(mapping.dpsBedAssignmentId).isEqualTo(MAPPING_DPS_ID)
      assertThat(mapping.nomisBookingId).isEqualTo(123456)
      assertThat(mapping.nomisSequence).isEqualTo(2)
    }

    @Test
    fun `returns null only when mapping is absent`() = runTest {
      mockServer.stubGetByNomisId(123456, 2, HttpStatus.NOT_FOUND)

      assertThat(mappingService.getMappingByNomisIdOrNull(123456, 2)).isNull()
    }

    @Test
    fun `propagates mapping service errors other than not found`() {
      mockServer.stubGetByNomisId(123456, 2, HttpStatus.INTERNAL_SERVER_ERROR)

      assertThatThrownBy {
        runTest { mappingService.getMappingByNomisIdOrNull(123456, 2) }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  inner class CreateSyncMapping {
    @Test
    fun `posts exact mapping fields with oauth`() = runTest {
      mockServer.stubCreateMapping()

      mappingService.createMapping(
        syncMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<BedAssignmentMappingDto>>() {},
      )

      mockServer.verify(
        postRequestedFor(urlEqualTo("/mapping/bed-assignments"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withRequestBody(matchingJsonPath("dpsBedAssignmentId", equalTo(MAPPING_DPS_ID)))
          .withRequestBody(matchingJsonPath("nomisBookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("nomisSequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("NOMIS_CREATED"))),
      )
    }

    @Test
    fun `returns success after a bodiless 201 response`() = runTest {
      mockServer.stubCreateMapping()

      val result = mappingService.createMapping(
        syncMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<BedAssignmentMappingDto>>() {},
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `returns duplicate details for a 409 conflict`() = runTest {
      mappingApi.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/mapping/bed-assignments"))
          .willReturn(
            com.github.tomakehurst.wiremock.client.WireMock.jsonResponse(
              """
              {
                "moreInfo": {
                  "duplicate": {
                    "dpsBedAssignmentId":"$MAPPING_DPS_ID",
                    "nomisBookingId":123456,
                    "nomisSequence":1,
                    "mappingType":"NOMIS_CREATED"
                  },
                  "existing": {
                    "dpsBedAssignmentId":"$MAPPING_DPS_ID",
                    "nomisBookingId":123456,
                    "nomisSequence":1,
                    "mappingType":"NOMIS_CREATED"
                  }
                },
                "status":"409 CONFLICT",
                "errorCode":1409,
                "userMessage":"Duplicate mapping"
              }
              """.trimIndent(),
              409,
            ),
          ),
      )

      val result = mappingService.createMapping(
        syncMapping(),
        object : ParameterizedTypeReference<DuplicateErrorResponse<BedAssignmentMappingDto>>() {},
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsBedAssignmentId).isEqualTo(MAPPING_DPS_ID)
      assertThat(result.errorResponse.moreInfo.existing.dpsBedAssignmentId).isEqualTo(MAPPING_DPS_ID)
    }

    @Test
    fun `propagates unexpected mapping creation failures`() {
      mockServer.stubCreateMapping(HttpStatus.INTERNAL_SERVER_ERROR)

      assertThatThrownBy {
        runTest {
          mappingService.createMapping(
            syncMapping(),
            object : ParameterizedTypeReference<DuplicateErrorResponse<BedAssignmentMappingDto>>() {},
          )
        }
      }.isInstanceOf(WebClientResponseException.InternalServerError::class.java)
    }
  }

  @Nested
  inner class DeleteByDpsId {
    @Test
    fun `deletes by DPS identity and passes oauth`() = runTest {
      mockServer.stubDeleteMapping()

      mappingService.deleteMappingByDpsId(MAPPING_DPS_ID)

      mockServer.verify(
        deleteRequestedFor(urlEqualTo("/mapping/bed-assignments/dps-bed-assignment-id/$MAPPING_DPS_ID"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `does not hide errors for a missing DPS mapping`() {
      mappingApi.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.delete(
          urlEqualTo("/mapping/bed-assignments/dps-bed-assignment-id/$MAPPING_DPS_ID"),
        ).willReturn(aResponse().withStatus(HttpStatus.NOT_FOUND.value())),
      )

      assertThatThrownBy {
        runTest { mappingService.deleteMappingByDpsId(MAPPING_DPS_ID) }
      }.isInstanceOf(WebClientResponseException.NotFound::class.java)
    }
  }

  @Nested
  inner class CreateMigrationMappings {
    @Test
    fun `posts the full migration mapping envelope and migration label`() = runTest {
      mockServer.stubCreateMigrationMappings(MAPPING_OFFENDER_NO)

      migrationMappingService.createMappings(
        BedAssignmentMigrationMapping(
          prisonerMappings = PrisonerBedAssignmentMappingsDto(
            mappingType = PrisonerBedAssignmentMappingsDto.MappingType.MIGRATED,
            mappings = listOf(
              BedAssignmentMappingIdDto(
                dpsBedAssignmentId = MAPPING_DPS_ID,
                nomisBookingId = 123456,
                nomisSequence = 1,
              ),
            ),
            label = MIGRATION_ID,
          ),
          offenderNo = MAPPING_OFFENDER_NO,
        ),
      )

      mockServer.verify(
        postRequestedFor(urlEqualTo("/mapping/bed-assignments/$MAPPING_OFFENDER_NO/all"))
          .withHeader("Authorization", equalTo("Bearer ABCDE"))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED")))
          .withRequestBody(matchingJsonPath("label", equalTo(MIGRATION_ID)))
          .withRequestBody(matchingJsonPath("mappings[0].dpsBedAssignmentId", equalTo(MAPPING_DPS_ID)))
          .withRequestBody(matchingJsonPath("mappings[0].nomisBookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("mappings[0].nomisSequence", equalTo("1"))),
      )
    }

    @Test
    fun `awaits successful bodiless mapping response`() = runTest {
      mockServer.stubCreateMigrationMappings(MAPPING_OFFENDER_NO)

      val result = migrationMappingService.createMappings(
        BedAssignmentMigrationMapping(
          PrisonerBedAssignmentMappingsDto(
            mappingType = PrisonerBedAssignmentMappingsDto.MappingType.MIGRATED,
            mappings = emptyList(),
            label = MIGRATION_ID,
          ),
          MAPPING_OFFENDER_NO,
        ),
      )

      assertThat(result.isError).isFalse()
    }
  }

  private fun syncMapping() = BedAssignmentMappingDto(
    dpsBedAssignmentId = MAPPING_DPS_ID,
    nomisBookingId = 123456,
    nomisSequence = 1,
    mappingType = BedAssignmentMappingDto.MappingType.NOMIS_CREATED,
  )
}
