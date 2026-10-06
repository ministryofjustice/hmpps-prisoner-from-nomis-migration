package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressUsageMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto.NomisContactType.EMAIL
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto.NomisContactType.PHONE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

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

  private fun addressUsageMapping() = CorePersonAddressUsageMappingDto(
    cprId = "cpr-address-usage-id",
    nomisId = 1234567,
    addressUsageCode = "CURFEW",
    nomisPrisonNumber = "A1234BC",
    mappingType = CorePersonAddressUsageMappingDto.MappingType.NOMIS_CREATED,
  )

  private fun emailMapping() = CorePersonContactMappingDto(
    cprId = "cpr-email-id",
    nomisId = 1234567,
    nomisContactType = EMAIL,
    nomisPrisonNumber = "A1234BC",
    mappingType = CorePersonContactMappingDto.MappingType.NOMIS_CREATED,
  )

  private fun phoneMapping() = CorePersonContactMappingDto(
    cprId = "cpr-phone-id",
    nomisId = 1234567,
    nomisContactType = PHONE,
    nomisPrisonNumber = "A1234BC",
    mappingType = CorePersonContactMappingDto.MappingType.NOMIS_CREATED,
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
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisAddressId(1234567)

      apiService.deleteByNomisAddressId(1234567)

      mockServer.verify(deleteRequestedFor(urlPathEqualTo("/mapping/core-person/address/nomis-address-id/1234567")))
    }
  }

  @Nested
  inner class CreateAddressMapping {
    @Test
    fun `will pass mapping to service`() = runTest {
      mockServer.stubCreateAddressMapping()

      apiService.createAddressMapping(addressMapping())

      mockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/core-person/address")))
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val cprId = "cpr-address-id"
      val existingCprId = "existing-cpr-address-id"

      mockServer.stubCreateAddressMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = CorePersonAddressMappingDto(
              cprId = cprId,
              nomisId = nomisId,
              nomisPrisonNumber = "A1234BC",
              mappingType = CorePersonAddressMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = CorePersonAddressMappingDto(
              cprId = existingCprId,
              nomisId = nomisId,
              nomisPrisonNumber = "A1234BC",
              mappingType = CorePersonAddressMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createAddressMapping(addressMapping())

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.cprId).isEqualTo(cprId)
      assertThat(result.errorResponse.moreInfo.existing.cprId).isEqualTo(existingCprId)
    }
  }

  @Nested
  inner class GetByNomisAddressUsageIdOrNull {
    @Test
    fun `will pass NOMIS address id and usage code to service`() = runTest {
      mockServer.stubGetByNomisAddressUsageIdOrNull(1234567, "CURFEW")

      apiService.getByNomisAddressUsageIdOrNull(1234567, "CURFEW")

      mockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/address-usage/nomis-address-id/1234567/usage-code/CURFEW")))
    }

    @Test
    fun `will return mapping when it exists`() = runTest {
      mockServer.stubGetByNomisAddressUsageIdOrNull(1234567, "CURFEW")

      val mapping = apiService.getByNomisAddressUsageIdOrNull(1234567, "CURFEW")!!

      assertThat(mapping.nomisId).isEqualTo(1234567)
      assertThat(mapping.addressUsageCode).isEqualTo("CURFEW")
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisAddressUsageIdOrNull(1234567, "CURFEW", null)

      assertThat(apiService.getByNomisAddressUsageIdOrNull(1234567, "CURFEW")).isNull()
    }
  }

  @Nested
  inner class GetByNomisAddressUsageId {
    @Test
    fun `will pass NOMIS address id and usage code to service`() = runTest {
      mockServer.stubGetByNomisAddressUsageIdOrNull(1234567, "CURFEW")

      apiService.getByNomisAddressUsageId(1234567, "CURFEW")

      mockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/address-usage/nomis-address-id/1234567/usage-code/CURFEW")))
    }

    @Test
    fun `will return mapping`() = runTest {
      mockServer.stubGetByNomisAddressUsageIdOrNull(1234567, "CURFEW")

      val mapping = apiService.getByNomisAddressUsageId(1234567, "CURFEW")

      assertThat(mapping.nomisId).isEqualTo(1234567)
      assertThat(mapping.addressUsageCode).isEqualTo("CURFEW")
    }
  }

  @Nested
  inner class DeleteByNomisAddressUsageId {
    @Test
    fun `will pass NOMIS address id and usage code to service`() = runTest {
      mockServer.stubDeleteByNomisAddressUsageId(1234567, "CURFEW")

      apiService.deleteByNomisAddressUsageId(1234567, "CURFEW")

      mockServer.verify(deleteRequestedFor(urlPathEqualTo("/mapping/core-person/address-usage/nomis-address-id/1234567/usage-code/CURFEW")))
    }
  }

  @Nested
  inner class CreateAddressUsageMapping {
    @Test
    fun `will pass mapping to service`() = runTest {
      mockServer.stubCreateAddressUsageMapping()

      apiService.createAddressUsageMapping(addressUsageMapping())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/address-usage"))
          .withRequestBodyJsonPath("cprId", "cpr-address-usage-id")
          .withRequestBodyJsonPath("nomisId", 1234567)
          .withRequestBodyJsonPath("addressUsageCode", "CURFEW")
          .withRequestBodyJsonPath("nomisPrisonNumber", "A1234BC")
          .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
      )
    }

    @Test
    fun `will return success result when created`() = runTest {
      mockServer.stubCreateAddressUsageMapping()

      val result = apiService.createAddressUsageMapping(addressUsageMapping())

      assertThat(result.isError).isFalse()
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val cprId = "cpr-address-usage-id"
      val existingCprId = "existing-cpr-address-usage-id"

      mockServer.stubCreateAddressUsageMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = CorePersonAddressUsageMappingDto(
              cprId = cprId,
              nomisId = nomisId,
              addressUsageCode = "CURFEW",
              nomisPrisonNumber = "A1234BC",
              mappingType = CorePersonAddressUsageMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = CorePersonAddressUsageMappingDto(
              cprId = existingCprId,
              nomisId = nomisId,
              addressUsageCode = "CURFEW",
              nomisPrisonNumber = "A1234BC",
              mappingType = CorePersonAddressUsageMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createAddressUsageMapping(addressUsageMapping())

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.cprId).isEqualTo(cprId)
      assertThat(result.errorResponse.moreInfo.duplicate.addressUsageCode).isEqualTo("CURFEW")
      assertThat(result.errorResponse.moreInfo.existing.cprId).isEqualTo(existingCprId)
    }
  }

  @Nested
  inner class GetByNomisEmailIdOrNull {
    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisContactIdOrNull(1234567, EMAIL)

      apiService.getByNomisEmailIdOrNull(1234567)

      mockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/contact/nomis-contact-id/1234567/type/EMAIL")))
    }

    @Test
    fun `will return mapping when it exists`() = runTest {
      mockServer.stubGetByNomisContactIdOrNull(1234567, EMAIL)

      assertThat(apiService.getByNomisEmailIdOrNull(1234567)!!.nomisId).isEqualTo(1234567)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisContactIdOrNull(1234567, EMAIL, null)

      assertThat(apiService.getByNomisEmailIdOrNull(1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisEmailId {
    @Test
    fun `will return mapping`() = runTest {
      mockServer.stubGetByNomisContactIdOrNull(1234567, EMAIL)

      assertThat(apiService.getByNomisEmailId(1234567).nomisId).isEqualTo(1234567)
    }
  }

  @Nested
  inner class DeleteByNomisEmailId {
    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisContactId(1234567, EMAIL)

      apiService.deleteByNomisEmailId(1234567)

      mockServer.verify(deleteRequestedFor(urlPathEqualTo("/mapping/core-person/contact/nomis-contact-id/1234567/type/EMAIL")))
    }
  }

  @Nested
  inner class CreateEmailMapping {
    @Test
    fun `will pass mapping to service`() = runTest {
      mockServer.stubCreateContactMapping()

      apiService.createEmailMapping(emailMapping())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/contact"))
          .withRequestBodyJsonPath("cprId", "cpr-email-id")
          .withRequestBodyJsonPath("nomisId", 1234567)
          .withRequestBodyJsonPath("nomisContactType", "EMAIL")
          .withRequestBodyJsonPath("nomisPrisonNumber", "A1234BC")
          .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
      )
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val cprId = "cpr-email-id"
      val existingCprId = "existing-cpr-email-id"

      mockServer.stubCreateContactMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = CorePersonContactMappingDto(
              cprId = cprId,
              nomisId = nomisId,
              nomisContactType = EMAIL,
              nomisPrisonNumber = "A1234BC",
              mappingType = CorePersonContactMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = CorePersonContactMappingDto(
              cprId = existingCprId,
              nomisId = nomisId,
              nomisContactType = EMAIL,
              nomisPrisonNumber = "A1234BC",
              mappingType = CorePersonContactMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createEmailMapping(emailMapping())

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.cprId).isEqualTo(cprId)
      assertThat(result.errorResponse.moreInfo.existing.cprId).isEqualTo(existingCprId)
    }
  }

  @Nested
  inner class GetByNomisPhoneIdOrNull {
    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetByNomisContactIdOrNull(1234567, PHONE)

      apiService.getByNomisPhoneIdOrNull(1234567)

      mockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/core-person/contact/nomis-contact-id/1234567/type/PHONE")))
    }

    @Test
    fun `will return mapping when it exists`() = runTest {
      mockServer.stubGetByNomisContactIdOrNull(1234567, PHONE)

      assertThat(apiService.getByNomisPhoneIdOrNull(1234567)!!.nomisId).isEqualTo(1234567)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetByNomisContactIdOrNull(1234567, PHONE, null)

      assertThat(apiService.getByNomisPhoneIdOrNull(1234567)).isNull()
    }
  }

  @Nested
  inner class GetByNomisPhoneId {
    @Test
    fun `will return mapping`() = runTest {
      mockServer.stubGetByNomisContactIdOrNull(1234567, PHONE)

      assertThat(apiService.getByNomisPhoneId(1234567).nomisId).isEqualTo(1234567)
    }
  }

  @Nested
  inner class DeleteByNomisPhoneId {
    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubDeleteByNomisContactId(1234567, PHONE)

      apiService.deleteByNomisPhoneId(1234567)

      mockServer.verify(deleteRequestedFor(urlPathEqualTo("/mapping/core-person/contact/nomis-contact-id/1234567/type/PHONE")))
    }
  }

  @Nested
  inner class CreatePhoneMapping {
    @Test
    fun `will pass mapping to service`() = runTest {
      mockServer.stubCreateContactMapping()

      apiService.createPhoneMapping(phoneMapping())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/contact"))
          .withRequestBodyJsonPath("cprId", "cpr-phone-id")
          .withRequestBodyJsonPath("nomisId", 1234567)
          .withRequestBodyJsonPath("nomisContactType", "PHONE")
          .withRequestBodyJsonPath("nomisPrisonNumber", "A1234BC")
          .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED"),
      )
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val nomisId = 1234567890L
      val cprId = "cpr-phone-id"
      val existingCprId = "existing-cpr-phone-id"

      mockServer.stubCreateContactMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = CorePersonContactMappingDto(
              cprId = cprId,
              nomisId = nomisId,
              nomisContactType = PHONE,
              nomisPrisonNumber = "A1234BC",
              mappingType = CorePersonContactMappingDto.MappingType.NOMIS_CREATED,
            ),
            existing = CorePersonContactMappingDto(
              cprId = existingCprId,
              nomisId = nomisId,
              nomisContactType = PHONE,
              nomisPrisonNumber = "A1234BC",
              mappingType = CorePersonContactMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val result = apiService.createPhoneMapping(phoneMapping())

      assertThat(result.isError).isTrue()
      assertThat(result.errorResponse!!.moreInfo.duplicate.cprId).isEqualTo(cprId)
      assertThat(result.errorResponse.moreInfo.existing.cprId).isEqualTo(existingCprId)
    }
  }
}
