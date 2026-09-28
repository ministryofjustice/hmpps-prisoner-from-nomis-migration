package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
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
    internal fun `will pass oauth2 token to sync endpoint`() = runTest {
      cprCorePersonServer.stubMigrateAddressesAndContacts("A1234BC")

      apiService.migrateCorePersonAddressesAndContacts("A1234BC", prisonAddressesRequest())

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will post request data to the sync endpoint`() = runTest {
      cprCorePersonServer.stubMigrateAddressesAndContacts()

      apiService.migrateCorePersonAddressesAndContacts("A1234BC", prisonAddressesRequest())

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("addresses[0].nomisAddressId", equalTo("12345"))
          .withRequestBodyJsonPath("addresses[0].isPrimary", equalTo("true"))
          .withRequestBodyJsonPath("addresses[0].postcode", equalTo("MK15 2ST")),
      )
    }

    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubMigrateAddressesAndContacts("A1234BC")

      apiService.migrateCorePersonAddressesAndContacts("A1234BC", prisonAddressesRequest())

      cprCorePersonServer.verify(
        postRequestedFor(urlPathEqualTo("/syscon-sync/addresses-contacts/A1234BC")),
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
    internal fun `will pass oauth2 token to sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateOffenderBelief("A1234BC")

      apiService.syncCreateOffenderBelief("A1234BC", prisonReligionRequest())

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will post request data to the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateOffenderBelief()

      apiService.syncCreateOffenderBelief("A1234BC", prisonReligionRequest())

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("nomisReligionId", equalTo("1"))
          .withRequestBodyJsonPath("religionCode", equalTo("BAPT"))
          .withRequestBodyJsonPath("current", equalTo("true"))
          .withRequestBodyJsonPath("changeReasonKnown", equalTo("true"))
          .withRequestBodyJsonPath("comments", equalTo("This is a comment"))
          .withRequestBodyJsonPath("startDate", equalTo("2020-11-01"))
          .withRequestBodyJsonPath("endDate", equalTo("2022-07-19"))
          .withRequestBodyJsonPath("createDateTime", equalTo("2019-11-01T04:05:00"))
          .withRequestBodyJsonPath("createUserId", equalTo("FRED_GEN"))
          .withRequestBodyJsonPath("modifyDateTime", equalTo("2020-11-01T04:05:00"))
          .withRequestBodyJsonPath("modifyUserId", equalTo("FRED_ADM")),
      )
    }

    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateOffenderBelief("A1234BC")

      apiService.syncCreateOffenderBelief("A1234BC", prisonReligionRequest())

      cprCorePersonServer.verify(
        postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/religion")),
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
    internal fun `will pass oauth2 token to sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncUpdateOffenderBelief("A1234BC")

      apiService.syncUpdateOffenderBelief("A1234BC", "cprId", prisonReligionUpdateRequest())

      cprCorePersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will post request data to the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncUpdateOffenderBelief()

      apiService.syncUpdateOffenderBelief("A1234BC", "cprId", prisonReligionUpdateRequest())

      cprCorePersonServer.verify(
        putRequestedFor(anyUrl())
          .withRequestBodyJsonPath("comments", equalTo("This is a comment"))
          .withRequestBodyJsonPath("modifyDateTime", equalTo("2020-11-01T04:05:00"))
          .withRequestBodyJsonPath("modifyUserId", equalTo("FRED_ADM")),
      )
    }

    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncUpdateOffenderBelief("A1234BC")

      apiService.syncUpdateOffenderBelief("A1234BC", "cprId", prisonReligionUpdateRequest())

      cprCorePersonServer.verify(
        putRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/religion/cprId")),
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
    internal fun `will pass oauth2 token to sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateEmail("A1234BC")

      apiService.syncCreateEmail("A1234BC", prisonEmailRequest())

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will post request data to the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateEmail()

      apiService.syncCreateEmail("A1234BC", prisonEmailRequest())

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("nomisContactId", equalTo("12345"))
          .withRequestBodyJsonPath("type", equalTo("EMAIL"))
          .withRequestBodyJsonPath("value", equalTo("test@example.com"))
          .withRequestBodyJsonPath("createDateTime", equalTo("2019-11-01T04:05:00"))
          .withRequestBodyJsonPath("createUserId", equalTo("FRED_GEN"))
          .withRequestBodyJsonPath("modifyDateTime", equalTo("2020-11-01T04:05:00"))
          .withRequestBodyJsonPath("modifyUserId", equalTo("FRED_ADM")),
      )
    }

    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncCreateEmail("A1234BC")

      apiService.syncCreateEmail("A1234BC", prisonEmailRequest())

      cprCorePersonServer.verify(
        postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact")),
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
    internal fun `will pass oauth2 token to sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncUpdateEmail("A1234BC")

      apiService.syncUpdateEmail("A1234BC", "cprContactId", prisonEmailRequest())

      cprCorePersonServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will post request data to the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncUpdateEmail()

      apiService.syncUpdateEmail("A1234BC", "cprContactId", prisonEmailRequest())

      cprCorePersonServer.verify(
        putRequestedFor(anyUrl())
          .withRequestBodyJsonPath("nomisContactId", equalTo("12345"))
          .withRequestBodyJsonPath("type", equalTo("EMAIL"))
          .withRequestBodyJsonPath("value", equalTo("test@example.com"))
          .withRequestBodyJsonPath("modifyDateTime", equalTo("2020-11-01T04:05:00"))
          .withRequestBodyJsonPath("modifyUserId", equalTo("FRED_ADM")),
      )
    }

    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncUpdateEmail("A1234BC")

      apiService.syncUpdateEmail("A1234BC", "cprContactId", prisonEmailRequest())

      cprCorePersonServer.verify(
        putRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact/cprContactId")),
      )
    }

    @Test
    fun `should throw if bad request`() = runTest {
      cprCorePersonServer.stubSyncUpdateEmail("A1234BC", status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.syncUpdateEmail("A1234BC", "cprContactId", prisonEmailRequest())
      }
    }
  }

  @Nested
  inner class SyncDeleteEmail {
    @Test
    internal fun `will pass oauth2 token to sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncDeleteEmail("A1234BC")

      apiService.syncDeleteEmail("A1234BC", "cprContactId")

      cprCorePersonServer.verify(
        deleteRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubSyncDeleteEmail("A1234BC")

      apiService.syncDeleteEmail("A1234BC", "cprContactId")

      cprCorePersonServer.verify(
        deleteRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/contact/cprContactId")),
      )
    }

    @Test
    fun `should throw if bad request`() = runTest {
      cprCorePersonServer.stubSyncDeleteEmail("A1234BC", status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.syncDeleteEmail("A1234BC", "cprContactId")
      }
    }
  }

  @Nested
  inner class ProcessPrisonMerge {
    @Test
    internal fun `will pass oauth2 token to sync endpoint`() = runTest {
      cprCorePersonServer.stubProcessPrisonMerge("A1234BC")

      apiService.processPrisonMerge("A1234BC", PrisonMerge(fromPrisonNumber = "B2345CD"))

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will post request data to the sync endpoint`() = runTest {
      cprCorePersonServer.stubProcessPrisonMerge()

      apiService.processPrisonMerge("A1234BC", PrisonMerge(fromPrisonNumber = "B2345CD"))

      cprCorePersonServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("fromPrisonNumber", equalTo("B2345CD")),
      )
    }

    @Test
    fun `will call the sync endpoint`() = runTest {
      cprCorePersonServer.stubProcessPrisonMerge("A1234BC")

      apiService.processPrisonMerge("A1234BC", PrisonMerge(fromPrisonNumber = "B2345CD"))

      cprCorePersonServer.verify(
        postRequestedFor(urlPathEqualTo("/syscon-sync/person/A1234BC/merge")),
      )
    }

    @Test
    fun `should throw if bad request`() = runTest {
      cprCorePersonServer.stubProcessPrisonMerge("A1234BC", status = BAD_REQUEST)

      assertThrows<WebClientResponseException.BadRequest> {
        apiService.processPrisonMerge("A1234BC", PrisonMerge(fromPrisonNumber = "B2345CD"))
      }
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
      PrisonAddress(
        nomisAddressId = 12345,
        isPrimary = true,
        addressUsage = emptyList(),
        contacts = emptyList(),
        postcode = "MK15 2ST",
        createDateTime = LocalDateTime.parse("2019-11-01T04:05:00"),
        createUserId = "joebiggs",
      ),
    ),
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
