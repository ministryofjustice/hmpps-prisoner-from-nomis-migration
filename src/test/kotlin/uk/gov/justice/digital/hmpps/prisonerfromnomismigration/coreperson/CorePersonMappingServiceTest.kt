package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AddressUsageByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactByNomisIdRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMappingsByNomisIdsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMappingsResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressContactMergeMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAddressUsageMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

@ExtendWith(MappingApiExtension::class)
@SpringAPIServiceTest
@Import(CorePersonMappingService::class, CorePersonMappingApiMockServer::class)
class CorePersonMappingServiceTest(
  @Autowired private val apiService: CorePersonMappingService,
  @Autowired private val mockServer: CorePersonMappingApiMockServer,
) {
  private fun addressMapping() = CorePersonAddressMappingDto(
    cprId = "cpr-address-id",
    nomisId = 12345,
    nomisPrisonNumber = "A1234BC",
    mappingType = CorePersonAddressMappingDto.MappingType.MIGRATED,
  )

  private fun addressUsageMapping() = CorePersonAddressUsageMappingDto(
    cprId = "cpr-address-usage-id",
    nomisId = 12345,
    addressUsageCode = "HOME",
    nomisPrisonNumber = "A1234BC",
    mappingType = CorePersonAddressUsageMappingDto.MappingType.MIGRATED,
  )

  private fun contactMapping() = CorePersonContactMappingDto(
    cprId = "cpr-contact-id",
    nomisId = 67890,
    nomisContactType = CorePersonContactMappingDto.NomisContactType.PHONE,
    nomisPrisonNumber = "A1234BC",
    mappingType = CorePersonContactMappingDto.MappingType.MIGRATED,
  )

  @Nested
  inner class GetMappingsFromNomisIds {
    private val request = CorePersonAddressContactMappingsByNomisIdsRequest(
      addresses = listOf(12345),
      contacts = listOf(ContactByNomisIdRequest(nomisId = 67890, nomisContactType = ContactByNomisIdRequest.NomisContactType.PHONE)),
      addressUsages = listOf(AddressUsageByNomisIdRequest(nomisId = 12345, addressUsageCode = "HOME")),
    )

    @Test
    fun `will pass NOMIS ids to service`() = runTest {
      mockServer.stubGetMappingsFromNomisIds()

      apiService.getMappingsFromNomisIds(request)

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/merge/mappings/by-nomis-ids"))
          .withRequestBodyJsonPath("addresses[0]", 12345)
          .withRequestBodyJsonPath("contacts[0].nomisId", 67890)
          .withRequestBodyJsonPath("contacts[0].nomisContactType", "PHONE")
          .withRequestBodyJsonPath("addressUsages[0].nomisId", 12345)
          .withRequestBodyJsonPath("addressUsages[0].addressUsageCode", "HOME"),
      )
    }

    @Test
    fun `will return mappings`() = runTest {
      mockServer.stubGetMappingsFromNomisIds(
        CorePersonAddressContactMappingsResult(
          addresses = listOf(addressMapping()),
          contacts = listOf(contactMapping()),
          addressUsages = listOf(addressUsageMapping()),
        ),
      )

      val result = apiService.getMappingsFromNomisIds(request)

      assertThat(result.addresses.single().cprId).isEqualTo("cpr-address-id")
      assertThat(result.contacts.single().cprId).isEqualTo("cpr-contact-id")
      assertThat(result.addressUsages.single().cprId).isEqualTo("cpr-address-usage-id")
    }
  }

  @Nested
  inner class Merge {
    @Test
    fun `will pass merge mappings to service`() = runTest {
      mockServer.stubMerge()

      apiService.merge(
        CorePersonAddressContactMergeMappingsDto(
          toNomisPrisonNumber = "A1234BC",
          fromNomisPrisonNumber = "B1234CD",
          addresses = listOf(addressMapping()),
          contacts = listOf(contactMapping()),
          addressUsages = listOf(addressUsageMapping()),
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/core-person/merge"))
          .withRequestBodyJsonPath("toNomisPrisonNumber", "A1234BC")
          .withRequestBodyJsonPath("fromNomisPrisonNumber", "B1234CD")
          .withRequestBodyJsonPath("addresses[0].cprId", "cpr-address-id")
          .withRequestBodyJsonPath("contacts[0].cprId", "cpr-contact-id")
          .withRequestBodyJsonPath("addressUsages[0].cprId", "cpr-address-usage-id"),
      )
    }
  }
}
