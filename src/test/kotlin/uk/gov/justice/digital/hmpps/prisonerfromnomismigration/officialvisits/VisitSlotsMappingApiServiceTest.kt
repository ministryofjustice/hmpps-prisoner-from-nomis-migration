package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi

@SpringAPIServiceTest
@Import(VisitSlotsMappingService::class, VisitSlotsMappingApiMockServer::class)
class VisitSlotsMappingApiServiceTest {
  val errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<VisitTimeSlotMigrationMappingDto>>() {}

  @Autowired
  private lateinit var apiService: VisitSlotsMappingService

  @Autowired
  private lateinit var mockServer: VisitSlotsMappingApiMockServer

  @Nested
  inner class CreateMappingsForMigration {
    @Test
    fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      apiService.createMapping(
        VisitTimeSlotMigrationMappingDto(
          mappingType = VisitTimeSlotMigrationMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisPrisonId = "WWI",
          nomisDayOfWeek = "MON",
          nomisSlotSequence = 2,
          visitSlots = emptyList(),
        ),
        errorJavaClass = errorJavaClass,
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/visit-slots")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      val result = apiService.createMapping(
        VisitTimeSlotMigrationMappingDto(
          mappingType = VisitTimeSlotMigrationMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisPrisonId = "WWI",
          nomisDayOfWeek = "MON",
          nomisSlotSequence = 2,
          visitSlots = emptyList(),
        ),
        errorJavaClass = errorJavaClass,
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val dpsId = "1234"
      val existingDpsId = "5678"

      mockServer.stubCreateMappingsForMigration(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = VisitTimeSlotMigrationMappingDto(
              dpsId = dpsId,
              nomisPrisonId = "WWI",
              nomisDayOfWeek = "MON",
              nomisSlotSequence = 2,
              mappingType = VisitTimeSlotMigrationMappingDto.MappingType.MIGRATED,
              visitSlots = emptyList(),
            ),
            existing = VisitTimeSlotMigrationMappingDto(
              dpsId = existingDpsId,
              nomisPrisonId = "WWI",
              nomisDayOfWeek = "MON",
              nomisSlotSequence = 2,
              mappingType = VisitTimeSlotMigrationMappingDto.MappingType.MIGRATED,
              visitSlots = emptyList(),
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMapping(
        VisitTimeSlotMigrationMappingDto(
          mappingType = VisitTimeSlotMigrationMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisPrisonId = "WWI",
          nomisDayOfWeek = "MON",
          nomisSlotSequence = 2,
          visitSlots = emptyList(),
        ),
        errorJavaClass = errorJavaClass,
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class CreateTimeSlotMapping {
    @Test
    fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateTimeSlotMapping()

      apiService.createTimeSlotMapping(
        VisitTimeSlotMappingDto(
          mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisPrisonId = "WWI",
          nomisDayOfWeek = "MON",
          nomisSlotSequence = 2,
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/visit-slots/time-slots")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateTimeSlotMapping()

      val result = apiService.createTimeSlotMapping(
        VisitTimeSlotMappingDto(
          mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisPrisonId = "WWI",
          nomisDayOfWeek = "MON",
          nomisSlotSequence = 2,
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val dpsId = "1234"
      val existingDpsId = "5678"

      mockServer.stubCreateTimeSlotMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = VisitTimeSlotMappingDto(
              dpsId = dpsId,
              nomisPrisonId = "WWI",
              nomisDayOfWeek = "MON",
              nomisSlotSequence = 2,
              mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
            ),
            existing = VisitTimeSlotMappingDto(
              dpsId = existingDpsId,
              nomisPrisonId = "WWI",
              nomisDayOfWeek = "MON",
              nomisSlotSequence = 2,
              mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createTimeSlotMapping(
        VisitTimeSlotMappingDto(
          mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisPrisonId = "WWI",
          nomisDayOfWeek = "MON",
          nomisSlotSequence = 2,
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse.moreInfo.existing?.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class GetTimeSlotByNomisIdsOrNull {
    val nomisPrisonId = "WWI"
    val nomisDayOfWeek = "MON"
    val nomisSlotSequence = 2

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetTimeSlotByNomisIdsOrNull(
        nomisPrisonId = nomisPrisonId,
        nomisDayOfWeek = nomisDayOfWeek,
        nomisSlotSequence = nomisSlotSequence,
        mapping = VisitTimeSlotMappingDto(
          dpsId = "1234",
          nomisPrisonId = nomisPrisonId,
          nomisDayOfWeek = nomisDayOfWeek,
          nomisSlotSequence = nomisSlotSequence,
          mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getTimeSlotByNomisIdsOrNull(
        nomisPrisonId = nomisPrisonId,
        nomisDayOfWeek = nomisDayOfWeek,
        nomisSlotSequence = nomisSlotSequence,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetTimeSlotByNomisIdsOrNull(
        nomisPrisonId = nomisPrisonId,
        nomisDayOfWeek = nomisDayOfWeek,
        nomisSlotSequence = nomisSlotSequence,
        mapping = VisitTimeSlotMappingDto(
          dpsId = "1234",
          nomisPrisonId = nomisPrisonId,
          nomisDayOfWeek = nomisDayOfWeek,
          nomisSlotSequence = nomisSlotSequence,
          mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getTimeSlotByNomisIdsOrNull(
        nomisPrisonId = nomisPrisonId,
        nomisDayOfWeek = nomisDayOfWeek,
        nomisSlotSequence = nomisSlotSequence,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/visit-slots/time-slots/nomis-prison-id/$nomisPrisonId/nomis-day-of-week/$nomisDayOfWeek/nomis-slot-sequence/$nomisSlotSequence")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetTimeSlotByNomisIdsOrNull(
        nomisPrisonId = nomisPrisonId,
        nomisDayOfWeek = nomisDayOfWeek,
        nomisSlotSequence = nomisSlotSequence,
        mapping = VisitTimeSlotMappingDto(
          dpsId = "1234",
          nomisPrisonId = nomisPrisonId,
          nomisDayOfWeek = nomisDayOfWeek,
          nomisSlotSequence = nomisSlotSequence,
          mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getTimeSlotByNomisIdsOrNull(
        nomisPrisonId = nomisPrisonId,
        nomisDayOfWeek = nomisDayOfWeek,
        nomisSlotSequence = nomisSlotSequence,
      )

      assertThat(mapping?.dpsId).isEqualTo("1234")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetTimeSlotByNomisIdsOrNull(
        nomisPrisonId = nomisPrisonId,
        nomisDayOfWeek = nomisDayOfWeek,
        nomisSlotSequence = nomisSlotSequence,
        mapping = null,
      )

      assertThat(
        apiService.getTimeSlotByNomisIdsOrNull(
          nomisPrisonId = nomisPrisonId,
          nomisDayOfWeek = nomisDayOfWeek,
          nomisSlotSequence = nomisSlotSequence,
        ),
      ).isNull()
    }
  }

  @Nested
  inner class GetVisitSlotByNomisId {
    val nomisId = 123456L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetVisitSlotByNomisId(
        nomisId = nomisId,
        mapping = VisitSlotMappingDto(
          dpsId = "1234",
          nomisId = nomisId,
          mappingType = VisitSlotMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getVisitSlotByNomisId(
        nomisVisitSlotId = nomisId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetVisitSlotByNomisId(
        nomisId = nomisId,
        mapping = VisitSlotMappingDto(
          dpsId = "1234",
          nomisId = nomisId,
          mappingType = VisitSlotMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getVisitSlotByNomisId(
        nomisVisitSlotId = nomisId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/visit-slots/visit-slot/nomis-id/$nomisId")),
      )
    }
  }

  @Nested
  inner class GetInternalLocationByNomisId {
    val nomisLocationId = 1234L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetInternalLocationByNomisId(
        nomisLocationId = nomisLocationId,
      )

      apiService.getInternalLocationByNomisId(
        nomisLocationId = nomisLocationId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetInternalLocationByNomisId(
        nomisLocationId = nomisLocationId,
      )

      apiService.getInternalLocationByNomisId(
        nomisLocationId = nomisLocationId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/locations/nomis/$nomisLocationId")),
      )
    }
  }

  @Nested
  inner class GetMigrationCount {
    @BeforeEach
    fun setUp() {
      mockServer.stubGetMigrationCount("2020-01-01T10:00:00", count = 56_766)
    }

    @Test
    fun `will supply authentication token`(): Unit = runTest {
      apiService.getMigrationCount("2020-01-01T10:00:00")

      mappingApi.verify(
        getRequestedFor(
          urlPathMatching("/mapping/visit-slots/migration-id/.*"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return zero when not found`(): Unit = runTest {
      mappingApi.stubFor(
        get(urlPathMatching("/mapping/visit-slots/migration-id/.*")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody("""{"message":"Not found"}"""),
        ),
      )

      assertThat(apiService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(0)
    }

    @Test
    fun `will return the mapping count when found`(): Unit = runTest {
      assertThat(apiService.getMigrationCount("2020-01-01T10:00:00")).isEqualTo(56_766)
    }
  }
}
