package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.prisonerDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerRestrictionIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerWithRestrictions
import java.time.LocalDate

@SpringAPIServiceTest
@Import(ContactPersonNomisApiService::class, ContactPersonNomisApiMockServer::class)
class ContactPersonNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: ContactPersonNomisApiService

  @Autowired
  private lateinit var mockServer: ContactPersonNomisApiMockServer

  @Nested
  inner class GetPerson {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPerson(personId = 1234567)

      apiService.getPerson(nomisPersonId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetPerson(personId = 1234567)

      apiService.getPerson(nomisPersonId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/persons/1234567")),
      )
    }

    @Test
    fun `will return person`() = runTest {
      mockServer.stubGetPerson(personId = 1234567, person = contactPerson().copy(lastName = "Smith"))

      val person = apiService.getPerson(nomisPersonId = 1234567)

      assertThat(person.lastName).isEqualTo("Smith")
    }

    @Test
    fun `will throw error when person does not exist`() = runTest {
      mockServer.stubGetPerson(personId = 1234567, status = HttpStatus.NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getPerson(nomisPersonId = 1234567)
      }
    }
  }

  @Nested
  inner class GetContact {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetContact(contactId = 1234567)

      apiService.getContact(nomisContactId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetContact(contactId = 1234567)

      apiService.getContact(nomisContactId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/contact/1234567")),
      )
    }
  }

  @Nested
  inner class GetContactsForPrisoner {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubContactsForPrisoner("A1234KT")

      apiService.getContactsForPrisoner("A1234KT")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubContactsForPrisoner("A1234KT")

      apiService.getContactsForPrisoner("A1234KT")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234KT/contacts"))
          .withQueryParam("latest-booking-only", equalTo("false"))
          .withQueryParam("active-only", equalTo("false")),
      )
    }
  }

  @Nested
  inner class GetPrisonerDetails {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPrisonerDetails(offenderNo = "A1234TT")

      apiService.getPrisonerDetails("A1234TT")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetPrisonerDetails(offenderNo = "A1234TT")

      apiService.getPrisonerDetails("A1234TT")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234TT")),
      )
    }

    @Test
    fun `will return status`() = runTest {
      mockServer.stubGetPrisonerDetails(offenderNo = "A1234TT", prisonerDetails = prisonerDetails().copy(active = false, location = "OUT"))

      val details = apiService.getPrisonerDetails("A1234TT")

      assertThat(details.active).isFalse()
      assertThat(details.location).isEqualTo("OUT")
    }
  }

  @Nested
  inner class GetPrisonerRestrictionIdsToMigrate {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPrisonerRestrictionIdsToMigrate()

      apiService.getPrisonerRestrictionIdsToMigrate()

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass mandatory parameters to service`() = runTest {
      mockServer.stubGetPrisonerRestrictionIdsToMigrate()

      apiService.getPrisonerRestrictionIdsToMigrate(pageNumber = 12, pageSize = 20)

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("fromDate", equalTo(""))
          .withQueryParam("toDate", equalTo(""))
          .withQueryParam("page", equalTo("12"))
          .withQueryParam("size", equalTo("20")),
      )
    }

    @Test
    fun `can pass optional parameters to service`() = runTest {
      mockServer.stubGetPrisonerRestrictionIdsToMigrate()

      apiService.getPrisonerRestrictionIdsToMigrate(
        fromDate = LocalDate.parse("2020-01-01"),
        toDate = LocalDate.parse("2020-01-02"),
        pageNumber = 12,
        pageSize = 20,
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("fromDate", equalTo("2020-01-01"))
          .withQueryParam("toDate", equalTo("2020-01-02")),
      )
    }

    @Test
    fun `will return person ids`() = runTest {
      mockServer.stubGetPrisonerRestrictionIdsToMigrate(content = listOf(PrisonerRestrictionIdResponse(1234567), PrisonerRestrictionIdResponse(1234568)))

      val pages = apiService.getPrisonerRestrictionIdsToMigrate(pageNumber = 12, pageSize = 20)

      with(pages.content!!) {
        assertThat(this).hasSize(2)
        assertThat(this[0].restrictionId).isEqualTo(1234567)
        assertThat(this[1].restrictionId).isEqualTo(1234568)
      }
    }
  }

  @Nested
  inner class GetPrisonerRestrictionById {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPrisonerRestrictionById(restrictionId = 1234567)

      apiService.getPrisonerRestrictionById(prisonerRestrictionId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetPrisonerRestrictionById(restrictionId = 1234567)

      apiService.getPrisonerRestrictionById(prisonerRestrictionId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/restrictions/1234567")),
      )
    }
  }

  @Nested
  inner class GetPrisonerRestrictions {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPrisonerRestrictions(offenderNo = "A1234KT")

      apiService.getPrisonerRestrictions(offenderNo = "A1234KT")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass offender number to service`() = runTest {
      mockServer.stubGetPrisonerRestrictions(offenderNo = "A1234KT")

      apiService.getPrisonerRestrictions(offenderNo = "A1234KT")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234KT/restrictions")),
      )
    }

    @Test
    fun `will return prisoner restrictions`() = runTest {
      val restriction = uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.nomisPrisonerRestriction().copy(id = 12345)
      mockServer.stubGetPrisonerRestrictions(
        offenderNo = "A1234KT",
        response = PrisonerWithRestrictions(restrictions = listOf(restriction)),
      )

      val result = apiService.getPrisonerRestrictions(offenderNo = "A1234KT")

      assertThat(result.restrictions).hasSize(1)
      assertThat(result.restrictions[0].id).isEqualTo(12345)
    }
  }
}
