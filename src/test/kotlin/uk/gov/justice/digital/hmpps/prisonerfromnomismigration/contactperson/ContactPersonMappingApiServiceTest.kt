package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonSimpleMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto.MappingType.NOMIS_CREATED
import java.util.*

@SpringAPIServiceTest
@Import(ContactPersonMappingApiService::class, ContactPersonMappingApiMockServer::class)
class ContactPersonMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: ContactPersonMappingApiService

  @Autowired
  private lateinit var mockServer: ContactPersonMappingApiMockServer

  @Nested
  inner class GetByNomisPersonIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisPersonIdOrNull(nomisPersonId = 1234567)

      apiService.getByNomisPersonIdOrNull(nomisPersonId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisPersonIdOrNull()

      apiService.getByNomisPersonIdOrNull(nomisPersonId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/person/nomis-person-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisPersonIdOrNull(
        nomisPersonId = 1234567,
        mapping = PersonMappingDto(
          dpsId = "d94c3c31-ecdc-4a0e-b8bb-94e8cb8262bc",
          nomisId = 1234567,
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisPersonIdOrNull(nomisPersonId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("d94c3c31-ecdc-4a0e-b8bb-94e8cb8262bc")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisPersonIdOrNull(
        nomisPersonId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisPersonIdOrNull(nomisPersonId = 1234567))
    }
  }

  @Nested
  inner class CreateMappingsForMigration {
    @Test
    internal fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      apiService.createMappingsForMigration(
        ContactPersonMappingsDto(
          mappingType = ContactPersonMappingsDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          personMapping = ContactPersonSimpleMappingIdDto(
            dpsId = UUID.randomUUID().toString(),
            nomisId = 1234567,
          ),
          personPhoneMapping = emptyList(),
          personEmailMapping = emptyList(),
          personAddressMapping = emptyList(),
          personContactMapping = emptyList(),
          personContactRestrictionMapping = emptyList(),
          personEmploymentMapping = emptyList(),
          personIdentifierMapping = emptyList(),
          personRestrictionMapping = emptyList(),
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/migrate")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateMappingsForMigration()

      val result = apiService.createMappingsForMigration(
        ContactPersonMappingsDto(
          mappingType = ContactPersonMappingsDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          personMapping = ContactPersonSimpleMappingIdDto(
            dpsId = UUID.randomUUID().toString(),
            nomisId = 1234567,
          ),
          personPhoneMapping = emptyList(),
          personEmailMapping = emptyList(),
          personAddressMapping = emptyList(),
          personContactMapping = emptyList(),
          personContactRestrictionMapping = emptyList(),
          personEmploymentMapping = emptyList(),
          personIdentifierMapping = emptyList(),
          personRestrictionMapping = emptyList(),
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "956d4326-b0c3-47ac-ab12-f0165109a6c5"
      val existingDpsId = "f612a10f-4827-4022-be96-d882193dfabd"

      mockServer.stubCreateMappingsForMigration(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
            existing = PersonMappingDto(
              dpsId = existingDpsId,
              nomisId = nomisId,
              mappingType = NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createMappingsForMigration(
        ContactPersonMappingsDto(
          mappingType = ContactPersonMappingsDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          personMapping = ContactPersonSimpleMappingIdDto(
            dpsId = UUID.randomUUID().toString(),
            nomisId = 1234567,
          ),
          personPhoneMapping = emptyList(),
          personEmailMapping = emptyList(),
          personAddressMapping = emptyList(),
          personContactMapping = emptyList(),
          personContactRestrictionMapping = emptyList(),
          personEmploymentMapping = emptyList(),
          personIdentifierMapping = emptyList(),
          personRestrictionMapping = emptyList(),
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse!!.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class GetMigrationDetails {
    @Test
    fun `will call the person mapping endpoint`() = runTest {
      mockServer.stubGetMigrationDetails(migrationId = "2020-01-01T10%3A00")

      apiService.getMigrationDetails(migrationId = "2020-01-01T10:00")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/person/migration-id/2020-01-01T10%3A00")),
      )
    }
  }
}
