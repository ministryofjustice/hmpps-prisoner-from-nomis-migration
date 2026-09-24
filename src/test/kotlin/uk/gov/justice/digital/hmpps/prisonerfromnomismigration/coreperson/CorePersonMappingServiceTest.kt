package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

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
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonEmailAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension

@ExtendWith(MappingApiExtension::class)
@SpringAPIServiceTest
@Import(CorePersonMappingService::class, CorePersonMappingApiMockServer::class)
class CorePersonMappingServiceTest(
  @Autowired private val apiService: CorePersonMappingService,
  @Autowired private val mockServer: CorePersonMappingApiMockServer,
) {
  @Nested
  inner class GetByNomisAddressIdOrNull {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull()

      apiService.getByNomisAddressIdOrNull(1234567)

      mockServer.verify(getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull(1234567)

      apiService.getByNomisAddressIdOrNull(1234567)

      mockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/address/nomis-address-id/1234567")))
    }

    @Test
    fun `will return mapping when it exists`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull(1234567)

      assertThat(apiService.getByNomisAddressIdOrNull(1234567)!!.nomisId).isEqualTo(1234567)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull(1234567, null)

      assertThat(apiService.getByNomisAddressIdOrNull(1234567)).isNull()
    }
  }

  private fun addressMapping() = CorePersonAddressMappingDto(
    cprId = "cpr-address-id",
    nomisId = 1234567,
    nomisPrisonNumber = "A1234BC",
    mappingType = CorePersonAddressMappingDto.MappingType.NOMIS_CREATED,
  )

  private fun emailMapping() = CorePersonEmailAddressMappingDto(
    cprId = "cpr-email-id",
    nomisId = 1234567,
    nomisPrisonNumber = "A1234BC",
    mappingType = CorePersonEmailAddressMappingDto.MappingType.NOMIS_CREATED,
  )

  private fun phoneMapping() = CorePersonPhoneMappingDto(
    cprId = "cpr-phone-id",
    nomisId = 1234567,
    nomisPrisonNumber = "A1234BC",
    mappingType = CorePersonPhoneMappingDto.MappingType.NOMIS_CREATED,
  )

  @Nested
  inner class GetByNomisAddressId {
    @Test
    fun `will return mapping`() = runTest {
      mockServer.stubGetByNomisAddressIdOrNull(1234567)

      assertThat(apiService.getByNomisAddressId(1234567).nomisId).isEqualTo(1234567)
    }
  }

  @Nested
  inner class DeleteByNomisAddressId {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubDeleteByNomisAddressId(1234567)

      apiService.deleteByNomisAddressId(1234567)

      mockServer.verify(deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisAddressId(1234567)

      apiService.deleteByNomisAddressId(1234567)

      mockServer.verify(deleteRequestedFor(urlPathEqualTo("/mapping/core-person/address/nomis-address-id/1234567")))
    }
  }

  @Nested
  inner class CreateAddressMapping {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubCreateAddressMapping()

      apiService.createAddressMapping(addressMapping())

      mockServer.verify(postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `will pass mapping to service`() = runTest {
      mockServer.stubCreateAddressMapping()

      apiService.createAddressMapping(addressMapping())

      mockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/core-person/address")))
    }
  }

  @Nested
  inner class GetByNomisEmailIdOrNull {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull()

      apiService.getByNomisEmailIdOrNull(1234567)

      mockServer.verify(getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull(1234567)

      apiService.getByNomisEmailIdOrNull(1234567)

      mockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/email/nomis-email-address-id/1234567")))
    }

    @Test
    fun `will return mapping when it exists`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull(1234567)

      assertThat(apiService.getByNomisEmailIdOrNull(1234567)!!.nomisId).isEqualTo(1234567)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull(1234567, null)

      assertThat(apiService.getByNomisEmailIdOrNull(1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisEmailId {
    @Test
    fun `will return mapping`() = runTest {
      mockServer.stubGetByNomisEmailIdOrNull(1234567)

      assertThat(apiService.getByNomisEmailId(1234567).nomisId).isEqualTo(1234567)
    }
  }

  @Nested
  inner class DeleteByNomisEmailId {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubDeleteByNomisEmailId(1234567)

      apiService.deleteByNomisEmailId(1234567)

      mockServer.verify(deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisEmailId(1234567)

      apiService.deleteByNomisEmailId(1234567)

      mockServer.verify(deleteRequestedFor(urlPathEqualTo("/mapping/core-person/email/nomis-email-address-id/1234567")))
    }
  }

  @Nested
  inner class CreateEmailMapping {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubCreateEmailMapping()

      apiService.createEmailMapping(emailMapping())

      mockServer.verify(postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `will pass mapping to service`() = runTest {
      mockServer.stubCreateEmailMapping()

      apiService.createEmailMapping(emailMapping())

      mockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/core-person/email")))
    }
  }

  @Nested
  inner class GetByNomisPhoneIdOrNull {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull()

      apiService.getByNomisPhoneIdOrNull(1234567)

      mockServer.verify(getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull(1234567)

      apiService.getByNomisPhoneIdOrNull(1234567)

      mockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/phone/nomis-phone-id/1234567")))
    }

    @Test
    fun `will return mapping when it exists`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull(1234567)

      assertThat(apiService.getByNomisPhoneIdOrNull(1234567)!!.nomisId).isEqualTo(1234567)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull(1234567, null)

      assertThat(apiService.getByNomisPhoneIdOrNull(1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisPhoneId {
    @Test
    fun `will return mapping`() = runTest {
      mockServer.stubGetByNomisPhoneIdOrNull(1234567)

      assertThat(apiService.getByNomisPhoneId(1234567).nomisId).isEqualTo(1234567)
    }
  }

  @Nested
  inner class DeleteByNomisPhoneId {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubDeleteByNomisPhoneId(1234567)

      apiService.deleteByNomisPhoneId(1234567)

      mockServer.verify(deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisPhoneId(1234567)

      apiService.deleteByNomisPhoneId(1234567)

      mockServer.verify(deleteRequestedFor(urlPathEqualTo("/mapping/core-person/phone/nomis-phone-id/1234567")))
    }
  }

  @Nested
  inner class CreatePhoneMapping {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubCreatePhoneMapping()

      apiService.createPhoneMapping(phoneMapping())

      mockServer.verify(postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")))
    }

    @Test
    fun `will pass mapping to service`() = runTest {
      mockServer.stubCreatePhoneMapping()

      apiService.createPhoneMapping(phoneMapping())

      mockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/core-person/phone")))
    }
  }
}
