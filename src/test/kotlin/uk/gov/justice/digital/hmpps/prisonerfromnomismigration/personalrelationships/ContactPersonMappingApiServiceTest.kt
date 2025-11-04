package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonSimpleMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonEmploymentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonRestrictionMappingDto
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
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisPersonIdOrNull(nomisPersonId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("1234567")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisPersonIdOrNull(
        nomisPersonId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisPersonIdOrNull(nomisPersonId = 1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisPersonId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisPersonId(nomisPersonId = 1234567)

      apiService.getByNomisPersonId(nomisPersonId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisPersonId(nomisPersonId = 1234567)

      apiService.getByNomisPersonId(nomisPersonId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/person/nomis-person-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId`() = runTest {
      mockServer.stubGetByNomisPersonId(
        nomisPersonId = 1234567,
        mapping = PersonMappingDto(
          dpsId = "1234567",
          nomisId = 1234567,
          mappingType = MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisPersonId(nomisPersonId = 1234567)

      assertThat(mapping.dpsId).isEqualTo("1234567")
    }
  }

  @Nested
  inner class DeleteByNomisPersonId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisPersonId(nomisPersonId = 1234567)

      apiService.deleteByNomisPersonId(nomisPersonId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisPersonId(nomisPersonId = 1234567)

      apiService.deleteByNomisPersonId(nomisPersonId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/person/nomis-person-id/1234567")),
      )
    }
  }

  @Nested
  inner class GetByNomisContactIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisContactIdOrNull(nomisContactId = 1234567)

      apiService.getByNomisContactIdOrNull(nomisContactId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisContactIdOrNull(nomisContactId = 1234567)

      apiService.getByNomisContactIdOrNull(nomisContactId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/contact/nomis-contact-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisContactIdOrNull(
        nomisContactId = 1234567,
        mapping = PersonContactMappingDto(
          dpsId = "7654321",
          nomisId = 1234567,
          mappingType = PersonContactMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisContactIdOrNull(nomisContactId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("7654321")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisContactIdOrNull(
        nomisContactId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisContactIdOrNull(nomisContactId = 1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisContactId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisContactId(nomisContactId = 1234567)

      apiService.getByNomisContactId(nomisContactId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisContactId(nomisContactId = 1234567)

      apiService.getByNomisContactId(nomisContactId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/contact/nomis-contact-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId`() = runTest {
      mockServer.stubGetByNomisContactId(
        nomisContactId = 1234567,
        mapping = PersonContactMappingDto(
          dpsId = "7654321",
          nomisId = 1234567,
          mappingType = PersonContactMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisContactId(nomisContactId = 1234567)

      assertThat(mapping.dpsId).isEqualTo("7654321")
    }
  }

  @Nested
  inner class DeleteByNomisContactId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisContactId(nomisContactId = 1234567)

      apiService.deleteByNomisContactId(nomisContactId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisContactId(nomisContactId = 1234567)

      apiService.deleteByNomisContactId(nomisContactId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/contact/nomis-contact-id/1234567")),
      )
    }
  }

  @Nested
  inner class GetByNomisAddressIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull(nomisAddressId = 1234567)

      apiService.getByNomisAddressIdOrNull(nomisAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull(nomisAddressId = 1234567)

      apiService.getByNomisAddressIdOrNull(nomisAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/address/nomis-address-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull(
        nomisAddressId = 1234567,
        mapping = PersonAddressMappingDto(
          dpsId = "7654321",
          nomisId = 1234567,
          mappingType = PersonAddressMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisAddressIdOrNull(nomisAddressId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("7654321")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull(
        nomisAddressId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisAddressIdOrNull(nomisAddressId = 1234567)).isNull()
    }
  }

  @Nested
  inner class DeleteByNomisAddressId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisAddressId(nomisAddressId = 1234567)

      apiService.deleteByNomisAddressId(nomisAddressId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisAddressId(nomisAddressId = 1234567)

      apiService.deleteByNomisAddressId(nomisAddressId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/address/nomis-address-id/1234567")),
      )
    }
  }

  @Nested
  inner class GetByNomisEmailIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull(nomisInternetAddressId = 1234567)

      apiService.getByNomisEmailIdOrNull(nomisInternetAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull(nomisInternetAddressId = 1234567)

      apiService.getByNomisEmailIdOrNull(nomisInternetAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/email/nomis-internet-address-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull(
        nomisInternetAddressId = 1234567,
        mapping = PersonEmailMappingDto(
          dpsId = "7654321",
          nomisId = 1234567,
          mappingType = PersonEmailMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisEmailIdOrNull(nomisInternetAddressId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("7654321")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull(
        nomisInternetAddressId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisEmailIdOrNull(nomisInternetAddressId = 1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisEmailId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisEmailId(nomisInternetAddressId = 1234567)

      apiService.getByNomisEmailId(nomisInternetAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisEmailId(nomisInternetAddressId = 1234567)

      apiService.getByNomisEmailId(nomisInternetAddressId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/email/nomis-internet-address-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId`() = runTest {
      mockServer.stubGetByNomisEmailId(
        nomisInternetAddressId = 1234567,
        mapping = PersonEmailMappingDto(
          dpsId = "7654321",
          nomisId = 1234567,
          mappingType = PersonEmailMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisEmailId(nomisInternetAddressId = 1234567)

      assertThat(mapping.dpsId).isEqualTo("7654321")
    }
  }

  @Nested
  inner class DeleteByNomisEmailId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisEmailId(nomisInternetAddressId = 1234567)

      apiService.deleteByNomisEmailId(nomisInternetAddressId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisEmailId(nomisInternetAddressId = 1234567)

      apiService.deleteByNomisEmailId(nomisInternetAddressId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/email/nomis-internet-address-id/1234567")),
      )
    }
  }

  @Nested
  inner class GetByNomisPhoneIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull(nomisPhoneId = 1234567)

      apiService.getByNomisPhoneIdOrNull(nomisPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull(nomisPhoneId = 1234567)

      apiService.getByNomisPhoneIdOrNull(nomisPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/phone/nomis-phone-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull(
        nomisPhoneId = 1234567,
        mapping = PersonPhoneMappingDto(
          dpsId = "7654321",
          nomisId = 1234567,
          dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
          mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisPhoneIdOrNull(nomisPhoneId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("7654321")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull(
        nomisPhoneId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisPhoneIdOrNull(nomisPhoneId = 1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisPhoneId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisPhoneId(nomisPhoneId = 1234567)

      apiService.getByNomisPhoneId(nomisPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisPhoneId(nomisPhoneId = 1234567)

      apiService.getByNomisPhoneId(nomisPhoneId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/phone/nomis-phone-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId`() = runTest {
      mockServer.stubGetByNomisPhoneId(
        nomisPhoneId = 1234567,
        mapping = PersonPhoneMappingDto(
          dpsId = "7654321",
          nomisId = 1234567,
          dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
          mappingType = PersonPhoneMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisPhoneId(nomisPhoneId = 1234567)

      assertThat(mapping.dpsId).isEqualTo("7654321")
    }
  }

  @Nested
  inner class DeleteByNomisPhoneId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisPhoneId(nomisPhoneId = 1234567)

      apiService.deleteByNomisPhoneId(nomisPhoneId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisPhoneId(nomisPhoneId = 1234567)

      apiService.deleteByNomisPhoneId(nomisPhoneId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/phone/nomis-phone-id/1234567")),
      )
    }
  }

  @Nested
  inner class GetByNomisIdentifierIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisIdentifierIdsOrNull(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      apiService.getByNomisIdentifierIdsOrNull(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubGetByNomisIdentifierIdsOrNull(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      apiService.getByNomisIdentifierIdsOrNull(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/identifier/nomis-person-id/1234567/nomis-sequence-number/4")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisIdentifierIdsOrNull(
        nomisPersonId = 1234567,
        nomisSequenceNumber = 4,
        mapping = PersonIdentifierMappingDto(
          dpsId = "7654321",
          nomisPersonId = 1234567,
          nomisSequenceNumber = 4,
          mappingType = PersonIdentifierMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisIdentifierIdsOrNull(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      assertThat(mapping?.dpsId).isEqualTo("7654321")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisIdentifierIdsOrNull(
        nomisPersonId = 1234567,
        nomisSequenceNumber = 4,
        mapping = null,
      )

      assertThat(apiService.getByNomisIdentifierIdsOrNull(nomisPersonId = 1234567, nomisSequenceNumber = 4)).isNull()
    }
  }

  @Nested
  inner class GetByNomisIdentifierId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisIdentifierIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      apiService.getByNomisIdentifierIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubGetByNomisIdentifierIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      apiService.getByNomisIdentifierIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/identifier/nomis-person-id/1234567/nomis-sequence-number/4")),
      )
    }

    @Test
    fun `will return dpsId`() = runTest {
      mockServer.stubGetByNomisIdentifierIds(
        nomisPersonId = 1234567,
        nomisSequenceNumber = 4,
        mapping = PersonIdentifierMappingDto(
          dpsId = "7654321",
          nomisPersonId = 1234567,
          nomisSequenceNumber = 4,
          mappingType = PersonIdentifierMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisIdentifierIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      assertThat(mapping.dpsId).isEqualTo("7654321")
    }
  }

  @Nested
  inner class DeleteByNomisIdentifierId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisIdentifierIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      apiService.deleteByNomisIdentifierIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubDeleteByNomisIdentifierIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      apiService.deleteByNomisIdentifierIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/identifier/nomis-person-id/1234567/nomis-sequence-number/4")),
      )
    }
  }

  @Nested
  inner class GetByNomisEmploymentIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisEmploymentIdsOrNull(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      apiService.getByNomisEmploymentIdsOrNull(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubGetByNomisEmploymentIdsOrNull(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      apiService.getByNomisEmploymentIdsOrNull(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/employment/nomis-person-id/1234567/nomis-sequence-number/4")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisEmploymentIdsOrNull(
        nomisPersonId = 1234567,
        nomisSequenceNumber = 4,
        mapping = PersonEmploymentMappingDto(
          dpsId = "7654321",
          nomisPersonId = 1234567,
          nomisSequenceNumber = 4,
          mappingType = PersonEmploymentMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisEmploymentIdsOrNull(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      assertThat(mapping?.dpsId).isEqualTo("7654321")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisEmploymentIdsOrNull(
        nomisPersonId = 1234567,
        nomisSequenceNumber = 4,
        mapping = null,
      )

      assertThat(apiService.getByNomisEmploymentIdsOrNull(nomisPersonId = 1234567, nomisSequenceNumber = 4)).isNull()
    }
  }

  @Nested
  inner class GetByNomisEmploymentId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisEmploymentIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      apiService.getByNomisEmploymentIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubGetByNomisEmploymentIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      apiService.getByNomisEmploymentIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/employment/nomis-person-id/1234567/nomis-sequence-number/4")),
      )
    }

    @Test
    fun `will return dpsId`() = runTest {
      mockServer.stubGetByNomisEmploymentIds(
        nomisPersonId = 1234567,
        nomisSequenceNumber = 4,
        mapping = PersonEmploymentMappingDto(
          dpsId = "7654321",
          nomisPersonId = 1234567,
          nomisSequenceNumber = 4,
          mappingType = PersonEmploymentMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisEmploymentIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      assertThat(mapping.dpsId).isEqualTo("7654321")
    }
  }

  @Nested
  inner class DeleteByNomisEmploymentId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisEmploymentIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      apiService.deleteByNomisEmploymentIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubDeleteByNomisEmploymentIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      apiService.deleteByNomisEmploymentIds(nomisPersonId = 1234567, nomisSequenceNumber = 4)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/employment/nomis-person-id/1234567/nomis-sequence-number/4")),
      )
    }
  }

  @Nested
  inner class GetByNomisContactRestrictionIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisContactRestrictionIdOrNull(nomisContactRestrictionId = 1234567)

      apiService.getByNomisContactRestrictionIdOrNull(nomisContactRestrictionId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisContactRestrictionIdOrNull(nomisContactRestrictionId = 1234567)

      apiService.getByNomisContactRestrictionIdOrNull(nomisContactRestrictionId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/contact-restriction/nomis-contact-restriction-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisContactRestrictionIdOrNull(
        nomisContactRestrictionId = 1234567,
        mapping = PersonContactRestrictionMappingDto(
          dpsId = "7654321",
          nomisId = 1234567,
          mappingType = PersonContactRestrictionMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisContactRestrictionIdOrNull(nomisContactRestrictionId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("7654321")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisContactRestrictionIdOrNull(
        nomisContactRestrictionId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisContactRestrictionIdOrNull(nomisContactRestrictionId = 1234567)).isNull()
    }
  }

  @Nested
  inner class DeleteByNomisContactRestrictionId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisContactRestrictionId(nomisContactRestrictionId = 1234567)

      apiService.deleteByNomisContactRestrictionId(nomisContactRestrictionId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisContactRestrictionId(nomisContactRestrictionId = 1234567)

      apiService.deleteByNomisContactRestrictionId(nomisContactRestrictionId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/contact-restriction/nomis-contact-restriction-id/1234567")),
      )
    }
  }

  @Nested
  inner class GetByNomisPersonRestrictionIdOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = 1234567)

      apiService.getByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = 1234567)

      apiService.getByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/contact-person/person-restriction/nomis-person-restriction-id/1234567")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetByNomisPersonRestrictionIdOrNull(
        nomisPersonRestrictionId = 1234567,
        mapping = PersonRestrictionMappingDto(
          dpsId = "7654321",
          nomisId = 1234567,
          mappingType = PersonRestrictionMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = 1234567)

      assertThat(mapping?.dpsId).isEqualTo("7654321")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisPersonRestrictionIdOrNull(
        nomisPersonRestrictionId = 1234567,
        mapping = null,
      )

      assertThat(apiService.getByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = 1234567)).isNull()
    }
  }

  @Nested
  inner class DeleteByNomisPersonRestrictionId {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeleteByNomisPersonRestrictionId(nomisPersonRestrictionId = 1234567)

      apiService.deleteByNomisPersonRestrictionId(nomisPersonRestrictionId = 1234567)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisPersonRestrictionId(nomisPersonRestrictionId = 1234567)

      apiService.deleteByNomisPersonRestrictionId(nomisPersonRestrictionId = 1234567)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/contact-person/person-restriction/nomis-person-restriction-id/1234567")),
      )
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
  inner class ReplaceMappingsForPrisoner {
    @Test
    internal fun `will pass oath2 token to replace endpoint`() = runTest {
      mockServer.stubReplaceMappingsForPrisoner("A1234KT")

      apiService.replaceMappingsForPrisoner(
        "A1234KT",
        ContactPersonPrisonerMappingsDto(
          mappingType = ContactPersonPrisonerMappingsDto.MappingType.MIGRATED,
          personContactMapping = emptyList(),
          personContactRestrictionMapping = emptyList(),
          personContactMappingsToRemoveByDpsId = emptyList(),
          personContactRestrictionMappingsToRemoveByDpsId = emptyList(),
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/prisoner/A1234KT")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }
  }

  @Nested
  inner class ReplaceMappingsForPerson {
    @Test
    internal fun `will pass oath2 token to replace endpoint`() = runTest {
      mockServer.stubReplaceMappingsForPerson(99)

      apiService.replaceMappingsForPerson(
        99,
        ContactPersonMappingsDto(
          mappingType = ContactPersonMappingsDto.MappingType.MIGRATED,
          personContactMapping = emptyList(),
          personContactRestrictionMapping = emptyList(),
          personMapping = ContactPersonSimpleMappingIdDto(nomisId = 1, dpsId = "1"),
          personAddressMapping = emptyList(),
          personPhoneMapping = emptyList(),
          personEmailMapping = emptyList(),
          personEmploymentMapping = emptyList(),
          personIdentifierMapping = emptyList(),
          personRestrictionMapping = emptyList(),
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/replace/person/99")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }
  }

  @Nested
  inner class CreatePersonMapping {
    @Test
    internal fun `will pass oath2 token to create person mapping endpoint`() = runTest {
      mockServer.stubCreatePersonMapping()

      apiService.createPersonMapping(
        PersonMappingDto(
          mappingType = NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/person")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreatePersonMapping()

      val result = apiService.createPersonMapping(
        PersonMappingDto(
          mappingType = PersonMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreatePersonMapping(
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

      val result = apiService.createPersonMapping(
        PersonMappingDto(
          mappingType = PersonMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse!!.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class CreateContactMapping {
    @Test
    internal fun `will pass oath2 token to create contact mapping endpoint`() = runTest {
      mockServer.stubCreateContactMapping()

      apiService.createContactMapping(
        PersonContactMappingDto(
          mappingType = PersonContactMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/contact")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateContactMapping()

      val result = apiService.createContactMapping(
        PersonContactMappingDto(
          mappingType = PersonContactMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreateContactMapping(
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

      val result = apiService.createContactMapping(
        PersonContactMappingDto(
          mappingType = PersonContactMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse!!.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class CreateEmailMapping {
    @Test
    internal fun `will pass oath2 token to create email mapping endpoint`() = runTest {
      mockServer.stubCreateEmailMapping()

      apiService.createEmailMapping(
        PersonEmailMappingDto(
          mappingType = PersonEmailMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/email")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateEmailMapping()

      val result = apiService.createEmailMapping(
        PersonEmailMappingDto(
          mappingType = PersonEmailMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreateEmailMapping(
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

      val result = apiService.createEmailMapping(
        PersonEmailMappingDto(
          mappingType = PersonEmailMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse!!.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class CreateAddressMapping {
    @Test
    internal fun `will pass oath2 token to create address mapping endpoint`() = runTest {
      mockServer.stubCreateAddressMapping()

      apiService.createAddressMapping(
        PersonAddressMappingDto(
          mappingType = PersonAddressMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/address")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateAddressMapping()

      val result = apiService.createAddressMapping(
        PersonAddressMappingDto(
          mappingType = PersonAddressMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreateAddressMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonAddressMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = PersonAddressMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = PersonAddressMappingDto(
              dpsId = existingDpsId,
              nomisId = nomisId,
              mappingType = PersonAddressMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createAddressMapping(
        PersonAddressMappingDto(
          mappingType = PersonAddressMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse!!.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class CreatePhoneMapping {
    @Test
    internal fun `will pass oath2 token to create phone mapping endpoint`() = runTest {
      mockServer.stubCreatePhoneMapping()

      apiService.createPhoneMapping(
        PersonPhoneMappingDto(
          mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
          dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/phone")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreatePhoneMapping()

      val result = apiService.createPhoneMapping(
        PersonPhoneMappingDto(
          mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
          dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreatePhoneMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonPhoneMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
              dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
            ),
            existing = PersonPhoneMappingDto(
              dpsId = existingDpsId,
              nomisId = nomisId,
              mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
              dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createPhoneMapping(
        PersonPhoneMappingDto(
          mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
          dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse!!.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class CreateIdentifierMapping {
    @Test
    internal fun `will pass oath2 token to create identifier mapping endpoint`() = runTest {
      mockServer.stubCreateIdentifierMapping()

      apiService.createIdentifierMapping(
        PersonIdentifierMappingDto(
          mappingType = PersonIdentifierMappingDto.MappingType.NOMIS_CREATED,
          nomisPersonId = 1234567,
          nomisSequenceNumber = 4,
          dpsId = "7654321",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/identifier")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateIdentifierMapping()

      val result = apiService.createIdentifierMapping(
        PersonIdentifierMappingDto(
          mappingType = PersonIdentifierMappingDto.MappingType.NOMIS_CREATED,
          nomisPersonId = 1234567,
          nomisSequenceNumber = 4,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisPersonId = 1234567890L
      val nomisSequenceNumber = 4L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreateIdentifierMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonIdentifierMappingDto(
              dpsId = dpsId,
              nomisPersonId = 1234567,
              nomisSequenceNumber = 4,
              mappingType = PersonIdentifierMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = PersonIdentifierMappingDto(
              dpsId = existingDpsId,
              nomisPersonId = nomisPersonId,
              nomisSequenceNumber = nomisSequenceNumber,
              mappingType = PersonIdentifierMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createIdentifierMapping(
        PersonIdentifierMappingDto(
          mappingType = PersonIdentifierMappingDto.MappingType.NOMIS_CREATED,
          nomisPersonId = 1234567,
          nomisSequenceNumber = 4,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse!!.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class CreateEmploymentMapping {
    @Test
    internal fun `will pass oath2 token to create employment mapping endpoint`() = runTest {
      mockServer.stubCreateEmploymentMapping()

      apiService.createEmploymentMapping(
        PersonEmploymentMappingDto(
          mappingType = PersonEmploymentMappingDto.MappingType.NOMIS_CREATED,
          nomisPersonId = 1234567,
          nomisSequenceNumber = 4,
          dpsId = "7654321",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/employment")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateEmploymentMapping()

      val result = apiService.createEmploymentMapping(
        PersonEmploymentMappingDto(
          mappingType = PersonEmploymentMappingDto.MappingType.NOMIS_CREATED,
          nomisPersonId = 1234567,
          nomisSequenceNumber = 4,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisPersonId = 1234567890L
      val nomisSequenceNumber = 4L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreateEmploymentMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonEmploymentMappingDto(
              dpsId = dpsId,
              nomisPersonId = 1234567,
              nomisSequenceNumber = 4,
              mappingType = PersonEmploymentMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = PersonEmploymentMappingDto(
              dpsId = existingDpsId,
              nomisPersonId = nomisPersonId,
              nomisSequenceNumber = nomisSequenceNumber,
              mappingType = PersonEmploymentMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createEmploymentMapping(
        PersonEmploymentMappingDto(
          mappingType = PersonEmploymentMappingDto.MappingType.NOMIS_CREATED,
          nomisPersonId = 1234567,
          nomisSequenceNumber = 4,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse!!.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class CreateContactRestrictionMapping {
    @Test
    internal fun `will pass oath2 token to create  mapping endpoint`() = runTest {
      mockServer.stubCreateContactRestrictionMapping()

      apiService.createContactRestrictionMapping(
        PersonContactRestrictionMappingDto(
          mappingType = PersonContactRestrictionMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/contact-restriction")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateContactRestrictionMapping()

      val result = apiService.createContactRestrictionMapping(
        PersonContactRestrictionMappingDto(
          mappingType = PersonContactRestrictionMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreateContactRestrictionMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonContactRestrictionMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = PersonContactRestrictionMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = PersonContactRestrictionMappingDto(
              dpsId = existingDpsId,
              nomisId = nomisId,
              mappingType = PersonContactRestrictionMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createContactRestrictionMapping(
        PersonContactRestrictionMappingDto(
          mappingType = PersonContactRestrictionMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse!!.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }

  @Nested
  inner class CreatePersonRestrictionMapping {
    @Test
    internal fun `will pass oath2 token to create  mapping endpoint`() = runTest {
      mockServer.stubCreatePersonRestrictionMapping()

      apiService.createPersonRestrictionMapping(
        PersonRestrictionMappingDto(
          mappingType = PersonRestrictionMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/contact-person/person-restriction")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreatePersonRestrictionMapping()

      val result = apiService.createPersonRestrictionMapping(
        PersonRestrictionMappingDto(
          mappingType = PersonRestrictionMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "7654321",
        ),
      )

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val dpsId = "1234567890"
      val existingDpsId = "1234567890"

      mockServer.stubCreatePersonRestrictionMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = PersonRestrictionMappingDto(
              dpsId = dpsId,
              nomisId = nomisId,
              mappingType = PersonRestrictionMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = PersonRestrictionMappingDto(
              dpsId = existingDpsId,
              nomisId = nomisId,
              mappingType = PersonRestrictionMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createPersonRestrictionMapping(
        PersonRestrictionMappingDto(
          mappingType = PersonRestrictionMappingDto.MappingType.NOMIS_CREATED,
          nomisId = 1234567,
          dpsId = "1234567",
        ),
      )

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.dpsId).isEqualTo(dpsId)
      assertThat(result.errorResponse!!.moreInfo.existing.dpsId).isEqualTo(existingDpsId)
    }
  }
}
