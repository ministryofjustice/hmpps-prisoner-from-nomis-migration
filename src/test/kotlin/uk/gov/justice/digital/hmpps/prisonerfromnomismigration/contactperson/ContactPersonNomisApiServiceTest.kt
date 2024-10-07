package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonIdResponse
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
  inner class GetPersonIdsToMigrate {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPersonIdsToMigrate()

      apiService.getPersonIdsToMigrate()

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass mandatory parameters to service`() = runTest {
      mockServer.stubGetPersonIdsToMigrate()

      apiService.getPersonIdsToMigrate(pageNumber = 12, pageSize = 20)

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
      mockServer.stubGetPersonIdsToMigrate()

      apiService.getPersonIdsToMigrate(
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
      mockServer.stubGetPersonIdsToMigrate(content = listOf(PersonIdResponse(1234567), PersonIdResponse(1234568)))

      val pages = apiService.getPersonIdsToMigrate(pageNumber = 12, pageSize = 20)

      assertThat(pages.content).hasSize(2)
      assertThat(pages.content[0].personId).isEqualTo(1234567)
      assertThat(pages.content[1].personId).isEqualTo(1234568)
    }
  }
}
