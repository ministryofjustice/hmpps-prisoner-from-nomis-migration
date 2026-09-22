package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ReligionsMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension

@ExtendWith(MappingApiExtension::class)
@SpringAPIServiceTest
@Import(ReligionsMappingService::class, ReligionsMappingApiMockServer::class)
class ReligionsMappingApiServiceTest(
  @Autowired private val apiService: ReligionsMappingService,
  @Autowired private val mockServer: ReligionsMappingApiMockServer,
) {
  private val errorJavaClass = object : ParameterizedTypeReference<DuplicateErrorResponse<ReligionsMigrationMappingDto>>() {}

  @Nested
  inner class CreateMappingsForMigration {
    @Test
    fun `will pass oauth2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      apiService.createMapping(
        ReligionsMigrationMappingDto(
          mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          cprId = "1233",
          nomisPrisonNumber = "A1234BC",
          religions = emptyList(),
        ),
        errorJavaClass = errorJavaClass,
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person-religion")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      val result = apiService.createMapping(
        ReligionsMigrationMappingDto(
          mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          cprId = "1233",
          nomisPrisonNumber = "A1234BC",
          religions = emptyList(),
        ),
        errorJavaClass = errorJavaClass,
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val cprId = "1234"
      val existingCprId = "5678"

      mockServer.stubCreateMappingsForMigration(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = ReligionsMigrationMappingDto(
              cprId = cprId,
              nomisPrisonNumber = "A1234BC",
              mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
              religions = emptyList(),
            ),
            existing = ReligionsMigrationMappingDto(
              cprId = existingCprId,
              nomisPrisonNumber = "A1234BC",
              mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
              religions = emptyList(),
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMapping(
        ReligionsMigrationMappingDto(
          mappingType = ReligionsMigrationMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          cprId = "1233",
          nomisPrisonNumber = "A1234BC",
          religions = emptyList(),
        ),
        errorJavaClass = errorJavaClass,
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.cprId).isEqualTo(cprId)
      assertThat(result.errorResponse.moreInfo.existing.cprId).isEqualTo(existingCprId)
    }
  }

  @Nested
  inner class CreateReligionMapping {
    @Test
    fun `will pass oauth2 token to create endpoint`() = runTest {
      mockServer.stubCreateReligionMapping()

      apiService.createReligionMapping(
        ReligionMappingDto(
          mappingType = ReligionMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          cprId = "1233",
          nomisId = 3311,
          nomisPrisonNumber = "A1234BC",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person-religion/religion"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateReligionMapping()

      val result = apiService.createReligionMapping(
        ReligionMappingDto(
          mappingType = ReligionMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          cprId = "1233",
          nomisId = 3321,
          nomisPrisonNumber = "A1234BC",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val cprId = "1234"
      val existingCprId = "5678"

      mockServer.stubCreateReligionMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = ReligionMappingDto(
              cprId = cprId,
              nomisId = 4321,
              mappingType = ReligionMappingDto.MappingType.MIGRATED,
              nomisPrisonNumber = "A1234BC",
            ),
            existing = ReligionMappingDto(
              cprId = existingCprId,
              nomisId = 4321,
              mappingType = ReligionMappingDto.MappingType.MIGRATED,
              nomisPrisonNumber = "A1234BC",
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createReligionMapping(
        ReligionMappingDto(
          mappingType = ReligionMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          cprId = "1233",
          nomisId = 3321,
          nomisPrisonNumber = "A1234BC",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.cprId).isEqualTo(cprId)
      assertThat(result.errorResponse.moreInfo.existing?.cprId).isEqualTo(existingCprId)
    }
  }

  @Nested
  inner class GetReligionByNomisId {
    val nomisId = 123456L

    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetReligionByNomisId(
        nomisId = nomisId,
        mapping = ReligionMappingDto(
          cprId = "1234",
          nomisId = nomisId,
          mappingType = ReligionMappingDto.MappingType.MIGRATED,
          nomisPrisonNumber = "A1234BC",
        ),
      )

      apiService.getReligionByNomisId(
        nomisReligionId = nomisId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetReligionByNomisId(
        nomisId = nomisId,
        mapping = ReligionMappingDto(
          cprId = "1234",
          nomisId = nomisId,
          mappingType = ReligionMappingDto.MappingType.MIGRATED,
          nomisPrisonNumber = "A1234BC",
        ),
      )

      apiService.getReligionByNomisId(
        nomisReligionId = nomisId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/core-person-religion/religion/nomis-id/$nomisId")),
      )
    }
  }
}
