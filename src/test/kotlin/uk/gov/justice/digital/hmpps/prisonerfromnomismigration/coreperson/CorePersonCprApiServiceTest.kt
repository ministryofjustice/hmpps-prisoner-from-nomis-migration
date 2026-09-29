package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiExtension.Companion.cprCorePersonServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonAddressesAndContactsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonMerge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonReligionUpdateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(CorePersonCprApiExtension::class)
@SpringAPIServiceTest
@Import(CorePersonCprApiService::class, CorePersonConfiguration::class)
class CorePersonCprApiServiceTest(@Autowired private val apiService: CorePersonCprApiService) {

  @Nested
  inner class MigrateCorePersonAddresses {
    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubMigrateAddressesAndContacts("A1234BC")

      apiService.migrateCorePersonAddressesAndContacts("A1234BC", prisonAddressesRequest())

      cprCorePersonServer.verify(
        postRequestedFor(urlPathEqualTo("/syscon-sync/addresses-contacts/A1234BC"))
          .withRequestBodyJsonPath("addresses[0].nomisAddressId", equalTo("12345")),
      )
    }

    @Test
    fun `should throw if bad request`() = runTest {
      cprCorePersonServer.stubMigrateAddressesAndContacts("A1234BC", status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.migrateCorePersonAddressesAndContacts("A1234BC", prisonAddressesRequest())
      }
    }
  }

  @Nested
  inner class SyncCreateOffenderBelief {
    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateOffenderBelief("A1234BC")

      apiService.syncCreateOffenderBelief("A1234BC", prisonReligionRequest())

      cprCorePersonServer.verify(
        postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/religion"))
          .withRequestBodyJsonPath("nomisReligionId", equalTo("1")),
      )
    }

    @Test
    fun `should throw if bad request`() = runTest {
      cprCorePersonServer.stubSyncCreateOffenderBelief("A1234BC", status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.syncCreateOffenderBelief("A1234BC", prisonReligionRequest())
      }
    }
  }

  @Nested
  inner class SyncUpdateOffenderBelief {
    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncUpdateOffenderBelief("A1234BC")

      apiService.syncUpdateOffenderBelief("A1234BC", "cprId", prisonReligionUpdateRequest())

      cprCorePersonServer.verify(
        putRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/religion/cprId"))
          .withRequestBodyJsonPath("comments", equalTo("This is a comment")),
      )
    }

    @Test
    fun `should throw if bad request`() = runTest {
      cprCorePersonServer.stubSyncUpdateOffenderBelief("A1234BC", status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.syncUpdateOffenderBelief("A1234BC", "cprId", prisonReligionUpdateRequest())
      }
    }
  }

  @Nested
  inner class SyncCreateEmail {
    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateEmail("A1234BC")

      apiService.syncCreateEmail("A1234BC", prisonEmailRequest())

      cprCorePersonServer.verify(
        postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact"))
          .withRequestBodyJsonPath("nomisContactId", equalTo("12345")),
      )
    }

    @Test
    fun `should throw if bad request`() = runTest {
      cprCorePersonServer.stubSyncCreateEmail("A1234BC", status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.syncCreateEmail("A1234BC", prisonEmailRequest())
      }
    }
  }

  @Nested
  inner class SyncUpdateEmail {
    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncUpdateEmail("A1234BC")

      apiService.syncUpdateEmail("A1234BC", "cprContactId", prisonEmailRequest())

      cprCorePersonServer.verify(
        putRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact/cprContactId"))
          .withRequestBodyJsonPath("nomisContactId", equalTo("12345")),
      )
    }
  }

  @Nested
  inner class SyncDeleteEmail {
    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncDeleteEmail("A1234BC")

      apiService.syncDeleteEmail("A1234BC", "cprContactId")

      cprCorePersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact/cprContactId")),
      )
    }
  }

  @Nested
  inner class SyncCreateAddress {
    @Test
    fun `will call the sync endpoint and return the address mapping`() = runTest {
      cprCorePersonServer.stubSyncCreateAddress("A1234BC", addressId = 12345, cprAddressId = "cprAddressId")

      val response = apiService.syncCreateAddress("A1234BC", prisonAddress())

      assertThat(response.nomisAddressId).isEqualTo(12345)
      assertThat(response.cprAddressId).isEqualTo("cprAddressId")
      cprCorePersonServer.verify(
        postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/address"))
          .withRequestBodyJsonPath("nomisAddressId", equalTo("12345")),
      )
    }

    @Test
    fun `should throw if bad request`() = runTest {
      cprCorePersonServer.stubSyncCreateAddress("A1234BC", status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.syncCreateAddress("A1234BC", prisonAddress())
      }
    }
  }

  @Nested
  inner class SyncUpdateAddress {
    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncUpdateAddress("A1234BC", "cprAddressId")

      apiService.syncUpdateAddress("A1234BC", "cprAddressId", prisonAddress())

      cprCorePersonServer.verify(
        putRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/address/cprAddressId"))
          .withRequestBodyJsonPath("nomisAddressId", equalTo("12345")),
      )
    }
  }

  @Nested
  inner class SyncDeleteAddress {
    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncDeleteAddress("A1234BC", "cprAddressId")

      apiService.syncDeleteAddress("A1234BC", "cprAddressId")

      cprCorePersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/address/cprAddressId")),
      )
    }
  }

  @Nested
  inner class ProcessPrisonMerge {
    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubProcessPrisonMerge("A1234BC")

      apiService.processPrisonMerge("A1234BC", PrisonMerge(fromPrisonNumber = "B2345CD"))

      cprCorePersonServer.verify(
        postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/merge"))
          .withRequestBodyJsonPath("fromPrisonNumber", equalTo("B2345CD")),
      )
    }
  }

  fun prisonReligionRequest() = PrisonReligionHistory(
    nomisReligionId = "1",
    religionCode = PrisonReligionHistory.ReligionCode.BAPT,
    current = true,
    changeReasonKnown = true,
    comments = "This is a comment",
    startDate = LocalDate.parse("2020-11-01"),
    endDate = LocalDate.parse("2022-07-19"),
    createDateTime = LocalDateTime.parse("2019-11-01T04:05:00"),
    createUserId = "FRED_GEN",
    modifyDateTime = LocalDateTime.parse("2020-11-01T04:05:00"),
    modifyUserId = "FRED_ADM",
  )

  fun prisonAddressesRequest() = PrisonAddressesAndContactsRequest(
    addresses = listOf(
      prisonAddress(),
    ),
  )

  fun prisonAddress() = PrisonAddress(
    nomisAddressId = 12345,
    isPrimary = true,
    addressUsage = emptyList(),
    contacts = emptyList(),
    postcode = "MK15 2ST",
    createDateTime = LocalDateTime.parse("2019-11-01T04:05:00"),
    createUserId = "joebiggs",
  )

  fun prisonReligionUpdateRequest() = PrisonReligionUpdateRequest(
    comments = "This is a comment",
    modifyDateTime = LocalDateTime.parse("2020-11-01T04:05:00"),
    modifyUserId = "FRED_ADM",
  )

  fun prisonEmailRequest() = PrisonContact(
    nomisContactId = 12345,
    type = PrisonContact.Type.EMAIL,
    value = "test@example.com",
    createDateTime = LocalDateTime.parse("2019-11-01T04:05:00"),
    createUserId = "FRED_GEN",
    modifyDateTime = LocalDateTime.parse("2020-11-01T04:05:00"),
    modifyUserId = "FRED_ADM",
  )
}
